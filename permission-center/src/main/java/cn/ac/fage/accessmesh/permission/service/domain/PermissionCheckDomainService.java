package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;

import java.util.Map;

/**
 * Domain-level permission checking logic, extracted from PermissionServiceImpl
 * to avoid cross-service calls between scheduling-layer services.
 */
public interface PermissionCheckDomainService {

    AuthCheckResp check(Long tenantId, AuthCheckReq req);

    BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req);

    CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req);

    /**
     * Internal auth check used by both PermissionService and PermissionViewService.
     */
    AuthCheckResp checkInternal(Long tenantId, Long userId, Long resourceEntityId,
                                Long operationPermissionId, Long bizDomainId,
                                String inheritMode, Map<String, Object> context);
}
