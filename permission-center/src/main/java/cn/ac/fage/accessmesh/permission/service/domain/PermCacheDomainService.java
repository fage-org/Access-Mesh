package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;

import java.util.Optional;
import java.util.Set;

/**
 * 权限缓存领域服务接口
 * <p>
 * 提供权限相关的缓存管理功能，支持双层缓存架构（L1 Caffeine + L2 Redis）
 * </p>
 */
public interface PermCacheDomainService {

    // ---- 用户有效角色缓存 ----

    /**
     * 获取用户的有效角色集合
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 有效角色ID集合，不存在时返回Optional.empty()
     */
    Optional<Set<Long>> getEffectiveRoles(Long tenantId, Long userId);

    /**
     * 设置用户的有效角色集合
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param roleIds  有效角色ID集合
     */
    void setEffectiveRoles(Long tenantId, Long userId, Set<Long> roleIds);

    /**
     * 失效用户的有效角色缓存
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    void evictEffectiveRoles(Long tenantId, Long userId);

    // ---- 角色权限快照缓存 ----

    /**
     * 获取角色权限快照
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 角色权限快照，不存在时返回Optional.empty()
     */
    Optional<RolePermSnapshot> getRolePermSnapshot(Long tenantId, Long roleId);

    /**
     * 设置角色权限快照
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @param snapshot 权限快照
     */
    void setRolePermSnapshot(Long tenantId, Long roleId, RolePermSnapshot snapshot);

    /**
     * 失效角色权限快照缓存
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     */
    void evictRolePermSnapshot(Long tenantId, Long roleId);

    // ---- 权限版本缓存 ----

    /**
     * 获取权限版本号
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 权限版本号，不存在时返回Optional.empty()
     */
    Optional<Long> getPermVersion(Long tenantId, Long roleId);

    /**
     * 设置权限版本号
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @param version  版本号
     */
    void setPermVersion(Long tenantId, Long roleId, long version);

    // ---- 接口权限快照 ----

    /**
     * 获取接口权限快照
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @return 接口快照，不存在时返回Optional.empty()
     */
    Optional<InterfaceSnapshot> getInterfaceSnapshot(Long tenantId, String serviceCode);

    /**
     * 设置接口权限快照
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @param snapshot   接口快照
     */
    void setInterfaceSnapshot(Long tenantId, String serviceCode, InterfaceSnapshot snapshot);

    /**
     * 失效接口权限快照缓存
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     */
    void evictInterfaceSnapshot(Long tenantId, String serviceCode);
}
