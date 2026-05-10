package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.UserRole;
import cn.ac.fage.accessmesh.permission.enums.RoleType;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.permission.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef;

/**
 * 用户角色领域服务实现类
 * <p>
 * 实现用户角色的解析与双层缓存管理，支持组角色递归展开、有效期过滤等功能。
 * 采用L1 Caffeine本地缓存 + L2 Redis分布式缓存的架构。
 * </p>
 */
@Service
public class UserRoleDomainServiceImpl implements UserRoleDomainService {

    private static final Logger log = LoggerFactory.getLogger(UserRoleDomainServiceImpl.class);
    private static final String ROLES_KEY_PREFIX = "perm:user:effective-roles:";
    private static final long CACHE_TTL_MINUTES = 30;

    private final UserRoleMapper userRoleMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final PermCacheDomainService permCacheDomainService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param userRoleMapper        用户角色数据访问层
     * @param abstractRoleMapper    抽象角色数据访问层
     * @param permCacheDomainService 权限缓存领域服务
     * @param redisTemplate         Redis操作模板
     * @param objectMapper          JSON解析器
     */
    public UserRoleDomainServiceImpl(UserRoleMapper userRoleMapper,
                                     AbstractRoleMapper abstractRoleMapper,
                                     PermCacheDomainService permCacheDomainService,
                                     RedisTemplate<String, Object> redisTemplate,
                                     ObjectMapper objectMapper) {
        this.userRoleMapper = userRoleMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.permCacheDomainService = permCacheDomainService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析用户的有效角色
     * <p>
     * 采用双层缓存策略：先查L1本地缓存，再查L2 Redis缓存，最后查数据库。
     * 数据库查询委托给批量方法处理，自动填充缓存。
     * </p>
     *
     * @param tenantId    租户ID
     * @param userId      用户ID
     * @param bizDomainId 业务域ID，null表示全局范围
     * @return 用户的有效角色ID集合
     */
    @Override
    public Set<Long> resolveEffectiveRoles(Long tenantId, Long userId, Long bizDomainId) {
        // L1本地缓存
        Optional<Set<Long>> cached = permCacheDomainService.getEffectiveRoles(tenantId, userId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // L2 Redis缓存
        String l2Key = ROLES_KEY_PREFIX + tenantId + ":" + userId;
        Object l2Value = redisTemplate.opsForValue().get(l2Key);
        if (l2Value instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<Long> roles = (Set<Long>) l2Value;
            permCacheDomainService.setEffectiveRoles(tenantId, userId, roles);
            return roles;
        }

        // 委托给批量方法处理数据库查询和缓存写入
        Map<Long, Set<Long>> batchResult = resolveEffectiveRolesBatch(tenantId, Set.of(userId), bizDomainId);
        return batchResult.getOrDefault(userId, Collections.emptySet());
    }

    /**
     * 批量解析多个用户的有效角色
     * <p>
     * 使用批量查询方法避免N+1问题，返回用户ID到角色ID集合的映射
     * </p>
     *
     * @param tenantId    租户ID
     * @param userIds     用户ID集合
     * @param bizDomainId 业务域ID，null表示全局范围
     * @return 用户ID到角色ID集合的映射
     */
    @Override
    public Map<Long, Set<Long>> batchResolveEffectiveRoles(Long tenantId, Set<Long> userIds, Long bizDomainId) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 使用批量查询方法，避免N+1问题
        return resolveEffectiveRolesBatch(tenantId, userIds, bizDomainId);
    }

    /**
     * 批量解析多个用户的有效角色（内部实现）
     * <p>
     * 实现真正的批量查询逻辑：
     * 1. 先检查L1和L2缓存
     * 2. 批量查询未命中用户的UserRole记录
     * 3. 批量展开组角色
     * 4. 批量过滤角色状态
     * 5. 写入缓存并返回结果
     * </p>
     *
     * @param tenantId    租户ID
     * @param userIds     用户ID集合
     * @param bizDomainId 业务域ID
     * @return 用户ID到角色ID集合的映射
     */
    private Map<Long, Set<Long>> resolveEffectiveRolesBatch(Long tenantId, Set<Long> userIds, Long bizDomainId) {
        Map<Long, Set<Long>> result = new HashMap<>();
        Set<Long> uncachedUserIds = new HashSet<>();

        // 1. 先检查缓存（L1 + L2）
        for (Long userId : userIds) {
            // L1本地缓存
            Optional<Set<Long>> l1Cached = permCacheDomainService.getEffectiveRoles(tenantId, userId);
            if (l1Cached.isPresent()) {
                result.put(userId, l1Cached.get());
                continue;
            }

            // L2 Redis缓存
            String l2Key = ROLES_KEY_PREFIX + tenantId + ":" + userId;
            Object l2Value = redisTemplate.opsForValue().get(l2Key);
            if (l2Value instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<Long> roles = (Set<Long>) l2Value;
                permCacheDomainService.setEffectiveRoles(tenantId, userId, roles);
                result.put(userId, roles);
                continue;
            }

            uncachedUserIds.add(userId);
        }

        // 如果所有用户都命中缓存，直接返回
        if (uncachedUserIds.isEmpty()) {
            return result;
        }

        // 2. 批量查询所有未命中缓存的用户的UserRole记录
        LocalDateTime now = LocalDateTime.now();
        List<UserRole> allUserRoles = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                .and(UserRoleTableDef.USER_ROLE.ABSTRACT_USER_ID.in(uncachedUserIds))
                .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
                .and(UserRoleTableDef.USER_ROLE.VALID_FROM.le(now).or(UserRoleTableDef.USER_ROLE.VALID_FROM.isNull()))
                .and(UserRoleTableDef.USER_ROLE.VALID_TO.ge(now).or(UserRoleTableDef.USER_ROLE.VALID_TO.isNull()))
        );

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
            QueryWrapper qw = QueryWrapper.create()
                .where(AbstractRoleTableDef.ABSTRACT_ROLE.ID.in(allCandidateRoleIds))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.STATUS.eq(PermissionConstants.ENABLED_STATUS))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0));
            if (bizDomainId != null) {
                qw.and(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(bizDomainId).or(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull()));
            }
            List<AbstractRole> enabledRoles = abstractRoleMapper.selectListByQuery(qw);
            enabledRoleIds = enabledRoles.stream().map(AbstractRole::getId).collect(Collectors.toSet());
        }

        // 7. 在内存中为每个userId组装有效角色列表
        for (Long userId : uncachedUserIds) {
            Set<Long> userRoleIds = userToRoleIds.getOrDefault(userId, Collections.emptySet());
            Set<Long> effectiveRoles = new HashSet<>(userRoleIds);
            effectiveRoles.retainAll(enabledRoleIds);

            // 写入缓存
            String l2Key = ROLES_KEY_PREFIX + tenantId + ":" + userId;
            redisTemplate.opsForValue().set(l2Key, effectiveRoles, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            permCacheDomainService.setEffectiveRoles(tenantId, userId, effectiveRoles);

            result.put(userId, effectiveRoles);
        }

        return result;
    }

    /**
     * 批量解析组角色
     * <p>
     * 使用PostgreSQL递归CTE一次性查询所有子孙角色，然后在内存中展开。
     * 返回每个groupId对应的基础角色ID集合
     * </p>
     *
     * @param tenantId     租户ID
     * @param groupRoleIds 组角色ID集合
     * @return 组角色ID到基础角色ID集合的映射
     */
    private Map<Long, Set<Long>> resolveGroupRolesBatch(Long tenantId, Set<Long> groupRoleIds) {
        Map<Long, Set<Long>> result = new HashMap<>();
        if (groupRoleIds == null || groupRoleIds.isEmpty()) {
            return result;
        }

        // 使用递归CTE一次性查询所有子孙角色
        List<AbstractRole> allRoles = abstractRoleMapper.selectRoleTreeByGroupIds(groupRoleIds, tenantId);

        // 构建parentId到children映射
        Map<Long, List<AbstractRole>> parentToChildren = allRoles.stream()
            .filter(r -> r.getParentId() != null)
            .collect(Collectors.groupingBy(AbstractRole::getParentId));

        // 构建id到role映射
        Map<Long, AbstractRole> roleMap = allRoles.stream()
            .collect(Collectors.toMap(AbstractRole::getId, r -> r));

        // 收集所有GROUP_ROLE类型角色的ID集合
        Set<Long> nestedGroupRoleIds = allRoles.stream()
            .filter(r -> r.getRoleType() != null && r.getRoleType() == RoleType.GROUP_ROLE.getValue())
            .map(AbstractRole::getId)
            .collect(Collectors.toSet());

        // 在内存中展开每个groupRoleId
        for (Long groupRoleId : groupRoleIds) {
            Set<Long> expanded = expandInMemory(groupRoleId, parentToChildren, roleMap, nestedGroupRoleIds, new HashSet<>());
            result.put(groupRoleId, expanded);
        }

        return result;
    }

    /**
     * 在内存中递归展开组角色
     * <p>
     * 处理extra.basicRoleIds和parent-child关系两种方式的角色展开
     * </p>
     *
     * @param roleId              当前角色ID
     * @param parentToChildren    父角色ID到子角色列表的映射
     * @param roleMap             角色ID到角色对象的映射
     * @param nestedGroupRoleIds  所有GROUP_ROLE类型角色的ID集合
     * @param visited             已访问的角色ID集合，防止循环引用
     * @return 展开后的基础角色ID集合
     */
    private Set<Long> expandInMemory(Long roleId, Map<Long, List<AbstractRole>> parentToChildren,
                                     Map<Long, AbstractRole> roleMap, Set<Long> nestedGroupRoleIds, Set<Long> visited) {
        if (roleId == null || visited.contains(roleId)) {
            return Set.of();
        }
        visited.add(roleId);

        Set<Long> result = new HashSet<>();
        AbstractRole role = roleMap.get(roleId);

        // 处理extra.basicRoleIds
        if (role != null) {
            Set<Long> basicIds = parseBasicRoleIds(role.getExtra());
            for (Long basicId : basicIds) {
                AbstractRole basicRole = roleMap.get(basicId);
                if (basicRole != null && basicRole.getRoleType() != null
                    && basicRole.getRoleType() == RoleType.GROUP_ROLE.getValue()) {
                    // 嵌套GROUP_ROLE，递归展开
                    result.addAll(expandInMemory(basicId, parentToChildren, roleMap, nestedGroupRoleIds, visited));
                } else if (basicRole != null) {
                    result.add(basicId);
                }
            }
        }

        // 处理子角色（parent_id = roleId）
        List<AbstractRole> children = parentToChildren.getOrDefault(roleId, List.of());
        for (AbstractRole child : children) {
            if (child.getRoleType() != null && child.getRoleType() == RoleType.GROUP_ROLE.getValue()) {
                // 子角色是GROUP_ROLE，递归展开
                result.addAll(expandInMemory(child.getId(), parentToChildren, roleMap, nestedGroupRoleIds, visited));
            } else {
                // 普通角色，直接添加
                result.add(child.getId());
            }
        }

        return result;
    }

    /**
     * 解析extra字段中的basicRoleIds
     * <p>
     * 从角色的extra JSON字段中提取basicRoleIds数组
     * </p>
     *
     * @param extra 角色extra字段JSON字符串
     * @return 基础角色ID集合
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
     * 失效单个用户的角色缓存
     * <p>
     * 同时清除L1 Caffeine缓存和L2 Redis缓存
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    @Override
    public void invalidateRoleCache(Long tenantId, Long userId) {
        String l2Key = ROLES_KEY_PREFIX + tenantId + ":" + userId;
        redisTemplate.delete(l2Key);
        permCacheDomainService.evictEffectiveRoles(tenantId, userId);
    }

    /**
     * 失效角色关联的所有用户缓存
     * <p>
     * 查询所有拥有该角色的用户，批量清除其缓存。
     * 使用批量删除减少Redis网络往返次数
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     */
    @Override
    public void invalidateRoleCacheByRole(Long tenantId, Long roleId) {
        List<Long> userIds = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                .and(UserRoleTableDef.USER_ROLE.TARGET_ID.eq(roleId))
                .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
        ).stream().map(UserRole::getAbstractUserId).distinct().collect(Collectors.toList());

        if (userIds.isEmpty()) {
            return;
        }

        // 批量构建L2 Redis keys
        String keyPrefix = ROLES_KEY_PREFIX + tenantId + ":";
        List<String> l2Keys = userIds.stream()
            .map(userId -> keyPrefix + userId)
            .collect(Collectors.toList());

        // 批量删除L2 Redis缓存（一次网络往返）
        redisTemplate.delete(l2Keys);

        // 批量失效L1 Caffeine缓存（本地操作，可循环处理）
        for (Long userId : userIds) {
            permCacheDomainService.evictEffectiveRoles(tenantId, userId);
        }
    }

}
