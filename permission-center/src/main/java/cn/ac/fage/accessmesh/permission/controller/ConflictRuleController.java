package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdWithTenantReq;
import cn.ac.fage.accessmesh.permission.dto.req.TenantIdReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConflictRuleResp;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conflict rule management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/conflict-rule")
public class ConflictRuleController {

    private final AdvancedFeatureService advancedFeatureService;

    public ConflictRuleController(AdvancedFeatureService advancedFeatureService) {
        this.advancedFeatureService = advancedFeatureService;
    }

    @PostMapping("/create")
    public PermResult<ConflictRuleResp> createConflictRule(@Valid @RequestBody ConflictRuleReq req) {
        return PermResult.success(advancedFeatureService.createConflictRule(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detail")
    public PermResult<ConflictRuleResp> getConflictRule(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(advancedFeatureService.getConflictRule(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/list")
    public PermResult<List<ConflictRuleResp>> listConflictRules(@Valid @RequestBody TenantIdReq req) {
        return PermResult.success(advancedFeatureService.listConflictRules(TenantContextHolder.getTenantId()));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteConflictRule(@Valid @RequestBody IdWithTenantReq req) {
        advancedFeatureService.deleteConflictRule(TenantContextHolder.getTenantId(), req.id(), null);
        return PermResult.success();
    }
}
