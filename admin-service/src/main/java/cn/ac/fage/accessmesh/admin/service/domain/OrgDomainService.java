package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysOrg;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织领域服务接口
 * <p>
 * 封装组织树的核心领域逻辑，提供层级遍历、批量查询、软删除等操作。
 * 使用 PostgreSQL CTE 递归查询高效处理组织树的层级关系。
 * 所有方法均遵循租户隔离原则，确保多租户数据安全。
 * </p>
 */
public interface OrgDomainService {

    /**
     * 获取指定组织的所有子孙组织ID（不含自身）
     * <p>
     * 使用 PostgreSQL CTE 递归查询向下遍历组织树。
     * 用于级联删除、批量权限计算等场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param orgId    组织ID，作为遍历起点
     * @return 子孙组织ID列表，不含起始组织自身
     */
    List<Long> getDescendantIds(Long tenantId, Long orgId);

    /**
     * 获取指定组织的所有子孙组织ID（含自身）
     * <p>
     * 使用 PostgreSQL CTE 递归查询向下遍历组织树。
     * 用于权限范围计算、数据范围限定等场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param orgId    组织ID，作为遍历起点
     * @return 子孙组织ID列表，包含起始组织自身
     */
    List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long orgId);

    /**
     * 批量获取多个组织的子孙组织ID
     * <p>
     * 对多个组织同时执行子孙查询，返回 ID 到子孙列表的映射。
     * 用于批量操作场景，减少数据库往返次数。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param orgIds   组织ID集合，多个遍历起点
     * @return orgId 到子孙ID列表的映射
     */
    Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> orgIds);

    /**
     * 获取指定组织的所有祖先组织ID
     * <p>
     * 向上遍历父链直到根节点，获取完整的祖先路径。
     * 用于权限继承计算、数据范围向上追溯等场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param orgId    组织ID，作为遍历起点
     * @return 祖先组织ID列表，从父节点到根节点排序
     */
    List<Long> getAncestorIds(Long tenantId, Long orgId);

    /**
     * 批量获取多个组织的祖先组织ID
     * <p>
     * 对多个组织同时执行祖先查询，返回 ID 到祖先列表的映射。
     * 用于批量操作场景，减少数据库往返次数。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param orgIds   组织ID集合，多个遍历起点
     * @return orgId 到祖先ID列表的映射
     */
    Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> orgIds);

    /**
     * 查询有效的组织实体
     * <p>
     * 查询未删除且属于指定租户的组织记录。
     * 用于需要精确验证组织存在性的场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param orgId    组织ID
     * @return 组织实体，不存在或已删除返回 null
     */
    SysOrg selectValidById(Long tenantId, Long orgId);

    /**
     * 批量查询有效的组织实体
     * <p>
     * 批量查询未删除且属于指定租户的组织记录。
     * 用于批量加载组织信息避免 N+1 查询问题。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param orgIds   组织ID集合
     * @return 组织实体列表，不存在的ID会被忽略
     */
    List<SysOrg> selectValidByIds(Long tenantId, Set<Long> orgIds);

    /**
     * 批量软删除组织
     * <p>
     * 将组织的 delete_flag 设置为组织ID，deleted_at 设置为当前时间。
     * 用于批量删除场景，保留数据记录便于审计追溯。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param orgIds   待删除的组织ID列表
     */
    void softDeleteBatch(Long tenantId, List<Long> orgIds);

    /**
     * 删除组织及其所有子孙组织
     * <p>
     * 先查询子孙组织ID，然后批量软删除所有子孙和自身。
     * 用于组织树的整体删除场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param orgId    组织ID，删除该组织及其所有子孙
     */
    void deleteWithChildren(Long tenantId, Long orgId);

    /**
     * 检查组织是否有子组织
     * <p>
     * 查询是否存在以该组织为父节点的子组织。
     * 用于删除前的验证，防止误删有子节点的组织。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param orgId    组织ID
     * @return 有子组织返回 true，无子组织返回 false
     */
    boolean hasChildren(Long tenantId, Long orgId);

    /**
     * 根据组织编码查询组织
     * <p>
     * 通过组织编码查找组织，用于编码唯一性检查和组织定位。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param code     组织编码
     * @return 组织实体，不存在返回 null
     */
    SysOrg findByCode(Long tenantId, String code);

    /**
     * 批量查询已存在的组织编码
     * <p>
     * 从给定的编码集合中筛选出已存在的组织编码。
     * 用于批量创建组织时的唯一性批量校验。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param codes    组织编码集合
     * @return 已存在的组织编码集合
     */
    Set<String> findExistingCodes(Long tenantId, Set<String> codes);

    /**
     * 批量查询组织实体并返回Map形式
     * <p>
     * 批量查询组织实体，返回 ID 到实体的映射便于快速查找。
     * 用于批量加载父组织信息等场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param ids      组织ID集合
     * @return 组织ID 到组织实体的映射
     */
    Map<Long, SysOrg> batchSelectValidByIdsMap(Long tenantId, Set<Long> ids);

    /**
     * 批量插入组织
     * <p>
     * 批量插入多条组织记录，用于组织批量导入场景。
     * </p>
     *
     * @param orgs 组织列表
     */
    void insertBatch(List<SysOrg> orgs);
}