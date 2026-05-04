package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.req.ConfigUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.resp.ConfigResp;
import cn.ac.fage.accessmesh.admin.service.ConfigService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<ConfigResp>> pageConfigs(@Valid @RequestBody PageReq pageReq) {
        return PermResult.success(configService.pageConfigs(pageReq));
    }

    @PostMapping("/detail")
    public PermResult<ConfigResp> getConfig(@Valid @RequestBody IdReq req) {
        return PermResult.success(configService.getConfig(req.id()));
    }

    @PostMapping("/update")
    @AuditLog(module = "配置管理", action = "修改", targetType = "CONFIG")
    public PermResult<Void> updateConfig(@Valid @RequestBody ConfigUpdateReq req) {
        configService.updateConfig(req);
        return PermResult.success();
    }

    @PostMapping("/delete")
    @AuditLog(module = "配置管理", action = "删除", targetType = "CONFIG")
    public PermResult<Void> deleteConfig(@Valid @RequestBody IdsReq req) {
        configService.deleteConfig(req);
        return PermResult.success();
    }
}
