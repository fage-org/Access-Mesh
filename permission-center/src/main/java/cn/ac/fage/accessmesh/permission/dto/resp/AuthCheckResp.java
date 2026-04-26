package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

public record AuthCheckResp(
    boolean allowed,
    String reason,
    Long resourceEntityId,
    String resourceCode,
    String operationCode,
    Long conditionId,
    List<DataPermItem> dataPermissions
) {
    public static AuthCheckResp allow(Long resourceEntityId, String resourceCode, String operationCode) {
        return new AuthCheckResp(true, null, resourceEntityId, resourceCode, operationCode, null, List.of());
    }

    public static AuthCheckResp deny(String reason) {
        return new AuthCheckResp(false, reason, null, null, null, null, List.of());
    }

    public record DataPermItem(
        Long resourceEntityId,
        String resourceCode,
        String resourceName
    ) {}
}
