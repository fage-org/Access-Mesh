package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;

/**
 * 角色代理服务接口
 * <p>
 * 提供admin-service与permission-center之间的角色操作代理方法。
 * 用于在admin-service中创建和管理permission-center的角色，
 * 实现跨服务的角色权限管理功能。
 * </p>
 */
public interface RoleProxyService {

    /**
     * 为组织创建角色
     * <p>
     * 在permission-center中创建角色，并关联到指定组织。
     * 用于组织创建时自动创建对应的角色实体。
     * </p>
     *
     * @param roleName  角色名称
     * @param orgId     组织ID（admin-service中的组织实体ID）
     * @param tenantId  租户ID
     * @return 创建的角色ID（permission-center中的角色ID）
     */
    Long createRoleForOrg(String roleName, Long orgId, Long tenantId);

    /**
     * 向角色授予菜单权限
     * <p>
     * 将指定菜单资源的权限授予角色。
     * 菜单ID会被解析为permission-center中的资源ID。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID（permission-center中的角色ID）
     * @param menuId   菜单实体ID（将被解析为资源ID）
     * @param opCode   操作码（如"VIEW"、"MANAGE"等）
     */
    void grantMenuToRole(Long tenantId, Long roleId, Long menuId, String opCode);

    /**
     * 从角色撤销菜单权限
     * <p>
     * 撤销角色对指定菜单资源的权限。
     * 菜单ID会被解析为permission-center中的资源ID。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID（permission-center中的角色ID）
     * @param menuId   菜单实体ID（将被解析为资源ID）
     */
    void revokeMenuFromRole(Long tenantId, Long roleId, Long menuId);

    /**
     * 加载用户的角色和有效权限
     * <p>
     * 从permission-center加载用户的角色列表和有效权限列表。
     * 用于构建用户信息响应中的角色和权限数据。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户信息响应，包含角色和权限列表
     */
    UserInfoResp loadUserRolesAndPermissions(Long userId);
}