package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 角色权限子节点移除请求体
 * <p>
 * 用于移除角色权限配置的子节点权限。
 * </p>
 *
 * @param permissionId 权限配置ID，必填
 */
public record RolePermissionRemoveChildReq(
    @NotNull Long permissionId
) {}