package cn.ac.fage.accessmesh.access.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 用户角色关联数据访问接口
 * <p>
 * 提供用户角色关联表的基础CRUD操作和自定义查询方法。
 * 用户角色关联定义用户与角色的绑定关系，是权限分配的核心关联表。
 * 支持批量软删除操作。
 * </p>
 */
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 批量软删除用户角色关联
     *
     * @param tenantId  租户ID
     * @param ids       待删除的用户角色关联ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据租户ID和用户ID集合查询有效用户角色关联（用于批量删除用户时查找关联）
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     * @return 用户角色关联列表
     */
    List<UserRole> selectValidByUserIds(@Param("tenantId") Long tenantId,
                                         @Param("userIds") Set<Long> userIds);

    /**
     * 根据租户ID、用户ID集合、目标ID集合和目标类型查询有效用户角色关联（用于assignRole检查）
     *
     * @param tenantId  租户ID
     * @param userIds   用户ID集合
     * @param targetIds 目标ID集合
     * @param targetType 目标类型
     * @return 用户角色关联列表
     */
    List<UserRole> selectValidByUserIdsAndTargetIds(@Param("tenantId") Long tenantId,
                                                     @Param("userIds") Set<Long> userIds,
                                                     @Param("targetIds") Set<Long> targetIds,
                                                     @Param("targetType") String targetType);

    /**
     * 根据租户ID、用户ID集合、单个目标ID和目标类型查询有效用户角色关联（用于assignRolesBatch检查）
     *
     * @param tenantId  租户ID
     * @param userIds   用户ID集合
     * @param targetId  目标ID
     * @param targetType 目标类型
     * @return 用户角色关联列表
     */
    List<UserRole> selectValidByUserIdsAndTargetId(@Param("tenantId") Long tenantId,
                                                    @Param("userIds") Set<Long> userIds,
                                                    @Param("targetId") Long targetId,
                                                    @Param("targetType") String targetType);

    /**
     * 根据租户ID、用户ID集合、目标类型和目标ID集合查询有效用户角色关联（用于revokeRolesBatch）
     *
     * @param tenantId  租户ID
     * @param userIds   用户ID集合
     * @param targetType 目标类型
     * @param targetIds 目标ID集合
     * @return 用户角色关联列表
     */
    List<UserRole> selectValidByUserIdsTypeAndTargetIds(@Param("tenantId") Long tenantId,
                                                         @Param("userIds") Set<Long> userIds,
                                                         @Param("targetType") String targetType,
                                                         @Param("targetIds") Set<Long> targetIds);

    /**
     * 根据用户ID和租户ID查询有效的用户角色关联（含有效期过滤）
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @param now      当前时间
     * @return 用户角色关联列表
     */
    List<UserRole> selectValidByUserIdWithValidity(@Param("userId") Long userId,
                                                     @Param("tenantId") Long tenantId,
                                                     @Param("now") LocalDateTime now);

    /**
     * 批量查询多个用户的有效角色关联（含有效期过滤）
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     * @param now      当前时间
     * @return 用户角色关联列表
     */
    List<UserRole> selectValidByUserIdsWithValidity(@Param("tenantId") Long tenantId,
                                                      @Param("userIds") Set<Long> userIds,
                                                      @Param("now") LocalDateTime now);

    /**
     * 查询指定角色和目标类型的有效用户角色关联
     *
     * @param tenantId   租户ID
     * @param targetId   目标ID
     * @param targetType 目标类型
     * @return 用户角色关联列表
     */
    List<UserRole> selectValidByTargetIdAndType(@Param("tenantId") Long tenantId,
                                                 @Param("targetId") Long targetId,
                                                 @Param("targetType") String targetType);

    /**
     * 查询指定角色集合的有效用户角色关联
     *
     * @param tenantId 租户ID
     * @param targetIds 目标ID集合
     * @param targetType 目标类型
     * @return 用户角色关联列表
     */
    List<UserRole> selectValidByTargetIdsAndType(@Param("tenantId") Long tenantId,
                                                   @Param("targetIds") Set<Long> targetIds,
                                                   @Param("targetType") String targetType);

    /**
     * 根据租户ID、目标类型、目标ID和关联ID查询有效用户角色
     *
     * @param tenantId    租户ID
     * @param targetType  目标类型
     * @param targetId    目标ID
     * @param relationId  关联ID
     * @return 用户角色实体，不存在返回null
     */
    UserRole selectValidByTargetAndRelation(@Param("tenantId") Long tenantId,
                                              @Param("targetType") String targetType,
                                              @Param("targetId") Long targetId,
                                              @Param("relationId") Long relationId);

    /**
     * 根据租户ID、目标类型和目标ID查询有效用户角色列表
     *
     * @param tenantId   租户ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 用户角色列表
     */
    List<UserRole> selectByTargetTypeAndTargetId(@Param("tenantId") Long tenantId,
                                                    @Param("targetType") String targetType,
                                                    @Param("targetId") Long targetId);

    /**
     * 批量查询指定 (userId, targetId, relationId) 三元组的有效用户角色（同 targetType）。
     * <p>
     * 用于 user-role full-sync 阶段 B 一次性预加载所有 items 对应的 user_role 行，避免循环单条 select。
     * 入参 {@code userIds/targetIds/relationIds} 仅用于收窄候选集；调用方按三元组 in-memory 过滤。
     * </p>
     *
     * @param tenantId    租户 ID
     * @param userIds     候选用户 ID 集合
     * @param targetIds   候选 targetId 集合
     * @param relationIds 候选 relationId 集合
     * @param targetType  target 类型常量
     * @return 命中候选范围的用户角色列表
     */
    List<UserRole> selectValidByUserTargetRelation(@Param("tenantId") Long tenantId,
                                                    @Param("userIds") Set<Long> userIds,
                                                    @Param("targetIds") Set<Long> targetIds,
                                                    @Param("relationIds") Set<Long> relationIds,
                                                    @Param("targetType") String targetType);

    /**
     * 查询孤儿用户角色关联：用户已被软删但 user_role 仍存活（全租户）。
     * <p>
     * 用于延迟补偿定时任务（UserRoleOrphanCleanupTask），
     * 在确认 UNBIND envelope 未能到达后兜底清理残留的 user_role。
     * cutoff 参数给 envelope 处理留窗口期（默认 5 分钟）。
     * <p>
     * 不限 tenantId，自动覆盖所有租户的孤儿记录。
     *
     * @param cutoff   截止时间，仅清理 updated_at 早于此时间的记录
     * @return 孤儿 user_role 列表
     */
    List<UserRole> selectOrphansByCutoff(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 批量刷新已有绑定行的 owner/updatedAt（batchBind 已有行路径，单条 SQL 替代循环 update）。
     *
     * @param tenantId  租户ID
     * @param owner     所有者服务编码
     * @param ids       待刷新行ID集合
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int batchRefreshOwner(@Param("tenantId") Long tenantId,
                          @Param("owner") String owner,
                          @Param("ids") List<Long> ids,
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 批量迁移成员 relation_id（岗位移动路径，单条 SQL 替代循环 update）。
     *
     * @param tenantId   租户ID
     * @param relationId 新 relation_id（新所属组织角色ID）
     * @param ids        待迁移成员行ID集合
     * @param updatedAt  更新时间
     * @return 影响行数
     */
    int batchUpdateRelationByIds(@Param("tenantId") Long tenantId,
                                 @Param("relationId") Long relationId,
                                 @Param("ids") List<Long> ids,
                                 @Param("updatedAt") LocalDateTime updatedAt);
}