package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;

import java.util.List;
import java.util.Set;

public interface PermissionConflictDomainService {

    Set<Long> filterRoleMutex(Long tenantId, Set<Long> effectiveRoleIds);

    List<RolePermSnapshot.RolePermEntry> filterPermMutex(Long tenantId, List<RolePermSnapshot.RolePermEntry> passedEntries);
}
