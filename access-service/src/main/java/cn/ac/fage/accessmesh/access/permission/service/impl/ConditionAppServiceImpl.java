package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConditionResp;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.enums.ConditionSource;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.ConditionAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限条件管理服务实现类（管理页轨，T-PERM-048 双轨制）
 * <p>
 * 提供权限条件（PermissionCondition）的CRUD操作，全部作用于 source=MANAGED 的管理页条件；
 * source=INLINE 的内联条件由 apply-grant-plan 内联轨同事务创建/回收（{@link PermissionGrantPlanDomainServiceImpl}），
 * 本入口对 INLINE 行的 update/remove 一律 20060 拒绝（双轨管理边界互斥）。
 * 条件规则存储为JSON格式，支持复杂的条件表达式。
 * 读取（list/detail）无门禁（2026-08-08 产品确认：条件规则全租户开放、非敏感）；
 * list 缺省只回 MANAGED（管理页口径），includeInline=true 时含内联（授权页回显用）。
 * 写门禁（T-PERM-048 定案④升级）：create 维持类型级 CONDITION:CREATE（scope_all）；
 * update/delete 升实例级 CONDITION:UPDATE/DELETE@{code}（USER:MANAGE 同款；bootstrap 固定图
 * 与存量授权全为 scope_all 天然覆盖全部实例，零破坏）。
 * 删除引用守卫（T-PERM-048 定案③，20059）：condition_id 挂靠引用或投影行下实例授权引用
 * 任一命中整批拒绝，零引用才放行——挂条件的授权评估 fail-close，静默删除会使其「静默失效」。
 * MANAGED 条件创建/更新/删除同事务维护 resource_entity(CONDITION) 实例投影
 * （code=条件 code，status 镜像 enabled）；投影行支持实例级授权 CONDITION:UPDATE/DELETE@code。
 * 缓存失效操作在事务提交后执行，防止缓存被回滚数据污染。
 * 批量删除采用批量软删除策略，避免N+1查询问题。
 * 管理端点定位一律使用业务键 code（uk tenant+code，T-PERM-029 从内部主键切换）。
 * </p>
 */
@Service
public class ConditionAppServiceImpl implements ConditionAppService {

    private final PermissionConditionMapper conditionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final PermQueryEngine engine;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final PermissionConditionDomainService conditionDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param conditionMapper             权限条件数据访问层
     * @param rolePermMapper              角色资源权限数据访问层（引用守卫 + T-PERM-017 P2-A 反查 serviceCodes）
     * @param engine                      权限查询引擎
     * @param localProjectionDomainService 本地投影领域服务（CONDITION 实例投影，T-PERM-048）
     * @param conditionDomainService      条件领域服务（规则写入口径校验双轨共享）
     */
    public ConditionAppServiceImpl(PermissionConditionMapper conditionMapper,
                                       RoleResourcePermissionMapper rolePermMapper,
                                       PermQueryEngine engine,
                                       LocalProjectionDomainService localProjectionDomainService,
                                       PermissionConditionDomainService conditionDomainService) {
        this.conditionMapper = conditionMapper;
        this.rolePermMapper = rolePermMapper;
        this.engine = engine;
        this.localProjectionDomainService = localProjectionDomainService;
        this.conditionDomainService = conditionDomainService;
    }

