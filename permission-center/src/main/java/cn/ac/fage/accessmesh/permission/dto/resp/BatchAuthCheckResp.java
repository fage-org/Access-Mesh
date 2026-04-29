package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

public record BatchAuthCheckResp(
    List<AuthCheckItemResult> results
) {
    public record AuthCheckItemResult(
        String resourceTypeCode,
        String resourceCode,
        String operationCode,
        boolean allowed,
        String reason
    ) {}
}
