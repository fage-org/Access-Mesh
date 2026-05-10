package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * 操作权限列表查询请求体
 * <p>
 * 用于查询操作权限列表，可选按资源类型和业务域过滤。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，可选，用于过滤
 * @param domainCode       业务域编码，可选，用于过滤
 */
public record OperationListReq(
    String resourceTypeCode,
    String domainCode
) {}