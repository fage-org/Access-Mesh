package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record BizDomainUpdateReq(
    @NotNull Long tenantId,
    @NotNull Long domainId,
    String name,
    String description
) {}
