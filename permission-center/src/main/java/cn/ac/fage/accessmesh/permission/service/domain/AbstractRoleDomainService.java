package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;

import java.util.List;
import java.util.Set;

/**
 * 抽象角色领域服务接口
 * <p>
 * 提供抽象角色（AbstractRole）的CRUD操作和树形结构查询功能。
 * 抽象角色是权限系统的核心概念，用于组织用户并配置权限。
 * 支持多种角色类型：组角色（GROUP_ROLE）、组织角色（ORG）、业务角色等。
 * 组角色和组织角色删除时会级联删除其所有子孙角色。
 * </p>
 */
public interface AbstractRoleDomainService {

    /**
     * 创建角色
     * <p>
     * 在指定租户和域下创建新角色。
     * </p>
     *
     * @param tenantId   租户ID
     * @param bizDomainId 业务域ID
     * @param parentId   父角色ID
     * @param roleType   角色类型值（参考RoleType枚举）
     * @param externalId 外部标识
     * @param name       角色名称
     * @param sortOrder  排序顺序
     * @param extra      扩展属性JSON
     * @return 创建的角色ID
     */
    Long createRole(Long tenantId, Long bizDomainId, Long parentId, Integer roleType,
                    String externalId, String name, Integer sortOrder, String extra);

    /**
     * 删除角色及其子孙角色
     * <p>
     * 软删除指定角色。如果角色类型为组角色或组织角色，
     * 会级联删除其所有子孙角色。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     */
    void deleteRole(Long tenantId, Long roleId);

    /**
     * 查询子角色列表
     *
     * @param tenantId 租户ID
     * @param parentId 父角色ID
     * @return 子角色列表
     */
    List<AbstractRole> listChildren(Long tenantId, Long parentId);

    /**
     * 解析子孙角色ID列表
     * <p>
     * 获取指定角色的所有子孙角色ID。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 子孙角色ID列表
     */
    List<Long> resolveDescendantIds(Long tenantId, Long roleId);

    /**
     * 根据ID查询有效角色
     * <p>
     * 查询未删除的角色实体，包含租户校验。
     * 如果角色不存在、已删除或不属于租户，返回null。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 角色实体，不存在或已删除返回null
     */
    AbstractRole selectValidById(Long tenantId, Long roleId);

    /**
     * 批量查询有效角色
     * <p>
     * 批量查询多个角色实体，包含租户和删除标志校验。
     * 返回存在、未删除且属于租户的角色列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 角色列表
     */
    List<AbstractRole> selectValidByIds(Long tenantId, Set<Long> roleIds);

    /**
     * 批量软删除角色
     * <p>
     * 批量设置角色的deleteFlag为ID值，记录删除时间。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     */
    void softDeleteBatch(Long tenantId, Set<Long> roleIds);

    /**
     * 批量解析子孙角色ID列表
     * <p>
     * 批量获取多个角色的所有子孙角色ID。
     * 使用CTE递归查询高效获取。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  起始角色ID集合
     * @return 所有子孙角色ID列表（不包含起始角色）
     */
    List<Long> resolveDescendantIdsBatch(Long tenantId, Set<Long> roleIds);
}