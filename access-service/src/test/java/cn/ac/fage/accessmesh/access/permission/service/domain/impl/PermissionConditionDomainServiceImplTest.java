package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.vo.ConditionEvaluationDetail;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionConditionDomainServiceImpl} 单元测试（T-PERM-033 evaluateDetailed）。
 * <p>
 * 锁定：逐项评估过程（logic AND/OR 聚合与运行时判定一致）、脱敏规则
 * （IP 掩码主机段、日期/时间原样）、条件加载状态（DISABLED/NOT_FOUND fail-close）、
 * 无条件条目不产生明细。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionConditionDomainServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock private PermissionConditionMapper conditionMapper;
    @Mock private CacheService cacheService;
    @Mock private CacheReadToken<com.fasterxml.jackson.databind.JsonNode> readToken;

    private PermissionConditionDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionConditionDomainServiceImpl(conditionMapper,
            new ObjectMapper(), cacheService);
        lenient().when(cacheService.beginRead(PermCacheCatalog.CONDITION_RULES)).thenReturn(readToken);
    }

    private RolePermEntry entry(Long permissionId, Long roleId, Long conditionId, boolean hasCondition) {
        return new RolePermEntry(permissionId, roleId, 200L, "sys:user", 1, 2L,
            "VIEW", 2L, "DIRECT", true, conditionId, hasCondition, null, false);
    }

    /** OK 状态 + AND 聚合逐项结果 + 脱敏：IP 掩码主机段、日期范围原样 */
    @Test
    void shouldEvaluateDetailedWithMaskedParams() {
        PermissionCondition condition = new PermissionCondition();
        condition.setId(77L);
        condition.setTenantId(TENANT);
        condition.setEnabled(true);
        condition.setConditionRules("""
            {"logic":"AND","items":[
              {"type":"IP_WHITELIST","params":{"cidrs":["192.168.1.0/24","10.20.30.40"]}},
              {"type":"DATE_RANGE","params":{"start":"2026-01-01","end":"2026-12-31"}}]}
            """);
        when(cacheService.get(PermCacheCatalog.CONDITION_RULES, TENANT, 77L)).thenReturn(null);
        when(conditionMapper.selectOneById(77L)).thenReturn(condition);

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
        verify(cacheService).put(eq(readToken), eq(TENANT), eq(77L), any());
    }

    /** OR 逻辑：任一项满足即通过（matched=条件项满足；黑名单命中 IP 反而不满足） */
    @Test
    void shouldAggregateOrLogicLikeRuntime() {
        PermissionCondition condition = new PermissionCondition();
        condition.setId(78L);
        condition.setTenantId(TENANT);
        condition.setEnabled(true);
        condition.setConditionRules("""
            {"logic":"OR","items":[
              {"type":"IP_WHITELIST","params":{"cidrs":["10.1.0.0/16"]}},
              {"type":"IP_BLACKLIST","params":{"cidrs":["10.0.0.0/8"]}}]}
            """);
        when(cacheService.get(PermCacheCatalog.CONDITION_RULES, TENANT, 78L)).thenReturn(null);
        when(conditionMapper.selectOneById(78L)).thenReturn(condition);

        // clientIp 命中白名单（条件项满足）且落入黑名单（条件项不满足）→ OR 整体通过
        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(502L, 21L, 78L, true)), Map.of("clientIp", "10.1.2.3"));

        assertTrue(details.get(0).passed());
        assertTrue(details.get(0).items().get(0).matched());
        assertFalse(details.get(0).items().get(1).matched());
    }

    /** 禁用条件：状态 DISABLED、passed=false（fail-close）、无逐项明细 */
    @Test
    void shouldMarkDisabledConditionFailClose() {
        PermissionCondition condition = new PermissionCondition();
        condition.setId(79L);
        condition.setTenantId(TENANT);
        condition.setEnabled(false);
        condition.setConditionRules("{\"logic\":\"AND\",\"items\":[]}");
        when(cacheService.get(PermCacheCatalog.CONDITION_RULES, TENANT, 79L)).thenReturn(null);
        when(conditionMapper.selectOneById(79L)).thenReturn(condition);

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(503L, 22L, 79L, true)), Map.of());

        assertEquals(ConditionEvaluationDetail.STATUS_DISABLED, details.get(0).status());
        assertFalse(details.get(0).passed());
        assertTrue(details.get(0).items().isEmpty());
    }

    /** 条件不存在：NOT_FOUND fail-close；无条件条目不产生明细 */
    @Test
    void shouldSkipUnconditionedEntriesAndMarkNotFound() {
        when(cacheService.get(PermCacheCatalog.CONDITION_RULES, TENANT, 80L)).thenReturn(null);
        when(conditionMapper.selectOneById(80L)).thenReturn(null);

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
        PermissionCondition missingItems = condition(83L, "{\"logic\":\"AND\"}");
        when(cacheService.get(PermCacheCatalog.CONDITION_RULES, TENANT, 83L)).thenReturn(null);
        when(conditionMapper.selectOneById(83L)).thenReturn(missingItems);
        List<ConditionEvaluationDetail> missing = service.evaluateDetailed(TENANT,
            List.of(entry(507L, 26L, 83L, true)), Map.of());
        assertFalse(missing.get(0).passed());
    }

    private void assertEmptyItemsPassed(String logic, boolean expectedPassed) {
        long conditionId = logic == null ? 84L : 85L;
        String rules = logic == null
            ? "{\"items\":[]}"
            : "{\"logic\":\"" + logic + "\",\"items\":[]}";
        when(cacheService.get(PermCacheCatalog.CONDITION_RULES, TENANT, conditionId)).thenReturn(null);
        when(conditionMapper.selectOneById(conditionId)).thenReturn(condition(conditionId, rules));

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(508L, 27L, conditionId, true)), Map.of());

        assertEquals(expectedPassed, details.get(0).passed());
        assertTrue(details.get(0).items().isEmpty());
    }

    /** IPv4-mapped IPv6（::ffff:x.x.x.x）按点分段也是 4 段，须整体 MASKED（不泄露内嵌 IPv4） */
    @Test
    void shouldMaskIpv4MappedIpv6CidrAsWhole() {
        PermissionCondition condition = condition(86L, """
            {"logic":"AND","items":[
              {"type":"IP_WHITELIST","params":{"cidrs":["::ffff:192.168.1.0/120","2001:db8::/32"]}}]}
            """);
        when(cacheService.get(PermCacheCatalog.CONDITION_RULES, TENANT, 86L)).thenReturn(null);
        when(conditionMapper.selectOneById(86L)).thenReturn(condition);

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(509L, 28L, 86L, true)), Map.of("clientIp", "192.168.1.55"));

        assertEquals("MASKED, MASKED", details.get(0).items().get(0).maskedParams());
    }

    private PermissionCondition condition(Long id, String rules) {
        PermissionCondition condition = new PermissionCondition();
        condition.setId(id);
        condition.setTenantId(TENANT);
        condition.setEnabled(true);
        condition.setConditionRules(rules);
        return condition;
    }

    /** 缓存命中：不查 DB、不回填（与 evaluate 共用加载路径） */
    @Test
    void shouldReuseCachedRulesWithoutDbHit() throws Exception {
        var cached = new ObjectMapper().readTree(
            "{\"logic\":\"AND\",\"items\":[{\"type\":\"TIME_RANGE\",\"params\":{\"start\":\"00:00:00\",\"end\":\"23:59:59\"}}]}");
        when(cacheService.get(PermCacheCatalog.CONDITION_RULES, TENANT, 81L)).thenReturn(cached);

        List<ConditionEvaluationDetail> details = service.evaluateDetailed(TENANT,
            List.of(entry(506L, 25L, 81L, true)), Map.of());

        assertEquals(ConditionEvaluationDetail.STATUS_OK, details.get(0).status());
        assertEquals("00:00:00~23:59:59", details.get(0).items().get(0).maskedParams());
        verifyNoInteractions(conditionMapper);
        verify(cacheService, never()).put(any(CacheReadToken.class), anyLong(), anyLong(), any());
    }
}
