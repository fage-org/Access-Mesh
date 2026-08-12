package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 角色更新请求体
 * <p>
 * 用于更新角色的信息，包括名称、状态、排序和扩展属性。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param roleId    角色ID，必填
 * @param name      角色名称，可选
 * @param status    角色状态，可选，0=禁用，1=启用
 * @param sortOrder 排序顺序，可选
 * @param extra     扩展属性JSON，可选
 */
public record RoleUpdateReq(
    @NotNull Long roleId,
    String name,
    Integer status,
    Integer sortOrder,
    String extra
) {}