    /**
     * 创建权限条件（管理页轨）
     * <p>
     * 创建新的管理页条件定义（source 固定 MANAGED，不接受请求指定——内联条件只能经
     * apply-grant-plan 内联轨产生）。需要 CONDITION:CREATE 类型级权限。事实行落库后
     * 同事务登记 CONDITION 实例投影（T-PERM-048）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含条件编码、名称、规则等
     * @param operatorId 操作者ID，可选
     * @return 创建的条件响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "PERMISSION_CONDITION_CREATE", targetType = "permission_condition", targetId = "#result.id()", summary = "'create permission condition ' + #req.code()")
    public ConditionResp createCondition(Long tenantId, ConditionCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONDITION, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("Permission denied: CREATE on CONDITION");
        }

        PermissionCondition condition = new PermissionCondition();
        condition.setTenantId(tenantId);
        condition.setCode(req.code());
        condition.setName(req.name());
        boolean enabled = req.enabled() != null ? req.enabled() : true;
        boolean gatewayEvaluable = req.gatewayEvaluable() != null ? req.gatewayEvaluable() : false;
        // 规则写入口径校验（JSON 语法 + gatewayEvaluable 白名单，双轨共享，T-PERM-048 收敛）
        conditionDomainService.assertConditionRulesValid(req.conditionRules(), gatewayEvaluable);
        condition.setConditionRules(req.conditionRules());
        condition.setEnabled(enabled);
        condition.setGatewayEvaluable(gatewayEvaluable);
        condition.setSource(ConditionSource.MANAGED.getValue());
        condition.setDescription(req.description());
        condition.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        condition.setCreatedAt(now);
        condition.setUpdatedAt(now);
        condition.setDeleteFlag(0L);
        conditionMapper.insert(condition);
        // CONDITION 实例投影（T-PERM-048）：同事务登记，code=条件 code，status 镜像 enabled
        localProjectionDomainService.upsertConditionResource(tenantId, condition.getCode(), condition.getName(), enabled);
        return toConditionResp(condition);
    }

    /**
     * 获取权限条件详情（管理页读面：仅 MANAGED）
     * <p>
     * 根据条件编码（业务键，T-PERM-029 从内部主键切换）查询权限条件的完整信息。
     * 读取无门禁（2026-08-08 产品确认：条件规则全租户开放、非敏感）；
     * 双轨制（T-PERM-048）：INLINE 内联条件在管理面查不到——20060 拒绝
     * （授权页回显走 list includeInline=true）。
     * </p>
     *
     * @param tenantId      租户ID
     * @param conditionCode 条件编码
     * @return 条件响应
     * @throws BizException 条件不存在（20006 CONDITION_NOT_FOUND）或为内联条件（20060）
     */
    @Override
    public ConditionResp getCondition(Long tenantId, String conditionCode) {
        PermissionCondition condition = conditionMapper.selectValidByCode(tenantId, conditionCode);
        if (condition == null) {
            throw new BizException(PermissionErrorCode.CONDITION_NOT_FOUND.getCode(),
                "Condition not found: " + conditionCode);
        }
        assertManageable(condition);
        return toConditionResp(condition);
    }

    /**
     * 更新权限条件（管理页轨）
     * <p>
     * 更新管理页条件的名称、规则、启用状态、描述等属性。
     * 门禁（T-PERM-048 定案④升级）：实例级 CONDITION:UPDATE@{code}
     * （resource_entity(CONDITION).code=条件 code；scope_all 授权天然覆盖全部实例）。
     * INLINE 内联条件 20060 拒绝（只能在授权页随记录更改）。
     * 更新后同事务镜像投影（name/enabled），并在事务提交后失效相关缓存。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，以业务键 code 定位（不可改），包含要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的条件响应
     * @throws SecurityException 无实例级 UPDATE 权限时抛出
     * @throws BizException      条件不存在（20006）/内联条件（20060）/规则不可下发（20031）时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "PERMISSION_CONDITION_UPDATE", targetType = "permission_condition", targetId = "#req.code()", summary = "'update permission condition ' + #req.code()")
    @PermissionChange
    public ConditionResp updateCondition(Long tenantId, ConditionUpdateReq req, Long operatorId) {
        // 业务键 code 定位（T-PERM-029 从内部主键切换；selectValidByCode 已含租户 + delete_flag=0 过滤）
        PermissionCondition condition = conditionMapper.selectValidByCode(tenantId, req.code());
        if (condition == null) {
            throw new BizException(PermissionErrorCode.CONDITION_NOT_FOUND.getCode(), "Condition not found: " + req.code());
        }
        // 双轨制（T-PERM-048 定案①）：内联条件只能在授权页随记录更改，管理面拒绝
        assertManageable(condition);
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        // 实例级门禁（T-PERM-048 定案④升级，USER:MANAGE 同款）：CONDITION 投影 code=条件 code；
        // scope_all 授权 passesScopeAll 全放行（bootstrap 固定图与存量授权零破坏）
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.CONDITION, req.code(), OperationCodeConstants.UPDATE)) {
            throw new SecurityException("Permission denied: UPDATE on CONDITION:" + req.code());
        }

        if (req.name() != null) condition.setName(req.name());
        if (req.conditionRules() != null) {
            condition.setConditionRules(req.conditionRules());
        }
        if (req.enabled() != null) condition.setEnabled(req.enabled());
        if (req.gatewayEvaluable() != null) condition.setGatewayEvaluable(req.gatewayEvaluable());
        if (req.description() != null) condition.setDescription(req.description());
        // T-PERM-017 C2.5：取"最终状态"联合校验——只切 flag 不改 rules 时用 DB 老 rules、
        // 同时改用新 rules、只改 rules 且已 true 也重校验（双轨共享校验，T-PERM-048 收敛）
        conditionDomainService.assertConditionRulesValid(condition.getConditionRules(),
            Boolean.TRUE.equals(condition.getGatewayEvaluable()));
        condition.setUpdatedBy(operatorId);
        condition.setUpdatedAt(LocalDateTime.now());
        conditionMapper.update(condition);
        // 投影镜像（T-PERM-048）：name/status(enabled) 随事实行同事务同步
        localProjectionDomainService.upsertConditionResource(tenantId, condition.getCode(),
            condition.getName(), Boolean.TRUE.equals(condition.getEnabled()));

        // 登记受影响条件，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
        // T-PERM-017 P2-A：条件规则变更需同步失效 Gateway 已下发的内联 conditionRules 接口快照。
        PermissionChangeContext.markConditions(tenantId, Set.of(condition.getId()));
        markServiceCodesForConditions(tenantId, Set.of(condition.getId()));
        return toConditionResp(condition);
    }

    /**
     * 查询权限条件列表（双轨制过滤）
     * <p>
     * 查询租户下所有活跃的权限条件。缺省（includeInline=null/false）只返回 MANAGED
     * 管理页条件——权限条件页口径（内联条件查不到也不能管理，T-PERM-048 定案①）；
     * includeInline=true 时含 INLINE（授权页回显内联条件名称/规则摘要）。
     * </p>
     *
     * @param tenantId      租户ID
     * @param includeInline 是否包含内联条件
     * @return 条件响应列表
     */
    @Override
    public List<ConditionResp> listConditions(Long tenantId, Boolean includeInline) {
        return conditionMapper.selectByTenantId(tenantId).stream()
            .filter(condition -> Boolean.TRUE.equals(includeInline)
                || ConditionSource.MANAGED.getValue().equals(condition.getSource()))
            .map(this::toConditionResp)
            .collect(Collectors.toList());
    }

