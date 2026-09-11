package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConditionResp;
import cn.ac.fage.accessmesh.access.permission.service.ConditionAppService;
import cn.ac.fage.accessmesh.access.permission.service.PermissionGrantAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * T-PERM-048 验收：条件实例投影与双轨制（真实 PostgreSQL + Redis，Testcontainers）。覆盖：
 * <ol>
 *   <li>bootstrap 自愈补种：存量 MANAGED 条件幂等补投影（code=条件 code）；INLINE 不投影；
 *       野行（CONDITION 类型下无对应条件的手工资源行）不自动清理；</li>
 *   <li>写路径同生共死：create/update（enabled 翻转镜像 status）/remove（条件行+投影行同事务软删）；
 *       投影失败整体回滚；</li>
 *   <li>实例门禁升级（定案④）：scope_all 存量授权零破坏放行；实例级 CONDITION:UPDATE@code 命中/拒绝；
 *       零授权拒绝；</li>
 *   <li>删除引用守卫（定案③，20059）：condition_id 挂靠引用 / 投影行下实例授权两类任一命中整批拒绝，
 *       零引用放行；</li>
 *   <li>内联生命周期（定案①）：apply-grant-plan 同事务创建（source=INLINE、code inline- 前缀、无投影行）、
 *       removes/换绑引用归零回收、创建失败零残留；管理面 20060 三面（update/remove/detail）与
 *       conditionCode 引用轨焊点。</li>
 * </ol>
 * 装配策略对齐 {@code TypeDefinitionProjectionPgIT}（T-PERM-051）：事实/授权行经 jdbc 直插；
 * Docker 不可用时由 Testcontainers 自动跳过。
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
class ConditionProjectionDualTrackPgIT {

    private static final Long TENANT = 1L;

    /** resource_type 种子：CONDITION=13（操作位 CREATE=1/VIEW=2/UPDATE=4/DELETE=8）；ROLE=5（MANAGE=16） */
    private static final int RESOURCE_TYPE_CONDITION = 13;
    private static final int RESOURCE_TYPE_ROLE = 5;
    private static final long CONDITION_UPDATE_BIT = 4L;
    private static final long CONDITION_VIEW_BIT = 2L;
    private static final long ROLE_MANAGE_BIT = 16L;

