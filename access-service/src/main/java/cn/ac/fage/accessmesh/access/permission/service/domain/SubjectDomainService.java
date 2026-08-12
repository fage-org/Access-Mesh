package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主体领域服务接口
 * <p>
 * 合并 AbstractUserDomainService、AbstractRoleDomainService、UserRoleDomainService 三个旧接口。
 * 提供用户、角色、用户角色关系的基础数据访问和解析功能。
 * </p>
 */
public interface SubjectDomainService {

    // ===== AbstractUser =====

    /**
     * 根据ID查询有效用户
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户实体，不存在或已删除返回null
     */
    AbstractUser selectValidUserById(Long tenantId, Long userId);

    // ===== AbstractRole =====

    /**
     * 创建角色
     *
     * @param tenantId   租户ID
     * @param parentId   父角色ID
     * @param roleType   角色类型值
     * @param externalId 外部标识
     * @param name       角色名称
     * @param sortOrder  排序顺序
     * @param extra      扩展属性JSON
     * @return 创建的角色ID
     */
    Long createRole(Long tenantId, Long parentId, Integer roleType,
                    String externalId, String name, Integer sortOrder, String extra);

    /**
     * 根据ID查询有效角色
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 角色实体，不存在或已删除返回null
     */
    AbstractRole selectValidRoleById(Long tenantId, Long roleId);

    /**
     * 批量查询有效角色
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 角色列表
     */
    List<AbstractRole> selectValidRolesByIds(Long tenantId, Set<Long> roleIds);

    /**
     * 批量软删除角色
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     */
    void softDeleteRoleBatch(Long tenantId, Set<Long> roleIds);

    /**
     * 批量解析子孙角色ID列表
     *
     * @param tenantId 租户ID
     * @param roleIds  起始角色ID集合
     * @return 所有子孙角色ID列表（不包含起始角色）
     */
    List<Long> resolveDescendantRoleIdsBatch(Long tenantId, Set<Long> roleIds);

    // ===== UserRole =====

    /**
     * 解析用户的有效角色
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户的有效角色ID集合
     */
    Set<Long> resolveEffectiveRoles(Long tenantId, Long userId);

    /**
     * 批量解析多个用户的有效角色
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     * @return 用户ID到角色ID集合的映射
     */
    Map<Long, Set<Long>> batchResolveEffectiveRoles(Long tenantId, Set<Long> userIds);

    /**
     * 批量失效多个用户的角色缓存
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     */
    void invalidateRoleCacheBatch(Long tenantId, Set<Long> userIds);

    /**
     * 失效角色关联的所有用户缓存
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     */
    void invalidateRoleCacheByRole(Long tenantId, Long roleId);

    /**
     * 批量失效多个角色关联的所有用户缓存（T-PERM-018 P2：消除按角色循环 N+1）。
     * <p>
     * 与 {@link #invalidateRoleCacheByRole} 语义一致，但一次查询多个角色的直接用户与祖先组角色用户，
     * 固定 ≤3 SQL + 1 次 evictBatch，避免 afterCommit 阶段 O(R) SQL（资源批量删除等场景）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     */
    void invalidateRoleCacheByRoles(Long tenantId, Set<Long> roleIds);
}
