package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageReq(
    @Min(1)
    Integer pageNum,
    @Min(1) @Max(100)
    Integer pageSize,
    String sort
) {
    public int getPageNum() { return pageNum != null ? pageNum : 1; }
    public int getPageSize() { return pageSize != null ? pageSize : 20; }
}
