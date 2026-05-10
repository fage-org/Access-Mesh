package cn.ac.fage.accessmesh.common.model;

import java.util.List;

/**
 * 分页结果记录类
 * <p>
 * 封装分页查询结果，包含数据列表和分页元信息。
 * 用于API响应的分页数据传输。
 * </p>
 *
 * @param <T> 数据项类型
 */
public record PaginatedResult<T>(
    /**
     * 数据项列表
     */
    List<T> items,

    /**
     * 分页元信息
     */
    PaginationMeta pagination
) {
    /**
     * 分页元信息记录类
     * <p>
     * 包含分页的总记录数、当前页码、每页大小和总页数。
     * </p>
     */
    public record PaginationMeta(
        /**
         * 总记录数
         */
        long total,

        /**
         * 当前页码（从1开始）
         */
        int page,

        /**
         * 每页大小
         */
        int size,

        /**
         * 总页数
         */
        int totalPages
    ) {}
}