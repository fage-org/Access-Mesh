package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 资源列表查询请求体
 * <p>
 * 用于查询资源列表，支持按资源类型、业务域过滤和标准分页。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，可选，用于过滤
 * @param domainCode       业务域编码，可选，用于过滤
 * @param pageNum          页码，可选
 * @param pageSize         每页条数，可选
 * @param sort             排序字段，可选
 */
public record ResourceListReq(
    String resourceTypeCode,
    String domainCode,
    Integer pageNum,
    Integer pageSize,
    String sort
) {}