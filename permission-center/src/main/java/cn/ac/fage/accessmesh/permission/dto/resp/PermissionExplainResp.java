package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

public record PermissionExplainResp(
    String targetType,
    boolean allowed,
    String reason,
    PermissionKey permission,
    List<SourceRole> sourceRoles,
    List<Long> matchedPermissionIds,
    List<RecentChangeResp> recentChanges
) {
    public record PermissionKey(
        String domainCode,
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String operationCode,
        boolean scopeAll
    ) {}

    public record SourceRole(
        String roleTypeCode,
        String roleExternalId,
        String roleName,
        List<String> via
    ) {}
}
