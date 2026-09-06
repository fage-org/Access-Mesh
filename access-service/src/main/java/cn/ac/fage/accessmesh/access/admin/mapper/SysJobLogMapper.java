package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.admin.entity.SysJobLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
     * 按条件分页查询任务执行日志（按创建时间倒序）
     * <p>
     * XML 分页统一 offset/limit + count 双查询（仓库既定模式，见 SysUserMapper；
     * MyBatis-Flex 的 Page 参数在 XML 映射下不生效——selectOne 多行异常，T-ADMIN-026 收口）
     * </p>
     *
     * @param tenantId 租户ID
     * @param jobId    任务ID过滤条件，可选
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 任务日志列表（当前页）
     */
    List<SysJobLog> selectJobLogsByCondition(@Param("tenantId") Long tenantId,
                                             @Param("jobId") Long jobId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    /**
     * 按条件统计任务日志数（条件与 {@link #selectJobLogsByCondition} 一致，用于分页计算）
     */
    long countJobLogsByCondition(@Param("tenantId") Long tenantId,
                                 @Param("jobId") Long jobId);
}
