package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingListReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingRemoveReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.service.ResourceManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public PermResult<Void> addApiMapping(@Valid @RequestBody ApiMappingAddReq req) {
        ApiMappingReq mappingReq = new ApiMappingReq(
                req.serviceCode(), req.httpMethod(),
                req.pathPattern(), req.matchOrder(), req.enabled(), req.extra());
        resourceManageService.addApiMapping(TenantContextHolder.getTenantId(), req.resourceId(), mappingReq);
        return PermResult.success();
    }

    @PostMapping("/update")
    public PermResult<Void> updateApiMapping(@Valid @RequestBody ApiMappingUpdateReq req) {
        ApiMappingReq mappingReq = new ApiMappingReq(
                null, req.method(), req.apiPath(), null, null, req.description());
        resourceManageService.updateApiMapping(TenantContextHolder.getTenantId(), req.resourceId(), req.mappingId(), mappingReq);
        return PermResult.success();
    }

    @PostMapping("/remove")
    public PermResult<Void> removeApiMapping(@Valid @RequestBody ApiMappingRemoveReq req) {
        resourceManageService.removeApiMapping(TenantContextHolder.getTenantId(), req.resourceId(), req.mappingId(), null);
        return PermResult.success();
    }

    @PostMapping("/list")
    public PermResult<List<ApiMappingResp>> listApiMappings(@Valid @RequestBody ApiMappingListReq req) {
        return PermResult.success(resourceManageService.listApiMappings(TenantContextHolder.getTenantId(), req.resourceId()));
    }
}
