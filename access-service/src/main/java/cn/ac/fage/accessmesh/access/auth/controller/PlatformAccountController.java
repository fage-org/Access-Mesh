package cn.ac.fage.accessmesh.access.auth.controller;

import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountCreateReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountCreatedResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountNameReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformStatusReq;
import cn.ac.fage.accessmesh.access.auth.dto.IssuedPasswordResp;
import cn.ac.fage.accessmesh.access.auth.service.PlatformAccountAppService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access/platform-account")
public class PlatformAccountController {
    private final PlatformAccountAppService service;
    public PlatformAccountController(PlatformAccountAppService service) { this.service=service; }
    @PostMapping("/page") public R<PageResp<PlatformAccountResp>> page(@Valid @RequestBody PageReq req) { return R.ok(service.page(req)); }
    @PostMapping("/create") public R<PlatformAccountCreatedResp> create(@Valid @RequestBody PlatformAccountCreateReq req) { return R.ok(service.create(req)); }
    @PostMapping("/update") public R<Void> update(@Valid @RequestBody PlatformAccountNameReq req) { service.updateName(req); return R.ok(); }
    @PostMapping("/update-status") public R<Void> status(@Valid @RequestBody PlatformStatusReq req) { service.updateStatus(req); return R.ok(); }
    @PostMapping("/reset-password") public R<IssuedPasswordResp> reset(@Valid @RequestBody IdReq req) { return R.ok(service.resetPassword(req.id())); }
}
