package cn.ac.fage.accessmesh.permquery.domain.repository;

import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.GrantedPermission;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限条目仓储接口
 * <p>
 * 负责 RoleResourcePermission 实体的查询操作。
 * 提供类型级和实例级权限的查询方法，支持位掩码过滤。
 * </p>
 */
public interface PermissionEntryRepository {

    /**
     * 查询类型级权限（scopeAll=true）
     * <p>
     * 使用位掩码批量查询多个资源类型的权限，避免多次SQL调用。
     * </p>
     *
     * @param tenantId  租户ID
     * @param roleIds   角色ID集合
     * @param bitMasks  resourceType → bitMask 映射
     * @return 权限条目列表
     */
    List<GrantedPermission> queryScopeAllPermissions(
        Long tenantId,
        Set<Long> roleIds,
        Map<Integer, Long> bitMasks
    );

    /**
     * 查询实例级权限
     * <p>
     * 使用位掩码批量查询多个资源类型的权限，避免多次SQL调用。
     * </p>
     *
     * @param tenantId          租户ID
     * @param roleIds           角色ID集合
     * @param resourceEntityIds 资源实体ID集合
     * @param bitMasks          resourceType → bitMask 映射
     * @return 权限条目列表
     */
    List<GrantedPermission> queryInstancePermissions(
        Long tenantId,
        Set<Long> roleIds,
        Set<Long> resourceEntityIds,
        Map<Integer, Long> bitMasks
    );

    /**
     * 查询角色的所有权限（不限资源类型）
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 权限条目列表
     */
    List<GrantedPermission> queryPermissionsByRole(Long tenantId, Long roleId);

    /**
     * 批量查询多个角色的所有权限
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 权限条目列表
     */
    List<GrantedPermission> queryPermissionsByRoles(Long tenantId, Set<Long> roleIds);

    /**
     * 查询指定资源实体的权限
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 权限条目列表
     */
    List<GrantedPermission> queryPermissionsByResourceEntity(Long tenantId, Long resourceEntityId);
}