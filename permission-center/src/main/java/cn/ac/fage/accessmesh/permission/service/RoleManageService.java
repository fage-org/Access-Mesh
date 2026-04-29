package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleTreeResp;

import java.util.List;

/**
 * Role management service — CRUD, tree structure, enable/disable.
 */
public interface RoleManageService {

    /**
     * Create a role.
     */
    RoleResp createRole(Long tenantId, RoleCreateReq req, Long operatorId);

    /**
     * Get role by ID.
     */
    RoleResp getRole(Long tenantId, Long roleId);

    /**
     * Update a role's basic info.
     */
    RoleResp updateRole(Long tenantId, Long roleId, String name, Integer sortOrder, String extra, Long operatorId);

    /**
     * Delete (soft) a role and cascade-delete children.
     */
    void deleteRole(Long tenantId, Long roleId, Long operatorId);

    /**
     * Enable/disable a role.
     */
    void setRoleStatus(Long tenantId, Long roleId, int status, Long operatorId);

    /**
     * Get role tree for a tenant (optionally scoped to bizDomainId).
     */
    List<RoleTreeResp> getRoleTree(Long tenantId, Long bizDomainId);

    /**
     * List roles by tenant (flat list).
     */
    List<RoleResp> listRoles(Long tenantId, int offset, int limit);
}
