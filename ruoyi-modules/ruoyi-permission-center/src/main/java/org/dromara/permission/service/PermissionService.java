package org.dromara.permission.service;

import org.dromara.permission.model.permission.GrantPermissionRequest;
import org.dromara.permission.model.permission.GrantResult;
import org.dromara.permission.model.permission.PermissionCheckRequest;
import org.dromara.permission.model.permission.PermissionCheckResult;
import org.dromara.permission.model.permission.PermissionSnapshot;
import org.dromara.permission.model.permission.PermissionVersionQueryRequest;
import org.dromara.permission.model.permission.PermissionVersionResult;
import org.dromara.permission.model.permission.RolePermissionBatchGrantRequest;
import org.dromara.permission.model.permission.RolePermissionBatchRevokeRequest;
import org.dromara.permission.model.permission.RevokePermissionRequest;
import org.dromara.permission.model.permission.RevokeResult;
import org.dromara.permission.model.permission.SnapshotRequest;
import org.dromara.permission.model.permission.UserRoleBatchAssignRequest;
import org.dromara.permission.model.permission.UserRoleBatchRevokeRequest;
public interface PermissionService {

    PermissionCheckResult check(PermissionCheckRequest request);

    GrantResult grant(GrantPermissionRequest request);

    RevokeResult revoke(RevokePermissionRequest request);

    void grantRolePermissions(RolePermissionBatchGrantRequest request);

    void revokeRolePermissions(RolePermissionBatchRevokeRequest request);

    void assignUserRoles(UserRoleBatchAssignRequest request);

    void revokeUserRoles(UserRoleBatchRevokeRequest request);

    PermissionSnapshot buildSnapshot(SnapshotRequest request);

    PermissionVersionResult queryVersion(PermissionVersionQueryRequest request);
}
