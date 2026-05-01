package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.PermissionExplainReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionRecentChangesReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserEffectiveRolesReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserResourceTreeReq;
import cn.ac.fage.accessmesh.permission.dto.resp.EffectiveRoleResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionExplainResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionRecentChangesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserPermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PaginatedResp;

/**
 * Permission View — read-only visibility APIs for user/resource/role permissions.
 */
public interface PermissionViewService {

    /**
     * View a user's effective permissions (grouped by resource), with pagination.
     */
    UserPermissionViewResp getUserPermissions(Long tenantId, Long userId);

    /**
     * View a user's effective permissions with filters and pagination (unified item type per §6.8).
     */
    PermissionEffectivePermissionsResp getEffectivePermissions(Long tenantId, UserPermissionViewReq req);

    /**
     * View which roles have permissions on a resource.
     */
    ResourcePermissionViewResp getResourcePermissions(Long tenantId, String domainCode, String resourceTypeCode, String resourceCode, String codeType);

    /**
     * View a role's permissions (flat list, optionally expanded to show sub-permissions).
     */
    RolePermissionViewResp getRolePermissions(Long tenantId, String domainCode, String roleTypeCode, String roleExternalId, boolean expandSub);

    PaginatedResp<RolePermissionViewResp.PermissionItem> getRolePermissionItemsPaged(
        Long tenantId, String domainCode, String roleTypeCode, String roleExternalId, int pageNum, int pageSize);

    PermissionExplainResp explain(Long tenantId, PermissionExplainReq req);

    PermissionRecentChangesResp recentChanges(Long tenantId, PermissionRecentChangesReq req);

    ItemsResp<EffectiveRoleResp> listEffectiveRoles(Long tenantId, UserEffectiveRolesReq req);

    java.util.List<ResourcePermissionTreeResp> getUserResourceTree(Long tenantId, Long userId, UserResourceTreeReq req);
}
