package cn.ac.fage.accessmesh.admin.dto.req;

/**
 * Query files with optional bizType filter.
 */
public record FilePageReq(Integer pageNum, Integer pageSize, String sort, String bizType) {}
