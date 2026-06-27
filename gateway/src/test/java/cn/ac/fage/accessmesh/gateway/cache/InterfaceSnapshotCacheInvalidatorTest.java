package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;

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
    private final InterfaceSnapshotCacheInvalidator invalidator = new InterfaceSnapshotCacheInvalidator(cache);

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

    private void put(Long tenantId, Long userId, String serviceCode) {
        cache.put(key(tenantId, userId, serviceCode), new InterfaceSnapshotResp(List.of()));
    }

    private String key(Long tenantId, Long userId, String serviceCode) {
        return InterfaceSnapshotCacheKeys.build(tenantId, "USER", userId, serviceCode);
    }
}
