package cn.ac.fage.accessmesh.access.sync.controller;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.sync.dto.SyncStatusListReq;
import cn.ac.fage.accessmesh.access.sync.dto.SyncStatusResp;
import cn.ac.fage.accessmesh.access.sync.service.SyncStatusAppService;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access/sync-status")
public class SyncStatusController {
    private final SyncStatusAppService service;
    public SyncStatusController(SyncStatusAppService service) { this.service = service; }

    @PostMapping("/list")
    public R<PageResp<SyncStatusResp>> list(@Valid @RequestBody SyncStatusListReq req) {
        return R.ok(service.list(TenantContextHolder.getTenantId(), req));
    }
}
