package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 本地投影保护：权限管理写入口与外部 sync/full-sync 不得改写 access-service 投影。
 */
@Component
public class LocalProjectionGuard {

    public void rejectIfLocalOwner(String ownerServiceCode) {
        if (LocalProjectionOwner.isLocalOwner(ownerServiceCode)) {
            throw immutable("owner_service_code=access-service");
        }
    }

    public void rejectIfLocalUser(AbstractUser user) {
        if (user != null) {
            rejectIfLocalOwner(user.getOwnerServiceCode());
        }
    }

    public void rejectIfLocalRole(AbstractRole role) {
        if (role != null) {
            rejectIfLocalOwner(role.getOwnerServiceCode());
        }
    }

    public void rejectIfLocalResource(ResourceEntity resource) {
        if (resource != null) {
            rejectIfLocalOwner(resource.getOwnerServiceCode());
        }
    }

    public void rejectIfLocalUserRole(UserRole userRole) {
        if (userRole != null) {
            rejectIfLocalOwner(userRole.getOwnerServiceCode());
        }
    }

    public void rejectReservedSubjectType(String subjectTypeCode) {
        if (LocalProjectionOwner.isReservedSubjectType(subjectTypeCode)) {
            throw immutable("subjectTypeCode=" + subjectTypeCode);
        }
    }

    public void rejectReservedRoleType(String roleTypeCode) {
        if (LocalProjectionOwner.isReservedRoleType(roleTypeCode)) {
            throw immutable("roleTypeCode=" + roleTypeCode);
        }
    }

    public void rejectReservedResourceType(String resourceTypeCode) {
        if (LocalProjectionOwner.isReservedResourceType(resourceTypeCode)) {
            throw immutable("resourceTypeCode=" + resourceTypeCode);
        }
    }

    public void rejectReservedUserRoleSource(String sourceType) {
        if (LocalProjectionOwner.isReservedUserRoleSource(sourceType)) {
            throw immutable("sourceType=" + sourceType);
        }
    }

    public void rejectInternalSourceService(String sourceService) {
        if (LocalProjectionOwner.isInternalSourceService(sourceService)) {
            throw immutable("sourceService=" + sourceService);
        }
    }

    private static BizException immutable(String detail) {
        return new BizException(
            PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode(),
            PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getMessage() + ": " + detail);
    }
}
