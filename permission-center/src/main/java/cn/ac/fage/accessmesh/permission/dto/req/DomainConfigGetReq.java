package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Get domain config by tenantId + bizDomainId + configType.
 */
public record DomainConfigGetReq(
    @NotNull Long tenantId,
    @NotNull Long bizDomainId,
    @NotNull String configType
) {}
