package cn.ac.fage.accessmesh.access.sync.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.sync.dto.AbstractUserFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.AbstractUserSyncReq;
import cn.ac.fage.accessmesh.access.user.service.AbstractUserSyncAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * abstract-user 同步入口控制器。
 * <p>
 * 严格遵循 docs/design/access-service-api-contract.md §19.4。
 * 路径与既有 {@code UserController} 共享 base path，但仅暴露 sync/full-sync 端点。
 * </p>
 */
@RestController
@RequestMapping("/api/access/abstract-user")
public class AbstractUserSyncController {

    private final AbstractUserSyncAppService abstractUserSyncAppService;

    public AbstractUserSyncController(AbstractUserSyncAppService abstractUserSyncAppService) {
        this.abstractUserSyncAppService = abstractUserSyncAppService;
    }

    /**
     * 增量同步：UPSERT / DISABLE / DELETE。
     */
    @PostMapping("/sync")
    public R<SyncResultResp> sync(@Valid @RequestBody AbstractUserSyncReq req,
                                           HttpServletRequest httpRequest) {
        return R.ok(abstractUserSyncAppService.sync(
                TenantContextHolder.getTenantId(), req, httpRequest));
    }

    /**
     * 全量同步：scope 内未出现的业务键自动 DELETE。
     * 顶层契约同 sync；批量明细放入 {@code SyncResultResp.detail}。
     */
    @PostMapping("/full-sync")
    public R<SyncResultResp> fullSync(@Valid @RequestBody AbstractUserFullSyncReq req,
                                               HttpServletRequest httpRequest) {
        return R.ok(abstractUserSyncAppService.fullSync(
                TenantContextHolder.getTenantId(), req, httpRequest));
    }
}
