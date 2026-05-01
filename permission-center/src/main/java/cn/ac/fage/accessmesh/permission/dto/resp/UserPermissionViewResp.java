package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Permission View: user's effective permissions (grouped by resource).
 */
public record UserPermissionViewResp(
    String subjectTypeCode,
    String subjectExternalId,
    String subjectName,
    List<ResourcePermissionView> resources
) {
    public record ResourcePermissionView(
        Long resourceEntityId,
        String domainCode,
        String resourceCode,
        String resourceName,
        String resourceTypeCode,
        String codeType,
        boolean scopeAll,
        List<String> operationCodes,
        List<SourceRoleView> sourceRoles,
        int sourceRoleCount,
        boolean sourceRolesTruncated,
        List<Long> matchedPermissionIds
    ) {}

    public record SourceRoleView(
        String roleTypeCode,
        String roleExternalId,
        String roleName,
        List<String> via
    ) {}
}
