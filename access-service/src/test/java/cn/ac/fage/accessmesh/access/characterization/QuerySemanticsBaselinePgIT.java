package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.engine.service.PermissionCheckAppService;
import cn.ac.fage.accessmesh.access.engine.service.PermissionQueryAppService;
import cn.ac.fage.accessmesh.access.engine.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.grant.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.access.engine.dto.AuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.dto.BatchAuthCheckResp;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 查询语义正常基线（T-PERM-081，真实 PostgreSQL + Redis）——X03 差分锚。
 * <p>
 * 固定事实集（{@link R2BaselineFixture} 固定 seed）上，把六族消费面
 * （check／batch-check／范围四态／快照投影／视图权限串与资源访问事实／转授资格）的
 * 正常语义输出钉成 golden：T-PERM-089/090/091 X03 新旧等价差分在同一事实集上重放，
 * 逐字段对拍本类期望——已知错误（PQ-01/06 形态）按新正确预期比较，其余语义必须与
 * 本基线一致（设计 §10.2 X03）。
 * </p>
 * <p>
 * 固定口径：全部经 AppService/DomainService 契约入口（消费面真实线格式）；条件仅 COND_UNSAT
 * （恒不满足，与运行时钟无关）；ROLE_MUTEX 运行时双删（u_mutex）与范围四态、scopeAll 短路、
 * 闭包继承、快照 scopeAll 展开／实例映射各占独立主体，互不串扰。
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
    "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
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
    @Autowired private PermissionViewAppService permissionViewAppService;
    @Autowired private PermissionGrantDomainService grantDomainService;
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
    @DisplayName("check：挂恒不满足条件的授权行 → 拒 CONDITION_NOT_MET（非 NO_PERMISSION）")
    void shouldDenyConditionNotMetWhenUnsatisfiedCondition() {
        seedBaseline();
        AuthCheckResp resp = checkOf(R2BaselineFixture.USER_INST,
            R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R4, "VIEW", null);
        assertThat(resp.allowed()).isFalse();
        assertThat(resp.reason()).isEqualTo("CONDITION_NOT_MET");
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
    @DisplayName("check：scopeAll 行被条件评估摘光 → 拒 CONDITION_NOT_MET（实例段无授权兜底）")
    void shouldDenyConditionNotMetWhenScopeAllClearedByCondition() {
        seedBaseline();
        AuthCheckResp resp = checkOf(R2BaselineFixture.USER_EMPTY,
            R2BaselineFixture.TYPE_T3_CODE, "r2b-t3-any", "VIEW", null);
        assertThat(resp.allowed()).isFalse();
        assertThat(resp.reason()).isEqualTo("CONDITION_NOT_MET");
    }

    // ===== batch-check 族：批量判定（下标对齐 + reason 词表 golden）=====

    @Test
    @DisplayName("batch-check：基线图六形态 item 与输入序对齐（放行/NO_PERMISSION/CNM/幽灵/类型级/UPDATE 放行）")
    void batchCheckShouldAlignOutcomesWithInputOrderOnBaselineGraph() {
        seedBaseline();
        List<BatchAuthCheckReq.AuthCheckItem> items = List.of(
            new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R1, "VIEW", null, null, null),
            new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R3, "VIEW", null, null, null),
            new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R4, "VIEW", null, null, null),
            new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, "r2b-ghost", "VIEW", null, null, null),
            new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, null, "VIEW", null, null, null),
            new BatchAuthCheckReq.AuthCheckItem(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R1, "UPDATE", null, null, null));
        BatchAuthCheckResp resp = checkAppService.batchCheck(TENANT, new BatchAuthCheckReq(
            "USER", String.valueOf(R2BaselineFixture.USER_INST), items,
            null, null, null, null, Map.of()));

        assertThat(resp.items()).hasSize(6);
        assertItem(resp.items().get(0), items.get(0), true, null, "r1:VIEW 放行");
        assertThat(resp.items().get(0).matchedRoleIds()).containsExactly(R2BaselineFixture.ROLE_A);
        assertItem(resp.items().get(1), items.get(1), false, "NO_PERMISSION", "r3:VIEW 无授权");
        assertItem(resp.items().get(2), items.get(2), false, "CONDITION_NOT_MET", "r4:VIEW 条件摘光");
        assertItem(resp.items().get(3), items.get(3), false, "NO_PERMISSION", "幽灵编码无投影");
        assertItem(resp.items().get(4), items.get(4), false, "NO_PERMISSION", "类型级仅实例授权");
        assertItem(resp.items().get(5), items.get(5), true, null, "r1:UPDATE 直接命中");
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
        assertGroupKey(view, R2BaselineFixture.TYPE_T1_CODE, "VIEW");
        assertThat(view.scopeMode()).isEqualTo(ScopeMode.INSTANCE);
        assertThat(codesOf(view)).containsExactlyInAnyOrder(R2BaselineFixture.CODE_R1, R2BaselineFixture.CODE_R2);

        QueryScopesResp.ScopeGroup update = resp.scopeGroups().get(1);
        assertGroupKey(update, R2BaselineFixture.TYPE_T1_CODE, "UPDATE");
        assertThat(update.scopeMode()).isEqualTo(ScopeMode.INSTANCE);
        assertThat(codesOf(update)).containsExactly(R2BaselineFixture.CODE_R1);

        assertGroupKey(resp.scopeGroups().get(2), R2BaselineFixture.TYPE_T1_CODE, "DELETE");
        assertThat(resp.scopeGroups().get(2).scopeMode())
            .as("DELETE 仅条件行覆盖（评估摘光）→ 有权限无数据").isEqualTo(ScopeMode.EMPTY);
        assertGroupKey(resp.scopeGroups().get(3), R2BaselineFixture.TYPE_T1_CODE, "CREATE");
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
        assertGroupKey(resp.scopeGroups().get(0), R2BaselineFixture.TYPE_T2_CODE, "VIEW");
        assertThat(resp.scopeGroups().get(0).scopeMode())
            .as("T2:VIEW scopeAll 存活 → ALL（items 空，业务方不加范围过滤）").isEqualTo(ScopeMode.ALL);
        assertThat(resp.scopeGroups().get(0).items()).isEmpty();
        assertGroupKey(resp.scopeGroups().get(1), R2BaselineFixture.TYPE_T1_CODE, "VIEW");
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
        assertGroupKey(resp.scopeGroups().get(0), R2BaselineFixture.TYPE_T2_CODE, "VIEW");
        assertGroupKey(resp.scopeGroups().get(1), R2BaselineFixture.TYPE_T1_CODE, "VIEW");
        resp.scopeGroups().forEach(group -> {
            assertThat(group.scopeMode())
                .as("父判定失败：scopeAll 组同样 DENIED（整表拒绝）").isEqualTo(ScopeMode.DENIED);
            assertThat(group.items()).isEmpty();
        });
    }

    // ===== 视图族（T-PERM-091）：登录权限串 + 菜单派生资源访问事实 golden =====

    @Test
    @DisplayName("视图：权限码全量聚合——位覆盖展开进码、条件摘除不进码、白名单按类型过滤")
    void viewShouldAggregateEffectivePermissionCodesWithCoverageAndConditionSemantics() {
        seedBaseline();

        assertThat(permissionViewAppService.getEffectivePermissionCodes(TENANT, viewReq(
                R2BaselineFixture.USER_INST, List.of(R2BaselineFixture.TYPE_T1_CODE))).permissions())
            .as("golden：r1 VIEW 行 + r1 UPDATE 行（覆盖 VIEW）+ r2 VIEW 行 → VIEW/UPDATE 两码；"
                + "r4 DELETE 行挂 COND_UNSAT 被评估摘除 → DELETE 不进码；CREATE 无授权不进码")
            .containsExactlyInAnyOrder(R2BaselineFixture.TYPE_T1_CODE + ":VIEW",
                R2BaselineFixture.TYPE_T1_CODE + ":UPDATE");

        assertThat(permissionViewAppService.getEffectivePermissionCodes(TENANT, viewReq(
                R2BaselineFixture.USER_ALL, List.of(R2BaselineFixture.TYPE_T1_CODE))).permissions())
            .as("白名单外类型（T2 scopeAll 行）不进权限码").isEmpty();

        assertThat(permissionViewAppService.getEffectivePermissionCodes(TENANT, viewReq(
                R2BaselineFixture.USER_ALL, List.of(R2BaselineFixture.TYPE_T2_CODE))).permissions())
            .containsExactly(R2BaselineFixture.TYPE_T2_CODE + ":VIEW");

        assertThat(permissionViewAppService.getEffectivePermissionCodes(TENANT, viewReq(
                R2BaselineFixture.USER_NONE, List.of(R2BaselineFixture.TYPE_T1_CODE))).permissions())
            .as("无角色主体 → 空权限串").isEmpty();

        assertThat(permissionViewAppService.getEffectivePermissionCodes(TENANT, viewReq(
                R2BaselineFixture.USER_MUTEX, List.of(R2BaselineFixture.TYPE_T1_CODE))).permissions())
            .as("互斥双删主体 → 空权限串").isEmpty();
    }

    @Test
    @DisplayName("视图：资源访问事实——instanceIdsByType 不含子孙扩展、可见集经子孙扩展、条件摘除行不进实例集")
    void viewShouldProjectResourceAccessWithUnexpandedInstanceIdsAndExpandedVisibility() {
        seedBaseline();

        PermissionViewAppService.EffectiveResourceAccess access = permissionViewAppService
            .getEffectiveResourceAccess(TENANT, viewReq(
                R2BaselineFixture.USER_INST, List.of(R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.TYPE_T2_CODE)));

        assertThat(access.allScopeTypes()).as("role-a 无 scopeAll 行").isEmpty();
        assertThat(access.instanceIdsByType())
            .as("golden：直接实例授权行按类型分组且不含子孙扩展——r1/r2 两实例（r4 条件行被评估摘除），"
                + "r3 是 r1 的子资源但不出现（类型页「任意有效操作」语义，验收第 2 条）")
            .containsOnlyKeys(R2BaselineFixture.TYPE_T1);
        assertThat(access.instanceIdsByType().get(R2BaselineFixture.TYPE_T1))
            .containsExactlyInAnyOrder(R2BaselineFixture.RES_R1, R2BaselineFixture.RES_R2);
        assertThat(access.resourceEntityIds())
            .as("可见集=实例集∪子孙扩展：r1 的后代 {r2, r3} 并入（读过滤面继承，r4 条件行不在场）")
            .containsExactlyInAnyOrder(R2BaselineFixture.RES_R1, R2BaselineFixture.RES_R2, R2BaselineFixture.RES_R3);

        PermissionViewAppService.EffectiveResourceAccess allAccess = permissionViewAppService
            .getEffectiveResourceAccess(TENANT, viewReq(
                R2BaselineFixture.USER_ALL, List.of(R2BaselineFixture.TYPE_T2_CODE)));
        assertThat(allAccess.allScopeTypes()).containsExactly(R2BaselineFixture.TYPE_T2);
        assertThat(allAccess.resourceEntityIds()).as("scopeAll 不展开业务实例").isEmpty();
        assertThat(allAccess.instanceIdsByType()).isEmpty();
    }

    // ===== 转授族（T-PERM-091）：checkCanGrant 迁新 execute 后的 DB 直查/同行资格/不自动扩大 =====
    // 每用例独立自定义类型（951+ 段）＋显式授权根行（异角色 scopeAll 可转授覆盖行）——
    // 既是 T-PERM-062 reason 细分的触发面隔离（自定义类型零授权根会改判
    // TYPE_GRANT_ORIGIN_MISSING），也消除类库共享下用例间顺序耦合。

    @Test
    @DisplayName("转授 T01：授权撤销后清单 DB 直查——快照缓存被预热仍必须看到撤销（DATABASE 读来源）")
    void canGrantShouldReadRevokedRowFromDatabaseDespiteWarmRoleSnapshot() {
        seedBaseline();
        R2BaselineFixture fixture = new R2BaselineFixture(jdbc);
        String typeCode = "R2BDLG1";
        fixture.newType(951, typeCode);
        fixture.insertOperation(951, "VIEW", 2L, 0L);
        long resource = fixture.insertResourceRow(951, "dlg1-r");
        long originRole = fixture.insertRoleRow(TENANT, "dlg1-origin");
        long role = fixture.insertRoleRow(TENANT, "dlg1-op");
        long user = fixture.insertUserWithRoles(TENANT, "dlg1-op", role);
        // 授权根行（异角色 scopeAll 可转授 VIEW）：保持 reason=NO_PERMISSION 不被 T-PERM-062 细分改判
        fixture.insertGrantablePermRow(originRole, 951, null, 2L, true);
        long perm = fixture.insertGrantablePermRow(role, 951, resource, 2L, false);

        // 预热 ROLE_PERM_SNAPSHOT（视图面走 ROLE_SNAPSHOT 来源，把含该行的角色快照写入缓存）
        assertThat(permissionViewAppService.getEffectivePermissionCodes(TENANT, viewReq(
                user, List.of(typeCode))).permissions())
            .contains(typeCode + ":VIEW");
        // 直接软删（不经写入口=@PermissionChange 不触发失效，模拟 TTL 陈旧窗口）
        jdbc.update("UPDATE role_resource_permission SET delete_flag = id, deleted_at = now() WHERE id = ?", perm);

        Map<PermissionGrantDomainService.GrantCheckKey, PermissionGrantDomainService.GrantCheckResult> results = grantDomainService.checkCanGrant(
            TENANT, user, Set.of(new PermissionGrantDomainService.GrantCheckKey(
                typeCode, "dlg1-r", null, "VIEW", false)), null);

        assertThat(results.values().iterator().next().canGrant())
            .as("DATABASE 读来源直查不读不回填快照：撤销行不得经预热缓存放行转授资格").isFalse();
        assertThat(results.values().iterator().next().reason()).isEqualTo("NO_PERMISSION");
    }

    @Test
    @DisplayName("转授 T02：可覆盖行不可转授＋另行可转授不覆盖——不拼接两行资格（同行验证）")
    void canGrantShouldNotCombineCoverageRowWithGrantRightRow() {
        seedBaseline();
        R2BaselineFixture fixture = new R2BaselineFixture(jdbc);
        String typeCode = "R2BDLG2";
        fixture.newType(952, typeCode);
        fixture.insertOperation(952, "VIEW", 2L, 0L);
        fixture.insertOperation(952, "UPDATE", 4L, 2L);
        fixture.insertOperation(952, "CREATE", 1L, 0L);
        long resource = fixture.insertResourceRow(952, "dlg2-r");
        long originRole = fixture.insertRoleRow(TENANT, "dlg2-origin");
        long role = fixture.insertRoleRow(TENANT, "dlg2-op");
        long user = fixture.insertUserWithRoles(TENANT, "dlg2-op", role);
        fixture.insertGrantablePermRow(originRole, 952, null, 2L, true);
        // 行 A：UPDATE（inheritMask 覆盖 VIEW）canGrant=false；行 B：CREATE（不覆盖 VIEW）canGrant=true
        jdbc.update("INSERT INTO role_resource_permission "
            + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, can_grant, grant_source) "
            + "VALUES (?, ?, ?, ?, ?, false, false, 'MANUAL')",
            TENANT, role, resource, 4L, 952);
        fixture.insertGrantablePermRow(role, 952, resource, 1L, false);

        Map<PermissionGrantDomainService.GrantCheckKey, PermissionGrantDomainService.GrantCheckResult> results = grantDomainService.checkCanGrant(
            TENANT, user, Set.of(new PermissionGrantDomainService.GrantCheckKey(
                typeCode, "dlg2-r", null, "VIEW", false)), null);

        assertThat(results.values().iterator().next().reason())
            .as("目标 VIEW 的转授资格只认同一真实授权行：覆盖行 canGrant=false、canGrant 行不覆盖 → NO_GRANT_RIGHT")
            .isEqualTo("NO_GRANT_RIGHT");
    }

    @Test
    @DisplayName("转授 T04：运行时祖先可用（判定面闭包可放行子目标）但转授不自动扩大")
    void canGrantShouldNotExtendDelegationThroughRuntimeAncestor() {
        seedBaseline();
        R2BaselineFixture fixture = new R2BaselineFixture(jdbc);
        String typeCode = "R2BDLG3";
        fixture.newType(953, typeCode);
        fixture.insertOperation(953, "VIEW", 2L, 0L);
        long parent = fixture.insertResourceRow(953, "dlg3-parent");
        long child = fixture.insertResourceRow(953, "dlg3-child");
        jdbc.update("UPDATE resource_entity SET parent_id = ? WHERE id = ?", parent, child);
        long originRole = fixture.insertRoleRow(TENANT, "dlg3-origin");
        long role = fixture.insertRoleRow(TENANT, "dlg3-op");
        long user = fixture.insertUserWithRoles(TENANT, "dlg3-op", role);
        fixture.insertGrantablePermRow(originRole, 953, null, 2L, true);
        // 父资源上可转授 VIEW 行；目标为子资源（运行时 INSTANCE 判定可经闭包放行）
        fixture.insertGrantablePermRow(role, 953, parent, 2L, false);
        assertThat(checkAppService.check(TENANT, new AuthCheckReq(
            "USER", String.valueOf(user), typeCode, "dlg3-child",
            "VIEW", null, null, "PARENT", null, null, null, null, Map.of())).allowed())
            .as("前置事实核：判定面继承（PARENT）下父授权覆盖子目标——运行时祖先可用成立").isTrue();

        Map<PermissionGrantDomainService.GrantCheckKey, PermissionGrantDomainService.GrantCheckResult> results = grantDomainService.checkCanGrant(
            TENANT, user, Set.of(new PermissionGrantDomainService.GrantCheckKey(
                typeCode, "dlg3-child", null, "VIEW", false)), null);

        assertThat(results.values().iterator().next().reason())
            .as("转授按精确实例键判定，不消费祖先闭包——父资源可转授不扩大到子资源").isEqualTo("NO_PERMISSION");
    }

    @Test
    @DisplayName("转授：互斥双删主体整批 NO_ROLE（无有效角色不产生转授资格）")
    void canGrantShouldReturnNoRoleWhenAllRolesMutexDropped() {
        seedBaseline();
        Map<PermissionGrantDomainService.GrantCheckKey, PermissionGrantDomainService.GrantCheckResult> results = grantDomainService.checkCanGrant(
            TENANT, R2BaselineFixture.USER_MUTEX, Set.of(new PermissionGrantDomainService.GrantCheckKey(
                R2BaselineFixture.TYPE_T1_CODE, R2BaselineFixture.CODE_R2, null, "VIEW", false)), null);

        assertThat(results.values().iterator().next().reason())
            .as("u_mutex 双角色被 ROLE_MUTEX 双删 → 引擎 User 主体解析为空 → NO_ROLE").isEqualTo("NO_ROLE");
    }

    // ===== 辅助 =====

    private UserEffectivePermissionCodesReq viewReq(long userId, List<String> typeCodes) {
        return new UserEffectivePermissionCodesReq("USER", String.valueOf(userId), typeCodes);
    }

    private void seedBaseline() {
        new R2BaselineFixture(jdbc).seedBaselineGraph();
    }

    private AuthCheckResp checkOf(long userId, String typeCode, String code, String op, String inheritMode) {
        return checkAppService.check(TENANT, new AuthCheckReq(
            "USER", String.valueOf(userId), typeCode, code, op,
            null, null, inheritMode, null, null, null, null, Map.of()));
    }

    /** 判定值 + 回显键（类型/编码/操作）逐项锁定——判定值相同而错标目标时回显键断言变红。 */
    private static void assertItem(BatchAuthCheckResp.AuthCheckItemResult item,
                                   BatchAuthCheckReq.AuthCheckItem source,
                                   boolean allowed, String reason, String desc) {
        assertThat(item.resourceTypeCode()).as(desc + " 回显类型").isEqualTo(source.resourceTypeCode());
        assertThat(item.resourceCode()).as(desc + " 回显编码").isEqualTo(source.resourceCode());
        assertThat(item.operationCode()).as(desc + " 回显操作").isEqualTo(source.operationCode());
        assertThat(item.allowed()).as(desc + " allowed").isEqualTo(allowed);
        assertThat(item.reason()).as(desc + " reason").isEqualTo(reason);
    }

    /** 分组键（资源类型×操作）锁定——组序错乱或错标分组时键断言变红（贴回外评 P3 补）。 */
    private static void assertGroupKey(QueryScopesResp.ScopeGroup group, String typeCode, String opCode) {
        assertThat(group.resourceTypeCode()).as("范围分组键-类型").isEqualTo(typeCode);
        assertThat(group.operationCode()).as("范围分组键-操作").isEqualTo(opCode);
    }

    private static List<String> codesOf(QueryScopesResp.ScopeGroup group) {
        return group.items().stream().map(QueryScopesResp.ScopeItem::resourceCode).toList();
    }
}
