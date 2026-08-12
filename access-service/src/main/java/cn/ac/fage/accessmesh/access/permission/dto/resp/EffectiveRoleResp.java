package cn.ac.fage.accessmesh.access.permission.dto.resp;

/**
 * 有效角色响应体
 * <p>
 * 返回用户的有效角色信息，包括角色类型、外部标识和名称。
 * 用于查询用户在特定业务域下的有效角色。
 * </p>
 *
 * @param roleTypeCode   角色类型编码
 * @param roleExternalId 角色外部标识
 * @param roleName       角色名称
 */
public record EffectiveRoleResp(
    String roleTypeCode,
    String roleExternalId,
    String roleName
) {}