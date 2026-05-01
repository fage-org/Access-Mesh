package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeListReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.TypeDefinitionResp;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Type definition management API.
 * All APIs: POST + JSON Body. tenantId from X-Tenant-Id header.
 */
@RestController
@RequestMapping("/api/perm/type-definition")
public class ConfigManageController {

    private final ConfigManageService configManageService;

    public ConfigManageController(ConfigManageService configManageService) {
        this.configManageService = configManageService;
    }

    @PostMapping("/create")
    public PermResult<TypeDefinitionResp> createType(@Valid @RequestBody TypeCreateReq req) {
        return PermResult.success(configManageService.createType(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detail")
    public PermResult<TypeDefinitionResp> getType(@Valid @RequestBody IdReq req) {
        return PermResult.success(configManageService.getType(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/list")
    public PermResult<ItemsResp<TypeDefinitionResp>> listTypes(@Valid @RequestBody TypeListReq req) {
        return PermResult.success(new ItemsResp<>(
            configManageService.listTypes(TenantContextHolder.getTenantId(), req.domainCode())
        ));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteType(@Valid @RequestBody IdsReq req) {
        configManageService.deleteTypesByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    @PostMapping("/update")
    public PermResult<TypeDefinitionResp> updateType(@Valid @RequestBody TypeUpdateReq req) {
        return PermResult.success(configManageService.updateType(TenantContextHolder.getTenantId(), req, null));
    }
}