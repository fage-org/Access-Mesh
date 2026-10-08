package cn.ac.fage.accessmesh.access.tenant.controller;

import cn.ac.fage.accessmesh.access.tenant.dto.TenantAdminPasswordResp;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantCreateReq;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantCreatedResp;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantNameReq;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantPageReq;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantResp;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantStatusReq;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAppService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access/tenant")
public class TenantController {
    private final TenantAppService service;
    public TenantController(TenantAppService service) { this.service=service; }
    @PostMapping("/page") public R<PageResp<TenantResp>> page(@Valid @RequestBody TenantPageReq req) { return R.ok(service.page(req)); }
    @PostMapping("/detail") public R<TenantResp> detail(@Valid @RequestBody IdReq req) { return R.ok(service.detail(req.id())); }
    @PostMapping("/create") public R<TenantCreatedResp> create(@Valid @RequestBody TenantCreateReq req) { return R.ok(service.create(req)); }
    @PostMapping("/update") public R<Void> update(@Valid @RequestBody TenantNameReq req) { service.updateName(req); return R.ok(); }
    @PostMapping("/update-status") public R<TenantResp> status(@Valid @RequestBody TenantStatusReq req) { return R.ok(service.updateStatus(req)); }
    @PostMapping("/reset-admin-password") public R<TenantAdminPasswordResp> reset(@Valid @RequestBody IdReq req) { return R.ok(service.resetAdminPassword(req.id())); }
}
