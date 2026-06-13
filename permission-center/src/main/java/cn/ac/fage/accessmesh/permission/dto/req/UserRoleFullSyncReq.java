package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 用户角色全量同步请求。
 */
public record UserRoleFullSyncReq(
        @NotNull @Valid UserRoleSyncScope scope,
        @NotEmpty @Valid List<UserRoleSyncItem> items
) {
}
