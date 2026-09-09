package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;

import java.util.Collection;
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

    /**
     * 批量判定哪些 user_type 值下存在有效用户行（T-PERM-056 user_type 删除守卫，
     * 对齐 ResourceEntityDomainService#findTypesWithValidRows 先例）。
     *
     * @param tenantId  租户ID
     * @param userTypes user_type 内部类型值集合
     * @return 存在有效用户行的类型值集合
     */
    Set<Integer> findUserTypesWithValidRows(Long tenantId, Collection<Integer> userTypes);

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
     * 批量判定哪些 role_type 值下存在有效角色行（T-PERM-056 role_type 删除守卫，
     * 对齐 ResourceEntityDomainService#findTypesWithValidRows 先例）。
     *
     * @param tenantId  租户ID
     * @param roleTypes role_type 内部类型值集合
     * @return 存在有效角色行的类型值集合
     */
    Set<Integer> findRoleTypesWithValidRows(Long tenantId, Collection<Integer> roleTypes);

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
     * 固定 ≤4 SQL + 1 次 evictBatch，避免 afterCommit 阶段 O(R) SQL（资源批量删除等场景）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     */
    void invalidateRoleCacheByRoles(Long tenantId, Set<Long> roleIds);

    /**
     * 反查有效角色包含任一给定角色的用户 ID（直接绑定 + 经组角色展开）。
     * <p>
     * 按**当前**角色树解析——移动/删除角色场景须在树变更**前**调用，
     * 提交后旧树关系不可再发现（受影响用户在事务内
     * 预计算并 markUsers，afterCommit 按显式用户 ID 失效，不依赖提交后反查）。
     * 与 {@link #invalidateRoleCacheByRoles} 共用同一查询口径。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 受影响用户 ID 集合
     */
    Set<Long> findUserIdsByEffectiveRoles(Long tenantId, Set<Long> roleIds);
}
