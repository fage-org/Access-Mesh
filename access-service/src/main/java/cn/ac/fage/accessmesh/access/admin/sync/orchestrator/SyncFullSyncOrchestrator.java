package cn.ac.fage.accessmesh.access.admin.sync.orchestrator;

import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysSyncTaskMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.access.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskEnvelope;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 全量校准编排器（S6）
 * <p>
 * 按 §2.4 阶段推进规则发起一次全量校准批次：
 * <ol>
 *   <li>检测当前 (tenantId + sourceService) 是否已有未结束 batchKey；存在则抛 {@link BizException}</li>
 *   <li>查询全部业务事实（user / org / userOrg / menu）</li>
 *   <li>按 7 阶段顺序生成 envelope（每 phase 一条任务）</li>
 *   <li>一次 enqueueAll 落库（同事务）</li>
 * </ol>
 * </p>
 * <p>
 * batchKey 格式：{@code sourceService=admin-service&runId={uuid}}（URL percent-encoding 后保持原样）。
 * 阶段闸门由 {@code SysSyncTaskMapper.claimDueTasks} SQL 通过 phase 排序保证。
 * </p>
 */
@Component
public class SyncFullSyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SyncFullSyncOrchestrator.class);

    /** 7 阶段枚举名（与 sys_sync_task.phase 一致） */
    public static final String PHASE_USER_SUBJECT = "USER_SUBJECT";
    public static final String PHASE_USER_RESOURCE = "USER_RESOURCE";
    public static final String PHASE_ORG_RESOURCE = "ORG_RESOURCE";
    public static final String PHASE_ORG_ROLE = "ORG_ROLE";
    public static final String PHASE_USER_ROLE = "USER_ROLE";
    public static final String PHASE_MENU_RESOURCE = "MENU_RESOURCE";
    public static final String PHASE_OTHER_RESOURCE = "OTHER_RESOURCE";

    /** batchKey 前缀，用于 LIKE 查询活跃批次 */
    public static final String BATCH_KEY_PREFIX_TEMPLATE = "sourceService=%s&";

    private static final String SOURCE_SERVICE_DEFAULT = "admin-service";
    private static final String STATUS_ENABLED = "1";

    private final SyncTaskBuilder syncTaskBuilder;
    private final SyncTaskDomainService syncTaskDomainService;
    private final SysSyncTaskMapper sysSyncTaskMapper;
    private final SysUserMapper sysUserMapper;
    private final SysOrgMapper sysOrgMapper;
    private final SysUserOrgMapper sysUserOrgMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysOrgTreeConfigMapper sysOrgTreeConfigMapper;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;

    public SyncFullSyncOrchestrator(SyncTaskBuilder syncTaskBuilder,
                                    SyncTaskDomainService syncTaskDomainService,
                                    SysSyncTaskMapper sysSyncTaskMapper,
                                    SysUserMapper sysUserMapper,
                                    SysOrgMapper sysOrgMapper,
                                    SysUserOrgMapper sysUserOrgMapper,
                                    SysMenuMapper sysMenuMapper,
                                    SysOrgTreeConfigMapper sysOrgTreeConfigMapper,
                                    OrgTreeConfigDomainService orgTreeConfigDomainService) {
        this.syncTaskBuilder = syncTaskBuilder;
        this.syncTaskDomainService = syncTaskDomainService;
        this.sysSyncTaskMapper = sysSyncTaskMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysOrgMapper = sysOrgMapper;
        this.sysUserOrgMapper = sysUserOrgMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysOrgTreeConfigMapper = sysOrgTreeConfigMapper;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
    }

    /**
     * 检查指定租户与 sourceService 是否已有未结束的批次。
     *
     * @param tenantId      租户 ID
     * @param sourceService 来源服务编码（如 admin-service）
     * @return 活跃 batchKey；不存在时返回 {@link Optional#empty()}
     */
    public Optional<String> findActiveBatch(Long tenantId, String sourceService) {
        String prefix = String.format(BATCH_KEY_PREFIX_TEMPLATE, sourceService);
        String batchKey = sysSyncTaskMapper.selectActiveBatchByTenantSource(tenantId, prefix);
        return Optional.ofNullable(batchKey);
    }

    /**
     * 启动一次全量校准批次。
     *
     * @param tenantId      租户 ID
     * @param sourceService 来源服务编码（约定为 admin-service）
     * @param triggeredBy   触发方（cron / manual / username）
     * @return 本次启动的 batchKey
     * @throws BizException 已有活跃批次时抛出（reason 含 {@code FULL_SYNC_BATCH_ACTIVE}）
     */
    @Transactional(rollbackFor = Exception.class)
    public String startFullSyncRun(Long tenantId, String sourceService, String triggeredBy) {
        String svc = sourceService == null ? SOURCE_SERVICE_DEFAULT : sourceService;
        log.info("startFullSyncRun start: tenantId={}, sourceService={}, triggeredBy={}",
            tenantId, svc, triggeredBy);
        Optional<String> active = findActiveBatch(tenantId, svc);
        if (active.isPresent()) {
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "FULL_SYNC_BATCH_ACTIVE: existing batchKey=" + active.get());
        }

        String runId = UUID.randomUUID().toString();
        String batchKey = "sourceService=" + svc + "&runId=" + runId;

        // 1. 加载全部事实
        List<SysUser> users = loadAllActiveUsers(tenantId);
        List<SysOrg> allOrgs = loadAllActiveOrgs(tenantId);
        List<SysUserOrg> bindings = loadAllUserOrgBindings(tenantId);
        List<SysMenu> menus = sysMenuMapper.selectAllValid(tenantId);
        List<SysOrgTreeConfig> trees = sysOrgTreeConfigMapper.selectDefaultConfigs(tenantId);
        // 全量校准必须依赖明确的默认组织树；禁止 fallback "1" 或任何静默默认值。
        // PHASE 4/5 envelope 按 (treeRootExternalId, roleTypeCode) 二维分桶，treeRoot 由 OrgTreeConfigDomainService
        // 批量 resolver 解析每个 binding/org 的实际归属，不再依赖隐式单树假设。
        if (trees == null || trees.isEmpty()) {
            throw new BizException(AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(),
                "FULL_SYNC_NO_DEFAULT_TREE: no default org tree config for tenantId=" + tenantId);
        }

        // 1. 一次性构建 orgTypeByOrgId（POSITION/ORG 二元）
        Map<Long, String> orgTypeByOrgId = new LinkedHashMap<>();
        for (SysOrg org : allOrgs) {
            if (org.getId() == null) continue;
            orgTypeByOrgId.put(org.getId(),
                "POSITION".equalsIgnoreCase(org.getOrgType()) ? "POSITION" : "ORG");
        }

        // 2. 一次性批量解析 (allOrgs.id ∪ bindings.orgId) 的并集 → treeRootByOrgId
        //    任一 orgId 不命中即抛 BizException(ORG_TREE_ROOT_NOT_RESOLVED)，禁止 fallback "1"。
        Set<Long> allOrgIdsForResolve = new LinkedHashSet<>();
        for (SysOrg org : allOrgs) {
            if (org.getId() != null) {
                allOrgIdsForResolve.add(org.getId());
            }
        }
        for (SysUserOrg b : bindings) {
            if (b.getOrgId() != null) {
                allOrgIdsForResolve.add(b.getOrgId());
            }
        }
        Map<Long, String> treeRootByOrgId =
            orgTreeConfigDomainService.resolveTreeRootExternalIds(tenantId, allOrgIdsForResolve);
        // 注意：批量 resolver 对任一 orgId 不命中即抛 BizException，到达此处即保证 allOrgIdsForResolve
        // 中每个 orgId 都有 entry，无需再做 null 校验。

        // 3. 按阶段顺序生成 envelopes
        List<SyncTaskEnvelope> envelopes = new ArrayList<>();
        // PHASE 1 USER_SUBJECT
        envelopes.add(annotateTrigger(
            syncTaskBuilder.userFullSync(tenantId, users, batchKey, PHASE_USER_SUBJECT), triggeredBy));
        // PHASE 2 USER_RESOURCE
        envelopes.add(annotateTrigger(
            syncTaskBuilder.userResourceFullSync(tenantId, users, batchKey, PHASE_USER_RESOURCE), triggeredBy));
        // PHASE 3 ORG_RESOURCE
        envelopes.add(annotateTrigger(
            syncTaskBuilder.orgResourceFullSync(tenantId, allOrgs, batchKey, PHASE_ORG_RESOURCE), triggeredBy));

        // PHASE 4 ORG_ROLE：按 (treeRootExternalId, roleTypeCode) 二维分桶发 envelope（不生成空桶）
        Map<String, List<SysOrg>> orgRoleBuckets = new LinkedHashMap<>();
        Map<String, Map.Entry<String, String>> orgRoleBucketKey = new LinkedHashMap<>();
        for (SysOrg org : allOrgs) {
            String treeRoot = treeRootByOrgId.get(org.getId());
            String roleType = orgTypeByOrgId.get(org.getId());
            String key = treeRoot + "|" + roleType;
            orgRoleBuckets.computeIfAbsent(key, k -> new ArrayList<>()).add(org);
            orgRoleBucketKey.putIfAbsent(key, Map.entry(treeRoot, roleType));
        }
        for (Map.Entry<String, List<SysOrg>> bucket : orgRoleBuckets.entrySet()) {
            String treeRoot = orgRoleBucketKey.get(bucket.getKey()).getKey();
            String roleType = orgRoleBucketKey.get(bucket.getKey()).getValue();
            envelopes.add(annotateTrigger(
                syncTaskBuilder.orgRoleFullSync(tenantId, bucket.getValue(), batchKey, PHASE_ORG_ROLE,
                    roleType, treeRoot), triggeredBy));
        }

        // PHASE 5 USER_ROLE：按 (treeRootExternalId, roleTypeCode) 二维分桶发 envelope（不生成空桶）
        Map<String, List<SysUserOrg>> userRoleBuckets = new LinkedHashMap<>();
        Map<String, Map.Entry<String, String>> userRoleBucketKey = new LinkedHashMap<>();
        for (SysUserOrg b : bindings) {
            String treeRoot = treeRootByOrgId.get(b.getOrgId());
            String roleType = orgTypeByOrgId.get(b.getOrgId());
            if (roleType == null) {
                // binding 指向的 org 不在 allOrgs 中（status!=1 或被软删但 binding 残留）
                throw new BizException(AdminErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode(),
                    "FULL_SYNC_BINDING_ORG_INACTIVE: orgId=" + b.getOrgId());
            }
            String key = treeRoot + "|" + roleType;
            userRoleBuckets.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
            userRoleBucketKey.putIfAbsent(key, Map.entry(treeRoot, roleType));
        }
        for (Map.Entry<String, List<SysUserOrg>> bucket : userRoleBuckets.entrySet()) {
            String treeRoot = userRoleBucketKey.get(bucket.getKey()).getKey();
            String roleType = userRoleBucketKey.get(bucket.getKey()).getValue();
            envelopes.add(annotateTrigger(
                syncTaskBuilder.userOrgFullSync(tenantId, bucket.getValue(), batchKey, PHASE_USER_ROLE,
                    roleType, treeRoot), triggeredBy));
        }
        // PHASE 6 MENU_RESOURCE
        envelopes.add(annotateTrigger(
            syncTaskBuilder.menuFullSync(tenantId, menus, batchKey, PHASE_MENU_RESOURCE), triggeredBy));
        // PHASE 7 OTHER_RESOURCE：暂无生产事实，未来按需扩展 SyncTaskBuilder.otherResourceFullSync 后再入队

        syncTaskDomainService.enqueueAll(tenantId, envelopes);
        log.info("startFullSyncRun: tenantId={}, sourceService={}, batchKey={}, phaseCount={}, triggeredBy={}",
            tenantId, svc, batchKey, envelopes.size(), triggeredBy);
        return batchKey;
    }

    /** 把 triggeredBy 写入 displayAttrs（不可变 envelope，需要重建一份）。 */
    private SyncTaskEnvelope annotateTrigger(SyncTaskEnvelope src, String triggeredBy) {
        if (triggeredBy == null) {
            return src;
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (src.displayAttrs() != null) {
            attrs.putAll(src.displayAttrs());
        }
        attrs.put("triggeredBy", triggeredBy);
        return new SyncTaskEnvelope(
            src.syncAction(),
            src.businessKey(),
            src.batchKey(),
            src.payload(),
            src.payloadVersion(),
            attrs,
            src.syncOccurredAt(),
            src.syncSequenceNo(),
            src.phase(),
            src.messageKey()
        );
    }

    private List<SysUser> loadAllActiveUsers(Long tenantId) {
        return sysUserMapper.selectListByQuery(QueryWrapper.create()
            .where("tenant_id = {0}", tenantId)
            .and("status = {0}", 1)
            .and("delete_flag = 0"));
    }

    private List<SysOrg> loadAllActiveOrgs(Long tenantId) {
        return sysOrgMapper.selectListByQuery(QueryWrapper.create()
            .where("tenant_id = {0}", tenantId)
            .and("status = {0}", 1)
            .and("delete_flag = 0"));
    }

    private List<SysUserOrg> loadAllUserOrgBindings(Long tenantId) {
        return sysUserOrgMapper.selectListByQuery(QueryWrapper.create()
            .where("tenant_id = {0}", tenantId)
            .and("delete_flag = 0"));
    }
}
