package cn.ac.fage.accessmesh.access.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionConflictRule;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限冲突规则数据访问接口
 * <p>
 * 提供权限冲突规则表的基础CRUD操作和自定义查询方法。
 * 权限冲突规则用于检测和处理权限冲突场景，如权限重叠、冲突操作等。
 * 支持按冲突类型查询、批量软删除操作。
 * </p>
 */
public interface PermissionConflictRuleMapper extends BaseMapper<PermissionConflictRule> {

    /**
     * 按租户和冲突类型查询冲突规则
     *
     * @param tenantId     租户ID
     * @param conflictType 冲突类型值
     * @return 匹配的冲突规则列表
     */
    List<PermissionConflictRule> selectByConflictType(@Param("tenantId") Long tenantId,
                                                       @Param("conflictType") String conflictType);

    /**
     * 批量软删除冲突规则
     * <p>
     * 将指定冲突规则的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的冲突规则ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据ID和租户ID查询有效冲突规则
     *
     * @param ruleId   冲突规则ID
     * @param tenantId 租户ID
     * @return 冲突规则实体，不存在返回null
     */
    PermissionConflictRule selectValidById(@Param("ruleId") Long ruleId,
                                            @Param("tenantId") Long tenantId);

    /**
     * 根据租户ID查询所有有效冲突规则列表
     *
     * @param tenantId 租户ID
     * @return 冲突规则列表
     */
    List<PermissionConflictRule> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 根据租户ID和可选资源类型值查询有效冲突规则列表
     *
     * @param tenantId         租户ID
     * @param resourceTypeValue 资源类型值，可为null
     * @return 冲突规则列表
     */
    List<PermissionConflictRule> selectByTenantAndResourceType(@Param("tenantId") Long tenantId,
                                                                @Param("resourceTypeValue") Integer resourceTypeValue);

    /**
     * 根据租户ID和ID集合查询有效冲突规则列表
     *
     * @param tenantId 租户ID
     * @param ids      冲突规则ID集合
     * @return 冲突规则列表
     */
    List<PermissionConflictRule> selectValidByIds(@Param("tenantId") Long tenantId,
                                                   @Param("ids") java.util.Set<Long> ids);
}