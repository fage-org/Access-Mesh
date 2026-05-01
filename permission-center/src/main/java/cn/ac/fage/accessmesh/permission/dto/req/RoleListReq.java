package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * List roles with standard pagination.
 */
public record RoleListReq(
    String domainCode,
    String roleTypeCode,
    String keyword,
    Integer pageNum,
    Integer pageSize,
    String sort
) {}
