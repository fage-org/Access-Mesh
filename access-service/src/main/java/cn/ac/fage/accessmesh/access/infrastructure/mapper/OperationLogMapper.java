package cn.ac.fage.accessmesh.access.infrastructure.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.infrastructure.entity.OperationLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志数据访问接口
 * <p>
 * 提供操作日志表的基础CRUD操作。
 * 操作日志表记录用户的操作行为，用于审计和追踪。
 * 使用MyBatis-Flex BaseMapper提供的通用方法。
 * </p>
 * <p>
 * 归并（T-ACCESS-002）：sys_audit_log（admin）并入 operation_log，原 admin 侧
 * SysAuditLogMapper 的租户分页方法合并到本接口后因无调用方删除（T-ADMIN-026）。
 * </p>
 */
public interface OperationLogMapper extends BaseMapper<OperationLog> {
    /**
     * 按条件分页查询操作日志（按创建时间倒序）
     *
     * @param tenantId   租户ID
     * @param module     模块，可选，精确过滤
     * @param action     操作类型，可选，精确过滤
     * @param operatorId 操作者用户ID，可选
     * @param since      创建时间下界（含），可选
     * @param until      创建时间上界（含），可选
     * @param targetType 目标类型，可选，精确过滤
     * @param offset     分页偏移量
     * @param limit      分页大小
     * @return 操作日志列表
     */
    List<OperationLog> selectPageByCondition(@Param("tenantId") Long tenantId,
                                              @Param("module") String module,
                                              @Param("action") String action,
                                              @Param("operatorId") Long operatorId,
                                              @Param("since") LocalDateTime since,
                                              @Param("until") LocalDateTime until,
                                              @Param("targetType") String targetType,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /**
     * 按条件统计操作日志数量
     *
     * @param tenantId   租户ID
     * @param module     模块，可选，精确过滤
     * @param action     操作类型，可选，精确过滤
     * @param operatorId 操作者用户ID，可选
     * @param since      创建时间下界（含），可选
     * @param until      创建时间上界（含），可选
     * @param targetType 目标类型，可选，精确过滤
     * @return 操作日志总数
     */
    long countByCondition(@Param("tenantId") Long tenantId,
                           @Param("module") String module,
                           @Param("action") String action,
                           @Param("operatorId") Long operatorId,
                           @Param("since") LocalDateTime since,
                           @Param("until") LocalDateTime until,
                           @Param("targetType") String targetType);

    /**
     * 查询操作日志当前实际存在的 action 去重集合（字典接口，T-PERM-025）
     *
     * @param tenantId 租户ID
     * @param module   模块，可选，精确过滤
     * @return 去重且按字典序排列的 action 集合
     */
    List<String> selectDistinctActions(@Param("tenantId") Long tenantId,
                                        @Param("module") String module);
}
