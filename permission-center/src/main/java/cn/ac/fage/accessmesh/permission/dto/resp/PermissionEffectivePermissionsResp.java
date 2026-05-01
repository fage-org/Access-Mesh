package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Effective permissions response for both USER and ROLE target types (§6.8).
 * Unified item structure — USER view populates sourceRoles; ROLE view leaves them empty.
 */
public record PermissionEffectivePermissionsResp(
    String targetType,
    List<EffectivePermissionItem> items,
    int total,
    int pageNum,
    int pageSize,
    boolean hasNext
) {
    public record EffectivePermissionItem(
        String resourceTypeCode,
        String resourceCode,
        String resourceName,
        String codeType,
        List<String> operationCodes,
        boolean scopeAll,
        List<SourceRole> sourceRoles,
        int sourceRoleCount,
        boolean sourceRolesTruncated,
        List<Long> matchedPermissionIds
    ) {}

    public record SourceRole(
        String roleTypeCode,
        String roleExternalId,
        String roleName,
        List<String> via
    ) {}
}
