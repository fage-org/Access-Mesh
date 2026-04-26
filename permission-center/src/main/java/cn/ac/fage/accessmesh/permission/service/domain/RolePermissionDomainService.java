package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;

import java.util.List;

public interface RolePermissionDomainService {

    RolePermSnapshot getRolePermissions(Long tenantId, Long roleId);

    void grantPermissions(Long tenantId, Long roleId, List<RolePermSnapshot.RolePermEntry> entries, String changeSource);

    void revokePermissions(Long tenantId, Long roleId, List<Long> permissionIds);
}
