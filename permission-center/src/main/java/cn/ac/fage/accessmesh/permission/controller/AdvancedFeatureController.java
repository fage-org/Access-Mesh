package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.*;
import cn.ac.fage.accessmesh.permission.dto.resp.*;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

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
    public PermResult<ConditionResp> getCondition(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(advancedFeatureService.getCondition(req.tenantId(), req.id()));
    }

    @PostMapping("/condition/list")
    public PermResult<List<ConditionResp>> listConditions(@Valid @RequestBody TenantIdReq req) {
        return PermResult.success(advancedFeatureService.listConditions(req.tenantId()));
    }

    @PostMapping("/condition/delete")
    public PermResult<Void> deleteCondition(@Valid @RequestBody IdWithTenantReq req) {
        advancedFeatureService.deleteCondition(req.tenantId(), req.id(), null);
        return PermResult.success();
    }

    @PostMapping("/condition/set-enabled")
    public PermResult<Void> setConditionEnabled(@Valid @RequestBody ConditionSetEnabledReq req) {
        advancedFeatureService.setConditionEnabled(req.tenantId(), req.conditionId(), req.enabled(), null);
        return PermResult.success();
    }

    // ===== PermissionConflictRule =====

    @PostMapping("/conflict-rule/create")
    public PermResult<ConflictRuleResp> createConflictRule(@Valid @RequestBody ConflictRuleReq req) {
        return PermResult.success(advancedFeatureService.createConflictRule(req, null));
    }

    @PostMapping("/conflict-rule/get")
    public PermResult<ConflictRuleResp> getConflictRule(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(advancedFeatureService.getConflictRule(req.tenantId(), req.id()));
    }

    @PostMapping("/conflict-rule/list")
    public PermResult<List<ConflictRuleResp>> listConflictRules(@Valid @RequestBody TenantIdReq req) {
        return PermResult.success(advancedFeatureService.listConflictRules(req.tenantId()));
    }

    @PostMapping("/conflict-rule/delete")
    public PermResult<Void> deleteConflictRule(@Valid @RequestBody IdWithTenantReq req) {
        advancedFeatureService.deleteConflictRule(req.tenantId(), req.id(), null);
        return PermResult.success();
    }

    // ===== PermissionChangeLog =====

    @PostMapping("/change-log/list")
    public PermResult<List<ChangeLogResp>> listChangeLogs(@Valid @RequestBody ChangeLogListReq req) {
        return PermResult.success(advancedFeatureService.listChangeLogs(
                req.tenantId(), req.entityType(), req.entityId(),
                req.pageNum() != null ? req.pageNum() : 0,
                req.pageSize() != null ? req.pageSize() : 20));
    }

    // ===== OperationLog =====

    @PostMapping("/operation-log/list")
    public PermResult<List<OperationLogResp>> listOperationLogs(@Valid @RequestBody OperationLogListReq req) {
        return PermResult.success(advancedFeatureService.listOperationLogs(
                req.tenantId(), req.module(), req.action(),
                req.pageNum() != null ? req.pageNum() : 0,
                req.pageSize() != null ? req.pageSize() : 20));
    }

    // ===== ResourceDependency =====

    @PostMapping("/dependency/create")
    public PermResult<ResourceDependencyResp> createDependency(@Valid @RequestBody ResourceDependencyCreateReq req) {
        return PermResult.success(advancedFeatureService.createDependency(req, null));
    }

    @PostMapping("/dependency/list")
    public PermResult<List<ResourceDependencyResp>> listDependencies(@Valid @RequestBody DependencyListReq req) {
        return PermResult.success(advancedFeatureService.listDependencies(req.tenantId(), req.resourceEntityId()));
    }

    @PostMapping("/dependency/delete")
    public PermResult<Void> deleteDependency(@Valid @RequestBody IdWithTenantReq req) {
        advancedFeatureService.deleteDependency(req.tenantId(), req.id(), null);
        return PermResult.success();
    }

    @PostMapping("/dependency/batch-sync")
    public PermResult<Void> batchSyncDependencies(@Valid @RequestBody DependencyBatchSyncReq req) {
        advancedFeatureService.batchSyncDependencies(req.tenantId(), req.roleId(), null);
        return PermResult.success();
    }

    // ===== GroupRole extra-roles =====

    @PostMapping("/group-role/extra/add")
    public PermResult<Void> addGroupRoleExtraRole(@Valid @RequestBody GroupRoleExtraRoleReq req) {
        advancedFeatureService.addGroupRoleExtraRole(req.tenantId(), req.groupId(), req.basicRoleId(), null);
        return PermResult.success();
    }

    @PostMapping("/group-role/extra/remove")
    public PermResult<Void> removeGroupRoleExtraRole(@Valid @RequestBody GroupRoleExtraRoleReq req) {
        advancedFeatureService.removeGroupRoleExtraRole(req.tenantId(), req.groupId(), req.basicRoleId(), null);
        return PermResult.success();
    }

    @PostMapping("/group-role/extra/list")
    public PermResult<Set<Long>> listGroupRoleExtraRoles(@Valid @RequestBody GroupRoleExtraRoleReq req) {
        return PermResult.success(advancedFeatureService.listGroupRoleExtraRoles(req.tenantId(), req.groupId()));
    }
}
