package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigDeleteReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigGetReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.TenantIdReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigResp;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service config management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/service-config")
public class ServiceConfigController {

    private final ConfigManageService configManageService;

    public ServiceConfigController(ConfigManageService configManageService) {
        this.configManageService = configManageService;
    }

    @PostMapping("/save")
    public PermResult<ServiceConfigResp> saveServiceConfig(@Valid @RequestBody ServiceConfigReq req) {
        return PermResult.success(configManageService.createServiceConfig(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detail")
    public PermResult<ServiceConfigResp> getServiceConfig(@Valid @RequestBody ServiceConfigGetReq req) {
        return PermResult.success(configManageService.getServiceConfig(TenantContextHolder.getTenantId(), req.serviceCode()));
    }

    @PostMapping("/list")
    public PermResult<List<ServiceConfigResp>> listServiceConfigs(@Valid @RequestBody TenantIdReq req) {
        return PermResult.success(configManageService.listServiceConfigs(TenantContextHolder.getTenantId()));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteServiceConfig(@Valid @RequestBody ServiceConfigDeleteReq req) {
        configManageService.deleteServiceConfig(TenantContextHolder.getTenantId(), req.serviceCode(), null);
        return PermResult.success();
    }

    @PostMapping("/update")
    public PermResult<ServiceConfigResp> updateServiceConfig(@Valid @RequestBody ServiceConfigReq req) {
        return PermResult.success(configManageService.updateServiceConfig(TenantContextHolder.getTenantId(), req, null));
    }
}
