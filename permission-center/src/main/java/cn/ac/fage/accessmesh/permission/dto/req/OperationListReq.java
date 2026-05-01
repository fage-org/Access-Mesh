package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List operations with optional resourceType filter.
 */
public record OperationListReq(
    String resourceTypeCode,
    String domainCode
) {}
