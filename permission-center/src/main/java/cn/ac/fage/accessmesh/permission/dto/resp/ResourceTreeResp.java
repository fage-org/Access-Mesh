package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

public record ResourceTreeResp(
    ResourceTreeNode root
) {
    public record ResourceTreeNode(
        Long id,
        Long parentId,
        String resourceTypeCode,
        String code,
        String codeType,
        String name,
        String path,
        Integer status,
        Integer sortOrder,
        List<ResourceTreeNode> children
    ) {}
}
