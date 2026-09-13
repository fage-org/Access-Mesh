package cn.ac.fage.accessmesh.access.rule.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.rule.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.access.rule.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.access.rule.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.access.rule.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.access.rule.dto.resp.ConflictRuleResp;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.rule.enums.ConflictType;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.rule.service.ConflictRuleAppService;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorUtil;
import com.mybatisflex.core.util.UpdateEntity;
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
 * 批量删除采用批量软删除策略，避免N+1查询问题。
 * </p>
 * <p>
 * 门禁语义（T-PERM-030 口径）：读 list/detail/detect 与写 create/update/remove 均为
 * CONFLICT_RULE 类型级（scope_all）——CONFLICT_RULE 无 resource_entity 实例投影，
 * 实例级授权无从配置（role_resource_permission.resource_entity_id 引用 resource_entity.id
 * 空间），原「编码轨传内部 id」的实例级声称系 ID 空间错位已废弃（T-PERM-029/030 同口径；
 * CONDITION 已随 T-PERM-048 落地实例投影与实例级写门禁，CONFLICT_RULE 维持类型级——
 * 其投影与门禁升级如需另立任务）。remove 类型级全有或全无；幽灵 id 静默跳过不进入门禁。
 * </p>
 * <p>
 * ID 顺序规范化：create/update 写库时保证 first_id &lt; second_id
 * （对齐 schema 注释「存库时 first_id &lt; second_id」），使唯一索引
 * uk_conflict_rule_perm/role 正确去重，并简化双向匹配语义。
 * </p>
 * <p>
 * update 全量覆盖：按 conflictType 用 UpdateEntity 显式写入对应字段集
 * （对侧强制 null、resourceTypeValue 可清空），解决 if(field!=null) 语义
 * 无法清空字段的问题（类型切换脏数据 / 资源类型清空无效）。
 * </p>
 */
@Service
public class ConflictRuleAppServiceImpl implements ConflictRuleAppService {

    private static final String ROLE_MUTEX = "ROLE_MUTEX";
    private static final String PERM_MUTEX = "PERM_MUTEX";

    /** 20063 message 中冲突用户 id 清单的截断上限 */
    private static final int EXISTING_HOLDERS_MESSAGE_LIMIT = 20;

    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final PermQueryEngine engine;
    private final PermissionConflictDomainService permissionConflictDomainService;
    private final cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper abstractRoleMapper;
    private final cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService typeResolutionService;

    /**
     * 构造函数注入依赖
     *
     * @param conflictRuleMapper             权限冲突规则数据访问层
     * @param engine                         权限查询引擎
     * @param permissionConflictDomainService 权限冲突领域服务（T-PERM-063 存量双持守卫）
     */
    public ConflictRuleAppServiceImpl(PermissionConflictRuleMapper conflictRuleMapper,
                                      PermQueryEngine engine,
                                      PermissionConflictDomainService permissionConflictDomainService,
                                      cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper abstractRoleMapper,
                                      cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService typeResolutionService) {
        this.conflictRuleMapper = conflictRuleMapper;
        this.engine = engine;
        this.permissionConflictDomainService = permissionConflictDomainService;
        this.abstractRoleMapper = abstractRoleMapper;
        this.typeResolutionService = typeResolutionService;
    }

    /**
     * 结构角色对拒绝（T-PERM-064）：ROLE_MUTEX 规则角色对不得为 ORG/POSITION 类型——
     * 本地投影通道（组织/岗位成员关系投影）只写 ORG/POSITION 目标行，规则面拒绝后
     * 该通道结构性造不出违规持有；UI 选择器本就只提供 BASIC_ROLE/GROUP_ROLE。
     * 角色行缺失时不拒（维持既有惰性规则语义，存在性校验属存量观察不随本任务收口）。
     */
    private void rejectStructuralRolePair(Long tenantId, Long firstRoleId, Long secondRoleId) {
        List<cn.ac.fage.accessmesh.access.role.entity.AbstractRole> roles = abstractRoleMapper.selectValidByIds(
            tenantId, java.util.Set.of(firstRoleId, secondRoleId));
        if (roles.size() < 2) {
            return;
        }
        Set<Integer> structuralTypes = new java.util.HashSet<>(
            typeResolutionService.batchResolveTypeValues(tenantId, "role_type",
                java.util.Set.of("ORG", "POSITION")).values());
        boolean structural = roles.stream().anyMatch(r -> structuralTypes.contains(r.getRoleType()));
        if (structural) {
            throw new BizException(AccessErrorCode.VALIDATION_FAILED.getCode(),
                "角色互斥规则不支持 ORG/POSITION 结构角色对（结构角色由组织/岗位成员关系投影维护，"
                    + "请改用功能角色）");
        }
    }

