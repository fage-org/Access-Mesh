package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserSyncReq;
import cn.ac.fage.accessmesh.permission.dto.resp.UserResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserRolesResp;

import java.util.List;

/**
 * User management service — sync, CRUD, role assignment.
 */
public interface UserManageService {

    /**
     * Sync a user from external system (upsert by tenantId+userType+externalId).
     */
    UserResp syncUser(Long tenantId, UserSyncReq req);

    /**
     * Get user by ID.
     */
    UserResp getUser(Long tenantId, Long userId);

    /**
     * Delete (soft) a user.
     */
    void deleteUser(Long tenantId, Long userId);

    /**
     * Enable/disable a user.
     */
    void setUserEnabled(Long tenantId, Long userId, boolean enabled);

    /**
     * Assign a role to a user.
     */
    void assignRole(Long tenantId, UserAssignRoleReq req);

    /**
     * Revoke a role from a user.
     */
    void revokeRole(Long tenantId, Long userId, Long userRoleId);

    /**
     * Get user's assigned roles.
     */
    UserRolesResp getUserRoles(Long tenantId, Long userId);

    /**
     * List users by tenant (simple pagination).
     */
    List<UserResp> listUsers(Long tenantId, int offset, int limit);
}
