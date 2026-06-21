package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 抽象角色数据访问接口
 * <p>
 * 提供抽象角色表的基础CRUD操作和自定义查询方法。
 * 抽象角色包括基础角色和分组角色，是权限分配的核心实体。
 * 支持角色树递归查询、批量软删除等操作。
 * </p>
 */
public interface AbstractRoleMapper extends BaseMapper<AbstractRole> {

    /**
     * 使用PostgreSQL递归CTE查询所有后代角色
     * <p>
     * 从指定的分组角色ID开始，递归查询所有子孙角色。
     * 用于角色树查询和权限继承计算。
     * </p>
     *
     * @param groupRoleIds 起始分组角色ID集合
     * @param tenantId     租户ID
     * @return 所有后代角色列表（包含起始角色）
     */
    List<AbstractRole> selectRoleTreeByGroupIds(@Param("groupRoleIds") Set<Long> groupRoleIds, @Param("tenantId") Long tenantId);

    /**
     * 批量软删除抽象角色
     * <p>
     * 将指定角色的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的抽象角色ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 使用PostgreSQL递归CTE批量查询多个角色的所有后代角色ID
     * <p>
     * 从多个角色ID开始，递归查询所有子孙角色的ID。
     * 用于批量操作场景，提高查询效率。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  起始角色ID集合
     * @return 所有后代角色ID列表（不含起始角色）
     */
    List<Long> selectDescendantIdsBatch(@Param("tenantId") Long tenantId, @Param("roleIds") Set<Long> roleIds);
    List<Long> selectAncestorGroupRoleIds(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    /**
     * 批量递归 CTE 查询多个角色的所有祖先 GROUP_ROLE ID（T-PERM-018 P2：消除按角色循环 N+1）。
     * <p>
     * 与 {@link #selectAncestorGroupRoleIds} 语义一致，起点改为多角色集合，
     * 供 {@code invalidateRoleCacheByRoles} 一次查询多个角色关联的祖先组角色。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  起始角色ID集合
     * @return 所有祖先 GROUP_ROLE ID列表（不含起始角色）
     */
    List<Long> selectAncestorGroupRoleIdsBatch(@Param("tenantId") Long tenantId, @Param("roleIds") Set<Long> roleIds);

    /**
     * 根据ID和租户ID查询有效角色
     *
     * @param id       角色ID
     * @param tenantId 租户ID
     * @return 角色实体，不存在或已删除返回null
     */
    AbstractRole selectValidById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 根据角色ID集合批量查询有效角色
     *
     * @param tenantId 租户ID
     * @param ids      角色ID集合
     * @return 角色列表
     */
    List<AbstractRole> selectValidByIds(@Param("tenantId") Long tenantId, @Param("ids") Set<Long> ids);

    /**
     * 根据角色ID集合和可选过滤条件批量查询角色（用于filterRoleIds）
     *
     * @param tenantId           租户ID
     * @param ids                角色ID集合
     * @param externalId         角色外部ID，可选
     * @param roleType           角色类型值，可选
     * @return 角色列表
     */
    List<AbstractRole> selectFilteredByIds(@Param("tenantId") Long tenantId,
                                            @Param("ids") Set<Long> ids,
                                            @Param("externalId") String externalId,
                                            @Param("roleType") Integer roleType);

    /**
     * 查询角色树（所有有效且启用的角色）
     *
     * @param tenantId 租户ID
     * @return 角色列表
     */
    List<AbstractRole> selectEnabledRoleTree(@Param("tenantId") Long tenantId);

    /**
     * 分页查询角色列表（带过滤条件）
     *
     * @param tenantId      租户ID
     * @param roleType      角色类型值，可选
     * @param keyword       搜索关键字，可选（LIKE匹配name或external_id）
     * @param matchNone     是否匹配空结果（用于域过滤不匹配时）
     * @param offset        偏移量
     * @param limit         每页数量
     * @return 角色列表
     */
    List<AbstractRole> selectRoleListPaged(@Param("tenantId") Long tenantId,
                                            @Param("roleTypes") Set<Integer> roleTypes,
                                            @Param("keyword") String keyword,
                                            @Param("matchNone") boolean matchNone,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /**
     * 统计角色数量（带过滤条件）
     *
     * @param tenantId      租户ID
     * @param roleType      角色类型值，可选
     * @param keyword       搜索关键字，可选
     * @param matchNone     是否匹配空结果
     * @return 角色总数
     */
    long selectRoleListCount(@Param("tenantId") Long tenantId,
                              @Param("roleTypes") Set<Integer> roleTypes,
                              @Param("keyword") String keyword,
                              @Param("matchNone") boolean matchNone);

    /**
     * 根据角色ID集合查询有效且启用的角色ID列表
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 有效且启用的角色ID列表
     */
    List<Long> selectEnabledIdsByIds(@Param("tenantId") Long tenantId,
                                     @Param("roleIds") Set<Long> roleIds);

    /**
     * 根据角色类型和外部ID批量查询有效角色
     *
     * @param tenantId    租户ID
     * @param roleType    角色类型值
     * @param externalIds 外部ID集合
     * @return 角色列表
     */
    List<AbstractRole> selectByTypeAndExternalIds(@Param("tenantId") Long tenantId,
                                                   @Param("roleType") Integer roleType,
                                                   @Param("externalIds") Set<String> externalIds);

    /**
     * 根据角色类型和外部ID查询有效角色（单条）
     *
     * @param tenantId    租户ID
     * @param roleType    角色类型值
     * @param externalId  外部ID
     * @return 角色实体
     */
    AbstractRole selectByTypeAndExternalId(@Param("tenantId") Long tenantId,
                                           @Param("roleType") Integer roleType,
                                           @Param("externalId") String externalId);
}
