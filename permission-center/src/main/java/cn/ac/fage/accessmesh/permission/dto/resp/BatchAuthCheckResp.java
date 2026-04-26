package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

public record BatchAuthCheckResp(
    Long tenantId,
    Long abstractUserId,
    List<AuthCheckItemResult> results
) {
    public record AuthCheckItemResult(
        Long resourceEntityId,
        Long operationPermissionId,
        boolean allowed,
        String reason
    ) {}
}
