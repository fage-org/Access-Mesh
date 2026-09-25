package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.engine.dto.PermBatchQuery;
import cn.ac.fage.accessmesh.access.engine.dto.PermBatchResult;
import cn.ac.fage.accessmesh.access.engine.dto.PermEvalContext;
import cn.ac.fage.accessmesh.access.engine.dto.PermQuery;
import cn.ac.fage.accessmesh.access.engine.dto.PermResult;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 互斥语义特征测试（T-PERM-081，真实 PostgreSQL + Redis）。
 * <p>
 * PQ-01／PQ-06 反例复现与留证（设计 §1.2、§10.2 D01~D03／R01~R02；断言口径=锁当前行为，
 * 2026-09-25 拍板——缺陷断言今天绿，修复任务落地当天变红强制翻转）：
 * </p>
 * <ul>
 *   <li><b>PQ-01</b>（{@code computeInstanceDenied} 整批 {@code filterPermMutex} 后回映射）：
 *       D01 锁现状「getDenied* 跨 item 整批互斥过拒」并与 queryBatch 逐 item 放行对拍；
 *       D02／D03 锁正确语义锚（一个目标集合项两端同场按共同集合拒绝／同目标挂两端必须拒绝），
 *       防 T-PERM-095 修复时把互斥改没；</li>
 *   <li><b>PQ-06</b>（{@code filterRoleMutex} 顺序遍历边删边判、规则查询无 ORDER BY）：
 *       R01 两租户两规则处理序实跑锁「同规则集不同顺序不同存活集」（顺序敏感性证据；
 *       实测序由 uk_conflict_rule_role 索引扫描决定，用例以角色创建序+规则插入序双控对齐，
 *       任一执行计划下序稳定），R02 锁「只持一端不产生传递冲突」的正确语义锚。</li>
 * </ul>
 * <p>
 * 修复翻转契约：T-PERM-095 落地后 D01 两断言翻转为「拒绝集为空」；
 * T-PERM-083 落地后 R01 两租户存活集均收敛为「仅 D」且相等（顺序无关）。
 * D02/D03/R02 修复前后语义不变。queryBatch 逐 item 放行侧由 BatchAuthCheckPgIT ⑤ 同款锁定。
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
class MutexSemanticsCharacterizationPgIT {

