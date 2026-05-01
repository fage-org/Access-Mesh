package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Unified list response object.
 */
public record ItemsResp<T>(
    List<T> items
) {}
