package cn.ac.fage.accessmesh.access.sync.dto;

import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 用户角色 full-sync 单条 item。
 */
public record UserRoleSyncItem(
        @NotBlank String subjectTypeCode,
        @NotBlank String subjectExternalId,
        @NotBlank String roleTypeCode,
        @NotBlank String roleExternalId,
        @NotBlank String relationKey,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String sourceEntityType,
        String sourceEntityId,
        @NotNull @Valid SyncVersionRef syncVersion
) {
}
