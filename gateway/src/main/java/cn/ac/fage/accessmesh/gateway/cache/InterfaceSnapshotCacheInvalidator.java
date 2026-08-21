package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gateway 本地接口快照缓存失效器（T-PERM-006 / T-ACCESS-008）
 * <p>
 * 根据 access-service 广播的 {@link PermInvalidateEvent} 清理本地
 * {@code INTERFACE_SNAPSHOT}（统一 CacheService，L1_ONLY）。Redis pub/sub 只保证尽力送达，
 * 丢失时由 TTL 兜底（≤15s）。
 * </p>
 * <p>
 * T-ACCESS-008 用户决策（2026-08-21）：失效粒度取——
 * <ul>
 *   <li>{@code userIds} 非空且无 {@code serviceCodes}/{@code roleIds}：按用户精确清理
 *       （{@code evict(identifier)}，跟踪索引 + 在途回源注册表枚举该用户全部 identifier）</li>
 *   <li>其余情况（{@code serviceCodes} 非空或仅 {@code roleIds} 非空）：租户级
 *       {@code evictAll(catalog, tenantId)} 安全兜底——Gateway 无法本地反查服务/角色
 *       影响的用户集合，过度失效方向安全；回源惊群由 per-key in-flight 去重缓解</li>
 *   <li>订阅重连：catalog 级跨租户全清 {@code evictAll(catalog)}（不依赖租户枚举）</li>
 * </ul>
 * </p>
 * <p>
 * <b>失效与代际顺序（复评 P1 修复）</b>：所有失效先递增代际/标记（作废全部在途
 * {@code LoadToken}），再清理缓存条目——保证失效前启动的旧回源无法在清理后重新写入
 * （旧回源不得复活已撤销权限）。
 * </p>
 */
