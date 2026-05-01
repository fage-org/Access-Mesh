package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Get domain config by domainCode + configType.
 */
public record DomainConfigGetReq(
    @NotBlank String domainCode,
    @NotBlank String configType
) {}
