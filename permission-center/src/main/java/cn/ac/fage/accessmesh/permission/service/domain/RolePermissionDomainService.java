package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;

import java.util.List;

/**
 * 角色权限领域服务接口
 * <p>
 * 管理角色的权限配置，包括查询、授予、撤销等操作。
 * 权限变更后需配合版本递增确保缓存一致性。
 * </p>
 */
public interface RolePermissionDomainService {

    /**
     * 批量撤销角色权限
     * <p>
     * 批量软删除权限，同时级联删除依赖该权限的子权限。
     * 事务提交后自动递增版本号。
     * </p>
     *
     * @param tenantId     租户ID
     * @param roleId       角色ID
     * @param permissionIds 待撤销的权限ID列表
     */
    void revokePermissions(Long tenantId, Long roleId, List<Long> permissionIds);

    /**
     * 根据ID查询有效的权限记录
     * <p>
     * 查询未删除且属于指定租户/角色的权限记录。
     * 不存在或已删除时返回null。
     * </p>
     *
     * @param tenantId     租户ID
     * @param roleId       角色ID（可选过滤条件）
     * @param permissionId 权限ID
     * @return 权限实体，不存在时返回null
     */
    RoleResourcePermission selectValidById(Long tenantId, Long roleId, Long permissionId);
}