@Component
public class InterfaceSnapshotCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(InterfaceSnapshotCacheInvalidator.class);

    private final CacheService cacheService;
    private final InvalidationMarker invalidationMarker;
    private final InterfaceSnapshotLoadRegistry loadRegistry;

    /**
     * 本地跟踪索引：{@code tenantId:identifier → userId}。
     * <p>
     * 仅用于失效事件枚举（用户级精确清理需要知道某用户有哪些 subjectType/service 快照），
     * 不承载业务读；键含租户前缀（identifier 不含租户，直接复用会跨租户串扰）。
     * TTL/容量跟随 {@code gw:interface-snapshot} 的有效配置（经 accessmesh.cache 覆盖后的
     * 最终值），与主缓存同步过期。索引缺失（定时器毫秒级偏差）的残留条目与广播丢失
     * 同等语义——由快照自身 ≤15s TTL 兜底，在 30s 安全预算内（复评 P2 用户决策：
     * TTL 兜底，不降级租户级清理）。PermissionFilter 回填成功后调用 {@link #track} 登记。
     * </p>
     */
    private final Cache<String, Long> trackedIdentifiers;

    public InterfaceSnapshotCacheInvalidator(CacheService cacheService,
                                             InvalidationMarker invalidationMarker,
                                             InterfaceSnapshotLoadRegistry loadRegistry,
                                             CacheProperties cacheProperties) {
        this.cacheService = cacheService;
        this.invalidationMarker = invalidationMarker;
        this.loadRegistry = loadRegistry;
        this.trackedIdentifiers = Caffeine.newBuilder()
            .maximumSize(cacheProperties.getEffectiveL1MaxSize(
                GatewayCacheCatalog.INTERFACE_SNAPSHOT.getCode(),
                GatewayCacheCatalog.INTERFACE_SNAPSHOT.getL1MaxSize()))
            .expireAfterWrite(cacheProperties.getEffectiveL1Ttl(
                GatewayCacheCatalog.INTERFACE_SNAPSHOT.getCode(),
                GatewayCacheCatalog.INTERFACE_SNAPSHOT.getL1Ttl()))
            .build();
    }

    /**
     * 登记一个已写入的快照 identifier（供用户级精确失效枚举）。
     *
     * @param tenantId 租户ID
     * @param identifier 快照缓存 identifier（subjectTypeCode:userId:serviceCode）
     */
    public void track(Long tenantId, String identifier) {
        if (tenantId == null || identifier == null) {
            return;
        }
        InterfaceSnapshotCacheKeys.ParsedKey parsed = InterfaceSnapshotCacheKeys.parse(identifier);
        trackedIdentifiers.put(trackedKey(tenantId, identifier), parsed != null ? parsed.userId() : null);
    }

    private static String trackedKey(Long tenantId, String identifier) {
        return tenantId + ":" + identifier;
    }

    /**
     * 清理本地快照缓存。
     * <ul>
     *   <li>仅 userIds 非空：按用户精确清理该用户全部服务快照（跟踪索引 + 在途注册表枚举）</li>
     *   <li>serviceCodes 非空或仅 roleIds 非空：租户级安全清理（代际递增 + evictAll）</li>
     * </ul>
     *
     * @param event 失效广播事件
     * @return 实际清理的本地快照 identifier 数（租户级返回 -1 表示全量）
     */
    public long evict(PermInvalidateEvent event) {
        if (event == null || event.tenantId() == null) {
            return 0L;
        }
        Set<String> serviceCodes = normalizeStrings(event.serviceCodes());
        Set<Long> userIds = normalizeLongs(event.userIds());
        Set<Long> roleIds = normalizeLongs(event.roleIds());
        if (serviceCodes.isEmpty() && userIds.isEmpty() && roleIds.isEmpty()) {
            return 0L;
        }

        if (serviceCodes.isEmpty() && roleIds.isEmpty() && !userIds.isEmpty()) {
            return evictByUsers(event.tenantId(), userIds);
        }
        return evictTenantWide(event.tenantId());
    }

    /**
     * 用户级精确清理。
     * <p>
     * 候选 = 跟踪索引（已写入快照）∪ 在途回源注册表（复评 P1 修复：首次回源的 key 在
     * 写入前尚未 track，遗漏会使旧回源在撤权事件后正常提交）。先标记（作废在途
     * LoadToken），再逐个 evict。
     * </p>
     */
    private long evictByUsers(Long tenantId, Set<Long> userIds) {
        String tenantPrefix = tenantId + ":";
        Set<String> affected = new HashSet<>();
        // 1) 跟踪索引：已写入快照的 identifier
        for (Map.Entry<String, Long> entry : trackedIdentifiers.asMap().entrySet()) {
            String trackedKey = entry.getKey();
            if (!trackedKey.startsWith(tenantPrefix) || !userIds.contains(entry.getValue())) {
                continue;
            }
            affected.add(trackedKey.substring(tenantPrefix.length()));
        }
        // 2) 在途回源注册表（loadRegistry 键 = 租户限定 loadKey）
        for (String loadKey : loadRegistry.keys()) {
            if (!loadKey.startsWith(tenantPrefix)) {
                continue;
            }
            InterfaceSnapshotCacheKeys.ParsedKey parsed =
                InterfaceSnapshotCacheKeys.parse(loadKey.substring(tenantPrefix.length()));
            if (parsed != null && userIds.contains(parsed.userId())) {
                affected.add(loadKey.substring(tenantPrefix.length()));
            }
        }
        // 先标记后清理：标记即递增 keyGeneration，在途回源提交被作废
        invalidationMarker.markAll(affected.stream()
            .map(identifier -> trackedKey(tenantId, identifier))
            .collect(Collectors.toSet()));
        for (String identifier : affected) {
            cacheService.evict(GatewayCacheCatalog.INTERFACE_SNAPSHOT, tenantId, identifier);
            trackedIdentifiers.invalidate(trackedKey(tenantId, identifier));
        }
        invalidationMarker.cleanupIfOversized(liveKeys());
        log.debug("Evicted gateway interface snapshots by users (tenantId={}, userIds={}, evicted={})",
            tenantId, userIds, affected.size());
        return affected.size();
    }

    /**
     * 租户级安全清理。
     * <p>
     * 复评 P1 修复：先递增全局代际（作废全部在途 LoadToken），再 evictAll——
     * 若先清缓存后递增，两步之间旧回源仍可通过代际校验并把旧快照写回，
     * 且随后的代际递增不会清除该新写入条目（旧回源复活窗口）。
     * </p>
     */
    private long evictTenantWide(Long tenantId) {
        invalidationMarker.clearAndBumpGlobalEpoch();
        cacheService.evictAll(GatewayCacheCatalog.INTERFACE_SNAPSHOT, tenantId);
        String tenantPrefix = tenantId + ":";
        trackedIdentifiers.asMap().keySet().removeIf(key -> key.startsWith(tenantPrefix));
        log.debug("Evicted all gateway interface snapshots for tenant={}", tenantId);
        return -1L;
    }

    /**
     * 订阅断线恢复时执行全量清空，防止 pub/sub 断线期间遗漏显式失效事件。
     * <p>
     * 先递增全局代际（作废全部在途回源），再执行 catalog 级跨租户全量清空
     * （evictAll(catalog)，不依赖跟踪索引枚举租户——索引与主缓存是独立
     * Caffeine，容量压力下索引会先于主缓存淘汰，按索引推导租户会漏清）。
     * </p>
     */
    public void clearAll() {
        invalidationMarker.clearAndBumpGlobalEpoch();
        cacheService.evictAll(GatewayCacheCatalog.INTERFACE_SNAPSHOT);
        trackedIdentifiers.invalidateAll();
    }

    public Set<String> liveKeys() {
        Set<String> keys = new HashSet<>();
        keys.addAll(trackedIdentifiers.asMap().keySet());
        keys.addAll(loadRegistry.keys());
        return keys;
    }

    /** 孤立标记定期清理（60s 扫描，T-ACCESS-008：替代原 stale-grace 派生周期） */
    @Scheduled(fixedDelayString = "PT60S")
    void cleanupOrphanedMarkers() {
        invalidationMarker.retainLiveKeys(liveKeys());
    }

    private Set<Long> normalizeLongs(Set<Long> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
            .filter(v -> v != null)
            .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> normalizeStrings(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
            .filter(v -> v != null && !v.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}
