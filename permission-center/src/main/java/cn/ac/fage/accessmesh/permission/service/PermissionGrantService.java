package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionChildrenReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionAddChildReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionRemoveChildReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionItemResp;
import java.util.List;

/**
 * Permission grant/revoke management service.
 */
public interface PermissionGrantService {

    /**
     * Batch grant permissions to a role (add/update/delete).
     * Returns the full list of resulting permission items.
     */
    List<RolePermissionItemResp> batchGrant(Long tenantId, RoleGrantReq req);

    /**
     * Batch revoke permissions from a role.
     */
    void batchRevoke(Long tenantId, BatchRevokeReq req);

    List<RolePermissionItemResp> listPermissions(Long tenantId, RolePermissionListReq req);

    List<RolePermissionItemResp> listChildren(Long tenantId, RolePermissionChildrenReq req);

    List<RolePermissionItemResp> addChildren(Long tenantId, RolePermissionAddChildReq req);

    void removeChild(Long tenantId, RolePermissionRemoveChildReq req);
}
