package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * OAuth2客户端分页查询请求DTO
 */
public record Oauth2ClientPageReq(
    @Min(1) Integer pageNum,
    @Min(1) @Max(100) Integer pageSize,
    String sort,
    String clientName,
    Integer status
) {
    public int getPageNum() { return pageNum != null ? pageNum : 1; }
    public int getPageSize() { return pageSize != null ? pageSize : 20; }
}