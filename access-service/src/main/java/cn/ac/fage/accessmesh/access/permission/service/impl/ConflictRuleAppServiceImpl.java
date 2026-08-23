package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConflictRuleResp;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.permission.entity.table.PermissionConflictRuleTableDef;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.permission.service.ConflictRuleAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import com.mybatisflex.core.update.UpdateChain;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * <p>
 * ID 顺序规范化：create/update 写库时保证 first_id &lt; second_id
 * （对齐 schema 注释「存库时 first_id &lt; second_id」），使唯一索引
 * uk_conflict_rule_perm/role 正确去重，并简化双向匹配语义。
 * </p>
 * <p>
 * update 全量覆盖：按 conflictType 用 UpdateChain 显式写入对应字段集
 * （对侧强制 null、resourceTypeValue 可清空），解决 if(field!=null) 语义
 * 无法清空字段的问题（类型切换脏数据 / 资源类型清空无效）。
 * </p>
 */
@Service
public class ConflictRuleAppServiceImpl implements ConflictRuleAppService {

    private static final String ROLE_MUTEX = "ROLE_MUTEX";
    private static final String PERM_MUTEX = "PERM_MUTEX";

    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param conflictRuleMapper 权限冲突规则数据访问层
     * @param engine             权限查询引擎
     */
    public ConflictRuleAppServiceImpl(PermissionConflictRuleMapper conflictRuleMapper,
                                      PermQueryEngine engine) {
        this.conflictRuleMapper = conflictRuleMapper;
        this.engine = engine;
    }

    /**
     * 校验冲突规则字段与类型一致性。
     * <p>
     * 规范化的前提：保证两个对象 ID 非 null 才能排序。
     * </p>
     * <ul>
     *   <li>conflictType 仅允许 ROLE_MUTEX / PERM_MUTEX</li>
     *   <li>ROLE_MUTEX：两个角色 ID 必填且不同</li>
     *   <li>PERM_MUTEX：两个操作权限 ID 必填且不同</li>
     * </ul>
     *
     * @throws BizException 字段不满足约束时抛出
     */
    private void validateFields(String conflictType, Long firstOp, Long secondOp,
                                Long firstRole, Long secondRole) {
        if (!ROLE_MUTEX.equals(conflictType) && !PERM_MUTEX.equals(conflictType)) {
            throw new BizException(PermissionErrorCode.VALIDATION_FAILED.getCode(),
                "conflictType 仅允许 ROLE_MUTEX 或 PERM_MUTEX");
        }
        if (ROLE_MUTEX.equals(conflictType)) {
            if (firstRole == null || secondRole == null) {
                throw new BizException(PermissionErrorCode.VALIDATION_FAILED.getCode(),
                    "角色互斥需指定两个角色");
            }
            if (firstRole.equals(secondRole)) {
                throw new BizException(PermissionErrorCode.VALIDATION_FAILED.getCode(),
                    "两个角色不能相同");
            }
        } else {
            if (firstOp == null || secondOp == null) {
                throw new BizException(PermissionErrorCode.VALIDATION_FAILED.getCode(),
                    "权限互斥需指定两个操作权限");
            }
            if (firstOp.equals(secondOp)) {
                throw new BizException(PermissionErrorCode.VALIDATION_FAILED.getCode(),
                    "两个操作权限不能相同");
            }
        }
    }

    /**
     * 规范化对象对顺序：first = min(a,b), second = max(a,b)。
     * <p>
     * 对齐 schema 注释「存库时 first_id &lt; second_id」，使唯一索引生效。
     * 调用前需保证 a/b 非 null（由 {@link #validateFields} 校验）。
     * </p>
     */
    private static long[] normalizePair(long a, long b) {
        return a <= b ? new long[]{a, b} : new long[]{b, a};
    }

