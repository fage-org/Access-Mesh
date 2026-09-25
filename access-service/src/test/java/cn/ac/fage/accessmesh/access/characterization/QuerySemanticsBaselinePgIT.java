package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.engine.service.PermissionCheckAppService;
import cn.ac.fage.accessmesh.access.engine.service.PermissionQueryAppService;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.access.engine.dto.AuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.dto.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 查询语义正常基线（T-PERM-081，真实 PostgreSQL + Redis）——X03 差分锚。
 * <p>
 * 固定事实集（{@link R2BaselineFixture} 固定 seed）上，把四族消费面
 * （check／batch-check／范围四态／快照投影）的正常语义输出钉成 golden：
 * T-PERM-089/090 X03 新旧等价差分在同一事实集上重放，逐字段对拍本类期望——
 * 已知错误（PQ-01/06 形态）按新正确预期比较，其余语义必须与本基线一致（设计 §10.2 X03）。
 * </p>
 * <p>
 * 固定口径：全部经 AppService 契约入口（消费面真实线格式）；条件仅 COND_UNSAT（恒不满足，
 * 与运行时钟无关）；ROLE_MUTEX 运行时双删（u_mutex）与范围四态、scopeAll 短路、闭包继承、
 * 快照 scopeAll 展开／实例映射各占独立主体，互不串扰。
 * </p>
 */
