package cn.ac.fage.accessmesh.access.application.query.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 组织可见性查询 Mapper（跨域只读，T-ACCESS-006）。
 * <p>
 * 只读 admin 域表（sys_org_tree_config、sys_org），返回基础 ID 列表；
 * 所有查询显式携带 tenant_id 条件。禁止定义或执行任何写 SQL。
 * </p>
 */
public interface OrgVisibilityQueryMapper {

    /**
     * 查询租户全部默认组织树的根组织 ID。
     *
     * @param tenantId 租户 ID
     * @return 根组织 ID 列表（无默认树时为空）
     */
    List<Long> selectDefaultTreeRootOrgIds(@Param("tenantId") Long tenantId);

    /**
     * 查询组织子树全部后代节点 ID（含自身，递归 CTE，向下沿 parent_id）。
     *
     * @param tenantId 租户 ID
     * @param orgId    起点组织 ID
     * @return 后代组织 ID 列表（含起点自身）
     */
    List<Long> selectDescendantOrgIds(@Param("tenantId") Long tenantId, @Param("orgId") Long orgId);
}
