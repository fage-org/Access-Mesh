package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserUpdateReq;
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

    UserResp createUser(Long tenantId, UserCreateReq req);

    UserResp updateUser(Long tenantId, UserUpdateReq req);

    /**
     * Get user by ID.
     */
    UserResp getUser(Long tenantId, Long userId);

    /**
     * Delete (soft) a user.
     */
    void deleteUser(Long tenantId, Long userId);

    void deleteUsers(Long tenantId, List<Long> userIds);

    /**
     * Assign a role to a user.
     */
    void assignRole(Long tenantId, UserAssignRoleReq req);

    void assignRolesBatch(Long tenantId, UserRoleBatchAssignReq req);

    /**
     * Batch revoke user-role relations (business keys per item).
     */
    void revokeRolesBatch(Long tenantId, UserRoleBatchRevokeReq req);

    /**
     * Get user's assigned roles by subject business keys.
     */
    UserRolesResp getUserRoles(Long tenantId, UserRoleListReq req);

    /**
     * List users by tenant (simple pagination).
     */
    List<UserResp> listUsers(Long tenantId, String subjectTypeCode, String domainCode, String keyword, int offset, int limit);

    long countUsers(Long tenantId, String subjectTypeCode, String domainCode, String keyword);
}
