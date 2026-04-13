package org.dromara.permission.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 权限中心指标收集器
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Component
public class PermissionMetricsCollector {

    private final MeterRegistry meterRegistry;

    /**
     * 缓存命中计数器
     */
    private final AtomicLong cacheHits = new AtomicLong(0);

    /**
     * 缓存未命中计数器
     */
    private final AtomicLong cacheMisses = new AtomicLong(0);

    /**
     * 缓存大小
     */
    private final AtomicLong cacheSize = new AtomicLong(0);

    /**
     * 租户级计数器缓存
     */
    private final ConcurrentMap<String, AtomicLong> tenantCounters = new ConcurrentHashMap<>();

    public PermissionMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 注册缓存命中率 Gauge
        Gauge.builder(PermissionMetrics.SNAPSHOT_CACHE_HITS, cacheHits, AtomicLong::get)
            .description("Permission snapshot cache hits")
            .register(meterRegistry);

        Gauge.builder(PermissionMetrics.SNAPSHOT_CACHE_MISSES, cacheMisses, AtomicLong::get)
            .description("Permission snapshot cache misses")
            .register(meterRegistry);

        Gauge.builder(PermissionMetrics.SNAPSHOT_CACHE_SIZE, cacheSize, AtomicLong::get)
            .description("Permission snapshot cache size")
            .register(meterRegistry);

