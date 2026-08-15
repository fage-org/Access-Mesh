package cn.ac.fage.accessmesh.access.application.query.projection;

/**
 * 功能角色投影（跨域只读查询用）。
 * <p>
 * 对应 abstract_role 表中满足功能角色类型过滤的有效行，
 * 仅承载前端展示所需字段，不暴露领域实体。
 * </p>
 *
 * @param roleType   角色类型值（type_definition 的 role_type 映射 value，由服务层解析为 roleTypeCode）
 * @param externalId 角色外部标识（业务键）
 * @param name       角色名
 */
public record FunctionalRoleProjection(
    Integer roleType,
    String externalId,
    String name
) {}
