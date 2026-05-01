package cn.ac.fage.accessmesh.perm.common.dto.req;

/**
 * Shared: list operations with optional resourceType filter.
 */
public record OperationListReq(
    String resourceTypeCode
) {}
