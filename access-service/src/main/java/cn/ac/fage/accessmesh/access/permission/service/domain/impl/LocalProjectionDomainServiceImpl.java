package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地权限投影写入。不写 sync_metadata，owner 固定 access-service。
 * <p>
 * 单条投影（用户/组织/菜单）与依赖解析在本类；user_role 绑定/迁移委托
 * {@link UserRoleProjectionWriter}，批量用户投影委托 {@link BatchAdminUserProjectionWriter}。
 * </p>
 */
@Service
public class LocalProjectionDomainServiceImpl implements LocalProjectionDomainService {

    private static final String CODE_TYPE_DEFAULT = "default";
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final TypeResolutionService typeResolutionService;
    private final AbstractUserMapper abstractUserMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final UserRoleProjectionWriter userRoleProjectionWriter;
    private final BatchAdminUserProjectionWriter batchAdminUserProjectionWriter;

    public LocalProjectionDomainServiceImpl(TypeResolutionService typeResolutionService,
                                            AbstractUserMapper abstractUserMapper,
                                            AbstractRoleMapper abstractRoleMapper,
                                            ResourceEntityMapper resourceEntityMapper,
                                            UserRoleMapper userRoleMapper) {
        this.typeResolutionService = typeResolutionService;
        this.abstractUserMapper = abstractUserMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.userRoleProjectionWriter = new UserRoleProjectionWriter(
            typeResolutionService, abstractUserMapper, abstractRoleMapper, userRoleMapper);
        this.batchAdminUserProjectionWriter = new BatchAdminUserProjectionWriter(
            typeResolutionService, abstractUserMapper, resourceEntityMapper, userRoleMapper);
    }

