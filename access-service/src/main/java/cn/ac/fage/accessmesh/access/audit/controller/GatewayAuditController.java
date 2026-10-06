package cn.ac.fage.accessmesh.access.audit.controller;

import cn.ac.fage.accessmesh.access.audit.service.GatewayAuditAppService;
import cn.ac.fage.accessmesh.common.model.GatewayDenialAuditReq;
import cn.ac.fage.accessmesh.common.model.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 内部审计入口，不注册 Gateway 业务路由或服务凭证白名单。 */
@RestController
public class GatewayAuditController {
    private final GatewayAuditAppService service;

    public GatewayAuditController(GatewayAuditAppService service) { this.service = service; }

    @PostMapping("/api/access/internal-audit/gateway-denial")
    public R<Void> recordDenial(@Valid @RequestBody GatewayDenialAuditReq request) {
        service.recordDenial(request);
        return R.ok();
    }
}
