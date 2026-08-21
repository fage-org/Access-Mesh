package cn.ac.fage.accessmesh.common.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheKeyUtil 单元测试
 */
class CacheKeyUtilTest {

    @Test
    void build_shouldGenerateTenantFirstFormat() {
        Long tenantId = 1L;
        String catalogCode = "perm:effective-roles";
        Object identifier = 456L;

        String key = CacheKeyUtil.build(tenantId, catalogCode, identifier);

        assertEquals("1:perm:effective-roles:456", key);
    }

    @Test
    void build_shouldHandleStringIdentifier() {
        Long tenantId = 100L;
        String catalogCode = "admin:dict-types";
        String identifier = "all";

        String key = CacheKeyUtil.build(tenantId, catalogCode, identifier);

        assertEquals("100:admin:dict-types:all", key);
    }

    @Test
    void buildScanPattern_shouldGenerateCorrectPattern() {
        Long tenantId = 1L;
        String catalogCode = "perm:effective-roles";

        String pattern = CacheKeyUtil.buildScanPattern(tenantId, catalogCode);

        assertEquals("1:perm:effective-roles:*", pattern);
    }

    @Test
    void buildTenantPattern_shouldGenerateCorrectPattern() {
        Long tenantId = 1L;

        String pattern = CacheKeyUtil.buildTenantPattern(tenantId);

        assertEquals("1:*", pattern);
    }

    @Test
    void build_shouldThrowExceptionForNullTenantId() {
        assertThrows(IllegalArgumentException.class, () ->
            CacheKeyUtil.build(null, "perm:cache", 123L));
    }

    @Test
    void build_shouldThrowExceptionForNullIdentifier() {
        assertThrows(IllegalArgumentException.class, () ->
            CacheKeyUtil.build(1L, "perm:cache", null));
    }

    @Test
    void buildBatch_shouldGenerateMultipleKeys() {
        Long tenantId = 1L;
        String catalogCode = "perm:cache";
        java.util.Set<Long> identifiers = java.util.Set.of(1L, 2L, 3L);

        java.util.Set<String> keys = CacheKeyUtil.buildBatch(tenantId, catalogCode, identifiers);

        assertEquals(3, keys.size());
        assertTrue(keys.contains("1:perm:cache:1"));
        assertTrue(keys.contains("1:perm:cache:2"));
        assertTrue(keys.contains("1:perm:cache:3"));
    }

    @Test
    void belongsToCatalog_shouldMatchOnlyKeysOfThatCatalog() {
        assertTrue(CacheKeyUtil.belongsToCatalog("1:perm:cache:abc", "perm:cache"));
        assertTrue(CacheKeyUtil.belongsToCatalog("22:perm:cache:x:y", "perm:cache"));
        // catalogCode 必须紧跟租户段：identifier 内嵌其他目录编码的键不得命中
        assertFalse(CacheKeyUtil.belongsToCatalog("1:other:x:perm:cache:y", "perm:cache"));
        assertFalse(CacheKeyUtil.belongsToCatalog("1:perm:cache-v2:z", "perm:cache"));
        // 首段必须是数字租户ID
        assertFalse(CacheKeyUtil.belongsToCatalog("abc:perm:cache:x", "perm:cache"));
        assertFalse(CacheKeyUtil.belongsToCatalog(null, "perm:cache"));
    }

    @Test
    void buildCatalogPattern_shouldBeLooseSuperset() {
        assertEquals("*:perm:cache:*", CacheKeyUtil.buildCatalogPattern("perm:cache"));
    }
}