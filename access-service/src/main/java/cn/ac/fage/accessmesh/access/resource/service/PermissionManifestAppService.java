package cn.ac.fage.accessmesh.access.resource.service;

import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;

public interface PermissionManifestAppService {
    SyncResultResp fullSync(Long tenantId, PermissionManifestReq request);
}
