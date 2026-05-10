package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * OAuth2客户端分页查询请求记录类
 * <p>
 * 用于OAuth2客户端列表的分页查询参数。
 * 支持按客户端名称、状态过滤。
 * </p>
 *
 * @param pageNum    页码（可选，默认1，最小1）
 * @param pageSize   每页大小（可选，默认20，范围1-100）
 * @param sort       排序字段（可选）
 * @param clientName 客户端名称（可选，模糊匹配）
 * @param status     状态（可选，0=正常，1=禁用）
 */
public record Oauth2ClientPageReq(
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
     * 客户端名称（模糊匹配）
     */
    String clientName,

    /**
     * 状态（0=正常，1=禁用）
     */
    Integer status
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