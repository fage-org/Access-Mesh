package cn.ac.fage.accessmesh.common.cache.impl;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheKeyUtil;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Caffeine 秒级精度 TTL 测试（T-ACCESS-008：新增 10 秒与 15 秒单条及批量过期验证）。
 * <p>
 * 精确值断言：10s/15s 目录写入后经 Caffeine policy 读取条目剩余 TTL ≈ 配置值。
 * 真实过期路径：短 TTL 目录（300ms）单条/批量写入后真实等待过期，证明 Duration
 * 秒级精度端到端生效（10s/15s 不真等）。
 * </p>
 */
class CaffeineSecondsPrecisionTest {

    private static final Long TENANT_ID = 1L;

    private CaffeineLocalCacheStore store;

    @BeforeEach
    void setUp() {
        store = new CaffeineLocalCacheStore(new ObjectMapper(), null, new CacheProperties());
    }

    private CacheCatalogEntry<String> catalog(Duration ttl) {
        return CacheCatalogEntry.<String>builder()
            .code("test:l1only:" + ttl.toMillis())
            .mode(CacheMode.L1_ONLY)
            .l1Ttl(ttl)
            .l1MaxSize(100)
            .valueType(new TypeRef<String>() {})
            .build();
    }

    private String fullKey(CacheCatalogEntry<String> catalog, Object id) {
        return CacheKeyUtil.build(TENANT_ID, catalog.getCode(), id);
    }

    @Test
    void singlePut_with10sTtl_shouldConfigureEntryExpiry() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(10));
        String key = fullKey(catalog, "a");
        store.put(catalog, key, "v");

        assertThat((double) store.policyExpirationMillis(catalog, key))
            .isCloseTo(10_000.0, within(500.0));
    }

    @Test
    void singlePut_with15sTtl_shouldConfigureEntryExpiry() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(15));
        String key = fullKey(catalog, "a");
        store.put(catalog, key, "v");

        assertThat((double) store.policyExpirationMillis(catalog, key))
            .isCloseTo(15_000.0, within(500.0));
    }

    @Test
    void batchPut_with10sTtl_shouldConfigureAllEntryExpiry() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(10));
        Map<String, String> data = Map.of(
            fullKey(catalog, "a"), "v1",
            fullKey(catalog, "b"), "v2",
            fullKey(catalog, "c"), "v3");
        store.putBatch(catalog, data);

        for (String key : data.keySet()) {
            assertThat((double) store.policyExpirationMillis(catalog, key))
                .isCloseTo(10_000.0, within(500.0));
        }
    }

    @Test
    void batchPut_with15sTtl_shouldConfigureAllEntryExpiry() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(15));
        Map<String, String> data = Map.of(
            fullKey(catalog, "a"), "v1",
            fullKey(catalog, "b"), "v2");
        store.putBatch(catalog, data);

        for (String key : data.keySet()) {
            assertThat((double) store.policyExpirationMillis(catalog, key))
                .isCloseTo(15_000.0, within(500.0));
        }
    }

    @Test
    void shortTtl_shouldReallyExpire_singleAndBatch() throws InterruptedException {
        // 秒级精度端到端：亚秒 Duration 生效证明 Duration 全链路（非分钟截断）
        CacheCatalogEntry<String> catalog = catalog(Duration.ofMillis(300));
        String keyA = fullKey(catalog, "a");
        String keyB = fullKey(catalog, "b");
        store.put(catalog, keyA, "v1");
        store.putBatch(catalog, Map.of(keyB, "v2"));

        assertThat(store.get(catalog, keyA)).isEqualTo("v1");
        assertThat(store.getBatch(catalog, Set.of(keyB)).get(keyB)).isEqualTo("v2");

        waitUntilExpired(() -> store.get(catalog, keyA) == null
            && store.getBatch(catalog, Set.of(keyB)).get(keyB) == null);
    }

    private void waitUntilExpired(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("entries did not expire within 3s");
            }
            Thread.sleep(50);
        }
    }
}