    /** 反例类型段（R2BaselineFixture 注记：951~953）与位值（每类型内自洽；UPDATE mask 覆盖 VIEW） */
    private static final int TYPE_D01 = 951;
    private static final int TYPE_D02 = 952;
    private static final int TYPE_D03 = 953;
    private static final String CODE_D01 = "R2BMX1";
    private static final String CODE_D02 = "R2BMX2";
    private static final String CODE_D03 = "R2BMX3";
    private static final long VIEW_BIT = 2L;
    private static final long UPDATE_BIT = 4L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, MutexSemanticsCharacterizationPgIT.class);
    }

    @Autowired private PermQueryEngine engine;
    @Autowired private PermissionConflictDomainService conflictDomainService;
    @Autowired private JdbcTemplate jdbc;

    private R2BaselineFixture fixture() {
        return new R2BaselineFixture(jdbc);
    }

    // ===== D01：PQ-01 getDenied* 跨 item 整批互斥过拒 vs queryBatch 逐 item 评估 =====

    @Test
    @DisplayName("D01 反例：X:VIEW 行 + Y:UPDATE 行（覆盖 VIEW）+ VIEW⊥UPDATE 规则——queryBatch 双放行，getDenied* 现状双拒（PQ-01）")
    void shouldOverDenyAcrossItemsInGetDeniedWhileQueryBatchAllowsPerItem() {
        R2BaselineFixture fx = fixture();
        fx.newType(TYPE_D01, CODE_D01);
        long viewOp = fx.insertOperation(TYPE_D01, "VIEW", VIEW_BIT, 0L);
        long updateOp = fx.insertOperation(TYPE_D01, "UPDATE", UPDATE_BIT, VIEW_BIT);
        fx.insertPermMutexRule(viewOp, updateOp);
        long roleX = fx.insertRoleRow(R2BaselineFixture.TENANT, "d01-x");
        long roleY = fx.insertRoleRow(R2BaselineFixture.TENANT, "d01-y");
        long user = fx.insertUserWithRoles(R2BaselineFixture.TENANT, "d01", roleX, roleY);
        long entityX = fx.insertResourceRow(TYPE_D01, "r2b-mx-x");
        long entityY = fx.insertResourceRow(TYPE_D01, "r2b-mx-y");
        fx.insertPermRow(roleX, TYPE_D01, entityX, VIEW_BIT, false, null);
        fx.insertPermRow(roleY, TYPE_D01, entityY, UPDATE_BIT, false, null);

        // 对照极：queryBatch 逐 item 评估——各 item 闭包子集内仅单端在场，不构成冲突
        PermBatchQuery batch = PermBatchQuery.forAuthCheckBatch(R2BaselineFixture.TENANT, user, List.of(
            new PermBatchQuery.Item(CODE_D01, "r2b-mx-x", "VIEW", null, null, false),
            new PermBatchQuery.Item(CODE_D01, "r2b-mx-y", "VIEW", null, null, false)));
        batch.setEvalContext(pinnedNow());
        List<PermBatchResult.ItemOutcome> outcomes = engine.queryBatch(batch).outcomes();
        assertThat(outcomes.get(0).allowed()).as("item1 子集仅 VIEW 端：单端不冲突").isTrue();
        assertThat(outcomes.get(1).allowed()).as("item2 子集仅 UPDATE 端：单端不冲突").isTrue();

        // PQ-01 特征锚：computeInstanceDenied 把两个独立目标的条目合并成一个判定集合，
        // 单端+单端凑成两端同场 → 双删 → 两个目标都被拒（设计 §10.2 D01 正确预期=getDenied 投影为空；
        // T-PERM-095 修复时本两断言翻转为 isEmpty）
        assertThat(engine.getDeniedEntityIds(R2BaselineFixture.TENANT, user, CODE_D01,
                Set.of(entityX, entityY), "VIEW"))
            .as("PQ-01 现状：id 轨整批互斥跨 item 双删 → 双目标过拒（T-PERM-095 修复翻转为空集；"
                + "红跑取证 2026-09-25：正确预期 isEmpty 下实际返回两个实体 id）")
            .containsExactlyInAnyOrder(entityX, entityY);
        assertThat(engine.getDeniedResourceCodes(R2BaselineFixture.TENANT, user, CODE_D01,
                Set.of("r2b-mx-x", "r2b-mx-y"), "VIEW"))
            .as("PQ-01 现状：code 轨同形态（T-PERM-095 修复翻转为空集）")
            .containsExactlyInAnyOrder("r2b-mx-x", "r2b-mx-y");

        // 单目标对照：判定集合内仅单端——现状与修复后语义一致，均为放行
        assertThat(engine.getDeniedEntityIds(R2BaselineFixture.TENANT, user, CODE_D01,
                Set.of(entityX), "VIEW"))
            .as("单目标仅 VIEW 端：任何实现下都不应被拒").isEmpty();
    }

    // ===== D02：一个目标集合项两端同场 → 共同集合拒绝（正确语义锚，修复前后不变） =====

    @Test
    @DisplayName("D02 语义锚：同样数据放进一个目标集合项（单 query 多编码目标）→ 两端同场按共同集合拒绝")
    void shouldDenyAsCommonSetWhenBothEndsInOneTargetSetQuery() {
        R2BaselineFixture fx = fixture();
        fx.newType(TYPE_D02, CODE_D02);
        long viewOp = fx.insertOperation(TYPE_D02, "VIEW", VIEW_BIT, 0L);
        long updateOp = fx.insertOperation(TYPE_D02, "UPDATE", UPDATE_BIT, VIEW_BIT);
        fx.insertPermMutexRule(viewOp, updateOp);
        long roleX = fx.insertRoleRow(R2BaselineFixture.TENANT, "d02-x");
        long roleY = fx.insertRoleRow(R2BaselineFixture.TENANT, "d02-y");
        long user = fx.insertUserWithRoles(R2BaselineFixture.TENANT, "d02", roleX, roleY);
        long entityX = fx.insertResourceRow(TYPE_D02, "r2b-mx-x");
        long entityY = fx.insertResourceRow(TYPE_D02, "r2b-mx-y");
        fx.insertPermRow(roleX, TYPE_D02, entityX, VIEW_BIT, false, null);
        fx.insertPermRow(roleY, TYPE_D02, entityY, UPDATE_BIT, false, null);

        // 单条 query 的 INSTANCE 目标集 = 整个编码集合一个判定集合（TargetSet 共同集合语义的旧核心对应形态）
        PermQuery q = PermQuery.forAuthCheck(R2BaselineFixture.TENANT, user, CODE_D02, "r2b-mx-x", "VIEW");
        q.setResourceCodes(Set.of("r2b-mx-x", "r2b-mx-y"));
        q.setEvalContext(pinnedNow());
        PermResult result = engine.query(q);

        assertThat(result.allowed())
            .as("D02 正确语义锚：目标集合项内两端同场 → 共同集合拒绝（T-PERM-095 修复后仍须拒绝）")
            .isFalse();
        assertThat(result.reason()).isEqualTo("CONDITION_NOT_MET_OR_CONFLICT");
    }

    // ===== D03：同目标挂互斥两端 → 必须拒绝（正确语义锚，修复前后不变） =====

    @Test
    @DisplayName("D03 语义锚：同一实体挂 VIEW 行 + UPDATE 行（覆盖 VIEW）+ 互斥规则 → 单点/批量/getDenied 全拒")
    void shouldDenyWhenSingleTargetHoldsBothMutexEnds() {
        R2BaselineFixture fx = fixture();
        fx.newType(TYPE_D03, CODE_D03);
        long viewOp = fx.insertOperation(TYPE_D03, "VIEW", VIEW_BIT, 0L);
        long updateOp = fx.insertOperation(TYPE_D03, "UPDATE", UPDATE_BIT, VIEW_BIT);
        fx.insertPermMutexRule(viewOp, updateOp);
        long role = fx.insertRoleRow(R2BaselineFixture.TENANT, "d03");
        long user = fx.insertUserWithRoles(R2BaselineFixture.TENANT, "d03", role);
        long entityZ = fx.insertResourceRow(TYPE_D03, "r2b-mx-z");
        fx.insertPermRow(role, TYPE_D03, entityZ, VIEW_BIT, false, null);
        fx.insertPermRow(role, TYPE_D03, entityZ, UPDATE_BIT, false, null);

        // 单点：不能第一条授权提前返回——互斥两端同目标必须拒绝
        PermQuery single = PermQuery.forAuthCheck(R2BaselineFixture.TENANT, user, CODE_D03, "r2b-mx-z", "VIEW");
        single.setEvalContext(pinnedNow());
        PermResult singleResult = engine.query(single);
        assertThat(singleResult.allowed())
            .as("D03 正确语义锚：同目标两端同场必须拒绝").isFalse();
        assertThat(singleResult.reason()).isEqualTo("CONDITION_NOT_MET_OR_CONFLICT");

        // 批量 item 同形
        PermBatchQuery batch = PermBatchQuery.forAuthCheckBatch(R2BaselineFixture.TENANT, user, List.of(
            new PermBatchQuery.Item(CODE_D03, "r2b-mx-z", "VIEW", null, null, false)));
        batch.setEvalContext(pinnedNow());
        PermBatchResult.ItemOutcome outcome = engine.queryBatch(batch).outcomes().get(0);
        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.reason()).isEqualTo("CONDITION_NOT_MET_OR_CONFLICT");

        // getDenied 单目标（集合语义对单目标=共同集合）：修复后仍应拒绝
        assertThat(engine.getDeniedEntityIds(R2BaselineFixture.TENANT, user, CODE_D03, Set.of(entityZ), "VIEW"))
            .as("D03：同目标两端 → getDenied 必含该目标（T-PERM-095 修复后不变）")
            .containsExactly(entityZ);
        assertThat(engine.hasPermissionByEntityId(R2BaselineFixture.TENANT, user, CODE_D03, entityZ, "VIEW"))
            .isFalse();
    }

    // ===== R01：PQ-06 角色互斥顺序依赖（两租户两插入序实跑，2026-09-25 拍板） =====

    @Test
    @DisplayName("R01 反例：{A,B,C,D}+规则 A-B/B-C——两租户两规则序存活集不同（顺序依赖），且都不是正确答案「仅 D」")
    void shouldProduceOrderDependentRoleSurvivalUnderChainedMutexRules() {
        R2BaselineFixture fx = fixture();
        // 规则处理序双控（selectByConflictType 无 ORDER BY，PG 实际走 uk_conflict_rule_role 索引扫描，
        // 返回序=first_abstract_role_id 索引序，非堆插入序——实测取证 2026-09-25）：
        // 角色创建序决定索引序（id 越小排越前）+ 规则插入序决定堆序，两者对齐后任一执行计划下序稳定。
        // 租户 A：创建序 A,B,C,D → A-B 索引序在前；租户 B：创建序 B,C,D,A → B-C 索引序在前。
        Map<String, Long> graphA = seedChainedMutexRoles(fx, R2BaselineFixture.TENANT_R1A, "r01a",
            "A", "B", "C", "D");
        Map<String, Long> graphB = seedChainedMutexRoles(fx, R2BaselineFixture.TENANT_R1B, "r01b",
            "B", "C", "D", "A");
        fx.insertRoleMutexRule(R2BaselineFixture.TENANT_R1A, graphA.get("A"), graphA.get("B"));
        fx.insertRoleMutexRule(R2BaselineFixture.TENANT_R1A, graphA.get("B"), graphA.get("C"));
        fx.insertRoleMutexRule(R2BaselineFixture.TENANT_R1B, graphB.get("B"), graphB.get("C"));
        // 生产写路径 normalizePair 钉死存库 first_id < second_id（schema 表注释契约）——租户 B
        // 的 A-B 规则按生产形态直插 (B,A)：索引键 (idB,idA) 仍排在 (B,C) 之后（first 同 B、
        // second C<A），处理序与结果 {A,D} 与未规范化形态一致，数据形态生产可达
        fx.insertRoleMutexRule(R2BaselineFixture.TENANT_R1B, graphB.get("B"), graphB.get("A"));

        long holderA = fx.insertUserWithRoles(R2BaselineFixture.TENANT_R1A, "r01a-holder",
            graphA.get("A"), graphA.get("B"), graphA.get("C"), graphA.get("D"));
        long holderB = fx.insertUserWithRoles(R2BaselineFixture.TENANT_R1B, "r01b-holder",
            graphB.get("A"), graphB.get("B"), graphB.get("C"), graphB.get("D"));

        Set<String> survivalA = labelsOf(conflictDomainService.resolveJudgementRoleIds(
            R2BaselineFixture.TENANT_R1A, holderA), graphA);
        Set<String> survivalB = labelsOf(conflictDomainService.resolveJudgementRoleIds(
            R2BaselineFixture.TENANT_R1B, holderB), graphB);

        // PQ-06 特征锚：顺序遍历对不断缩小的结果集判定——任一顺序下 B 必删、D 必留、存活恰 2 个
        // （先 A-B → {C,D}；先 B-C → {A,D}）。设计 §10.2 R01 正确预期=对原始集算全部命中再一次删除 → 仅 D；
        // T-PERM-083 修复时两断言翻转为 containsExactly("D")
        assertThat(survivalA)
            .as("PQ-06 现状（租户 A，规则序 A-B→B-C）：顺序依赖存活集 ≠ 正确答案 {D}"
                + "（红跑取证 2026-09-25：正确预期 containsExactly(\"D\") 下实际 [\"C\",\"D\"]）")
            .hasSize(2).contains("D").doesNotContain("B")
            .isNotEqualTo(Set.of("D"));
        assertThat(survivalB)
            .as("PQ-06 现状（租户 B，规则序 B-C→A-B）：顺序依赖存活集 {A,D} ≠ 正确答案 {D}"
                + "（绿跑 isNotEqualTo 反证取证 2026-09-25）")
            .hasSize(2).contains("D").doesNotContain("B")
            .isNotEqualTo(Set.of("D"));
        // 顺序敏感性实跑证据：同规则集、同持有集、不同规则处理序 → 不同存活集（{C,D} vs {A,D}，
        // 由本断言绿跑通过反证；T-PERM-083 修复后两边同为 {D}，本断言翻转为 isEqualTo）
        assertThat(survivalA)
            .as("PQ-06 顺序敏感性：两种规则处理序产生不同结果（filterRoleMutex 顺序遍历边删边判）")
            .isNotEqualTo(survivalB);
    }

    // ===== R02：只持一端不产生传递冲突（正确语义锚，修复前后不变） =====

    @Test
    @DisplayName("R02 语义锚：只持 {A,C,D}（不持 B）→ 规则 A-B/B-C 均不触发，全保留无传递冲突")
    void shouldKeepAllRolesWhenOnlyOneEndOfEachRuleHeld() {
        R2BaselineFixture fx = fixture();
        Map<String, Long> graph = seedChainedMutexRoles(fx, R2BaselineFixture.TENANT_R2, "r02",
            "A", "B", "C", "D");
        long partialUser = fx.insertUserWithRoles(R2BaselineFixture.TENANT_R2, "r02-partial",
            graph.get("A"), graph.get("C"), graph.get("D"));   // 不持 B：各规则最多单端在场
        fx.insertRoleMutexRule(R2BaselineFixture.TENANT_R2, graph.get("A"), graph.get("B"));
        fx.insertRoleMutexRule(R2BaselineFixture.TENANT_R2, graph.get("B"), graph.get("C"));

        Set<String> survival = labelsOf(conflictDomainService.resolveJudgementRoleIds(
            R2BaselineFixture.TENANT_R2, partialUser), graph);

        assertThat(survival)
            .as("R02 正确语义锚：两端不同场不触发、无传递冲突（T-PERM-083 修复后不变）")
            .containsExactlyInAnyOrder("A", "C", "D");
    }

    // ===== 夹具辅助 =====

    /** 链式互斥图：按给定创建序造角色 A/B/C/D（创建序=角色 id 序=规则索引序）；持有用户由调用方按需自建。 */
    private Map<String, Long> seedChainedMutexRoles(R2BaselineFixture fx, long tenantId, String tag,
                                                    String... creationOrder) {
        Map<String, Long> roles = new LinkedHashMap<>();
        for (String label : creationOrder) {
            roles.put(label, fx.insertRoleRow(tenantId, tag + "-" + label.toLowerCase()));
        }
        return roles;
    }

    private static Set<String> labelsOf(Set<Long> roleIds, Map<String, Long> rolesByLabel) {
        return rolesByLabel.entrySet().stream()
            .filter(e -> roleIds.contains(e.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    private static PermEvalContext pinnedNow() {
        return new PermEvalContext(null, LocalDateTime.now(), Map.of());
    }
}
