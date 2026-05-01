package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

public record UserRolesResp(
    String subjectTypeCode,
    String subjectExternalId,
    List<RoleSummary> roles
) {
    public record RoleSummary(
        String roleExternalId,
        String roleName,
        String roleTypeCode,
        String targetType,
        Long relationId,
        java.time.LocalDateTime validFrom,
        java.time.LocalDateTime validTo
    ) {}
}
