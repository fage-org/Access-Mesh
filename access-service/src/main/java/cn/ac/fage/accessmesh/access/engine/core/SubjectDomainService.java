package cn.ac.fage.accessmesh.access.engine.core;

import cn.ac.fage.accessmesh.access.role.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.user.entity.AbstractUser;

import java.time.LocalDateTime;
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

    // ===== user_role 原始行层（Q-009 收敛读/写，T-ACCESS-046）=====
    // 与上方 effectiveRoles 缓存层同服务两档一致性：缓存档（EFFECTIVE_ROLES，afterCommit 失效）
    // 服务运行时判定；原始行档无缓存直读直写，服务管理面写路径的「读点在写前」去重/定位/展示
    // 与投影写路径（同事务可见）。写方法不声明事务（REQUIRED 跟随调用方 AppService）。

    /**
     * 批量查用户的有效角色关系原始行（无缓存直读；管理面删除用户级联盘点）。
     *
     * @param tenantId 租户ID
     * @param userIds  抽象用户ID集合
     * @return 关系原始行列表
     */
    java.util.List<cn.ac.fage.accessmesh.access.role.entity.UserRole> selectValidUserRolesByUserIds(
        Long tenantId, Set<Long> userIds);

    /**
     * 按用户集合 + 目标集合查有效关系原始行（授予去重；无缓存直读）。
     */
    java.util.List<cn.ac.fage.accessmesh.access.role.entity.UserRole> selectValidUserRolesByUserIdsAndTargetIds(
        Long tenantId, Set<Long> userIds, Set<Long> targetIds, String targetType);

    /**
     * 按用户集合 + 单目标查有效关系原始行（批量授予去重的单目标变体，独立 SQL；无缓存直读）。
     */
    java.util.List<cn.ac.fage.accessmesh.access.role.entity.UserRole> selectValidUserRolesByUserIdsAndTargetId(
        Long tenantId, Set<Long> userIds, Long targetId, String targetType);

    /**
     * 按用户集合 + 目标类型 + 目标集合查有效关系原始行（撤销按三元组定位；无缓存直读）。
     */
    java.util.List<cn.ac.fage.accessmesh.access.role.entity.UserRole> selectValidUserRolesByUserIdsTypeAndTargetIds(
        Long tenantId, Set<Long> userIds, String targetType, Set<Long> targetIds);

    /**
     * 单用户有效期窗口内的有效关系原始行（用户角色展示；无缓存直读）。
     */
    java.util.List<cn.ac.fage.accessmesh.access.role.entity.UserRole> selectValidUserRolesByUserIdWithValidity(
        Long tenantId, Long userId, java.time.LocalDateTime now);

    /**
     * 用户角色关系投影读（user_role JOIN abstract_role 双投影形态；无门禁版，menu 登录链路
     * 与 role 查询服务共用；无缓存直读，失败降级由调用方处理）。
     *
     * @param tenantId       租户ID
     * @param abstractUserId 抽象用户ID
     * @param now            有效期窗口判定基准
     * @return 关系投影列表（角色投影缺失时保留关系行、角色字段为 null）
     */
    java.util.List<cn.ac.fage.accessmesh.access.role.dto.projection.UserRoleProjection> selectUserRoleProjections(
        Long tenantId, Long abstractUserId, java.time.LocalDateTime now);

    /**
     * 批量插入关系行（授予路径；不声明事务，REQUIRED 跟随调用方）。
     *
     * @param userRoles 待插入关系行
     */
    void insertUserRoles(java.util.List<cn.ac.fage.accessmesh.access.role.entity.UserRole> userRoles);

    /**
     * 按关系行 ID 批量软删（撤销路径）。
     */
    void softDeleteUserRolesBatch(Long tenantId, java.util.List<Long> ids, java.time.LocalDateTime deletedAt);

    /**
     * 按抽象用户集合批量软删关系行（投影轨删除用户级联）。
     */
    void softDeleteUserRolesByAbstractUserIds(Long tenantId, Set<Long> userIds, java.time.LocalDateTime deletedAt);

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
     * 解析用户的原始持有候选（单用户便捷版，T-PERM-075 写守卫专用）。
     * <p>
     * 语义同 {@link #batchResolveRawHoldings}——未过期原始行闭包（含未来窗口、
     * 含禁用持有与禁用组子树，保留有效期窗口），不缓存、不做运行时过滤。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 原始持有窗口集合
     */
    Set<RawHolding> resolveRawHoldings(Long tenantId, Long userId);

    /**
     * 批量解析多个用户的有效角色
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     * @return 用户ID到角色ID集合的映射
     */
    Map<Long, Set<Long>> batchResolveEffectiveRoles(Long tenantId, Set<Long> userIds);

    /**
     * 批量解析多个用户的原始持有候选（T-PERM-075 写守卫专用）。
     * <p>
     * 与 {@link #batchResolveEffectiveRoles} 的差别——本方法刻意**不做**运行时过滤，
     * 返回「未过期原始行闭包」，且**保留有效期窗口**供互斥重叠判定：
     * </p>
     * <ul>
     *   <li>有效期：仅 valid_to 未过期（含尚未生效的未来 valid_from 窗口）；
     *       已过期行永不生效（改期通道在改写时重查），不计入；</li>
     *   <li>组角色展开：含禁用组与禁用子树（不剪枝）——禁用是可逆状态，
     *       启用后持有即参与判定（U002-2 绑定写时堵死）；经组展开的间接持有
     *       继承该 GROUP_ROLE 绑定行的窗口（用户侧唯一时间约束，保守方向）；</li>
     *   <li>不做角色启用过滤、不做用户禁用置空、不做互斥过滤——
     *       写时冲突守卫必须看原始候选，不能先过滤再断言无冲突。</li>
     * </ul>
     * <p>
     * 不走 EFFECTIVE_ROLES 缓存：写路径要求 DB 新鲜读，且「未过期」集合随时间单调演进，
     * 缓存会引入与写入并发的陈旧窗口。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     * @return 用户ID到原始持有窗口集合的映射（roleId + validFrom/validTo，null=无限端）
     */
    Map<Long, Set<RawHolding>> batchResolveRawHoldings(Long tenantId, Set<Long> userIds);

    /**
     * 原始持有窗口（T-PERM-075）：角色 id + 有效期窗口（null=无限端，闭区间）。
     * <p>
     * 互斥写守卫按「窗口区间交」判定冲突——两个互斥角色的持有窗口真正不相交时放行
     * （U002-1 拍板：写时阻止可确定的重叠，不是按角色集合粗暴互斥）。
     * 经组展开的间接持有窗口继承 GROUP_ROLE 绑定行窗口。
     * </p>
     */
    record RawHolding(Long roleId, LocalDateTime validFrom, LocalDateTime validTo) {}

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
