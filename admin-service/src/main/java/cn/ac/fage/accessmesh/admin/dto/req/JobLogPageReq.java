package cn.ac.fage.accessmesh.admin.dto.req;

/**
 * 定时任务日志分页查询请求记录类
 * <p>
 * 用于定时任务执行日志的分页查询参数。
 * 支持按任务ID过滤。
 * </p>
 *
 * @param pageNum  页码（可选，默认1）
 * @param pageSize 每页大小（可选，默认10）
 * @param sort     排序字段（可选）
 * @param jobId    任务ID（可选，用于过滤特定任务的日志）
 */
public record JobLogPageReq(
    /**
     * 页码
     */
    Integer pageNum,

    /**
     * 每页大小
     */
    Integer pageSize,

    /**
     * 排序字段
     */
    String sort,

    /**
     * 任务ID（用于过滤特定任务的日志）
     */
    Long jobId
) {}