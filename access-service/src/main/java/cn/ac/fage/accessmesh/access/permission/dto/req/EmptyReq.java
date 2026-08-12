package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 空请求体
 * <p>
 * 用于不需要额外请求参数的POST接口。
 * 租户ID从请求头 X-Tenant-Id 获取，不需要在请求体中传递。
 * 例如：查询列表、查询所有配置等操作。
 * </p>
 */
public record EmptyReq() {}