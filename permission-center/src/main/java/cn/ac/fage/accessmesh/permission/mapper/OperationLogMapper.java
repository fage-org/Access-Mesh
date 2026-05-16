package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.OperationLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作日志数据访问接口
 * <p>
 * 提供操作日志表的基础CRUD操作。
 * 操作日志表记录用户的操作行为，用于审计和追踪。
 * 使用MyBatis-Flex BaseMapper提供的通用方法。
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
}
