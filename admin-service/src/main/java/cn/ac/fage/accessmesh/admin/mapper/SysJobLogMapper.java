package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import cn.ac.fage.accessmesh.admin.entity.SysJobLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统定时任务日志数据访问接口
 * <p>
 * 提供定时任务日志表的基础CRUD操作。
 * 任务日志记录定时任务的执行情况，包括执行时间、状态、结果等。
 * </p>
 */
@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {

    /**
     * 分页查询任务执行日志
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @param jobId    任务ID过滤条件，可选
     * @return 分页结果
     */
    Page<SysJobLog> paginateJobLogs(@Param("page") Page<SysJobLog> page,
                                    @Param("tenantId") Long tenantId,
                                    @Param("jobId") Long jobId);
}