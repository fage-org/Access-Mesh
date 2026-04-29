package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigGetReq;
import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.TenantIdReq;
import cn.ac.fage.accessmesh.permission.dto.resp.SystemConfigResp;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * System config management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/system-config")
public class SystemConfigController {

    private final ConfigManageService configManageService;

    public SystemConfigController(ConfigManageService configManageService) {
        this.configManageService = configManageService;
    }

    @PostMapping("/save")
    public PermResult<Void> upsertSystemConfig(@Valid @RequestBody SystemConfigReq req) {
        configManageService.upsertSystemConfig(TenantContextHolder.getTenantId(), req);
        return PermResult.success();
    }

    @PostMapping("/detail")
    public PermResult<SystemConfigResp> getSystemConfig(@Valid @RequestBody SystemConfigGetReq req) {
        return PermResult.success(configManageService.getSystemConfig(TenantContextHolder.getTenantId(), req.configKey()));
    }

    @PostMapping("/list")
    public PermResult<List<SystemConfigResp>> listSystemConfigs(@Valid @RequestBody TenantIdReq req) {
        return PermResult.success(configManageService.listSystemConfigs(TenantContextHolder.getTenantId()));
    }
}
