package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 组织分页查询请求DTO（合并分页参数和查询条件）
 */
public record OrgPageReq(
    @Min(1) Integer pageNum,
    @Min(1) @Max(100) Integer pageSize,
    String sort,
    String orgName,
    Integer orgType,
    Integer status,
    String parentOrgId
) {
    public int getPageNum() { return pageNum != null ? pageNum : 1; }
    public int getPageSize() { return pageSize != null ? pageSize : 20; }
}