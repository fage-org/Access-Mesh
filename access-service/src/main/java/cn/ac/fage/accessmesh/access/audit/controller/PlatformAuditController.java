package cn.ac.fage.accessmesh.access.audit.controller;

import cn.ac.fage.accessmesh.access.audit.dto.req.PlatformAuditPageReq;
import cn.ac.fage.accessmesh.access.audit.dto.resp.PlatformAuditResp;
import cn.ac.fage.accessmesh.access.audit.service.PlatformAuditAppService;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access/platform-audit")
public class PlatformAuditController {
    private final PlatformAuditAppService service;
    public PlatformAuditController(PlatformAuditAppService service) { this.service=service; }
    @PostMapping("/page")
    public R<PageResp<PlatformAuditResp>> page(@Valid @RequestBody PlatformAuditPageReq req) { return R.ok(service.page(req)); }
}
