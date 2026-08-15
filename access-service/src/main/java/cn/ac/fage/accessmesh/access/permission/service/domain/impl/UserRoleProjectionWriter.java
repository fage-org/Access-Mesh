package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.entity.table.UserRoleTableDef;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.mybatisflex.core.query.QueryWrapper;

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
 * 用户-组织角色（user_role）投影写入组件：单条/批量绑定、解绑与岗位成员 relation 迁移。
 * <p>
 * 由 {@link LocalProjectionDomainServiceImpl} 内部装配（构造注入 mapper），不独立作为 Spring bean；
 * 事务边界由调用方 AppService 声明，本组件只承担领域规则与数据写入。
 * </p>
 * <p>
 * relation 语义：POSITION 成员绑定到所属组织角色（relationSysOrgId），普通 ORG 沿用自身角色。
 * 所属组织角色 / 父角色等依赖投影缺失 = 依赖缺失（20043），整体回滚（强事务投影 fail-closed）。
 * </p>
*/
public class UserRoleProjectionWriter {

    private final TypeResolutionService typeResolutionService;
    private final AbstractUserMapper abstractUserMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final UserRoleMapper userRoleMapper;

    public UserRoleProjectionWriter(TypeResolutionService typeResolutionService,
    AbstractUserMapper abstractUserMapper,
    AbstractRoleMapper abstractRoleMapper,
    UserRoleMapper userRoleMapper) {
        this.typeResolutionService = typeResolutionService;
        this.abstractUserMapper = abstractUserMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.userRoleMapper = userRoleMapper;
    }

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

