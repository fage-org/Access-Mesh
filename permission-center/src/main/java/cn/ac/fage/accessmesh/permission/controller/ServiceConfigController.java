package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigGetReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigApisReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.EmptyReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;
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
        return PermResult.success(configManageService.saveServiceConfig(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detail")
    public PermResult<ServiceConfigResp> getServiceConfig(@Valid @RequestBody ServiceConfigGetReq req) {
        return PermResult.success(configManageService.getServiceConfig(TenantContextHolder.getTenantId(), req.serviceCode()));
    }

    @PostMapping("/list")
    public PermResult<ItemsResp<ServiceConfigResp>> listServiceConfigs(@Valid @RequestBody EmptyReq req) {
        return PermResult.success(new ItemsResp<>(
            configManageService.listServiceConfigs(TenantContextHolder.getTenantId())
        ));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteServiceConfig(@Valid @RequestBody IdsReq req) {
        configManageService.deleteServiceConfigsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    @PostMapping("/sync")
    public PermResult<ServiceConfigSyncResp> syncServiceConfig(@Valid @RequestBody ServiceConfigSyncReq req) {
        return PermResult.success(configManageService.syncServiceInterfaces(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/apis")
    public PermResult<ItemsResp<ApiMappingResp>> listServiceApis(@Valid @RequestBody ServiceConfigApisReq req) {
        return PermResult.success(new ItemsResp<>(
            configManageService.listServiceApis(TenantContextHolder.getTenantId(), req.serviceCode())
        ));
    }
}
