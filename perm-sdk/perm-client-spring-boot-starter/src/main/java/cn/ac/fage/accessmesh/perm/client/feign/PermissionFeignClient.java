package cn.ac.fage.accessmesh.perm.client.feign;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.req.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.*;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckReq;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Feign client for permission-center.
 * All endpoints are POST + JSON Body, matching the unified API convention.
 */
@FeignClient(name = "permission-center")
public interface PermissionFeignClient {

    // === Internal / SDK (permission check) ===

    @PostMapping("/internal/perm/check")
    PermResult<PermCheckResp> checkPermission(@RequestBody PermCheckReq req);

    // === Role Management ===

    @PostMapping("/api/role/create")
    PermResult<Long> createRole(@RequestBody RoleCreateReq req);

    @PostMapping("/api/user/roles")
    PermResult<UserRolesResp> getUserRoles(@RequestBody IdWithTenantReq req);

    // === Resource Management ===

    @PostMapping("/api/resource/create")
    PermResult<Long> createResource(@RequestBody ResourceCreateReq req);

    @PostMapping("/api/resource/update")
    PermResult<Long> updateResource(@RequestBody ResourceUpdateReq req);

    @PostMapping("/api/resource/delete")
    PermResult<Void> deleteResource(@RequestBody IdWithTenantReq req);

    // === Operation Management ===

    @PostMapping("/api/operation/list")
    PermResult<List<OperationPermissionResp>> listOperations(@RequestBody OperationListReq req);

    // === Permission Grant / Revoke ===

    @PostMapping("/api/role-permission/batch-grant")
    PermResult<Void> batchGrant(@RequestBody RoleGrantReq req);

    @PostMapping("/api/role-permission/batch-revoke")
    PermResult<Void> batchRevoke(@RequestBody BatchRevokeReq req);

    // === Permission View ===

    @PostMapping("/api/permission-view/user")
    PermResult<UserPermissionViewResp> getUserPermissions(@RequestBody UserPermissionViewReq req);
}
