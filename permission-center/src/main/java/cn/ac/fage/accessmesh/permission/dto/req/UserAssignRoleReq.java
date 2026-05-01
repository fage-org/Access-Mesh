package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UserAssignRoleReq(
    @NotEmpty List<@Valid AssignItem> items
) {
    public record AssignItem(
        @NotBlank String subjectTypeCode,
        @NotBlank String subjectExternalId,
        @NotBlank String domainCode,
        @NotBlank String roleTypeCode,
        @NotBlank String roleExternalId,
        Long relationId,
        java.time.LocalDateTime validFrom,
        java.time.LocalDateTime validTo
    ) {}
}
