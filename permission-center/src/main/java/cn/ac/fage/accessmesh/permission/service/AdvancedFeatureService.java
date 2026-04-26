package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.resp.*;

import java.util.List;

/**
 * Advanced features — PermissionCondition, PermissionConflictRule, ChangeLog, OperationLog.
 */
public interface AdvancedFeatureService {

    // ===== PermissionCondition =====
    ConditionResp createCondition(ConditionCreateReq req, Long operatorId);
    ConditionResp getCondition(Long tenantId, Long conditionId);
    List<ConditionResp> listConditions(Long tenantId);
    void deleteCondition(Long tenantId, Long conditionId, Long operatorId);
    void setConditionEnabled(Long tenantId, Long conditionId, boolean enabled, Long operatorId);

    // ===== PermissionConflictRule =====
    ConflictRuleResp createConflictRule(ConflictRuleReq req, Long operatorId);
    ConflictRuleResp getConflictRule(Long tenantId, Long ruleId);
    List<ConflictRuleResp> listConflictRules(Long tenantId);
    void deleteConflictRule(Long tenantId, Long ruleId, Long operatorId);

    // ===== PermissionChangeLog =====
    List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId, int offset, int limit);

    // ===== OperationLog =====
    List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit);
}
