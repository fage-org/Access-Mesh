package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 本地投影保护：权限管理写入口与外部 sync/full-sync 不得改写 access-service 投影
 * （subject/role/user_role 三侧防线保留；resource 侧已收编进 ResourceTypeOwnershipGuard
 * 类型级所有权门禁——事实链路类型（USER/ORG/MENU/ROLE/ADMIN_FILE）种子声明 SYNC+access-service，
 * T-PERM-052 2026-09-05、T-ADMIN-025 增 ADMIN_FILE）。
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
