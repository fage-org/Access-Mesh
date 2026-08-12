package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncReq;
import jakarta.servlet.http.HttpServletRequest;

/**
 * user-role 同步应用服务。
 * <p>
 * 负责 BIND / UNBIND 增量事件，以及全量同步差异校准。
 * 详见 docs/design/permission-center/api-contract.md §6.2.2.3。
 * </p>
 */
public interface UserRoleSyncAppService {

    /**
     * 增量同步：BIND / UNBIND。
     */
    SyncResultResp sync(Long tenantId, UserRoleSyncReq req, HttpServletRequest httpRequest);

    /**
     * 全量同步：scope 内未出现的业务键自动 UNBOUND + 软删 user_role。
     * 顶层契约同 sync；批量明细放入 {@code SyncResultResp.detail}。
     */
    SyncResultResp fullSync(Long tenantId, UserRoleFullSyncReq req, HttpServletRequest httpRequest);
}
