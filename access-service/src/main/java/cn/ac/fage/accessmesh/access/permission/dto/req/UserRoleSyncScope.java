package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户角色 full-sync scope。
 * <p>
 * sourceType 为调用方自有成员关系类型（保留键 SYS_USER_ORG 由入口拒绝，
 * 见 service_config.extra.syncTypes 服务-类型白名单）。
 * </p>
 */
public record UserRoleSyncScope(
        @NotBlank String sourceService,
        @NotBlank String sourceType,
        @NotBlank String roleTypeCode,
        @NotBlank String treeRootExternalId
) {
}
