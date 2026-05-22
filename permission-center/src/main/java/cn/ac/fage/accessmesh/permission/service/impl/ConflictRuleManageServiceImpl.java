package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ConflictRuleResp;
import cn.ac.fage.accessmesh.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.permission.service.ConflictRuleManageService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限冲突规则管理服务实现类
 * <p>
 * 提供权限冲突规则的CRUD操作和冲突检测功能。
 * 权限冲突规则定义了哪些操作权限组合被视为冲突，
 * 用于权限分配时检测和预警潜在的权限冲突。
 * 冲突检测支持双向匹配（A-B和B-A都视为冲突）。
 * 所有操作均通过PermQueryEngine进行权限校验，确保操作安全。
 * 批量删除采用批量软删除策略，避免N+1查询问题。
 * </p>
 */
@Service
public class ConflictRuleManageServiceImpl implements ConflictRuleManageService {

    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final OperationLogDomainService operationLogDomainService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param conflictRuleMapper       权限冲突规则数据访问层
     * @param operationLogDomainService 操作日志领域服务
     * @param engine                    权限查询引擎
     */
    public ConflictRuleManageServiceImpl(PermissionConflictRuleMapper conflictRuleMapper,
                                          OperationLogDomainService operationLogDomainService,
                                          PermQueryEngine engine) {
        this.conflictRuleMapper = conflictRuleMapper;
        this.operationLogDomainService = operationLogDomainService;
        this.engine = engine;
    }

    /**
     * 创建权限冲突规则
     * <p>
     * 创建新的权限冲突规则定义。
     * 冲突规则指定两个操作权限的组合被视为冲突，
     * 可限定于特定业务域、资源类型或角色。
     * 需要CONFLICT_RULE_CREATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含冲突类型、两个操作权限ID等
     * @param operatorId 操作者ID，可选
     * @return 创建的冲突规则响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConflictRuleResp createConflictRule(Long tenantId, ConflictRuleReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("Permission denied: CREATE on CONFLICT_RULE");
        }

        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setTenantId(tenantId);
        rule.setConflictType(req.conflictType());
        rule.setFirstOperationPermissionId(req.firstOperationPermissionId());
        rule.setSecondOperationPermissionId(req.secondOperationPermissionId());
        rule.setResourceTypeValue(req.resourceTypeValue());
        rule.setFirstAbstractRoleId(req.firstAbstractRoleId());
        rule.setSecondAbstractRoleId(req.secondAbstractRoleId());
        rule.setDescription(req.description());
        rule.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rule.setDeleteFlag(0L);
        conflictRuleMapper.insert(rule);
        return toConflictRuleResp(rule);
    }

    /**
     * 获取权限冲突规则详情
     * <p>
     * 根据规则ID查询权限冲突规则的完整信息。
     * </p>
     *
     * @param tenantId 租户ID
     * @param ruleId   冲突规则ID
     * @return 冲突规则响应，不存在返回null
     */
    @Override
    public ConflictRuleResp getConflictRule(Long tenantId, Long ruleId) {
        PermissionConflictRule rule = conflictRuleMapper.selectValidById(ruleId, tenantId);
        return rule != null ? toConflictRuleResp(rule) : null;
    }

    /**
     * 查询权限冲突规则列表
     * <p>
     * 查询租户下所有活跃的权限冲突规则。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 冲突规则响应列表
     */
    @Override
    public List<ConflictRuleResp> listConflictRules(Long tenantId) {
        return conflictRuleMapper.selectByTenantId(tenantId).stream().map(this::toConflictRuleResp).collect(Collectors.toList());
    }

