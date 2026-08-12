package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 类型定义列表查询请求体
 * <p>
 * 用于查询类型定义列表，可选按业务域编码过滤。
 * </p>
 *
 * @param domainCode 业务域编码，可选，用于过滤
 */
public record TypeListReq(
    String domainCode
) {}