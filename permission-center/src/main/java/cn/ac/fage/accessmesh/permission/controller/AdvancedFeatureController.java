package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionSetEnabledReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdWithTenantReq;
import cn.ac.fage.accessmesh.permission.dto.req.TenantIdReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConditionResp;
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
    public PermResult<ConditionResp> getCondition(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(advancedFeatureService.getCondition(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/list")
    public PermResult<List<ConditionResp>> listConditions(@Valid @RequestBody TenantIdReq req) {
        return PermResult.success(advancedFeatureService.listConditions(TenantContextHolder.getTenantId()));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteCondition(@Valid @RequestBody IdWithTenantReq req) {
        advancedFeatureService.deleteCondition(TenantContextHolder.getTenantId(), req.id(), null);
        return PermResult.success();
    }

    @PostMapping("/set-enabled")
    public PermResult<Void> setConditionEnabled(@Valid @RequestBody ConditionSetEnabledReq req) {
        advancedFeatureService.setConditionEnabled(TenantContextHolder.getTenantId(), req.conditionId(), req.enabled(), null);
        return PermResult.success();
    }
}