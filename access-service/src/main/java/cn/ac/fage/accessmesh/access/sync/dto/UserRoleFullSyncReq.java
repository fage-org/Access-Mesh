package cn.ac.fage.accessmesh.access.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 用户角色全量同步请求。
 * <p>
 * 2026-10-06 逐任务评审 P2：items 允许空清单（@NotEmpty→@NotNull，对齐资源通道
 * ResourceEntityFullSyncReq 先例）——最后一名成员/角色删除后的 FULL 校准路径
 * （scope 内全部解绑）此前被 @NotEmpty 挡死为必 400，手册指引的恢复路径不可执行。
 * </p>
 */
public record UserRoleFullSyncReq(
        @NotNull @Valid UserRoleSyncScope scope,
        @NotNull @Valid List<@NotNull @Valid UserRoleSyncItem> items
) {
}
