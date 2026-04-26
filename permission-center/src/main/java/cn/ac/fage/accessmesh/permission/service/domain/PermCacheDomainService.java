package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;

import java.util.Optional;
import java.util.Set;

public interface PermCacheDomainService {

    // ---- 用户有效角色缓存 ----
    Optional<Set<Long>> getEffectiveRoles(Long tenantId, Long userId);
    void setEffectiveRoles(Long tenantId, Long userId, Set<Long> roleIds);
    void evictEffectiveRoles(Long tenantId, Long userId);

    // ---- 角色权限快照缓存 ----
    Optional<RolePermSnapshot> getRolePermSnapshot(Long tenantId, Long roleId);
    void setRolePermSnapshot(Long tenantId, Long roleId, RolePermSnapshot snapshot);
    void evictRolePermSnapshot(Long tenantId, Long roleId);

    // ---- 权限版本缓存 ----
    Optional<Long> getPermVersion(Long tenantId, Long roleId);
    void setPermVersion(Long tenantId, Long roleId, long version);

    // ---- 接口权限快照 ----
    Optional<InterfaceSnapshot> getInterfaceSnapshot(Long tenantId, String serviceCode);
    void setInterfaceSnapshot(Long tenantId, String serviceCode, InterfaceSnapshot snapshot);
    void evictInterfaceSnapshot(Long tenantId, String serviceCode);
}
