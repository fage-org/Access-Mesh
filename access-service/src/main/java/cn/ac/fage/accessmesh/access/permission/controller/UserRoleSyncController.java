package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncReq;
import cn.ac.fage.accessmesh.access.permission.service.UserRoleSyncAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户角色同步控制器。
 * <p>
 * 端点：
 * <ul>
 *   <li>POST /api/perm/user-role/sync —— BIND/UNBIND 增量同步</li>
 *   <li>POST /api/perm/user-role/full-sync —— 全量同步</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/perm/user-role")
public class UserRoleSyncController {

    private final UserRoleSyncAppService userRoleSyncAppService;

    public UserRoleSyncController(UserRoleSyncAppService userRoleSyncAppService) {
        this.userRoleSyncAppService = userRoleSyncAppService;
    }

    @PostMapping("/sync")
    public R<SyncResultResp> sync(@Valid @RequestBody UserRoleSyncReq req,
                                            HttpServletRequest httpRequest) {
        Long tenantId = TenantContextHolder.getTenantId();
        return R.ok(userRoleSyncAppService.sync(tenantId, req, httpRequest));
    }

    @PostMapping("/full-sync")
    public R<SyncResultResp> fullSync(@Valid @RequestBody UserRoleFullSyncReq req,
                                               HttpServletRequest httpRequest) {
        Long tenantId = TenantContextHolder.getTenantId();
        return R.ok(userRoleSyncAppService.fullSync(tenantId, req, httpRequest));
    }
}
