package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.Oauth2ClientResp;
import cn.ac.fage.accessmesh.admin.service.Oauth2ClientService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oauth2/client")
public class Oauth2ClientController {

    private final Oauth2ClientService oauth2ClientService;

    public Oauth2ClientController(Oauth2ClientService oauth2ClientService) {
        this.oauth2ClientService = oauth2ClientService;
    }

    @PostMapping("/create")
    @AuditLog(module = "OAuth2客户端", action = "创建", targetType = "OAUTH2_CLIENT")
    public PermResult<Long> createClient(@Valid @RequestBody Oauth2ClientCreateReq req) {
        return PermResult.success(oauth2ClientService.createClient(req));
    }

    @PostMapping("/update")
    @AuditLog(module = "OAuth2客户端", action = "修改", targetType = "OAUTH2_CLIENT")
    public PermResult<Void> updateClient(@Valid @RequestBody Oauth2ClientUpdateReq req) {
        oauth2ClientService.updateClient(req);
        return PermResult.success();
    }

    @PostMapping("/delete")
    @AuditLog(module = "OAuth2客户端", action = "删除", targetType = "OAUTH2_CLIENT")
    public PermResult<Void> deleteClients(@Valid @RequestBody IdsReq req) {
        oauth2ClientService.deleteClients(req);
        return PermResult.success();
    }

    @PostMapping("/detail")
    public PermResult<Oauth2ClientResp> getClient(@Valid @RequestBody IdReq req) {
        return PermResult.success(oauth2ClientService.getClientResp(req.id()));
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<Oauth2ClientResp>> pageClients(@Valid @RequestBody Oauth2ClientPageReq req) {
        return PermResult.success(oauth2ClientService.pageClientResps(req));
    }
}
