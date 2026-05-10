package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * 角色树响应体
 * <p>
 * 返回角色层级树结构，包含角色节点及其子节点列表。
 * 用于角色树查询接口的响应，支持前端树形展示。
 * </p>
 *
 * @param root 根节点，包含完整树结构
 */
public record RoleTreeResp(
    RoleTreeNode root
) {
    /**
     * 角色树节点
     * <p>
     * 表示角色树中的一个节点，包含角色信息和子节点列表。
     * </p>
     *
     * @param id           角色ID
     * @param tenantId     租户ID
     * @param parentId     父角色ID
     * @param roleTypeCode 角色类型编码
     * @param name         角色名称
     * @param externalId   外部标识
     * @param status       角色状态，0=禁用，1=启用
     * @param sortOrder    排序顺序
     * @param children     子节点列表
     */
    public record RoleTreeNode(
        Long id,
        Long tenantId,
        Long parentId,
        String roleTypeCode,
        String name,
        String externalId,
        Integer status,
        Integer sortOrder,
        List<RoleTreeNode> children
    ) {}
}