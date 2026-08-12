package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 资源解析键
 * <p>
 * 用于批量资源ID解析结果的键，作为batchResolveResourceIds返回值的Map键。
 * 包含资源类型、编码、编码类型和业务域的组合。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码
 * @param resourceCode     资源编码
 * @param codeType         编码类型
 * @param domainCode       业务域编码
 */
public record ResourceResolveKey(
    String resourceTypeCode,
    String resourceCode,
    String codeType,
    String domainCode
) {
}