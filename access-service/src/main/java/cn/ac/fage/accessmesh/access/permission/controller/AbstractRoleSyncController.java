package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncReq;
import cn.ac.fage.accessmesh.access.permission.service.AbstractRoleSyncAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * abstract-role 同步入口控制器。
 * <p>
 * 严格遵循 docs/design/permission-center/api-contract.md §6.2.2.3。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/abstract-role")
public class AbstractRoleSyncController {

    private final AbstractRoleSyncAppService abstractRoleSyncAppService;

    public AbstractRoleSyncController(AbstractRoleSyncAppService abstractRoleSyncAppService) {
        this.abstractRoleSyncAppService = abstractRoleSyncAppService;
    }

    /**
     * 增量同步：UPSERT / DISABLE / DELETE。
     */
    @PostMapping("/sync")
    public PermResult<SyncResultResp> sync(@Valid @RequestBody AbstractRoleSyncReq req,
                                           HttpServletRequest httpRequest) {
        return PermResult.success(abstractRoleSyncAppService.sync(
                TenantContextHolder.getTenantId(), req, httpRequest));
    }

    /**
     * 全量同步。顶层契约同 sync；批量明细放入 {@code SyncResultResp.detail}。
     */
    @PostMapping("/full-sync")
    public PermResult<SyncResultResp> fullSync(@Valid @RequestBody AbstractRoleFullSyncReq req,
                                               HttpServletRequest httpRequest) {
        return PermResult.success(abstractRoleSyncAppService.fullSync(
                TenantContextHolder.getTenantId(), req, httpRequest));
    }
}
