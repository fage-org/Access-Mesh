package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConditionResp;

import java.util.List;

/**
 * Permission condition management service.
 */
public interface ConditionManageService {

    ConditionResp createCondition(Long tenantId, ConditionCreateReq req, Long operatorId);

    ConditionResp getCondition(Long tenantId, Long conditionId);

    ConditionResp updateCondition(Long tenantId, ConditionUpdateReq req, Long operatorId);

    List<ConditionResp> listConditions(Long tenantId);

    void deleteCondition(Long tenantId, Long conditionId, Long operatorId);

    void deleteConditionsByIds(Long tenantId, List<Long> ids, Long operatorId);
}