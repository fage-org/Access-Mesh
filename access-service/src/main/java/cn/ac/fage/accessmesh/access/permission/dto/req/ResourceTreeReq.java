package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 资源树查询请求体
 * <p>
 * 用于查询资源层级树结构，可选按资源类型过滤。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，可选，用于过滤
 * @param domainCode       业务域编码，可选，用于过滤
 */
public record ResourceTreeReq(
    String resourceTypeCode,
    String domainCode
) {}