package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;
import java.util.Set;

/**
 * 权限树查询响应体
 * <p>
 * 返回可访问资源的树结构，从起始节点向上追溯祖先、向下展开子孙。
 * 用于权限树查询接口的响应。
 * </p>
 *
 * @param root            起始节点（可能没有权限）
 * @param ancestors       祖父节点链（direction=ANCESTORS/BOTH时返回）
 * @param descendants     子孙节点树（direction=DESCENDANTS/BOTH时返回）
 * @param cacheTtlSeconds 缓存有效时间（秒）
 */
public record PermissionTreeResp(
    TreeNode root,
    List<TreeNode> ancestors,
    List<TreeNode> descendants,
    int cacheTtlSeconds
) {
    /**
     * 树节点
     * <p>
     * 表示树结构中的一个资源节点，包含资源和权限信息。
     * depth: 祖父节点为负数，子孙节点为正数，起始节点为0。
     * </p>
     *
     * @param resourceId       资源ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param resourceName     资源名称
     * @param depth            相对深度，祖父为负，子孙为正，起始为0
     * @param operations       主体在此节点拥有的操作权限集合
     * @param canGrant         是否可以将此权限授予他人
     * @param children         子节点列表（仅子孙树包含）
     */
    public record TreeNode(
        Long resourceId,
        String resourceTypeCode,
        String resourceCode,
        String resourceName,
        int depth,
        Set<String> operations,
        boolean canGrant,
        List<TreeNode> children
    ) {}
}