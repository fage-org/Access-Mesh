package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户角色 full-sync scope。
 * <p>
 * sourceType 固定为 SYS_USER_ORG。
 * </p>
 */
public record UserRoleSyncScope(
        @NotBlank String sourceService,
        @NotBlank String sourceType,
        @NotBlank String roleTypeCode,
        @NotBlank String treeRootExternalId
) {
}
