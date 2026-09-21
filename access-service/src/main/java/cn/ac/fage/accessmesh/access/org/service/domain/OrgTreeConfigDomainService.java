package cn.ac.fage.accessmesh.access.org.service.domain;

import cn.ac.fage.accessmesh.access.org.entity.SysOrgTreeConfig;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织树配置领域服务
 * 封装组织树配置查询核心领域逻辑
 */
public interface OrgTreeConfigDomainService {

    /**
     * 写入组织树配置（bootstrap 固定图专用入口——管理链写入口在 OrgTreeConfigAppService，
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
     * 解析默认组织树的全部组织 ID（根 + 全部后代，含软删排除后的有效节点）。
     * <p>
     * 身份目录边界判定的共享入口（原 UserOrgWriteAppServiceImpl 内联 helper 下沉，
     * T-ORG-002）；无默认配置的租户返回空列表（无身份目录语义）。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 默认树组织 ID 列表；无默认配置时空列表
     */
    List<Long> resolveDefaultTreeOrgIds(Long tenantId);

    /**
     * 计算默认树范围由 oldDefaultOrgIds 收窄为 newDefaultOrgIds 后，
     * 将失去默认树最后归属的用户集合（共享守卫核心，T-ORG-002 U001 拍板：拒绝并提示人数）。
     * <p>
     * 判定：归属在被移除范围（old - new）内的用户中，在 newDefaultOrgIds 内无任何归属者。
     * 组织删除（new = old - {orgId}）、默认配置切树/改根（old/new 为两棵树的子树集合）
     * 与单用户移除（对目标用户等价判定）共用本方法，保证各入口语义一致。
     * 两次批量查询（成员 + 候选用户全部归属），无逐用户 SQL。
     * </p>
     *
     * @param tenantId        租户ID
     * @param oldDefaultOrgIds 变更前的默认树组织 ID 集合
     * @param newDefaultOrgIds 变更后的默认树组织 ID 集合
     * @return 将失去最后归属的用户 ID 集合；无受影响用户时为空集
     */
    Set<Long> findUsersLosingDefaultHome(Long tenantId, Set<Long> oldDefaultOrgIds, Set<Long> newDefaultOrgIds);

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