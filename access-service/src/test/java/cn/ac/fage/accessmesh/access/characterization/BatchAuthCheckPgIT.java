package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.access.engine.dto.AuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.dto.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.engine.service.PermissionCheckAppService;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * batchCheck 批量化容器轨回归锁 ①-⑪（T-PERM-092 起驱动面＝batchCheck 服务面生产消费者）。
 * <p>
 * 设计定稿 v4 验收面：等价差分（check×N vs batchCheck 逐 item 对拍 allowed/reason/matched
 * 按集合比较）、共享计数（N=10/100 下 mapper 调用次数不变）、投影谓词否定（分档闭包/
 * TYPE_LEVEL 无条件丢子行/跨类型位泄漏）、空目标集守卫（1000 项上限形态禁 500）、
 * 互斥真锁（逐 item 评估）、reason 边界（DEPENDENT_NOT_IN_PARENT_CONTEXT）、时间窗
 * （批内单一评估时刻）、通知次数/内容（T-PERM-088 起按 ConflictEvidence 形态：
 * execution＋item＋stage＋ruleRef 聚合，单行单规则结构化摘要）、禁用条件 fail-close 两轨、
 * 条件-互斥顺序、条件增量快照次数。种子全部经 jdbc 直插（独立类型段避开
 * OPERATION_PERMISSIONS_BY_TYPE 陈旧缓存），GoldenFixturePgIT/TargetModeClosurePgIT 先例形态。
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
class BatchAuthCheckPgIT {

    private static final Long TENANT = 1L;
    private static final int USER_TYPE_EXTERNAL = 1; // 预置 user_type：USER=外部人员（batchCheck subjectTypeCode="USER"）
    private static final int ROLE_TYPE_BASIC = 6;

    private static final long RESOURCE_ID_BASE = 9_630_000L;
    private static final long SUBJECT_ID_BASE = 9_640_000L;
    private static final int TYPE_VALUE_BASE = 901;

