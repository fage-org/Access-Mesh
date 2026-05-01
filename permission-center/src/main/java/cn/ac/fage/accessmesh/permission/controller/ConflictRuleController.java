package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.EmptyReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
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
    public PermResult<ConflictRuleResp> getConflictRule(@Valid @RequestBody IdReq req) {
        return PermResult.success(advancedFeatureService.getConflictRule(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/list")
    public PermResult<ItemsResp<ConflictRuleResp>> listConflictRules(@Valid @RequestBody EmptyReq req) {
        return PermResult.success(new ItemsResp<>(
            advancedFeatureService.listConflictRules(TenantContextHolder.getTenantId())
        ));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteConflictRule(@Valid @RequestBody IdsReq req) {
        advancedFeatureService.deleteConflictRulesByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    @PostMapping("/update")
    public PermResult<ConflictRuleResp> updateConflictRule(@Valid @RequestBody ConflictRuleUpdateReq req) {
        return PermResult.success(advancedFeatureService.updateConflictRule(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detect")
    public PermResult<ConflictDetectResp> detectConflictRule(@Valid @RequestBody ConflictRuleDetectReq req) {
        return PermResult.success(advancedFeatureService.detectConflictRule(TenantContextHolder.getTenantId(), req));
    }
}
