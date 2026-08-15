package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.entity.table.UserRoleTableDef;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本地权限投影写入。不写 sync_metadata，owner 固定 access-service。
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
    private final UserRoleMapper userRoleMapper;

    public LocalProjectionDomainServiceImpl(TypeResolutionService typeResolutionService,
                                            AbstractUserMapper abstractUserMapper,
                                            AbstractRoleMapper abstractRoleMapper,
                                            ResourceEntityMapper resourceEntityMapper,
                                            UserRoleMapper userRoleMapper) {
        this.typeResolutionService = typeResolutionService;
        this.abstractUserMapper = abstractUserMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.userRoleMapper = userRoleMapper;
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
                               Long parentOrgId, Integer status, Integer sortOrder, String extraJson) {
        String roleTypeCode = resolveOrgRoleType(orgType);
        Integer roleType = requireType(tenantId, "role_type", roleTypeCode);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_ORG);
        String externalId = String.valueOf(sysOrgId);
        LocalDateTime now = LocalDateTime.now();
        int statusVal = status != null ? status : STATUS_ENABLED;

        Long parentRoleId = resolveParentRoleId(tenantId, roleType, parentOrgId);

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
    public Long bindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer roleType = requireType(tenantId, "role_type", roleTypeCode);
        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(
            tenantId, userType, String.valueOf(sysUserId));
        AbstractRole role = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, roleType, String.valueOf(sysOrgId));
        if (user == null || role == null) {
            throw new BizException(PermissionErrorCode.USER_ROLE_RELATION_NOT_FOUND.getCode(),
                "local projection missing for user-org bind: userId=" + sysUserId + ", orgId=" + sysOrgId);
        }
        Integer orgRoleType = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_ORG);
        AbstractRole relationRole = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, orgRoleType, String.valueOf(sysOrgId));
        Long relationId = relationRole != null ? relationRole.getId() : role.getId();

        LocalDateTime now = LocalDateTime.now();
        UserRole existing = findUserRole(tenantId, user.getId(), role.getId(), relationId);
        if (existing == null) {
            UserRole ur = new UserRole();
            ur.setTenantId(tenantId);
            ur.setAbstractUserId(user.getId());
            ur.setTargetType(ResourceTypeCode.ROLE);
            ur.setTargetId(role.getId());
            ur.setRelationId(relationId);
            ur.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
            ur.setCreatedAt(now);
            ur.setUpdatedAt(now);
            ur.setDeleteFlag(0L);
            userRoleMapper.insert(ur);
            return ur.getId();
        }
        existing.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        existing.setUpdatedAt(now);
        userRoleMapper.update(existing);
        return existing.getId();
    }

    @Override
    public Long unbindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer roleType = requireType(tenantId, "role_type", roleTypeCode);
        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(
            tenantId, userType, String.valueOf(sysUserId));
        AbstractRole role = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, roleType, String.valueOf(sysOrgId));
        if (user == null || role == null) {
            return null;
        }
        Integer orgRoleType = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_ORG);
        AbstractRole relationRole = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, orgRoleType, String.valueOf(sysOrgId));
        Long relationId = relationRole != null ? relationRole.getId() : role.getId();
        UserRole existing = findUserRole(tenantId, user.getId(), role.getId(), relationId);
        if (existing != null) {
            userRoleMapper.softDeleteBatch(tenantId, List.of(existing.getId()), LocalDateTime.now());
            return existing.getId();
        }
        return null;
    }

    @Override
    public void batchUnbindUserOrg(Long tenantId, List<UserOrgBindKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer orgRoleType = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_ORG);
        Integer roleTypeOrg = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_ORG);
        Integer roleTypePosition = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_POSITION);

        Set<String> userExtIds = keys.stream()
            .map(k -> String.valueOf(k.sysUserId())).collect(Collectors.toSet());
        Set<String> orgExtIds = keys.stream()
            .map(k -> String.valueOf(k.sysOrgId())).collect(Collectors.toSet());

        // 1. 批量加载 abstract_user / abstract_role（ORG+POSITION）/ relationRole（ORG）
        Map<String, AbstractUser> usersByExt = abstractUserMapper
            .selectByTypeAndExternalIds(tenantId, userType, userExtIds)
            .stream().collect(Collectors.toMap(AbstractUser::getExternalId, u -> u, (a, b) -> a));
        Map<String, AbstractRole> rolesByExt = new HashMap<>();
        if (!orgExtIds.isEmpty()) {
            abstractRoleMapper.selectByTypeAndExternalIds(tenantId, roleTypeOrg, orgExtIds)
                .forEach(r -> rolesByExt.put(r.getExternalId(), r));
            abstractRoleMapper.selectByTypeAndExternalIds(tenantId, roleTypePosition, orgExtIds)
                .forEach(r -> rolesByExt.putIfAbsent(r.getExternalId(), r));
        }
        Map<String, AbstractRole> relationsByExt = abstractRoleMapper
            .selectByTypeAndExternalIds(tenantId, orgRoleType, orgExtIds)
            .stream().collect(Collectors.toMap(AbstractRole::getExternalId, r -> r, (a, b) -> a));

        // 2. 批量加载候选 user_role（三元组收窄一次查询）
        Set<Long> userIds = usersByExt.values().stream().map(AbstractUser::getId).collect(Collectors.toSet());
        Set<Long> roleIds = rolesByExt.values().stream().map(AbstractRole::getId).collect(Collectors.toSet());
        Set<Long> relationIds = relationsByExt.values().stream().map(AbstractRole::getId).collect(Collectors.toSet());
        relationIds.addAll(roleIds);
        List<UserRole> candidates = userIds.isEmpty() || roleIds.isEmpty()
            ? List.of()
            : userRoleMapper.selectValidByUserTargetRelation(
                tenantId, userIds, roleIds, relationIds, ResourceTypeCode.ROLE);

        // 3. 构建三元组命中集（abstractUserId|targetId|relationId）
        Set<String> keysToMatch = new HashSet<>();
        for (UserOrgBindKey key : keys) {
            AbstractUser user = usersByExt.get(String.valueOf(key.sysUserId()));
            AbstractRole role = rolesByExt.get(String.valueOf(key.sysOrgId()));
            if (user == null || role == null) {
                continue;
            }
            AbstractRole relationRole = relationsByExt.get(String.valueOf(key.sysOrgId()));
            Long relationId = relationRole != null ? relationRole.getId() : role.getId();
            keysToMatch.add(user.getId() + "|" + role.getId() + "|" + relationId);
        }

        // 4. 内存匹配后批量软删
        List<Long> toDelete = candidates.stream()
            .filter(ur -> keysToMatch.contains(ur.getAbstractUserId() + "|" + ur.getTargetId() + "|" + ur.getRelationId()))
            .map(UserRole::getId)
            .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            userRoleMapper.softDeleteBatch(tenantId, toDelete, LocalDateTime.now());
        }
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

    private Long resolveParentRoleId(Long tenantId, Integer roleType, Long parentOrgId) {
        if (parentOrgId == null || parentOrgId == 0L) {
            return null;
        }
        AbstractRole parent = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, roleType, String.valueOf(parentOrgId));
        return parent == null ? null : parent.getId();
    }

    private Long resolveParentResourceId(Long tenantId, Integer resourceType, Long parentExternalId) {
        if (parentExternalId == null || parentExternalId == 0L) {
            return null;
        }
        ResourceEntity parent = resourceEntityMapper.selectByTypeCodeAndCodeType(
            tenantId, resourceType, String.valueOf(parentExternalId), CODE_TYPE_DEFAULT);
        return parent == null ? null : parent.getId();
    }

    private UserRole findUserRole(Long tenantId, Long userId, Long roleId, Long relationId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
            .and(UserRoleTableDef.USER_ROLE.ABSTRACT_USER_ID.eq(userId))
            .and(UserRoleTableDef.USER_ROLE.TARGET_TYPE.eq(ResourceTypeCode.ROLE))
            .and(UserRoleTableDef.USER_ROLE.TARGET_ID.eq(roleId))
            .and(UserRoleTableDef.USER_ROLE.RELATION_ID.eq(relationId))
            .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0L));
        return userRoleMapper.selectOneByQuery(qw);
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