    @Override
    public Long createLocalUserSubject(Long tenantId, String name, boolean enabled, String extraJson) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_USER);
        // T-ORG-001（§12.2）：abstract_user.id 序列预取主体 ID，external_id 终态 = 主体 ID 字符串化，
        // 不能先插行再回填——本地用户由调用方以同一 N 显式插 sys_user(id=N)
        Long subjectId = abstractUserMapper.nextSubjectId();
        String externalId = String.valueOf(subjectId);
        LocalDateTime now = LocalDateTime.now();

        AbstractUser user = new AbstractUser();
        user.setId(subjectId);
        user.setTenantId(tenantId);
        user.setUserType(userType);
        user.setExternalId(externalId);
        user.setName(name);
        user.setEnabled(enabled);
        user.setExtra(extraJson);
        user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleteFlag(0L);
        abstractUserMapper.insertWithExplicitId(user);

        upsertResource(tenantId, resourceType, externalId, name, null,
            enabled ? STATUS_ENABLED : STATUS_DISABLED, now);
        return subjectId;
    }

    @Override
    public Long upsertAdminUser(Long tenantId, Long sysUserId, String name, boolean enabled, String extraJson) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_USER);
        String externalId = String.valueOf(sysUserId);
        LocalDateTime now = LocalDateTime.now();

        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, externalId);
        if (user == null) {
            user = new AbstractUser();
            user.setTenantId(tenantId);
            user.setUserType(userType);
            user.setExternalId(externalId);
            user.setName(name);
            user.setEnabled(enabled);
            user.setExtra(extraJson);
            user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            user.setDeleteFlag(0L);
            abstractUserMapper.insert(user);
        } else {
            user.setName(name);
            user.setEnabled(enabled);
            if (extraJson != null) {
                user.setExtra(extraJson);
            }
            user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            user.setUpdatedAt(now);
            abstractUserMapper.update(user);
        }

        upsertResource(tenantId, resourceType, externalId, name, null, enabled ? STATUS_ENABLED : STATUS_DISABLED, now);
        return user.getId();
    }

    @Override
    public void disableAdminUser(Long tenantId, Long sysUserId) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_USER);
        String externalId = String.valueOf(sysUserId);
        LocalDateTime now = LocalDateTime.now();

        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, externalId);
        if (user != null) {
            user.setEnabled(Boolean.FALSE);
            user.setUpdatedAt(now);
            abstractUserMapper.update(user);
        }
        ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, externalId, CODE_TYPE_DEFAULT);
        if (resource != null) {
            resource.setStatus(STATUS_DISABLED);
            resource.setUpdatedAt(now);
            resourceEntityMapper.update(resource);
        }
    }

    @Override
    public void deleteAdminUser(Long tenantId, Long sysUserId) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_USER);
        String externalId = String.valueOf(sysUserId);
        LocalDateTime now = LocalDateTime.now();

        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, externalId);
        if (user != null) {
            abstractUserMapper.softDeleteBatch(tenantId, List.of(user.getId()), now);
        }
        ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, externalId, CODE_TYPE_DEFAULT);
        if (resource != null) {
            resourceEntityMapper.softDeleteBatch(tenantId, List.of(resource.getId()), now);
        }
    }

    @Override
    public Long upsertAdminOrg(Long tenantId, Long sysOrgId, String orgType, String name,
                               Long parentOrgId, String parentOrgType, Integer status, Integer sortOrder,
                               String extraJson) {
        String roleTypeCode = resolveOrgRoleType(orgType);
        Integer roleType = requireType(tenantId, "role_type", roleTypeCode);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_ORG);
        String externalId = String.valueOf(sysOrgId);
        LocalDateTime now = LocalDateTime.now();
        int statusVal = status != null ? status : STATUS_ENABLED;

        // 父角色类型按父节点实际 orgType 解析（ORG 父 + POSITION 子不再查不到）
        Long parentRoleId = resolveParentRoleId(tenantId, roleType, parentOrgId, parentOrgType);

        AbstractRole role = abstractRoleMapper.selectByTypeAndExternalId(tenantId, roleType, externalId);
        if (role == null) {
            role = new AbstractRole();
            role.setTenantId(tenantId);
            role.setRoleType(roleType);
            role.setExternalId(externalId);
            role.setName(name);
            role.setParentId(parentRoleId);
            role.setStatus(statusVal);
            role.setSortOrder(sortOrder);
            role.setExtra(extraJson);
            role.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            role.setCreatedAt(now);
            role.setUpdatedAt(now);
            role.setDeleteFlag(0L);
            abstractRoleMapper.insert(role);
        } else {
            role.setName(name);
            role.setParentId(parentRoleId);
            role.setStatus(statusVal);
            if (sortOrder != null) {
                role.setSortOrder(sortOrder);
            }
            if (extraJson != null) {
                role.setExtra(extraJson);
            }
            role.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            role.setUpdatedAt(now);
            abstractRoleMapper.update(role);
        }

        Long parentResourceId = resolveParentResourceId(tenantId, resourceType, parentOrgId);
        upsertResource(tenantId, resourceType, externalId, name, parentResourceId, statusVal, now);
        return role.getId();
    }

    @Override
    public void deleteAdminOrg(Long tenantId, Long sysOrgId, String orgType) {
        String roleTypeCode = resolveOrgRoleType(orgType);
        Integer roleType = requireType(tenantId, "role_type", roleTypeCode);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_ORG);
        String externalId = String.valueOf(sysOrgId);
        LocalDateTime now = LocalDateTime.now();

        AbstractRole role = abstractRoleMapper.selectByTypeAndExternalId(tenantId, roleType, externalId);
        if (role != null) {
            abstractRoleMapper.softDeleteBatch(tenantId, List.of(role.getId()), now);
        }
        ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, externalId, CODE_TYPE_DEFAULT);
        if (resource != null) {
            resourceEntityMapper.softDeleteBatch(tenantId, List.of(resource.getId()), now);
        }
    }

    @Override
    public Long upsertAdminMenu(Long tenantId, Long sysMenuId, String name, Long parentMenuId,
                                Integer status, Integer sortOrder) {
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_MENU);
        String externalId = String.valueOf(sysMenuId);
        LocalDateTime now = LocalDateTime.now();
        int statusVal = status != null ? status : STATUS_ENABLED;
        Long parentResourceId = resolveParentResourceId(tenantId, resourceType, parentMenuId);
        return upsertResource(tenantId, resourceType, externalId, name, parentResourceId, statusVal, now);
    }

    @Override
    public void deleteAdminMenu(Long tenantId, Long sysMenuId) {
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_MENU);
        String externalId = String.valueOf(sysMenuId);
        ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, externalId, CODE_TYPE_DEFAULT);
        if (resource != null) {
            resourceEntityMapper.softDeleteBatch(tenantId, List.of(resource.getId()), LocalDateTime.now());
        }
    }

    @Override
    public Long bindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode,
                            Long relationSysOrgId) {
        return userRoleProjectionWriter.bindUserOrg(tenantId, sysUserId, sysOrgId, roleTypeCode, relationSysOrgId);
    }

    @Override
    public Long unbindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode,
                              Long relationSysOrgId) {
        return userRoleProjectionWriter.unbindUserOrg(tenantId, sysUserId, sysOrgId, roleTypeCode, relationSysOrgId);
    }

    @Override
    public Map<UserOrgBindKey, Long> batchBindUserOrg(Long tenantId, List<UserOrgBindKey> keys) {
        return userRoleProjectionWriter.batchBindUserOrg(tenantId, keys);
    }

    @Override
    public void batchUnbindUserOrg(Long tenantId, List<UserOrgBindKey> keys) {
        userRoleProjectionWriter.batchUnbindUserOrg(tenantId, keys);
    }

    @Override
    public Set<Long> migratePositionRelation(Long tenantId, Long sysPositionId,
                                             Long oldRelationOrgId, Long newRelationOrgId) {
        return userRoleProjectionWriter.migratePositionRelation(tenantId, sysPositionId, oldRelationOrgId, newRelationOrgId);
    }

    @Override
    public void batchDeleteAdminUsers(Long tenantId, Set<Long> sysUserIds) {
        batchAdminUserProjectionWriter.batchDeleteAdminUsers(tenantId, sysUserIds);
    }

    @Override
    public void batchDisableAdminUsers(Long tenantId, Set<Long> sysUserIds) {
        batchAdminUserProjectionWriter.batchDisableAdminUsers(tenantId, sysUserIds);
    }

    @Override
    public Map<Long, Long> batchUpsertAdminUsers(Long tenantId, List<UpsertUserKey> keys) {
        return batchAdminUserProjectionWriter.batchUpsertAdminUsers(tenantId, keys);
    }

    @Override
    public Map<Long, Long> batchFindAdminUserIds(Long tenantId, Set<Long> sysUserIds) {
        return batchAdminUserProjectionWriter.batchFindAdminUserIds(tenantId, sysUserIds);
    }

    @Override
    public Long findAdminUserId(Long tenantId, Long sysUserId) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(
            tenantId, userType, String.valueOf(sysUserId));
        return user == null ? null : user.getId();
    }

    @Override
    public Long findAdminOrgRoleId(Long tenantId, Long sysOrgId, String orgType) {
        Integer roleType = requireType(tenantId, "role_type", resolveOrgRoleType(orgType));
        AbstractRole role = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, roleType, String.valueOf(sysOrgId));
        return role == null ? null : role.getId();
    }

    @Override
    public Long findAdminMenuResourceId(Long tenantId, Long sysMenuId) {
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_MENU);
        ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, String.valueOf(sysMenuId), CODE_TYPE_DEFAULT);
        return resource == null ? null : resource.getId();
    }

    private Long upsertResource(Long tenantId, Integer resourceType, String code, String name,
                                Long parentId, int status, LocalDateTime now) {
        ResourceEntity existing = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, code, CODE_TYPE_DEFAULT);
        if (existing == null) {
            ResourceEntity resource = new ResourceEntity();
            resource.setTenantId(tenantId);
            resource.setResourceType(resourceType);
            resource.setCode(code);
            resource.setCodeType(CODE_TYPE_DEFAULT);
            resource.setName(name);
            resource.setParentId(parentId);
            resource.setStatus(status);
            resource.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            // DDL maintain_source NOT NULL：显式 NULL 会绕过列默认值触发约束
            resource.setMaintainSource(PermConstants.MaintainSource.MANUAL);
            resource.setCreatedAt(now);
            resource.setUpdatedAt(now);
            resource.setDeleteFlag(0L);
            resourceEntityMapper.insert(resource);
            return resource.getId();
        }
        existing.setName(name);
        existing.setParentId(parentId);
        existing.setStatus(status);
        existing.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        existing.setUpdatedAt(now);
        resourceEntityMapper.update(existing);
        return existing.getId();
    }

    private Long resolveParentRoleId(Long tenantId, Integer childRoleType, Long parentOrgId,
                                     String parentOrgType) {
        if (parentOrgId == null || parentOrgId == 0L) {
            return null;
        }
        // 父角色类型按父节点实际 orgType 解析（ORG 父 + POSITION 子不再查不到）；
        // parentOrgType 缺失时回退用子节点 roleType，兼容未提供父类型的调用
        Integer parentRoleType = childRoleType;
        if (parentOrgType != null && !parentOrgType.isBlank()) {
            parentRoleType = requireType(tenantId, "role_type", resolveOrgRoleType(parentOrgType));
        }
        AbstractRole parent = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, parentRoleType, String.valueOf(parentOrgId));
        if (parent == null) {
            // 父角色投影缺失 = 依赖缺失，抛错回滚（不再静默写 parentId=null 脱离父树）
            throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
                "父组织角色投影缺失: parentOrgId=" + parentOrgId);
        }
        return parent.getId();
    }

    private Long resolveParentResourceId(Long tenantId, Integer resourceType, Long parentExternalId) {
        if (parentExternalId == null || parentExternalId == 0L) {
            return null;
        }
        ResourceEntity parent = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, String.valueOf(parentExternalId), CODE_TYPE_DEFAULT);
        if (parent == null) {
            // 父资源投影缺失 = 依赖缺失，抛错回滚（与父角色同语义，不再静默写 parentId=null 脱离父树）
            throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
                "父资源投影缺失: parentId=" + parentExternalId);
        }
        return parent.getId();
    }

    private Integer requireType(Long tenantId, String typeKey, String typeCode) {
        Integer value = typeResolutionService.resolveTypeValue(tenantId, typeKey, typeCode);
        if (value == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(),
                "Unknown " + typeKey + ": " + typeCode);
        }
        return value;
    }

    private static String resolveOrgRoleType(String orgType) {
        if ("2".equals(orgType) || LocalProjectionOwner.ROLE_POSITION.equalsIgnoreCase(orgType)) {
            return LocalProjectionOwner.ROLE_POSITION;
        }
        return LocalProjectionOwner.ROLE_ORG;
    }
}
