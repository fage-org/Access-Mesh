package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record TypeUpdateReq(
    @NotNull Long typeId,
    Long bizDomainId,
    String name,
    String description,
    Integer sortOrder,
    String extra
) {}
