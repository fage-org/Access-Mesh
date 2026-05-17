package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
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
     * 查询角色的所有有效权限
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 权限列表
     */
    List<RoleResourcePermission> selectByRoleId(@Param("tenantId") Long tenantId,
                                                @Param("roleId") Long roleId);

    /**
     * 查询指定角色和资源的自动授权权限（grantSource=AUTO_DEP）
     *
     * @param tenantId        租户ID
     * @param roleId          角色ID
     * @param resourceEntityId 源资源实体ID
     * @return 权限列表
     */
    List<RoleResourcePermission> selectAutoGrantsByResource(@Param("tenantId") Long tenantId,
                                                            @Param("roleId") Long roleId,
                                                            @Param("resourceEntityId") Long resourceEntityId);

    /**
     * 统计指定复合键的有效权限数量（重复检查）
     *
     * @param tenantId     租户ID
     * @param roleId       角色ID
     * @param resourceId   资源实体ID
     * @param operationId  操作权限ID
     * @return 数量
     */
    long countByCompositeKey(@Param("tenantId") Long tenantId,
                             @Param("roleId") Long roleId,
                             @Param("resourceId") Long resourceId,
                             @Param("operationId") Long operationId);

    /**
     * 查询指定角色和目标资源的现有自动授权记录（按grantDepId分组）
     *
     * @param tenantId        租户ID
     * @param roleId          角色ID
     * @param targetResourceIds 目标资源实体ID集合
     * @param depIds          依赖规则ID集合
     * @return 权限列表
     */
    List<RoleResourcePermission> selectExistingAutoGrants(@Param("tenantId") Long tenantId,
                                                          @Param("roleId") Long roleId,
                                                          @Param("targetResourceIds") Set<Long> targetResourceIds,
                                                          @Param("depIds") Set<Long> depIds);

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
     * 查询指定角色和资源的软删除权限（复合键匹配）
     *
     * @param tenantId     租户ID
     * @param roleId       角色ID
     * @param resourceId   资源实体ID
     * @param operationId  操作权限ID
     * @return 权限列表
     */
    List<RoleResourcePermission> selectSoftDeletedByCompositeKey(@Param("tenantId") Long tenantId,
                                                                @Param("roleId") Long roleId,
                                                                @Param("resourceId") Long resourceId,
                                                                @Param("operationId") Long operationId);

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
     * 根据角色ID集合查询有效的权限记录
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectValidByRoleIds(@Param("tenantId") Long tenantId,
                                                       @Param("roleIds") Set<Long> roleIds);

    /**
     * 根据角色ID和资源ID集合查询有效的非全量权限记录
     *
     * @param tenantId    租户ID
     * @param roleIds     角色ID集合
     * @param resourceIds 资源ID集合
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectValidByRoleIdsAndResourceIds(@Param("tenantId") Long tenantId,
                                                                     @Param("roleIds") Set<Long> roleIds,
                                                                     @Param("resourceIds") Set<Long> resourceIds);

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
     * 根据租户ID、角色ID集合、资源类型集合和操作权限ID集合查询角色资源权限列表
     * <p>
     * 用于授权检查场景，批量查询指定角色的权限记录。
     * </p>
     *
     * @param tenantId         租户ID
     * @param abstractRoleIds  抽象角色ID集合
     * @param resourceTypeValues 资源类型值集合
     * @param opPermIds        操作权限ID集合
     * @return 角色资源权限列表
     */
    List<RoleResourcePermission> selectByTenantRolesResourceTypesAndOpPermIds(
        @Param("tenantId") Long tenantId,
        @Param("abstractRoleIds") Set<Long> abstractRoleIds,
        @Param("resourceTypeValues") Set<Integer> resourceTypeValues,
        @Param("opPermIds") Set<Long> opPermIds);

    /**
     * 根据租户ID和角色ID查询角色资源权限列表
     * <p>
     * 用于权限版本计算场景。
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
     * 查询类型级权限（scopeAll=true），可选资源类型和操作权限ID过滤
     *
     * @param tenantId              租户ID
     * @param roleIds               角色ID集合
     * @param resourceTypes         资源类型值集合，可为null或空（不过滤）
     * @param operationPermissionIds 操作权限ID集合，可为null或空（不过滤）
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectScopeAllPerms(
        @Param("tenantId") Long tenantId,
        @Param("roleIds") Set<Long> roleIds,
        @Param("resourceTypes") Set<Integer> resourceTypes,
        @Param("operationPermissionIds") Set<Long> operationPermissionIds);

    /**
     * 查询实例级权限（scopeAll=false），可选资源实体ID和操作权限ID过滤
     *
     * @param tenantId              租户ID
     * @param roleIds               角色ID集合
     * @param resourceEntityIds     资源实体ID集合，可为null或空（不过滤）
     * @param operationPermissionIds 操作权限ID集合，可为null或空（不过滤）
     * @return 权限记录列表
     */
    List<RoleResourcePermission> selectInstancePerms(
        @Param("tenantId") Long tenantId,
        @Param("roleIds") Set<Long> roleIds,
        @Param("resourceEntityIds") Set<Long> resourceEntityIds,
        @Param("operationPermissionIds") Set<Long> operationPermissionIds);
}