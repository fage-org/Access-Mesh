package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * 资源树响应体
 * <p>
 * 返回资源层级树结构，包含资源节点及其子节点列表。
 * 用于资源树查询接口的响应，支持前端树形展示。
 * </p>
 *
 * @param root 根节点，包含完整树结构
 */
public record ResourceTreeResp(
    ResourceTreeNode root
) {
    /**
     * 资源树节点
     * <p>
     * 表示资源树中的一个节点，包含资源信息和子节点列表。
     * </p>
     *
     * @param id               资源ID
     * @param parentId         父资源ID
     * @param resourceTypeCode 资源类型编码
     * @param code             资源编码
     * @param codeType         编码类型
     * @param name             资源名称
     * @param path             资源路径
     * @param status           资源状态，0=禁用，1=启用
     * @param sortOrder        排序顺序
     * @param children         子节点列表
     */
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