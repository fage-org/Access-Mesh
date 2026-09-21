package cn.ac.fage.accessmesh.access.grant.service.impl;

import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.grant.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.grant.dto.req.PreviewGrantPlanReq;
import cn.ac.fage.accessmesh.access.grant.dto.resp.GrantPlanPreviewResp;
import cn.ac.fage.accessmesh.access.grant.service.PermissionGrantAppService;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantReconcileDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.dto.req.AutoGrantExplainReq;
import cn.ac.fage.accessmesh.access.resource.dto.resp.AutoGrantExplainResp;
import cn.ac.fage.accessmesh.access.resource.dto.resp.DependencyDeclarationStatusResp;
import cn.ac.fage.accessmesh.access.resource.service.DependencyAppService;
import cn.ac.fage.accessmesh.access.resource.service.PermissionManifestAppService;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncScope;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.Dependency;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.ResourceKey;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.Requirement;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * T-PERM-073 按需来源解释、授撤影响预览、声明诊断与对账验收（真实 PG/Redis + 真实链路）。
 * <p>
 * 覆盖契约 §12.3.1（explain 共享 DAG/截断/漂移/声明引用/门禁/目标解析）、§11.4.1
 * （preview added/removed/retained + PREVIEW_INLINE 临时身份 + 只读零残留 + 漂移标识 +
 * 保存同源门禁）、§12.3（declaration-status 拒绝原因与发布状态）与设计 §13（对账只读
 * 发现：desired/actual 漂移与声明↔编译边一致）。物化事实行由 072 PgIT 锁定，此处断言
 * 解释/预览/对账输出。
 * </p>
 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false"
})
class AutoGrantInsightPgIT {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 21, 0, 0);
    private static final long VIEW = 2L;
    private static final long READ = 4L;
    private static int nextType = 2100;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, AutoGrantInsightPgIT.class); }
    @Autowired private PermissionGrantAppService grants;
    @Autowired private DependencyAppService dependencies;
    @Autowired private PermissionManifestAppService manifests;
    @Autowired private ResourceEntitySyncAppService resources;
    @Autowired private AutoGrantReconcileDomainService reconcile;
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private PermQueryEngine engine;

    @BeforeEach void allowManagement() {
        when(engine.hasPermissionByCode(anyLong(), any(), anyString(), any(), anyString())).thenReturn(true);
        doReturn(java.util.Set.of()).when(engine).getDeniedResourceCodes(any(), any(), any(), any(), any());
        jdbc.execute("INSERT INTO abstract_role(tenant_id,role_type,external_id,name) SELECT 1,6,'bootstrap-admin','admin' WHERE NOT EXISTS (SELECT 1 FROM abstract_role WHERE tenant_id=1 AND role_type=6 AND external_id='bootstrap-admin' AND delete_flag=0)");
    }
    @AfterEach void clear() { AccessRequestContext.clear(); }

    // ========== explain：共享 DAG ==========

    @Test void shouldExplainFullGraphWithEdgesAndDeclarationRefs() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "bc", "b", "c", "READ", "VIEW"));
        grantTo(f, "a", "VIEW", null);
        AutoGrantExplainResp resp = explain(f, null);
        assertThat(resp.driftDetected()).isFalse();
        assertThat(resp.truncated()).isFalse();
        assertThat(resp.totalNodeCount()).isEqualTo(3L);
        assertThat(resp.nodes()).extracting(AutoGrantExplainResp.Node::nodeKey)
            .containsExactly("n1", "n2", "n3");
        AutoGrantExplainResp.Node seed = findNode(resp, "a", "VIEW");
        assertThat(seed.explicitSeed()).isTrue();
        assertThat(seed.desired()).isFalse();
        assertThat(seed.seedRefs()).hasSize(1);
        assertThat(seed.actualPermissionIds()).isEmpty();
        AutoGrantExplainResp.Node middle = findNode(resp, "b", "READ");
        assertThat(middle.explicitSeed()).isFalse();
        assertThat(middle.desired()).isTrue();
        assertThat(middle.actualPermissionIds()).hasSize(1);
        AutoGrantExplainResp.Node leaf = findNode(resp, "c", "VIEW");
        assertThat(leaf.desired()).isTrue();
        assertThat(resp.edges()).hasSize(2);
        AutoGrantExplainResp.Node bNode = middle;
        assertThat(resp.edges()).anySatisfy(edge -> {
            assertThat(edge.toNodeKey()).isEqualTo(bNode.nodeKey());
            assertThat(edge.fromNodeKey()).isEqualTo(seed.nodeKey());
            assertThat(edge.triggerOperationCode()).isEqualTo("VIEW");
            assertThat(edge.declarationRefs()).hasSize(1);
            assertThat(edge.declarationRefs().get(0).declarationKey()).isEqualTo("ab");
            assertThat(edge.declarationRefs().get(0).sourceService()).isEqualTo(f.source());
        });
    }

    @Test void shouldExcludeNonParticipatingSeedsFromFullView() {
        // 定案 B（2026-09-21）：无 target 全集仅含参与推导的种子——d:VIEW 无任何依赖边，
        // 不进解释视图（防大授权量角色孤点淹没推导链并吃掉 maxNodes 预算）
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        grantTo(f, "d", "VIEW", null);
        AutoGrantExplainResp resp = explain(f, null);
        assertThat(nodeFacts(resp)).containsExactlyInAnyOrder("a:VIEW", "b:READ");
        assertThat(resp.totalNodeCount()).isEqualTo(2L);
    }

    @Test void shouldExplainTargetAsBackwardClosureOnly() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "bc", "b", "c", "READ", "VIEW"));
        grantTo(f, "a", "VIEW", null);
        AutoGrantExplainResp resp = explain(f, target(f, "b", "READ", null));
        assertThat(resp.totalNodeCount()).isEqualTo(2L);
        assertThat(nodeFacts(resp)).containsExactlyInAnyOrder("a:VIEW", "b:READ");
        assertThat(resp.edges()).hasSize(1);
        assertThat(resp.edges().get(0).toNodeKey()).isEqualTo(findNode(resp, "b", "READ").nodeKey());
    }

    @Test void shouldMarkTruncationWithoutLyingAboutTotals() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "bc", "b", "c", "READ", "VIEW"));
        grantTo(f, "a", "VIEW", null);
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        AutoGrantExplainResp resp = dependencies.explainAutoGrant(1L, new AutoGrantExplainReq(
            null, "BASIC_ROLE", f.roleExternal(), null, null, 1, null));
        assertThat(resp.truncated()).isTrue();
        assertThat(resp.totalNodeCount()).isEqualTo(3L);
        assertThat(resp.nodes()).hasSize(1);
        assertThat(resp.edges()).isEmpty();
    }

    @Test void shouldKeepExplainTargetInOutputUnderNodeTruncation() {
        // target 模式目标保底入选（2026-09-21 贴回外评拍板）：maxNodes 不足时名额被祖先
        // 按资源 id 升序占满，旧形态会把查询目标 C 挤出输出（只剩 A）
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "bc", "b", "c", "READ", "VIEW"));
        grantTo(f, "a", "VIEW", null);
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        AutoGrantExplainResp resp = dependencies.explainAutoGrant(1L, new AutoGrantExplainReq(
            null, "BASIC_ROLE", f.roleExternal(), target(f, "c", "VIEW", null), null, 1, null));
        assertThat(resp.truncated()).isTrue();
        assertThat(resp.totalNodeCount()).isEqualTo(3L);
        assertThat(resp.nodes()).hasSize(1);
        assertThat(nodeFacts(resp)).containsExactly("c:VIEW");
    }

    @Test void shouldFlagDriftAndOrphanActualNodes() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        // 漂移①：删掉应有的 AUTO 行（desired 有、actual 无）
        jdbc.update("UPDATE role_resource_permission SET delete_flag=id, deleted_at=now() WHERE tenant_id=1 AND abstract_role_id=? AND grant_source='AUTO_DEP' AND delete_flag=0", f.roleId());
        // 漂移②：直插无来源 AUTO 行（孤立 actual）
        jdbc.update("INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_entity_id,resource_type,granted_bits,scope_all,can_grant,grant_source) VALUES (1,?,?,?,?,false,false,'AUTO_DEP')",
            f.roleId(), resourceId(f, "d"), f.type(), READ);
        AutoGrantExplainResp resp = explain(f, null);
        assertThat(resp.driftDetected()).isTrue();
        AutoGrantExplainResp.Node missing = findNode(resp, "b", "READ");
        assertThat(missing.desired()).isTrue();
        assertThat(missing.actualPermissionIds()).isEmpty();
        AutoGrantExplainResp.Node orphan = findNode(resp, "d", "READ");
        assertThat(orphan.desired()).isFalse();
        assertThat(orphan.explicitSeed()).isFalse();
        assertThat(orphan.actualPermissionIds()).hasSize(1);
    }

    @Test void shouldEnforceExplainGateAndRejectUnknownRole() {
        Fixture f = fixture();
        doReturn(false).when(engine).hasPermissionByCode(anyLong(), any(), eq("DEPENDENCY"), any(), any());
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        assertThatThrownBy(() -> dependencies.explainAutoGrant(1L,
            new AutoGrantExplainReq(null, "BASIC_ROLE", f.roleExternal(), null, null, null, null)))
            .isInstanceOf(SecurityException.class);
        when(engine.hasPermissionByCode(anyLong(), any(), anyString(), any(), anyString())).thenReturn(true);
        assertThatThrownBy(() -> dependencies.explainAutoGrant(1L,
            new AutoGrantExplainReq(null, "BASIC_ROLE", "no-such-role", null, null, null, null)))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .extracting("errorCode").isEqualTo(20001);
    }

    @Test void shouldRejectExplainTargetResolutionFailures() {
        Fixture f = fixture();
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        // 操作码任何类型都不存在 → 20005
        assertThatThrownBy(() -> explain(f, target(f, "a", "NOPE", null)))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .extracting("errorCode").isEqualTo(20005);
        // 资源不存在 → 20004
        assertThatThrownBy(() -> explain(f, target(f, "missing", "VIEW", null)))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .extracting("errorCode").isEqualTo(20004);
        // 条件不存在 → 20006
        assertThatThrownBy(() -> explain(f, target(f, "a", "VIEW", 999999999L)))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .extracting("errorCode").isEqualTo(20006);
    }

    // ========== preview：授撤影响 ==========

    @Test void shouldPreviewRetainedWhenOtherExplicitSourceRemains() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "cb", "c", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        grantTo(f, "c", "VIEW", null);
        GrantPlanPreviewResp resp = previewRemove(f, manualPermId(f, "a", VIEW));
        assertThat(resp.advisory()).isTrue();
        assertThat(resp.driftDetected()).isFalse();
        assertThat(resp.truncated()).isFalse();
        assertThat(factsOf(resp.removed())).isEmpty();
        assertThat(factsOf(resp.added())).isEmpty();
        assertThat(factsOf(resp.retained())).containsExactly("b:READ");
        // 保留事实的存续来源指向另一显式授权 c:VIEW
        GrantPlanPreviewResp.FactElement retained = resp.retained().get(0);
        assertThat(retained.seeds()).hasSize(1);
        assertThat(retained.seeds().get(0).permissionId()).isEqualTo(manualPermId(f, "c", VIEW));
        assertThat(retained.seeds().get(0).requestItemRef()).isNull();
        assertThat(retained.fact().operationCode()).isEqualTo("READ");
        assertThat(retained.fact().conditionRef().kind()).isEqualTo("NONE");
        assertThat(resp.totalCount()).isEqualTo(1L);
    }

    @Test void shouldPreviewRemovedOnLastSourceWithdraw() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        GrantPlanPreviewResp resp = previewRemove(f, manualPermId(f, "a", VIEW));
        assertThat(factsOf(resp.retained())).isEmpty();
        assertThat(factsOf(resp.added())).isEmpty();
        assertThat(factsOf(resp.removed())).containsExactly("b:READ");
        assertThat(resp.removed().get(0).seeds()).hasSize(1);
        assertThat(resp.removed().get(0).seeds().get(0).permissionId())
            .isEqualTo(manualPermId(f, "a", VIEW));
    }

    @Test void shouldReportPerGroupTotalsUnderTruncation() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "bc", "b", "c", "READ", "VIEW"));
        grantTo(f, "a", "VIEW", null);
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        GrantPlanPreviewResp resp = grants.previewGrantPlan(1L, new PreviewGrantPlanReq(
            new ApplyGrantPlanReq(null, "BASIC_ROLE", f.roleExternal(),
                new ApplyGrantPlanReq.GrantPlan(null, null, List.of(manualPermId(f, "a", VIEW)))), 1));
        // removed 全集 {b:READ, c:VIEW} 超预算 1：分组完整数不受截断影响（removedTotal=2 而非输出数 1）
        assertThat(resp.truncated()).isTrue();
        assertThat(resp.totalCount()).isEqualTo(2L);
        assertThat(resp.removedTotal()).isEqualTo(2L);
        assertThat(resp.addedTotal()).isZero();
        assertThat(resp.retainedTotal()).isZero();
        assertThat(resp.removed()).hasSize(1);
        assertThat(resp.added()).isEmpty();
        assertThat(resp.retained()).isEmpty();
    }

    @Test void shouldPreviewAddedFactWithPreviewInlineIdentity() {
        Fixture f = fixture();
        publish(f, dep(f, "db", "d", "b", "VIEW", "READ"));
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        GrantPlanPreviewResp resp = grants.previewGrantPlan(1L, new PreviewGrantPlanReq(
            new ApplyGrantPlanReq(null, "BASIC_ROLE", f.roleExternal(), new ApplyGrantPlanReq.GrantPlan(
                List.of(new ApplyGrantPlanReq.CreateItem(new ApplyGrantPlanReq.GrantRecordKey(
                    f.code(), "d", "default", "VIEW", ScopeMode.INSTANCE, null, inline("pv-inline"), null),
                    null, null)), null, null)), null));
        assertThat(factsOf(resp.added())).containsExactly("b:READ#creates[0]");
        GrantPlanPreviewResp.FactElement added = resp.added().get(0);
        assertThat(added.fact().conditionRef().kind()).isEqualTo("PREVIEW_INLINE");
        assertThat(added.fact().conditionRef().requestItemRef()).isEqualTo("creates[0]");
        assertThat(added.fact().conditionRef().conditionId()).isNull();
        assertThat(added.seeds()).hasSize(1);
        assertThat(added.seeds().get(0).permissionId()).isNull();
        assertThat(added.seeds().get(0).requestItemRef()).isEqualTo("creates[0]");
        // 预览零残留：INLINE 不落库、无权限行变更
        assertThat(conditionCount(f, "pv-inline")).isZero();
    }

    @Test void shouldPreviewWithoutAnyDatabaseWrites() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        long permCount = jdbc.queryForObject("SELECT count(*) FROM role_resource_permission WHERE tenant_id=1 AND abstract_role_id=? AND delete_flag=0", Long.class, f.roleId());
        long condCount = jdbc.queryForObject("SELECT count(*) FROM permission_condition WHERE tenant_id=1 AND delete_flag=0", Long.class);
        previewRemove(f, manualPermId(f, "a", VIEW));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_resource_permission WHERE tenant_id=1 AND abstract_role_id=? AND delete_flag=0", Long.class, f.roleId()))
            .isEqualTo(permCount);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permission_condition WHERE tenant_id=1 AND delete_flag=0", Long.class))
            .isEqualTo(condCount);
    }

    @Test void shouldPreviewFlagExistingDriftSeparatelyFromPlanImpact() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        // 现有漂移：直插无来源 AUTO 行；计划本身零影响（移除不存在的种子→ 空计划拒绝，改用合法零影响计划：
        // 对 b 授 MANUAL（creates）不影响自动事实集合）
        jdbc.update("INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_entity_id,resource_type,granted_bits,scope_all,can_grant,grant_source) VALUES (1,?,?,?,?,false,false,'AUTO_DEP')",
            f.roleId(), resourceId(f, "d"), f.type(), READ);
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        GrantPlanPreviewResp resp = grants.previewGrantPlan(1L, new PreviewGrantPlanReq(
            new ApplyGrantPlanReq(null, "BASIC_ROLE", f.roleExternal(), new ApplyGrantPlanReq.GrantPlan(
                List.of(new ApplyGrantPlanReq.CreateItem(new ApplyGrantPlanReq.GrantRecordKey(
                    f.code(), "b", "default", "READ", ScopeMode.INSTANCE, null, null, null), null, null)),
                null, null)), null));
        assertThat(resp.driftDetected()).isTrue();
        assertThat(resp.totalCount()).isZero();
        assertThat(resp.removed()).isEmpty();
        assertThat(resp.added()).isEmpty();
        assertThat(resp.retained()).isEmpty();
    }

    @Test void shouldEnforcePreviewGateAndDisabledRole() {
        Fixture f = fixture();
        grantTo(f, "a", "VIEW", null);
        long permissionId = manualPermId(f, "a", VIEW);
        doReturn(false).when(engine).hasPermissionByCode(anyLong(), any(), eq("ROLE"), anyString(), eq("MANAGE"));
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        assertThatThrownBy(() -> previewRemove(f, permissionId))
            .isInstanceOf(SecurityException.class);
        when(engine.hasPermissionByCode(anyLong(), any(), anyString(), any(), anyString())).thenReturn(true);
        jdbc.update("UPDATE abstract_role SET status=0 WHERE id=?", f.roleId());
        assertThatThrownBy(() -> previewRemove(f, permissionId))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .extracting("errorCode").isEqualTo(20003);
    }

    // ========== declaration-status：声明诊断 ==========

    @Test void shouldExposeDeclarationStatusWithRejectReasonAndSyncState() {
        Fixture f = fixture();
        bind(f);
        // 一条合法声明 + 一条目标缺失声明（RESOURCE_MISSING → REJECTED，诊断行保留）
        assertThat(manifests.fullSync(1L, new PermissionManifestReq(1, "1", "original", List.of(
                dep(f, "ab", "a", "b", "VIEW", "READ"),
                dep(f, "az", "a", "zzz", "VIEW", "READ"))))
            .detail().failedCount()).isEqualTo(1);
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        DependencyDeclarationStatusResp resp = dependencies.declarationStatus(1L,
            new cn.ac.fage.accessmesh.access.resource.dto.req.DependencyDeclarationStatusReq(f.source()));
        assertThat(resp.manifestSyncs()).hasSize(1);
        assertThat(resp.manifestSyncs().get(0).sourceService()).isEqualTo(f.source());
        assertThat(resp.manifestSyncs().get(0).syncStatus()).isIn("SUCCESS", "PARTIAL");
        assertThat(resp.declarations()).hasSize(2);
        DependencyDeclarationStatusResp.DeclarationItem rejected = resp.declarations().stream()
            .filter(item -> "REJECTED".equals(item.compileStatus())).findFirst().orElseThrow();
        assertThat(rejected.rejectReason()).isEqualTo("RESOURCE_MISSING");
        assertThat(rejected.sourceResourceCode()).isEqualTo("a");
        assertThat(rejected.targetResourceCode()).isEqualTo("zzz");
        assertThat(rejected.requiredOperationCodes()).containsExactly("READ");
        DependencyDeclarationStatusResp.DeclarationItem resolved = resp.declarations().stream()
            .filter(item -> "RESOLVED".equals(item.compileStatus())).findFirst().orElseThrow();
        assertThat(resolved.rejectReason()).isNull();
        assertThat(resolved.targetResourceCode()).isEqualTo("b");
    }

    // ========== reconcile：对账只读发现 ==========

    @Test void shouldReconcileCleanThenReportDriftAndGraphIssues() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        // 干净态：无漂移、声明与图一致
        AutoGrantReconcileDomainService.ReconcileReport clean = reconcile.reconcile(1L);
        assertThat(clean.clean()).as(clean.summary()).isTrue();
        // 漂移：直插无来源 AUTO 行
        jdbc.update("INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_entity_id,resource_type,granted_bits,scope_all,can_grant,grant_source) VALUES (1,?,?,?,?,false,false,'AUTO_DEP')",
            f.roleId(), resourceId(f, "d"), f.type(), READ);
        // 图问题：直改库删编译边（RESOLVED 声明失背书）
        jdbc.update("UPDATE resource_dependency SET delete_flag=id, deleted_at=now() WHERE tenant_id=1 AND owner_service_code=? AND delete_flag=0", f.source());
        AutoGrantReconcileDomainService.ReconcileReport drift = reconcile.reconcile(1L);
        assertThat(drift.clean()).isFalse();
        assertThat(drift.roleDriftDetails()).anySatisfy(detail ->
            assertThat(detail).contains("role " + f.roleId()).contains("stale"));
        assertThat(drift.declarationIssueDetails()).anySatisfy(detail ->
            assertThat(detail).contains("no compiled edge"));
        // 对账只读发现：不修复（漂移行仍在，二次对账仍报）
        AutoGrantReconcileDomainService.ReconcileReport again = reconcile.reconcile(1L);
        assertThat(again.clean()).isFalse();
    }

    @Test void shouldReconcileDetectEdgeBitsBeyondDeclaredUnion() {
        Fixture f = fixture();
        jdbc.update("INSERT INTO operation_permission(tenant_id,resource_type,code,name,binary_bit,inherit_mask) VALUES(1,?,'DELETE','Delete',8,0)", f.type());
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        // 污染编译边：目标位并入未声明的 DELETE（历史缺陷/迁移脏数据形态）
        jdbc.update("UPDATE resource_dependency SET required_operation_bits = required_operation_bits | 8 WHERE tenant_id=1 AND owner_service_code=? AND delete_flag=0", f.source());
        // 角色层补齐污染图的 desired（actual 含 b:DELETE）→ 角色层 clean——声明层聚合比较
        // 是「编译边多授」的唯一探测器（角色层 desired 由同一张图推导，图被污染时恒一致）
        jdbc.update("INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_entity_id,resource_type,granted_bits,scope_all,can_grant,grant_source) VALUES (1,?,?,?,?,false,false,'AUTO_DEP')",
            f.roleId(), resourceId(f, "b"), f.type(), 8L);
        AutoGrantReconcileDomainService.ReconcileReport report = reconcile.reconcile(1L);
        // 本角色角色层 clean（污染图的 desired 与 actual 一致——角色层探测不到多授）；
        // 声明层聚合比较是「编译边多授」的唯一探测器。类共享租户库，其他用例遗留漂移
        // 不属本用例断言面，按角色号收窄
        assertThat(report.roleDriftDetails())
            .noneSatisfy(detail -> assertThat(detail).contains("role " + f.roleId() + " "));
        assertThat(report.declarationIssueDetails()).anySatisfy(detail ->
            assertThat(detail).contains("do not match declared union"));
    }

    @Test void shouldAttributeEdgeDeclarationsToTheirTargetOperation() {
        Fixture f = fixture();
        jdbc.update("INSERT INTO operation_permission(tenant_id,resource_type,code,name,binary_bit,inherit_mask) VALUES(1,?,'DELETE','Delete',8,0)", f.type());
        // 同编译键（a→b@VIEW）两条声明：目标位分别为 READ 与 DELETE——编译器 OR 聚合为一条边、
        // 推导出两个目标事实；每条推导边的声明引用只能挂声明了该目标操作位的原始声明
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "ab2", "a", "b", "VIEW", "DELETE"));
        grantTo(f, "a", "VIEW", null);
        AutoGrantExplainResp resp = explain(f, null);
        AutoGrantExplainResp.Node readNode = findNode(resp, "b", "READ");
        AutoGrantExplainResp.Node deleteNode = findNode(resp, "b", "DELETE");
        assertThat(resp.edges()).hasSize(2);
        assertThat(resp.edges()).anySatisfy(edge -> {
            assertThat(edge.toNodeKey()).isEqualTo(readNode.nodeKey());
            assertThat(edge.declarationRefs())
                .extracting(AutoGrantExplainResp.DeclarationRef::declarationKey)
                .containsExactly("ab");
        });
        assertThat(resp.edges()).anySatisfy(edge -> {
            assertThat(edge.toNodeKey()).isEqualTo(deleteNode.nodeKey());
            assertThat(edge.declarationRefs())
                .extracting(AutoGrantExplainResp.DeclarationRef::declarationKey)
                .containsExactly("ab2");
        });
    }

    @Test void shouldPreviewConditionChangeWithoutCorruptingBeforeSnapshot() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String oldCode = "cond-old-" + tag;
        String newCode = "cond-new-" + tag;
        long condOld = jdbc.queryForObject(
            "INSERT INTO permission_condition(tenant_id,code,name,source,enabled) VALUES(1,?,?,'MANAGED',true) RETURNING id",
            Long.class, oldCode, oldCode);
        long condNew = jdbc.queryForObject(
            "INSERT INTO permission_condition(tenant_id,code,name,source,enabled) VALUES(1,?,?,'MANAGED',true) RETURNING id",
            Long.class, newCode, newCode);
        // 种子 a:VIEW 绑定条件 C1 → AUTO b:READ/C1（同一预览事务内 view 装载与 prepare 再装载
        // 同语句命中 SESSION 一级缓存返回同一批实例——before 快照不可被计划构建污染）
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        grants.applyGrantPlan(1L, new ApplyGrantPlanReq(null, "BASIC_ROLE", f.roleExternal(),
            new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                new ApplyGrantPlanReq.GrantRecordKey(f.code(), "a", "default", "VIEW",
                    ScopeMode.INSTANCE, oldCode, null, null), null, null)), null, null)));
        long permissionId = manualPermId(f, "a", VIEW);

        GrantPlanPreviewResp resp = grants.previewGrantPlan(1L, new PreviewGrantPlanReq(
            new ApplyGrantPlanReq(null, "BASIC_ROLE", f.roleExternal(), new ApplyGrantPlanReq.GrantPlan(
                null, List.of(new ApplyGrantPlanReq.UpdateItem(permissionId, null, newCode, null)), null)), null));

        assertThat(resp.driftDetected()).isFalse();
        assertThat(resp.removed()).hasSize(1);
        assertThat(resp.removed().get(0).fact().operationCode()).isEqualTo("READ");
        assertThat(resp.removed().get(0).fact().conditionRef().conditionId()).isEqualTo(condOld);
        assertThat(resp.added()).hasSize(1);
        assertThat(resp.added().get(0).fact().operationCode()).isEqualTo("READ");
        assertThat(resp.added().get(0).fact().conditionRef().conditionId()).isEqualTo(condNew);
    }

    // ========== 夹具与辅助（沿 072 PgIT 同款） ==========

    private record Fixture(String source, String code, int type, long roleId, String roleExternal) {}

    private Fixture fixture() {
        int type = nextType++;
        String code = "AG_" + type;
        String source = "insight-" + UUID.randomUUID();
        String roleExternal = "in-role-" + UUID.randomUUID();
        jdbc.update("INSERT INTO service_config(tenant_id,service_code,name) VALUES(1,?,'insight')", source);
        jdbc.update("INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name,extra) VALUES(1,'resource_type',?,?,'insight',CAST(? AS jsonb))",
            code, type, "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"" + source + "\"}");
        jdbc.update("INSERT INTO operation_permission(tenant_id,resource_type,code,name,binary_bit,inherit_mask) VALUES(1,?,'VIEW','View',2,0),(1,?,'READ','Read',4,0)", type, type);
        long roleId = jdbc.queryForObject("INSERT INTO abstract_role(tenant_id,role_type,external_id,name) VALUES(1,6,?,'in role') RETURNING id", Long.class, roleExternal);
        long operatorRoleId = jdbc.queryForObject("SELECT id FROM abstract_role WHERE tenant_id=1 AND role_type=6 AND external_id='bootstrap-admin' AND delete_flag=0", Long.class);
        jdbc.update("INSERT INTO user_role(tenant_id,abstract_user_id,target_type,target_id) VALUES (1,100,'ROLE',?) ON CONFLICT DO NOTHING", operatorRoleId);
        for (long bit : new long[] {VIEW, READ}) {
            jdbc.update("INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_entity_id,resource_type,granted_bits,scope_all,can_grant,grant_source) VALUES (1,?,NULL,?,?,true,true,'MANUAL')",
                operatorRoleId, type, bit);
        }
        Fixture f = new Fixture(source, code, type, roleId, roleExternal);
        bind(f);
        var items = List.of("a", "b", "c", "d").stream()
            .map(c -> new ResourceEntitySyncItem(c, null, c, null, null, null, null, 1, null, null, null, new SyncVersionRef(AT, 1L))).toList();
        assertThat(resources.fullSync(1L, new ResourceEntityFullSyncReq(new ResourceEntitySyncScope(source, code), items, "1"), null)
            .detail().appliedCount()).isEqualTo(4);
        return f;
    }

    private void bind(Fixture f) { AccessRequestContext.bind(RequestContext.service(1L, f.source())); }

    private Dependency dep(Fixture f, String key, String from, String to, String trigger, String required) {
        return new Dependency(key, new ResourceKey(f.code(), from, null), List.of(trigger),
            List.of(new Requirement(new ResourceKey(f.code(), to, null), List.of(required))), null);
    }

    private void publish(Fixture f, Dependency... dependencies) {
        bind(f);
        assertThat(manifests.fullSync(1L, new PermissionManifestReq(1, "1", "original", List.of(dependencies)))
            .detail().failedCount()).isZero();
    }

    private ApplyGrantPlanReq.InlineConditionDef inline(String tag) {
        return new ApplyGrantPlanReq.InlineConditionDef(tag, "{\"logic\":\"AND\",\"items\":[]}", null);
    }

    private void grantTo(Fixture f, String resourceCode, String operationCode, ApplyGrantPlanReq.InlineConditionDef inlineCondition) {
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        grants.applyGrantPlan(1L, new ApplyGrantPlanReq(null, "BASIC_ROLE", f.roleExternal(),
            new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                new ApplyGrantPlanReq.GrantRecordKey(f.code(), resourceCode, "default", operationCode,
                    ScopeMode.INSTANCE, null, inlineCondition, null), null, null)), null, null)));
    }

    private GrantPlanPreviewResp previewRemove(Fixture f, long permissionId) {
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        return grants.previewGrantPlan(1L, new PreviewGrantPlanReq(
            new ApplyGrantPlanReq(null, "BASIC_ROLE", f.roleExternal(),
                new ApplyGrantPlanReq.GrantPlan(null, null, List.of(permissionId))), null));
    }

    private AutoGrantExplainResp explain(Fixture f, AutoGrantExplainReq.Target target) {
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        return dependencies.explainAutoGrant(1L, new AutoGrantExplainReq(
            null, "BASIC_ROLE", f.roleExternal(), target, null, null, null));
    }

    private AutoGrantExplainReq.Target target(Fixture f, String resourceCode, String operationCode, Long conditionId) {
        return new AutoGrantExplainReq.Target(f.code(), resourceCode, "default", operationCode, conditionId);
    }

    private AutoGrantExplainResp.Node findNode(AutoGrantExplainResp resp, String resourceCode, String operationCode) {
        return resp.nodes().stream()
            .filter(node -> node.fact().resource().resourceCode().equals(resourceCode)
                && operationCode.equals(node.fact().operationCode()))
            .findFirst().orElseThrow();
    }

    private List<String> nodeFacts(AutoGrantExplainResp resp) {
        return resp.nodes().stream()
            .map(node -> node.fact().resource().resourceCode() + ":" + node.fact().operationCode()).toList();
    }

    private List<String> factsOf(List<GrantPlanPreviewResp.FactElement> elements) {
        return elements.stream()
            .map(element -> element.fact().resource().resourceCode() + ":" + element.fact().operationCode()
                + ("PREVIEW_INLINE".equals(element.fact().conditionRef().kind())
                    ? "#" + element.fact().conditionRef().requestItemRef() : ""))
            .toList();
    }

    private long resourceId(Fixture f, String code) {
        return jdbc.queryForObject("SELECT id FROM resource_entity WHERE tenant_id=1 AND resource_type=? AND code=? AND code_type='default' AND delete_flag=0", Long.class, f.type(), code);
    }

    private long manualPermId(Fixture f, String resourceCode, long bit) {
        return jdbc.queryForObject("SELECT id FROM role_resource_permission WHERE abstract_role_id=? AND resource_entity_id=? AND granted_bits=? AND COALESCE(grant_source,'MANUAL')='MANUAL' AND delete_flag=0", Long.class, f.roleId(), resourceId(f, resourceCode), bit);
    }

    private long conditionCount(Fixture f, String name) {
        return jdbc.queryForObject("SELECT count(*) FROM permission_condition WHERE tenant_id=1 AND name=? AND delete_flag=0", Long.class, name);
    }
}
