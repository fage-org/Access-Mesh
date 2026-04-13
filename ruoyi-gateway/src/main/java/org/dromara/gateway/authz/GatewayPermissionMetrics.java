package org.dromara.gateway.authz;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gateway 权限指标收集器
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Component
public class GatewayPermissionMetrics {

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
     * 鉴权拒绝计数器
     */
    private final AtomicLong denials = new AtomicLong(0);

    /**
     * HTTP 调用失败计数器
     */
    private final AtomicLong httpFailures = new AtomicLong(0);

    public GatewayPermissionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 注册 Gauge
        Gauge.builder("gateway.permission.cache.hits", cacheHits, AtomicLong::get)
            .description("Gateway permission cache hits")
            .register(meterRegistry);

        Gauge.builder("gateway.permission.cache.misses", cacheMisses, AtomicLong::get)
            .description("Gateway permission cache misses")
            .register(meterRegistry);

        Gauge.builder("gateway.permission.denials", denials, AtomicLong::get)
            .description("Gateway permission denials")
            .register(meterRegistry);

        Gauge.builder("gateway.permission.http.failures", httpFailures, AtomicLong::get)
            .description("Gateway permission HTTP call failures")
            .register(meterRegistry);

        log.info("GatewayPermissionMetrics initialized");
    }

    /**
     * 记录鉴权请求
     *
     * @param tenantId   租户ID
     * @param granted    是否通过
     * @param durationMs 耗时（毫秒）
     */
    public void recordAuthorization(String tenantId, boolean granted, long durationMs) {
        Counter.builder("gateway.permission.requests")
            .tag("tenant.id", tenantId)
            .tag("result", granted ? "granted" : "denied")
            .description("Gateway permission authorization requests")
            .register(meterRegistry)
            .increment();

        Timer.builder("gateway.permission.duration")
            .tag("tenant.id", tenantId)
            .description("Gateway permission authorization duration")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);

        if (!granted) {
            denials.incrementAndGet();
        }
    }

    /**
     * 记录鉴权拒绝
     *
     * @param tenantId   租户ID
     * @param subjectKey 主体标识
     * @param route      路由
     * @param reason     拒绝原因
     */
    public void recordDenial(String tenantId, String subjectKey, String route, String reason) {
        denials.incrementAndGet();

        Counter.builder("gateway.permission.denial.details")
            .tag("tenant.id", tenantId)
            .tag("deny.reason", reason)
            .description("Gateway permission denial details")
            .register(meterRegistry)
            .increment();

        log.warn("Permission denied: tenant={}, subject={}, route={}, reason={}",
            tenantId, subjectKey, route, reason);
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
     * 记录 HTTP 调用
     *
     * @param success    是否成功
     * @param durationMs 耗时（毫秒）
     */
    public void recordHttpCall(boolean success, long durationMs) {
        Counter.builder("gateway.permission.http.calls")
            .tag("status", success ? "success" : "failure")
            .description("Gateway permission HTTP calls")
            .register(meterRegistry)
            .increment();

        Timer.builder("gateway.permission.http.duration")
            .tag("status", success ? "success" : "failure")
            .description("Gateway permission HTTP call duration")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);

        if (!success) {
            httpFailures.incrementAndGet();
        }
    }

    /**
     * 记录灰度跳过
     *
     * @param tenantId 租户ID
     * @param route    路由
     * @param reason   跳过原因
     */
    public void recordGrayscaleSkip(String tenantId, String route, String reason) {
        Counter.builder("gateway.permission.grayscale.skip")
            .tag("tenant.id", tenantId)
            .tag("reason", reason)
            .description("Gateway permission grayscale skip")
            .register(meterRegistry)
            .increment();

        log.debug("Grayscale skip: tenant={}, route={}, reason={}", tenantId, route, reason);
    }

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
        return String.format("cacheHits=%d, cacheMisses=%d, cacheHitRate=%.2f%%, denials=%d, httpFailures=%d",
            cacheHits.get(), cacheMisses.get(), getCacheHitRate() * 100, denials.get(), httpFailures.get());
    }
}
