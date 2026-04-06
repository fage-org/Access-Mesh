package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.DependencyCheckReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.ResourceDependencyListReq;
import org.dromara.permission.domain.dto.ResourceDependencySaveReq;
import org.dromara.permission.domain.vo.DependencyCheckGapVo;
import org.dromara.permission.domain.vo.DependencyCheckVo;
import org.dromara.permission.domain.vo.DependencyPathNodeVo;
import org.dromara.permission.domain.vo.ResourceDependencyVo;
import org.dromara.permission.model.permission.DependencyGap;
import org.dromara.permission.service.ResourceDependencyService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 资源依赖 resource_dependency 接口
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm/resource-dependencies")
public class ResourceDependencyController {

    private final ResourceDependencyService resourceDependencyService;

    @GetMapping
    public R<List<ResourceDependencyVo>> listByContract(@RequestParam("tenantId") Long tenantId,
                                                        @RequestParam(value = "resourceEntityId", required = false) Long resourceEntityId,
                                                        @RequestParam(value = "dependsOnResourceEntityId", required = false) Long dependsOnResourceEntityId,
                                                        @RequestParam(value = "sourceOperationPermissionId", required = false) Long sourceOperationPermissionId,
                                                        @RequestParam(value = "requiredOperationPermissionId", required = false) Long requiredOperationPermissionId) {
        ResourceDependencyListReq req = new ResourceDependencyListReq();
        req.setTenantId(tenantId);
        req.setResourceEntityId(resourceEntityId);
        req.setDependsOnResourceEntityId(dependsOnResourceEntityId);
        req.setSourceOperationPermissionId(sourceOperationPermissionId);
        req.setRequiredOperationPermissionId(requiredOperationPermissionId);
        return R.ok(resourceDependencyService.list(req));
    }

    @PostMapping("/list")
    public R<List<ResourceDependencyVo>> list(@Validated @RequestBody ResourceDependencyListReq req) {
        return R.ok(resourceDependencyService.list(req));
    }

    @GetMapping("/graph")
    public R<List<ResourceDependencyVo>> graphByContract(@RequestParam("tenantId") Long tenantId,
                                                         @RequestParam(value = "resourceEntityId", required = false) Long resourceEntityId,
                                                         @RequestParam(value = "dependsOnResourceEntityId", required = false) Long dependsOnResourceEntityId,
                                                         @RequestParam(value = "sourceOperationPermissionId", required = false) Long sourceOperationPermissionId,
                                                         @RequestParam(value = "requiredOperationPermissionId", required = false) Long requiredOperationPermissionId,
                                                         @RequestParam(value = "graphMode", required = false) String graphMode) {
        ResourceDependencyListReq req = new ResourceDependencyListReq();
        req.setTenantId(tenantId);
        req.setResourceEntityId(resourceEntityId);
        req.setDependsOnResourceEntityId(dependsOnResourceEntityId);
        req.setSourceOperationPermissionId(sourceOperationPermissionId);
        req.setRequiredOperationPermissionId(requiredOperationPermissionId);
        req.setGraphMode(graphMode);
        return R.ok(resourceDependencyService.graph(req));
    }

    @PostMapping("/graph")
    public R<List<ResourceDependencyVo>> graph(@Validated @RequestBody ResourceDependencyListReq req) {
        return R.ok(resourceDependencyService.graph(req));
    }

    @PostMapping
    public R<Void> saveByContract(@Validated @RequestBody ResourceDependencySaveReq req) {
        resourceDependencyService.save(req);
        return R.ok();
    }

    @PostMapping("/save")
    public R<Void> save(@Validated @RequestBody ResourceDependencySaveReq req) {
        resourceDependencyService.save(req);
        return R.ok();
    }

    @DeleteMapping
    public R<Void> removeByContract(@Validated @RequestBody IdsReq req) {
        resourceDependencyService.remove(req);
        return R.ok();
    }

    @PostMapping("/remove")
    public R<Void> remove(@Validated @RequestBody IdsReq req) {
        resourceDependencyService.remove(req);
        return R.ok();
    }

    @PostMapping("/check")
    public R<DependencyCheckVo> check(@Validated @RequestBody DependencyCheckReq req) {
        return R.ok(toVo(resourceDependencyService.check(req)));
    }

    private DependencyCheckVo toVo(org.dromara.permission.model.permission.DependencyCheckResult result) {
        DependencyCheckVo vo = new DependencyCheckVo();
        vo.setSatisfied(result != null && result.isSatisfied());
        if (result == null || result.getGaps() == null) {
            return vo;
        }
        vo.setGaps(result.getGaps().stream().map(this::toGapVo).collect(Collectors.toList()));
        return vo;
    }

    private DependencyCheckGapVo toGapVo(DependencyGap gap) {
        DependencyCheckGapVo vo = new DependencyCheckGapVo();
        vo.setResourceEntityId(gap.getResourceEntityId());
        vo.setOperationPermissionId(gap.getOperationPermissionId());
        if (gap.getPath() != null) {
            vo.setPath(gap.getPath().stream().map(node -> {
                DependencyPathNodeVo pathNode = new DependencyPathNodeVo();
                pathNode.setResourceEntityId(node.getResourceEntityId());
                pathNode.setOperationPermissionId(node.getOperationPermissionId());
                return pathNode;
            }).collect(Collectors.toList()));
        }
        return vo;
    }
}
