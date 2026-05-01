package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingListReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.service.ResourceManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resource API mapping management.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/resource-api-mapping")
public class ResourceApiMappingController {

    private final ResourceManageService resourceManageService;

    public ResourceApiMappingController(ResourceManageService resourceManageService) {
        this.resourceManageService = resourceManageService;
    }

    @PostMapping("/create")
    public PermResult<ApiMappingResp> addApiMapping(@Valid @RequestBody ApiMappingAddReq req) {
        return PermResult.success(resourceManageService.addApiMapping(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/update")
    public PermResult<ApiMappingResp> updateApiMapping(@Valid @RequestBody ApiMappingUpdateReq req) {
        return PermResult.success(resourceManageService.updateApiMapping(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/remove")
    public PermResult<Void> removeApiMapping(@Valid @RequestBody IdsReq req) {
        resourceManageService.removeApiMappingsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    @PostMapping("/list")
    public PermResult<ItemsResp<ApiMappingResp>> listApiMappings(@Valid @RequestBody ApiMappingListReq req) {
        return PermResult.success(new ItemsResp<>(
            resourceManageService.listApiMappings(TenantContextHolder.getTenantId(), req.resourceId(), req.serviceCode())
        ));
    }
}
