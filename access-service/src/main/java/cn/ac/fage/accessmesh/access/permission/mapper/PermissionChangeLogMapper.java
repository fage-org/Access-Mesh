package cn.ac.fage.accessmesh.access.permission.mapper;

import cn.ac.fage.accessmesh.access.permission.entity.PermissionChangeLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限变更日志数据访问接口
 * <p>
 * 提供权限变更日志表的CRUD操作和自定义查询方法。
 * 权限变更日志表记录权限的变更历史，用于审计和溯源。
 * 支持按用户、角色、时间范围、事件类型等条件筛选查询。
 * </p>
 */
public interface PermissionChangeLogMapper extends BaseMapper<PermissionChangeLog> {

    /**
     * 筛选查询权限变更日志
     * <p>
     * 支持多条件筛选查询：用户、角色、时间范围、事件类型。
     * 用于审计和统计分析场景。
     * </p>
     *
     * @param tenantId  租户ID
     * @param userId    受影响的用户ID，可选
     * @param roleId    涉及的角色ID，可选
     * @param since     开始时间，可选
     * @param until     结束时间，可选
     * @param eventTypes 事件类型列表，可选
     * @param offset    分页偏移量
     * @param limit     每页条数
     * @return 权限变更日志列表
     */
    List<PermissionChangeLog> selectFiltered(@Param("tenantId") Long tenantId,
                                              @Param("userId") Long userId,
                                              @Param("roleId") Long roleId,
                                              @Param("since") LocalDateTime since,
                                              @Param("until") LocalDateTime until,
                                              @Param("eventTypes") List<String> eventTypes,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /**
     * 统计筛选后的权限变更日志数量
     * <p>
     * 统计满足筛选条件的变更记录总数，用于分页计算。
     * </p>
     *
     * @param tenantId  租户ID
     * @param userId    受影响的用户ID，可选
     * @param roleId    涉及的角色ID，可选
     * @param since     开始时间，可选
     * @param until     结束时间，可选
     * @param eventTypes 事件类型列表，可选
     * @return 变更日志数量
     */
    long countFiltered(@Param("tenantId") Long tenantId,
                      @Param("userId") Long userId,
                      @Param("roleId") Long roleId,
                      @Param("since") LocalDateTime since,
                      @Param("until") LocalDateTime until,
                      @Param("eventTypes") List<String> eventTypes);

    /**
     * 根据租户ID和可选实体类型/实体ID查询变更日志列表（按创建时间倒序）
     *
     * @param tenantId   租户ID
     * @param entityType 实体类型，可为null
     * @param entityId   实体ID，可为null
     * @param offset     分页偏移量
     * @param limit      分页大小
     * @return 变更日志列表
     */
    List<PermissionChangeLog> selectByTenantEntityTypeEntityId(@Param("tenantId") Long tenantId,
                                                                 @Param("entityType") String entityType,
                                                                 @Param("entityId") Long entityId,
                                                                 @Param("offset") int offset,
                                                                 @Param("limit") int limit);

    /**
     * 根据租户ID和可选实体类型/实体ID统计变更日志数量
     *
     * @param tenantId   租户ID
     * @param entityType 实体类型，可为null
     * @param entityId   实体ID，可为null
     * @return 变更日志总数
     */
    long countByTenantEntityTypeEntityId(@Param("tenantId") Long tenantId,
                                          @Param("entityType") String entityType,
                                          @Param("entityId") Long entityId);
}