package cn.ac.fage.accessmesh.access.application.query;

import java.util.Collection;
import java.util.Set;

/**
 * 组织可见性查询服务（跨域只读，T-ACCESS-006）。
 * <p>
 * 聚合 admin 域（sys_org_tree_config、sys_org 子树）与 permission 域（操作者主体解析、
 * ORG:VIEW 批量判定）数据。权限判定经 PermQueryEngine；数据读取走 query 包
 * 专用 Mapper；只读事务执行，不产生任何写 SQL。
 * </p>
 */
public interface OrgVisibilityQueryService {

    /**
     * 过滤操作者可见的组织 ID（ORG:VIEW 批量判定，未解析投影的组织视为不可见）。
     *
     * @param tenantId   租户 ID
     * @param operatorId 操作者 ID
     * @param orgIds     候选组织 ID 集合（空集合返回空）
     * @return 可见组织 ID 集合（保持候选顺序）
     */
    Set<Long> filterVisibleOrgIds(Long tenantId, Long operatorId, Collection<Long> orgIds);

    /**
     * 获取操作者在默认组织树内的可见组织 ID（含子树，结果按操作者缓存）。
     *
     * @param tenantId   租户 ID
     * @param operatorId 操作者 ID
     * @return 可见组织 ID 集合
     */
    Set<Long> getOperatorVisibleDefaultTreeOrgIds(Long tenantId, Long operatorId);
}
