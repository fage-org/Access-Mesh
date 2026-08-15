package cn.ac.fage.accessmesh.access.application.query.projection;

import java.time.LocalDateTime;

/**
 * 用户角色关系投影（跨域只读查询用）。
 * <p>
 * user_role 与 abstract_role 组合后的展示投影：
 * target 角色承载用户实际持有的角色，relation 角色（POSITION 类型）承载所属组织角色，
 * 服务层据此补充岗位所属组织名称等 admin 域展示字段。
 * target 角色投影缺失时 roleExternalId/roleName/targetRoleType 为 null（保留关系行，与
 * permission 域 getUserRoles 语义一致）。
 * 注意：user_role.target_type 为写路径固定的 ROLE/GROUP_ROLE（岗位/组织成员绑定同样写
 * ROLE），岗位语义由 target 角色类型（abstract_role.role_type）承载——POSITION 判定
 * 必须基于 targetRoleType 解析后的类型码，而非 targetType 原始值。
 * </p>
 *
 * @param relationId          关联角色 ID（POSITION 时对应所属组织 abstract_role.id，其他类型为 null）
 * @param targetType          关联角色类型原始值（写路径固定 ROLE/GROUP_ROLE，不承载岗位语义）
 * @param targetRoleType      target 角色类型值（abstract_role.role_type，服务层解析为 roleTypeCode，岗位判定依据）
 * @param roleExternalId      target 角色外部标识
 * @param roleName            target 角色名
 * @param relationExternalId  relation 角色外部标识（即所属组织 sys_org.id 的字符串形式，服务层转 Long 查组织名）
 * @param validFrom           有效期起始（null 不限制）
 * @param validTo             有效期截止（null 不限制）
 */
public record UserRoleProjection(
    Long relationId,
    String targetType,
    Integer targetRoleType,
    String roleExternalId,
    String roleName,
    String relationExternalId,
    LocalDateTime validFrom,
    LocalDateTime validTo
) {}
