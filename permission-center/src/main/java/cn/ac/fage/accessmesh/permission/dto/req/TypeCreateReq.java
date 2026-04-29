package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TypeCreateReq(
    Long bizDomainId,
    @NotBlank String typeKey,
    @NotNull Integer typeValue,
    @NotBlank String name,
    String description,
    Boolean isSystem,
    Integer sortOrder,
    String extra
) {}
