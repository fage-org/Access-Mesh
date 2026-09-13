package cn.ac.fage.accessmesh.access.user.service;

import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.sync.dto.AbstractUserFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.AbstractUserSyncReq;
import jakarta.servlet.http.HttpServletRequest;

/**
 * abstract-user 同步应用服务。
 * <p>
 * 负责 UPSERT / DISABLE / DELETE 增量事件，以及全量同步差异校准。
 * 详见 docs/design/access-service-api-contract.md §19.4。
 * </p>
 */
public interface AbstractUserSyncAppService {

    /**
     * 增量同步：UPSERT / DISABLE / DELETE。
     */
    SyncResultResp sync(Long tenantId, AbstractUserSyncReq req, HttpServletRequest httpRequest);

    /**
     * 全量同步：scope 内未出现的业务键自动 DELETE + 软删目标事实。
     * <p>
     * 顶层契约同 sync；批量明细放入 {@code SyncResultResp.detail}。
     * </p>
     */
    SyncResultResp fullSync(Long tenantId, AbstractUserFullSyncReq req, HttpServletRequest httpRequest);
}
