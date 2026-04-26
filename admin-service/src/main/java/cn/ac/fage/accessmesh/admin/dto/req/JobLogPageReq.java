package cn.ac.fage.accessmesh.admin.dto.req;

/**
 * Query job logs with optional jobId filter.
 */
public record JobLogPageReq(Integer pageNum, Integer pageSize, String sort, Long jobId) {}
