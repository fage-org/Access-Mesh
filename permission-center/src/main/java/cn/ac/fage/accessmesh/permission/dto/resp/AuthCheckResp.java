package cn.ac.fage.accessmesh.permission.dto.resp;

public record AuthCheckResp(
    boolean allowed,
    String reason,
    Long resourceEntityId,
    String resourceCode,
    String operationCode,
    Long conditionId,
    java.util.List<DataPermItem> dataPermissions
) {
    public static AuthCheckResp allow(Long resourceEntityId, String resourceCode, String operationCode) {
        return new AuthCheckResp(true, null, resourceEntityId, resourceCode, operationCode, null, java.util.List.of());
    }

    public static AuthCheckResp deny(String reason) {
        return new AuthCheckResp(false, reason, null, null, null, null, java.util.List.of());
    }

    public record DataPermItem(
        Long resourceEntityId,
        String resourceCode,
        String resourceName
    ) {}
}
