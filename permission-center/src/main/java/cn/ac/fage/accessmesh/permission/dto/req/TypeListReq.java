package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List types filtered by tenantId + optional bizDomainId.
 */
public record TypeListReq(
    @NotNull Long tenantId,
    Long bizDomainId
) {}
