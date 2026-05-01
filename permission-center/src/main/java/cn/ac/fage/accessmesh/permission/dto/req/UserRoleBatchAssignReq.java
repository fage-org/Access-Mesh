package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UserRoleBatchAssignReq(
    @NotEmpty List<String> subjectExternalIds,
    @NotBlank String subjectTypeCode,
    @NotBlank String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    Long relationId
) {}
