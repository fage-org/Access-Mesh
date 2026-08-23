package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.RoleType;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 主体领域服务实现类
 * <p>
 * 合并 AbstractUserDomainServiceImpl、AbstractRoleDomainServiceImpl、UserRoleDomainServiceImpl 三个旧实现。
 * 提供用户、角色、用户角色关系的基础数据访问和解析功能。
 * 用户角色解析支持双层缓存（L1本地缓存 + L2 Redis缓存）。
 * 组角色展开使用PostgreSQL递归CTE一次性查询所有子孙角色，在内存中展开。
 * </p>
 */
@Service
public class SubjectDomainServiceImpl implements SubjectDomainService {

    private static final Logger log = LoggerFactory.getLogger(SubjectDomainServiceImpl.class);

    private final AbstractUserMapper abstractUserMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final UserRoleMapper userRoleMapper;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param abstractUserMapper  抽象用户数据访问层
     * @param abstractRoleMapper  抽象角色数据访问层
     * @param userRoleMapper      用户角色数据访问层
     * @param cacheService        统一缓存服务
     * @param objectMapper        JSON解析器
     */
    public SubjectDomainServiceImpl(AbstractUserMapper abstractUserMapper,
                                    AbstractRoleMapper abstractRoleMapper,
                                    UserRoleMapper userRoleMapper,
                                    CacheService cacheService,
                                    ObjectMapper objectMapper) {
        this.abstractUserMapper = abstractUserMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.userRoleMapper = userRoleMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    // ===== AbstractUser =====

    /**
     * 查询有效的用户实体
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户实体，不存在返回null
     */
    @Override
    public AbstractUser selectValidUserById(Long tenantId, Long userId) {
        if (userId == null) {
            return null;
        }
        return abstractUserMapper.selectValidById(userId, tenantId);
    }

    // ===== AbstractRole =====

    /**
     * 创建角色
     * <p>
     * 创建新的角色实体。如果指定父角色，需确保父角色存在且类型匹配。
     * </p>
     *
     * @param tenantId   租户ID
     * @param parentId   父角色ID，可选
     * @param roleType   角色类型值
     * @param externalId 外部ID
     * @param name       角色名称
     * @param sortOrder  排序值，可选
     * @param extra      扩展JSON，可选
     * @return 新创建的角色ID
     * @throws BizException 父角色不存在或类型不匹配时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(Long tenantId, Long parentId, Integer roleType,
                           String externalId, String name, Integer sortOrder, String extra) {
        RoleType rt = RoleType.fromValue(roleType);

        if (parentId != null) {
            AbstractRole parent = selectValidRoleById(tenantId, parentId);
            if (parent == null) {
                throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "Parent role not found: " + parentId);
            }
            if (!parent.getRoleType().equals(roleType)) {
                throw new BizException(PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode(),
                    "Child roleType must match parent roleType: expected " + parent.getRoleType() + ", got " + roleType);
            }
        }

        AbstractRole role = new AbstractRole();
        role.setTenantId(tenantId);
        role.setParentId(parentId);
        role.setRoleType(roleType);
        role.setExternalId(externalId);
        role.setName(name);
        role.setStatus(1);
        role.setSortOrder(sortOrder != null ? sortOrder : 0);
        role.setExtra(extra);
        LocalDateTime now = LocalDateTime.now();
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setDeleteFlag(0L);
        abstractRoleMapper.insert(role);
        return role.getId();
    }

    @Override
    public AbstractRole selectValidRoleById(Long tenantId, Long roleId) {
        if (roleId == null) {
            return null;
        }
        // T-ACCESS-019 IT 暴露的存量参数反转：mapper 约定为 (id, tenantId)，
        // 原实参 (tenantId, roleId) 使 updateRole/moveRole/deleteRoles 对 roleId≠租户号的角色恒报不存在
        return abstractRoleMapper.selectValidById(roleId, tenantId);
    }

    @Override
    public List<AbstractRole> selectValidRolesByIds(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return abstractRoleMapper.selectValidByIds(tenantId, roleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteRoleBatch(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        abstractRoleMapper.softDeleteBatch(tenantId, new ArrayList<>(roleIds), now);
    }

    @Override
    public List<Long> resolveDescendantRoleIdsBatch(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> descendantIds = abstractRoleMapper.selectDescendantIdsBatch(tenantId, roleIds);
        return descendantIds != null ? descendantIds : Collections.emptyList();
    }

    // ===== UserRole =====

    /**
     * 解析用户的有效角色
     * <p>
     * 直接委托给批量方法，缓存检查统一在批量方法中处理。
     * </p>
     */
    @Override
    public Set<Long> resolveEffectiveRoles(Long tenantId, Long userId) {
        Map<Long, Set<Long>> batchResult = resolveEffectiveRolesBatch(tenantId, Set.of(userId));
        return batchResult.getOrDefault(userId, Collections.emptySet());
    }

    /**
     * 批量解析多个用户的有效角色
     * <p>
     * 使用批量查询方法避免N+1问题。
     * </p>
     */
    @Override
    public Map<Long, Set<Long>> batchResolveEffectiveRoles(Long tenantId, Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return resolveEffectiveRolesBatch(tenantId, userIds);
    }

    /**
     * 批量解析多个用户的有效角色（内部实现）
     * <p>
     * 实现真正的批量查询逻辑：
     * 1. 先通过 CacheService 检查 L1/L2 缓存
     * 2. 批量查询未命中用户的UserRole记录
     * 3. 批量展开组角色
     * 4. 批量过滤角色状态
     * 5. 写入缓存并返回结果
     * </p>
     */
    private Map<Long, Set<Long>> resolveEffectiveRolesBatch(Long tenantId, Set<Long> userIds) {
        Map<Long, Set<Long>> result = new HashMap<>(
            cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userIds));
        Set<Long> uncachedUserIds = new HashSet<>(userIds);
        uncachedUserIds.removeAll(result.keySet());

        if (uncachedUserIds.isEmpty()) {
            return result;
        }

        // T-ACCESS-008：授权 L2 miss——数据库读取前记录单调时钟起点，回填只写剩余 TTL
        CacheReadToken<Set<Long>> readToken = cacheService.beginRead(PermCacheCatalog.EFFECTIVE_ROLES);

        // T-ACCESS-019 评审 P1-2：DDL 语义 enabled=false 时鉴权不通过——禁用主体有效角色置空
        // （空集同样回填缓存，重新启用由写路径 markUsers 失效）
        Map<Long, Set<Long>> disabledEmpty = new HashMap<>();
        Set<Long> disabledUserIds = new HashSet<>(
            abstractUserMapper.selectDisabledIdsByIds(tenantId, uncachedUserIds));
        for (Long disabledId : disabledUserIds) {
            disabledEmpty.put(disabledId, Collections.emptySet());
            result.put(disabledId, Collections.emptySet());
        }
        uncachedUserIds.removeAll(disabledUserIds);
        if (uncachedUserIds.isEmpty()) {
            cacheService.putBatch(readToken, tenantId, disabledEmpty);
            return result;
        }

        // 2. 批量查询所有未命中缓存的用户的UserRole记录
        LocalDateTime now = LocalDateTime.now();
        List<UserRole> allUserRoles = userRoleMapper.selectValidByUserIdsWithValidity(tenantId, uncachedUserIds, now);

        // 3. 按userId分组，收集所有涉及的roleId和groupId
        Map<Long, Set<Long>> userToRoleIds = new HashMap<>();
        Set<Long> groupRoleIds = new HashSet<>();

        for (UserRole ur : allUserRoles) {
            Long userId = ur.getAbstractUserId();
            userToRoleIds.computeIfAbsent(userId, k -> new HashSet<>());

            if (PermConstants.TargetType.GROUP_ROLE.equals(ur.getTargetType())) {
                groupRoleIds.add(ur.getTargetId());
            } else {
                userToRoleIds.get(userId).add(ur.getTargetId());
            }
        }

        // 4. 批量展开GROUP_ROLE（递归收集所有基础角色）
        Set<Long> allRoleIds = new HashSet<>();
        for (Set<Long> roleIds : userToRoleIds.values()) {
            allRoleIds.addAll(roleIds);
        }
        allRoleIds.addAll(groupRoleIds);

        // 批量解析组角色
        Map<Long, Set<Long>> groupRoleExpandCache = resolveGroupRolesBatch(tenantId, groupRoleIds);

        // 将展开后的角色添加到用户角色集合
        for (UserRole ur : allUserRoles) {
            if (PermConstants.TargetType.GROUP_ROLE.equals(ur.getTargetType())) {
                Set<Long> expandedRoles = groupRoleExpandCache.get(ur.getTargetId());
                if (expandedRoles != null) {
                    userToRoleIds.get(ur.getAbstractUserId()).addAll(expandedRoles);
                }
            }
        }

        // 5. 收集所有需要过滤的角色ID
        Set<Long> allCandidateRoleIds = new HashSet<>();
        for (Set<Long> roleIds : userToRoleIds.values()) {
            allCandidateRoleIds.addAll(roleIds);
        }

        // 6. 批量查询所有AbstractRole并过滤状态
        Set<Long> enabledRoleIds = new HashSet<>();
        if (!allCandidateRoleIds.isEmpty()) {
            List<Long> enabledIdList = abstractRoleMapper.selectEnabledIdsByIds(tenantId, allCandidateRoleIds);
            enabledRoleIds = new HashSet<>(enabledIdList);
        }

        // 7. 在内存中为每个userId组装有效角色列表，并批量回填缓存（含禁用主体空集）
        Map<Long, Set<Long>> uncachedResults = new HashMap<>(disabledEmpty);
        for (Long userId : uncachedUserIds) {
            Set<Long> userRoleIds = userToRoleIds.getOrDefault(userId, Collections.emptySet());
            Set<Long> effectiveRoles = new HashSet<>(userRoleIds);
            effectiveRoles.retainAll(enabledRoleIds);

            uncachedResults.put(userId, effectiveRoles);
            result.put(userId, effectiveRoles);
        }

        cacheService.putBatch(readToken, tenantId, uncachedResults);

        return result;
    }

