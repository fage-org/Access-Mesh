package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.*;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceTreeResp;
import cn.ac.fage.accessmesh.permission.service.ResourceManageService;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Resource entity management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/resource-entity")
public class ResourceManageController {

    private final ResourceManageService resourceManageService;

    public ResourceManageController(ResourceManageService resourceManageService) {
        this.resourceManageService = resourceManageService;
    }

    @PostMapping("/create")
    public PermResult<ResourceResp> createResource(@Valid @RequestBody ResourceCreateReq req) {
        return PermResult.success(resourceManageService.createResource(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/batch-create")
    public PermResult<ItemsResp<ResourceResp>> batchCreateResources(@Valid @RequestBody ResourceBatchCreateReq req) {
        return PermResult.success(new ItemsResp<>(
            resourceManageService.batchCreateResources(TenantContextHolder.getTenantId(), req.items(), null)
        ));
    }

    @PostMapping("/detail")
    public PermResult<ResourceResp> getResource(@Valid @RequestBody IdReq req) {
        return PermResult.success(resourceManageService.getResource(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/update")
    public PermResult<ResourceResp> updateResource(@Valid @RequestBody ResourceUpdateReq req) {
        return PermResult.success(resourceManageService.updateResource(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/move")
    public PermResult<Void> moveResource(@Valid @RequestBody ResourceMoveReq req) {
        resourceManageService.moveResource(TenantContextHolder.getTenantId(), req.resourceId(), req.parentId(), null);
        return PermResult.success();
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteResource(@Valid @RequestBody IdsReq req) {
        resourceManageService.deleteResources(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    @PostMapping("/tree")
    public PermResult<ItemsResp<ResourceTreeResp>> getResourceTree(@Valid @RequestBody ResourceTreeReq req) {
        return PermResult.success(new ItemsResp<>(
            resourceManageService.getResourceTree(TenantContextHolder.getTenantId(), req.resourceTypeCode(), req.domainCode())
        ));
    }

    @PostMapping("/list")
    public PermResult<PaginatedResp<ResourceResp>> listResources(@Valid @RequestBody ResourceListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = resourceManageService.countResources(tenantId, req.resourceTypeCode(), req.domainCode());
        List<ResourceResp> items = resourceManageService.listResources(tenantId, req.resourceTypeCode(), req.domainCode(), offset, pageSize);
        return PermResult.success(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }
}
