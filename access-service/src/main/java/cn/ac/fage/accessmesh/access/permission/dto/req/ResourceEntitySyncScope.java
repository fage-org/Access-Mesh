package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 资源实体 full-sync scope。
 */
public record ResourceEntitySyncScope(
        @NotBlank String sourceService,
        @NotBlank String resourceTypeCode
) {
}
