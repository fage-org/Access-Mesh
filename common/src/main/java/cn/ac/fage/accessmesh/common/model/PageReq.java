package cn.ac.fage.accessmesh.common.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 通用分页请求DTO
 */
public record PageReq(
    @Min(1) Integer pageNum,
    @Min(1) @Max(100) Integer pageSize,
    String sort
) {
    public int getPageNum() { return pageNum != null ? pageNum : 1; }
    public int getPageSize() { return pageSize != null ? pageSize : 20; }

    public static PageReq defaults() {
        return new PageReq(1, 20, null);
    }

    public static PageReq of(int pageNum, int pageSize) {
        return new PageReq(pageNum, pageSize, null);
    }
}