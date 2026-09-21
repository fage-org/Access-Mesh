package cn.ac.fage.accessmesh.access.grant.service.impl;

import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.grant.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.grant.service.PermissionGrantAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.service.PermissionManifestAppService;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import cn.ac.fage.accessmesh.access.role.service.RoleManageAppService;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncScope;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import cn.ac.fage.accessmesh.access.type.dto.req.OperationKeyReq;
import cn.ac.fage.accessmesh.access.type.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.access.type.service.OperationAppService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * T-PERM-072 自动授权物化与共享推导验收（真实 PG/Redis + 真实写入口链路）。
 * <p>
 * 覆盖设计 §6.3.1 全部可检验预期与 §7 触发面：apply-grant-plan 授/撤触发完整重算、
 * 共享来源单源保留/末源回收、条件变体（含 NULL）直传、AUTO/MANUAL 并存、中间事实传播、
 * 资源停用不改变传播/删除收缩、manifest 撤依赖收缩、操作生命周期引用拒绝（20069）、
 * 角色删除级联回收（拍板 A）、INLINE 统一时序回收，以及 M4 提交闸门（新种子未提交时删边）。
 * 引擎对候选行的消费语义由 AutoGrantEngineContractPgIT（078）校准，本类断言物化事实行。
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
class AutoGrantMaterializationPgIT {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 21, 0, 0);
    private static final long VIEW = 2L;
    private static final long READ = 4L;
    private static final long EXPORT = 8L;
    private static int nextType = 1900;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, AutoGrantMaterializationPgIT.class); }
    @Autowired private PermissionGrantAppService grants;
    @Autowired private PermissionManifestAppService manifests;
    @Autowired private ResourceEntitySyncAppService resources;
    @Autowired private OperationAppService operations;
    @Autowired private RoleManageAppService roleManage;
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private TreeWriteLockSupport locks;
    @SpyBean private PermQueryEngine engine;

    @BeforeEach void allowManagement() {
        when(engine.hasPermissionByCode(anyLong(), any(), anyString(), any(), anyString())).thenReturn(true);
        doReturn(java.util.Set.of()).when(engine).getDeniedResourceCodes(any(), any(), any(), any(), any());
        jdbc.execute("INSERT INTO abstract_role(tenant_id,role_type,external_id,name) SELECT 1,6,'bootstrap-admin','admin' WHERE NOT EXISTS (SELECT 1 FROM abstract_role WHERE tenant_id=1 AND role_type=6 AND external_id='bootstrap-admin' AND delete_flag=0)");
    }
    @AfterEach void clear() { AccessRequestContext.clear(); }

    // ========== 授权种子生命周期 → 物化与收缩 ==========

    @Test void shouldMaterializeFromManualSeedAndShrinkWhenSeedRemoved() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        assertThat(autoFacts(f)).containsExactly("b:READ");
        assertThat(autoRow(f, "b", READ)).satisfies(row -> {
            assertThat(row.get("can_grant")).isEqualTo(false);
            assertThat(row.get("depend_on")).isNull();
            assertThat(row.get("scope_all")).isEqualTo(false);
            assertThat(row.get("grant_source")).isEqualTo("AUTO_DEP");
        });
        revoke(f, manualPermId(f, "a", VIEW));
        assertThat(autoFacts(f)).isEmpty();
    }

    @Test void shouldKeepInlineConditionVariantAlongsideNullVariantAndRecycleAfterWithdraw() {
        // MANUAL 种子单形态（manual_direct 唯一）——NULL 与带条件变体经两个种子源并存：
        // a:VIEW/inline-C1 与 c:VIEW 无条件共享边到 b，推导出 b:READ 两个条件变体
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "cb", "c", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", inline("inline-a"));
        assertThat(autoFacts(f)).containsExactly("b:READ#" + inlineConditionId(f, "inline-a"));
        grantTo(f, "c", "VIEW", null);
        assertThat(autoFacts(f)).containsExactlyInAnyOrder("b:READ#" + inlineConditionId(f, "inline-a"), "b:READ");
        // 撤无条件种子：仅移除 NULL 变体；带条件变体与 INLINE 条件行保留（多来源按事实键独立存续）
        revoke(f, manualPermId(f, "c", VIEW, null));
        assertThat(autoFacts(f)).containsExactly("b:READ#" + inlineConditionId(f, "inline-a"));
        assertThat(inlineAlive(f, "inline-a")).isTrue();
        // 撤带条件种子：自动变体消失，INLINE 引用归零同事务回收
        revoke(f, manualPermId(f, "a", VIEW, inlineConditionId(f, "inline-a")));
        assertThat(autoFacts(f)).isEmpty();
        assertThat(inlineAlive(f, "inline-a")).isFalse();
    }

    @Test void shouldKeepDownstreamWhenSharedSourceRemains_thenWithdrawOnLastSource() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "cb", "c", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        grantTo(f, "c", "VIEW", null);
        assertThat(autoFacts(f)).containsExactly("b:READ");
        revoke(f, manualPermId(f, "a", VIEW));
        assertThat(autoFacts(f)).containsExactly("b:READ");
        revoke(f, manualPermId(f, "c", VIEW));
        assertThat(autoFacts(f)).isEmpty();
    }

    @Test void shouldKeepAutoDepAlongsideIndependentManualGrant() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        grantTo(f, "b", "READ", null);
        assertThat(autoFacts(f)).containsExactly("b:READ");
        assertThat(manualFacts(f)).containsExactly("a:VIEW", "b:READ");
        revoke(f, manualPermId(f, "a", VIEW));
        assertThat(autoFacts(f)).isEmpty();
        assertThat(manualFacts(f)).containsExactly("b:READ");
    }

    @Test void shouldPropagateThroughIntermediateFactsAndWithdrawWholeChain() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "bc", "b", "c", "READ", "VIEW"));
        grantTo(f, "a", "VIEW", null);
        assertThat(autoFacts(f)).containsExactlyInAnyOrder("b:READ", "c:VIEW");
        revoke(f, manualPermId(f, "a", VIEW));
        assertThat(autoFacts(f)).isEmpty();
    }

    @Test void shouldNotBorrowUnconditionalVariantFromUntriggeredOperation() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "EXPORT", "READ"));
        grantTo(f, "a", "VIEW", null);
        assertThat(autoFacts(f)).isEmpty();
        grantTo(f, "a", "EXPORT", null);
        assertThat(autoFacts(f)).containsExactly("b:READ");
    }

    // ========== §7 触发面 ==========

    @Test void shouldNotChangePropagationOnDisableAndRestore() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        assertThat(single(f, "DISABLE", "b", 2, 0).applied()).isTrue();
        assertThat(single(f, "DISABLE", "a", 3, 0).applied()).isTrue();
        assertThat(autoFacts(f)).containsExactly("b:READ");
        // 停用期间新增合法种子仍可推导；恢复不重建（行保留、不重复）
        grantTo(f, "c", "VIEW", null);
        assertThat(single(f, "UPSERT", "b", 4, 1).applied()).isTrue();
        assertThat(autoFacts(f)).containsExactly("b:READ");
        revoke(f, manualPermId(f, "a", VIEW));
        assertThat(autoFacts(f)).isEmpty();
    }

    @Test void shouldShrinkAutoDepOnResourceDelete_withoutExtraManifest() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"), dep(f, "bc", "b", "c", "READ", "VIEW"));
        grantTo(f, "a", "VIEW", null);
        assertThat(autoFacts(f)).containsExactlyInAnyOrder("b:READ", "c:VIEW");
        assertThat(single(f, "DELETE", "b", 2, 1).applied()).isTrue();
        assertThat(autoFacts(f)).isEmpty();
        // MANUAL 种子行现状滞留（fail-close 语义，072 只收缩自动结果）
        assertThat(manualFacts(f)).containsExactly("a:VIEW");
    }

    @Test void shouldShrinkAutoDepWhenManifestWithdrawsDependency() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        assertThat(autoFacts(f)).containsExactly("b:READ");
        publishEmpty(f, 2);
        assertThat(autoFacts(f)).isEmpty();
        assertThat(manualFacts(f)).containsExactly("a:VIEW");
    }

    @Test void shouldRejectOperationLifecycleWhileReferenced_thenAllowAfterWithdraw() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", null);
        assertThat(autoFacts(f)).containsExactly("b:READ");
        assertThatThrownBy(() -> operations.updateOperation(1L, new OperationUpdateReq(f.code(), "VIEW", null, 16L, null), 100L))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .hasMessageContaining("不可变更位值或删除");
        assertThatThrownBy(() -> operations.deleteOperations(1L, List.of(new OperationKeyReq(f.code(), "VIEW")), 100L))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .hasMessageContaining("不可变更位值或删除");
        // 清掉全部有效引用（角色种子 + 操作者覆盖行）后删除放行；种子消失令 AUTO_DEP 同事务收缩
        softDeleteGrantRows(f, "VIEW");
        operations.deleteOperations(1L, List.of(new OperationKeyReq(f.code(), "VIEW")), 100L);
        assertThat(autoFacts(f)).isEmpty();
    }

    @Test void shouldRecycleAllGrantRowsAndInlineConditionsOnRoleDeletion() {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        grantTo(f, "a", "VIEW", inline("inline-role"));
        assertThat(autoFacts(f)).containsExactly("b:READ#" + inlineConditionId(f, "inline-role"));
        roleManage.deleteRoles(1L, List.of(f.roleId()), 100L);
        assertThat(autoFacts(f)).isEmpty();
        assertThat(manualFacts(f)).isEmpty();
        assertThat(inlineAlive(f, "inline-role")).isFalse();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM role_resource_permission WHERE abstract_role_id=? AND delete_flag=0",
            Integer.class, f.roleId())).isZero();
    }

    // ========== M4 提交闸门：新种子未提交时删边 ==========

    @Test void shouldWithdrawAutoDepWhenEdgeDeletedAfterUncommittedSeedCommits() throws Exception {
        Fixture f = fixture();
        publish(f, dep(f, "ab", "a", "b", "VIEW", "READ"));
        CountDownLatch grantAtLock = new CountDownLatch(1);
        CountDownLatch withdrawSubmitted = new CountDownLatch(1);
        doAnswer(call -> {
            if ("auto-grant-grant-waiter".equals(Thread.currentThread().getName())) grantAtLock.countDown();
            return call.callRealMethod();
        }).when(locks).lockTreeWrites(1L, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var grantFuture = CompletableFuture.supplyAsync(() -> {
                Thread.currentThread().setName("auto-grant-grant-waiter");
                try {
                    grantTo(f, "a", "VIEW", null);
                    return true;
                } finally { AccessRequestContext.clear(); }
            }, pool);
            assertThat(grantAtLock.await(20, TimeUnit.SECONDS)).as("grant thread reached resource lock").isTrue();
            // 删边事务在锁上排队（授予事务未提交前不可进入）
            var withdrawFuture = CompletableFuture.supplyAsync(() -> {
                try { publishEmpty(f, 2); return true; } finally { AccessRequestContext.clear(); }
            }, pool);
            withdrawSubmitted.countDown();
            assertThat(grantFuture.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(withdrawFuture.get(30, TimeUnit.SECONDS)).isTrue();
        }
        // 授予先提交（旧图物化 b:READ），删边随后获锁提交并重算 → 无来源自动行被回收
        assertThat(autoFacts(f)).isEmpty();
        assertThat(manualFacts(f)).containsExactly("a:VIEW");
    }

    // ========== 夹具与辅助 ==========

    private record Fixture(String source, String code, int type, long roleId, String roleExternal) {}

    private Fixture fixture() {
        int type = nextType++;
        String code = "AG_" + type;
        String source = "autogrant-" + UUID.randomUUID();
        String roleExternal = "ag-role-" + UUID.randomUUID();
        jdbc.update("INSERT INTO service_config(tenant_id,service_code,name) VALUES(1,?,'autogrant')", source);
        jdbc.update("INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name,extra) VALUES(1,'resource_type',?,?,'autogrant',CAST(? AS jsonb))",
            code, type, "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"" + source + "\"}");
        jdbc.update("INSERT INTO operation_permission(tenant_id,resource_type,code,name,binary_bit,inherit_mask) VALUES(1,?,'VIEW','View',2,0),(1,?,'READ','Read',4,0),(1,?,'EXPORT','Export',8,0)", type, type, type);
        long roleId = jdbc.queryForObject("INSERT INTO abstract_role(tenant_id,role_type,external_id,name) VALUES(1,6,?,'ag role') RETURNING id", Long.class, roleExternal);
        // 操作者（bootstrap-admin）类型级 canGrant 覆盖行：单 canonical 位各一行（MANUAL 单位约束）；
        // 100L 绑定 bootstrap-admin 使 checkCanGrant 真实装载操作者授权事实（引擎真实查询）
        long operatorRoleId = jdbc.queryForObject("SELECT id FROM abstract_role WHERE tenant_id=1 AND role_type=6 AND external_id='bootstrap-admin' AND delete_flag=0", Long.class);
        jdbc.update("INSERT INTO user_role(tenant_id,abstract_user_id,target_type,target_id) VALUES (1,100,'ROLE',?) ON CONFLICT DO NOTHING", operatorRoleId);
        for (long bit : new long[] {VIEW, READ, EXPORT}) {
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

    private void publishEmpty(Fixture f, int generation) {
        bind(f);
        // schemaVersion 恒 1；发布代次（publicationGeneration）承载排序
        assertThat(manifests.fullSync(1L, new PermissionManifestReq(1, Integer.toString(generation), Integer.toString(generation), List.of()))
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

    private void revoke(Fixture f, long permissionId) {
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        grants.applyGrantPlan(1L, new ApplyGrantPlanReq(null, "BASIC_ROLE", f.roleExternal(),
            new ApplyGrantPlanReq.GrantPlan(null, null, List.of(permissionId))));
    }

    private long resourceId(Fixture f, String code) {
        return jdbc.queryForObject("SELECT id FROM resource_entity WHERE tenant_id=1 AND resource_type=? AND code=? AND code_type='default' AND delete_flag=0", Long.class, f.type(), code);
    }

    private long manualPermId(Fixture f, String resourceCode, long bit) {
        return jdbc.queryForObject("SELECT id FROM role_resource_permission WHERE abstract_role_id=? AND resource_entity_id=? AND granted_bits=? AND COALESCE(grant_source,'MANUAL')='MANUAL' AND delete_flag=0", Long.class, f.roleId(), resourceId(f, resourceCode), bit);
    }

    private long manualPermId(Fixture f, String resourceCode, long bit, Long conditionId) {
        if (conditionId == null) {
            return jdbc.queryForObject("SELECT id FROM role_resource_permission WHERE abstract_role_id=? AND resource_entity_id=? AND granted_bits=? AND condition_id IS NULL AND COALESCE(grant_source,'MANUAL')='MANUAL' AND delete_flag=0", Long.class, f.roleId(), resourceId(f, resourceCode), bit);
        }
        return jdbc.queryForObject("SELECT id FROM role_resource_permission WHERE abstract_role_id=? AND resource_entity_id=? AND granted_bits=? AND condition_id=? AND COALESCE(grant_source,'MANUAL')='MANUAL' AND delete_flag=0", Long.class, f.roleId(), resourceId(f, resourceCode), bit, conditionId);
    }

    private long inlineConditionId(Fixture f, String tag) {
        return jdbc.queryForObject("SELECT id FROM permission_condition WHERE tenant_id=1 AND name=? AND source='INLINE' AND delete_flag=0", Long.class, tag);
    }

    private boolean inlineAlive(Fixture f, String tag) {
        return jdbc.queryForObject("SELECT count(*) FROM permission_condition WHERE tenant_id=1 AND name=? AND delete_flag=0", Integer.class, tag) > 0;
    }

    private List<String> autoFacts(Fixture f) {
        return facts(f, "AUTO_DEP");
    }

    private List<String> manualFacts(Fixture f) {
        return facts(f, "MANUAL");
    }

    private List<String> facts(Fixture f, String source) {
        var rows = jdbc.queryForList("""
                SELECT r.code AS code, p.granted_bits AS bits, p.condition_id AS cond
                FROM role_resource_permission p JOIN resource_entity r ON r.id = p.resource_entity_id
                WHERE p.tenant_id=1 AND p.abstract_role_id=? AND p.delete_flag=0
                  AND COALESCE(p.grant_source,'MANUAL')=? ORDER BY r.code, p.granted_bits
                """, f.roleId(), source);
        var opNames = Map.of(VIEW, "VIEW", READ, "READ", EXPORT, "EXPORT");
        List<String> result = new ArrayList<>();
        for (var row : rows) {
            String fact = row.get("code") + ":" + opNames.get(((Number) row.get("bits")).longValue());
            Object cond = row.get("cond");
            if (cond != null) fact = fact + "#" + ((Number) cond).longValue();
            result.add(fact);
        }
        return result;
    }

    private Map<String, Object> autoRow(Fixture f, String code, long bit) {
        return jdbc.queryForMap("SELECT * FROM role_resource_permission WHERE abstract_role_id=? AND resource_entity_id=? AND granted_bits=? AND grant_source='AUTO_DEP' AND delete_flag=0", f.roleId(), resourceId(f, code), bit);
    }

    private void softDeleteGrantRows(Fixture f, String operationCode) {
        long bit = switch (operationCode) { case "VIEW" -> VIEW; case "READ" -> READ; case "EXPORT" -> EXPORT; default -> throw new IllegalArgumentException(operationCode); };
        jdbc.update("UPDATE role_resource_permission SET delete_flag=id, deleted_at=now() WHERE tenant_id=1 AND resource_type=? AND granted_bits=? AND delete_flag=0", f.type(), bit);
    }

    private cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp single(Fixture f, String action, String code, int generation, int status) {
        bind(f);
        return resources.sync(1L, new ResourceEntitySyncReq(action, f.code(), code, null, code, null, null, null, null,
            status, null, f.source(), null, null, new SyncVersionRef(AT, (long) generation), Integer.toString(generation)), null);
    }
}
