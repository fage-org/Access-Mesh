package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.EmptyReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConditionResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Permission condition management API.
 * All APIs: POST + JSON Body. tenantId from X-Tenant-Id header.
 */
@RestController
@RequestMapping("/api/perm/permission-condition")
public class AdvancedFeatureController {

    private final AdvancedFeatureService advancedFeatureService;

    public AdvancedFeatureController(AdvancedFeatureService advancedFeatureService) {
        this.advancedFeatureService = advancedFeatureService;
    }

    @PostMapping("/create")
    public PermResult<ConditionResp> createCondition(@Valid @RequestBody ConditionCreateReq req) {
        return PermResult.success(advancedFeatureService.createCondition(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detail")
    public PermResult<ConditionResp> getCondition(@Valid @RequestBody IdReq req) {
        return PermResult.success(advancedFeatureService.getCondition(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/list")
    public PermResult<ItemsResp<ConditionResp>> listConditions(@Valid @RequestBody EmptyReq req) {
        return PermResult.success(new ItemsResp<>(
            advancedFeatureService.listConditions(TenantContextHolder.getTenantId())
        ));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteCondition(@Valid @RequestBody IdsReq req) {
        advancedFeatureService.deleteConditionsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    @PostMapping("/update")
    public PermResult<ConditionResp> updateCondition(@Valid @RequestBody ConditionUpdateReq req) {
        return PermResult.success(advancedFeatureService.updateCondition(TenantContextHolder.getTenantId(), req, null));
    }
}