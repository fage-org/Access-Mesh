package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 定时任务日志分页查询请求记录类
 * <p>
 * 用于定时任务执行日志的分页查询参数。
 * 支持按任务ID过滤。
 * 分页参数校验与默认值对齐同域 PageReq 先例（T-ADMIN-026，原裸 Integer 漏传即 NPE 500）。
 * </p>
 *
 * @param pageNum  页码（可选，默认1，最小1）
 * @param pageSize 每页大小（可选，默认20，范围1-100）
 * @param sort     排序字段（可选）
 * @param jobId    任务ID（可选，用于过滤特定任务的日志）
 */
public record JobLogPageReq(
    /**
     * 页码（最小1）
     */
    @Min(1) Integer pageNum,

    /**
     * 每页大小（范围1-100）
     */
    @Min(1) @Max(100) Integer pageSize,

    /**
     * 排序字段
     */
    String sort,

    /**
     * 任务ID（用于过滤）
     */
    Long jobId
) {
    /**
     * 获取页码（默认1）
     *
     * @return 页码
     */
    public int getPageNum() { return pageNum != null ? pageNum : 1; }

    /**
     * 获取每页大小（默认20）
     *
     * @return 每页大小
     */
    public int getPageSize() { return pageSize != null ? pageSize : 20; }
}
