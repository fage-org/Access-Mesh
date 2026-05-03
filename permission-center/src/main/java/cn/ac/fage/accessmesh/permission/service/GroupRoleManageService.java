package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRolesListReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleSummaryResp;

import java.util.List;

/**
 * Group role extra-roles management service.
 * Handles the assignment of basic roles to group roles.
 */
public interface GroupRoleManageService {

    void addGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId);

    void removeGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId);

    List<RoleSummaryResp> listGroupRoleExtraRoles(Long tenantId, GroupRoleExtraRolesListReq req);
}