@Tag("testcontainers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class QuerySemanticsBaselinePgIT {

    private static final Long TENANT = R2BaselineFixture.TENANT;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, QuerySemanticsBaselinePgIT.class);
    }

    @Autowired private PermissionCheckAppService checkAppService;
    @Autowired private PermissionQueryAppService queryAppService;
    @Autowired private JdbcTemplate jdbc;

    // ===== check 族：单条权限校验（allowed/reason/matched 三维 golden）=====

    @Test
    @DisplayName("check：直接实例授权放行——r1:VIEW 命中 VIEW+UPDATE 双行（位覆盖），r2:VIEW 命中单行")
    void shouldAllowDirectInstanceGrantsWhenUserHoldsInstanceRole() {
        seedBaseline();

        AuthCheckResp r1View = checkOf(R2BaselineFixture.USER_INST,
            R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R1, "VIEW", null);
        assertThat(r1View.allowed()).isTrue();
        assertThat(r1View.matchedRoleIds())
            .as("golden：r1:VIEW 命中角色 A（VIEW 行 + 覆盖 VIEW 的 UPDATE 行）")
            .containsExactly(R2BaselineFixture.ROLE_A);
        assertThat(r1View.matchedPermissionIds())
            .containsExactlyInAnyOrder(R2BaselineFixture.PERM_R1_VIEW, R2BaselineFixture.PERM_R1_UPDATE);

        AuthCheckResp r2View = checkOf(R2BaselineFixture.USER_INST,
            R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R2, "VIEW", null);
        assertThat(r2View.allowed()).isTrue();
        assertThat(r2View.matchedRoleIds()).containsExactly(R2BaselineFixture.ROLE_A);
        assertThat(r2View.matchedPermissionIds()).containsExactly(R2BaselineFixture.PERM_R2_VIEW);
    }

    @Test
    @DisplayName("check：无授权目标拒 NO_PERMISSION（无角色授权行命中）")
    void shouldDenyNoPermissionWhenTargetWithoutGrant() {
        seedBaseline();
        AuthCheckResp resp = checkOf(R2BaselineFixture.USER_INST,
            R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R3, "VIEW", null);
        assertThat(resp.allowed()).isFalse();
        assertThat(resp.reason()).isEqualTo("NO_PERMISSION");
        assertThat(resp.matchedRoleIds()).isEmpty();
        assertThat(resp.matchedPermissionIds()).isEmpty();
    }

    @Test
    @DisplayName("check：inheritMode=PARENT 判定面继承——无直接授权的子目标经父授权闭包放行")
    void shouldAllowThroughAncestorClosureWhenInheritModeParent() {
        seedBaseline();
        AuthCheckResp resp = checkOf(R2BaselineFixture.USER_INST,
            R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R3, "VIEW", "PARENT");
        assertThat(resp.allowed())
            .as("r3 无直接授权；PARENT 模式目标闭包 {r3,r1} 命中 r1 授权 → 放行").isTrue();
        assertThat(resp.matchedRoleIds()).containsExactly(R2BaselineFixture.ROLE_A);
        assertThat(resp.matchedPermissionIds())
            .containsExactlyInAnyOrder(R2BaselineFixture.PERM_R1_VIEW, R2BaselineFixture.PERM_R1_UPDATE);
    }

    @Test
    @DisplayName("check：挂恒不满足条件的授权行 → 拒 CONDITION_NOT_MET_OR_CONFLICT（非 NO_PERMISSION）")
    void shouldDenyConditionNotMetWhenUnsatisfiedCondition() {
        seedBaseline();
        AuthCheckResp resp = checkOf(R2BaselineFixture.USER_INST,
            R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R4, "VIEW", null);
        assertThat(resp.allowed()).isFalse();
        assertThat(resp.reason()).isEqualTo("CONDITION_NOT_MET_OR_CONFLICT");
    }

    @Test
    @DisplayName("check：类型级门禁只认 scopeAll——仅实例授权时类型级拒 NO_PERMISSION")
    void shouldDenyTypeLevelWhenOnlyInstanceGrants() {
        seedBaseline();
        AuthCheckResp resp = checkOf(R2BaselineFixture.USER_INST,
            R2BaselineFixture.TYPE_T1_CODE, null, "VIEW", null);
        assertThat(resp.allowed()).isFalse();
        assertThat(resp.reason()).isEqualTo("NO_PERMISSION");
    }

    @Test
    @DisplayName("check：无角色用户拒 NO_ROLE（fail-closed）")
    void shouldDenyNoRoleWhenUserHasNoRole() {
        seedBaseline();
        AuthCheckResp resp = checkOf(R2BaselineFixture.USER_NONE,
            R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R1, "VIEW", null);
        assertThat(resp.allowed()).isFalse();
        assertThat(resp.reason()).isEqualTo("NO_ROLE");
    }

    @Test
    @DisplayName("check：角色互斥双持 → 两角色同时删除 → 判定集空拒 NO_ROLE（T-PERM-075 运行时双删锚）")
    void shouldDenyNoRoleWhenAllRolesDroppedByRoleMutex() {
        seedBaseline();
        AuthCheckResp resp = checkOf(R2BaselineFixture.USER_MUTEX,
            R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R2, "VIEW", null);
        assertThat(resp.allowed())
            .as("u_mutex 持 X⊥Y 双角色：resolveJudgementRoleIds 双删 → 空集 → NO_ROLE").isFalse();
        assertThat(resp.reason()).isEqualTo("NO_ROLE");
    }

    @Test
    @DisplayName("check：scopeAll 类型级放行——任意实例编码（含不存在的编码）均放行")
    void shouldAllowAnyInstanceOfTypeWhenScopeAll() {
        seedBaseline();
        assertThat(checkOf(R2BaselineFixture.USER_ALL, R2BaselineFixture.TYPE_T2_CODE,
            R2BaselineFixture.CODE_S1, "VIEW", null).allowed()).isTrue();
        assertThat(checkOf(R2BaselineFixture.USER_ALL, R2BaselineFixture.TYPE_T2_CODE,
            "r2b-ghost", "VIEW", null).allowed())
            .as("scopeAll 短路：幽灵编码同样放行").isTrue();
    }

    @Test
    @DisplayName("check：scopeAll 行被条件评估摘光 → 拒 CONDITION_NOT_MET_OR_CONFLICT（实例段无授权兜底）")
    void shouldDenyConditionNotMetWhenScopeAllClearedByCondition() {
        seedBaseline();
        AuthCheckResp resp = checkOf(R2BaselineFixture.USER_EMPTY,
            R2BaselineFixture.TYPE_T3_CODE, "r2b-t3-any", "VIEW", null);
        assertThat(resp.allowed()).isFalse();
        assertThat(resp.reason()).isEqualTo("CONDITION_NOT_MET_OR_CONFLICT");
    }

    // ===== batch-check 族：批量判定（下标对齐 + reason 词表 golden）=====

    @Test
    @DisplayName("batch-check：基线图六形态 item 与输入序对齐（放行/NO_PERMISSION/CNM/幽灵/类型级/UPDATE 放行）")
    void batchCheckShouldAlignOutcomesWithInputOrderOnBaselineGraph() {
        seedBaseline();
        BatchAuthCheckResp resp = checkAppService.batchCheck(TENANT, new BatchAuthCheckReq(
            "USER", String.valueOf(R2BaselineFixture.USER_INST),
            List.of(
                new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R1, "VIEW", null, null, null),
                new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R3, "VIEW", null, null, null),
                new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R4, "VIEW", null, null, null),
                new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, "r2b-ghost", "VIEW", null, null, null),
                new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, null, "VIEW", null, null, null),
                new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R1, "UPDATE", null, null, null)),
            null, null, null, null, Map.of()));

        assertThat(resp.items()).hasSize(6);
        assertItem(resp.items().get(0), true, null, "r1:VIEW 放行");
        assertThat(resp.items().get(0).matchedRoleIds()).containsExactly(R2BaselineFixture.ROLE_A);
        assertItem(resp.items().get(1), false, "NO_PERMISSION", "r3:VIEW 无授权");
        assertItem(resp.items().get(2), false, "CONDITION_NOT_MET_OR_CONFLICT", "r4:VIEW 条件摘光");
        assertItem(resp.items().get(3), false, "NO_PERMISSION", "幽灵编码无投影");
        assertItem(resp.items().get(4), false, "NO_PERMISSION", "类型级仅实例授权");
        assertItem(resp.items().get(5), true, null, "r1:UPDATE 直接命中");
        assertThat(resp.items().get(5).matchedPermissionIds())
            .containsExactly(R2BaselineFixture.PERM_R1_UPDATE);
    }

    @Test
    @DisplayName("batch-check：角色互斥双持主体整批 NO_ROLE，逐 item 对齐")
    void batchCheckShouldDenyAllItemsWithNoRoleWhenAllRolesDroppedByRoleMutex() {
        seedBaseline();
        BatchAuthCheckResp resp = checkAppService.batchCheck(TENANT, new BatchAuthCheckReq(
            "USER", String.valueOf(R2BaselineFixture.USER_MUTEX),
            List.of(
                new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R2, "VIEW", null, null, null),
                new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R2, "UPDATE", null, null, null)),
            null, null, null, null, Map.of()));
        assertThat(resp.items()).hasSize(2);
        resp.items().forEach(item -> {
            assertThat(item.allowed()).isFalse();
            assertThat(item.reason()).isEqualTo("NO_ROLE");
        });
    }

    // ===== 范围四态族：query-scopes（DENIED/EMPTY/ALL/INSTANCE golden）=====

    @Test
    @DisplayName("范围四态：同一父上下文下 VIEW=INSTANCE{r1,r2}／UPDATE=INSTANCE{r1}／DELETE=EMPTY／CREATE=DENIED")
    void scopesShouldRenderFourStatesOnBaselineGraph() {
        seedBaseline();
        QueryScopesResp resp = queryAppService.queryScopes(TENANT, new QueryScopesReq(
            "USER", String.valueOf(R2BaselineFixture.USER_INST),
            R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R1, null, List.of("VIEW"),
            List.of(R2BaselineFixture.TYPE_T1_CODE), List.of("VIEW", "UPDATE", "DELETE", "CREATE"),
            null, null, Map.of()));

        assertThat(resp.reason()).isNull();
        assertThat(resp.matchedParentOperations()).containsExactly("VIEW");
        assertThat(resp.scopeGroups()).hasSize(4);

        QueryScopesResp.ScopeGroup view = resp.scopeGroups().get(0);
        assertThat(view.scopeMode()).isEqualTo(ScopeMode.INSTANCE);
        assertThat(codesOf(view)).containsExactlyInAnyOrder(R2BaselineFixture.CODE_R1, R2BaselineFixture.CODE_R2);

        QueryScopesResp.ScopeGroup update = resp.scopeGroups().get(1);
        assertThat(update.scopeMode()).isEqualTo(ScopeMode.INSTANCE);
        assertThat(codesOf(update)).containsExactly(R2BaselineFixture.CODE_R1);

        assertThat(resp.scopeGroups().get(2).scopeMode())
            .as("DELETE 仅条件行覆盖（评估摘光）→ 有权限无数据").isEqualTo(ScopeMode.EMPTY);
        assertThat(resp.scopeGroups().get(3).scopeMode())
            .as("CREATE 无任何覆盖行 → DENIED").isEqualTo(ScopeMode.DENIED);
    }

    @Test
    @DisplayName("范围四态：scopeAll 存活 → ALL 优先（不展开为实例清单）；同批无授权类型 → DENIED")
    void scopesShouldPreferAllStateWhenScopeAllSurvives() {
        seedBaseline();
        QueryScopesResp resp = queryAppService.queryScopes(TENANT, new QueryScopesReq(
            "USER", String.valueOf(R2BaselineFixture.USER_ALL),
            R2BaselineFixture.TYPE_T2_CODE, R2BaselineFixture.CODE_S1, null, List.of("VIEW"),
            List.of(R2BaselineFixture.TYPE_T2_CODE, R2BaselineFixture.TYPE_T1_CODE), List.of("VIEW"),
            null, null, Map.of()));

        assertThat(resp.reason()).isNull();
        assertThat(resp.matchedParentOperations()).containsExactly("VIEW");
        assertThat(resp.scopeGroups().get(0).scopeMode())
            .as("T2:VIEW scopeAll 存活 → ALL（items 空，业务方不加范围过滤）").isEqualTo(ScopeMode.ALL);
        assertThat(resp.scopeGroups().get(0).items()).isEmpty();
        assertThat(resp.scopeGroups().get(1).scopeMode())
            .as("u_all 在 T1 无授权 → DENIED").isEqualTo(ScopeMode.DENIED);
    }

    @Test
    @DisplayName("范围四态：父资源无任何匹配权限 → 整表拒 NO_PERMISSION，全组 DENIED（scopeAll 组不豁免）")
    void scopesShouldDenyAllGroupsWhenParentHasNoPermission() {
        seedBaseline();
        QueryScopesResp resp = queryAppService.queryScopes(TENANT, new QueryScopesReq(
            "USER", String.valueOf(R2BaselineFixture.USER_ALL),
            R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R1, null, List.of("VIEW"),
            List.of(R2BaselineFixture.TYPE_T2_CODE, R2BaselineFixture.TYPE_T1_CODE), List.of("VIEW"),
            null, null, Map.of()));

        assertThat(resp.reason()).isEqualTo("NO_PERMISSION");
        assertThat(resp.scopeGroups()).hasSize(2);
        resp.scopeGroups().forEach(group -> {
            assertThat(group.scopeMode())
                .as("父判定失败：scopeAll 组同样 DENIED（整表拒绝）").isEqualTo(ScopeMode.DENIED);
            assertThat(group.items()).isEmpty();
        });
    }

    // ===== 快照投影族：interface-snapshot（实例映射/scopeAll 展开/空主体 golden）=====

    @Test
    @DisplayName("快照：实例授权按 serviceCode 映射出精确路由条目；无映射服务空集")
    void snapshotShouldReturnMappedInstanceEntriesForService() {
        seedBaseline();
        InterfaceSnapshotResp svcA = queryAppService.interfaceSnapshot(TENANT, new InterfaceSnapshotReq(
            "USER", String.valueOf(R2BaselineFixture.USER_SNAP), R2BaselineFixture.SVC_INST));
        assertThat(svcA.allowedApis()).hasSize(1);
        InterfaceSnapshotResp.ApiPermissionEntry entry = svcA.allowedApis().get(0);
        assertThat(entry.serviceCode()).isEqualTo(R2BaselineFixture.SVC_INST);
        assertThat(entry.httpMethod()).isEqualTo("POST");
        assertThat(entry.pathPattern()).isEqualTo("/r2b/inst");
        assertThat(entry.hasCondition()).isFalse();
        assertThat(entry.conditionId()).isNull();
        assertThat(entry.conditionRules()).isNull();
        assertThat(entry.scopeMode()).isEqualTo(ScopeMode.INSTANCE);

        InterfaceSnapshotResp svcB = queryAppService.interfaceSnapshot(TENANT, new InterfaceSnapshotReq(
            "USER", String.valueOf(R2BaselineFixture.USER_SNAP), R2BaselineFixture.SVC_ALL));
        assertThat(svcB.allowedApis())
            .as("实例授权仅对已映射路由生效：svc-b 无该实体映射 → 空").isEmpty();
    }

    @Test
    @DisplayName("快照：API scopeAll 展开为该服务全部 enabled 映射（INSTANCE 条目，不输出 ALL 通配）")
    void snapshotShouldExpandScopeAllToAllEnabledMappings() {
        seedBaseline();
        InterfaceSnapshotResp svcB = queryAppService.interfaceSnapshot(TENANT, new InterfaceSnapshotReq(
            "USER", String.valueOf(R2BaselineFixture.USER_SNAP_ALL), R2BaselineFixture.SVC_ALL));
        assertThat(svcB.allowedApis()).hasSize(2);
        assertThat(svcB.allowedApis())
            .allSatisfy(entry -> {
                assertThat(entry.serviceCode()).isEqualTo(R2BaselineFixture.SVC_ALL);
                assertThat(entry.scopeMode()).isEqualTo(ScopeMode.INSTANCE);
                assertThat(entry.hasCondition()).isFalse();
            });
        assertThat(svcB.allowedApis())
            .extracting(InterfaceSnapshotResp.ApiPermissionEntry::pathPattern)
            .containsExactlyInAnyOrder("/r2b/all-1", "/r2b/all-2");

        InterfaceSnapshotResp svcA = queryAppService.interfaceSnapshot(TENANT, new InterfaceSnapshotReq(
            "USER", String.valueOf(R2BaselineFixture.USER_SNAP_ALL), R2BaselineFixture.SVC_INST));
        assertThat(svcA.allowedApis())
            .as("scopeAll 语义=「该服务全部已注册 API」：svc-a 的 1 条映射同样展开").hasSize(1);
    }

    @Test
    @DisplayName("快照：无角色主体与互斥双删主体均为空快照（Gateway 缓存空集靠 TTL/广播）")
    void snapshotShouldBeEmptyWhenNoRoleOrAllRolesMutexDropped() {
        seedBaseline();
        assertThat(queryAppService.interfaceSnapshot(TENANT, new InterfaceSnapshotReq(
            "USER", String.valueOf(R2BaselineFixture.USER_NONE), R2BaselineFixture.SVC_INST)).allowedApis())
            .isEmpty();
        assertThat(queryAppService.interfaceSnapshot(TENANT, new InterfaceSnapshotReq(
            "USER", String.valueOf(R2BaselineFixture.USER_MUTEX), R2BaselineFixture.SVC_INST)).allowedApis())
            .as("u_mutex 双角色被 ROLE_MUTEX 双删 → 有效角色空 → 空快照").isEmpty();
    }

    // ===== 辅助 =====

    private void seedBaseline() {
        new R2BaselineFixture(jdbc).seedBaselineGraph();
    }

    private AuthCheckResp checkOf(long userId, String typeCode, String code, String op, String inheritMode) {
        return checkAppService.check(TENANT, new AuthCheckReq(
            "USER", String.valueOf(userId), typeCode, code, op,
            null, null, inheritMode, null, null, null, null, Map.of()));
    }

    private static void assertItem(BatchAuthCheckResp.AuthCheckItemResult item, boolean allowed,
                                   String reason, String desc) {
        assertThat(item.allowed()).as(desc + " allowed").isEqualTo(allowed);
        assertThat(item.reason()).as(desc + " reason").isEqualTo(reason);
    }

    private static List<String> codesOf(QueryScopesResp.ScopeGroup group) {
        return group.items().stream().map(QueryScopesResp.ScopeItem::resourceCode).toList();
    }
}
