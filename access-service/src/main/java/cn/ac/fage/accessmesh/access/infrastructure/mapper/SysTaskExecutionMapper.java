package cn.ac.fage.accessmesh.access.infrastructure.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SysTaskExecution;
import org.apache.ibatis.annotations.Param;

/**
 * 任务执行记录数据访问接口（T-ACCESS-002 预建）
 * <p>
 * 提供 sys_task_execution 表的基础 CRUD（BaseMapper 泛型）。
 * 原子抢占/续租/接管/条件完成等并发 SQL 由 T-ACCESS-009 扩展。
 * </p>
 */
public interface SysTaskExecutionMapper extends BaseMapper<SysTaskExecution> {

    /**
     * 按租户与执行键查询有效执行记录
     *
     * @param tenantId     租户ID
     * @param executionKey 执行键
     * @return 执行记录，不存在返回null
     */
    SysTaskExecution selectByTenantAndExecutionKey(@Param("tenantId") Long tenantId,
                                                    @Param("executionKey") String executionKey);
}
