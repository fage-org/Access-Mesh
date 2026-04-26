package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List domain configs filtered by tenantId + optional bizDomainId.
 */
public record DomainConfigListReq(
    @NotNull Long tenantId,
    Long bizDomainId
) {}
