package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * List users with standard pagination.
 */
public record UserListReq(
    String subjectTypeCode,
    String domainCode,
    String keyword,
    Integer pageNum,
    Integer pageSize,
    String sort
) {}
