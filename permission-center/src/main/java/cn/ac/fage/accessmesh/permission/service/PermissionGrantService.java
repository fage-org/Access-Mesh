package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.RoleGrantReq;

/**
 * Permission grant/revoke management service.
 */
public interface PermissionGrantService {

    /**
     * Batch grant permissions to a role (add/update/delete).
     */
    void batchGrant(Long tenantId, Long roleId, RoleGrantReq req);

    /**
     * Batch revoke permissions from a role.
     */
    void batchRevoke(Long tenantId, Long roleId, java.util.List<Long> permissionIds);
}
