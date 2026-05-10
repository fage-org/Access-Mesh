package cn.ac.fage.accessmesh.common.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 通用分页请求DTO
 * <p>
 * 用于分页查询请求的参数封装。
 * </p>
 */
public record PageReq(
    /**
     * 页码（最小值1）
     */
    @Min(1) Integer pageNum,

    /**
     * 每页大小（最小值1，最大值100）
     */
    @Min(1) @Max(100) Integer pageSize,

    /**
     * 排序字段（可选）
     */
    String sort
) {
    /**
     * 获取页码（默认值1）
     *
     * @return 页码
     */
    public int getPageNum() { return pageNum != null ? pageNum : 1; }

    /**
     * 获取每页大小（默认值20）
     *
     * @return 每页大小
     */
    public int getPageSize() { return pageSize != null ? pageSize : 20; }

    /**
     * 创建默认分页请求
     *
     * @return 默认分页请求实例（页码1，每页20条）
     */
    public static PageReq defaults() {
        return new PageReq(1, 20, null);
    }

    /**
     * 创建分页请求实例
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页请求实例
     */
    public static PageReq of(int pageNum, int pageSize) {
        return new PageReq(pageNum, pageSize, null);
    }
}