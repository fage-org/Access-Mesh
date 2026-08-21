package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.DefaultCacheService;
import cn.ac.fage.accessmesh.common.cache.impl.CaffeineLocalCacheStore;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InterfaceSnapshotCacheInvalidator 测试（T-PERM-006 / T-ACCESS-008 迁 CacheService + 失效粒度）。
 * <p>
 * T-ACCESS-008 用户决策：userIds 非空且无 serviceCodes/roleIds → 用户级精确清理；
 * serviceCodes 非空或仅 roleIds → 租户级 evictAll 安全兜底。
 * </p>
 */
class InterfaceSnapshotCacheInvalidatorTest {

    private static final Long TENANT_ID = 1L;
    private static final Long OTHER_TENANT_ID = 2L;
    private static final String SUBJECT_TYPE_CODE = "USER";

    private CacheService cacheService;
    private InvalidationMarker marker;
    private InterfaceSnapshotLoadRegistry loadRegistry;
    private InterfaceSnapshotCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        marker = new InvalidationMarker();
        loadRegistry = new InterfaceSnapshotLoadRegistry();
        cacheService = new DefaultCacheService(null, null,
            new CaffeineLocalCacheStore(objectMapper, null, new CacheProperties()),
            new CacheProperties(), null);
        invalidator = new InterfaceSnapshotCacheInvalidator(cacheService, marker, loadRegistry,
            new cn.ac.fage.accessmesh.common.cache.CacheProperties());
    }

    private String identifier(Long userId, String serviceCode) {
        return InterfaceSnapshotCacheKeys.build(SUBJECT_TYPE_CODE, userId, serviceCode);
    }

    private void putSnapshot(Long tenantId, Long userId, String serviceCode) {
        String id = identifier(userId, serviceCode);
        cacheService.put(GatewayCacheCatalog.INTERFACE_SNAPSHOT, tenantId, id, snapshot(serviceCode));
        invalidator.track(tenantId, id);
    }

    private InterfaceSnapshotResp snapshot(String serviceCode) {
        return new InterfaceSnapshotResp(List.of(
            new ApiPermissionEntry(serviceCode, null, null, false, null, null, ScopeMode.ALL)
        ));
    }

    @Test
    void evictByUserIds_shouldEvictOnlyThatUserInTenant() {
        putSnapshot(TENANT_ID, 10L, "example-service");
        putSnapshot(TENANT_ID, 10L, "order-service");
        putSnapshot(TENANT_ID, 20L, "example-service");
        putSnapshot(OTHER_TENANT_ID, 10L, "example-service");

        long evicted = invalidator.evict(new PermInvalidateEvent(TENANT_ID, Set.of(), Set.of(10L), Set.of()));

        assertThat(evicted).isEqualTo(2);
        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier(10L, "example-service"))).isNull();
        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier(10L, "example-service"))).isNull();
        // 同租户其他用户不受影响
        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier(20L, "example-service"))).isNotNull();
        // 其他租户同用户不受影响
        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, OTHER_TENANT_ID, identifier(10L, "example-service"))).isNotNull();
        // 被清理 key 标记失效（租户限定键，防旧回源复活）
        assertThat(marker.contains(TENANT_ID + ":" + identifier(10L, "example-service"))).isTrue();
    }

    @Test
    void evictByServiceCodes_shouldEvictTenantWide() {
        putSnapshot(TENANT_ID, 10L, "example-service");
        putSnapshot(TENANT_ID, 20L, "example-service");

        long evicted = invalidator.evict(new PermInvalidateEvent(TENANT_ID, Set.of(), Set.of(), Set.of("example-service")));

        // 服务级失效降级为租户级兜底（-1 表示全量）
        assertThat(evicted).isEqualTo(-1L);
        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier(10L, "example-service"))).isNull();
        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier(20L, "example-service"))).isNull();
    }

    @Test
    void evictByRoleIdsOnly_shouldEvictTenantWide() {
        putSnapshot(TENANT_ID, 10L, "example-service");
        putSnapshot(TENANT_ID, 20L, "example-service");

        long evicted = invalidator.evict(new PermInvalidateEvent(TENANT_ID, Set.of(5L), Set.of(), Set.of()));

        assertThat(evicted).isEqualTo(-1L);
        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier(10L, "example-service"))).isNull();
        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier(20L, "example-service"))).isNull();
    }

    @Test
    void evictWithEmptyPayload_shouldNoop() {
        putSnapshot(TENANT_ID, 10L, "example-service");

        long evicted = invalidator.evict(new PermInvalidateEvent(TENANT_ID, Set.of(), Set.of(), Set.of()));

        assertThat(evicted).isZero();
        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier(10L, "example-service"))).isNotNull();
    }

    @Test
    void clearAll_shouldEvictAllTenantsAndBumpEpoch() {
        putSnapshot(TENANT_ID, 10L, "example-service");
        putSnapshot(OTHER_TENANT_ID, 20L, "example-service");

        invalidator.clearAll();

        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier(10L, "example-service"))).isNull();
        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, OTHER_TENANT_ID, identifier(20L, "example-service"))).isNull();
    }

    /**
     * 复评 P2-1 回归：索引条目丢失（容量压力下索引先于主缓存淘汰）的租户，
     * clearAll 仍必须清空——catalog 级跨租户 evictAll 不依赖跟踪索引推导租户。
     */
    @Test
    void clearAll_shouldEvictTenantsMissingFromTrackingIndex() {
        // 快照直接写入缓存但不登记跟踪索引（模拟索引条目已丢失）
        cacheService.put(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID,
            identifier(10L, "example-service"), snapshot("example-service"));

        invalidator.clearAll();

        assertThat(cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID,
            identifier(10L, "example-service"))).isNull();
    }
}
