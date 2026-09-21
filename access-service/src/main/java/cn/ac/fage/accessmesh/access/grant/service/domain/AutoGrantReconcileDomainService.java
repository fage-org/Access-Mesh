package cn.ac.fage.accessmesh.access.grant.service.domain;

import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation.DependencyEdge;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation.Fact;
import cn.ac.fage.accessmesh.access.infrastructure.util.SqlBatches;
import cn.ac.fage.accessmesh.access.resource.entity.PermissionDependencyDeclaration;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 自动授权对账领域服务（T-PERM-073，设计 §13）。
 * <p>
 * 复用同一共享推导检查三类一致性：①RESOLVED 声明与编译聚合边一一对应（含目标操作位覆盖）；
 * ②资源/声明失效残留（RESOLVED 声明或编译边引用已软删资源）；③逐角色 desired 与 actual
 * AUTO_DEP 差异（批量装载，种子口径/防御层与物化器一致——已删角色不作种子、其滞留 AUTO_DEP
 * 按 desired 恒空计入差异）。
 * </p>
 * <p>
 * 对账只发现异常不修复（正常撤销由主事务保证；自动修复须先定义新写入口的门禁/事务/完整触发
 * 行为）。无缓存直读、不写任何数据；不声明事务——一致读视图由唯一调用方
 * {@link AutoGrantReconcileJob} 持 {@code REPEATABLE_READ} 只读事务提供；只读扫描租户内
 * 持有授权行的角色全集。</p>
 */
@Service
public class AutoGrantReconcileDomainService {

    private static final Logger log = LoggerFactory.getLogger(AutoGrantReconcileDomainService.class);
    /** 对账角色批大小（rows 批量装载 + 有效角色过滤批查询） */
    private static final int ROLE_BATCH_SIZE = 200;

    private final RoleResourcePermissionMapper rolePermissionMapper;
    private final AutoGrantDerivation derivation;
    private final AutoGrantInsightDomainService insight;
    private final SubjectDomainService subjectDomainService;
    private final ResourceEntityDomainService resourceEntities;

    public AutoGrantReconcileDomainService(RoleResourcePermissionMapper rolePermissionMapper,
            AutoGrantDerivation derivation, AutoGrantInsightDomainService insight,
            SubjectDomainService subjectDomainService, ResourceEntityDomainService resourceEntities) {
        this.rolePermissionMapper = rolePermissionMapper;
        this.derivation = derivation;
        this.insight = insight;
        this.subjectDomainService = subjectDomainService;
        this.resourceEntities = resourceEntities;
    }

    /** 对账报告：差异明细（日志回传）+ 汇总（任务执行日志成功消息）。 */
    public record ReconcileReport(int scannedRoles, List<String> roleDriftDetails,
                                  List<String> declarationIssueDetails) {

        public boolean clean() {
            return roleDriftDetails.isEmpty() && declarationIssueDetails.isEmpty();
        }

        public String summary() {
            return clean()
                ? "auto-grant reconcile ok: " + scannedRoles + " role(s) consistent, declarations consistent"
                : "auto-grant reconcile found issues: " + roleDriftDetails.size()
                    + " role drift(s), " + declarationIssueDetails.size() + " declaration/edge issue(s) across "
                    + scannedRoles + " role(s)";
        }
    }

    public ReconcileReport reconcile(Long tenantId) {
        AutoGrantInsightDomainService.TenantGraph graph = insight.loadTenantGraph(tenantId);
        List<String> declarationIssues = checkDeclarationsAndGraph(graph);
        declarationIssues.addAll(checkStaleResourceReferences(tenantId, graph));
        ScanResult roleScan = checkRoleDrifts(tenantId, graph);
        if (!roleScan.drifts().isEmpty() || !declarationIssues.isEmpty()) {
            log.warn("auto-grant reconcile tenant {} found {} role drift(s) and {} declaration issue(s)",
                tenantId, roleScan.drifts().size(), declarationIssues.size());
        }
        return new ReconcileReport(roleScan.scannedRoles(), roleScan.drifts(), declarationIssues);
    }

