package cn.ac.fage.accessmesh.permission.dto.req;

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
            codeType != null ? codeType : "default",
            domainCode != null ? domainCode : "");
    }
}