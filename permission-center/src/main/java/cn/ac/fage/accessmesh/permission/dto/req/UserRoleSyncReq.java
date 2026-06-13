package cn.ac.fage.accessmesh.permission.dto.req;

import cn.ac.fage.accessmesh.permission.dto.common.SyncVersionRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 用户角色绑定/解绑同步请求
 * <p>
 * 详见 docs/design/permission-center/api-contract.md §6.2.2.3。
 * </p>
 */
public record UserRoleSyncReq(
        @NotBlank String operation,
        @NotBlank String sourceType,
        @NotBlank String subjectTypeCode,
        @NotBlank String subjectExternalId,
        @NotBlank String roleTypeCode,
        @NotBlank String treeRootExternalId,
        @NotBlank String roleExternalId,
        @NotBlank String relationKey,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        @NotBlank String sourceService,
        String sourceEntityType,
        String sourceEntityId,
        @NotNull @Valid SyncVersionRef syncVersion
) {
}
