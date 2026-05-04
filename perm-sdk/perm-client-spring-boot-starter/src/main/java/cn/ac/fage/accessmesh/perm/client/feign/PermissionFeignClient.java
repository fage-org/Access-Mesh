package cn.ac.fage.accessmesh.perm.client.feign;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.req.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckReq;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign client for permission-center.
 * Paths and bodies follow {@code /api/perm/*} contracts (POST + JSON).
 */
@FeignClient(name = "permission-center")
public interface PermissionFeignClient {

    // ========== User Sync ==========

    @PostMapping("/api/perm/abstract-user/sync")
    PermResult<Map<String, Object>> syncUser(@RequestBody UserSyncReq req);

    @PostMapping("/api/perm/abstract-user/remove")
    PermResult<Void> deleteUsers(@RequestBody IdsReq req);

    // ========== Auth Check (New API with stable business keys) ==========

    /**
     * Single permission check using stable business keys.
     * @param req request with subjectTypeCode, subjectExternalId, resourceTypeCode, resourceCode, operationCode
     * @return permission check result with allowed flag and reason
     */
    @PostMapping("/api/perm/auth/check")
    PermResult<AuthCheckResp> checkAuth(@RequestBody AuthCheckReq req);

    /**
     * Batch permission check using stable business keys.
     * @param req request with multiple items to check
     * @return batch result with each item's permission status
     */
    @PostMapping("/api/perm/auth/batch-check")
    PermResult<BatchAuthCheckResp> batchCheckAuth(@RequestBody BatchAuthCheckReq req);

    // ========== Role ==========

    @PostMapping("/api/perm/auth/check")
    PermResult<PermCheckResp> checkPermission(@RequestBody PermCheckReq req);

    @PostMapping("/api/perm/abstract-role/create")
    PermResult<Map<String, Object>> createRole(@RequestBody RoleCreateReq req);

    @PostMapping("/api/perm/user-role/list")
    PermResult<UserRolesResp> getUserRoles(@RequestBody UserRoleListReq req);

    // ========== Resource Sync ==========

    @PostMapping("/api/perm/resource-entity/create")
    PermResult<Map<String, Object>> createResource(@RequestBody ResourceCreateReq req);

    @PostMapping("/api/perm/resource-entity/batch-create")
    PermResult<Map<String, Object>> batchCreateResources(@RequestBody ResourceBatchCreateReq req);

    @PostMapping("/api/perm/resource-entity/update")
    PermResult<Map<String, Object>> updateResource(@RequestBody ResourceUpdateReq req);

    @PostMapping("/api/perm/resource-entity/remove")
    PermResult<Void> deleteResources(@RequestBody IdsReq req);

    // ========== Operation Permission ==========

    @PostMapping("/api/perm/operation-permission/list")
    PermResult<Map<String, Object>> listOperations(@RequestBody OperationListReq req);

    // ========== Grant/Revoke ==========

    @PostMapping("/api/perm/role-resource-permission/save")
    PermResult<Map<String, Object>> batchGrant(@RequestBody RoleGrantReq req);

    @PostMapping("/api/perm/role-resource-permission/revoke")
    PermResult<Void> batchRevoke(@RequestBody BatchRevokeReq req);

    @PostMapping("/api/perm/permission-view/effective-permissions")
    PermResult<PermissionEffectivePermissionsResp<Map<String, Object>>> getEffectivePermissions(
        @RequestBody UserPermissionViewReq req);
}