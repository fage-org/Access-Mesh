package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 抽象角色 full-sync scope。
 *
 * @param sourceService      调用方服务编码
 * @param roleTypeCode       角色类型编码
 * @param treeRootExternalId 树根外部 ID
 */
public record AbstractRoleSyncScope(
        @NotBlank String sourceService,
        @NotBlank String roleTypeCode,
        @NotBlank String treeRootExternalId
) {
}
