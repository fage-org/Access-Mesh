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

    /**
     * 本地投影写路径的反向所有权防线（T-ACCESS-018 评审 P1）。
     * <p>
     * USER/ORG/MENU 合并为公共基础类型后，本地投影按 (type, code, codeType=default)
     * 命中的既有行可能属于外部同步（owner 非 access-service）。本地投影只维护自己的行：
     * upsert 命中外部行时不得接管改写 owner（fail-closed 拒绝，防止外部资源被本地生命周期
     * 覆盖/删除、其 sync_metadata.target_id 悬挂指向本地行）；禁用/删除路径由调用方按
     * {@code isLocalOwner} 过滤跳过外部行（本地生命周期不因外部行阻断，也不触碰外部行）。
     * </p>
     */
    public void rejectIfForeignResource(ResourceEntity resource) {
        if (resource != null && !LocalProjectionOwner.isLocalOwner(resource.getOwnerServiceCode())) {
            throw new BizException(
                PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode(),
                "本地投影资源编码冲突：已被外部同步占用，不得接管改写 owner="
                    + resource.getOwnerServiceCode()
                    + "（type=" + resource.getResourceType() + ", code=" + resource.getCode() + "）");
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

    /**
     * 管理入口（resource-entity create/update 等人工建资源）的类型保留清单 {USER, ORG, MENU, ROLE}：
     * 人工不得绕过管理事实链路（用户/组织/菜单/角色管理）直接建本地业务资源投影
     * （ROLE 随 T-ACCESS-019 加入：ROLE 资源由角色管理写路径产出，code=roleId）。
     * <p>
     * 外部 sync 入口不调用本方法（T-ACCESS-018：resource 侧取消类型级保留，
     * USER/MENU 为公共基础类型；本地投影行改按 {@link #rejectIfLocalResource} 所有权保护）。
     * </p>
     */
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
