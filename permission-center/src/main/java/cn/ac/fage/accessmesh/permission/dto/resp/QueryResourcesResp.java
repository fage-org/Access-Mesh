package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

public record QueryResourcesResp(
    List<ResourceEntry> items,
    String permissionVersion,
    int cacheTtlSeconds
) {
    public record ResourceEntry(
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String resourceName,
        boolean canGrant,
        List<String> operations,
        List<Long> matchedRoleIds,
        List<Long> matchedPermissionIds,
        List<String> grantSources
    ) {}
}
