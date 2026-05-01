package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

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
