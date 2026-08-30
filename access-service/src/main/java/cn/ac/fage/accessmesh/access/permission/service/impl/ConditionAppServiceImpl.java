package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.util.ConditionEvalUtils;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConditionResp;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.ConditionAppService;
import cn.ac.fage.accessmesh.access.permission.util.JsonValidationUtils;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限条件管理服务实现类
 * <p>
 * 提供权限条件（PermissionCondition）的CRUD操作。
 * 权限条件定义了权限生效的附加约束规则，如时间范围、数据属性等。
 * 条件规则存储为JSON格式，支持复杂的条件表达式。
 * 读取（list/detail）无门禁（2026-08-08 产品确认：条件规则全租户开放、非敏感）；
 * 写操作经PermQueryEngine做CONDITION域实例级门禁（CREATE 类型级 / UPDATE、DELETE 实例级）。
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
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param conditionMapper 权限条件数据访问层
     * @param rolePermMapper  角色资源权限数据访问层（T-PERM-017 P2-A：反查受影响 serviceCodes）
     * @param engine          权限查询引擎
     * @param objectMapper    JSON 解析器（用于 gatewayEvaluable 校验时解析 conditionRules）
     */
    public ConditionAppServiceImpl(PermissionConditionMapper conditionMapper,
                                       RoleResourcePermissionMapper rolePermMapper,
                                       PermQueryEngine engine,
                                       ObjectMapper objectMapper) {
        this.conditionMapper = conditionMapper;
        this.rolePermMapper = rolePermMapper;
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建权限条件
     * <p>
     * 创建新的权限条件定义。条件包含编码、名称、规则JSON、启用状态等。
     * 需要CONDITION_CREATE权限。
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
        JsonValidationUtils.validateJson(req.conditionRules());
        condition.setConditionRules(req.conditionRules());
        condition.setEnabled(req.enabled() != null ? req.enabled() : true);
        boolean gatewayEvaluable = req.gatewayEvaluable() != null ? req.gatewayEvaluable() : false;
        // T-PERM-017 C2.5：gatewayEvaluable=true 时校验 items[].type 全部在白名单
        if (gatewayEvaluable) {
            validateGatewayPushable(req.conditionRules());
        }
        condition.setGatewayEvaluable(gatewayEvaluable);
        condition.setDescription(req.description());
        condition.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        condition.setCreatedAt(now);
        condition.setUpdatedAt(now);
        condition.setDeleteFlag(0L);
        conditionMapper.insert(condition);
        return toConditionResp(condition);
    }

    /**
     * 获取权限条件详情
     * <p>
     * 根据条件编码（业务键，T-PERM-029 从内部主键切换）查询权限条件的完整信息。
     * 读取无门禁（2026-08-08 产品确认：条件规则全租户开放、非敏感）。
     * </p>
     *
     * @param tenantId      租户ID
     * @param conditionCode 条件编码
     * @return 条件响应
     * @throws BizException 条件不存在（20006 CONDITION_NOT_FOUND，原 data=null 宽松语义已删除）
     */
    @Override
    public ConditionResp getCondition(Long tenantId, String conditionCode) {
        PermissionCondition condition = conditionMapper.selectValidByCode(tenantId, conditionCode);
        if (condition == null) {
            throw new BizException(PermissionErrorCode.CONDITION_NOT_FOUND.getCode(),
                "Condition not found: " + conditionCode);
        }
        return toConditionResp(condition);
    }

    /**
     * 更新权限条件
     * <p>
     * 更新权限条件的名称、规则、启用状态、描述等属性。
     * 需要CONDITION_UPDATE权限。
     * 更新完成后在事务提交后失效相关缓存。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，以业务键 code 定位（不可改），包含要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的条件响应
     * @throws SecurityException     无权限时抛出
     * @throws BizException 条件不存在（20006 CONDITION_NOT_FOUND）或规则不可下发（20031）时抛出
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
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByEntityId(tenantId, operatorId, ResourceTypeCode.CONDITION, condition.getId(), OperationCodeConstants.UPDATE)) {
            throw new SecurityException("Permission denied: UPDATE on CONDITION:" + req.code());
        }

        if (req.name() != null) condition.setName(req.name());
        if (req.conditionRules() != null) {
            JsonValidationUtils.validateJson(req.conditionRules());
            condition.setConditionRules(req.conditionRules());
        }
        if (req.enabled() != null) condition.setEnabled(req.enabled());
        if (req.gatewayEvaluable() != null) condition.setGatewayEvaluable(req.gatewayEvaluable());
        if (req.description() != null) condition.setDescription(req.description());
        // T-PERM-017 C2.5：取"最终状态"联合校验——
        // 1) 只切 flag 不改 rules 时需重读 DB 老 rules 做校验（否则可绕过）。
        // 2) 同时改 rules + flag 时用新 rules。
        // 3) 只改 rules 不改 flag 时若当前已是 true，也需重新校验新 rules。
        if (Boolean.TRUE.equals(condition.getGatewayEvaluable())) {
            validateGatewayPushable(condition.getConditionRules());
        }
        condition.setUpdatedBy(operatorId);
        condition.setUpdatedAt(LocalDateTime.now());
        conditionMapper.update(condition);

        // 登记受影响条件，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
        // T-PERM-017 P2-A：条件规则变更需同步失效 Gateway 已下发的内联 conditionRules 接口快照。
        // 反查 condition_id 引用的 resource_entity_id 对应 serviceCodes，调 markServiceCodes
        // 进入广播事件载荷（Gateway 订阅侧按 tenant+serviceCodes 清本地 interfaceSnapshotCache，T-PERM-006 落地后生效）。
        PermissionChangeContext.markConditions(tenantId, Set.of(condition.getId()));
        markServiceCodesForConditions(tenantId, Set.of(condition.getId()));
        return toConditionResp(condition);
    }

    /**
     * 查询权限条件列表
     * <p>
     * 查询租户下所有活跃的权限条件。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 条件响应列表
     */
    @Override
    public List<ConditionResp> listConditions(Long tenantId) {
        return conditionMapper.selectByTenantId(tenantId).stream().map(this::toConditionResp).collect(Collectors.toList());
    }

    /**
     * 按业务键批量删除权限条件
     * <p>
     * 按条件编码集合批量软删除（T-PERM-029 从内部主键 ids 切换）。
     * 先批量解析编码为实体（一次 SQL），再对实体 id 集合做批量实例级 DELETE 门禁，
     * 最后批量软删除，避免N+1问题。
     * 请求中不存在或已删除的编码静默跳过（幂等语义，与 resource-entity/remove 一致）。
     * 删除完成后在事务提交后批量失效相关缓存。
     * </p>
     *
     * @param tenantId   租户ID
     * @param codes      条件编码列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 任一存在实体的编码无 DELETE 权限时抛出（fail-closed 整批不变更）
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

        Set<Long> validIds = entities.stream().map(PermissionCondition::getId).collect(Collectors.toSet());

        // T-PERM-042：引擎纯查询，拒绝时由调用方显式抛出
        Set<Long> deniedIds = engine.getDeniedEntityIds(
            tenantId, operatorId, ResourceTypeCode.CONDITION, validIds, OperationCodeConstants.DELETE);
        if (!deniedIds.isEmpty()) {
            throw new SecurityException("Permission denied: DELETE on CONDITION:" + deniedIds);
        }

        LocalDateTime now = LocalDateTime.now();
        conditionMapper.softDeleteBatch(tenantId, validIds.stream().toList(), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " permission_condition row(s)");

        // 登记受影响条件，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
        // T-PERM-017 P2-A：批量删除同步反查 serviceCodes 触发 Gateway 本地快照失效。
        PermissionChangeContext.markConditions(tenantId, validIds);
        markServiceCodesForConditions(tenantId, validIds);
    }

    /**
     * 反查受影响条件引用的 serviceCodes 并登记进 PermissionChangeContext（T-PERM-017 P2-A）。
     * <p>
     * 用于条件 update/delete 后通知 Gateway 失效已下发的内联 conditionRules 接口快照。
     * 委托 {@link RoleResourcePermissionMapper#selectServiceCodesByConditionIds}（JOIN 一次 SQL），
     * 空结果（条件未被任何 grant 引用）→ no-op，不无谓登记。
     * </p>
     * <p>
     * 即使本租户的某些条件未与 grants 关联，也仍调用 markServiceCodes(空集合)：
     * {@code markServiceCodes(空集合)} 由 PermissionChangeContext 内部 noop 处理。
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
            c.getDescription(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    /**
     * 校验条件规则可下发 Gateway（T-PERM-017 C2.5）
     * <p>
     * 当 {@code gatewayEvaluable=true} 时调用，要求 {@code conditionRules.items[].type}
     * 全部在 {@link ConditionEvalUtils#GATEWAY_PUSHABLE_TYPES} 白名单内（含未知类型默认 fail-close）。
     * 不通过抛 {@link BizException}({@link PermissionErrorCode#CONDITION_RULES_INVALID})。
     * </p>
     *
     * @param conditionRulesJson 条件规则 JSON 字符串（已通过 JsonValidationUtils 语法校验）
     * @throws BizException 含集合外类型 / items 为空 / JSON 解析失败
     */
    private void validateGatewayPushable(String conditionRulesJson) {
        if (conditionRulesJson == null || conditionRulesJson.isBlank()) {
            throw new BizException(PermissionErrorCode.CONDITION_RULES_INVALID.getCode(),
                "gatewayEvaluable=true 但 conditionRules 为空");
        }
        JsonNode tree;
        try {
            tree = objectMapper.readTree(conditionRulesJson);
        } catch (Exception e) {
            throw new BizException(PermissionErrorCode.CONDITION_RULES_INVALID.getCode(),
                "conditionRules 解析失败: " + e.getMessage());
        }
        if (!ConditionEvalUtils.isGatewayPushable(tree)) {
            throw new BizException(PermissionErrorCode.CONDITION_RULES_INVALID.getCode(),
                "gatewayEvaluable=true 仅允许 logic ∈ "
                    + ConditionEvalUtils.VALID_LOGIC
                    + "（或缺省=AND）且 items[].type ∈ "
                    + ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES + " 的规则");
        }
    }
}