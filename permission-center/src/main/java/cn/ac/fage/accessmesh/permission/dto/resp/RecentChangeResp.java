package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record RecentChangeResp(
    Long changeLogId,
    String eventType,
    String changeType,
    String impactLevel,
    String message,
    PermissionKey permission,
    SourceRole sourceRole,
    Long operatorId,
    String operatorName,
    String changeReason,
    LocalDateTime createdAt
) {
    public record PermissionKey(
        String domainCode,
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String operationCode,
        Boolean scopeAll
    ) {}

    public record SourceRole(
        String roleTypeCode,
        String roleExternalId,
        String roleName
    ) {}
}
