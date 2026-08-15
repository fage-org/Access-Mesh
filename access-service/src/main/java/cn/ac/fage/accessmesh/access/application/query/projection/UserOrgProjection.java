package cn.ac.fage.accessmesh.access.application.query.projection;

/**
 * 用户-组织关系投影（跨域只读查询用）。
 * <p>
 * 对应 sys_user_org 有效行中用户信息聚合所需字段，不暴露领域实体。
 * </p>
 *
 * @param userId    用户 ID（批量查询归组键，多用户结果可区分归属）
 * @param orgId     组织 ID
 * @param isPrimary 是否主组织
 */
public record UserOrgProjection(
    Long userId,
    Long orgId,
    Boolean isPrimary
) {}
