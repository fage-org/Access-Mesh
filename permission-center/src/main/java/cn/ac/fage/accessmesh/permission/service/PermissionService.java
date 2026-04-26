package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.perm.common.model.PermCheckReq;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckResp;

import java.util.List;

public interface PermissionService {

    PermCheckResp checkPermission(PermCheckReq req);

    Long createRole(String roleName, Long orgId, Long tenantId);

    void grantMenuToRole(Long roleId, Long menuId);

    void revokeMenuFromRole(Long roleId, Long menuId);

    List<String> getUserPermissions(Long userId);
}
