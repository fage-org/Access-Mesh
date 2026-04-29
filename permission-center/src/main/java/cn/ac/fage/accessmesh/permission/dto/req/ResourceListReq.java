package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List resources with optional filter.
 */
public record ResourceListReq(
    Integer resourceType,
    Integer offset,
    Integer limit
) {}
