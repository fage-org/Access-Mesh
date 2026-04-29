package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RoleGrantReq(
    @NotNull(message = "角色ID不能为空")
    Long abstractRoleId,
    @NotEmpty(message = "授权列表不能为空")
    List<GrantItem> grants
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