    /**
     * 批量解析组角色
     * <p>
     * 使用PostgreSQL递归CTE一次性查询所有子孙角色，然后在内存中展开。
     * 仅 status=1 的组角色参与展开（fail-closed：写入口未限定 status 取值，
     * 非 0/1 值不得视为启用；与基础角色 selectEnabledIdsByIds 的 status=1 口径对齐）：
     * 根组角色非启用 → 展开为空；递归遇非启用嵌套组 → 剪枝其整棵子树。
     * </p>
     */
    private Map<Long, Set<Long>> resolveGroupRolesBatch(Long tenantId, Set<Long> groupRoleIds) {
        Map<Long, Set<Long>> result = new HashMap<>();
        if (groupRoleIds == null || groupRoleIds.isEmpty()) {
            return result;
        }

        List<AbstractRole> allRoles = abstractRoleMapper.selectRoleTreeByGroupIds(groupRoleIds, tenantId);

        Map<Long, List<AbstractRole>> parentToChildren = allRoles.stream()
            .filter(r -> r.getParentId() != null)
            .collect(Collectors.groupingBy(AbstractRole::getParentId));

        Map<Long, AbstractRole> roleMap = allRoles.stream()
            .collect(Collectors.toMap(AbstractRole::getId, r -> r));

        Set<Long> nestedGroupRoleIds = allRoles.stream()
            .filter(r -> r.getRoleType() != null && r.getRoleType() == RoleType.GROUP_ROLE.getValue())
            .map(AbstractRole::getId)
            .collect(Collectors.toSet());

        Set<Long> disabledRoleIds = allRoles.stream()
            .filter(r -> r.getStatus() == null || r.getStatus() != 1)
            .map(AbstractRole::getId)
            .collect(Collectors.toSet());

        for (Long groupRoleId : groupRoleIds) {
            if (disabledRoleIds.contains(groupRoleId)) {
                result.put(groupRoleId, Set.of());
                continue;
            }
            Set<Long> expanded = expandInMemory(groupRoleId, parentToChildren, roleMap, nestedGroupRoleIds,
                disabledRoleIds, new HashSet<>());
            result.put(groupRoleId, expanded);
        }

        return result;
    }

