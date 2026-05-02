package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * Request item for batch resource resolution.
 * Contains all parameters needed to resolve a single resource.
 */
public record ResourceResolveRequest(
    String resourceTypeCode,
    String resourceCode,
    String codeType,
    String domainCode
) {
    /**
     * Convert to a ResourceResolveKey for result lookup.
     */
    public ResourceResolveKey toKey() {
        return new ResourceResolveKey(resourceTypeCode, resourceCode, codeType, domainCode);
    }
}