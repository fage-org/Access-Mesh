package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.PermissionVersionQueryReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionVersionResp;

/**
 * Permission version service — used by Gateway to check if cached snapshots are stale.
 */
public interface PermissionVersionService {

    /**
     * Query current permission version for a role (identified by business keys).
     * Version is incremented whenever role's permission assignments change.
     */
    PermissionVersionResp queryVersion(Long tenantId, PermissionVersionQueryReq req);
}
