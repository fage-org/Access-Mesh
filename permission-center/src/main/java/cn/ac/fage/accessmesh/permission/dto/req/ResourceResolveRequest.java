package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * 资源解析请求项
 * <p>
 * 用于批量资源ID解析的单个请求项，包含解析所需的所有参数。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码
 * @param resourceCode     资源编码
 * @param codeType         编码类型
 * @param domainCode       业务域编码
 */
public record ResourceResolveRequest(
    String resourceTypeCode,
    String resourceCode,
    String codeType,
    String domainCode
) {
    /**
     * 转换为资源解析键
     * <p>
     * 用于将请求项转换为结果查找键。
     * </p>
     *
     * @return 资源解析键对象
     */
    public ResourceResolveKey toKey() {
        return new ResourceResolveKey(resourceTypeCode, resourceCode, codeType, domainCode);
    }
}