package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysJobLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统定时任务日志数据访问接口
 * <p>
 * 提供定时任务日志表的基础CRUD操作。
 * 任务日志记录定时任务的执行情况，包括执行时间、状态、结果等。
 * </p>
 */
@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {
}