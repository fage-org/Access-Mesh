package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * API映射列表查询请求体
 * <p>
 * 用于查询API映射列表，可选按资源ID和服务编码过滤。
 * 两个参数同时存在时使用AND语义。
 * </p>
 *
 * @param resourceId  资源ID，可选
 * @param serviceCode 服务编码，可选
 */
public record ApiMappingListReq(
    Long resourceId,
    String serviceCode
) {}