package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * 权限缓存领域服务实现
 * <p>
 * 作为语义门面，委托给统一 CacheService 处理实际缓存操作。
 * 使用 PermCacheCatalog 定义缓存配置。
 * </p>
 */
@Service
public class PermCacheDomainServiceImpl implements PermCacheDomainService {

    private final CacheService cacheService;

    /**
     * 构造函数注入依赖
     *
     * @param cacheService 统一缓存服务
     */
    public PermCacheDomainServiceImpl(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    // ==================== 有效角色缓存 ====================

    /**
     * 获取用户的有效角色集合
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 有效角色ID集合
     */
    @Override
    public Optional<Set<Long>> getEffectiveRoles(Long tenantId, Long userId) {
        Set<Long> roles = cacheService.get(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId);
        return Optional.ofNullable(roles);
    }

    /**
     * 设置用户的有效角色集合
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param roleIds  有效角色ID集合
     */
    @Override
    public void setEffectiveRoles(Long tenantId, Long userId, Set<Long> roleIds) {
        if (roleIds != null) {
            cacheService.put(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId, roleIds);
        }
    }

    /**
     * 失效用户的有效角色缓存
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    @Override
    public void evictEffectiveRoles(Long tenantId, Long userId) {
        cacheService.evict(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId);
    }

    // ==================== 角色权限快照缓存 ====================

    /**
     * 获取角色权限快照
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 角色权限快照
     */
    @Override
    public Optional<RolePermSnapshot> getRolePermSnapshot(Long tenantId, Long roleId) {
        RolePermSnapshot snapshot = cacheService.get(PermCacheCatalog.ROLE_PERM_SNAPSHOT, tenantId, roleId);
        return Optional.ofNullable(snapshot);
    }

    /**
     * 设置角色权限快照
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @param snapshot 权限快照
     */
    @Override
    public void setRolePermSnapshot(Long tenantId, Long roleId, RolePermSnapshot snapshot) {
        if (snapshot != null) {
            cacheService.put(PermCacheCatalog.ROLE_PERM_SNAPSHOT, tenantId, roleId, snapshot);
        }
    }

    /**
     * 失效角色权限快照缓存
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     */
    @Override
    public void evictRolePermSnapshot(Long tenantId, Long roleId) {
        cacheService.evict(PermCacheCatalog.ROLE_PERM_SNAPSHOT, tenantId, roleId);
    }

    // ==================== 权限版本缓存 ====================

    /**
     * 获取权限版本号
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 权限版本号
     */
    @Override
    public Optional<Long> getPermVersion(Long tenantId, Long roleId) {
        Long version = cacheService.get(PermCacheCatalog.PERMISSION_VERSION, tenantId, roleId);
        return Optional.ofNullable(version);
    }

    /**
     * 设置权限版本号
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @param version  版本号
     */
    @Override
    public void setPermVersion(Long tenantId, Long roleId, long version) {
        cacheService.put(PermCacheCatalog.PERMISSION_VERSION, tenantId, roleId, version);
    }

    // ==================== 接口快照缓存 ====================

    /**
     * 获取接口权限快照
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @return 接口快照
     */
    @Override
    public Optional<InterfaceSnapshot> getInterfaceSnapshot(Long tenantId, String serviceCode) {
        InterfaceSnapshot snapshot = cacheService.get(PermCacheCatalog.INTERFACE_SNAPSHOT, tenantId, serviceCode);
        return Optional.ofNullable(snapshot);
    }

    /**
     * 设置接口权限快照
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @param snapshot   接口快照
     */
    @Override
    public void setInterfaceSnapshot(Long tenantId, String serviceCode, InterfaceSnapshot snapshot) {
        if (snapshot != null) {
            cacheService.put(PermCacheCatalog.INTERFACE_SNAPSHOT, tenantId, serviceCode, snapshot);
        }
    }

    /**
     * 失效接口权限快照缓存
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     */
    @Override
    public void evictInterfaceSnapshot(Long tenantId, String serviceCode) {
        cacheService.evict(PermCacheCatalog.INTERFACE_SNAPSHOT, tenantId, serviceCode);
    }
}