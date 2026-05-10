package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * 域配置列表请求体
 * <p>
 * 用于查询域配置列表，可选按业务域编码过滤。
 * </p>
 *
 * @param domainCode 业务域编码，可选，用于过滤
 */
public record DomainConfigListReq(
    String domainCode
) {}