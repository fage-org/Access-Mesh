package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRolesListReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Advanced features — PermissionCondition, PermissionConflictRule, ChangeLog, OperationLog, ResourceDependency, GroupRole.
 */
public interface AdvancedFeatureService {

    // ===== PermissionCondition =====
    ConditionResp createCondition(Long tenantId, ConditionCreateReq req, Long operatorId);
    ConditionResp getCondition(Long tenantId, Long conditionId);
    ConditionResp updateCondition(Long tenantId, ConditionUpdateReq req, Long operatorId);
    List<ConditionResp> listConditions(Long tenantId);
    void deleteCondition(Long tenantId, Long conditionId, Long operatorId);

    void deleteConditionsByIds(Long tenantId, List<Long> ids, Long operatorId);

    // ===== PermissionConflictRule =====
    ConflictRuleResp createConflictRule(Long tenantId, ConflictRuleReq req, Long operatorId);
    ConflictRuleResp getConflictRule(Long tenantId, Long ruleId);
    List<ConflictRuleResp> listConflictRules(Long tenantId);
    ConflictRuleResp updateConflictRule(Long tenantId, ConflictRuleUpdateReq req, Long operatorId);
    ConflictDetectResp detectConflictRule(Long tenantId, ConflictRuleDetectReq req);
    void deleteConflictRule(Long tenantId, Long ruleId, Long operatorId);

    void deleteConflictRulesByIds(Long tenantId, List<Long> ids, Long operatorId);

    // ===== PermissionChangeLog =====
    List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId, int offset, int limit);
    long countChangeLogs(Long tenantId, String entityType, Long entityId);
    List<ChangeLogResp> listChangeLogsForUser(Long tenantId, Long userId, int offset, int limit);
    long countChangeLogsForUser(Long tenantId, Long userId);

    List<ChangeLogResp> listChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                               LocalDateTime since, LocalDateTime until,
                                               List<String> eventTypes, int offset, int limit);

    long countChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                 LocalDateTime since, LocalDateTime until,
                                 List<String> eventTypes);

    // ===== OperationLog =====
    List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit);
    long countOperationLogs(Long tenantId, String module, String action);

    // ===== ResourceDependency =====
    ResourceDependencyResp createDependency(Long tenantId, ResourceDependencyCreateReq req, Long operatorId);
    List<ResourceDependencyResp> listDependencies(Long tenantId, Long resourceEntityId);
    List<ResourceDependencyResp> listAllDependencies(Long tenantId);
    ResourceDependencyResp updateDependency(Long tenantId, ResourceDependencyUpdateReq req, Long operatorId);
    boolean hasDependencyCycle(Long tenantId, Long resourceEntityId, Long dependsOnResourceEntityId);
    void deleteDependency(Long tenantId, Long dependencyId, Long operatorId);
    void deleteDependencies(Long tenantId, List<Long> dependencyIds, Long operatorId);
    void batchSyncDependencies(Long tenantId, DependencyBatchSyncReq req, Long operatorId);

    // ===== GroupRole extra-roles =====
    void addGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId);

    void removeGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId);

    List<RoleSummaryResp> listGroupRoleExtraRoles(Long tenantId, GroupRoleExtraRolesListReq req);
}
