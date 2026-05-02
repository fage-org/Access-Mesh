package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;
import java.util.Set;

/**
 * Response for permission tree query.
 * Returns accessible resources in tree structure from a starting node.
 */
public record PermissionTreeResp(
    TreeNode root,                          // Starting node (may not have permission)
    List<TreeNode> ancestors,               // Parent chain (direction=ANCESTORS/BOTH)
    List<TreeNode> descendants,             // Child tree (direction=DESCENDANTS/BOTH)
    String permissionVersion,
    int cacheTtlSeconds
) {
    /**
     * Tree node representing a resource with permission info.
     * depth: negative for ancestors, positive for descendants, 0 for root.
     */
    public record TreeNode(
        Long resourceId,
        String resourceTypeCode,
        String resourceCode,
        String resourceName,
        int depth,                          // Relative depth from root
        Set<String> operations,             // Operations the subject has on this node
        boolean canGrant,                   // Whether can grant this permission to others
        List<TreeNode> children             // Child nodes (only for descendants tree)
    ) {}
}