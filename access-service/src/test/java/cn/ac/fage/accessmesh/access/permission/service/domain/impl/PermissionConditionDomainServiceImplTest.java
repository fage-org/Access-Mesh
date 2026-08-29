package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.vo.ConditionEvaluationDetail;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionConditionDomainServiceImpl} 单元测试（T-PERM-033 evaluateDetailed）。
 * <p>
 * 锁定：批量条件加载（缓存 getBatch → miss 一次批量查库 → putBatch 回填，同 ID 去重不重复穿透）、
 * 逐项评估过程（logic AND/OR 聚合与运行时判定一致，空 items 三态维持旧语义）、脱敏规则
 * （IP 掩码主机段、IPv6/IPv4-mapped/非数字八位组整体 MASKED、日期/时间原样）、
 * 条件加载状态（DISABLED/NOT_FOUND fail-close）、无条件条目不产生明细。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionConditionDomainServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock private PermissionConditionMapper conditionMapper;
    @Mock private CacheService cacheService;
    @Mock private CacheReadToken<JsonNode> readToken;

    private PermissionConditionDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionConditionDomainServiceImpl(conditionMapper,
            new ObjectMapper(), cacheService);
        lenient().when(cacheService.beginRead(PermCacheCatalog.CONDITION_RULES)).thenReturn(readToken);
        // 默认缓存全 miss（各用例按需覆写为命中）
        lenient().when(cacheService.getBatch(eq(PermCacheCatalog.CONDITION_RULES), eq(TENANT), anySet()))
            .thenReturn(Map.of());
    }

    private RolePermEntry entry(Long permissionId, Long roleId, Long conditionId, boolean hasCondition) {
        return new RolePermEntry(permissionId, roleId, 200L, "sys:user", 1, 2L,
            "VIEW", 2L, "DIRECT", true, conditionId, hasCondition, null, false);
    }

    private PermissionCondition condition(Long id, String rules) {
        PermissionCondition condition = new PermissionCondition();
        condition.setId(id);
        condition.setTenantId(TENANT);
        condition.setEnabled(true);
        condition.setConditionRules(rules);
        return condition;
    }

    /** 缓存 miss 桩：批量查库返回给定条件实体 */
    private void stubDbLoad(PermissionCondition... conditions) {
        when(conditionMapper.selectValidByIds(eq(TENANT), anySet())).thenReturn(List.of(conditions));
    }

    /** OK 状态 + AND 聚合逐项结果 + 脱敏：IP 掩码主机段、日期范围原样 */
    @Test
    void shouldEvaluateDetailedWithMaskedParams() {
        stubDbLoad(condition(77L, """
            {"logic":"AND","items":[
              {"type":"IP_WHITELIST","params":{"cidrs":["192.168.1.0/24","10.20.30.40"]}},
              {"type":"DATE_RANGE","params":{"start":"2026-01-01","end":"2026-12-31"}}]}
            """));

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(501L, 20L, 77L, true)), Map.of("clientIp", "192.168.1.55"));

        assertEquals(1, details.size());
        ConditionEvaluationDetail detail = details.get(0);
        assertEquals(ConditionEvaluationDetail.STATUS_OK, detail.status());
        assertEquals("AND", detail.logic());
        assertEquals(2, detail.items().size());
        assertEquals("IP_WHITELIST", detail.items().get(0).type());
        // IP 掩码主机段：192.168.1.0/24 → 192.168.*.*\/24；单 IP → 10.20.*.*
        assertEquals("192.168.*.*/24, 10.20.*.*", detail.items().get(0).maskedParams());
        assertTrue(detail.items().get(0).matched());
        assertEquals("2026-01-01~2026-12-31", detail.items().get(1).maskedParams());
        // passed 按真实时钟评估（日期范围覆盖 2026 全年，断言仅校评估过程不锁时钟结果）
        verify(cacheService).putBatch(eq(readToken), eq(TENANT), eq(Map.of(77L,
            readTree("{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"192.168.1.0/24\",\"10.20.30.40\"]}},{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\"2026-01-01\",\"end\":\"2026-12-31\"}}]}"))));
    }

    private JsonNode readTree(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** OR 逻辑：任一项满足即通过（matched=条件项满足；黑名单命中 IP 反而不满足） */
    @Test
    void shouldAggregateOrLogicLikeRuntime() {
        stubDbLoad(condition(78L, """
            {"logic":"OR","items":[
              {"type":"IP_WHITELIST","params":{"cidrs":["10.1.0.0/16"]}},
              {"type":"IP_BLACKLIST","params":{"cidrs":["10.0.0.0/8"]}}]}
            """));

        // clientIp 命中白名单（条件项满足）且落入黑名单（条件项不满足）→ OR 整体通过
        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(502L, 21L, 78L, true)), Map.of("clientIp", "10.1.2.3"));

        assertTrue(details.get(0).passed());
        assertTrue(details.get(0).items().get(0).matched());
        assertFalse(details.get(0).items().get(1).matched());
    }

    /** 禁用条件：状态 DISABLED、passed=false（fail-close）、无逐项明细、不回填缓存 */
    @Test
    void shouldMarkDisabledConditionFailClose() {
        PermissionCondition disabled = condition(79L, "{\"logic\":\"AND\",\"items\":[]}");
        disabled.setEnabled(false);
        stubDbLoad(disabled);

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(503L, 22L, 79L, true)), Map.of());

        assertEquals(ConditionEvaluationDetail.STATUS_DISABLED, details.get(0).status());
        assertFalse(details.get(0).passed());
        assertTrue(details.get(0).items().isEmpty());
        verify(cacheService, never()).putBatch(any(CacheReadToken.class), anyLong(), any());
    }

    /** 条件不存在：NOT_FOUND fail-close；无条件条目不产生明细 */
    @Test
    void shouldSkipUnconditionedEntriesAndMarkNotFound() {
        stubDbLoad();

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(504L, 23L, null, false), entry(505L, 24L, 80L, true)), Map.of());

        assertEquals(1, details.size());
        assertEquals(ConditionEvaluationDetail.STATUS_NOT_FOUND, details.get(0).status());
        assertFalse(details.get(0).passed());
    }

    /** 空 items 数组维持旧运行时语义（T-PERM-033 评审定案）：AND=无条件满足放行、OR=无可满足项拒绝；
     * items 节点缺失/非数组恒拒绝——三态与重构前 evaluateCondition 逐分支一致 */
    @Test
    void shouldKeepLegacySemanticsForEmptyItems() {
        assertEmptyItemsPassed("AND", true);
        assertEmptyItemsPassed("OR", false);
        assertEmptyItemsPassed(null, true); // logic 缺省按 AND

        // items 节点缺失 → 恒拒绝（与空数组区分）
        stubDbLoad(condition(83L, "{\"logic\":\"AND\"}"));
        List<ConditionEvaluationDetail> missing = service.evaluateDetailed(TENANT,
            List.of(entry(507L, 26L, 83L, true)), Map.of());
        assertFalse(missing.get(0).passed());
    }

    private void assertEmptyItemsPassed(String logic, boolean expectedPassed) {
        long conditionId = logic == null ? 84L : 85L;
        String rules = logic == null
            ? "{\"items\":[]}"
            : "{\"logic\":\"" + logic + "\",\"items\":[]}";
        // 空数组与节点缺失共用一个 conditionId 桩位，逐个覆写
        stubDbLoad(condition(conditionId, rules));

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(508L, 27L, conditionId, true)), Map.of());

        assertEquals(expectedPassed, details.get(0).passed());
        assertTrue(details.get(0).items().isEmpty());
    }

    /** IPv4-mapped IPv6（::ffff:x.x.x.x）按点分段也是 4 段，须整体 MASKED（不泄露内嵌 IPv4）；
     * 非数字八位组（主机名样串）同样整体 MASKED（不泄露前两段） */
    @Test
    void shouldMaskIpv6AndIrregularCidrAsWhole() {
        stubDbLoad(condition(86L, """
            {"logic":"AND","items":[
              {"type":"IP_WHITELIST","params":{"cidrs":["::ffff:192.168.1.0/120","2001:db8::/32","finance.prod.secret.block/24"]}}]}
            """));

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(509L, 28L, 86L, true)), Map.of("clientIp", "192.168.1.55"));

        assertEquals("MASKED, MASKED, MASKED", details.get(0).items().get(0).maskedParams());
    }

    /** 缓存全命中：不查库、不回填（批量加载复用缓存路径） */
    @Test
    void shouldReuseCachedRulesWithoutDbHit() {
        JsonNode cached = readTree(
            "{\"logic\":\"AND\",\"items\":[{\"type\":\"TIME_RANGE\",\"params\":{\"start\":\"00:00:00\",\"end\":\"23:59:59\"}}]}");
        when(cacheService.getBatch(eq(PermCacheCatalog.CONDITION_RULES), eq(TENANT), anySet()))
            .thenReturn(Map.of(81L, cached));

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(506L, 25L, 81L, true)), Map.of());

        assertEquals(ConditionEvaluationDetail.STATUS_OK, details.get(0).status());
        assertEquals("00:00:00~23:59:59", details.get(0).items().get(0).maskedParams());
        verifyNoInteractions(conditionMapper);
        verify(cacheService, never()).putBatch(any(CacheReadToken.class), anyLong(), any());
    }

    /** 批量加载 + 同 ID 去重：多个条目共享/分属条件时，缓存 miss 后仅一次批量查库，
     * 同一 conditionId 不重复穿透（T-PERM-033 外部评审 N+1 修复锁） */
    @Test
    void shouldLoadConditionsInOneBatchQueryWithDedup() {
        stubDbLoad(condition(87L, "{\"logic\":\"AND\",\"items\":[]}"),
            condition(88L, "{\"logic\":\"OR\",\"items\":[]}"));

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT, List.of(
            entry(510L, 29L, 87L, true),
            entry(511L, 30L, 87L, true),
            entry(512L, 31L, 88L, true)), Map.of());

        assertEquals(3, details.size());
        verify(conditionMapper, times(1)).selectValidByIds(eq(TENANT), eq(Set.of(87L, 88L)));
        verify(cacheService, times(1)).getBatch(eq(PermCacheCatalog.CONDITION_RULES), eq(TENANT), anySet());
        // 两个条件各回填一次（批量一次调用）
        verify(cacheService, times(1)).putBatch(eq(readToken), eq(TENANT), any());
    }
}
