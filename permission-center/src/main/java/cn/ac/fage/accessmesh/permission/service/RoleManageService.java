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
    RoleResp updateRole(Long tenantId, Long roleId, String name, Integer status, Integer sortOrder, String extra, Long operatorId);

    void moveRole(Long tenantId, Long roleId, Long parentId, Long operatorId);

    /**
     * Delete (soft) a role and cascade-delete children.
     */
    void deleteRole(Long tenantId, Long roleId, Long operatorId);

    void deleteRoles(Long tenantId, List<Long> roleIds, Long operatorId);

    /**
     * Get role tree for a tenant. {@code domainCode} null/blank: global-domain roles only;
     * otherwise roles in that domain plus global-domain roles.
     */
    List<RoleTreeResp> getRoleTree(Long tenantId, String domainCode);

    /**
     * List roles by tenant (flat list).
     */
    List<RoleResp> listRoles(Long tenantId, String domainCode, String roleTypeCode, String keyword, int offset, int limit);

    long countRoles(Long tenantId, String domainCode, String roleTypeCode, String keyword);
}
