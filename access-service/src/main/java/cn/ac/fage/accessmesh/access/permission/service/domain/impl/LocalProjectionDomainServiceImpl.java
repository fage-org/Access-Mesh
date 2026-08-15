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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
                               Long parentOrgId, String parentOrgType, Integer status, Integer sortOrder,
                               String extraJson) {
        String roleTypeCode = resolveOrgRoleType(orgType);
        Integer roleType = requireType(tenantId, "role_type", roleTypeCode);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_ORG);
        String externalId = String.valueOf(sysOrgId);
        LocalDateTime now = LocalDateTime.now();
        int statusVal = status != null ? status : STATUS_ENABLED;

        // 九轮评审 P1：按父节点实际 orgType 解析父角色类型（ORG 父 + POSITION 子不再查不到）
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
        // 九轮评审 P1：POSITION 的 relation = 所属组织角色（relationSysOrgId），ORG 沿用自身语义
        Long relationId = resolveRelationRoleId(tenantId, roleTypeCode, sysOrgId, relationSysOrgId);

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
    public Long unbindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode,
                              Long relationSysOrgId) {
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer roleType = requireType(tenantId, "role_type", roleTypeCode);
        AbstractUser user = abstractUserMapper.selectByTypeAndExternalId(
            tenantId, userType, String.valueOf(sysUserId));
        AbstractRole role = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, roleType, String.valueOf(sysOrgId));
        if (user == null || role == null) {
            return null;
        }
        Long relationId = resolveRelationRoleId(tenantId, roleTypeCode, sysOrgId, relationSysOrgId);
        UserRole existing = findUserRole(tenantId, user.getId(), role.getId(), relationId);
        if (existing != null) {
            userRoleMapper.softDeleteBatch(tenantId, List.of(existing.getId()), LocalDateTime.now());
            return existing.getId();
        }
        return null;
    }

    /**
     * 解析 user_role.relation_id：POSITION 成员绑定到所属组织角色（ORG:relationSysOrgId），
     * 普通 ORG 沿用自身角色。十轮评审 P1：所属组织角色缺失 = 依赖缺失，抛 BizException
     * 整体回滚（强事务投影 fail-closed，不再回退 targetRole 制造 target_id == relation_id）。
     */
    private Long resolveRelationRoleId(Long tenantId, String roleTypeCode,
                                       Long sysOrgId, Long relationSysOrgId) {
        Integer orgRoleType = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_ORG);
        boolean position = LocalProjectionOwner.ROLE_POSITION.equals(roleTypeCode);
        if (position && relationSysOrgId == null) {
            throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
                "POSITION 成员缺少所属组织上下文: positionId=" + sysOrgId);
        }
        Long relationOrgId = position ? relationSysOrgId : sysOrgId;
        AbstractRole relationRole = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, orgRoleType, String.valueOf(relationOrgId));
        if (relationRole == null) {
            throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
                "POSITION 所属组织角色投影缺失: positionId=" + sysOrgId + ", relationOrgId=" + relationOrgId);
        }
        return relationRole.getId();
    }

    @Override
    public Map<UserOrgBindKey, Long> batchBindUserOrg(Long tenantId, List<UserOrgBindKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer orgRoleType = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_ORG);
        Integer roleTypeOrg = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_ORG);
        Integer roleTypePosition = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_POSITION);

        Set<String> userExtIds = keys.stream()
            .map(k -> String.valueOf(k.sysUserId())).collect(Collectors.toSet());
        Set<String> orgExtIds = keys.stream()
            .map(k -> String.valueOf(k.sysOrgId())).collect(Collectors.toSet());

        // 1. 批量加载 abstract_user / abstract_role（ORG+POSITION）/ relationRole（ORG，含 POSITION 所属组织）
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
        Set<String> relationOrgExtIds = keys.stream()
            .map(k -> String.valueOf(resolveRelationOrgId(k)))
            .collect(Collectors.toSet());
        Map<String, AbstractRole> relationsByExt = abstractRoleMapper
            .selectByTypeAndExternalIds(tenantId, orgRoleType, relationOrgExtIds)
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
        // 十轮评审 P1：保留三元组 → 完整实体映射（含主键），已有行更新不再重建无 id 实体
        Map<String, UserRole> candidatesByKey = candidates.stream()
            .collect(Collectors.toMap(
                ur -> ur.getAbstractUserId() + "|" + ur.getTargetId() + "|" + ur.getRelationId(),
                ur -> ur, (a, b) -> a));

        // 3. 逐 key 计算三元组，批量 insert（新）或 update（已存在，刷新 owner/updatedAt）
        LocalDateTime now = LocalDateTime.now();
        List<UserRole> toInsert = new ArrayList<>();
        List<UserRole> toUpdate = new ArrayList<>();
        Map<UserOrgBindKey, Long> result = new LinkedHashMap<>();
        // 新插入三元组 → key（insert 后按三元组批量回查 id；同批重复仅首个 key 记录）
        Map<UserOrgBindKey, String> pendingInsertKeys = new LinkedHashMap<>();
        for (UserOrgBindKey key : keys) {
            AbstractUser user = usersByExt.get(String.valueOf(key.sysUserId()));
            AbstractRole role = rolesByExt.get(String.valueOf(key.sysOrgId()));
            if (user == null || role == null) {
                throw new BizException(PermissionErrorCode.USER_ROLE_RELATION_NOT_FOUND.getCode(),
                    "local projection missing for user-org bind: userId=" + key.sysUserId()
                        + ", orgId=" + key.sysOrgId());
            }
            // 十轮评审 P1：POSITION 所属组织角色缺失 = 依赖缺失，抛错回滚（不再回退 targetRole）
            AbstractRole relationRole = relationsByExt.get(String.valueOf(resolveRelationOrgId(key)));
            if (relationRole == null) {
                throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
                    "POSITION 所属组织角色投影缺失: positionId=" + key.sysOrgId()
                        + ", relationOrgId=" + resolveRelationOrgId(key));
            }
            Long relationId = relationRole.getId();
            String tripleKey = user.getId() + "|" + role.getId() + "|" + relationId;
            UserRole existing = candidatesByKey.get(tripleKey);
            if (existing != null) {
                existing.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
                existing.setUpdatedAt(now);
                toUpdate.add(existing);
                result.put(key, existing.getId());
            } else if (!pendingInsertKeys.containsValue(tripleKey)) {
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
                toInsert.add(ur);
                pendingInsertKeys.put(key, tripleKey);
            } else {
                // 同批重复三元组：幂等跳过（首个 key 插入后回查 id），本 key 记 null
                result.put(key, null);
            }
        }
        if (!toInsert.isEmpty()) {
            userRoleMapper.insertBatch(toInsert);
            // JDBC batch 无法回填 generated keys：按三元组批量回查新插入行的 id
            Map<String, Long> insertedIds = queryUserRoleIdsByTriples(tenantId, pendingInsertKeys.values());
            for (Map.Entry<UserOrgBindKey, String> entry : pendingInsertKeys.entrySet()) {
                result.put(entry.getKey(), insertedIds.get(entry.getValue()));
            }
        }
        // MyBatis-Flex BaseMapper 无 updateBatch；toUpdate 仅覆盖已存在行的 owner/updatedAt 刷新，
        // 重复绑定场景数量有限，循环单条更新可接受
        for (UserRole ur : toUpdate) {
            userRoleMapper.update(ur);
        }
        return result;
    }

    /** 按三元组批量回查 user_role.id（batchBind 新插入行主键回查，一次批量查询）。 */
    private Map<String, Long> queryUserRoleIdsByTriples(Long tenantId, Collection<String> triples) {
        if (triples == null || triples.isEmpty()) {
            return Map.of();
        }
        Set<Long> userIds = new HashSet<>();
        Set<Long> targetIds = new HashSet<>();
        Set<Long> relationIds = new HashSet<>();
        for (String triple : triples) {
            String[] parts = triple.split("\\|");
            if (parts.length == 3) {
                userIds.add(Long.valueOf(parts[0]));
                targetIds.add(Long.valueOf(parts[1]));
                relationIds.add(Long.valueOf(parts[2]));
            }
        }
        Map<String, Long> result = new HashMap<>();
        for (UserRole ur : userRoleMapper.selectValidByUserTargetRelation(
            tenantId, userIds, targetIds, relationIds, ResourceTypeCode.ROLE)) {
            result.put(ur.getAbstractUserId() + "|" + ur.getTargetId() + "|" + ur.getRelationId(), ur.getId());
        }
        return result;
    }

    /** 批量键的 relation 所属组织 id（POSITION 用 relationSysOrgId，ORG 用自身）。 */
    private static Long resolveRelationOrgId(UserOrgBindKey key) {
        return LocalProjectionOwner.ROLE_POSITION.equals(key.roleTypeCode()) && key.relationSysOrgId() != null
            ? key.relationSysOrgId()
            : key.sysOrgId();
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

        // 1. 批量加载 abstract_user / abstract_role（ORG+POSITION）/ relationRole（ORG，含 POSITION 所属组织）
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
        Set<String> relationOrgExtIds = keys.stream()
            .map(k -> String.valueOf(resolveRelationOrgId(k)))
            .collect(Collectors.toSet());
        Map<String, AbstractRole> relationsByExt = abstractRoleMapper
            .selectByTypeAndExternalIds(tenantId, orgRoleType, relationOrgExtIds)
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
            // 十轮评审 P1：POSITION 所属组织角色缺失 = 依赖缺失，抛错回滚（不再回退 targetRole，
            // 否则删除按错误三元组匹配不到旧记录导致投影残留）
            AbstractRole relationRole = relationsByExt.get(String.valueOf(resolveRelationOrgId(key)));
            if (relationRole == null) {
                throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
                    "POSITION 所属组织角色投影缺失: positionId=" + key.sysOrgId()
                        + ", relationOrgId=" + resolveRelationOrgId(key));
            }
            Long relationId = relationRole.getId();
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
    public Set<Long> migratePositionRelation(Long tenantId, Long sysPositionId,
                                             Long oldRelationOrgId, Long newRelationOrgId) {
        if (oldRelationOrgId == null || newRelationOrgId == null || oldRelationOrgId.equals(newRelationOrgId)) {
            return Set.of();
        }
        Integer roleTypePosition = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_POSITION);
        Integer orgRoleType = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_ORG);
        AbstractRole positionRole = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, roleTypePosition, String.valueOf(sysPositionId));
        if (positionRole == null) {
            return Set.of();
        }
        AbstractRole oldRelation = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, orgRoleType, String.valueOf(oldRelationOrgId));
        AbstractRole newRelation = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, orgRoleType, String.valueOf(newRelationOrgId));
        if (newRelation == null) {
            // 十轮评审 P1：新所属组织角色投影缺失 = 依赖缺失，整体回滚
            throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
                "新所属组织角色投影缺失: orgId=" + newRelationOrgId);
        }
        if (oldRelation == null) {
            return Set.of();
        }
        LocalDateTime now = LocalDateTime.now();
        Set<Long> affected = new LinkedHashSet<>();
        for (UserRole ur : userRoleMapper.selectValidByTargetIdAndType(
            tenantId, positionRole.getId(), ResourceTypeCode.ROLE)) {
            if (oldRelation.getId().equals(ur.getRelationId())) {
                ur.setRelationId(newRelation.getId());
                ur.setUpdatedAt(now);
                userRoleMapper.update(ur);
                affected.add(ur.getAbstractUserId());
            }
        }
        return affected;
    }

    @Override
    public void batchDeleteAdminUsers(Long tenantId, Set<Long> sysUserIds) {
        if (sysUserIds == null || sysUserIds.isEmpty()) {
            return;
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_USER);
        Set<String> extIds = sysUserIds.stream().map(String::valueOf).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<AbstractUser> users = abstractUserMapper.selectByTypeAndExternalIds(tenantId, userType, extIds);
        if (!users.isEmpty()) {
            abstractUserMapper.softDeleteBatch(tenantId,
                users.stream().map(AbstractUser::getId).collect(Collectors.toList()), now);
        }
        List<ResourceEntity> resources = resourceEntityMapper.selectByTypeAndCodes(tenantId, resourceType, extIds);
        if (!resources.isEmpty()) {
            resourceEntityMapper.softDeleteBatch(tenantId,
                resources.stream().map(ResourceEntity::getId).collect(Collectors.toList()), now);
        }
    }

    @Override
    public void batchDisableAdminUsers(Long tenantId, Set<Long> sysUserIds) {
        if (sysUserIds == null || sysUserIds.isEmpty()) {
            return;
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_USER);
        Set<String> extIds = sysUserIds.stream().map(String::valueOf).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<AbstractUser> users = abstractUserMapper.selectByTypeAndExternalIds(tenantId, userType, extIds);
        if (!users.isEmpty()) {
            abstractUserMapper.batchDisable(tenantId,
                users.stream().map(AbstractUser::getId).collect(Collectors.toSet()), now);
        }
        List<ResourceEntity> resources = resourceEntityMapper.selectByTypeAndCodes(tenantId, resourceType, extIds);
        if (!resources.isEmpty()) {
            resourceEntityMapper.batchDisableStatus(tenantId,
                resources.stream().map(ResourceEntity::getId).collect(Collectors.toSet()), now);
        }
    }

    @Override
    public Map<Long, Long> batchUpsertAdminUsers(Long tenantId, List<UpsertUserKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer resourceType = requireType(tenantId, "resource_type", LocalProjectionOwner.RESOURCE_ADMIN_USER);
        Set<String> extIds = keys.stream()
            .map(k -> String.valueOf(k.sysUserId())).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();

        // 批量加载已有投影
        Map<String, AbstractUser> usersByExt = abstractUserMapper
            .selectByTypeAndExternalIds(tenantId, userType, extIds)
            .stream().collect(Collectors.toMap(AbstractUser::getExternalId, u -> u, (a, b) -> a));
        Map<String, ResourceEntity> resourcesByCode = resourceEntityMapper
            .selectByTypeAndCodes(tenantId, resourceType, extIds)
            .stream()
            .filter(r -> CODE_TYPE_DEFAULT.equals(r.getCodeType()))
            .collect(Collectors.toMap(ResourceEntity::getCode, r -> r, (a, b) -> a));

        // 逐 key 计算，新行 insertBatch / 已有行更新
        List<AbstractUser> toInsertUsers = new ArrayList<>();
        List<AbstractUser> toUpdateUsers = new ArrayList<>();
        List<ResourceEntity> toInsertResources = new ArrayList<>();
        List<ResourceEntity> toUpdateResources = new ArrayList<>();
        for (UpsertUserKey key : keys) {
            String externalId = String.valueOf(key.sysUserId());
            int statusVal = key.enabled() ? STATUS_ENABLED : STATUS_DISABLED;
            AbstractUser user = usersByExt.get(externalId);
            if (user == null) {
                user = new AbstractUser();
                user.setTenantId(tenantId);
                user.setUserType(userType);
                user.setExternalId(externalId);
                user.setName(key.name());
                user.setEnabled(key.enabled());
                user.setExtra(key.extraJson());
                user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
                user.setCreatedAt(now);
                user.setUpdatedAt(now);
                user.setDeleteFlag(0L);
                toInsertUsers.add(user);
            } else {
                user.setName(key.name());
                user.setEnabled(key.enabled());
                if (key.extraJson() != null) {
                    user.setExtra(key.extraJson());
                }
                user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
                user.setUpdatedAt(now);
                toUpdateUsers.add(user);
            }
            ResourceEntity resource = resourcesByCode.get(externalId);
            if (resource == null) {
                resource = new ResourceEntity();
                resource.setTenantId(tenantId);
                resource.setResourceType(resourceType);
                resource.setCode(externalId);
                resource.setCodeType(CODE_TYPE_DEFAULT);
                resource.setName(key.name());
                resource.setParentId(null);
                resource.setStatus(statusVal);
                resource.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
                resource.setCreatedAt(now);
                resource.setUpdatedAt(now);
                resource.setDeleteFlag(0L);
                toInsertResources.add(resource);
            } else {
                resource.setName(key.name());
                resource.setStatus(statusVal);
                resource.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
                resource.setUpdatedAt(now);
                toUpdateResources.add(resource);
            }
        }
        if (!toInsertUsers.isEmpty()) {
            abstractUserMapper.insertBatch(toInsertUsers);
        }
        if (!toInsertResources.isEmpty()) {
            resourceEntityMapper.insertBatch(toInsertResources);
        }
        // MyBatis-Flex BaseMapper 无 updateBatch；已有行刷新数量有限，循环单条更新可接受
        for (AbstractUser user : toUpdateUsers) {
            abstractUserMapper.update(user);
        }
        for (ResourceEntity resource : toUpdateResources) {
            resourceEntityMapper.update(resource);
        }
        // 统一回查全部 abstract_user.id（含 insertBatch 新行——JDBC batch 无法回填主键）
        Map<Long, Long> result = new HashMap<>();
        for (AbstractUser user : abstractUserMapper.selectByTypeAndExternalIds(tenantId, userType, extIds)) {
            try {
                result.put(Long.valueOf(user.getExternalId()), user.getId());
            } catch (NumberFormatException ignored) {
                // 本地投影 externalId 恒为数字串
            }
        }
        return result;
    }

    @Override
    public Map<Long, Long> batchFindAdminUserIds(Long tenantId, Set<Long> sysUserIds) {
        if (sysUserIds == null || sysUserIds.isEmpty()) {
            return Map.of();
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Set<String> extIds = sysUserIds.stream().map(String::valueOf).collect(Collectors.toSet());
        Map<Long, Long> result = new HashMap<>();
        for (AbstractUser user : abstractUserMapper.selectByTypeAndExternalIds(tenantId, userType, extIds)) {
            try {
                result.put(Long.valueOf(user.getExternalId()), user.getId());
            } catch (NumberFormatException ignored) {
                // 本地投影 externalId 恒为 sys_*.id 数字串；非数字串（外部同步）不映射
            }
        }
        return result;
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

    private Long resolveParentRoleId(Long tenantId, Integer childRoleType, Long parentOrgId,
                                     String parentOrgType) {
        if (parentOrgId == null || parentOrgId == 0L) {
            return null;
        }
        // 九轮评审 P1：父角色类型按父节点实际 orgType 解析（ORG 父 + POSITION 子不再查不到）；
        // parentOrgType 缺失时回退用子节点 roleType，兼容未提供父类型的调用
        Integer parentRoleType = childRoleType;
        if (parentOrgType != null && !parentOrgType.isBlank()) {
            parentRoleType = requireType(tenantId, "role_type", resolveOrgRoleType(parentOrgType));
        }
        AbstractRole parent = abstractRoleMapper.selectByTypeAndExternalId(
            tenantId, parentRoleType, String.valueOf(parentOrgId));
        if (parent == null) {
            // 十轮评审 P1：父角色投影缺失 = 依赖缺失，抛错回滚（不再静默写 parentId=null 脱离父树）
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