    private static final String RULES_IP =
        "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, ConditionProjectionDualTrackPgIT.class);
    }

    @Autowired
    private ConditionAppService conditionAppService;
    @Autowired
    private PermissionGrantAppService permissionGrantAppService;
    @Autowired
    private LocalProjectionDomainService localProjectionDomainService;
    @Autowired
    private JdbcTemplate jdbc;

    /** 投影层 spy：回滚用例仅指定方法注入故障，其余真实（成功场景全链路落库）。 */
    @SpyBean
    private LocalProjectionDomainService localProjectionSpy;
    /** 内联轨 spy：创建失败回滚用例仅 createInlineCondition 注入故障。 */
    @SpyBean
    private PermissionConditionDomainService conditionDomainSpy;

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("bootstrap 自愈补种：存量 MANAGED 条件幂等补投影（code=条件 code）；INLINE 不投影；野行不清理")
    void backfillShouldSeedManagedOnlyAndSkipInlineAndWildRows() {
        // 造存量：一个缺投影的 MANAGED 条件（jdbc 直插，模拟库先于特性存在）+ 一个 INLINE 条件 + 一个野资源行
        jdbc.update("INSERT INTO permission_condition (tenant_id, code, name, condition_rules, enabled, gateway_evaluable, source) "
            + "VALUES (?, 't048-backfill-m', '存量管理条件', ?, true, false, 'MANAGED')", TENANT, RULES_IP);
        jdbc.update("INSERT INTO permission_condition (tenant_id, code, name, condition_rules, enabled, gateway_evaluable, source) "
            + "VALUES (?, 't048-backfill-i', '存量内联条件', ?, true, false, 'INLINE')", TENANT, RULES_IP);
        jdbc.update("INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status, owner_service_code, maintain_source) "
            + "VALUES (?, 13, 't048-wild-orphan', 'default', '野行', 1, 'access-service', 'MANUAL')", TENANT);

        int inserted = localProjectionDomainService.backfillConditionProjections(TENANT);

        // 至少补了本用例的存量 MANAGED 条件（其余为库中其他缺投影行，断言聚焦本例）
        assertThat(inserted).isPositive();
        assertThat(countConditionProjection("t048-backfill-m")).isEqualTo(1);
        // INLINE 不投影（定案⑤：无资源身份消费者）
        assertThat(countConditionProjection("t048-backfill-i")).isZero();
        // 野行不自动清理（软删行可能是有效授权目标，runbook FAQ 兜底）
        assertThat(countConditionProjection("t048-wild-orphan")).isEqualTo(1);
        // 幂等重跑：已齐备
        assertThat(localProjectionDomainService.backfillConditionProjections(TENANT)).isZero();
    }

    @Test
    @DisplayName("写路径投影生灭：create 落投影 status 镜像 enabled；update 翻转镜像；remove 同事务软删；投影失败整体回滚")
    void writePathShouldProjectCreateUpdateRemoveAtomically() {
        Long creator = insertSubject("t048-op-proj", "投影操作者");
        Long creatorRole = insertBasicRole("t048-role-proj", "投影角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_CONDITION, 1L); // CONDITION:CREATE 类型级
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_CONDITION, CONDITION_UPDATE_BIT);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_CONDITION, 8L); // DELETE（remove 用）
        bindOperator(creator);

        ConditionResp created = conditionAppService.createCondition(TENANT,
            new ConditionCreateReq("t048-cond-a", "条件A", RULES_IP, true, false, null), creator);
        assertThat(created.source()).isEqualTo("MANAGED");
        Map<String, Object> projection = conditionProjectionRow("t048-cond-a");
        assertThat(((Number) projection.get("resource_type")).intValue()).isEqualTo(RESOURCE_TYPE_CONDITION);
        assertThat(projection.get("name")).isEqualTo("条件A");
        assertThat(projection.get("status")).isEqualTo(1);
        assertThat(projection.get("owner_service_code")).isEqualTo("access-service");

        // enabled 翻转 → 投影 status 镜像（停用条件自动隐出授权资源树）
        conditionAppService.updateCondition(TENANT,
            new ConditionUpdateReq("t048-cond-a", null, null, false, null, null), creator);
        assertThat(conditionProjectionRow("t048-cond-a").get("status")).isEqualTo(0);
        conditionAppService.updateCondition(TENANT,
            new ConditionUpdateReq("t048-cond-a", null, null, true, null, null), creator);
        assertThat(conditionProjectionRow("t048-cond-a").get("status")).isEqualTo(1);

        // 零引用删除：条件行 + 投影行同事务软删（旧实现投影行残留，本断言必红）
        conditionAppService.deleteConditionsByCodes(TENANT, List.of("t048-cond-a"), creator);
        assertThat(countValidRows("permission_condition", "code = 't048-cond-a'")).isZero();
        assertThat(countConditionProjection("t048-cond-a")).isZero();

        // 投影写入失败：条件行同事务回滚（强事务投影 fail-closed）
        doThrow(new RuntimeException("projection boom"))
            .when(localProjectionSpy).upsertConditionResource(anyLong(), anyString(), anyString(), anyBoolean());
        assertThatThrownBy(() -> conditionAppService.createCondition(TENANT,
            new ConditionCreateReq("t048-cond-rb", "回滚条件", RULES_IP, true, false, null), creator))
            .hasMessageContaining("projection boom");
        assertThat(countValidRows("permission_condition", "code = 't048-cond-rb'")).isZero();
    }

    @Test
    @DisplayName("实例门禁升级（定案④）：scope_all 存量授权零破坏；实例级@code 命中放行/未命中拒绝；零授权拒绝")
    void instanceLevelGatesShouldWorkOnProjectionResolution() {
        // 管理员A：CONDITION:UPDATE scope_all（存量形态）——升级后天然覆盖全部实例（零破坏）
        Long admin = insertSubject("t048-op-scope", "scope管理员");
        Long adminRole = insertBasicRole("t048-role-scope", "scope角色");
        insertUserRole(admin, adminRole);
        insertScopeAllRolePerm(adminRole, RESOURCE_TYPE_CONDITION, CONDITION_UPDATE_BIT);
        insertScopeAllRolePerm(adminRole, RESOURCE_TYPE_CONDITION, 1L); // CREATE（建条件用）
        bindOperator(admin);
        conditionAppService.createCondition(TENANT,
            new ConditionCreateReq("t048-gate-a", "门禁A", RULES_IP, true, false, null), admin);
        conditionAppService.createCondition(TENANT,
            new ConditionCreateReq("t048-gate-b", "门禁B", RULES_IP, true, false, null), admin);

        conditionAppService.updateCondition(TENANT,
            new ConditionUpdateReq("t048-gate-a", "scope改名", null, null, null, null), admin);
        assertThat(conditionProjectionRow("t048-gate-a").get("name")).isEqualTo("scope改名");

        // 管理员B：仅实例级 CONDITION:UPDATE@t048-gate-a（新解锁能力——旧实现实例授权配不进）
        Long scoped = insertSubject("t048-op-inst", "实例管理员");
        Long scopedRole = insertBasicRole("t048-role-inst", "实例角色");
        insertUserRole(scoped, scopedRole);
        long gateAProjectionId = conditionProjectionId("t048-gate-a");
        insertInstanceRolePerm(scopedRole, RESOURCE_TYPE_CONDITION, CONDITION_UPDATE_BIT, gateAProjectionId);
        bindOperator(scoped);
        conditionAppService.updateCondition(TENANT,
            new ConditionUpdateReq("t048-gate-a", "实例改名", null, null, null, null), scoped);
        assertThat(conditionProjectionRow("t048-gate-a").get("name")).isEqualTo("实例改名");
        // 未授权实例拒绝（投影解析 fail-closed；旧实现类型级门禁下本断言必红——scope_all 之外全放行）
        assertThatThrownBy(() -> conditionAppService.updateCondition(TENANT,
            new ConditionUpdateReq("t048-gate-b", "越权改名", null, null, null, null), scoped))
            .isInstanceOf(SecurityException.class);

        // 零授权账号：全拒 fail-closed
        Long bare = insertSubject("t048-op-bare", "零授权用户");
        bindOperator(bare);
        assertThatThrownBy(() -> conditionAppService.updateCondition(TENANT,
            new ConditionUpdateReq("t048-gate-a", "裸改", null, null, null, null), bare))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("删除引用守卫（定案③，20059）：挂靠引用/投影行下实例授权任一命中整批拒绝；零引用放行")
    void removeShouldRejectWhenReferencedByGrants() {
        Long manager = insertSubject("t048-op-del", "删除操作者");
        Long managerRole = insertBasicRole("t048-role-del", "删除角色");
        insertUserRole(manager, managerRole);
        insertScopeAllRolePerm(managerRole, RESOURCE_TYPE_CONDITION, 8L); // CONDITION:DELETE 类型级
        insertScopeAllRolePerm(managerRole, RESOURCE_TYPE_CONDITION, 1L); // CREATE（建条件用）
        bindOperator(manager);
        conditionAppService.createCondition(TENANT,
            new ConditionCreateReq("t048-guard-1", "守卫一", RULES_IP, true, false, null), manager);
        conditionAppService.createCondition(TENANT,
            new ConditionCreateReq("t048-guard-2", "守卫二", RULES_IP, true, false, null), manager);
        Long guard1Id = conditionId("t048-guard-1");
        Long guard2ProjectionId = conditionProjectionId("t048-guard-2");

        // 守卫①：condition_id 挂靠引用（挂该条件的授权行评估 fail-close——静默删除=授权静默失效）
        Long holderRole = insertBasicRole("t048-role-holder", "挂靠角色");
        jdbc.update("INSERT INTO role_resource_permission "
            + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source, condition_id) "
            + "VALUES (?, ?, NULL, ?, ?, true, 'MANUAL', ?)", TENANT, holderRole, CONDITION_VIEW_BIT,
            RESOURCE_TYPE_CONDITION, guard1Id);
        assertThatThrownBy(() -> conditionAppService.deleteConditionsByCodes(TENANT, List.of("t048-guard-1"), manager))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("t048-guard-1");
        assertThat(countValidRows("permission_condition", "code = 't048-guard-1'")).isEqualTo(1);

        // 守卫②：投影行下实例授权引用（CONDITION:VIEW@guard-2 实例级授权行悬空防护）
        Long instanceViewerRole = insertBasicRole("t048-role-instview", "实例查看角色");
        insertInstanceRolePerm(instanceViewerRole, RESOURCE_TYPE_CONDITION, CONDITION_VIEW_BIT, guard2ProjectionId);
        assertThatThrownBy(() -> conditionAppService.deleteConditionsByCodes(TENANT, List.of("t048-guard-2"), manager))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("t048-guard-2");
        assertThat(countValidRows("permission_condition", "code = 't048-guard-2'")).isEqualTo(1);

        // 零引用：放行（条件行 + 投影行同删）——清走两类引用后删 guard-1
        jdbc.update("UPDATE role_resource_permission SET delete_flag = id WHERE tenant_id = ? "
            + "AND condition_id = ?", TENANT, guard1Id);
        conditionAppService.deleteConditionsByCodes(TENANT, List.of("t048-guard-1"), manager);
        assertThat(countValidRows("permission_condition", "code = 't048-guard-1'")).isZero();
        assertThat(countConditionProjection("t048-guard-1")).isZero();
    }

    @Test
    @DisplayName("内联生命周期（定案①）：plan 同事务创建（INLINE/inline- 前缀/无投影）；removes 回收；创建失败零残留")
    void inlineLifecycleShouldCreateRecycleAndRollbackAtomically() {
        GrantContext ctx = prepareGrantContext("t048-inline");

        // 创建：CONDITION:VIEW ALL + 内联定义 → INLINE 条件行 + 授权行挂靠，无投影行
        ApplyGrantPlanReq.InlineConditionDef def =
            new ApplyGrantPlanReq.InlineConditionDef("内联工作时间", RULES_IP, false);
        permissionGrantAppService.applyGrantPlan(TENANT, new ApplyGrantPlanReq(
            null, "BASIC_ROLE", ctx.targetRoleExternalId(),
            new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                new ApplyGrantPlanReq.GrantRecordKey("CONDITION", null, null, "VIEW",
                    ScopeMode.ALL, null, def, null), null, List.of())),
                List.of(), List.of())));
        Long inlineId = jdbc.queryForObject(
            "SELECT condition_id FROM role_resource_permission WHERE tenant_id = ? "
                + "AND abstract_role_id = ? AND resource_type = 13 AND scope_all = true AND delete_flag = 0",
            Long.class, TENANT, ctx.targetRoleId());
        assertThat(inlineId).isNotNull();
        Map<String, Object> inlineRow = jdbc.queryForMap(
            "SELECT code, source, enabled FROM permission_condition WHERE id = ?", inlineId);
        assertThat(((String) inlineRow.get("code"))).startsWith("inline-");
        assertThat(inlineRow.get("source")).isEqualTo("INLINE");
        assertThat(inlineRow.get("enabled")).isEqualTo(Boolean.TRUE);
        // 定案⑤：INLINE 不投影
        assertThat(countValidRows("resource_entity",
            "resource_type = 13 AND code = '" + inlineRow.get("code") + "'")).isZero();

        // list 双轨：默认只回 MANAGED；includeInline=true 含内联（回显）
        assertThat(conditionAppService.listConditions(TENANT, null))
            .extracting(ConditionResp::code).doesNotContain((String) inlineRow.get("code"));
        assertThat(conditionAppService.listConditions(TENANT, true))
            .extracting(ConditionResp::code).contains((String) inlineRow.get("code"));

        // 编辑：update inlineCondition → 同 code 就地更新规则（1:1 保持）
        permissionGrantAppService.applyGrantPlan(TENANT, new ApplyGrantPlanReq(
            null, "BASIC_ROLE", ctx.targetRoleExternalId(),
            new ApplyGrantPlanReq.GrantPlan(List.of(),
                List.of(new ApplyGrantPlanReq.UpdateItem(jdbc.queryForObject(
                    "SELECT id FROM role_resource_permission WHERE tenant_id = ? "
                        + "AND abstract_role_id = ? AND resource_type = 13 AND scope_all = true AND delete_flag = 0",
                    Long.class, TENANT, ctx.targetRoleId()),
                    null, null,
                    new ApplyGrantPlanReq.InlineConditionDef("内联改名", RULES_IP, true))),
                List.of())));
        Map<String, Object> editedRow = jdbc.queryForMap(
            "SELECT code, name FROM permission_condition WHERE id = ?", inlineId);
        assertThat(editedRow.get("name")).isEqualTo("内联改名");
        assertThat(editedRow.get("code")).isEqualTo(inlineRow.get("code"));

        // 回收：removes 授权行 → 引用归零同事务回收条件行（旧实现无回收面=孤儿累积，本断言必红）
        Long permissionId = jdbc.queryForObject(
            "SELECT id FROM role_resource_permission WHERE tenant_id = ? "
                + "AND abstract_role_id = ? AND resource_type = 13 AND scope_all = true AND delete_flag = 0",
            Long.class, TENANT, ctx.targetRoleId());
        permissionGrantAppService.applyGrantPlan(TENANT, new ApplyGrantPlanReq(
            null, "BASIC_ROLE", ctx.targetRoleExternalId(),
            new ApplyGrantPlanReq.GrantPlan(List.of(), List.of(), List.of(permissionId))));
        assertThat(countValidRows("permission_condition", "id = " + inlineId)).isZero();

        // 创建失败零残留（取消弹窗同事务语义）：内联创建故障 → 授权行/条件行整体回滚
        doThrow(new RuntimeException("inline boom"))
            .when(conditionDomainSpy).createInlineCondition(anyLong(), anyLong(), any());
        assertThatThrownBy(() -> permissionGrantAppService.applyGrantPlan(TENANT, new ApplyGrantPlanReq(
            null, "BASIC_ROLE", ctx.targetRoleExternalId(),
            new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                new ApplyGrantPlanReq.GrantRecordKey("CONDITION", null, null, "VIEW",
                    ScopeMode.ALL, null, def, null), null, List.of())),
                List.of(), List.of()))))
            .hasMessageContaining("inline boom");
        assertThat(countValidRows("permission_condition", "source = 'INLINE' AND name = '内联工作时间'")).isZero();
        assertThat(countValidRows("role_resource_permission",
            "abstract_role_id = " + ctx.targetRoleId() + " AND resource_type = 13")).isZero();
    }

    @Test
    @DisplayName("20060 三面：管理面 update/remove/detail INLINE 拒绝；conditionCode 引用轨焊点")
    void inlineShouldBeRejectedOnManagementFaceAndReferenceTrack() {
        GrantContext ctx = prepareGrantContext("t048-reject");
        ApplyGrantPlanReq.InlineConditionDef def =
            new ApplyGrantPlanReq.InlineConditionDef("内联被拒", RULES_IP, false);
        permissionGrantAppService.applyGrantPlan(TENANT, new ApplyGrantPlanReq(
            null, "BASIC_ROLE", ctx.targetRoleExternalId(),
            new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                new ApplyGrantPlanReq.GrantRecordKey("CONDITION", null, null, "VIEW",
                    ScopeMode.ALL, null, def, null), null, List.of())),
                List.of(), List.of())));
        String inlineCode = jdbc.queryForObject(
            "SELECT pc.code FROM permission_condition pc JOIN role_resource_permission rrp "
                + "ON rrp.condition_id = pc.id WHERE rrp.tenant_id = ? AND rrp.abstract_role_id = ? "
                + "AND rrp.resource_type = 13 AND rrp.delete_flag = 0",
            String.class, TENANT, ctx.targetRoleId());

        // 管理面操作者（CONDITION 类型级全套，操作者本身权限拉满——双轨拒绝与权限无关）
        Long manager = insertSubject("t048-op-mgr", "管理面操作者");
        Long managerRole = insertBasicRole("t048-role-mgr", "管理面角色");
        insertUserRole(manager, managerRole);
        insertScopeAllRolePerm(managerRole, RESOURCE_TYPE_CONDITION, CONDITION_UPDATE_BIT);
        insertScopeAllRolePerm(managerRole, RESOURCE_TYPE_CONDITION, 8L);
        bindOperator(manager);

        // 三面：update / remove / detail
        assertThatThrownBy(() -> conditionAppService.updateCondition(TENANT,
            new ConditionUpdateReq(inlineCode, "越权改", null, null, null, null), manager))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("内联条件不可在管理面管理");
        assertThatThrownBy(() -> conditionAppService.deleteConditionsByCodes(TENANT, List.of(inlineCode), manager))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("内联条件不可在管理面删除");
        assertThatThrownBy(() -> conditionAppService.getCondition(TENANT, inlineCode))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("内联条件不可在管理面管理");

        // conditionCode 引用轨焊点：另一角色 plan 显式引用 INLINE code → 20060（1:1 不可共享）
        GrantContext other = prepareGrantContext("t048-reject-other");
        assertThatThrownBy(() -> permissionGrantAppService.applyGrantPlan(TENANT, new ApplyGrantPlanReq(
            null, "BASIC_ROLE", other.targetRoleExternalId(),
            new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                new ApplyGrantPlanReq.GrantRecordKey("CONDITION", null, null, "VIEW",
                    ScopeMode.ALL, inlineCode, null, null), null, List.of())),
                List.of(), List.of()))))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("不可引用内联条件");
    }

    // ===== 数据装配（jdbc 直插事实/授权，先于相关主体首次引擎调用） =====

    private record GrantContext(Long targetRoleId, String targetRoleExternalId) {}

    /** 授权计划上下文：操作者持 ROLE:MANAGE 可转授 + CONDITION:VIEW 可转授（转授检查需要），目标 BASIC_ROLE */
    private GrantContext prepareGrantContext(String tag) {
        Long operator = insertSubject(tag + "-op", tag + "操作者");
        Long operatorRole = insertBasicRole(tag + "-oprole", tag + "操作者角色");
        insertUserRole(operator, operatorRole);
        enableCanGrant(insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_ROLE, ROLE_MANAGE_BIT));
        enableCanGrant(insertScopeAllRolePerm(operatorRole, RESOURCE_TYPE_CONDITION, CONDITION_VIEW_BIT));

        String targetExternal = tag + "-target";
        Long targetRole = insertBasicRole(targetExternal, tag + "目标角色");
        bindOperator(operator);
        return new GrantContext(targetRole, targetExternal);
    }

    private void enableCanGrant(long permissionId) {
        jdbc.update("UPDATE role_resource_permission SET can_grant = true WHERE id = ?", permissionId);
    }

    private void bindOperator(Long operatorId) {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, operatorId));
    }

    private Long insertSubject(String externalId, String name) {
        // user_type 种子：LOCAL_USER=3
        return jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, 3, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, TENANT, externalId, name);
    }

    private Long insertBasicRole(String externalId, String name) {
        // role_type 种子：BASIC_ROLE=6
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, externalId, name);
    }

    private void insertUserRole(Long abstractUserId, Long targetRoleId) {
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, abstractUserId, targetRoleId);
    }

    private long insertScopeAllRolePerm(Long roleId, int resourceType, long grantedBits) {
        return jdbc.queryForObject(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, NULL, ?, ?, true, 'MANUAL') RETURNING id",
            Long.class, TENANT, roleId, grantedBits, resourceType);
    }

    private void insertInstanceRolePerm(Long roleId, int resourceType, long grantedBits, long resourceEntityId) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, ?, ?, ?, false, 'MANUAL')",
            TENANT, roleId, resourceEntityId, grantedBits, resourceType);
    }

    private Map<String, Object> conditionProjectionRow(String conditionCode) {
        return jdbc.queryForMap(
            "SELECT id, name, status, parent_id, resource_type, owner_service_code, delete_flag FROM resource_entity "
                + "WHERE tenant_id = ? AND resource_type = ? AND code = ? AND code_type = 'default' AND delete_flag = 0",
            TENANT, RESOURCE_TYPE_CONDITION, conditionCode);
    }

    private long conditionProjectionId(String conditionCode) {
        return ((Number) conditionProjectionRow(conditionCode).get("id")).longValue();
    }

    private long countConditionProjection(String conditionCode) {
        return countValidRows("resource_entity",
            "resource_type = " + RESOURCE_TYPE_CONDITION + " AND code = '" + conditionCode
                + "' AND code_type = 'default'");
    }

    private Long conditionId(String conditionCode) {
        return jdbc.queryForObject(
            "SELECT id FROM permission_condition WHERE tenant_id = ? AND code = ? AND delete_flag = 0",
            Long.class, TENANT, conditionCode);
    }

    private long countValidRows(String table, String condition) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND delete_flag = 0 AND " + condition,
            Long.class, TENANT);
        return count == null ? 0 : count;
    }
}
