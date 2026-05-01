package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * List resources with optional filter and standard pagination.
 */
public record ResourceListReq(
    String resourceTypeCode,
    String domainCode,
    Integer pageNum,
    Integer pageSize,
    String sort
) {}
