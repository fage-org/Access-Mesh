package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Shared: update a resource in permission-center.
 */
public record ResourceUpdateReq(
    @NotNull Long id,
    @NotNull Long tenantId,
    String code,
    String name,
    String path,
    Integer status,
    Integer sortOrder,
    String extra
) {}
