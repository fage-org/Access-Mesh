package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceEntitySyncReq;
import jakarta.servlet.http.HttpServletRequest;

/**
 * resource-entity 同步应用服务。
 * <p>
 * 负责 UPSERT / DISABLE / DELETE 增量事件，以及全量同步差异校准。
 * 详见 docs/design/permission-center/api-contract.md §6.2.2。
 * </p>
 */
public interface ResourceEntitySyncAppService {

    SyncResultResp sync(Long tenantId, ResourceEntitySyncReq req, HttpServletRequest httpRequest);

    /**
     * 全量同步。顶层契约同 sync；批量明细放入 {@code SyncResultResp.detail}。
     */
    SyncResultResp fullSync(Long tenantId, ResourceEntityFullSyncReq req, HttpServletRequest httpRequest);
}
