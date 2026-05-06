package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Unified mapper: RoleResourcePermission entity → RolePermEntry record.
 * Eliminates duplicated 13-parameter constructor calls across PermissionServiceImpl.
 */
@Component
public class RolePermEntryMapper {

    public RolePermEntry toEntry(RoleResourcePermission p) {
        return new RolePermEntry(
            p.getId(),
            p.getAbstractRoleId(),
            p.getResourceEntityId(),
            null,
            p.getResourceType(),
            p.getOperationPermissionId(),
            null,
            null,
            p.getGrantSource(),
            p.getCanGrant(),
            p.getConditionId(),
            p.getConditionId() != null,
            p.getDependOn()
        );
    }

    public RolePermEntry toEntryWithOpCode(RoleResourcePermission p, String operationCode) {
        return new RolePermEntry(
            p.getId(),
            p.getAbstractRoleId(),
            p.getResourceEntityId(),
            null,
            p.getResourceType(),
            p.getOperationPermissionId(),
            operationCode,
            null,
            p.getGrantSource(),
            p.getCanGrant(),
            p.getConditionId(),
            p.getConditionId() != null,
            p.getDependOn()
        );
    }

    public List<RolePermEntry> toEntryList(List<RoleResourcePermission> perms) {
        return perms.stream().map(this::toEntry).toList();
    }
}
