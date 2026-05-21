package cn.ac.fage.accessmesh.permquery.domain.service;

import cn.ac.fage.accessmesh.permquery.infrastructure.adapter.PermCacheAdapter;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 角色解析领域服务
 * <p>
 * 负责解析用户的有效角色集合，包括直接分配的角色和组角色展开。
 * 通过 UserRoleDomainService 获取角色数据，通过 PermCacheAdapter 管理缓存。
 * </p>
 */
@Service
public class RoleResolutionService {

    private final UserRoleDomainService userRoleDomainService;
    private final PermCacheAdapter permCacheAdapter;

    public RoleResolutionService(UserRoleDomainService userRoleDomainService,
                                  PermCacheAdapter permCacheAdapter) {
        this.userRoleDomainService = userRoleDomainService;
        this.permCacheAdapter = permCacheAdapter;
    }

    /**
     * 解析用户的有效角色
     * <p>
     * 返回用户的所有有效角色ID集合。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 有效角色ID集合
     */
    public Set<Long> resolve(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return Collections.emptySet();
        }
        return userRoleDomainService.resolveEffectiveRoles(tenantId, userId);
    }

    /**
     * 批量解析多个用户的有效角色
     * <p>
     * 使用真正的批量查询避免N+1问题。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     * @return 用户ID到角色ID集合的映射
     */
    public Map<Long, Set<Long>> resolveBatch(Long tenantId, Set<Long> userIds) {
        if (tenantId == null || userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRoleDomainService.batchResolveEffectiveRoles(tenantId, userIds);
    }

    /**
     * 失效用户的角色缓存
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    public void invalidateCache(Long tenantId, Long userId) {
        if (tenantId != null && userId != null) {
            permCacheAdapter.evictEffectiveRoles(tenantId, userId);
        }
    }

    /**
     * 批量失效用户的角色缓存
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     */
    public void invalidateCacheBatch(Long tenantId, Set<Long> userIds) {
        if (tenantId != null && userIds != null && !userIds.isEmpty()) {
            permCacheAdapter.evictEffectiveRolesBatch(tenantId, userIds);
        }
    }

    /**
     * 失效角色关联的所有用户缓存
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     */
    public void invalidateCacheByRole(Long tenantId, Long roleId) {
        if (tenantId != null && roleId != null) {
            userRoleDomainService.invalidateRoleCacheByRole(tenantId, roleId);
        }
    }
}