    /**
     * 按业务键批量删除权限条件（管理页轨）
     * <p>
     * 按条件编码集合批量软删除（T-PERM-029 从内部主键 ids 切换）。请求中不存在或已删除的
     * 编码静默跳过（幂等语义，与 resource-entity/remove 一致）。
     * 顺序：批量解析编码 → INLINE 拒绝（20060，整批）→ 实例级 DELETE 门禁
     * （T-PERM-048 定案④，getDeniedResourceCodes 批量、整批全有或全无）→ 引用守卫
     * （T-PERM-048 定案③，20059：挂靠引用/投影行下实例授权任一命中整批拒绝）→
     * 条件行 + 投影行同事务软删 → 缓存失效登记。
     * </p>
     *
     * @param tenantId   租户ID
     * @param codes      条件编码列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无实例级 CONDITION:DELETE 权限时抛出（全有或全无，整批不变更）
     * @throws BizException      批内含内联条件（20060）或被授权引用（20059）时抛出（整批拒绝）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "PERMISSION_CONDITION_REMOVE", targetType = "permission_condition", targetId = "", summary = "'batch remove permission conditions by code'")
    @PermissionChange
    public void deleteConditionsByCodes(Long tenantId, List<String> codes, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (codes == null || codes.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<String> validInputCodes = codes.stream()
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (validInputCodes.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        List<PermissionCondition> entities = conditionMapper.selectValidByCodes(tenantId, validInputCodes);
        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // 双轨制（T-PERM-048 定案①）：内联条件不可管理面删除（生命周期归授权页），整批拒绝
        List<String> inlineCodes = entities.stream()
            .filter(condition -> ConditionSource.INLINE.getValue().equals(condition.getSource()))
            .map(PermissionCondition::getCode)
            .toList();
        if (!inlineCodes.isEmpty()) {
            throw new BizException(PermissionErrorCode.CONDITION_INLINE_NOT_MANAGEABLE.getCode(),
                "内联条件不可在管理面删除（只能在授权页随记录更改）: " + String.join(", ", inlineCodes));
        }

        Set<Long> validIds = entities.stream().map(PermissionCondition::getId).collect(Collectors.toSet());
        Set<String> validCodes = entities.stream().map(PermissionCondition::getCode).collect(Collectors.toSet());

        // 实例级门禁（T-PERM-048 定案④升级，deleteUsers 批量先例）：T-PERM-042 引擎纯查询，拒绝由调用方显式抛出
        Set<String> deniedCodes = engine.getDeniedResourceCodes(tenantId, operatorId,
            ResourceTypeCode.CONDITION, validCodes, OperationCodeConstants.DELETE);
        if (!deniedCodes.isEmpty()) {
            throw new SecurityException("Permission denied: DELETE on CONDITION: " + deniedCodes);
        }

        // 引用守卫①（T-PERM-048 定案③）：condition_id 挂靠引用——挂该条件的授权行评估
        // fail-close，静默删除会使授权「静默失效」，零引用才放行（整批拒绝，message 带冲突 code）
        Set<Long> referencedIds = rolePermMapper.selectReferencedConditionIds(tenantId, validIds);
        if (!referencedIds.isEmpty()) {
            List<String> conflictCodes = entities.stream()
                .filter(condition -> referencedIds.contains(condition.getId()))
                .map(PermissionCondition::getCode)
                .toList();
            throw new BizException(PermissionErrorCode.CONDITION_REFERENCED_BY_GRANTS.getCode(),
                "条件被授权记录引用，不可删除（请先解绑/换条件）: " + String.join(", ", conflictCodes));
        }
        // 引用守卫②：投影行下实例授权引用（CONDITION:UPDATE/DELETE@code 等实例级授权行悬空防护）
        List<Long> projectionIds = localProjectionDomainService.findConditionResourceIds(tenantId, validCodes);
        if (!projectionIds.isEmpty()
            && !rolePermMapper.selectValidPermIdsByResourceIds(tenantId, projectionIds).isEmpty()) {
            throw new BizException(PermissionErrorCode.CONDITION_REFERENCED_BY_GRANTS.getCode(),
                "条件存在实例级授权引用（投影行下授权行），不可删除: " + String.join(", ", validCodes));
        }

        LocalDateTime now = LocalDateTime.now();
        conditionMapper.softDeleteBatch(tenantId, new ArrayList<>(validIds), now);
        // 投影行同事务软删（引用守卫保证投影行下无有效授权行，级联面收敛为投影行本身）
        localProjectionDomainService.softDeleteConditionResources(tenantId, validCodes);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " permission_condition row(s)");

        // 登记受影响条件，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
        // T-PERM-017 P2-A：批量删除同步反查 serviceCodes 触发 Gateway 本地快照失效。
        PermissionChangeContext.markConditions(tenantId, validIds);
        markServiceCodesForConditions(tenantId, validIds);
    }

    /**
     * 双轨制管理面防线（T-PERM-048 定案①）：INLINE 内联条件在权限条件页查不到也不能管理，
     * 管理面 update/remove/detail 一律 20060 拒绝（内联条件只能在授权页随记录更改）。
     */
    private void assertManageable(PermissionCondition condition) {
        if (ConditionSource.INLINE.getValue().equals(condition.getSource())) {
            throw new BizException(PermissionErrorCode.CONDITION_INLINE_NOT_MANAGEABLE.getCode(),
                "内联条件不可在管理面管理（只能在授权页随记录更改）: " + condition.getCode());
        }
    }

