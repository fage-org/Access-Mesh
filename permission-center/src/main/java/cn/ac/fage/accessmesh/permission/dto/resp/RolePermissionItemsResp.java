package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

public record RolePermissionItemsResp(
    List<RolePermissionItemResp> items
) {}
