package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.resp.*;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Advanced feature APIs — PermissionCondition, PermissionConflictRule, ChangeLog, OperationLog.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/advanced")
public class AdvancedFeatureController {

    private final AdvancedFeatureService advancedFeatureService;

    public AdvancedFeatureController(AdvancedFeatureService advancedFeatureService) {
        this.advancedFeatureService = advancedFeatureService;
    }

    // ===== PermissionCondition =====

    @PostMapping("/condition/create")
    public PermResult<ConditionResp> createCondition(@Valid @RequestBody ConditionCreateReq req) {
        return PermResult.success(advancedFeatureService.createCondition(req, null));
    }

    @PostMapping("/condition/get")
    public PermResult<ConditionResp> getCondition(@RequestParam Long tenantId, @RequestParam Long conditionId) {
        return PermResult.success(advancedFeatureService.getCondition(tenantId, conditionId));
    }

    @PostMapping("/condition/list")
    public PermResult<List<ConditionResp>> listConditions(@RequestParam Long tenantId) {
        return PermResult.success(advancedFeatureService.listConditions(tenantId));
    }

    @PostMapping("/condition/delete")
    public PermResult<Void> deleteCondition(@RequestParam Long tenantId, @RequestParam Long conditionId) {
        advancedFeatureService.deleteCondition(tenantId, conditionId, null);
        return PermResult.success();
    }

    @PostMapping("/condition/set-enabled")
    public PermResult<Void> setConditionEnabled(@RequestParam Long tenantId,
                                                  @RequestParam Long conditionId,
                                                  @RequestParam boolean enabled) {
        advancedFeatureService.setConditionEnabled(tenantId, conditionId, enabled, null);
        return PermResult.success();
    }

    // ===== PermissionConflictRule =====

    @PostMapping("/conflict-rule/create")
    public PermResult<ConflictRuleResp> createConflictRule(@Valid @RequestBody ConflictRuleReq req) {
        return PermResult.success(advancedFeatureService.createConflictRule(req, null));
    }

    @PostMapping("/conflict-rule/get")
    public PermResult<ConflictRuleResp> getConflictRule(@RequestParam Long tenantId, @RequestParam Long ruleId) {
        return PermResult.success(advancedFeatureService.getConflictRule(tenantId, ruleId));
    }

    @PostMapping("/conflict-rule/list")
    public PermResult<List<ConflictRuleResp>> listConflictRules(@RequestParam Long tenantId) {
        return PermResult.success(advancedFeatureService.listConflictRules(tenantId));
    }

    @PostMapping("/conflict-rule/delete")
    public PermResult<Void> deleteConflictRule(@RequestParam Long tenantId, @RequestParam Long ruleId) {
        advancedFeatureService.deleteConflictRule(tenantId, ruleId, null);
        return PermResult.success();
    }

    // ===== PermissionChangeLog =====

    @PostMapping("/change-log/list")
    public PermResult<List<ChangeLogResp>> listChangeLogs(@RequestParam Long tenantId,
                                                            @RequestParam(required = false) String entityType,
                                                            @RequestParam(required = false) Long entityId,
                                                            @RequestParam(defaultValue = "0") int offset,
                                                            @RequestParam(defaultValue = "20") int limit) {
        return PermResult.success(advancedFeatureService.listChangeLogs(tenantId, entityType, entityId, offset, limit));
    }

    // ===== OperationLog =====

    @PostMapping("/operation-log/list")
    public PermResult<List<OperationLogResp>> listOperationLogs(@RequestParam Long tenantId,
                                                                  @RequestParam(required = false) String module,
                                                                  @RequestParam(required = false) String action,
                                                                  @RequestParam(defaultValue = "0") int offset,
                                                                  @RequestParam(defaultValue = "20") int limit) {
        return PermResult.success(advancedFeatureService.listOperationLogs(tenantId, module, action, offset, limit));
    }
}
