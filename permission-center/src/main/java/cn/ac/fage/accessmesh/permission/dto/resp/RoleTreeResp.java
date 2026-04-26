package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

public record RoleTreeResp(
    RoleTreeNode root
) {
    public record RoleTreeNode(
        Long id,
        Long tenantId,
        Long parentId,
        Integer roleType,
        String name,
        String externalId,
        Integer status,
        Integer sortOrder,
        List<RoleTreeNode> children
    ) {}
}