    /**
     * 存量双持守卫（T-PERM-063）：ROLE_MUTEX 规则 create/update 写入前检查——
     * 存在同时持有两角色的用户即拒绝 20063（message 含冲突用户 id 清单，截断上限 20），
     * 管理员先解绑再立规；立规后系统内无违规持有，运行时双删不再是常态兜底。
     */
    private void rejectExistingMutexHolders(Long tenantId, Long firstRoleId, Long secondRoleId) {
        List<Long> holders = permissionConflictDomainService.findUsersHoldingBothRoles(
            tenantId, firstRoleId, secondRoleId);
        if (holders.isEmpty()) {
            return;
        }
        List<Long> shown = holders.size() > EXISTING_HOLDERS_MESSAGE_LIMIT
            ? holders.subList(0, EXISTING_HOLDERS_MESSAGE_LIMIT) : holders;
        throw new BizException(AccessErrorCode.ROLE_MUTEX_EXISTING_HOLDERS.getCode(),
            "users holding both roles (" + holders.size() + " total): " + shown
                + (holders.size() > shown.size() ? " ..." : ""));
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
            throw new BizException(AccessErrorCode.VALIDATION_FAILED.getCode(),
                "conflictType 仅允许 ROLE_MUTEX 或 PERM_MUTEX");
        }
        if (ROLE_MUTEX.equals(conflictType)) {
            if (firstRole == null || secondRole == null) {
                throw new BizException(AccessErrorCode.VALIDATION_FAILED.getCode(),
                    "角色互斥需指定两个角色");
            }
            if (firstRole.equals(secondRole)) {
                throw new BizException(AccessErrorCode.VALIDATION_FAILED.getCode(),
                    "两个角色不能相同");
            }
        } else {
            if (firstOp == null || secondOp == null) {
                throw new BizException(AccessErrorCode.VALIDATION_FAILED.getCode(),
                    "权限互斥需指定两个操作权限");
            }
            if (firstOp.equals(secondOp)) {
                throw new BizException(AccessErrorCode.VALIDATION_FAILED.getCode(),
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
     * 类型级 CONFLICT_RULE:CREATE 门禁。
     * 对象对写入前规范化为 first&lt;second 顺序。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含冲突类型、两个操作权限ID等
     * @param operatorId 操作者ID，可选
     * @return 创建的冲突规则响应
     * @throws SecurityException 无权限时抛出
     * @throws BizException      字段校验失败或等价规则已存在（20032）时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "CONFLICT_RULE_CREATE", targetType = "permission_conflict_rule", targetId = "#result.id()", summary = "'create conflict rule'")
    public ConflictRuleResp createConflictRule(Long tenantId, ConflictRuleReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, null, OperationCode.CREATE)) {
            throw new SecurityException("Permission denied: CREATE on CONFLICT_RULE");
        }

        validateFields(req.conflictType(), req.firstOperationPermissionId(), req.secondOperationPermissionId(),
            req.firstAbstractRoleId(), req.secondAbstractRoleId());

        if (isDuplicate(tenantId, req.conflictType(), req.firstOperationPermissionId(), req.secondOperationPermissionId(),
            req.firstAbstractRoleId(), req.secondAbstractRoleId(),
            ROLE_MUTEX.equals(req.conflictType()) ? null : req.resourceTypeValue(), null)) {
            throw new BizException(AccessErrorCode.CONFLICT_RULE_DUPLICATE.getCode(), "等价冲突规则已存在");
        }

        // T-PERM-063：ROLE_MUTEX 存量守卫——有用户同时持有两角色则拒绝立规（20063）；
        // T-PERM-064：结构角色对拒绝（ORG/POSITION——投影通道闭合）
        if (ROLE_MUTEX.equals(req.conflictType())) {
            rejectStructuralRolePair(tenantId, req.firstAbstractRoleId(), req.secondAbstractRoleId());
            rejectExistingMutexHolders(tenantId, req.firstAbstractRoleId(), req.secondAbstractRoleId());
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
                throw new BizException(AccessErrorCode.CONFLICT_RULE_DUPLICATE.getCode(), "等价冲突规则已存在");
            }
            throw e;
        }
        return toConflictRuleResp(rule);
    }

    /**
     * 获取权限冲突规则详情
     * <p>
     * 根据规则ID查询权限冲突规则的完整信息。
     * 类型级 CONFLICT_RULE:VIEW 门禁（T-PERM-030）。
     * 查不到抛 20020（T-PERM-030 收紧，原 data=null 宽松语义删除，
     * 对齐 T-PERM-028 resource-entity/detail 与 T-PERM-029 condition/detail 定案）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param ruleId   冲突规则ID
     * @return 冲突规则响应
     * @throws SecurityException 无 VIEW 权限时抛出
     * @throws BizException      规则不存在（20020）时抛出
     */
    @Override
    public ConflictRuleResp getConflictRule(Long tenantId, Long ruleId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, null, OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on CONFLICT_RULE");
        }
        PermissionConflictRule rule = conflictRuleMapper.selectValidById(ruleId, tenantId);
        if (rule == null) {
            throw new BizException(AccessErrorCode.CONFLICT_RULE_NOT_FOUND.getCode(),
                "Conflict rule not found: " + ruleId);
        }
        return toConflictRuleResp(rule);
    }

    /**
     * 查询权限冲突规则列表
     * <p>
     * 查询租户下所有活跃的权限冲突规则。全量不分页（量小，非流水表，
     * 对齐 T-PERM-029 condition / T-PERM-026 domain-config「量小不分页」定案），
     * 类型/关键词过滤由前端本地完成。
     * 类型级 CONFLICT_RULE:VIEW 门禁（T-PERM-030）。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 冲突规则响应列表
     * @throws SecurityException 无 VIEW 权限时抛出
     */
    @Override
    public List<ConflictRuleResp> listConflictRules(Long tenantId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, null, OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on CONFLICT_RULE");
        }
        return conflictRuleMapper.selectByTenantId(tenantId).stream().map(this::toConflictRuleResp).collect(Collectors.toList());
    }

    /**
     * 更新权限冲突规则
     * <p>
     * 先按 id 解析规则（selectValidById 已含租户 + delete_flag=0 过滤，
     * 查不到抛 20020），再做类型级 CONFLICT_RULE:UPDATE 门禁。
     * 按 conflictType 全量覆盖对应字段集（UpdateEntity 显式 set）：
     * 对侧字段强制 null，resourceTypeValue 在 PERM_MUTEX 下直接覆盖（null=全部，可清空）。
     * 解决原 if(field!=null) 语义无法清空字段的问题（类型切换脏数据 / 资源类型清空无效）。
     * 对象对写入前规范化为 first&lt;second 顺序，updatedBy/updatedAt 随写。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含规则ID和要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的冲突规则响应
     * @throws SecurityException 无权限时抛出
     * @throws BizException      规则不存在（20020）、字段校验失败或等价规则已存在（20032）时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "CONFLICT_RULE_UPDATE", targetType = "permission_conflict_rule", targetId = "#req.id()", summary = "'update conflict rule ' + #req.id()")
    public ConflictRuleResp updateConflictRule(Long tenantId, ConflictRuleUpdateReq req, Long operatorId) {
        // 先解析后门禁（T-PERM-029 模式：未知键 20020 优先于权限拒绝，零副作用）
        PermissionConflictRule rule = conflictRuleMapper.selectValidById(req.id(), tenantId);
        if (rule == null) {
            throw new BizException(AccessErrorCode.CONFLICT_RULE_NOT_FOUND.getCode(),
                "Conflict rule not found: " + req.id());
        }
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        // 类型级门禁（T-PERM-030 口径收窄，同 T-PERM-029 CONDITION）：CONFLICT_RULE 无
        // resource_entity 实例投影，实例级授权无从配置，与 OPERATION/CONDITION 同款类型级
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, null, OperationCode.UPDATE)) {
            throw new SecurityException("Permission denied: UPDATE on CONFLICT_RULE:" + req.id());
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
            ROLE_MUTEX.equals(conflictType) ? null : req.resourceTypeValue(), req.id())) {
            throw new BizException(AccessErrorCode.CONFLICT_RULE_DUPLICATE.getCode(), "等价冲突规则已存在");
        }

        // T-PERM-063：ROLE_MUTEX 存量守卫——有用户同时持有两角色则拒绝改规（20063）；
        // 同对重写幂等：系统干净时持有清单为空自然放行；
        // T-PERM-064：结构角色对拒绝（ORG/POSITION——投影通道闭合）
        if (ROLE_MUTEX.equals(conflictType)) {
            rejectStructuralRolePair(tenantId, firstRole, secondRole);
            rejectExistingMutexHolders(tenantId, firstRole, secondRole);
        }

        // UpdateEntity 全量覆盖（T-PERM-030 从 UpdateChain 对齐 T-PERM-028 extraClear 标准方式）：
        // 按 conflictType 写入对应字段集（规范化顺序），对侧强制 null。
        // PERM_MUTEX 下 resourceTypeValue 直接用 req 值（null=全部，可清空）。
        PermissionConflictRule patch = UpdateEntity.of(PermissionConflictRule.class);
        patch.setId(req.id());
        patch.setConflictType(conflictType);
        patch.setUpdatedAt(LocalDateTime.now());
        patch.setUpdatedBy(operatorId);
        if (ROLE_MUTEX.equals(conflictType)) {
            long[] pair = normalizePair(firstRole, secondRole);
            patch.setFirstAbstractRoleId(pair[0]);
            patch.setSecondAbstractRoleId(pair[1]);
            patch.setFirstOperationPermissionId(null);
            patch.setSecondOperationPermissionId(null);
            patch.setResourceTypeValue(null);
        } else {
            long[] pair = normalizePair(firstOp, secondOp);
            patch.setFirstOperationPermissionId(pair[0]);
            patch.setSecondOperationPermissionId(pair[1]);
            patch.setResourceTypeValue(req.resourceTypeValue());
            patch.setFirstAbstractRoleId(null);
            patch.setSecondAbstractRoleId(null);
        }
        if (req.description() != null) {
            patch.setDescription(req.description());
        }

        try {
            conflictRuleMapper.update(patch);
        } catch (DataIntegrityViolationException e) {
            if (isConflictRuleUniqueViolation(e)) {
                throw new BizException(AccessErrorCode.CONFLICT_RULE_DUPLICATE.getCode(), "等价冲突规则已存在");
            }
            throw e;
        }

        // 极小并发窗口内（本事务外）规则被并发软删时 re-select 可为 null——按 20020 收口而非 NPE 500
        PermissionConflictRule updated = conflictRuleMapper.selectValidById(req.id(), tenantId);
        if (updated == null) {
            throw new BizException(AccessErrorCode.CONFLICT_RULE_NOT_FOUND.getCode(),
                "Conflict rule not found: " + req.id());
        }
        return toConflictRuleResp(updated);
    }

    /**
     * 检测权限冲突
     * <p>
     * 两种形态二选一（T-PERM-063 扩展）：
     * <ul>
     *   <li>操作权限对（PERM_MUTEX 场景）：按给定的两个操作权限ID检测冲突规则，
     *       支持双向匹配；可按资源类型过滤，NULL 全局规则始终参与（schema「NULL=所有」语义）。</li>
     *   <li>角色对（ROLE_MUTEX 场景）：检测当前有效角色集同时含两角色的存量用户
     *       （conflictedUserIds 回传，conflictDetected = 清单非空——立规前预检语义，
     *       非空 = create/update 将被 20063 存量守卫拒绝）；同时双向匹配既有规则回传 matchedRules。</li>
     * </ul>
     * 两对都传或都不传拒绝 VALIDATION_FAILED。
     * 类型级 CONFLICT_RULE:VIEW 门禁（T-PERM-030，matchedRules/conflictedUserIds 同样透出数据）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      冲突检测请求（操作权限对或角色对，二选一）
     * @return 冲突检测结果（conflictDetected + matchedRules + conflictedUserIds）
     * @throws SecurityException 无 VIEW 权限时抛出
     * @throws BizException      请求形态不合法（两对都传/都不传）时抛出
     */
    @Override
    public ConflictDetectResp detectConflictRule(Long tenantId, ConflictRuleDetectReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, null, OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on CONFLICT_RULE");
        }

        boolean firstOpPresent = req.firstOperationPermissionId() != null;
        boolean secondOpPresent = req.secondOperationPermissionId() != null;
        boolean firstRolePresent = req.firstAbstractRoleId() != null;
        boolean secondRolePresent = req.secondAbstractRoleId() != null;
        boolean opPairPresent = firstOpPresent && secondOpPresent;
        boolean rolePairPresent = firstRolePresent && secondRolePresent;
        // 形态完整性 fail-closed（双轨评审 P2-2/P3-1）：任一字段出现即要求配对字段同现
        // （半传拒绝，静默忽略会与「两端必填」契约矛盾），且两对恰现其一
        if ((firstOpPresent != secondOpPresent) || (firstRolePresent != secondRolePresent)
            || opPairPresent == rolePairPresent) {
            throw new BizException(AccessErrorCode.VALIDATION_FAILED.getCode(),
                "detect 需二选一：操作权限对（first/secondOperationPermissionId）或角色对（first/secondAbstractRoleId），且两端必填");
        }

        if (rolePairPresent) {
            if (req.firstAbstractRoleId().equals(req.secondAbstractRoleId())) {
                throw new BizException(AccessErrorCode.VALIDATION_FAILED.getCode(),
                    "两个角色不能相同");
            }
            List<PermissionConflictRule> rules = conflictRuleMapper.selectByConflictType(
                tenantId, ConflictType.ROLE_MUTEX.getValue());
            List<ConflictRuleResp> matched = rules.stream().filter(rule ->
                (Objects.equals(rule.getFirstAbstractRoleId(), req.firstAbstractRoleId())
                    && Objects.equals(rule.getSecondAbstractRoleId(), req.secondAbstractRoleId()))
                || (Objects.equals(rule.getFirstAbstractRoleId(), req.secondAbstractRoleId())
                    && Objects.equals(rule.getSecondAbstractRoleId(), req.firstAbstractRoleId()))
            ).map(this::toConflictRuleResp).collect(Collectors.toList());
            List<Long> conflictedUserIds = permissionConflictDomainService.findUsersHoldingBothRoles(
                tenantId, req.firstAbstractRoleId(), req.secondAbstractRoleId());
            return new ConflictDetectResp(!conflictedUserIds.isEmpty(), matched, conflictedUserIds);
        }

        Integer resourceTypeValue = req.resourceTypeValue();
        List<PermissionConflictRule> rules = conflictRuleMapper.selectByTenantAndResourceType(tenantId, resourceTypeValue);
        List<ConflictRuleResp> matched = rules.stream().filter(rule ->
            (Objects.equals(rule.getFirstOperationPermissionId(), req.firstOperationPermissionId())
                && Objects.equals(rule.getSecondOperationPermissionId(), req.secondOperationPermissionId()))
                || (Objects.equals(rule.getFirstOperationPermissionId(), req.secondOperationPermissionId())
                && Objects.equals(rule.getSecondOperationPermissionId(), req.firstOperationPermissionId()))
        ).map(this::toConflictRuleResp).collect(Collectors.toList());
        return new ConflictDetectResp(!matched.isEmpty(), matched, List.of());
    }

    /**
     * 批量删除权限冲突规则
     * <p>
     * 按规则ID集合批量软删除（T-PERM-030：单删孤儿方法已删除，Controller 仅调本批量版）。
     * 先批量解析有效实体（一次 SQL），不存在/已删除的 id 静默跳过（幂等语义，
     * 与 resource-entity/remove、condition/remove 一致），再对整批做类型级
     * CONFLICT_RULE:DELETE 门禁（全有或全无），最后批量软删除。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        冲突规则ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无类型级 DELETE 权限时抛出（fail-closed 整批不变更）
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

        List<PermissionConflictRule> entities = conflictRuleMapper.selectValidByIds(tenantId, validInputIds);
        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validIds = entities.stream().map(PermissionConflictRule::getId).collect(Collectors.toSet());

        // 类型级门禁（T-PERM-030 口径收窄，同 T-PERM-029 CONDITION）：CONFLICT_RULE 无实例投影，
        // 类型级全有或全无——幽灵 id 已在解析阶段静默跳过，不进入门禁
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, null, OperationCode.DELETE)) {
            throw new SecurityException("Permission denied: DELETE on CONFLICT_RULE:" + validIds);
        }

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
            r.getDescription(), r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