    /**
     * 判定是否存在语义等价的冲突规则（同类型 + 同对象对双向匹配 + 同 resourceTypeValue）。
     * <p>
     * 用于 create/update 去重，对齐 Mock isDuplicate。
     * 规范化后对象对双向等价，双向匹配为兼容未规范化历史数据保留。
     * NULL resourceTypeValue 的全局规则参与去重（Objects.equals(null,null)=true），
     * 弥补 PG 唯一索引默认 NULL!=NULL 的缺口（B2 方案：业务层去重）。
     * </p>
     *
     * @param excludeId 排除的规则ID（update 传当前规则ID，create 传 null）
     * @return true 表示存在等价规则
     */
    private boolean isDuplicate(Long tenantId, String conflictType, Long firstOp, Long secondOp,
                                Long firstRole, Long secondRole, Integer resourceTypeValue,
                                Long excludeId) {
        Long a = ROLE_MUTEX.equals(conflictType) ? firstRole : firstOp;
        Long b = ROLE_MUTEX.equals(conflictType) ? secondRole : secondOp;
        return conflictRuleMapper.selectByTenantId(tenantId).stream().anyMatch(r -> {
            if (r.getId().equals(excludeId) || !conflictType.equals(r.getConflictType())) {
                return false;
            }
            if (!Objects.equals(r.getResourceTypeValue(), resourceTypeValue)) {
                return false;
            }
            Long ra = ROLE_MUTEX.equals(conflictType) ? r.getFirstAbstractRoleId() : r.getFirstOperationPermissionId();
            Long rb = ROLE_MUTEX.equals(conflictType) ? r.getSecondAbstractRoleId() : r.getSecondOperationPermissionId();
            return (Objects.equals(ra, a) && Objects.equals(rb, b))
                || (Objects.equals(ra, b) && Objects.equals(rb, a));
        });
    }

    /**
     * 判定 DataIntegrityViolationException 是否由冲突规则唯一约束违反引起。
     * <p>
     * PG 唯一约束违反消息含约束名（uk_conflict_rule_perm / uk_conflict_rule_role），
     * 用于并发场景下 isDuplicate 失效时的兜底（NULLS NOT DISTINCT 使 NULL 全局规则也受约束）。
     * </p>
     */
    private boolean isConflictRuleUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("uk_conflict_rule_perm") || msg.contains("uk_conflict_rule_role"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 创建权限冲突规则
     * <p>
     * 创建新的权限冲突规则定义。
     * 冲突规则指定两个操作权限的组合被视为冲突，
     * 可限定于特定资源类型或角色。
     * 需要CONFLICT_RULE_CREATE权限。
     * 对象对写入前规范化为 first&lt;second 顺序。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含冲突类型、两个操作权限ID等
     * @param operatorId 操作者ID，可选
     * @return 创建的冲突规则响应
     * @throws SecurityException 无权限时抛出
     * @throws BizException      字段校验失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "CONFLICT_RULE_CREATE", targetType = "permission_conflict_rule", targetId = "#result.id()", summary = "'create conflict rule'")
    public ConflictRuleResp createConflictRule(Long tenantId, ConflictRuleReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("Permission denied: CREATE on CONFLICT_RULE");
        }

        validateFields(req.conflictType(), req.firstOperationPermissionId(), req.secondOperationPermissionId(),
            req.firstAbstractRoleId(), req.secondAbstractRoleId());

