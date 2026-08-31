package cn.ac.fage.accessmesh.access.admin.service.domain;

import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 组织树配置领域服务
 * 封装组织树配置查询核心领域逻辑
 */
public interface OrgTreeConfigDomainService {

    /**
     * 写入组织树配置（bootstrap 固定图专用入口——管理链写入口在 OrgTreeConfigService，
     * 带操作者门禁与变更日志，bootstrap 无登录态不可复用）。
     *
     * @param config 已装配完整字段的配置实体（含 tenantId/isDefault/审计字段）
     * @return 配置ID
     */
    Long insert(SysOrgTreeConfig config);

    /**
     * 查询租户的默认组织树配置列表
     *
     * @param tenantId 租户ID
     * @return 默认配置列表
     */
    List<SysOrgTreeConfig> findDefaultConfigs(Long tenantId);

    /**
     * 解析 orgId 所属的组织树根的 externalId（= rootOrgId 的字符串形式）。
     * <p>
     * 通过 orgId 自身或其祖先链与该租户全部 SysOrgTreeConfig.rootOrgId 集合做交集，
     * 命中即返回对应 rootOrgId.toString()；未命中视为游离 org，禁止参与 user-org / user_role 同步。
     * </p>
     * <p>
     * 增量与全量必须使用同一 resolver；禁止 fallback "1" 或任何静默默认值。
     * </p>
     *
     * @param tenantId 租户ID
     * @param orgId    组织ID
     * @return 该 org 所属树根的 externalId（即 rootOrgId 的字符串形式）
     * @throws cn.ac.fage.accessmesh.common.exception.BizException
     *         ORG_NOT_FOUND（org 不存在或已删除）；
     *         ORG_TREE_ROOT_NOT_RESOLVED（org 不属于任何已配置组织树）
     */
    String resolveTreeRootExternalId(Long tenantId, Long orgId);

    /**
     * 批量解析多个 orgId 所属的组织树根 externalId。一次加载租户全部 SysOrgTreeConfig + 全部相关 SysOrg 路径，
     * 内存内交集匹配，避免循环单条调用导致的 N+1。
     * <p>
     * 任一 orgId 无法解析（org 不存在 / 游离 / 未配置树）→ 抛 BizException(ORG_TREE_ROOT_NOT_RESOLVED)，
     * message 含全部缺失的 orgId 集合，便于排障；不返回部分结果。
     * </p>
     * <p>
     * 入参为空 → 返回空 Map，不调任何 mapper。
     * </p>
     *
     * @param tenantId 租户ID
     * @param orgIds   组织ID集合（去重前后均可，内部 distinct）
     * @return Map&lt;orgId, treeRootExternalId&gt;，size 与去重后入参 size 相等（保证全部命中）
     * @throws cn.ac.fage.accessmesh.common.exception.BizException
     *         ORG_TREE_ROOT_NOT_RESOLVED（任一 orgId 不存在或游离）
     */
    Map<Long, String> resolveTreeRootExternalIds(Long tenantId, Collection<Long> orgIds);
}