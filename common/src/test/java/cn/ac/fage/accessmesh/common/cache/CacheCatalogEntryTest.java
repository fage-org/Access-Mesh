package cn.ac.fage.accessmesh.common.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheCatalogEntry 单元测试
 */
class CacheCatalogEntryTest {

    @Test
    void builder_shouldCreateEntryWithAllFields() {
        CacheCatalogEntry<String> entry = CacheCatalogEntry.<String>builder()
            .code("test:cache")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(10)
            .l1MaxSize(1000)
            .l2TtlMinutes(60)
            .valueType(new TypeRef<String>() {})
            .build();

        assertEquals("test:cache", entry.getCode());
        assertEquals(CacheMode.L1_L2, entry.getMode());
        assertEquals(10, entry.getL1TtlMinutes());
        assertEquals(1000, entry.getL1MaxSize());
        assertEquals(60, entry.getL2TtlMinutes());
        assertEquals(String.class, entry.getValueType().getRawClass());
    }

    @Test
    void builder_shouldCreateL2OnlyEntry() {
        CacheCatalogEntry<Long> entry = CacheCatalogEntry.<Long>builder()
            .code("test:version")
            .mode(CacheMode.L2_ONLY)
            .l2TtlMinutes(120)
            .valueType(new TypeRef<Long>() {})
            .build();

        assertEquals("test:version", entry.getCode());
        assertEquals(CacheMode.L2_ONLY, entry.getMode());
        assertEquals(120, entry.getL2TtlMinutes());
        assertEquals(Long.class, entry.getValueType().getRawClass());
    }

    @Test
    void builder_shouldCreateL1OnlyEntry() {
        CacheCatalogEntry<Boolean> entry = CacheCatalogEntry.<Boolean>builder()
            .code("test:local")
            .mode(CacheMode.L1_ONLY)
            .l1TtlMinutes(5)
            .l1MaxSize(5000)
            .valueType(new TypeRef<Boolean>() {})
            .build();

        assertEquals("test:local", entry.getCode());
        assertEquals(CacheMode.L1_ONLY, entry.getMode());
        assertEquals(5, entry.getL1TtlMinutes());
        assertEquals(5000, entry.getL1MaxSize());
        assertEquals(Boolean.class, entry.getValueType().getRawClass());
    }

    @Test
    void builder_shouldCreateEntryWithComplexGeneric() {
        CacheCatalogEntry<java.util.Set<Long>> entry = CacheCatalogEntry.<java.util.Set<Long>>builder()
            .code("test:roles")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(30)
            .l1MaxSize(2000)
            .l2TtlMinutes(60)
            .valueType(new TypeRef<java.util.Set<Long>>() {})
            .build();

        assertEquals("test:roles", entry.getCode());
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

        // Defaults from Builder
        assertEquals(CacheMode.L1_L2, entry.getMode());
        assertEquals(10, entry.getL1TtlMinutes());
        assertEquals(1000, entry.getL1MaxSize());
        assertEquals(30, entry.getL2TtlMinutes());
    }
}