    public Map<LocalProjectionDomainService.UserOrgBindKey, Long> batchBindUserOrg(
    Long tenantId, List<LocalProjectionDomainService.UserOrgBindKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer orgRoleType = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_ORG);
        Integer roleTypePosition = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_POSITION);

        Set<String> userExtIds = keys.stream()
        .map(k -> String.valueOf(k.sysUserId())).collect(Collectors.toSet());
        Set<String> orgExtIds = keys.stream()
        .map(k -> String.valueOf(k.sysOrgId())).collect(Collectors.toSet());

        // 1. 批量加载 abstract_user / abstract_role（ORG+POSITION）/ relationRole（ORG，含 POSITION 所属组织）
        Map<String, AbstractUser> usersByExt = abstractUserMapper
        .selectByTypeAndExternalIds(tenantId, userType, userExtIds)
        .stream().collect(Collectors.toMap(AbstractUser::getExternalId, u -> u, (a, b) -> a));
        // 角色索引键 = roleTypeCode + "|" + externalId：同一 externalId 的 ORG/POSITION 投影可共存
        // （数据库唯一约束含 role_type），必须按请求类型精确取值，与单条路径精确类型查询语义一致
        Map<String, AbstractRole> rolesByExt = new HashMap<>();
        if (!orgExtIds.isEmpty()) {
            abstractRoleMapper.selectByTypeAndExternalIds(tenantId, orgRoleType, orgExtIds)
            .forEach(r -> rolesByExt.put(LocalProjectionOwner.ROLE_ORG + "|" + r.getExternalId(), r));
            abstractRoleMapper.selectByTypeAndExternalIds(tenantId, roleTypePosition, orgExtIds)
            .forEach(r -> rolesByExt.put(LocalProjectionOwner.ROLE_POSITION + "|" + r.getExternalId(), r));
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
        // 保留三元组 → 完整实体映射（含主键），已有行更新不重建无 id 实体
        Map<String, UserRole> candidatesByKey = candidates.stream()
        .collect(Collectors.toMap(
        ur -> ur.getAbstractUserId() + "|" + ur.getTargetId() + "|" + ur.getRelationId(),
        ur -> ur, (a, b) -> a));

        // 3. 逐 key 计算三元组，批量 insert（新）或批量刷新（已存在，一次 UPDATE）
        LocalDateTime now = LocalDateTime.now();
        List<UserRole> toInsert = new ArrayList<>();
        List<Long> toUpdateIds = new ArrayList<>();
        Map<LocalProjectionDomainService.UserOrgBindKey, Long> result = new LinkedHashMap<>();
        // 本批新插入三元组 → key（insert 后按三元组批量回查 id；同批重复仅首个 key 记录）
        Map<LocalProjectionDomainService.UserOrgBindKey, String> pendingInsertKeys = new LinkedHashMap<>();
        Set<String> pendingTriples = new HashSet<>();
        for (LocalProjectionDomainService.UserOrgBindKey key : keys) {
            AbstractUser user = usersByExt.get(String.valueOf(key.sysUserId()));
            AbstractRole role = rolesByExt.get(key.roleTypeCode() + "|" + key.sysOrgId());
            if (user == null || role == null) {
                throw new BizException(PermissionErrorCode.USER_ROLE_RELATION_NOT_FOUND.getCode(),
                "local projection missing for user-org bind: userId=" + key.sysUserId()
                + ", orgId=" + key.sysOrgId());
            }
            // POSITION 所属组织角色缺失 = 依赖缺失，抛错回滚（不再回退 targetRole）
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
                toUpdateIds.add(existing.getId());
                result.put(key, existing.getId());
            } else if (!pendingTriples.contains(tripleKey)) {
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
                pendingTriples.add(tripleKey);
            } else {
                // 同批重复三元组：幂等跳过（首个 key 插入后回查 id），本 key 记 null
                result.put(key, null);
            }
        }
        if (!toInsert.isEmpty()) {
            userRoleMapper.insertBatch(toInsert);
            // JDBC batch 无法回填 generated keys：按三元组批量回查新插入行的 id
            Map<String, Long> insertedIds = queryUserRoleIdsByTriples(tenantId, pendingInsertKeys.values());
            for (Map.Entry<LocalProjectionDomainService.UserOrgBindKey, String> entry : pendingInsertKeys.entrySet()) {
                result.put(entry.getKey(), insertedIds.get(entry.getValue()));
            }
        }
        // 已有行统一刷新 owner/updatedAt（单条 SQL，替代循环 update）
        if (!toUpdateIds.isEmpty()) {
            userRoleMapper.batchRefreshOwner(tenantId, LocalProjectionOwner.SERVICE_CODE, toUpdateIds, now);
        }
        return result;
    }

    public void batchUnbindUserOrg(Long tenantId, List<LocalProjectionDomainService.UserOrgBindKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        Integer userType = requireType(tenantId, "user_type", LocalProjectionOwner.SUBJECT_ADMIN_USER);
        Integer orgRoleType = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_ORG);
        Integer roleTypePosition = requireType(tenantId, "role_type", LocalProjectionOwner.ROLE_POSITION);

        Set<String> userExtIds = keys.stream()
        .map(k -> String.valueOf(k.sysUserId())).collect(Collectors.toSet());
        Set<String> orgExtIds = keys.stream()
        .map(k -> String.valueOf(k.sysOrgId())).collect(Collectors.toSet());

        // 1. 批量加载 abstract_user / abstract_role（ORG+POSITION）/ relationRole（ORG，含 POSITION 所属组织）
        Map<String, AbstractUser> usersByExt = abstractUserMapper
        .selectByTypeAndExternalIds(tenantId, userType, userExtIds)
        .stream().collect(Collectors.toMap(AbstractUser::getExternalId, u -> u, (a, b) -> a));
        // 角色索引键 = roleTypeCode + "|" + externalId：同一 externalId 的 ORG/POSITION 投影可共存，
        // 必须按请求类型精确取值（与单条路径精确类型查询语义一致）
        Map<String, AbstractRole> rolesByExt = new HashMap<>();
        if (!orgExtIds.isEmpty()) {
            abstractRoleMapper.selectByTypeAndExternalIds(tenantId, orgRoleType, orgExtIds)
            .forEach(r -> rolesByExt.put(LocalProjectionOwner.ROLE_ORG + "|" + r.getExternalId(), r));
            abstractRoleMapper.selectByTypeAndExternalIds(tenantId, roleTypePosition, orgExtIds)
            .forEach(r -> rolesByExt.put(LocalProjectionOwner.ROLE_POSITION + "|" + r.getExternalId(), r));
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
        for (LocalProjectionDomainService.UserOrgBindKey key : keys) {
            AbstractUser user = usersByExt.get(String.valueOf(key.sysUserId()));
            AbstractRole role = rolesByExt.get(key.roleTypeCode() + "|" + key.sysOrgId());
            if (user == null || role == null) {
                continue;
            }
            // POSITION 所属组织角色缺失 = 依赖缺失，抛错回滚（不再回退 targetRole，
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
            // 岗位角色投影缺失 = 依赖缺失（岗位移动必须迁移其成员 relation，无岗位角色无法定位成员）
            throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
            "岗位角色投影缺失: positionId=" + sysPositionId);
        }
        AbstractRole oldRelation = abstractRoleMapper.selectByTypeAndExternalId(
        tenantId, orgRoleType, String.valueOf(oldRelationOrgId));
        AbstractRole newRelation = abstractRoleMapper.selectByTypeAndExternalId(
        tenantId, orgRoleType, String.valueOf(newRelationOrgId));
        if (newRelation == null) {
            // 新所属组织角色投影缺失 = 依赖缺失，整体回滚
            throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
            "新所属组织角色投影缺失: orgId=" + newRelationOrgId);
        }
        if (oldRelation == null) {
            // 旧所属组织角色投影缺失 = 依赖缺失（成员 relation 指向旧所属组织，缺失时无法判定待迁移成员）
            throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_DEPENDENCY_MISSING.getCode(),
            "旧所属组织角色投影缺失: orgId=" + oldRelationOrgId);
        }
        LocalDateTime now = LocalDateTime.now();
        Set<Long> affected = new LinkedHashSet<>();
        List<Long> toMigrateIds = new ArrayList<>();
        for (UserRole ur : userRoleMapper.selectValidByTargetIdAndType(
        tenantId, positionRole.getId(), ResourceTypeCode.ROLE)) {
            if (oldRelation.getId().equals(ur.getRelationId())) {
                toMigrateIds.add(ur.getId());
                affected.add(ur.getAbstractUserId());
            }
        }
        // 单条 SQL 批量迁移（替代循环 update）
        if (!toMigrateIds.isEmpty()) {
            userRoleMapper.batchUpdateRelationByIds(tenantId, newRelation.getId(), toMigrateIds, now);
        }
        return affected;
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

    /**
     * 解析 user_role.relation_id：POSITION 成员绑定到所属组织角色（ORG:relationSysOrgId），
     * 普通 ORG 沿用自身角色。所属组织角色缺失 = 依赖缺失，抛 BizException 整体回滚
     * （强事务投影 fail-closed，不再回退 targetRole 制造 target_id == relation_id）。
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

    /** 批量键的 relation 所属组织 id（POSITION 用 relationSysOrgId，ORG 用自身）。 */
    private static Long resolveRelationOrgId(LocalProjectionDomainService.UserOrgBindKey key) {
        return LocalProjectionOwner.ROLE_POSITION.equals(key.roleTypeCode()) && key.relationSysOrgId() != null
        ? key.relationSysOrgId()
        : key.sysOrgId();
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
}
