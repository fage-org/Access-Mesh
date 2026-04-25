package cn.ac.fage.accessmesh.common.model;

import java.util.List;

public record PaginatedResult<T>(
    List<T> items,
    PaginationMeta pagination
) {
    public record PaginationMeta(long total, int page, int size, int totalPages) {}
}
