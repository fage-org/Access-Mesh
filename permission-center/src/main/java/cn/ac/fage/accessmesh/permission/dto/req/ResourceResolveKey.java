package cn.ac.fage.accessmesh.permission.dto.req;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;

/**
 * Key for batch resource resolution results.
 * Used as the map key in batchResolveResourceIds return value.
 */
public record ResourceResolveKey(
    String resourceTypeCode,
    String resourceCode,
    String codeType,
    String domainCode
) {
    /**
     * Build a unique string key for map lookups.
     */
    public String toMapKey() {
        return String.format("%s:%s:%s:%s",
            resourceTypeCode != null ? resourceTypeCode : "",
            resourceCode != null ? resourceCode : "",
            codeType != null ? codeType : PermConstants.CodeType.DEFAULT,
            domainCode != null ? domainCode : "");
    }
}