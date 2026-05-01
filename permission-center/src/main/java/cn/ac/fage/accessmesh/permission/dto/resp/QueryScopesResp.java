package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

public record QueryScopesResp(
    boolean allowed,
    String reason,
    List<String> matchedParentOperations,
    List<Long> parentPermissionIds,
    List<ScopeEntry> items,
    String mergeMode,
    String permissionVersion,
    int cacheTtlSeconds
) {
    public record ScopeEntry(
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String resourceName,
        boolean scopeAll,
        List<String> operations,
        List<String> sources,
        List<Long> matchedRoleIds,
        List<Long> matchedPermissionIds,
        List<Long> dependOnPermissionIds
    ) {}
}
