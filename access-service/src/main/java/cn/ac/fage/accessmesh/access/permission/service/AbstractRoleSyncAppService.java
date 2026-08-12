package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncReq;
import jakarta.servlet.http.HttpServletRequest;

/**
 * abstract-role 同步应用服务。
 * <p>
 * 负责 UPSERT / DISABLE / DELETE 增量事件，以及全量同步差异校准。
 * 父角色解析失败时返回 DEPENDENCY_MISSING。
 * 详见 docs/design/permission-center/api-contract.md §6.2.2.3。
 * </p>
 */
public interface AbstractRoleSyncAppService {

    /**
     * 增量同步：UPSERT / DISABLE / DELETE。
     */
    SyncResultResp sync(Long tenantId, AbstractRoleSyncReq req, HttpServletRequest httpRequest);

    /**
     * 全量同步。顶层契约同 sync；批量明细放入 {@code SyncResultResp.detail}。
     */
    SyncResultResp fullSync(Long tenantId, AbstractRoleFullSyncReq req, HttpServletRequest httpRequest);
}
