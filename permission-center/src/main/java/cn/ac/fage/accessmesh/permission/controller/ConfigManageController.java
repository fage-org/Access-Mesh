package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.*;
import cn.ac.fage.accessmesh.permission.dto.resp.*;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Configuration management APIs — TypeDefinition, BizDomain, DomainConfig, ServiceConfig, SystemConfig.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigManageController {

    private final ConfigManageService configManageService;

    public ConfigManageController(ConfigManageService configManageService) {
        this.configManageService = configManageService;
    }

    // ===== TypeDefinition =====

    @PostMapping("/type/create")
    public PermResult<TypeDefinitionResp> createType(@Valid @RequestBody TypeCreateReq req) {
        return PermResult.success(configManageService.createType(req, null));
    }

    @PostMapping("/type/get")
    public PermResult<TypeDefinitionResp> getType(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(configManageService.getType(req.tenantId(), req.id()));
    }

    @PostMapping("/type/list")
    public PermResult<List<TypeDefinitionResp>> listTypes(@Valid @RequestBody TypeListReq req) {
        return PermResult.success(configManageService.listTypes(req.tenantId(), req.bizDomainId()));
    }

    @PostMapping("/type/delete")
    public PermResult<Void> deleteType(@Valid @RequestBody IdWithTenantReq req) {
        configManageService.deleteType(req.tenantId(), req.id(), null);
        return PermResult.success();
    }

    @PostMapping("/type/update")
    public PermResult<TypeDefinitionResp> updateType(@Valid @RequestBody TypeUpdateReq req) {
        return PermResult.success(configManageService.updateType(req, null));
    }

    // ===== BizDomain =====

    @PostMapping("/biz-domain/create")
    public PermResult<BizDomainResp> createBizDomain(@Valid @RequestBody BizDomainCreateReq req) {
        return PermResult.success(configManageService.createBizDomain(req, null));
    }

    @PostMapping("/biz-domain/get")
    public PermResult<BizDomainResp> getBizDomain(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(configManageService.getBizDomain(req.tenantId(), req.id()));
    }

    @PostMapping("/biz-domain/list")
    public PermResult<List<BizDomainResp>> listBizDomains(@Valid @RequestBody TenantIdReq req) {
        return PermResult.success(configManageService.listBizDomains(req.tenantId()));
    }

    @PostMapping("/biz-domain/delete")
    public PermResult<Void> deleteBizDomain(@Valid @RequestBody IdWithTenantReq req) {
        configManageService.deleteBizDomain(req.tenantId(), req.id(), null);
        return PermResult.success();
    }

    @PostMapping("/biz-domain/update")
    public PermResult<BizDomainResp> updateBizDomain(@Valid @RequestBody BizDomainUpdateReq req) {
        return PermResult.success(configManageService.updateBizDomain(req, null));
    }

    // ===== DomainConfig =====

    @PostMapping("/domain-config/upsert")
    public PermResult<Void> upsertDomainConfig(@Valid @RequestBody DomainConfigReq req) {
        configManageService.upsertDomainConfig(req);
        return PermResult.success();
    }

    @PostMapping("/domain-config/get")
    public PermResult<DomainConfigResp> getDomainConfig(@Valid @RequestBody DomainConfigGetReq req) {
        return PermResult.success(configManageService.getDomainConfig(req.tenantId(), req.bizDomainId(), req.configType()));
    }

    @PostMapping("/domain-config/list")
    public PermResult<List<DomainConfigResp>> listDomainConfigs(@Valid @RequestBody DomainConfigListReq req) {
        return PermResult.success(configManageService.listDomainConfigs(req.tenantId(), req.bizDomainId()));
    }

    // ===== ServiceConfig =====

    @PostMapping("/service/create")
    public PermResult<ServiceConfigResp> createServiceConfig(@Valid @RequestBody ServiceConfigReq req) {
        return PermResult.success(configManageService.createServiceConfig(req, null));
    }

    @PostMapping("/service/get")
    public PermResult<ServiceConfigResp> getServiceConfig(@Valid @RequestBody ServiceConfigGetReq req) {
        return PermResult.success(configManageService.getServiceConfig(req.tenantId(), req.serviceCode()));
    }

    @PostMapping("/service/list")
    public PermResult<List<ServiceConfigResp>> listServiceConfigs(@Valid @RequestBody TenantIdReq req) {
        return PermResult.success(configManageService.listServiceConfigs(req.tenantId()));
    }

    @PostMapping("/service/delete")
    public PermResult<Void> deleteServiceConfig(@Valid @RequestBody ServiceConfigDeleteReq req) {
        configManageService.deleteServiceConfig(req.tenantId(), req.serviceCode(), null);
        return PermResult.success();
    }

    @PostMapping("/service/update")
    public PermResult<ServiceConfigResp> updateServiceConfig(@Valid @RequestBody ServiceConfigReq req) {
        return PermResult.success(configManageService.updateServiceConfig(req, null));
    }

    // ===== SystemConfig =====

    @PostMapping("/system/upsert")
    public PermResult<Void> upsertSystemConfig(@Valid @RequestBody SystemConfigReq req) {
        configManageService.upsertSystemConfig(req);
        return PermResult.success();
    }

    @PostMapping("/system/get")
    public PermResult<SystemConfigResp> getSystemConfig(@Valid @RequestBody SystemConfigGetReq req) {
        return PermResult.success(configManageService.getSystemConfig(req.tenantId(), req.configKey()));
    }

    @PostMapping("/system/list")
    public PermResult<List<SystemConfigResp>> listSystemConfigs(@Valid @RequestBody TenantIdReq req) {
        return PermResult.success(configManageService.listSystemConfigs(req.tenantId()));
    }
}
