package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 操作权限列表查询请求体
 * <p>
 * 用于查询操作权限列表，可选按资源类型和业务域过滤。
 * 全局操作概念已退役（2026-08-30 设计定案）：操作定义仅按类型返回，
 * 原 includeGlobalFallback 合并参数随概念一并退役。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，可选，用于过滤
 * @param domainCode       业务域编码，可选，用于过滤
 */
public record OperationListReq(
    String resourceTypeCode,
    String domainCode
) {}