    /** VIEW/UPDATE 位值（每用例自建类型段，位值本类型内自洽；UPDATE inheritMask 覆盖 VIEW） */
    private static final long VIEW_BIT = 2L;
    private static final long UPDATE_BIT = 4L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, BatchAuthCheckPgIT.class);
    }

    @Autowired private PermissionCheckAppService appService;
    @Autowired private JdbcTemplate jdbc;

    @SpyBean private RoleResourcePermissionMapper rolePermMapper;
    @SpyBean private ResourceEntityMapper resourceEntityMapper;
    @SpyBean private PermissionConditionMapper conditionMapper;
    // 断言引擎的同步通知调用；真实异步落库不属于批量判定被测面。
    @MockBean private AuditDomainService auditDomainService;

    // 类级单调递增（JUnit PER_METHOD 生命周期下实例字段每方法重置会撞 uk——同 JVM 内跨方法共享 ID 段）
    private static int nextTypeValue = TYPE_VALUE_BASE;
    private static long nextSubjectId = SUBJECT_ID_BASE;
    private static long nextResourceId = RESOURCE_ID_BASE;

    // ===== 种子 helper（jdbc 直插；独立类型段避开操作缓存陈旧窗口）=====

    private int newType() {
        int value = nextTypeValue++;
        jdbc.update(
            "INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system, sort_order) "
            + "VALUES (?, 'resource_type', ?, ?, ?, false, 99)",
            TENANT, typeCodeOf(value), value, "b61-" + typeCodeOf(value));
        return value;
    }

    private static String typeCodeOf(int typeValue) {
        return "B61T" + typeValue;
    }

    private long ensureOperation(int typeValue, String code, long bit, long inheritMask) {
        return jdbc.queryForObject(
            "INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, delete_flag) "
            + "VALUES (?, ?, ?, ?, ?, ?, 0) RETURNING id",
            Long.class, TENANT, typeValue, code, "b61-" + code, bit, inheritMask);
    }

    private long newSubject(String tag) {
        long subjectId = nextSubjectId++;
        Long roleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, extra) "
            + "VALUES (?, ?, ?, ?, 1, '{}') RETURNING id",
            Long.class, TENANT, ROLE_TYPE_BASIC, "b61-role-" + tag, "b61-" + tag);
        jdbc.update(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
            + "VALUES (?, ?, ?, ?, ?, true, '{}', NULL)",
            subjectId, TENANT, USER_TYPE_EXTERNAL, String.valueOf(subjectId), "b61-" + tag);
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, subjectId, roleId);
        return subjectId;
    }

    private long roleIdOf(long subjectId) {
        return jdbc.queryForObject(
            "SELECT target_id FROM user_role WHERE tenant_id = ? AND abstract_user_id = ? AND delete_flag = 0",
            Long.class, TENANT, subjectId);
    }

    private long insertResource(int typeValue, String code, Long parentId) {
        long entityId = nextResourceId++;
        jdbc.update(
            "INSERT INTO resource_entity (id, tenant_id, parent_id, resource_type, code, code_type, name, status) "
            + "VALUES (?, ?, ?, ?, ?, 'default', ?, 1)",
            entityId, TENANT, parentId, typeValue, code, "b61-" + code);
        return entityId;
    }

    private long insertPerm(long roleId, int typeValue, Long entityId, long bits,
                            boolean scopeAll, Long conditionId, Long dependOn) {
        return jdbc.queryForObject(
            "INSERT INTO role_resource_permission "
            + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, condition_id, depend_on, grant_source) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL') RETURNING id",
            Long.class, TENANT, roleId, entityId, bits, typeValue, scopeAll, conditionId, dependOn);
    }

    private long insertCondition(boolean enabled, String rules) {
        return jdbc.queryForObject(
            "INSERT INTO permission_condition (tenant_id, code, name, condition_rules, enabled, gateway_evaluable, source, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, false, 'MANAGED', now(), now()) RETURNING id",
            Long.class, TENANT, "b61-c" + System.nanoTime(), "b61-cond", rules, enabled);
    }

    private long insertMutexRule(long firstOpId, long secondOpId) {
        return jdbc.queryForObject(
            "INSERT INTO permission_conflict_rule (tenant_id, conflict_type, first_operation_permission_id, second_operation_permission_id, description) "
            + "VALUES (?, 'PERM_MUTEX', ?, ?, 'b61') RETURNING id",
            Long.class, TENANT, firstOpId, secondOpId);
    }

    private static String dateRangeRules(String start, String end) {
        return "{\"logic\":\"AND\",\"items\":[{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\"" + start
            + "\",\"end\":\"" + end + "\"}}]}";
    }

    private static final String ALWAYS_TRUE_RULES = "{\"logic\":\"AND\",\"items\":[]}";

    // ===== 驱动面（T-PERM-092 起＝batchCheck 服务面生产消费者）=====

    private static BatchAuthCheckReq.AuthCheckItem item(String typeCode, String code, String op) {
        return new BatchAuthCheckReq.AuthCheckItem(typeCode, code, op, null, null, null);
    }

    private static BatchAuthCheckReq.AuthCheckItem inheritItem(String typeCode, String code, String op) {
        return new BatchAuthCheckReq.AuthCheckItem(typeCode, code, op, null, null, "PARENT");
    }

    private BatchAuthCheckResp.AuthCheckItemResult runSingleItemBatch(long subjectId,
                                                                      BatchAuthCheckReq.AuthCheckItem item) {
        return runBatch(subjectId, List.of(item)).get(0);
    }

    private List<BatchAuthCheckResp.AuthCheckItemResult> runBatch(long subjectId,
                                                                  List<BatchAuthCheckReq.AuthCheckItem> items) {
        return appService.batchCheck(TENANT, new BatchAuthCheckReq(
            "USER", String.valueOf(subjectId), items, null, null, null, null, Map.of())).items();
    }

    private BatchAuthCheckResp runBatchWithParent(long subjectId, List<BatchAuthCheckReq.AuthCheckItem> items,
                                                  String parentType, String parentCode, String parentOp) {
        return appService.batchCheck(TENANT, new BatchAuthCheckReq(
            "USER", String.valueOf(subjectId), items, parentType, parentCode, null,
            List.of(parentOp), Map.of()));
    }

    private AuthCheckResp runSingle(long subjectId, BatchAuthCheckReq.AuthCheckItem item) {
        return appService.check(TENANT, new AuthCheckReq(
            "USER", String.valueOf(subjectId), item.resourceTypeCode(), item.resourceCode(),
            item.operationCode(), null, null, item.inheritMode(),
            null, null, null, null, Map.of()));
    }

    // ===== ① 等价差分 + ⑥ reason 边界 =====

    @Test
    @DisplayName("① 等价差分：同一数据上 check()×N vs batchCheck 逐 item 对拍 allowed/reason/matched（按集合比较）")
    void batchCheckMustMatchPerItemCheckAcrossShapes() {
        long subjectId = newSubject("eq");
        long roleId = roleIdOf(subjectId);

        int t1 = newType(); // scopeAll 放行（TYPE_LEVEL 与 INSTANCE 短路）
        ensureOperation(t1, "VIEW", VIEW_BIT, 0L);
        insertPerm(roleId, t1, null, VIEW_BIT, true, null, null);

        int t2 = newType(); // 实例命中 + 实例条件拒绝 + 幽灵 code
        ensureOperation(t2, "VIEW", VIEW_BIT, 0L);
        long condFalseId = insertCondition(true, dateRangeRules("2000-01-01", "2001-01-01")); // 恒不满足
        long hitEntity = insertResource(t2, "b61-eq-hit", null);
        long condEntity = insertResource(t2, "b61-eq-cond", null);
        insertPerm(roleId, t2, hitEntity, VIEW_BIT, false, null, null);
        insertPerm(roleId, t2, condEntity, VIEW_BIT, false, condFalseId, null);

        int t3 = newType(); // 授父查子（默认 deny / PARENT allow）
        ensureOperation(t3, "VIEW", VIEW_BIT, 0L);
        long parentEntity = insertResource(t3, "b61-eq-parent", null);
        insertResource(t3, "b61-eq-child", parentEntity);
        insertPerm(roleId, t3, parentEntity, VIEW_BIT, false, null, null);

        int t4 = newType(); // depend_on 子行（无父上下文 deny）+ 幽灵 code + 仅子行 scopeAll（⑥ 形态）
        ensureOperation(t4, "VIEW", VIEW_BIT, 0L);
        long t4Entity = insertResource(t4, "b61-eq-dep", null);
        // 全部为 depend_on 子行（父 perm id 不落库——无 FK，引擎只比较 dependOn 与父命中集）：
        // 无父上下文时实例子行被剥（item7 DEPENDENT）、scopeAll 子行被剥（item8 幽灵目标空 DEPENDENT）
        long virtualParentPermId = 9_699_999L;
        insertPerm(roleId, t4, t4Entity, VIEW_BIT, false, null, virtualParentPermId); // depend_on 实例子行
        insertPerm(roleId, t4, null, VIEW_BIT, true, null, virtualParentPermId);      // 仅 depend_on scopeAll 子行

        int t5 = newType(); // 互斥双行（同实体 VIEW+UPDATE → 单 item 双丢）
        long viewOp5 = ensureOperation(t5, "VIEW", VIEW_BIT, 0L);
        long updateOp5 = ensureOperation(t5, "UPDATE", UPDATE_BIT, VIEW_BIT); // inheritMask 覆盖 VIEW
        insertMutexRule(viewOp5, updateOp5);
        long mutexEntity = insertResource(t5, "b61-eq-mutex", null);
        insertPerm(roleId, t5, mutexEntity, VIEW_BIT, false, null, null);
        insertPerm(roleId, t5, mutexEntity, UPDATE_BIT, false, null, null);

        List<BatchAuthCheckReq.AuthCheckItem> items = List.of(
            item(typeCodeOf(t1), null, "VIEW"),                            // TYPE_LEVEL allow
            item(typeCodeOf(t1), "b61-eq-ghost-any", "VIEW"),              // INSTANCE scopeAll 短路（幽灵 code 也放行）
            item(typeCodeOf(t2), "b61-eq-hit", "VIEW"),                    // 实例命中
            item(typeCodeOf(t2), "b61-eq-cond", "VIEW"),                   // 实例条件拒绝
            item(typeCodeOf(t2), "b61-eq-ghost", "VIEW"),                  // 幽灵 code
            item(typeCodeOf(t3), "b61-eq-child", "VIEW"),                  // 默认授父查子 deny
            inheritItem(typeCodeOf(t3), "b61-eq-child", "VIEW"),           // PARENT allow
            item(typeCodeOf(t4), "b61-eq-dep", "VIEW"),                    // depend_on 子行 deny
            item(typeCodeOf(t4), "b61-eq-ghost2", "VIEW"),                 // 幽灵 + 仅子行 scopeAll（⑥）
            item(typeCodeOf(t5), "b61-eq-mutex", "VIEW"),                  // 互斥双丢
            item("B61GHOST-TYPE", null, "VIEW"));                          // 幽灵类型 TYPE_LEVEL

        List<BatchAuthCheckResp.AuthCheckItemResult> outcomes = runBatch(subjectId, items);

        assertThat(outcomes).hasSameSizeAs(items);
        for (int i = 0; i < items.size(); i++) {
            BatchAuthCheckReq.AuthCheckItem item = items.get(i);
            AuthCheckResp single = runSingle(subjectId, item);

            assertThat(outcomes.get(i).allowed())
                .as("item[%d] %s:%s allowed 等价", i, item.resourceTypeCode(), item.resourceCode())
                .isEqualTo(single.allowed());
            assertThat(outcomes.get(i).conditionEvaluated()).isEqualTo(single.conditionEvaluated());
            assertThat(outcomes.get(i).reason())
                .as("item[%d] reason 等价", i)
                .isEqualTo(single.reason());
            assertThat(outcomes.get(i).matchedRoleIds())
                .as("item[%d] matchedRoleIds 等价（按集合比较）", i)
                .containsExactlyInAnyOrderElementsOf(single.matchedRoleIds());
            assertThat(outcomes.get(i).matchedPermissionIds())
                .as("item[%d] matchedPermissionIds 等价（按集合比较）", i)
                .containsExactlyInAnyOrderElementsOf(single.matchedPermissionIds());
        }

        // 形态抽查（锁 reason 落位，防全 deny 假绿）
        assertThat(outcomes.get(0).allowed()).isTrue();
        assertThat(outcomes.get(1).allowed()).as("scopeAll 短路优先：幽灵 code 也放行").isTrue();
        assertThat(outcomes.get(2).allowed()).isTrue();
        assertThat(outcomes.get(3).allowed()).isFalse();
        assertThat(outcomes.get(3).reason()).isEqualTo("CONDITION_NOT_MET");
        assertThat(outcomes.get(4).reason()).isEqualTo("NO_PERMISSION");
        assertThat(outcomes.get(5).allowed()).isFalse();
        assertThat(outcomes.get(6).allowed()).isTrue();
        assertThat(outcomes.get(7).reason()).isEqualTo("DEPENDENT_NOT_IN_PARENT_CONTEXT");
        assertThat(outcomes.get(8).reason()).isEqualTo("DEPENDENT_NOT_IN_PARENT_CONTEXT");
        assertThat(outcomes.get(9).reason()).isEqualTo("PERMISSION_CONFLICT");
        assertThat(outcomes.get(10).reason()).isEqualTo("NO_PERMISSION");
    }

    // ===== ② 共享计数锁 =====

    @Test
    @DisplayName("② 共享计数锁：N=10 与 N=100 下 scopeAll/实例/闭包 mapper 调用次数不变（防批量入口内部循环单查）")
    void sharedLoadCountsMustNotGrowWithBatchSize() {
        int type = newType();
        ensureOperation(type, "VIEW", VIEW_BIT, 0L);
        long subjectId = newSubject("count");
        long roleId = roleIdOf(subjectId);
        for (int i = 0; i < 100; i++) {
            long entityId = insertResource(type, "b61-cnt-" + i, null);
            insertPerm(roleId, type, entityId, VIEW_BIT, false, null, null);
        }

        runBatchOf(subjectId, type, 10);
        verify(rolePermMapper, times(1)).selectScopeAllPermsByBitsBatch(eq(TENANT), anySet(), anyList());
        verify(rolePermMapper, times(1)).selectInstancePermsByBitsBatch(eq(TENANT), anySet(), anySet(), anyList());
        verify(resourceEntityMapper, never()).selectSelfAndAncestorClosureBatch(eq(TENANT), anySet());

        runBatchOf(subjectId, type, 100);
        verify(rolePermMapper, times(2)).selectScopeAllPermsByBitsBatch(eq(TENANT), anySet(), anyList());
        verify(rolePermMapper, times(2)).selectInstancePermsByBitsBatch(eq(TENANT), anySet(), anySet(), anyList());
        verify(resourceEntityMapper, never()).selectSelfAndAncestorClosureBatch(eq(TENANT), anySet());
    }

    private void runBatchOf(long subjectId, int type, int n) {
        List<BatchAuthCheckReq.AuthCheckItem> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(item(typeCodeOf(type), "b61-cnt-" + i, "VIEW"));
        }
        List<BatchAuthCheckResp.AuthCheckItemResult> outcomes = runBatch(subjectId, items);
        assertThat(outcomes).hasSize(n);
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.allowed()).isTrue());
    }

    // ===== ③ 投影谓词否定锁 =====

    @Test
    @DisplayName("③ 投影谓词否定：默认模式授父查子 deny / TYPE_LEVEL+depend_on scopeAll+父上下文 deny / 跨类型位泄漏 deny")
    void projectionPredicatesMustNotLeakAcrossModesOrTypes() {
        // (a) 默认模式（不传 inheritMode）授父查子 deny——SELF 档不消费祖先闭包
        int tParent = newType();
        ensureOperation(tParent, "VIEW", VIEW_BIT, 0L);
        long subjectA = newSubject("proj");
        long parentEntity = insertResource(tParent, "b61-proj-parent", null);
        insertResource(tParent, "b61-proj-child", parentEntity);
        insertPerm(roleIdOf(subjectA), tParent, parentEntity, VIEW_BIT, false, null, null);

        List<BatchAuthCheckResp.AuthCheckItemResult> byDefault = runBatch(subjectA, List.of(
            item(typeCodeOf(tParent), "b61-proj-child", "VIEW")));
        assertThat(byDefault.get(0).allowed())
            .as("默认模式授父查子必须 deny（SELF 档不得消费祖先闭包）").isFalse();

        // (b) TYPE_LEVEL + 仅 depend_on scopeAll + 请求级父上下文 deny——TYPE_LEVEL 无条件丢子行
        int tDeps = newType();
        ensureOperation(tDeps, "VIEW", VIEW_BIT, 0L);
        long subjectB = newSubject("projdep");
        long depEntity = insertResource(tDeps, "b61-projdep-res", null);
        long parentPermId = insertPerm(roleIdOf(subjectB), tDeps, depEntity, VIEW_BIT, false, null, null);
        insertPerm(roleIdOf(subjectB), tDeps, null, VIEW_BIT, true, null, parentPermId); // 仅 depend_on scopeAll

        List<BatchAuthCheckResp.AuthCheckItemResult> typeLevel = runBatchWithParent(subjectB,
            List.of(item(typeCodeOf(tDeps), null, "VIEW")), typeCodeOf(tDeps), "b61-projdep-res", "VIEW").items();
        assertThat(typeLevel.get(0).allowed())
            .as("TYPE_LEVEL 组无条件丢 depend_on 子行（不走父上下文过滤）").isFalse();
        assertThat(typeLevel.get(0).reason()).isEqualTo("NO_PERMISSION");

        // (c) 同批跨类型：类型A scopeAll VIEW 放行不得泄漏给类型B item（位值跨类型同值）
        int tUser = newType();
        ensureOperation(tUser, "VIEW", VIEW_BIT, 0L);
        int tMenu = newType();
        ensureOperation(tMenu, "VIEW", VIEW_BIT, 0L);
        long subjectC = newSubject("projmix");
        insertResource(tUser, "b61-proj-user", null);
        insertResource(tMenu, "b61-proj-menu", null);
        insertPerm(roleIdOf(subjectC), tUser, null, VIEW_BIT, true, null, null); // USER scopeAll VIEW

        List<BatchAuthCheckResp.AuthCheckItemResult> mixed = runBatch(subjectC, List.of(
            item(typeCodeOf(tUser), "b61-proj-user", "VIEW"),
            item(typeCodeOf(tMenu), "b61-proj-menu", "VIEW")));
        assertThat(mixed.get(0).allowed()).as("USER scopeAll 放行本类型").isTrue();
        assertThat(mixed.get(1).allowed())
            .as("跨类型位泄漏否定：MENU item 不得消费 USER 组 scopeAll 行").isFalse();
    }

    // ===== ④ 空目标集批 =====

    @Test
    @DisplayName("④ 空目标集批：纯 TYPE_LEVEL 1000 项 / 全幽灵 code → 全 deny 不下推实例 SQL 与闭包 CTE（禁 500）")
    void emptyTargetBatchMustDenyAllWithoutInstanceSqlOrClosureCte() {
        int type = newType();
        ensureOperation(type, "VIEW", VIEW_BIT, 0L);
        long subjectId = newSubject("empty");

        // (a) 纯 TYPE_LEVEL 1000 项（同组放大；1000 上限形态）
        BatchAuthCheckResp typeLevelAll = appService.batchCheck(TENANT, batchReqOf(subjectId,
            ghostItems(typeCodeOf(type), null, 1000)));
        assertThat(typeLevelAll.items()).hasSize(1000);
        assertThat(typeLevelAll.items()).allSatisfy(item -> {
            assertThat(item.allowed()).isFalse();
            assertThat(item.reason()).isEqualTo("NO_PERMISSION");
        });

        // (b) 全幽灵 code 1000 项
        BatchAuthCheckResp ghostAll = appService.batchCheck(TENANT, batchReqOf(subjectId,
            ghostItems(typeCodeOf(type), "b61-ghost-", 1000)));
        assertThat(ghostAll.items()).hasSize(1000);
        assertThat(ghostAll.items()).allSatisfy(item -> assertThat(item.allowed()).isFalse());

        // 守卫：不调闭包 CTE 与实例 SQL（空 foreach IN()=500 / <if> 空集无界装载）
        verify(resourceEntityMapper, never()).selectSelfAndAncestorClosureBatch(eq(TENANT), anySet());
        verify(rolePermMapper, never()).selectInstancePermsByBitsBatch(eq(TENANT), anySet(), anySet(), anyList());
    }

    private BatchAuthCheckReq batchReqOf(long subjectId, List<BatchAuthCheckReq.AuthCheckItem> items) {
        return new BatchAuthCheckReq("USER", String.valueOf(subjectId), items, null, null, null, null, Map.of());
    }

    private List<BatchAuthCheckReq.AuthCheckItem> ghostItems(String typeCode, String codePrefix, int n) {
        List<BatchAuthCheckReq.AuthCheckItem> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(item(typeCode, codePrefix == null ? null : codePrefix + i, "VIEW"));
        }
        return items;
    }

    // ===== ⑤ 互斥真锁 =====

    @Test
    @DisplayName("⑤ 互斥真锁：VIEW 行 + UPDATE 行（inherit_mask 覆盖 VIEW）+ VIEW↔UPDATE 规则 → 逐 item 双 allowed")
    void perItemMutexEvaluationMustKeepSingleEndEntries() {
        int type = newType();
        long viewOp = ensureOperation(type, "VIEW", VIEW_BIT, 0L);
        long updateOp = ensureOperation(type, "UPDATE", UPDATE_BIT, VIEW_BIT);
        insertMutexRule(viewOp, updateOp);
        long subjectId = newSubject("mutex");
        long roleId = roleIdOf(subjectId);
        long entityA = insertResource(type, "b61-mx-a", null);
        long entityB = insertResource(type, "b61-mx-b", null);
        insertPerm(roleId, type, entityA, VIEW_BIT, false, null, null);   // item1 子集 = 仅 VIEW 行
        insertPerm(roleId, type, entityB, UPDATE_BIT, false, null, null); // item2 子集 = 仅 UPDATE 行

        List<BatchAuthCheckResp.AuthCheckItemResult> outcomes = runBatch(subjectId, List.of(
            item(typeCodeOf(type), "b61-mx-a", "VIEW"),
            item(typeCodeOf(type), "b61-mx-b", "VIEW")));

        // 逐 item 评估：各子集单端在场不构成冲突（合并评估=两端同场双丢=false deny）
        assertThat(outcomes.get(0).allowed()).as("item1 仅 VIEW 行：单端不冲突").isTrue();
        assertThat(outcomes.get(1).allowed()).as("item2 仅 UPDATE 行：单端不冲突").isTrue();
    }

    // ===== ⑦ 时间窗边界锁 =====

    @Test
    @DisplayName("⑦ 时间窗边界锁：mockStatic now() 依次 t1/t2 → 批内全部按 t1 评估一致（逐 item 路径下红）")
    void batchMustPinSingleEvaluationMomentAcrossItems() {
        int type = newType();
        ensureOperation(type, "VIEW", VIEW_BIT, 0L);
        long subjectId = newSubject("time");
        // DATE_RANGE 按天粒度（LocalDate.parse）：窗口 [过去, 今天] —— t1=今天 满足、t2=明天 越界
        long condId = insertCondition(true, dateRangeRules("2000-01-01", LocalDate.now().toString()));
        long entity = insertResource(type, "b61-time-res", null);
        insertPerm(roleIdOf(subjectId), type, entity, VIEW_BIT, false, condId, null);

        LocalDateTime t1 = LocalDate.now().atTime(12, 0);
        LocalDateTime t2 = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(1);

        // CALLS_REAL_METHODS：条件评估链内 LocalDateTime.parse 等其他静态方法走真实实现
        try (var mocked = mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(LocalDateTime::now).thenReturn(t1, t2);
            BatchAuthCheckResp resp = appService.batchCheck(TENANT, batchReqOf(subjectId, List.of(
                item(typeCodeOf(type), "b61-time-res", "VIEW"),
                item(typeCodeOf(type), "b61-time-res", "VIEW"))));
            // 批内单一评估时刻（RunState 单时钟）：第一个 now()=t1 钉住全链，第二个起不参与评估——
            // 逐 item 路径各 item 各自 now()（item2 拿 t2 越界 deny）会破坏一致性
            assertThat(resp.items()).hasSize(2);
            assertThat(resp.items()).allSatisfy(item -> assertThat(item.allowed()).isTrue());
        }
    }

    // ===== ⑧ 通知次数 + 内容锁（T-PERM-088 ConflictEvidence 形态） =====

    @Test
    @DisplayName("⑧ 通知次数/内容锁：同规则两 item 各自两端同场 → 两条 item 级 CONFLICT_DETECTED 证据行；未触发规则不出现")
    void mutexNotificationMustEmitPerItemEvidenceAndSkipUntriggered() {
        int type = newType();
        long viewOp = ensureOperation(type, "VIEW", VIEW_BIT, 0L);
        long updateOp = ensureOperation(type, "UPDATE", UPDATE_BIT, VIEW_BIT);
        long triggeredRuleId = insertMutexRule(viewOp, updateOp);
        int otherType = newType(); // 未触发规则端点：独立类型上的操作，永不与判定面同场
        long neverOp = ensureOperation(otherType, "NEVER", 64L, 0L);
        long untriggeredRuleId = insertMutexRule(neverOp, viewOp);

        long subjectId = newSubject("notify");
        long roleId = roleIdOf(subjectId);
        long entityA = insertResource(type, "b61-nt-a", null);
        long entityB = insertResource(type, "b61-nt-b", null);
        // 两实体各挂双行（各自 item 子集内两端同场 → 同规则冲突）
        insertPerm(roleId, type, entityA, VIEW_BIT, false, null, null);
        insertPerm(roleId, type, entityA, UPDATE_BIT, false, null, null);
        insertPerm(roleId, type, entityB, VIEW_BIT, false, null, null);
        insertPerm(roleId, type, entityB, UPDATE_BIT, false, null, null);

        runBatch(subjectId, List.of(
            item(typeCodeOf(type), "b61-nt-a", "VIEW"),
            item(typeCodeOf(type), "b61-nt-b", "VIEW")));

        // 新引擎受控提交（T-PERM-088）：按 execution＋item＋stage＋ruleRef 聚合——
        // 两 item 同规则各自两端同场 = 两条证据行（单行单规则结构化摘要；
        // 旧 (组,ruleId) 合并单行 hitItemCount=2 形态随旧执行体退场）。
        ArgumentCaptor<AuditDomainService.OperationLogEntry> captor =
            ArgumentCaptor.forClass(AuditDomainService.OperationLogEntry.class);
        verify(auditDomainService, times(2)).asyncRecordLog(captor.capture());
        List<String> summaries = captor.getAllValues().stream()
            .map(entry -> String.valueOf(entry.summary())).toList();
        assertThat(summaries)
            .allSatisfy(summary -> assertThat(summary)
                .contains("rule=" + triggeredRuleId)
                .contains("stage=INSTANCE")
                .contains("completion=COMPLETE"));
        // 两条行各自 item 键（batchCheck item key=输入下标）
        assertThat(summaries).anySatisfy(summary -> assertThat(summary).contains("item=0"));
        assertThat(summaries).anySatisfy(summary -> assertThat(summary).contains("item=1"));
        // 未触发规则端点不出现
        assertThat(summaries).noneSatisfy(summary -> assertThat(summary).contains("rule=" + untriggeredRuleId));
    }

    // ===== ⑨ 禁用条件 fail-close 两轨 =====

    @Test
    @DisplayName("⑨ 禁用条件 fail-close 两轨：scopeAll 轨与实例轨，enabled=false 且规则体可评估为真 → 必须 deny")
    void disabledConditionMustFailCloseInBothSegments() {
        long condId = insertCondition(false, ALWAYS_TRUE_RULES); // 禁用 + 规则体恒真

        // scopeAll 轨
        int t1 = newType();
        ensureOperation(t1, "VIEW", VIEW_BIT, 0L);
        long subjectA = newSubject("disable-sa");
        insertPerm(roleIdOf(subjectA), t1, null, VIEW_BIT, true, condId, null);
        insertResource(t1, "b61-ds-a", null);

        BatchAuthCheckResp.AuthCheckItemResult scopeAllTrack = runSingleItemBatch(subjectA,
            item(typeCodeOf(t1), "b61-ds-a", "VIEW"));
        assertThat(scopeAllTrack.allowed())
            .as("scopeAll 轨：禁用条件必须 fail-close（朴素批量当有效规则=绕过方向）").isFalse();
        assertThat(scopeAllTrack.reason()).isEqualTo("CONDITION_NOT_MET");

        // 实例轨
        int t2 = newType();
        ensureOperation(t2, "VIEW", VIEW_BIT, 0L);
        long subjectB = newSubject("disable-inst");
        long entityB = insertResource(t2, "b61-ds-b", null);
        insertPerm(roleIdOf(subjectB), t2, entityB, VIEW_BIT, false, condId, null);

        BatchAuthCheckResp.AuthCheckItemResult instanceTrack = runSingleItemBatch(subjectB,
            item(typeCodeOf(t2), "b61-ds-b", "VIEW"));
        assertThat(instanceTrack.allowed()).as("实例轨：禁用条件必须 fail-close").isFalse();
        assertThat(instanceTrack.reason()).isEqualTo("CONDITION_NOT_MET");
    }

    // ===== ⑩ 条件-互斥顺序锁 =====

    @Test
    @DisplayName("⑩ 条件-互斥顺序锁：VIEW 行挂不满足条件 + 无条件 UPDATE 行（覆盖 VIEW）+ 互斥 → 条件先摘、UPDATE 仍放行且零通知")
    void conditionMustRunBeforeMutexWithinProjectionSubset() {
        int type = newType();
        long viewOp = ensureOperation(type, "VIEW", VIEW_BIT, 0L);
        long updateOp = ensureOperation(type, "UPDATE", UPDATE_BIT, VIEW_BIT);
        insertMutexRule(viewOp, updateOp);
        long subjectId = newSubject("order");
        long condFalseId = insertCondition(true, dateRangeRules("2000-01-01", "2001-01-01")); // 恒不满足
        long entity = insertResource(type, "b61-ord-res", null);
        insertPerm(roleIdOf(subjectId), type, entity, VIEW_BIT, false, condFalseId, null); // VIEW 挂不满足条件
        insertPerm(roleIdOf(subjectId), type, entity, UPDATE_BIT, false, null, null);      // UPDATE 无条件

        BatchAuthCheckResp.AuthCheckItemResult outcome = runSingleItemBatch(subjectId,
            item(typeCodeOf(type), "b61-ord-res", "VIEW"));

        // 条件先摘 VIEW → 互斥两端不齐 → UPDATE 保留放行（若互斥先算：两端同场全丢 = false deny + 虚假审计）
        assertThat(outcome.allowed()).as("条件先摘 VIEW 一端后互斥不成立，UPDATE 仍放行").isTrue();
        verify(auditDomainService, never()).asyncRecordLog(any());
    }

    // ===== ⑪ 条件增量快照次数锁 =====

    @Test
    @DisplayName("⑪ 条件增量快照次数锁：同 conditionId 跨段（scopeAll/实例）至多回源一次")
    void conditionSnapshotMustResolveEachConditionAtMostOncePerRequest() {
        int type = newType();
        ensureOperation(type, "VIEW", VIEW_BIT, 0L);
        long subjectId = newSubject("snap");
        long roleId = roleIdOf(subjectId);
        long condId = insertCondition(true, dateRangeRules("2000-01-01", "2001-01-01")); // 恒不满足

        // scopeAll 主行挂条件（评估清空不走短路）+ 实例行挂同一条件：
        // 请求级条件装载回源 1 次，跨段命中请求级快照零回源
        long entity = insertResource(type, "b61-snap-res", null);
        insertPerm(roleId, type, null, VIEW_BIT, true, condId, null);
        insertPerm(roleId, type, entity, VIEW_BIT, false, condId, null);

        BatchAuthCheckResp.AuthCheckItemResult outcome = runSingleItemBatch(subjectId,
            item(typeCodeOf(type), "b61-snap-res", "VIEW"));

        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.reason()).isEqualTo("CONDITION_NOT_MET");
        verify(conditionMapper, times(1)).selectValidByIds(eq(TENANT), anySet());
    }

    // ===== 补：批量父判定装配分支（请求级父上下文 + 实例 depend_on 子行） =====

    @Test
    @DisplayName("父判定装配：请求级父上下文 + 实例 depend_on 子行 → 父命中放行 / 父操作不覆盖 DEPENDENT")
    void batchParentContextMustResolveThroughSharedCarrier() {
        int type = newType();
        ensureOperation(type, "VIEW", VIEW_BIT, 0L);
        ensureOperation(type, "UPDATE", UPDATE_BIT, 0L); // (b) 变体：父实体不挂 UPDATE 行
        long subjectId = newSubject("parent");
        long roleId = roleIdOf(subjectId);
        long parentEntity = insertResource(type, "b61-pt-parent", null);
        long childEntity = insertResource(type, "b61-pt-child", null);
        long parentPermId = insertPerm(roleId, type, parentEntity, VIEW_BIT, false, null, null); // 父主行
        long childPermId = insertPerm(roleId, type, childEntity, VIEW_BIT, false, null, parentPermId); // 子行

        // (a) 父操作覆盖（VIEW）：父判定命中集含 parentPermId ⊇ 子行 depend_on → 子行保留放行
        //     （请求级父上下文挂全批 item，惰性判定一次）
        List<BatchAuthCheckResp.AuthCheckItemResult> allowed = runBatchWithParent(subjectId,
            List.of(item(typeCodeOf(type), "b61-pt-child", "VIEW")),
            typeCodeOf(type), "b61-pt-parent", "VIEW").items();
        assertThat(allowed.get(0).allowed())
            .as("批量父判定命中：子行 depend_on ∈ 父命中集 → 放行").isTrue();
        assertThat(allowed.get(0).matchedPermissionIds()).containsExactly(childPermId);

        // (b) 父操作不覆盖（UPDATE：父实体只挂 VIEW 行）：父判定不命中 → 子行剥除 → DEPENDENT
        List<BatchAuthCheckResp.AuthCheckItemResult> dependent = runBatchWithParent(subjectId,
            List.of(item(typeCodeOf(type), "b61-pt-child", "VIEW")),
            typeCodeOf(type), "b61-pt-parent", "UPDATE").items();
        assertThat(dependent.get(0).allowed()).isFalse();
        assertThat(dependent.get(0).reason()).isEqualTo("DEPENDENT_NOT_IN_PARENT_CONTEXT");
    }
}
