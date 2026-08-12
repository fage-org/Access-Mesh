package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 分页响应体
 * <p>
 * 统一的分页响应格式，包含列表数据、总数、分页信息和是否有下一页。
 * 用于需要分页查询的API接口。
 * </p>
 *
 * @param items    当前页的对象列表
 * @param total    总记录数
 * @param pageNum  当前页码
 * @param pageSize 每页条数
 * @param hasNext  是否有下一页
 * @param <T>      对象类型
 */
public record PaginatedResp<T>(
    List<T> items,
    long total,
    int pageNum,
    int pageSize,
    boolean hasNext
) {}