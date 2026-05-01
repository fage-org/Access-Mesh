package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shared: user's assigned roles from permission-center (stable business keys only).
 */
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
        LocalDateTime validFrom,
        LocalDateTime validTo
    ) {}
}
