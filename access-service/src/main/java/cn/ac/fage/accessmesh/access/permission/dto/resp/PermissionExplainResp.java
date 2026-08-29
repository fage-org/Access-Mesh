package cn.ac.fage.accessmesh.access.permission.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import java.util.List;

/**
 * 权限解释响应体
 * <p>
 * 返回权限判定的详细解释信息，包括权限来源和最近变更。
 * 用于权限解释查询接口的响应，帮助用户理解权限判定原因。
 * T-PERM-033 扩展：条件评估明细（含脱敏参数与逐项结果）、互斥冲突丢弃明细、
 * 评估上下文来源（ADMIN_INPUT=管理员输入 / CURRENT_REQUEST=回退当前请求）。
 * </p>
 *
 * @param targetType         目标类型
 * @param allowed            是否允许访问
 * @param reason             拒绝原因，允许时为null
 * @param permission         权限键信息
 * @param sourceRoles        来源角色列表
 * @param matchedPermissionIds 匹配的权限ID列表
 * @param recentChanges      最近变更列表（按权限键过滤，见契约 §6.8）
 * @param evaluationContextSource 条件评估上下文来源（ADMIN_INPUT / CURRENT_REQUEST）
 * @param evaluatedClientIp  实际参与 IP 类条件评估的客户端 IP（无 IP 条件上下文时为 null）
 * @param conditionEvaluations 条件评估明细（候选命中条目中挂条件的逐项评估过程）
 * @param conflictDrops      被权限互斥规则丢弃的候选命中条目及命中规则
 */
public record PermissionExplainResp(
    String targetType,
    boolean allowed,
    String reason,
    PermissionKey permission,
    List<SourceRole> sourceRoles,
    List<Long> matchedPermissionIds,
    List<RecentChangeResp> recentChanges,
    String evaluationContextSource,
    String evaluatedClientIp,
    List<ConditionEvaluation> conditionEvaluations,
    List<ConflictDrop> conflictDrops
) {
    /**
     * 权限键信息
     * <p>
     * 表示权限的唯一标识信息。
     * </p>
     *
     * @param domainCode        业务域编码
     * @param resourceTypeCode  资源类型编码
     * @param resourceCode      资源编码
     * @param codeType          编码类型
     * @param operationCode     操作编码
     * @param scopeMode         范围模式
     */
    public record PermissionKey(
        String domainCode,
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String operationCode,
        ScopeMode scopeMode
    ) {}

    /**
     * 来源角色信息
     * <p>
     * 表示权限来源的角色信息，包括继承路径。
     * </p>
     *
     * @param roleTypeCode   角色类型编码
     * @param roleExternalId 角色外部标识
     * @param roleName       角色名称
     * @param via            继承路径列表
     */
    public record SourceRole(
        String roleTypeCode,
        String roleExternalId,
        String roleName,
        List<String> via
    ) {}

    /**
     * 条件评估明细（值已脱敏：IP 掩码主机段、日期/时间原样）
     *
     * @param conditionId  条件ID
     * @param permissionId 关联授权条目ID
     * @param roleId       来源角色ID
     * @param status       条件加载状态：OK / DISABLED / NOT_FOUND / INVALID
     * @param logic        条件逻辑（AND/OR）
     * @param passed       整体是否通过（非 OK 状态恒为 false，fail-close）
     * @param items        逐项评估结果
     */
    public record ConditionEvaluation(
        Long conditionId,
        Long permissionId,
        Long roleId,
        String status,
        String logic,
        boolean passed,
        List<ConditionItem> items
    ) {}

    /**
     * 单个条件项评估结果
     *
     * @param type         条件类型（IP_WHITELIST / IP_BLACKLIST / DATE_RANGE / TIME_RANGE）
     * @param maskedParams 脱敏后的参数摘要
     * @param matched      该项在评估上下文下是否满足
     */
    public record ConditionItem(
        String type,
        String maskedParams,
        boolean matched
    ) {}

    /**
     * 被权限互斥规则丢弃的候选命中条目
     *
     * @param permissionId        被丢弃的授权条目ID
     * @param roleId              来源角色ID
     * @param ruleId              命中的冲突规则ID
     * @param firstOperationCode  规则一侧操作码
     * @param secondOperationCode 规则另一侧操作码
     */
    public record ConflictDrop(
        Long permissionId,
        Long roleId,
        Long ruleId,
        String firstOperationCode,
        String secondOperationCode
    ) {}
}