    /**
     * 在内存中递归展开组角色；遇非启用（status≠1）嵌套组剪枝其整棵子树（fail-closed）
     */
    private Set<Long> expandInMemory(Long roleId, Map<Long, List<AbstractRole>> parentToChildren,
                                     Map<Long, AbstractRole> roleMap, Set<Long> nestedGroupRoleIds,
                                     Set<Long> disabledRoleIds, Set<Long> visited) {
        if (roleId == null || visited.contains(roleId)) {
            return Set.of();
        }
        visited.add(roleId);

        Set<Long> result = new HashSet<>();
        AbstractRole role = roleMap.get(roleId);

        if (role != null) {
            Set<Long> basicIds = parseBasicRoleIds(role.getExtra());
            for (Long basicId : basicIds) {
                AbstractRole basicRole = roleMap.get(basicId);
                if (basicRole != null && basicRole.getRoleType() != null
                    && basicRole.getRoleType() == RoleType.GROUP_ROLE.getValue()) {
                    if (!disabledRoleIds.contains(basicId)) {
                        result.addAll(expandInMemory(basicId, parentToChildren, roleMap, nestedGroupRoleIds,
                            disabledRoleIds, visited));
                    }
                } else if (basicRole != null) {
                    result.add(basicId);
                }
            }
        }

        List<AbstractRole> children = parentToChildren.getOrDefault(roleId, List.of());
        for (AbstractRole child : children) {
            if (child.getRoleType() != null && child.getRoleType() == RoleType.GROUP_ROLE.getValue()) {
                if (!disabledRoleIds.contains(child.getId())) {
                    result.addAll(expandInMemory(child.getId(), parentToChildren, roleMap, nestedGroupRoleIds,
                        disabledRoleIds, visited));
                }
            } else {
                result.add(child.getId());
            }
        }

        return result;
    }

