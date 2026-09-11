package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.BatchConditionEvaluator;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BatchConditionEvaluator}（T-PERM-061 请求级批量条件快照）单元测试。
 * <p>
 * 锁四态 fail-close 语义（DISABLED/NOT_FOUND/INVALID 拒绝——朴素批量把禁用条件当
 * 有效规则入正缓存 = 权限绕过方向，回归锁⑨的单测面）、增量装载（同 ID 至多回源一次，
 * 回归锁⑪的单测面）与「仅 OK 入正缓存」口径。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class BatchConditionEvaluatorTest {

    private static final Long TENANT = 1L;

    @Mock private PermissionConditionMapper conditionMapper;
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    @Mock private CacheService cacheService;
    @Mock private CacheReadToken<JsonNode> readToken;

    private PermissionConditionDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionConditionDomainServiceImpl(conditionMapper,
            rolePermMapper, new ObjectMapper(), cacheService);
        lenient().when(cacheService.beginRead(PermCacheCatalog.CONDITION_RULES)).thenReturn(readToken);
        // 默认缓存全 miss（各用例按需覆写为命中）
        lenient().when(cacheService.getBatch(eq(PermCacheCatalog.CONDITION_RULES), eq(TENANT), anySet()))
            .thenReturn(Map.of());
    }

    private RolePermEntry conditionedEntry(Long conditionId) {
        return new RolePermEntry(401L, 20L, 200L, "sys:user", 1, 2L,
            "VIEW", 2L, "MANUAL", false, conditionId, true, null, false);
    }

    private PermissionCondition dbCondition(Long id, boolean enabled, String rules) {
        PermissionCondition condition = new PermissionCondition();
        condition.setId(id);
        condition.setTenantId(TENANT);
        condition.setEnabled(enabled);
        condition.setConditionRules(rules);
        return condition;
    }

    /** 规则体恒真（AND 空 items = 无条件满足） */
    private static final String ALWAYS_TRUE_RULES = "{\"logic\":\"AND\",\"items\":[]}";

    @Test
    void disabledConditionMustFailCloseInBatchSnapshot() {
        // 禁用条件（enabled=false）且规则体可评估为真 → 批量评估必须拒绝（朴素批量把禁用
        // 条件当有效规则入缓存=权限绕过方向——RED 在朴素实现下失败）
        when(conditionMapper.selectValidByIds(eq(TENANT), eq(Set.of(10L))))
            .thenReturn(List.of(dbCondition(10L, false, ALWAYS_TRUE_RULES)));

        BatchConditionEvaluator evaluator = service.openBatchEvaluator(TENANT);
        List<RolePermEntry> passed = evaluator.evaluate(TENANT,
            List.of(conditionedEntry(10L)), Map.of());

        assertEquals(List.of(), passed, "禁用条件必须 fail-close 拒绝（规则体为真也不放行）");
        // 仅 OK 入正缓存：DISABLED 不回填
        verify(cacheService, never()).putBatch(any(CacheReadToken.class), eq(TENANT), any());
    }

    @Test
    void missingAndInvalidConditionsMustFailCloseInBatchSnapshot() {
        // NOT_FOUND（回源缺行）与 INVALID（规则体解析失败）→ 拒绝
        when(conditionMapper.selectValidByIds(eq(TENANT), eq(Set.of(10L, 11L))))
            .thenReturn(List.of(dbCondition(11L, true, "{not-json")));

        BatchConditionEvaluator evaluator = service.openBatchEvaluator(TENANT);
        List<RolePermEntry> passed = evaluator.evaluate(TENANT,
            List.of(conditionedEntry(10L), conditionedEntry(11L)), Map.of());

        assertEquals(List.of(), passed, "NOT_FOUND / INVALID 均须 fail-close 拒绝");
        verify(cacheService, never()).putBatch(any(CacheReadToken.class), eq(TENANT), any());
    }

    @Test
    void okConditionMustPassAndBeCachedWithRemainingTtl() {
        when(conditionMapper.selectValidByIds(eq(TENANT), eq(Set.of(10L))))
            .thenReturn(List.of(dbCondition(10L, true, ALWAYS_TRUE_RULES)));

        BatchConditionEvaluator evaluator = service.openBatchEvaluator(TENANT);
        List<RolePermEntry> passed = evaluator.evaluate(TENANT,
            List.of(conditionedEntry(10L)), Map.of());

        assertEquals(1, passed.size(), "OK 条件（规则体为真）放行");
        // 仅 OK 入正缓存（putBatch 带 beginRead 剩余 TTL）
        verify(cacheService).putBatch(eq(readToken), eq(TENANT), argThat(map ->
            map != null && map.containsKey(10L)));
    }

    @Test
    void sameConditionMustLoadAtMostOnceAcrossStages() {
        // 增量装载：同 conditionId 跨阶段（scopeAll 段 + 实例段）至多回源一次
        when(conditionMapper.selectValidByIds(eq(TENANT), eq(Set.of(10L))))
            .thenReturn(List.of(dbCondition(10L, true, ALWAYS_TRUE_RULES)));

        BatchConditionEvaluator evaluator = service.openBatchEvaluator(TENANT);
        evaluator.preload(TENANT, Set.of(10L));   // scopeAll 段
        evaluator.preload(TENANT, Set.of(10L));   // 实例段（快照已含——零 IO）
        evaluator.evaluate(TENANT, List.of(conditionedEntry(10L)), Map.of());  // 评估兜底同样零回源

        verify(conditionMapper, times(1)).selectValidByIds(eq(TENANT), anySet());
    }

    @Test
    void secondStageMustNotHitCacheWhenSnapshotAlreadyCovers() {
        // 快照已覆盖时第二阶段不产生 getBatch 调用（每阶段至多一批次的零 IO 侧面）
        when(conditionMapper.selectValidByIds(eq(TENANT), eq(Set.of(10L))))
            .thenReturn(List.of(dbCondition(10L, true, ALWAYS_TRUE_RULES)));

        BatchConditionEvaluator evaluator = service.openBatchEvaluator(TENANT);
        evaluator.preload(TENANT, Set.of(10L));
        evaluator.preload(TENANT, Set.of(10L));

        verify(cacheService, times(1)).getBatch(eq(PermCacheCatalog.CONDITION_RULES), eq(TENANT), anySet());
    }

    @Test
    void cachedHitMustShortCircuitDbLoad() {
        // 缓存命中（=OK）不回源 DB；命中规则体直接参与评估（恒真体放行）
        JsonNode rules;
        try {
            rules = new ObjectMapper().readTree(ALWAYS_TRUE_RULES);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        lenient().when(cacheService.getBatch(eq(PermCacheCatalog.CONDITION_RULES), eq(TENANT), eq(Set.of(10L))))
            .thenReturn(Map.of(10L, rules));

        BatchConditionEvaluator evaluator = service.openBatchEvaluator(TENANT);
        List<RolePermEntry> passed = evaluator.evaluate(TENANT,
            List.of(conditionedEntry(10L)), Map.of());

        assertEquals(1, passed.size());
        verify(conditionMapper, never()).selectValidByIds(any(), any());
    }
}