        if (isDuplicate(tenantId, req.conflictType(), req.firstOperationPermissionId(), req.secondOperationPermissionId(),
            req.firstAbstractRoleId(), req.secondAbstractRoleId(), req.resourceTypeValue(), null)) {
            throw new BizException(PermissionErrorCode.CONFLICT_RULE_DUPLICATE.getCode(), "等价冲突规则已存在");
        }

        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setTenantId(tenantId);
        rule.setConflictType(req.conflictType());
        rule.setDescription(req.description());
        rule.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rule.setDeleteFlag(0L);
        if (ROLE_MUTEX.equals(req.conflictType())) {
            long[] pair = normalizePair(req.firstAbstractRoleId(), req.secondAbstractRoleId());
            rule.setFirstAbstractRoleId(pair[0]);
            rule.setSecondAbstractRoleId(pair[1]);
        } else {
            long[] pair = normalizePair(req.firstOperationPermissionId(), req.secondOperationPermissionId());
            rule.setFirstOperationPermissionId(pair[0]);
            rule.setSecondOperationPermissionId(pair[1]);
            rule.setResourceTypeValue(req.resourceTypeValue());
        }
        try {
            conflictRuleMapper.insert(rule);
        } catch (DataIntegrityViolationException e) {
            if (isConflictRuleUniqueViolation(e)) {
                throw new BizException(PermissionErrorCode.CONFLICT_RULE_DUPLICATE.getCode(), "等价冲突规则已存在");
            }
            throw e;
        }
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
     * 按 conflictType 全量覆盖对应字段集（UpdateChain 显式 set）：
     * 对侧字段强制 null，resourceTypeValue 在 PERM_MUTEX 下直接覆盖（null=全部，可清空）。
     * 解决原 if(field!=null) 语义无法清空字段的问题（类型切换脏数据 / 资源类型清空无效）。
     * 对象对写入前规范化为 first&lt;second 顺序。
     * 需要CONFLICT_RULE_UPDATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含规则ID和要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的冲突规则响应
     * @throws SecurityException 无权限时抛出
     * @throws BizException      规则不存在或字段校验失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "CONFLICT_RULE_UPDATE", targetType = "permission_conflict_rule", targetId = "#req.id()", summary = "'update conflict rule ' + #req.id()")
    public ConflictRuleResp updateConflictRule(Long tenantId, ConflictRuleUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, String.valueOf(req.id()), OperationCodeConstants.UPDATE)) {
            throw new SecurityException("Permission denied: UPDATE on CONFLICT_RULE:" + req.id());
        }

        PermissionConflictRule rule = conflictRuleMapper.selectOneById(req.id());
        if (rule == null || rule.getDeleteFlag() != 0L || !tenantId.equals(rule.getTenantId())) {
            throw new BizException(PermissionErrorCode.CONFLICT_RULE_NOT_FOUND.getCode(), "Conflict rule not found: " + req.id());
        }

        // 合并出最终状态（op/role 未传字段保留原值，支持部分更新），校验 + 规范化。
        // resourceTypeValue 不合并：PERM_MUTEX 下直接用 req 值（null=清空"全部"），
        // 调用方须传完整字段集（前端 buildPayload 保证）。
        String conflictType = req.conflictType() != null ? req.conflictType() : rule.getConflictType();
        Long firstOp = req.firstOperationPermissionId() != null ? req.firstOperationPermissionId() : rule.getFirstOperationPermissionId();
        Long secondOp = req.secondOperationPermissionId() != null ? req.secondOperationPermissionId() : rule.getSecondOperationPermissionId();
        Long firstRole = req.firstAbstractRoleId() != null ? req.firstAbstractRoleId() : rule.getFirstAbstractRoleId();
        Long secondRole = req.secondAbstractRoleId() != null ? req.secondAbstractRoleId() : rule.getSecondAbstractRoleId();

        validateFields(conflictType, firstOp, secondOp, firstRole, secondRole);

        if (isDuplicate(tenantId, conflictType, firstOp, secondOp, firstRole, secondRole,
            req.resourceTypeValue(), req.id())) {
            throw new BizException(PermissionErrorCode.CONFLICT_RULE_DUPLICATE.getCode(), "等价冲突规则已存在");
        }

        // UpdateChain 全量覆盖：按 conflictType 写入对应字段集（规范化顺序），对侧强制 null。
        // PERM_MUTEX 下 resourceTypeValue 直接用 req 值（null=全部，可清空）。
        PermissionConflictRuleTableDef t = PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE;
        UpdateChain<PermissionConflictRule> chain = UpdateChain.of(conflictRuleMapper)
            .set(t.CONFLICT_TYPE, conflictType, true)
            .set(t.UPDATED_AT, LocalDateTime.now(), true);

        if (ROLE_MUTEX.equals(conflictType)) {
            long[] pair = normalizePair(firstRole, secondRole);
            chain.set(t.FIRST_ABSTRACT_ROLE_ID, pair[0], true)
                .set(t.SECOND_ABSTRACT_ROLE_ID, pair[1], true)
                .set(t.FIRST_OPERATION_PERMISSION_ID, null, true)
                .set(t.SECOND_OPERATION_PERMISSION_ID, null, true)
                .set(t.RESOURCE_TYPE_VALUE, null, true);
        } else {
            long[] pair = normalizePair(firstOp, secondOp);
            chain.set(t.FIRST_OPERATION_PERMISSION_ID, pair[0], true)
                .set(t.SECOND_OPERATION_PERMISSION_ID, pair[1], true)
                .set(t.RESOURCE_TYPE_VALUE, req.resourceTypeValue(), true)
                .set(t.FIRST_ABSTRACT_ROLE_ID, null, true)
                .set(t.SECOND_ABSTRACT_ROLE_ID, null, true);
        }
        if (req.description() != null) {
            chain.set(t.DESCRIPTION, req.description(), true);
        }

        try {
            chain.where(t.ID.eq(req.id()))
                .and(t.TENANT_ID.eq(tenantId))
                .update();
        } catch (DataIntegrityViolationException e) {
            if (isConflictRuleUniqueViolation(e)) {
                throw new BizException(PermissionErrorCode.CONFLICT_RULE_DUPLICATE.getCode(), "等价冲突规则已存在");
            }
            throw e;
        }

        return toConflictRuleResp(conflictRuleMapper.selectValidById(req.id(), tenantId));
    }

    /**
     * 检测权限冲突
     * <p>
     * 根据给定的两个操作权限ID检测是否存在冲突规则。
     * 支持双向匹配：如果规则定义了(A,B)冲突，则(A,B)和(B,A)都视为冲突。
     * 可按资源类型过滤冲突规则；resource_type_value IS NULL 的全局规则
     * 始终参与匹配（对齐 schema「NULL=所有」语义，由 Mapper SQL 保证）。
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
    @OperationLog(module = "PERMISSION", action = "CONFLICT_RULE_REMOVE", targetType = "permission_conflict_rule", targetId = "#ruleId", summary = "'remove conflict rule ' + #ruleId")
    public void deleteConflictRule(Long tenantId, Long ruleId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, String.valueOf(ruleId), OperationCodeConstants.DELETE)) {
            throw new SecurityException("Permission denied: DELETE on CONFLICT_RULE:" + ruleId);
        }

        PermissionConflictRule rule = conflictRuleMapper.selectOneById(ruleId);
        if (rule == null || rule.getDeleteFlag() != 0L || !tenantId.equals(rule.getTenantId())) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        rule.setDeleteFlag(rule.getId());
        rule.setDeletedAt(LocalDateTime.now());
        conflictRuleMapper.update(rule);
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
    @OperationLog(module = "PERMISSION", action = "CONFLICT_RULE_REMOVE", targetType = "permission_conflict_rule", targetId = "", summary = "'batch remove conflict rules'")
    public void deleteConflictRulesByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (ids == null || ids.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validInputIds = ids.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (validInputIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // T-PERM-042：引擎纯查询，拒绝时由调用方显式抛出
        Set<Long> deniedIds = engine.getDeniedEntityIds(
            tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, validInputIds, OperationCodeConstants.DELETE);
        if (!deniedIds.isEmpty()) {
            throw new SecurityException("Permission denied: DELETE on CONFLICT_RULE:" + deniedIds);
        }

        List<PermissionConflictRule> entities = conflictRuleMapper.selectValidByIds(tenantId, validInputIds);
        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validIds = entities.stream().map(PermissionConflictRule::getId).collect(Collectors.toSet());
        // 批量软删除（性能优化：使用单条SQL代替循环）
        LocalDateTime now = LocalDateTime.now();
        conflictRuleMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " permission_conflict_rule row(s)");
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
