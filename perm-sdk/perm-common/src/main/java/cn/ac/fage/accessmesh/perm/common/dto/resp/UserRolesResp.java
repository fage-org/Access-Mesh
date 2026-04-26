package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shared: user's assigned roles from permission-center.
 */
public record UserRolesResp(
    Long abstractUserId,
    List<RoleSummary> roles
) {
    public record RoleSummary(
        Long roleId,
        String roleName,
        Integer roleType,
        String targetType,
        Long relationId,
        LocalDateTime validFrom,
        LocalDateTime validTo
    ) {}
}
