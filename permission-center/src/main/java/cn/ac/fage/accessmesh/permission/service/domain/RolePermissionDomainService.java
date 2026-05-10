package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;

import java.time.LocalDateTime;
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
     * 获取角色的权限快照
     * <p>
     * 查询角色的所有有效权限配置，返回包含版本号的快照对象
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 角色权限快照
     */
    RolePermSnapshot getRolePermissions(Long tenantId, Long roleId);

    /**
     * 批量授予角色权限
     * <p>
     * 批量插入权限记录，事务提交后自动递增版本号
     * </p>
     *
     * @param tenantId     租户ID
     * @param roleId       角色ID
     * @param entries      待授予的权限条目列表
     * @param changeSource 变更来源
     */
    void grantPermissions(Long tenantId, Long roleId, List<RolePermSnapshot.RolePermEntry> entries, String changeSource);

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
     * 撤销单个权限并级联删除子权限
     * <p>
     * 软删除指定权限，同时删除所有依赖该权限的子权限。
     * 不自动递增版本号，需调用方自行处理版本递增。
     * </p>
     *
     * @param tenantId   租户ID
     * @param roleId     角色ID
     * @param permissionId 权限ID
     * @param deletedAt  删除时间
     */
    void revokePermissionWithCascade(Long tenantId, Long roleId, Long permissionId, LocalDateTime deletedAt);

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