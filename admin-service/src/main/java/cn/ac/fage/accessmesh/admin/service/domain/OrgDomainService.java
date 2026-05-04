package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysOrg;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织领域服务
 * 封装组织树遍历、批量查询等核心领域逻辑
 */
public interface OrgDomainService {

    /**
     * 获取指定组织的所有子孙组织ID（不包括自身）
     * 使用 PostgreSQL CTE 递归查询
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 子孙组织ID列表
     */
    List<Long> getDescendantIds(Long tenantId, Long orgId);

    /**
     * 获取指定组织的所有子孙组织ID（包括自身）
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 子孙组织ID列表（包含自身）
     */
    List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long orgId);

    /**
     * 批量获取多个组织的子孙ID
     *
     * @param tenantId 租户ID
     * @param orgIds   组织ID集合
     * @return orgId -> 子孙ID列表的映射
     */
    Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> orgIds);

    /**
     * 获取指定组织的所有祖先组织ID
     * 向上遍历父链直到根节点
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 祖先组织ID列表（从父节点到根节点）
     */
    List<Long> getAncestorIds(Long tenantId, Long orgId);

    /**
     * 批量获取多个组织的祖先ID
     *
     * @param tenantId 租户ID
     * @param orgIds   组织ID集合
     * @return orgId -> 祖先ID列表的映射
     */
    Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> orgIds);

    /**
     * 查询有效的组织（未删除、属于指定租户）
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 组织实体，不存在返回null
     */
    SysOrg selectValidById(Long tenantId, Long orgId);

    /**
     * 批量查询有效的组织
     *
     * @param tenantId 租户ID
     * @param orgIds   组织ID集合
     * @return 组织实体列表
     */
    List<SysOrg> selectValidByIds(Long tenantId, Set<Long> orgIds);

    /**
     * 批量软删除组织
     *
     * @param tenantId 租户ID
     * @param orgIds   组织ID列表
     */
    void softDeleteBatch(Long tenantId, List<Long> orgIds);

    /**
     * 删除组织及其所有子孙组织
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     */
    void deleteWithChildren(Long tenantId, Long orgId);

    /**
     * 检查组织是否有子组织
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 是否有子组织
     */
    boolean hasChildren(Long tenantId, Long orgId);

    /**
     * 根据组织编码查询组织
     *
     * @param tenantId 租户ID
     * @param code     组织编码
     * @return 组织实体
     */
    SysOrg findByCode(Long tenantId, String code);
}