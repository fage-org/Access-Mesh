package cn.ac.fage.accessmesh.access.permission.vo;

import java.util.List;

/**
 * 权限条件评估明细（T-PERM-033 explain DTO 扩展）。
 * <p>
 * 表示一条授权条目（permissionId + roleId）所挂条件在给定评估上下文下的逐项评估过程，
 * 供权限排查视图展示「为什么命中/未命中」。条件值按脱敏规则回传：
 * IP 黑白名单的 CIDR 掩码主机段，日期/时间范围为非敏感值原样回传。
 * </p>
 *
 * @param conditionId  条件ID
 * @param permissionId 关联的授权条目ID
 * @param roleId       来源角色ID
 * @param status       条件加载状态：OK / DISABLED / NOT_FOUND / INVALID
 * @param logic        条件逻辑（AND/OR）
 * @param passed       整体是否通过（非 OK 状态恒为 false，fail-close）
 * @param items        逐项评估结果
 */
public record ConditionEvaluationDetail(
    Long conditionId,
    Long permissionId,
    Long roleId,
    String status,
    String logic,
    boolean passed,
    List<ItemDetail> items
) {

    /** 条件加载状态常量 */
    public static final String STATUS_OK = "OK";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_NOT_FOUND = "NOT_FOUND";
    public static final String STATUS_INVALID = "INVALID";

    /**
     * 单个条件项评估结果
     *
     * @param type         条件类型（IP_WHITELIST / IP_BLACKLIST / DATE_RANGE / TIME_RANGE）
     * @param maskedParams 脱敏后的参数摘要
     * @param matched      该项在评估上下文下是否满足
     */
    public record ItemDetail(
        String type,
        String maskedParams,
        boolean matched
    ) {}
}
