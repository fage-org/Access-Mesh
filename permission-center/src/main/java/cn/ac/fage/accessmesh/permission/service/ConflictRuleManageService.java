package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ConflictRuleResp;

import java.util.List;

/**
 * Permission conflict rule management service.
 */
public interface ConflictRuleManageService {

    ConflictRuleResp createConflictRule(Long tenantId, ConflictRuleReq req, Long operatorId);

    ConflictRuleResp getConflictRule(Long tenantId, Long ruleId);

    List<ConflictRuleResp> listConflictRules(Long tenantId);

    ConflictRuleResp updateConflictRule(Long tenantId, ConflictRuleUpdateReq req, Long operatorId);

    ConflictDetectResp detectConflictRule(Long tenantId, ConflictRuleDetectReq req);

    void deleteConflictRule(Long tenantId, Long ruleId, Long operatorId);

    void deleteConflictRulesByIds(Long tenantId, List<Long> ids, Long operatorId);
}