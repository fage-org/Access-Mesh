package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.resp.OperationPermissionResp;

import java.util.List;

/**
 * Operation Permission management service.
 */
public interface OperationManageService {

    OperationPermissionResp createOperation(Long tenantId, String resourceTypeCode, String code, String name, Long binaryBit, Long inheritMask, Long operatorId);

    OperationPermissionResp getOperation(Long tenantId, Long operationId);

    List<OperationPermissionResp> listOperations(Long tenantId, String resourceTypeCode, String domainCode);

    OperationPermissionResp updateOperation(Long tenantId, Long operationId, String name, Long binaryBit, Long inheritMask, Long operatorId);

    void deleteOperation(Long tenantId, Long operationId, Long operatorId);

    void deleteOperations(Long tenantId, List<Long> operationIds, Long operatorId);
}