        log.info("PermissionMetricsCollector initialized");
    }

    // ==================== 鉴权指标 ====================

    /**
     * 记录鉴权请求
     *
     * @param tenantId 租户ID
     * @param granted  是否通过
     * @param durationMs 耗时（毫秒）
     */
    public void recordCheck(String tenantId, boolean granted, long durationMs) {
        Counter.builder(PermissionMetrics.CHECK_REQUESTS)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .tag(PermissionMetrics.TAG_RESULT, granted ? "granted" : "denied")
            .description("Permission check requests")
            .register(meterRegistry)
            .increment();

        Timer.builder(PermissionMetrics.CHECK_DURATION)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .description("Permission check duration")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);

        if (!granted) {
            Counter.builder(PermissionMetrics.CHECK_DENIALS)
                .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
                .description("Permission check denials")
                .register(meterRegistry)
                .increment();
        }
    }

    /**
     * 记录鉴权拒绝
     *
     * @param tenantId   租户ID
     * @param denyReason 拒绝原因
     */
    public void recordDenial(String tenantId, String denyReason) {
        Counter.builder(PermissionMetrics.CHECK_DENIALS)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .tag(PermissionMetrics.TAG_DENY_REASON, denyReason)
            .description("Permission check denials")
            .register(meterRegistry)
            .increment();
    }

    // ==================== 快照指标 ====================

    /**
     * 记录快照请求
     *
     * @param tenantId   租户ID
     * @param success    是否成功
     * @param durationMs 耗时（毫秒）
     */
    public void recordSnapshotRequest(String tenantId, boolean success, long durationMs) {
        Counter.builder(PermissionMetrics.SNAPSHOT_REQUESTS)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .tag(PermissionMetrics.TAG_STATUS, success ? "success" : "failure")
            .description("Permission snapshot requests")
            .register(meterRegistry)
            .increment();

        Timer.builder(PermissionMetrics.SNAPSHOT_DURATION)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .tag(PermissionMetrics.TAG_STATUS, success ? "success" : "failure")
            .description("Permission snapshot duration")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录缓存命中
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    /**
     * 更新缓存大小
     *
     * @param size 当前缓存大小
     */
    public void updateCacheSize(long size) {
        cacheSize.set(size);
    }

    // ==================== 版本指标 ====================

    /**
     * 记录版本查询
     *
     * @param tenantId 租户ID
     * @param success  是否成功
     */
    public void recordVersionQuery(String tenantId, boolean success) {
        Counter.builder(PermissionMetrics.VERSION_REQUESTS)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .tag(PermissionMetrics.TAG_STATUS, success ? "success" : "failure")
            .description("Permission version query requests")
            .register(meterRegistry)
            .increment();

        if (!success) {
            Counter.builder(PermissionMetrics.VERSION_FAILURES)
                .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
                .description("Permission version query failures")
                .register(meterRegistry)
                .increment();
        }
    }

    /**
     * 记录版本变更
     *
     * @param tenantId 租户ID
     */
    public void recordVersionChange(String tenantId) {
        Counter.builder(PermissionMetrics.VERSION_CHANGES)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .description("Permission version changes")
            .register(meterRegistry)
            .increment();
    }

    // ==================== 条件指标 ====================

    /**
     * 记录条件求值
     *
     * @param tenantId      租户ID
     * @param conditionType 条件类型
     * @param success       是否成功
     * @param durationMs    耗时（毫秒）
     */
    public void recordConditionEvaluation(String tenantId, String conditionType, boolean success, long durationMs) {
        Counter.builder(PermissionMetrics.CONDITION_EVALUATIONS)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .tag(PermissionMetrics.TAG_CONDITION_TYPE, conditionType)
            .tag(PermissionMetrics.TAG_STATUS, success ? "success" : "failure")
            .description("Permission condition evaluations")
            .register(meterRegistry)
            .increment();

        Timer.builder(PermissionMetrics.CONDITION_DURATION)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .tag(PermissionMetrics.TAG_CONDITION_TYPE, conditionType)
            .description("Permission condition evaluation duration")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);

        if (!success) {
            Counter.builder(PermissionMetrics.CONDITION_ERRORS)
                .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
                .tag(PermissionMetrics.TAG_CONDITION_TYPE, conditionType)
                .description("Permission condition evaluation errors")
                .register(meterRegistry)
                .increment();
        }
    }

    // ==================== 冲突指标 ====================

    /**
     * 记录冲突检测
     *
     * @param tenantId  租户ID
     * @param hasConflict 是否有冲突
     */
    public void recordConflictCheck(String tenantId, boolean hasConflict) {
        Counter.builder(PermissionMetrics.CONFLICT_CHECKS)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .tag(PermissionMetrics.TAG_RESULT, hasConflict ? "conflict" : "clean")
            .description("Permission conflict checks")
            .register(meterRegistry)
            .increment();

        if (hasConflict) {
            Counter.builder(PermissionMetrics.CONFLICT_HITS)
                .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
                .description("Permission conflict hits")
                .register(meterRegistry)
                .increment();
        }
    }

    // ==================== 依赖指标 ====================

    /**
     * 记录依赖检查
     *
     * @param tenantId    租户ID
     * @param satisfied   是否满足
     */
    public void recordDependencyCheck(String tenantId, boolean satisfied) {
        Counter.builder(PermissionMetrics.DEPENDENCY_CHECKS)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .tag(PermissionMetrics.TAG_RESULT, satisfied ? "satisfied" : "gap")
            .description("Permission dependency checks")
            .register(meterRegistry)
            .increment();

        if (!satisfied) {
            Counter.builder(PermissionMetrics.DEPENDENCY_FAILURES)
                .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
                .description("Permission dependency check failures")
                .register(meterRegistry)
                .increment();
        }
    }

    // ==================== 审计指标 ====================

    /**
     * 记录审计写入
     *
     * @param tenantId 租户ID
     * @param success  是否成功
     */
    public void recordAuditWrite(String tenantId, boolean success) {
        Counter.builder(PermissionMetrics.AUDIT_WRITES)
            .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
            .tag(PermissionMetrics.TAG_STATUS, success ? "success" : "failure")
            .description("Permission audit writes")
            .register(meterRegistry)
            .increment();

        if (!success) {
            Counter.builder(PermissionMetrics.AUDIT_FAILURES)
                .tag(PermissionMetrics.TAG_TENANT_ID, tenantId)
                .description("Permission audit write failures")
                .register(meterRegistry)
                .increment();
        }
    }

    // ==================== 统计信息 ====================

    /**
     * 获取缓存命中率
     *
     * @return 缓存命中率（0-1）
     */
    public double getCacheHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        return String.format("cacheHits=%d, cacheMisses=%d, cacheHitRate=%.2f%%, cacheSize=%d",
            cacheHits.get(), cacheMisses.get(), getCacheHitRate() * 100, cacheSize.get());
    }
}
