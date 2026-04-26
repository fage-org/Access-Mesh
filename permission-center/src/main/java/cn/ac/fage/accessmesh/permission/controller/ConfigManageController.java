package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
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
    public PermResult<TypeDefinitionResp> getType(@RequestParam Long tenantId, @RequestParam Long typeId) {
        return PermResult.success(configManageService.getType(tenantId, typeId));
    }

    @PostMapping("/type/list")
    public PermResult<List<TypeDefinitionResp>> listTypes(@RequestParam Long tenantId,
                                                           @RequestParam(required = false) Long bizDomainId) {
        return PermResult.success(configManageService.listTypes(tenantId, bizDomainId));
    }

    @PostMapping("/type/delete")
    public PermResult<Void> deleteType(@RequestParam Long tenantId, @RequestParam Long typeId) {
        configManageService.deleteType(tenantId, typeId, null);
        return PermResult.success();
    }

    // ===== BizDomain =====

    @PostMapping("/biz-domain/create")
    public PermResult<BizDomainResp> createBizDomain(@Valid @RequestBody BizDomainCreateReq req) {
        return PermResult.success(configManageService.createBizDomain(req, null));
    }

    @PostMapping("/biz-domain/get")
    public PermResult<BizDomainResp> getBizDomain(@RequestParam Long tenantId, @RequestParam Long domainId) {
        return PermResult.success(configManageService.getBizDomain(tenantId, domainId));
    }

    @PostMapping("/biz-domain/list")
    public PermResult<List<BizDomainResp>> listBizDomains(@RequestParam Long tenantId) {
        return PermResult.success(configManageService.listBizDomains(tenantId));
    }

    @PostMapping("/biz-domain/delete")
    public PermResult<Void> deleteBizDomain(@RequestParam Long tenantId, @RequestParam Long domainId) {
        configManageService.deleteBizDomain(tenantId, domainId, null);
        return PermResult.success();
    }

    // ===== DomainConfig =====

    @PostMapping("/domain-config/upsert")
    public PermResult<Void> upsertDomainConfig(@Valid @RequestBody DomainConfigReq req) {
        configManageService.upsertDomainConfig(req);
        return PermResult.success();
    }

    @PostMapping("/domain-config/get")
    public PermResult<DomainConfigResp> getDomainConfig(@RequestParam Long tenantId,
                                                         @RequestParam Long bizDomainId,
                                                         @RequestParam String configType) {
        return PermResult.success(configManageService.getDomainConfig(tenantId, bizDomainId, configType));
    }

    @PostMapping("/domain-config/list")
    public PermResult<List<DomainConfigResp>> listDomainConfigs(@RequestParam Long tenantId,
                                                                 @RequestParam(required = false) Long bizDomainId) {
        return PermResult.success(configManageService.listDomainConfigs(tenantId, bizDomainId));
    }

    // ===== ServiceConfig =====

    @PostMapping("/service/create")
    public PermResult<ServiceConfigResp> createServiceConfig(@Valid @RequestBody ServiceConfigReq req) {
        return PermResult.success(configManageService.createServiceConfig(req, null));
    }

    @PostMapping("/service/get")
    public PermResult<ServiceConfigResp> getServiceConfig(@RequestParam Long tenantId,
                                                           @RequestParam String serviceCode) {
        return PermResult.success(configManageService.getServiceConfig(tenantId, serviceCode));
    }

    @PostMapping("/service/list")
    public PermResult<List<ServiceConfigResp>> listServiceConfigs(@RequestParam Long tenantId) {
        return PermResult.success(configManageService.listServiceConfigs(tenantId));
    }

    @PostMapping("/service/delete")
    public PermResult<Void> deleteServiceConfig(@RequestParam Long tenantId, @RequestParam String serviceCode) {
        configManageService.deleteServiceConfig(tenantId, serviceCode, null);
        return PermResult.success();
    }

    // ===== SystemConfig =====

    @PostMapping("/system/upsert")
    public PermResult<Void> upsertSystemConfig(@Valid @RequestBody SystemConfigReq req) {
        configManageService.upsertSystemConfig(req);
        return PermResult.success();
    }

    @PostMapping("/system/get")
    public PermResult<SystemConfigResp> getSystemConfig(@RequestParam Long tenantId,
                                                         @RequestParam String configKey) {
        return PermResult.success(configManageService.getSystemConfig(tenantId, configKey));
    }

    @PostMapping("/system/list")
    public PermResult<List<SystemConfigResp>> listSystemConfigs(@RequestParam Long tenantId) {
        return PermResult.success(configManageService.listSystemConfigs(tenantId));
    }
}
