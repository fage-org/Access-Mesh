package cn.ac.fage.accessmesh.permission.dto.req;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;

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
    /**
     * 构建唯一的字符串键
     * <p>
     * 用于Map查找，将组合键转换为字符串格式。
     * </p>
     *
     * @return 字符串格式的唯一键
     */
    public String toMapKey() {
        return String.format("%s:%s:%s:%s",
            resourceTypeCode != null ? resourceTypeCode : "",
            resourceCode != null ? resourceCode : "",
            codeType != null ? codeType : PermConstants.CodeType.DEFAULT,
            domainCode != null ? domainCode : "");
    }
}