    /** ①声明↔编译边一致：RESOLVED 声明必有对应编译键的边且目标位被覆盖；边必有 RESOLVED 声明背书。 */
    private List<String> checkDeclarationsAndGraph(AutoGrantInsightDomainService.TenantGraph graph) {
        Map<String, DependencyEdge> edgesByCompileKey = new HashMap<>();
        for (DependencyEdge edge : graph.edges()) {
            edgesByCompileKey.put(AutoGrantInsightDomainService.compileKey(
                edge.sourceId(), edge.targetId(), edge.sourceOperationBits()), edge);
        }
        Map<String, List<PermissionDependencyDeclaration>> resolvedByKey = new HashMap<>();
        for (PermissionDependencyDeclaration declaration : graph.declarations()) {
            if (!PermissionDependencyDeclaration.COMPILE_STATUS_RESOLVED.equals(declaration.getCompileStatus())
                || declaration.getSourceResourceId() == null || declaration.getTargetResourceId() == null) {
                continue;
            }
            resolvedByKey.computeIfAbsent(AutoGrantInsightDomainService.compileKey(
                    declaration.getSourceResourceId(), declaration.getTargetResourceId(),
                    declaration.getSourceOperationBits()),
                key -> new ArrayList<>()).add(declaration);
        }
        List<String> issues = new ArrayList<>();
        for (Map.Entry<String, List<PermissionDependencyDeclaration>> entry : resolvedByKey.entrySet()) {
            DependencyEdge edge = edgesByCompileKey.get(entry.getKey());
            for (PermissionDependencyDeclaration declaration : entry.getValue()) {
                if (edge == null) {
                    issues.add("declaration " + declaration.getId() + " (" + declaration.getDeclarationKey()
                        + ", service=" + declaration.getSourceService()
                        + ") is RESOLVED but has no compiled edge");
                } else if ((edge.requiredOperationBits() & declaration.getRequiredOperationBits())
                        != declaration.getRequiredOperationBits()) {
                    issues.add("declaration " + declaration.getId() + " (" + declaration.getDeclarationKey()
                        + ", service=" + declaration.getSourceService()
                        + ") requires operations not covered by its compiled edge");
                }
            }
        }
        for (String compileKey : edgesByCompileKey.keySet()) {
            if (!resolvedByKey.containsKey(compileKey)) {
                issues.add("compiled edge " + compileKey + " has no RESOLVED declaration backing");
            }
        }
        return issues;
    }

    /** ②失效残留：RESOLVED 声明与编译边引用的资源必须仍是有效行。 */
    private List<String> checkStaleResourceReferences(Long tenantId,
            AutoGrantInsightDomainService.TenantGraph graph) {
        Set<Long> referenced = new LinkedHashSet<>();
        for (PermissionDependencyDeclaration declaration : graph.declarations()) {
            if (PermissionDependencyDeclaration.COMPILE_STATUS_RESOLVED.equals(declaration.getCompileStatus())) {
                if (declaration.getSourceResourceId() != null) referenced.add(declaration.getSourceResourceId());
                if (declaration.getTargetResourceId() != null) referenced.add(declaration.getTargetResourceId());
            }
        }
        for (DependencyEdge edge : graph.edges()) {
            referenced.add(edge.sourceId());
            referenced.add(edge.targetId());
        }
        Set<Long> validIds = new HashSet<>();
        List<Long> referencedList = new ArrayList<>(referenced);
        SqlBatches.forEach(referencedList, batch -> {
            for (ResourceEntity entity : resourceEntities.selectValidByIds(tenantId, new LinkedHashSet<>(batch))) {
                validIds.add(entity.getId());
            }
        });
        List<String> issues = new ArrayList<>();
        for (PermissionDependencyDeclaration declaration : graph.declarations()) {
            if (!PermissionDependencyDeclaration.COMPILE_STATUS_RESOLVED.equals(declaration.getCompileStatus())) continue;
            if ((declaration.getSourceResourceId() != null && !validIds.contains(declaration.getSourceResourceId()))
                || (declaration.getTargetResourceId() != null && !validIds.contains(declaration.getTargetResourceId()))) {
                issues.add("declaration " + declaration.getId() + " (" + declaration.getDeclarationKey()
                    + ", service=" + declaration.getSourceService()
                    + ") is RESOLVED but references a soft-deleted resource");
            }
        }
        for (DependencyEdge edge : graph.edges()) {
            if (!validIds.contains(edge.sourceId()) || !validIds.contains(edge.targetId())) {
                issues.add("compiled edge " + edge.sourceId() + "->" + edge.targetId()
                    + " references a soft-deleted resource");
            }
        }
        return issues;
    }

