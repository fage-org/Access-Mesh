package cn.ac.fage.accessmesh.access.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 角色资源权限数据访问接口
 * <p>
 * 提供角色资源权限表的基础CRUD操作和自定义查询方法。
 * 角色资源权限记录了角色对资源的操作权限，是权限系统的核心关联表。
 * 支持批量软删除、级联删除等操作。
 * </p>
 */
public interface RoleResourcePermissionMapper extends BaseMapper<RoleResourcePermission> {

    /**
     * 查询指定ID列表中属于指定角色和租户的有效权限
     *
     * @param tenantId     租户ID
     * @param roleId       角色ID
     * @param permissionIds 权限ID列表
     * @return 权限列表
     */
    List<RoleResourcePermission> selectValidByIds(@Param("tenantId") Long tenantId,
                                                  @Param("roleId") Long roleId,
                                                  @Param("permissionIds") List<Long> permissionIds);

    /**
     * 根据ID查询有效权限（可选角色过滤）
     *
     * @param tenantId     租户ID
     * @param roleId       角色ID（可为null）
     * @param permissionId 权限ID
     * @return 权限实体
     */
    RoleResourcePermission selectValidById(@Param("tenantId") Long tenantId,
                                          @Param("roleId") Long roleId,
                                          @Param("permissionId") Long permissionId);

    /**
     * 查询指定资源ID列表的有效权限ID
     *
     * @param tenantId 租户ID
     * @param resourceIds 资源ID列表
     * @return 权限ID列表
     */
    List<Long> selectValidPermIdsByResourceIds(@Param("tenantId") Long tenantId,
                                               @Param("resourceIds") List<Long> resourceIds);

    /**
     * 查询指定资源ID集合涉及的受影响角色ID集合（资源软删场景登记 ROLE_PERM_SNAPSHOT 失效）。
     * <p>
     * T-PERM-018：deleteResources 软删 role_resource_permission 前，查出受影响 roleIds 并 markRoles，
     * afterCommit 由 @PermissionChange AOP evictBatch ROLE_PERM_SNAPSHOT。
     * </p>
     *
     * @param tenantId    租户ID
     * @param resourceIds 资源ID集合
     * @return 受影响角色ID集合
     */
    Set<Long> selectRoleIdsByResourceIds(@Param("tenantId") Long tenantId,
                                         @Param("resourceIds") List<Long> resourceIds);

    /**
     * 根据条件ID集合反查受影响的服务编码集合（条件变更场景登记 serviceCodes 失效）。
     * <p>
     * T-PERM-017 P2-A 评审反馈：条件 update/delete 时，已下发到 Gateway 内联 conditionRules 的
     * 接口快照需失效。本方法 JOIN role_resource_permission + resource_api_mapping，
     * 一次 SQL 查出所有引用了这些条件的资源对应的 serviceCodes，供 ConditionAppService 调用
     * {@code markServiceCodes} 进入广播事件载荷。
     * </p>
     * <p>
     * SQL: SELECT DISTINCT m.service_code FROM role_resource_permission rrp
     *      JOIN resource_api_mapping m ON m.resource_entity_id = rrp.resource_entity_id
     *      WHERE rrp.tenant_id=? AND rrp.condition_id IN (...) AND rrp.delete_flag=0
     *        AND m.tenant_id=? AND m.delete_flag=0
     * </p>
     *
     * @param tenantId     租户ID
     * @param conditionIds 条件ID集合（不可为空）
     * @return 受影响的服务编码集合（去重）；无引用返回空集合
     */
    Set<String> selectServiceCodesByConditionIds(@Param("tenantId") Long tenantId,
                                                  @Param("conditionIds") Set<Long> conditionIds);

