package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigGetReq;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigListReq;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.permission.dto.resp.DomainConfigResp;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Domain config management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/domain-config")
public class DomainConfigController {

    private final ConfigManageService configManageService;

    public DomainConfigController(ConfigManageService configManageService) {
        this.configManageService = configManageService;
    }

    @PostMapping("/save")
    public PermResult<Void> upsertDomainConfig(@Valid @RequestBody DomainConfigReq req) {
        configManageService.upsertDomainConfig(TenantContextHolder.getTenantId(), req);
        return PermResult.success();
    }

    @PostMapping("/detail")
    public PermResult<DomainConfigResp> getDomainConfig(@Valid @RequestBody DomainConfigGetReq req) {
        return PermResult.success(configManageService.getDomainConfig(TenantContextHolder.getTenantId(), req.bizDomainId(), req.configType()));
    }

    @PostMapping("/list")
    public PermResult<List<DomainConfigResp>> listDomainConfigs(@Valid @RequestBody DomainConfigListReq req) {
        return PermResult.success(configManageService.listDomainConfigs(TenantContextHolder.getTenantId(), req.bizDomainId()));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteDomainConfig(@Valid @RequestBody DomainConfigGetReq req) {
        configManageService.deleteDomainConfig(TenantContextHolder.getTenantId(), req.bizDomainId(), req.configType());
        return PermResult.success();
    }
}
