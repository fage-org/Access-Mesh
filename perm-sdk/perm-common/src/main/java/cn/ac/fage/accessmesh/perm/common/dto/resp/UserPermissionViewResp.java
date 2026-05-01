package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * Shared: user's effective permissions view.
 */
public record UserPermissionViewResp(
    Long resourceEntityId,
    String domainCode,
    String resourceCode,
    String resourceName,
    String resourceTypeCode,
    String codeType,
    boolean scopeAll,
    List<String> operationCodes,
    List<SourceRole> sourceRoles,
    int sourceRoleCount,
    boolean sourceRolesTruncated,
    List<Long> matchedPermissionIds
) {
    public record SourceRole(
        String roleTypeCode,
        String roleExternalId,
        String roleName,
        List<String> via
    ) {}
}
