package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

public record BatchAuthCheckResp(
    List<AuthCheckItemResult> items
) {
    public record AuthCheckItemResult(
        String resourceTypeCode,
        String resourceCode,
        String operationCode,
        boolean allowed,
        String reason,
        List<Long> matchedRoleIds,
        List<Long> matchedPermissionIds
    ) {}
}
