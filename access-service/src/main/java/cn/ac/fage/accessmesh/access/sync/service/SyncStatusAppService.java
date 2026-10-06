package cn.ac.fage.accessmesh.access.sync.service;

import cn.ac.fage.accessmesh.access.sync.dto.SyncStatusListReq;
import cn.ac.fage.accessmesh.access.sync.dto.SyncStatusResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;

public interface SyncStatusAppService {
    PageResp<SyncStatusResp> list(Long tenantId, SyncStatusListReq req);
}
