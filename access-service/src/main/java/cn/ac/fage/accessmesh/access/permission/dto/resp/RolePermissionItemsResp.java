package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 角色权限条目列表响应体
 * <p>
 * 返回角色权限配置的条目列表。
 * 用于角色权限配置查询接口的响应。
 * </p>
 *
 * @param items 角色权限条目列表
 */
public record RolePermissionItemsResp(
    List<RolePermissionItemResp> items
) {}