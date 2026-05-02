package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;

import java.time.LocalDateTime;
import java.util.List;

public interface RolePermissionDomainService {

    RolePermSnapshot getRolePermissions(Long tenantId, Long roleId);

    void grantPermissions(Long tenantId, Long roleId, List<RolePermSnapshot.RolePermEntry> entries, String changeSource);

    void revokePermissions(Long tenantId, Long roleId, List<Long> permissionIds);

    /**
     * Soft delete a single permission with cascade delete of sub-permissions (depend_on this permission).
     * Does NOT increment version - caller should handle version increment.
     */
    void revokePermissionWithCascade(Long tenantId, Long roleId, Long permissionId, LocalDateTime deletedAt);

    /**
     * Select a valid role resource permission by ID with tenant, roleId and delete flag conditions.
     * Returns null if permission not found, deleted, or doesn't belong to the tenant/role.
     */
    RoleResourcePermission selectValidById(Long tenantId, Long roleId, Long permissionId);
}
