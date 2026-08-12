package cn.ac.fage.accessmesh.access.infrastructure.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import cn.ac.fage.accessmesh.access.infrastructure.entity.OperationLog;
import org.apache.ibatis.annotations.Param;

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
 * SysAuditLogMapper 的租户分页方法合并到本接口。
 * </p>
 */
public interface OperationLogMapper extends BaseMapper<OperationLog> {
    /**
     * 根据租户ID和可选模块/操作类型查询操作日志列表（按创建时间倒序）
     *
     * @param tenantId 租户ID
     * @param module   模块名称，可为null
     * @param action   操作类型，可为null
     * @param offset   分页偏移量
     * @param limit    分页大小
     * @return 操作日志列表
     */
    List<OperationLog> selectByTenantModuleAction(@Param("tenantId") Long tenantId,
                                                    @Param("module") String module,
                                                    @Param("action") String action,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    /**
     * 根据租户ID和可选模块/操作类型统计操作日志数量
     *
     * @param tenantId 租户ID
     * @param module   模块名称，可为null
     * @param action   操作类型，可为null
     * @return 操作日志总数
     */
    long countByTenantModuleAction(@Param("tenantId") Long tenantId,
                                    @Param("module") String module,
                                    @Param("action") String action);

    /**
     * 分页查询指定租户的操作日志，按创建时间倒序排列（原 admin 审计日志页）
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @return 分页结果
     */
    Page<OperationLog> paginateByTenantId(@Param("page") Page<OperationLog> page,
                                          @Param("tenantId") Long tenantId);
}
