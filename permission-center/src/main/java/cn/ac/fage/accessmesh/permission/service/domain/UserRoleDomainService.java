package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.Map;
import java.util.Set;

/**
 * 用户角色领域服务接口
 * <p>
 * 负责用户角色的解析与缓存管理，支持组角色展开、有效期过滤等功能
 * </p>
 */
public interface UserRoleDomainService {

    /**
     * 解析用户的有效角色
     * <p>
     * 根据用户ID解析其有效角色集合，包括直接分配的角色和组角色展开后的基础角色。
     * 支持双层缓存（L1本地缓存 + L2 Redis缓存）
     * </p>
     *
     * @param tenantId    租户ID
     * @param userId      用户ID
     * @return 用户的有效角色ID集合
     */
    Set<Long> resolveEffectiveRoles(Long tenantId, Long userId);

    /**
     * 批量解析多个用户的有效角色
     * <p>
     * 使用真正的批量查询避免N+1问题，返回用户ID到角色ID集合的映射
     * </p>
     *
     * @param tenantId    租户ID
     * @param userIds     用户ID集合
     * @return 用户ID到角色ID集合的映射
     */
    Map<Long, Set<Long>> batchResolveEffectiveRoles(Long tenantId, Set<Long> userIds);

    /**
     * 失效单个用户的角色缓存
     * <p>
     * 当用户角色发生变化时调用，清除L1和L2缓存
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    void invalidateRoleCache(Long tenantId, Long userId);

    /**
     * 批量失效多个用户的角色缓存
     * <p>
     * 当批量分配/撤销角色时调用，避免循环触发单条缓存失效
     * </p>
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     */
    void invalidateRoleCacheBatch(Long tenantId, Set<Long> userIds);

    /**
     * 失效角色关联的所有用户缓存
     * <p>
     * 当角色配置发生变化时调用，批量清除所有拥有该角色的用户的缓存
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     */
    void invalidateRoleCacheByRole(Long tenantId, Long roleId);
}
