package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InterfaceSnapshotCacheInvalidator 单元测试（T-PERM-006）
 * <p>
 * 验证 Gateway 收到 perm:invalidate 后按 tenant + serviceCodes / userIds 精确清理本地快照；
 * roleIds-only 等无法反查用户的事件采用 tenant 级安全清理，避免撤权后 Gateway 快照陈旧。
 * </p>
 */
class InterfaceSnapshotCacheInvalidatorTest {

    private final Cache<String, InterfaceSnapshotResp> cache = Caffeine.newBuilder().build();
    private final Cache<String, StaleEntry> staleCache = Caffeine.newBuilder().build();
    private final InvalidationMarker marker = new InvalidationMarker();
    private final InterfaceSnapshotLoadRegistry loadRegistry = new InterfaceSnapshotLoadRegistry();
    private final InterfaceSnapshotCacheInvalidator invalidator =
        new InterfaceSnapshotCacheInvalidator(cache, staleCache, marker, loadRegistry);

    @Test
    void shouldEvictOnlyMatchingTenantAndService_whenServiceCodesPresent() {
        put(1L, 10L, "admin-service");
        put(1L, 10L, "example-service");
        put(2L, 10L, "admin-service");

        long evicted = invalidator.evict(new PermInvalidateEvent(1L, Set.of(), Set.of(), Set.of("admin-service")));

        assertThat(evicted).isEqualTo(1);
        assertThat(cache.getIfPresent(key(1L, 10L, "admin-service"))).isNull();
        assertThat(cache.getIfPresent(key(1L, 10L, "example-service"))).isNotNull();
        assertThat(cache.getIfPresent(key(2L, 10L, "admin-service"))).isNotNull();
    }

    @Test
    void shouldEvictAllServicesForMatchingUsers_whenUserIdsPresent() {
        put(1L, 10L, "admin-service");
        put(1L, 10L, "example-service");
        put(1L, 11L, "admin-service");

        long evicted = invalidator.evict(new PermInvalidateEvent(1L, Set.of(), Set.of(10L), Set.of()));

        assertThat(evicted).isEqualTo(2);
        assertThat(cache.getIfPresent(key(1L, 10L, "admin-service"))).isNull();
        assertThat(cache.getIfPresent(key(1L, 10L, "example-service"))).isNull();
        assertThat(cache.getIfPresent(key(1L, 11L, "admin-service"))).isNotNull();
    }

    @Test
    void shouldEvictTenantWide_whenRoleOnlyEventCannotResolveAffectedUsers() {
        put(1L, 10L, "admin-service");
        put(1L, 11L, "example-service");
        put(2L, 10L, "admin-service");

        long evicted = invalidator.evict(new PermInvalidateEvent(1L, Set.of(200L), Set.of(), Set.of()));

        assertThat(evicted).isEqualTo(2);
        assertThat(cache.getIfPresent(key(1L, 10L, "admin-service"))).isNull();
        assertThat(cache.getIfPresent(key(1L, 11L, "example-service"))).isNull();
        assertThat(cache.getIfPresent(key(2L, 10L, "admin-service"))).isNotNull();
    }

    @Test
    void shouldEvictUnionOfMatchingServicesAndUsers_whenBothDimensionsPresent() {
        put(1L, 10L, "admin-service");
        put(1L, 10L, "example-service");
        put(1L, 11L, "admin-service");
        put(1L, 11L, "example-service");

        long evicted = invalidator.evict(new PermInvalidateEvent(1L, Set.of(), Set.of(10L), Set.of("admin-service")));

        assertThat(evicted).isEqualTo(3);
        assertThat(cache.getIfPresent(key(1L, 10L, "admin-service"))).isNull();
        assertThat(cache.getIfPresent(key(1L, 10L, "example-service"))).isNull();
        assertThat(cache.getIfPresent(key(1L, 11L, "admin-service"))).isNull();
        assertThat(cache.getIfPresent(key(1L, 11L, "example-service"))).isNotNull();
    }

    @Test
    void shouldSkipMalformedCacheKeys() {
        cache.put("malformed", new InterfaceSnapshotResp(List.of()));
        put(1L, 10L, "admin-service");

        long evicted = invalidator.evict(new PermInvalidateEvent(1L, Set.of(), Set.of(), Set.of("admin-service")));

        assertThat(evicted).isEqualTo(1);
        assertThat(cache.getIfPresent("malformed")).isNotNull();
    }

    @Test
    void shouldEvictStaleSnapshotAndMarkMatchedKey() {
        String key = key(1L, 10L, "admin-service");
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of());
        cache.put(key, snapshot);
        staleCache.put(key, new StaleEntry(snapshot, Instant.now().plusSeconds(60)));

        long evicted = invalidator.evict(new PermInvalidateEvent(1L, Set.of(), Set.of(10L), Set.of()));

        assertThat(evicted).isEqualTo(1);
        assertThat(cache.getIfPresent(key)).isNull();
        assertThat(staleCache.getIfPresent(key)).isNull();
        assertThat(marker.contains(key)).isTrue();
    }

    @Test
    void shouldMarkInFlightKeyEvenWhenNoSnapshotCacheEntryExists() {
        String key = key(1L, 10L, "admin-service");
        loadRegistry.load(key, () -> Mono.never());

        long evicted = invalidator.evict(new PermInvalidateEvent(1L, Set.of(), Set.of(), Set.of("admin-service")));

        assertThat(evicted).isEqualTo(1);
        assertThat(marker.contains(key)).isTrue();
    }

    @Test
    void shouldClearAllCachesAndBumpGlobalEpoch() {
        String key = key(1L, 10L, "admin-service");
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of());
        InvalidationMarker.LoadToken token = marker.beginLoad(key);
        cache.put(key, snapshot);
        staleCache.put(key, new StaleEntry(snapshot, Instant.now().plusSeconds(60)));

        invalidator.clearAll();

        assertThat(cache.getIfPresent(key)).isNull();
        assertThat(staleCache.getIfPresent(key)).isNull();
        assertThat(marker.isCurrent(token)).isFalse();
    }

    private void put(Long tenantId, Long userId, String serviceCode) {
        String key = key(tenantId, userId, serviceCode);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of());
        cache.put(key, snapshot);
    }

    private String key(Long tenantId, Long userId, String serviceCode) {
        return InterfaceSnapshotCacheKeys.build(tenantId, "USER", userId, serviceCode);
    }
}
