package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigGetReq;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigListReq;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.resp.DomainConfigResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
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
    public PermResult<DomainConfigResp> upsertDomainConfig(@Valid @RequestBody DomainConfigReq req) {
        return PermResult.success(configManageService.upsertDomainConfig(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/detail")
    public PermResult<DomainConfigResp> getDomainConfig(@Valid @RequestBody DomainConfigGetReq req) {
        return PermResult.success(configManageService.getDomainConfig(TenantContextHolder.getTenantId(), req.domainCode(), req.configType()));
    }

    @PostMapping("/list")
    public PermResult<ItemsResp<DomainConfigResp>> listDomainConfigs(@Valid @RequestBody DomainConfigListReq req) {
        return PermResult.success(new ItemsResp<>(
            configManageService.listDomainConfigs(TenantContextHolder.getTenantId(), req.domainCode())
        ));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteDomainConfig(@Valid @RequestBody IdsReq req) {
        configManageService.deleteDomainConfigsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }
}
