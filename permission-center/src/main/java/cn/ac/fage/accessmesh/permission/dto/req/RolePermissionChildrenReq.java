package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 角色权限子节点查询请求体
 * <p>
 * 用于查询角色权限配置的子节点权限列表。
 * </p>
 *
 * @param permissionId 权限配置ID，必填
 */
public record RolePermissionChildrenReq(
    @NotNull Long permissionId
) {}