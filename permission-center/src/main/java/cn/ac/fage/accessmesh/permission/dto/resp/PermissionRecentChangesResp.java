package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

public record PermissionRecentChangesResp(
    List<RecentChangeResp> items,
    int total,
    int pageNum,
    int pageSize,
    boolean hasNext
) {}