    /**
     * 批量软删除角色资源权限
     *
     * @param tenantId  租户ID
     * @param ids       待删除的权限ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 级联软删除子权限
     *
     * @param tenantId  租户ID
     * @param parentIds 父权限ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int cascadeSoftDeleteChildren(@Param("tenantId") Long tenantId,
                                   @Param("parentIds") List<Long> parentIds,
                                   @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 按租户查询 depend_on 命中指定主权限的有效子权限行
     * <p>
     * 与 {@link #cascadeSoftDeleteChildren} 同一租户口径（不限角色），
     * 供 apply-grant-plan 预检期快照级联删除子权限的业务键。
     *
     * @param tenantId 租户ID
     * @param dependOns 主权限ID集合
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectValidByDependOns(@Param("tenantId") Long tenantId,
                                                         @Param("dependOns") Set<Long> dependOns);

    /**
     * 按租户、角色和记录ID更新授权可变属性；conditionId 允许显式写 null。
     */
    int updateGrantAttributes(@Param("tenantId") Long tenantId,
                              @Param("roleId") Long roleId,
                              @Param("permissionId") Long permissionId,
                              @Param("canGrant") Boolean canGrant,
                              @Param("conditionId") Long conditionId,
                              @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 根据角色ID集合查询有效的权限记录
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectValidByRoleIds(@Param("tenantId") Long tenantId,
                                                       @Param("roleIds") Set<Long> roleIds);

    /**
     * 根据角色ID和依赖权限ID集合查询有效的权限记录
     *
     * @param tenantId  租户ID
     * @param roleId    角色ID
     * @param dependIds 依赖权限ID集合
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectValidByRoleIdAndDependIds(@Param("tenantId") Long tenantId,
                                                                  @Param("roleId") Long roleId,
                                                                  @Param("dependIds") Set<Long> dependIds);

    /**
     * 根据角色ID和资源实体ID查询有效的权限记录
     *
     * @param tenantId         租户ID
     * @param roleId           角色ID
     * @param resourceEntityId 资源实体ID
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectValidByRoleIdAndResourceId(@Param("tenantId") Long tenantId,
                                                                    @Param("roleId") Long roleId,
                                                                    @Param("resourceEntityId") Long resourceEntityId);

    /**
     * 按角色与资源类型查询有效主权限（授权矩阵类型过滤，depend_on IS NULL）
     *
     * @param tenantId       租户ID
     * @param abstractRoleId 抽象角色ID
     * @param resourceType   资源类型内部值
     * @return 该类型主权限列表
     */
    List<RoleResourcePermission> selectValidMainByRoleIdAndResourceType(
        @Param("tenantId") Long tenantId,
        @Param("abstractRoleId") Long abstractRoleId,
        @Param("resourceType") Integer resourceType);

    /**
     * 根据租户ID和角色ID查询角色资源权限列表
     * <p>
     * 用于角色权限快照构建场景（engine ROLE_PERM_SNAPSHOT 读路径）。
     * </p>
     *
     * @param tenantId        租户ID
     * @param abstractRoleId 抽象角色ID
     * @return 角色资源权限列表
     */
    List<RoleResourcePermission> selectValidByRoleId(
        @Param("tenantId") Long tenantId,
        @Param("abstractRoleId") Long abstractRoleId);

    /**
     * 根据租户ID和资源实体ID查询有效的权限记录列表
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectValidByResourceEntityId(@Param("tenantId") Long tenantId,
                                                                @Param("resourceEntityId") Long resourceEntityId);

    /**
     * 根据角色ID统计权限记录数
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 记录数
     */
    long countByRoleId(@Param("tenantId") Long tenantId,
                       @Param("roleId") Long roleId);

    /**
     * 批量按位掩码查询类型级权限（单次SQL，多资源类型）
     * <p>
     * 使用 OR 条件合并多个 resourceType 的 bitMask，避免多次SQL调用。
     * SQL: AND ((resource_type = #{entry.resourceType} AND (granted_bits & #{entry.bitMask}) != 0) OR ...)
     * </p>
     *
     * @param tenantId  租户ID
     * @param roleIds   角色ID集合
     * @param bitMasks  位掩码条目列表（resourceType + bitMask）
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectScopeAllPermsByBitsBatch(
        @Param("tenantId") Long tenantId,
        @Param("roleIds") Set<Long> roleIds,
        @Param("bitMasks") List<BitMaskEntry> bitMasks);

    /**
     * 批量按位掩码查询实例级权限（单次SQL，多资源类型）
     * <p>
     * 使用 OR 条件合并多个 resourceType 的 bitMask，避免多次SQL调用。
     * SQL: AND ((resource_type = #{entry.resourceType} AND (granted_bits & #{entry.bitMask}) != 0) OR ...)
     * </p>
     *
     * @param tenantId          租户ID
     * @param roleIds           角色ID集合
     * @param resourceEntityIds 资源实体ID集合
     * @param bitMasks          位掩码条目列表（resourceType + bitMask）
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectInstancePermsByBitsBatch(
        @Param("tenantId") Long tenantId,
        @Param("roleIds") Set<Long> roleIds,
        @Param("resourceEntityIds") Set<Long> resourceEntityIds,
        @Param("bitMasks") List<BitMaskEntry> bitMasks);

    /**
     * 位掩码条目（用于批量位操作查询）
     */
    record BitMaskEntry(Integer resourceType, Long bitMask) {}
}