    /**
     * 更新权限冲突规则
     * <p>
     * 更新权限冲突规则的各项属性。
     * 需要CONFLICT_RULE_UPDATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含规则ID和要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的冲突规则响应
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 规则不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConflictRuleResp updateConflictRule(Long tenantId, ConflictRuleUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, req.id(), OperationCodeConstants.UPDATE)) {
            throw new SecurityException("Permission denied: UPDATE on CONFLICT_RULE:" + req.id());
        }

        PermissionConflictRule rule = conflictRuleMapper.selectOneById(req.id());
        if (rule == null || rule.getDeleteFlag() != 0L || !tenantId.equals(rule.getTenantId())) {
            throw new IllegalArgumentException("Conflict rule not found: " + req.id());
        }
        if (req.conflictType() != null) rule.setConflictType(req.conflictType());
        if (req.firstOperationPermissionId() != null) rule.setFirstOperationPermissionId(req.firstOperationPermissionId());
        if (req.secondOperationPermissionId() != null) rule.setSecondOperationPermissionId(req.secondOperationPermissionId());
        if (req.resourceTypeValue() != null) rule.setResourceTypeValue(req.resourceTypeValue());
        if (req.firstAbstractRoleId() != null) rule.setFirstAbstractRoleId(req.firstAbstractRoleId());
        if (req.secondAbstractRoleId() != null) rule.setSecondAbstractRoleId(req.secondAbstractRoleId());
        if (req.description() != null) rule.setDescription(req.description());
        rule.setUpdatedAt(LocalDateTime.now());
        conflictRuleMapper.update(rule);
        return toConflictRuleResp(rule);
    }

    /**
     * 检测权限冲突
     * <p>
     * 根据给定的两个操作权限ID检测是否存在冲突规则。
     * 支持双向匹配：如果规则定义了(A,B)冲突，则(A,B)和(B,A)都视为冲突。
     * 可按资源类型过滤冲突规则。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      冲突检测请求，包含两个操作权限ID和可选的资源类型
     * @return 冲突检测结果，包含是否冲突和匹配的冲突规则列表
     */
    @Override
    public ConflictDetectResp detectConflictRule(Long tenantId, ConflictRuleDetectReq req) {
        Integer resourceTypeValue = req.resourceTypeValue();
        List<PermissionConflictRule> rules = conflictRuleMapper.selectByTenantAndResourceType(tenantId, resourceTypeValue);
        List<ConflictRuleResp> matched = rules.stream().filter(rule ->
            (Objects.equals(rule.getFirstOperationPermissionId(), req.firstOperationPermissionId())
                && Objects.equals(rule.getSecondOperationPermissionId(), req.secondOperationPermissionId()))
                || (Objects.equals(rule.getFirstOperationPermissionId(), req.secondOperationPermissionId())
                && Objects.equals(rule.getSecondOperationPermissionId(), req.firstOperationPermissionId()))
        ).map(this::toConflictRuleResp).collect(Collectors.toList());
        return new ConflictDetectResp(!matched.isEmpty(), matched);
    }

    /**
     * 删除单个权限冲突规则
     * <p>
     * 软删除指定的权限冲突规则。
     * 需要CONFLICT_RULE_DELETE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ruleId     冲突规则ID
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConflictRule(Long tenantId, Long ruleId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, ruleId, OperationCodeConstants.DELETE)) {
            throw new SecurityException("Permission denied: DELETE on CONFLICT_RULE:" + ruleId);
        }

        PermissionConflictRule rule = conflictRuleMapper.selectOneById(ruleId);
        if (rule != null && rule.getDeleteFlag() == 0L && rule.getTenantId().equals(tenantId)) {
            rule.setDeleteFlag(rule.getId());
            rule.setDeletedAt(LocalDateTime.now());
            conflictRuleMapper.update(rule);
        }
    }

    /**
     * 批量删除权限冲突规则
     * <p>
     * 批量软删除权限冲突规则。
     * 使用批量查询和批量软删除避免N+1问题。
     * 需要CONFLICT_RULE_DELETE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        冲突规则ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConflictRulesByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (ids == null || ids.isEmpty()) return;

        Set<Long> validInputIds = ids.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (validInputIds.isEmpty()) return;

        engine.validateBatch(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, validInputIds, OperationCodeConstants.DELETE);

        List<PermissionConflictRule> entities = conflictRuleMapper.selectValidByIds(tenantId, validInputIds);
        if (entities.isEmpty()) return;

        Set<Long> validIds = entities.stream().map(PermissionConflictRule::getId).collect(Collectors.toSet());
        // 批量软删除（性能优化：使用单条SQL代替循环）
        LocalDateTime now = LocalDateTime.now();
        conflictRuleMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);

        operationLogDomainService.asyncRecord(
            "perm", "conflict-rule-remove", "BATCH", tenantId,
            "soft-deleted " + validIds.size() + " permission_conflict_rule row(s), ids=" + validIds,
            operatorId, null, null, tenantId
        );
    }

    /**
     * 将PermissionConflictRule实体转换为响应对象
     *
     * @param r 权限冲突规则实体
     * @return 冲突规则响应对象
     */
    private ConflictRuleResp toConflictRuleResp(PermissionConflictRule r) {
        return new ConflictRuleResp(
            r.getId(), r.getTenantId(), r.getConflictType(),
            r.getFirstOperationPermissionId(), r.getSecondOperationPermissionId(),
            r.getResourceTypeValue(), r.getFirstAbstractRoleId(), r.getSecondAbstractRoleId(),
            r.getDescription(), r.getCreatedAt()
        );
    }
}