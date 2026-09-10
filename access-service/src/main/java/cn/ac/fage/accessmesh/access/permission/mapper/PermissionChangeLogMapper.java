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
     * 多条件筛选查询权限变更日志（按创建时间倒序，条件组与 countByCondition 共享）
     * <p>
     * T-PERM-032：页面查询条件组（原与 recent-changes 统一——该端点已随 T-PERM-059 删除，2026-09-10）。
     * 对齐 schema 索引：affected user/role 走 GIN 包含、eventType 走表达式索引、时间/实体走普通索引。
     * </p>
     *
     * @param tenantId     租户ID
     * @param entityType   实体类型，可选
     * @param entityId     实体ID，可选
     * @param userId       受影响用户ID（affected_abstract_user_ids 包含），可选
     * @param roleId       受影响角色ID（affected_abstract_role_ids 包含），可选
     * @param since        创建时间下界（含），可选
     * @param until        创建时间上界（含），可选
     * @param eventTypes   diff_snapshot.eventType 集合，可选
     * @param changeSource 变更来源（MANUAL/SERVICE_SYNC），可选
     * @param offset       分页偏移量
     * @param limit        分页大小
     * @return 权限变更日志列表
     */
    List<PermissionChangeLog> selectPageByCondition(@Param("tenantId") Long tenantId,
                                                    @Param("entityType") String entityType,
                                                    @Param("entityId") Long entityId,
                                                    @Param("userId") Long userId,
                                                    @Param("roleId") Long roleId,
                                                    @Param("since") LocalDateTime since,
                                                    @Param("until") LocalDateTime until,
                                                    @Param("eventTypes") List<String> eventTypes,
                                                    @Param("changeSource") String changeSource,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    /**
     * 多条件统计权限变更日志数量（条件与 {@link #selectPageByCondition} 一致，用于分页计算）
     */
    long countByCondition(@Param("tenantId") Long tenantId,
                          @Param("entityType") String entityType,
                          @Param("entityId") Long entityId,
                          @Param("userId") Long userId,
                          @Param("roleId") Long roleId,
                          @Param("since") LocalDateTime since,
                          @Param("until") LocalDateTime until,
                          @Param("eventTypes") List<String> eventTypes,
                          @Param("changeSource") String changeSource);
}