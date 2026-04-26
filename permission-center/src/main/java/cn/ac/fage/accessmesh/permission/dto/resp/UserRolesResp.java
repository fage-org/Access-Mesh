package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

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
        java.time.LocalDateTime validFrom,
        java.time.LocalDateTime validTo
    ) {}
}