    /**
     * 解析extra字段中的basicRoleIds
     */
    private Set<Long> parseBasicRoleIds(String extra) {
        Set<Long> basicRoleIds = new HashSet<>();
        if (StringUtils.isNotEmpty(extra)) {
            try {
                JsonNode extraNode = objectMapper.readTree(extra);
                JsonNode basicRoleIdsNode = extraNode.get("basicRoleIds");
                if (basicRoleIdsNode != null && basicRoleIdsNode.isArray()) {
                    for (JsonNode idNode : basicRoleIdsNode) {
                        Long basicRoleId = idNode.asLong();
                        if (basicRoleId != null && basicRoleId > 0) {
                            basicRoleIds.add(basicRoleId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse extra.basicRoleIds: {}", e.getMessage());
            }
        }
        return basicRoleIds;
    }

    /**
     * 批量失效多个用户的角色缓存
     */
    @Override
    public void invalidateRoleCacheBatch(Long tenantId, Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        cacheService.evictBatch(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userIds);
    }

    /**
     * 失效角色关联的所有用户缓存
     */
    @Override
    public void invalidateRoleCacheByRole(Long tenantId, Long roleId) {
        Set<Long> userIds = userRoleMapper.selectValidByTargetIdAndType(tenantId, roleId, PermConstants.TargetType.ROLE)
            .stream().map(UserRole::getAbstractUserId).collect(Collectors.toSet());

        List<Long> ancestorGroupRoleIds = abstractRoleMapper.selectAncestorGroupRoleIds(tenantId, roleId);
        if (!ancestorGroupRoleIds.isEmpty()) {
            userIds.addAll(userRoleMapper.selectValidByTargetIdsAndType(
                tenantId, new HashSet<>(ancestorGroupRoleIds), PermConstants.TargetType.GROUP_ROLE)
            .stream().map(UserRole::getAbstractUserId).collect(Collectors.toSet()));
        }

        if (userIds.isEmpty()) {
            return;
        }

        cacheService.evictBatch(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userIds);
    }

    /**
     * 批量失效多个角色关联的所有用户缓存（T-PERM-018 P2：消除按角色循环 N+1）。
     * <p>
     * 固定 ≤4 SQL：① 批量查多角色直接用户（ROLE）；② 批量查多角色直接组绑定用户（GROUP_ROLE，
     * 被变更角色本身为组角色时的组成员）；③ 批量递归查祖先 GROUP_ROLE；
     * ④ 批量查组角色用户（GROUP_ROLE）。一次 evictBatch(EFFECTIVE_ROLES)。
     * </p>
     */
    @Override
    public void invalidateRoleCacheByRoles(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> userIds = findUserIdsByEffectiveRoles(tenantId, roleIds);
        if (userIds.isEmpty()) {
            return;
        }
        cacheService.evictBatch(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userIds);
    }

    @Override
    public Set<Long> findUserIdsByEffectiveRoles(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> userIds = new HashSet<>(userRoleMapper.selectValidByTargetIdsAndType(
                tenantId, roleIds, PermConstants.TargetType.ROLE)
            .stream().map(UserRole::getAbstractUserId).filter(Objects::nonNull).collect(Collectors.toSet()));

        // 直接 GROUP_ROLE 绑定（被变更角色本身为组角色时的组成员；祖先查询显式排除起点 id，
        // 该类成员须单独覆盖——二轮评审 P1-B）
        userIds.addAll(userRoleMapper.selectValidByTargetIdsAndType(
                tenantId, roleIds, PermConstants.TargetType.GROUP_ROLE)
            .stream().map(UserRole::getAbstractUserId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<Long> ancestorGroupRoleIds = abstractRoleMapper.selectAncestorGroupRoleIdsBatch(tenantId, roleIds);
        if (!ancestorGroupRoleIds.isEmpty()) {
            userIds.addAll(userRoleMapper.selectValidByTargetIdsAndType(
                tenantId, new HashSet<>(ancestorGroupRoleIds), PermConstants.TargetType.GROUP_ROLE)
            .stream().map(UserRole::getAbstractUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        return userIds;
    }
}