    /** ③desired vs actual：批式扫描持有授权行的角色全集（防御层口径与物化器一致）。
     *  返回值=本次扫描的角色数（与角色 ID 全量查询共用一次结果，避免重复 DISTINCT 扫描）。 */
    private ScanResult checkRoleDrifts(Long tenantId, AutoGrantInsightDomainService.TenantGraph graph) {
        List<Long> roleIds = new ArrayList<>(new TreeSet<>(rolePermissionMapper.selectValidRoleIds(tenantId)));
        List<String> drifts = new ArrayList<>();
        for (int offset = 0; offset < roleIds.size(); offset += ROLE_BATCH_SIZE) {
            List<Long> batch = roleIds.subList(offset, Math.min(offset + ROLE_BATCH_SIZE, roleIds.size()));
            Set<Long> validRoleIds = subjectDomainService.selectValidRolesByIds(tenantId, new LinkedHashSet<>(batch))
                .stream().map(role -> role.getId()).collect(java.util.stream.Collectors.toSet());
            List<RoleResourcePermission> rows = rolePermissionMapper
                .selectValidByRoleIds(tenantId, new LinkedHashSet<>(batch));
            Map<Long, List<RoleResourcePermission>> rowsByRole = new HashMap<>();
            for (RoleResourcePermission row : rows) {
                rowsByRole.computeIfAbsent(row.getAbstractRoleId(), key -> new ArrayList<>()).add(row);
            }
            for (Long roleId : batch) {
                collectRoleDrift(roleId, validRoleIds.contains(roleId),
                    rowsByRole.getOrDefault(roleId, List.of()), graph, drifts);
            }
        }
        return new ScanResult(roleIds.size(), drifts);
    }

    /** 扫描结果：角色数 + 差异明细（复用一次全量角色查询，countScannedRoles 二次扫描已退役）。 */
    private record ScanResult(int scannedRoles, List<String> drifts) {}

    private void collectRoleDrift(Long roleId, boolean roleValid,
            List<RoleResourcePermission> rows, AutoGrantInsightDomainService.TenantGraph graph,
            List<String> drifts) {
        // 种子口径与物化器一致（§6.1 + 防御层）：已删角色不作种子（desired 恒空回收滞留行）
        List<Fact> seedFacts = rows.stream()
            .filter(AutoGrantInsightDomainService::isSeed)
            .filter(row -> roleValid)
            .map(AutoGrantInsightDomainService::factOf).toList();
        Set<Fact> actual = new TreeSet<>();
        for (RoleResourcePermission row : rows) {
            if (AutoGrantInsightDomainService.isAutoDep(row)) {
                actual.add(AutoGrantInsightDomainService.factOf(row));
            }
        }
        Set<Fact> desired = new TreeSet<>(derivation.derive(seedFacts,
            graph.resourceTypeByEntity(), graph.edges(), graph.operations()).desiredFacts());
        List<Fact> missing = desired.stream().filter(fact -> !actual.contains(fact)).toList();
        List<Fact> stale = actual.stream().filter(fact -> !desired.contains(fact)).toList();
        if (!missing.isEmpty()) {
            drifts.add("role " + roleId + " missing " + missing.size() + " desired AUTO_DEP fact(s): "
                + previewFacts(missing));
        }
        if (!stale.isEmpty()) {
            drifts.add("role " + roleId + " has " + stale.size() + " stale AUTO_DEP fact(s) with no source: "
                + previewFacts(stale));
        }
    }

    /** 差异事实预览（每类至多前 5 条，防日志膨胀；完整事实经 explain 端点核查）。 */
    private static String previewFacts(List<Fact> facts) {
        return facts.stream().limit(5).map(fact -> fact.resourceEntityId() + "#"
            + fact.operationBit() + (fact.conditionId() == null ? "" : "/" + fact.conditionId())).toList()
            + (facts.size() > 5 ? "...(+" + (facts.size() - 5) + " more)" : "");
    }
}
