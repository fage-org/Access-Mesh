package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Batch revoke user-role relations using stable business keys per item.
 */
public record UserRoleBatchRevokeReq(
    @NotEmpty @Valid List<RevokeItem> items
) {
    public record RevokeItem(
        @NotBlank String subjectTypeCode,
        @NotBlank String subjectExternalId,
        @NotBlank String domainCode,
        @NotBlank String roleTypeCode,
        @NotBlank String roleExternalId,
        Long relationId
    ) {}
}
