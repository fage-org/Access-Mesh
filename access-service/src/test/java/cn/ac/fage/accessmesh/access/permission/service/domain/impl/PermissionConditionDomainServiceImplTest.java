package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionConditionDomainServiceImpl} 单元测试（保留面 evaluate 运行时路径）。
 * <p>
 * 原 evaluateDetailed 明细用例族已随 explain 端点删除（T-PERM-059，2026-09-10）；
 * 本文件重写为 evaluate（引擎运行时条件过滤）驱动的行为锁：AND/OR 聚合与运行时判定一致、
 * 空 items 三态维持旧语义、DISABLED/NOT_FOUND fail-close、无条件条目直通。
 * （引擎单测对 ConditionDomainService 用直通桩，不覆盖真实评估——此处是唯一行为锁。）
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionConditionDomainServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock private PermissionConditionMapper conditionMapper;
    @Mock private cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper rolePermMapper;
    @Mock private CacheService cacheService;
    @Mock private CacheReadToken<JsonNode> readToken;

    private PermissionConditionDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionConditionDomainServiceImpl(conditionMapper,
            rolePermMapper, new ObjectMapper(), cacheService);
        lenient().when(cacheService.beginRead(PermCacheCatalog.CONDITION_RULES)).thenReturn(readToken);
        // 默认缓存全 miss（loadRules 单条路径走 get；各用例按需覆写为命中）
        lenient().when(cacheService.get(eq(PermCacheCatalog.CONDITION_RULES), eq(TENANT), anyLong()))
            .thenReturn(null);
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

    /** 缓存 miss 桩：单条查库返回给定条件实体 */
    private void stubDbLoad(PermissionCondition... conditions) {
        for (PermissionCondition condition : conditions) {
            lenient().when(conditionMapper.selectOneById(condition.getId())).thenReturn(condition);
        }
    }

    /** AND 聚合：全项满足保留条目，任一项不满足丢弃（运行时判定口径） */
    @Test
    void shouldEvaluateAndConditionLikeRuntime() {
        stubDbLoad(condition(77L, """
            {"logic":"AND","items":[
              {"type":"IP_WHITELIST","params":{"cidrs":["192.168.1.0/24","10.20.30.40"]}},
              {"type":"DATE_RANGE","params":{"start":"2020-01-01","end":"2020-12-31"}}]}
            """));

        // clientIp 命中白名单但日期范围已过 → AND 整体不满足 → 条目被丢弃
        List<RolePermEntry> kept = service.evaluate(TENANT,
            List.of(entry(501L, 20L, 77L, true)), Map.of("clientIp", "192.168.1.55"));

        assertTrue(kept.isEmpty(), "AND 任一项不满足须丢弃挂条件条目");

        // clientIp 命中白名单且换覆盖当前年份的范围 → AND 整体满足 → 条目保留
        stubDbLoad(condition(77L, """
            {"logic":"AND","items":[
              {"type":"IP_WHITELIST","params":{"cidrs":["192.168.1.0/24"]}},
              {"type":"DATE_RANGE","params":{"start":"2020-01-01","end":"2099-12-31"}}]}
            """));
        List<RolePermEntry> keptAll = service.evaluate(TENANT,
            List.of(entry(501L, 20L, 77L, true)), Map.of("clientIp", "192.168.1.55"));
        assertEquals(1, keptAll.size(), "AND 全项满足须保留挂条件条目");
    }

    /** OR 逻辑：任一项满足即通过（白名单过+黑名单命中→整体仍过），与运行时判定一致 */
    @Test
    void shouldAggregateOrLogicLikeRuntime() {
        stubDbLoad(condition(78L, """
            {"logic":"OR","items":[
              {"type":"IP_WHITELIST","params":{"cidrs":["10.1.0.0/16"]}},
              {"type":"IP_BLACKLIST","params":{"cidrs":["10.0.0.0/8"]}}]}
            """));

        // clientIp 命中白名单（条件项满足）且落入黑名单（条件项不满足）→ OR 整体通过 → 条目保留
        List<RolePermEntry> kept = service.evaluate(TENANT,
            List.of(entry(502L, 21L, 78L, true)), Map.of("clientIp", "10.1.2.3"));

        assertEquals(1, kept.size(), "OR 任一项满足须保留挂条件条目");

        // clientIp 不在白名单且落入黑名单（黑名单命中=条件项不满足）→ 两项均不满足 → OR 整体拒绝 → 条目丢弃
        List<RolePermEntry> dropped = service.evaluate(TENANT,
            List.of(entry(502L, 21L, 78L, true)), Map.of("clientIp", "10.2.3.4"));
        assertTrue(dropped.isEmpty(), "OR 全项不满足须丢弃挂条件条目");
    }

    /** 加载状态 fail-close：DISABLED / NOT_FOUND（跨租户含）→ 条目丢弃；无条件条目直通 */
    @Test
    void shouldFailCloseOnDisabledOrMissingCondition() {
        PermissionCondition disabled = condition(77L, "{}");
        disabled.setEnabled(false);
        stubDbLoad(disabled, condition(78L, "{\"logic\":\"AND\",\"items\":[]}"));

        // 77=禁用 → fail-close 丢弃；78 不在库（跨租户/已删）→ NOT_FOUND 丢弃；79 无条件 → 直通
        when(conditionMapper.selectOneById(78L)).thenReturn(null);

        List<RolePermEntry> kept = service.evaluate(TENANT, List.of(
            entry(501L, 20L, 77L, true),
            entry(502L, 21L, 78L, true),
            entry(503L, 22L, null, false)),
            Map.of("clientIp", "10.1.2.3"));

        assertEquals(1, kept.size(), "仅无条件条目直通，禁用/缺失条件条目 fail-close 丢弃");
        assertEquals(503L, kept.get(0).permissionId());
    }

    /** 空 items 三态维持旧语义（runbook 登记项的行为基线）：AND 空=无条件满足放行；OR 空=拒绝 */
    @Test
    void shouldKeepLegacySemanticsForEmptyItems() {
        stubDbLoad(
            condition(77L, "{\"logic\":\"AND\",\"items\":[]}"),
            condition(78L, "{\"logic\":\"OR\",\"items\":[]}"));

        List<RolePermEntry> kept = service.evaluate(TENANT, List.of(
            entry(501L, 20L, 77L, true),
            entry(502L, 21L, 78L, true)),
            Map.of("clientIp", "10.1.2.3"));

        assertEquals(1, kept.size(), "AND 空 items 放行、OR 空 items 拒绝（旧语义不变）");
        assertEquals(501L, kept.get(0).permissionId());
    }

    // ===== T-PERM-048 内联回收：来源过滤与引用归零判定 =====

    private PermissionCondition conditionWithSource(long id, String source) {
        PermissionCondition condition = condition(Long.valueOf(id), "{\"logic\":\"AND\",\"items\":[]}");
        condition.setSource(source);
        return condition;
    }

    @org.junit.jupiter.api.Test
    void recycleShouldFilterManagedAndSkipReferenced() {
        // 候选 {9=MANAGED, 10=INLINE}，均无引用 → 仅 INLINE 被软删（MANAGED 管理页条件不受
        // 授权页换绑/删行影响——误删该过滤会把管理页条件静默蒸发，本锁在旧实现下必失败于 9 被删）
        when(conditionMapper.selectValidByIds(eq(TENANT), eq(java.util.Set.of(9L, 10L))))
            .thenReturn(java.util.List.of(
                conditionWithSource(9L, "MANAGED"), conditionWithSource(10L, "INLINE")));
        when(rolePermMapper.selectReferencedConditionIds(eq(TENANT), eq(java.util.Set.of(10L))))
            .thenReturn(java.util.Set.of());

        java.util.Set<Long> recycled = service.recycleOrphanInlineConditions(TENANT, java.util.Set.of(9L, 10L));

        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(10L), recycled);
        verify(conditionMapper).softDeleteBatch(eq(TENANT),
            argThat((java.util.List<Long> ids) -> ids.size() == 1 && ids.contains(10L)), org.mockito.ArgumentMatchers.any());
    }

    @org.junit.jupiter.api.Test
    void recycleShouldSkipStillReferencedInline() {
        // 仍被有效授权行引用的 INLINE 跳过不删（防御分支：conditionCode 引用轨已 20060 焊死）
        when(conditionMapper.selectValidByIds(eq(TENANT), eq(java.util.Set.of(10L))))
            .thenReturn(java.util.List.of(conditionWithSource(10L, "INLINE")));
        when(rolePermMapper.selectReferencedConditionIds(eq(TENANT), eq(java.util.Set.of(10L))))
            .thenReturn(java.util.Set.of(10L));

        java.util.Set<Long> recycled = service.recycleOrphanInlineConditions(TENANT, java.util.Set.of(10L));

        org.junit.jupiter.api.Assertions.assertTrue(recycled.isEmpty());
        verify(conditionMapper, never()).softDeleteBatch(anyLong(), anyList(), org.mockito.ArgumentMatchers.any());
    }
}
