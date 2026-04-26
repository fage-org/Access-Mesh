package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;

public interface RoleProxyService {

    Long createRoleForOrg(String roleName, Long orgId, Long tenantId);

    void grantMenuToRole(Long roleId, Long menuId);

    void revokeMenuFromRole(Long roleId, Long menuId);

    UserInfoResp loadUserRolesAndPermissions(Long userId);
}
