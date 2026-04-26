package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.UserRole;
import cn.ac.fage.accessmesh.permission.enums.RoleType;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef.USER_ROLE;

@Service
public class UserRoleDomainServiceImpl implements UserRoleDomainService {

    private static final Logger log = LoggerFactory.getLogger(UserRoleDomainServiceImpl.class);
    private static final String ROLES_KEY_PREFIX = "perm:user:effective-roles:";
    private static final long CACHE_TTL_MINUTES = 30;

    private final UserRoleMapper userRoleMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final PermCacheDomainService permCacheDomainService;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserRoleDomainServiceImpl(UserRoleMapper userRoleMapper,
                                     AbstractRoleMapper abstractRoleMapper,
                                     PermCacheDomainService permCacheDomainService,
                                     RedisTemplate<String, Object> redisTemplate) {
        this.userRoleMapper = userRoleMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.permCacheDomainService = permCacheDomainService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Set<Long> resolveEffectiveRoles(Long tenantId, Long userId, Long bizDomainId) {
        // L1 cache
        Optional<Set<Long>> cached = permCacheDomainService.getEffectiveRoles(tenantId, userId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // L2 Redis cache
        String l2Key = ROLES_KEY_PREFIX + tenantId + ":" + userId;
        Object l2Value = redisTemplate.opsForValue().get(l2Key);
        if (l2Value instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<Long> roles = (Set<Long>) l2Value;
            permCacheDomainService.setEffectiveRoles(tenantId, userId, roles);
            return roles;
        }

        // DB resolution
        Set<Long> effectiveRoles = resolveFromDb(tenantId, userId, bizDomainId);

        // Write to L2 and L1
        redisTemplate.opsForValue().set(l2Key, effectiveRoles, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        permCacheDomainService.setEffectiveRoles(tenantId, userId, effectiveRoles);
        return effectiveRoles;
    }

    @Override
    public void invalidateRoleCache(Long tenantId, Long userId) {
        permCacheDomainService.evictEffectiveRoles(tenantId, userId);
        String l2Key = ROLES_KEY_PREFIX + tenantId + ":" + userId;
        redisTemplate.delete(l2Key);
    }

    @Override
    public void invalidateRoleCacheByRole(Long tenantId, Long roleId) {
        List<Long> userIds = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TARGET_ID.eq(roleId))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
        ).stream().map(UserRole::getAbstractUserId).distinct().collect(Collectors.toList());

        for (Long userId : userIds) {
            invalidateRoleCache(tenantId, userId);
        }
    }

    private Set<Long> resolveFromDb(Long tenantId, Long userId, Long bizDomainId) {
        List<UserRole> userRoles = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.ABSTRACT_USER_ID.eq(userId))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
                .and(USER_ROLE.VALID_FROM.le(LocalDateTime.now()).or(USER_ROLE.VALID_FROM.isNull()))
                .and(USER_ROLE.VALID_TO.ge(LocalDateTime.now()).or(USER_ROLE.VALID_TO.isNull()))
        );

        Set<Long> roleIds = new HashSet<>();
        for (UserRole ur : userRoles) {
            if ("GROUP_ROLE".equals(ur.getTargetType())) {
                resolveGroupRole(tenantId, ur.getTargetId(), roleIds);
            } else {
                roleIds.add(ur.getTargetId());
            }
        }

        // Filter by status=1 (enabled)
        if (!roleIds.isEmpty()) {
            QueryWrapper qw = QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.in(roleIds))
                .and(ABSTRACT_ROLE.STATUS.eq(1))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0));
            if (bizDomainId != null) {
                qw.and(ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(bizDomainId).or(ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull()));
            }
            List<AbstractRole> enabledRoles = abstractRoleMapper.selectListByQuery(qw);
            roleIds = enabledRoles.stream().map(AbstractRole::getId).collect(Collectors.toSet());
        }

        return roleIds;
    }

    private void resolveGroupRole(Long tenantId, Long groupId, Set<Long> roleIds) {
        // Recursive: resolve children
        List<AbstractRole> children = abstractRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.PARENT_ID.eq(groupId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        for (AbstractRole child : children) {
            if (child.getRoleType() != null && child.getRoleType() == RoleType.GROUP_ROLE.getValue()) {
                resolveGroupRole(tenantId, child.getId(), roleIds);
            } else {
                roleIds.add(child.getId());
            }
        }
    }
}
