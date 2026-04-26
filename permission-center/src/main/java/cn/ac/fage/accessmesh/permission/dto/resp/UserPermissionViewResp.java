package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Permission View: user's effective permissions (grouped by resource).
 */
public record UserPermissionViewResp(
    Long userId,
    String userName,
    Long tenantId,
    List<ResourcePermissionView> resources
) {
    public record ResourcePermissionView(
        Long resourceEntityId,
        String resourceCode,
        String resourceName,
        Integer resourceType,
        List<OperationView> operations,
        List<String> roleNames
    ) {}

    public record OperationView(
        Long operationPermissionId,
        String operationCode,
        String operationName,
        Long conditionId,
        String grantSource
    ) {}
}
