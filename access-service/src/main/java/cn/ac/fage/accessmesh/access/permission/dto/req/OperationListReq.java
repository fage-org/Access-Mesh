package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 操作权限列表查询请求体
 * <p>
 * 用于查询操作权限列表，可选按资源类型和业务域过滤。
 * </p>
 *
 * @param resourceTypeCode       资源类型编码，可选，用于过滤
 * @param domainCode             业务域编码，可选，用于过滤
 * @param includeGlobalFallback  可选，默认 false；true 时后端完成「专属优先、全局回退」合并，
 *                               响应直接返回当前 resourceTypeCode 最终可用的操作集合
 *                               （api-contract §5.3，T-PERM-040 契约；T-ACCESS-021 补齐后端实现）
 */
public record OperationListReq(
    String resourceTypeCode,
    String domainCode,
    Boolean includeGlobalFallback
) {}