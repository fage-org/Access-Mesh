package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import cn.ac.fage.accessmesh.access.admin.entity.SysJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统定时任务数据访问接口
 * <p>
 * 提供定时任务表的基础CRUD操作和自定义查询方法。
 * 支持批量软删除操作。
 * </p>
 */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {

    /**
     * 根据ID查询有效任务（租户隔离+未删除）
     *
     * @param tenantId 租户ID
     * @param id       任务ID
     * @return 任务实体，不存在返回null
     */
    SysJob selectValidById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 查询全部租户下所有启用的有效任务（跨租户单条批量查询，T-ACCESS-009）
     * <p>
     * 供启动加载与多实例配置对账使用——按租户循环查询违反 §8.4.8 N+1 禁令。
     * 任务调度与对账以 jobId 为主键操作，跨租户加载无租户作用域问题。
     * </p>
     *
     * @return 全部租户启用状态的有效任务列表
     */
    List<SysJob> selectAllEnabledJobs();

    /**
     * 批量查询有效任务（租户隔离+未删除）
     *
     * @param tenantId 租户ID
     * @param ids      任务ID列表
     * @return 有效任务列表
     */
    List<SysJob> selectValidByIds(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);

    /**
     * 分页查询任务列表
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @param jobGroup 任务组过滤条件，可选
     * @return 分页结果
     */
    Page<SysJob> paginateJobs(@Param("page") Page<SysJob> page,
                              @Param("tenantId") Long tenantId,
                              @Param("jobGroup") String jobGroup);

    /**
     * 批量软删除定时任务
     * <p>
     * 将指定任务的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID，用于数据隔离
     * @param ids       待删除的任务ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}