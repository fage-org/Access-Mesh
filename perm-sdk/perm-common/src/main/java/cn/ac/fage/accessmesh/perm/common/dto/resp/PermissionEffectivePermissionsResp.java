package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

public record PermissionEffectivePermissionsResp<T>(
    String targetType,
    List<T> items,
    int total,
    int pageNum,
    int pageSize,
    boolean hasNext
) {}
