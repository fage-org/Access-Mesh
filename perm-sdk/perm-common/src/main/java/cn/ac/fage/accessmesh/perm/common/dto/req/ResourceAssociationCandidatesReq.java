package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResourceAssociationCandidatesReq(
    @NotNull @Valid ResourceKeyReq parentResource,
    @NotBlank String resourceTypeCode,
    @Min(1) Integer pageNum,
    @Min(1) @Max(200) Integer pageSize
) {}
