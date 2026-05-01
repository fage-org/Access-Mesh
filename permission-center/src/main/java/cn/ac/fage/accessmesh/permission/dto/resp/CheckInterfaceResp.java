package cn.ac.fage.accessmesh.permission.dto.resp;

/**
 * Gateway callback response: allowed/denied with matched role and operation info.
 */
public record CheckInterfaceResp(
    boolean allowed,
    String reason,
    java.util.List<MatchedResource> matchedResources,
    int cacheTtlSeconds
) {
    public static CheckInterfaceResp allow(java.util.List<MatchedResource> matchedResources, int cacheTtlSeconds) {
        return new CheckInterfaceResp(true, null, matchedResources, cacheTtlSeconds);
    }

    public static CheckInterfaceResp deny(String reason, java.util.List<MatchedResource> matchedResources,
                                          int cacheTtlSeconds) {
        return new CheckInterfaceResp(false, reason, matchedResources, cacheTtlSeconds);
    }

    public static CheckInterfaceResp deny(String reason) {
        return deny(reason, java.util.List.of(), 30);
    }

    public record MatchedResource(
        Long resourceId,
        String resourceTypeCode,
        String resourceCode,
        String operationCode,
        boolean allowed,
        java.util.List<Long> matchedRoleIds,
        java.util.List<Long> matchedPermissionIds
    ) {}
}
