package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shared: user's effective permissions view.
 */
public record UserPermissionViewResp(
    Long userId,
    String userName,
    Long tenantId,
    List<ResourcePermission> resources
) {
    public record ResourcePermission(
        Long resourceEntityId,
        String resourceCode,
        String resourceName,
        Integer resourceType,
        List<Operation> operations,
        List<String> roleNames
    ) {}

    public record Operation(
        Long operationPermissionId,
        String operationCode,
        String operationName,
        Long conditionId,
        String grantSource
    ) {}
}
