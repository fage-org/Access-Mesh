package cn.ac.fage.accessmesh.access.admin.sync.scheduler;

import cn.ac.fage.accessmesh.access.admin.sync.SyncTaskBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同步任务调度器配置属性
 * <p>
 * 配置前缀：{@code accessmesh.sync.scheduler}。
 * 退避策略：
 * <ul>
 *   <li>RETRYABLE 指数退避：{@code retryableBaseSeconds * 2^retryCount}，封顶 {@code retryableMaxSeconds}</li>
 *   <li>DEPENDENCY_MISSING 短退避：{@code dependencyMissingBaseSeconds * 2^retryCount}，
 *       封顶 {@code dependencyMissingMaxSeconds}</li>
 * </ul>
 *
 * <h3>stale-lock-timeout 按 syncAction 维度配置（P6）</h3>
 * 通过 {@code accessmesh.sync.scheduler.stale-lock-timeout} 提供 map：
 * <pre>{@code
 * accessmesh:
 *   sync:
 *     scheduler:
 *       stale-lock-timeout:
 *         default: 60                    # 兜底，单位秒
 *         PERM_ABSTRACT_USER_SYNC: 60
 *         PERM_ABSTRACT_ROLE_SYNC: 60
 *         PERM_USER_ROLE_SYNC: 60
 *         PERM_RESOURCE_ENTITY_SYNC: 60
 *         full-sync: 300                 # 全量校准任务过期阈值
 * }</pre>
 * 查询通过 {@link #getTimeoutFor(String)}，回退顺序：syncAction → "default" → 60s。
 */
@ConfigurationProperties(prefix = "accessmesh.sync.scheduler")
public class SyncSchedulerProperties {

    /** stale-lock-timeout map 默认 key。 */
    public static final String DEFAULT_KEY = "default";

    public static final String FULL_SYNC_KEY = "full-sync";

    /** stale-lock-timeout 兜底值（秒）。 */
    public static final long FALLBACK_TIMEOUT_SECONDS = 60L;

    public static final long FULL_SYNC_TIMEOUT_SECONDS = 300L;

    /** 是否启用调度器；默认启用，测试可关闭。 */
    private boolean enabled = true;

    /** 调度间隔（毫秒）。 */
    private long fixedDelayMs = 5000L;

    /** 单次拉取上限。 */
    private int batchSize = 50;

    /** RETRYABLE 退避基数（秒）。 */
    private int retryableBaseSeconds = 5;

    /** RETRYABLE 退避封顶（秒）。 */
    private int retryableMaxSeconds = 1800;

    /** DEPENDENCY_MISSING 退避基数（秒）。 */
    private int dependencyMissingBaseSeconds = 5;

    /** DEPENDENCY_MISSING 退避封顶（秒）。 */
    private int dependencyMissingMaxSeconds = 30;

    /**
     * PROCESSING 锁超时阈值（秒），按 syncAction 维度配置。
     * <p>
     * key=syncAction（如 {@code PERM_ABSTRACT_USER_SYNC} / {@code full-sync}），
     * value=超时秒数。{@code default} key 提供未匹配时的兜底；缺失时进一步回退到
     * {@link #FALLBACK_TIMEOUT_SECONDS}=60s。
     * </p>
     */
    private Map<String, Long> staleLockTimeout = defaultStaleLockTimeout();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getFixedDelayMs() { return fixedDelayMs; }
    public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getRetryableBaseSeconds() { return retryableBaseSeconds; }
    public void setRetryableBaseSeconds(int retryableBaseSeconds) { this.retryableBaseSeconds = retryableBaseSeconds; }
    public int getRetryableMaxSeconds() { return retryableMaxSeconds; }
    public void setRetryableMaxSeconds(int retryableMaxSeconds) { this.retryableMaxSeconds = retryableMaxSeconds; }
    public int getDependencyMissingBaseSeconds() { return dependencyMissingBaseSeconds; }
    public void setDependencyMissingBaseSeconds(int s) { this.dependencyMissingBaseSeconds = s; }
    public int getDependencyMissingMaxSeconds() { return dependencyMissingMaxSeconds; }
    public void setDependencyMissingMaxSeconds(int s) { this.dependencyMissingMaxSeconds = s; }
    public Map<String, Long> getStaleLockTimeout() { return staleLockTimeout; }
    public void setStaleLockTimeout(Map<String, Long> staleLockTimeout) {
        Map<String, Long> merged = defaultStaleLockTimeout();
        if (staleLockTimeout != null) {
            merged.putAll(staleLockTimeout);
        }
        this.staleLockTimeout = merged;
    }

    /**
     * 根据 syncAction 获取超时时长。
     * <p>回退顺序：syncAction → {@code "default"} → {@link #FALLBACK_TIMEOUT_SECONDS}=60s。</p>
     *
     * @param syncAction 同步动作（如 {@code PERM_ABSTRACT_USER_SYNC}）；可为 {@code null}
     * @return 超时 {@link Duration}，永不为 {@code null}
     */
    public Duration getTimeoutFor(String syncAction) {
        if (staleLockTimeout != null) {
            if (syncAction != null) {
                Long v = staleLockTimeout.get(syncAction);
                if (v != null) {
                    return Duration.ofSeconds(v);
                }
            }
            Long def = staleLockTimeout.get(DEFAULT_KEY);
            if (def != null) {
                return Duration.ofSeconds(def);
            }
        }
        return Duration.ofSeconds(FALLBACK_TIMEOUT_SECONDS);
    }

    private static Map<String, Long> defaultStaleLockTimeout() {
        Map<String, Long> defaults = new LinkedHashMap<>();
        defaults.put(DEFAULT_KEY, FALLBACK_TIMEOUT_SECONDS);
        defaults.put(SyncTaskBuilder.ACTION_ABSTRACT_USER_SYNC, FALLBACK_TIMEOUT_SECONDS);
        defaults.put(SyncTaskBuilder.ACTION_ABSTRACT_ROLE_SYNC, FALLBACK_TIMEOUT_SECONDS);
        defaults.put(SyncTaskBuilder.ACTION_USER_ROLE_SYNC, FALLBACK_TIMEOUT_SECONDS);
        defaults.put(SyncTaskBuilder.ACTION_RESOURCE_ENTITY_SYNC, FALLBACK_TIMEOUT_SECONDS);
        defaults.put(FULL_SYNC_KEY, FULL_SYNC_TIMEOUT_SECONDS);
        return defaults;
    }
}
