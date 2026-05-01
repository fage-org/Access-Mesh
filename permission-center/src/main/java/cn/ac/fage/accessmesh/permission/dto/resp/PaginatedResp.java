package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Unified paginated response wrapper.
 */
public record PaginatedResp<T>(
    List<T> items,
    long total,
    int pageNum,
    int pageSize,
    boolean hasNext
) {}