    /**
     * 反查受影响条件引用的 serviceCodes 并登记进 PermissionChangeContext（T-PERM-017 P2-A）。
     * <p>
     * 用于条件 update/delete 后通知 Gateway 失效已下发的内联 conditionRules 接口快照。
     * 委托 {@link RoleResourcePermissionMapper#selectServiceCodesByConditionIds}（JOIN 一次 SQL），
     * 空结果（条件未被任何 grant 引用）→ no-op，不无谓登记。
     * </p>
     *
     * @param tenantId     租户ID
     * @param conditionIds 受影响条件ID集合（非空）
     */
    private void markServiceCodesForConditions(Long tenantId, Set<Long> conditionIds) {
        if (conditionIds == null || conditionIds.isEmpty()) return;
        Set<String> serviceCodes = rolePermMapper.selectServiceCodesByConditionIds(tenantId, conditionIds);
        if (serviceCodes != null && !serviceCodes.isEmpty()) {
            PermissionChangeContext.markServiceCodes(tenantId, serviceCodes);
        }
    }

    /**
     * 将PermissionCondition实体转换为响应对象
     *
     * @param c 权限条件实体
     * @return 条件响应对象
     */
    private ConditionResp toConditionResp(PermissionCondition c) {
        return new ConditionResp(
            c.getId(), c.getTenantId(), c.getCode(), c.getName(),
            c.getConditionRules(), c.getEnabled(),
            c.getGatewayEvaluable() != null ? c.getGatewayEvaluable() : false,
            c.getSource() != null ? c.getSource() : ConditionSource.MANAGED.getValue(),
            c.getDescription(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
