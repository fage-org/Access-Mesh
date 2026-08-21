package cn.ac.fage.accessmesh.common.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheCatalogEntry 单元测试（T-ACCESS-008 Duration 秒级精度）
 */
class CacheCatalogEntryTest {

    @Test
    void builder_shouldCreateEntryWithAllFields() {
        CacheCatalogEntry<String> entry = CacheCatalogEntry.<String>builder()
            .code("test:cache")
            .mode(CacheMode.L1_L2)
            .l1Ttl(Duration.ofMinutes(10))
            .l1MaxSize(1000)
            .l2Ttl(Duration.ofHours(1))
            .valueType(new TypeRef<String>() {})
            .build();

        assertEquals("test:cache", entry.getCode());
        assertEquals(CacheMode.L1_L2, entry.getMode());
        assertEquals(Duration.ofMinutes(10), entry.getL1Ttl());
        assertEquals(1000, entry.getL1MaxSize());
        assertEquals(Duration.ofHours(1), entry.getL2Ttl());
        assertEquals(String.class, entry.getValueType().getRawClass());
    }

    @Test
    void builder_shouldCreateL2OnlyEntryWithSecondsPrecision() {
        CacheCatalogEntry<Long> entry = CacheCatalogEntry.<Long>builder()
            .code("test:version")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<Long>() {})
            .build();

        assertEquals(CacheMode.L2_ONLY, entry.getMode());
        assertEquals(Duration.ofSeconds(10), entry.getL2Ttl());
        assertEquals(Long.class, entry.getValueType().getRawClass());
    }

    @Test
    void builder_shouldCreateL1OnlyEntry() {
        CacheCatalogEntry<Boolean> entry = CacheCatalogEntry.<Boolean>builder()
            .code("test:local")
            .mode(CacheMode.L1_ONLY)
            .l1Ttl(Duration.ofSeconds(15))
            .l1MaxSize(5000)
            .valueType(new TypeRef<Boolean>() {})
            .build();

        assertEquals(CacheMode.L1_ONLY, entry.getMode());
        assertEquals(Duration.ofSeconds(15), entry.getL1Ttl());
        assertEquals(5000, entry.getL1MaxSize());
        assertEquals(Boolean.class, entry.getValueType().getRawClass());
    }

    @Test
    void builder_shouldCreateEntryWithComplexGeneric() {
        CacheCatalogEntry<java.util.Set<Long>> entry = CacheCatalogEntry.<java.util.Set<Long>>builder()
            .code("test:roles")
            .mode(CacheMode.L1_L2)
            .l1Ttl(Duration.ofMinutes(30))
            .l1MaxSize(2000)
            .l2Ttl(Duration.ofHours(1))
            .valueType(new TypeRef<java.util.Set<Long>>() {})
            .build();

        assertEquals(java.util.Set.class, entry.getValueType().getRawClass());
        assertEquals(Long.class, entry.getValueType().getContentType().getRawClass());
    }

    @Test
    void builder_shouldThrowExceptionForMissingCode() {
        assertThrows(IllegalStateException.class, () ->
            CacheCatalogEntry.<String>builder()
                .valueType(new TypeRef<String>() {})
                .build());
    }

    @Test
    void builder_shouldThrowExceptionForMissingValueType() {
        assertThrows(IllegalStateException.class, () ->
            CacheCatalogEntry.<String>builder()
                .code("test:cache")
                .build());
    }

    @Test
    void builder_shouldUseDefaultValues() {
        CacheCatalogEntry<String> entry = CacheCatalogEntry.<String>builder()
            .code("test:cache")
            .valueType(new TypeRef<String>() {})
            .build();

        assertEquals(CacheMode.L1_L2, entry.getMode());
        assertEquals(Duration.ofMinutes(10), entry.getL1Ttl());
        assertEquals(1000, entry.getL1MaxSize());
        assertEquals(Duration.ofMinutes(30), entry.getL2Ttl());
    }
}
