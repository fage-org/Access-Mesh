package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Shared: batch grant permissions to a role.
 */
public record RoleGrantReq(
    @NotNull(message = "租户ID不能为空") Long tenantId,
    @NotNull(message = "角色ID不能为空") Long abstractRoleId,
    @NotEmpty(message = "授权列表不能为空") List<GrantItem> grants
) {
    public record GrantItem(
        @NotNull Long resourceEntityId,
        @NotNull Long operationPermissionId,
        Integer resourceType,
        Long dependOn,
        Boolean canManage,
        Long conditionId
    ) {}
}
