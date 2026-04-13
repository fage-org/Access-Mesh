package org.dromara.gateway.authz;

import org.dromara.authcenter.api.model.InterfacePermissionSnapshot;
import org.dromara.authcenter.api.model.PrincipalContext;
import org.dromara.authcenter.api.enums.SubjectType;
import org.dromara.gateway.config.properties.PermissionAuthzProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PermissionSnapshotCache 单元测试
 */
@DisplayName("PermissionSnapshotCache Tests")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class PermissionSnapshotCacheTest {

    @Mock
    private PermissionSnapshotClient delegate;

    private PermissionAuthzProperties properties;
    private PermissionSnapshotCache cache;

    @BeforeEach
    void setUp() {
        properties = new PermissionAuthzProperties();
        properties.setCacheMaxSize(100);
        properties.setCacheExpireMinutes(5);
        cache = new PermissionSnapshotCache(delegate, properties);
    }

    @Test
    @DisplayName("getSnapshot should return from delegate on cache miss")
    void getSnapshotShouldReturnFromDelegateOnCacheMiss() {
        PrincipalContext context = createPrincipalContext("tenant1", "user1", "v1");
        InterfacePermissionSnapshot expectedSnapshot = createSnapshot("tenant1", "user1", "v1");

        when(delegate.loadSnapshot(context)).thenReturn(expectedSnapshot);

        InterfacePermissionSnapshot result = cache.getSnapshot(context);

        assertNotNull(result);
        assertEquals("tenant1", result.getTenantId());
        assertEquals("v1", result.getPermissionVersion());
        verify(delegate, times(1)).loadSnapshot(context);
    }

    @Test
    @DisplayName("getSnapshot should return from cache on cache hit")
    void getSnapshotShouldReturnFromCacheOnCacheHit() {
        PrincipalContext context = createPrincipalContext("tenant1", "user1", "v1");
        InterfacePermissionSnapshot expectedSnapshot = createSnapshot("tenant1", "user1", "v1");

        when(delegate.loadSnapshot(context)).thenReturn(expectedSnapshot);

        // First call - cache miss
        InterfacePermissionSnapshot result1 = cache.getSnapshot(context);
        assertNotNull(result1);

        // Second call - cache hit
        InterfacePermissionSnapshot result2 = cache.getSnapshot(context);
        assertNotNull(result2);

        // Delegate should only be called once
        verify(delegate, times(1)).loadSnapshot(context);
    }

    @Test
    @DisplayName("getSnapshot should fetch new snapshot when version changes")
    void getSnapshotShouldFetchNewSnapshotWhenVersionChanges() {
        PrincipalContext contextV1 = createPrincipalContext("tenant1", "user1", "v1");
        PrincipalContext contextV2 = createPrincipalContext("tenant1", "user1", "v2");
        InterfacePermissionSnapshot snapshotV1 = createSnapshot("tenant1", "user1", "v1");
        InterfacePermissionSnapshot snapshotV2 = createSnapshot("tenant1", "user1", "v2");

        when(delegate.loadSnapshot(contextV1)).thenReturn(snapshotV1);
        when(delegate.loadSnapshot(contextV2)).thenReturn(snapshotV2);

        // Fetch with v1
        InterfacePermissionSnapshot result1 = cache.getSnapshot(contextV1);
        assertEquals("v1", result1.getPermissionVersion());

        // Fetch with v2 - should trigger new fetch
        InterfacePermissionSnapshot result2 = cache.getSnapshot(contextV2);
        assertEquals("v2", result2.getPermissionVersion());

        // Delegate should be called twice
        verify(delegate, times(1)).loadSnapshot(contextV1);
        verify(delegate, times(1)).loadSnapshot(contextV2);
    }

    @Test
    @DisplayName("invalidate should remove cache entries for subject")
    void invalidateShouldRemoveCacheEntriesForSubject() {
        PrincipalContext context = createPrincipalContext("tenant1", "user1", "v1");
        InterfacePermissionSnapshot snapshot = createSnapshot("tenant1", "user1", "v1");

        when(delegate.loadSnapshot(context)).thenReturn(snapshot);

        // Load into cache
        cache.getSnapshot(context);

        // Invalidate
        cache.invalidate("tenant1", "USER:user1");

        // Next call should go to delegate again
        cache.getSnapshot(context);

        verify(delegate, times(2)).loadSnapshot(context);
    }

    @Test
    @DisplayName("invalidateByTenant should remove all cache entries for tenant")
    void invalidateByTenantShouldRemoveAllCacheEntriesForTenant() {
        PrincipalContext context1 = createPrincipalContext("tenant1", "user1", "v1");
        PrincipalContext context2 = createPrincipalContext("tenant1", "user2", "v1");
        InterfacePermissionSnapshot snapshot1 = createSnapshot("tenant1", "user1", "v1");
        InterfacePermissionSnapshot snapshot2 = createSnapshot("tenant1", "user2", "v1");

        when(delegate.loadSnapshot(context1)).thenReturn(snapshot1);
        when(delegate.loadSnapshot(context2)).thenReturn(snapshot2);

        // Load both into cache
        cache.getSnapshot(context1);
        cache.getSnapshot(context2);

        // Invalidate tenant
        cache.invalidateByTenant("tenant1");

        // Both should go to delegate again
        cache.getSnapshot(context1);
        cache.getSnapshot(context2);

        verify(delegate, times(2)).loadSnapshot(context1);
        verify(delegate, times(2)).loadSnapshot(context2);
    }

    @Test
    @DisplayName("getStats should return cache statistics")
    void getStatsShouldReturnCacheStatistics() {
        PrincipalContext context = createPrincipalContext("tenant1", "user1", "v1");
        InterfacePermissionSnapshot snapshot = createSnapshot("tenant1", "user1", "v1");

        when(delegate.loadSnapshot(context)).thenReturn(snapshot);

        // Cache miss
        cache.getSnapshot(context);
        // Cache hit
        cache.getSnapshot(context);

        String stats = cache.getStats();
        assertTrue(stats.contains("hitCount=1"));
        assertTrue(stats.contains("missCount=1"));
    }

    private PrincipalContext createPrincipalContext(String tenantId, String subjectId, String version) {
        PrincipalContext context = new PrincipalContext();
        context.setTenantId(tenantId);
        context.setSubjectId(subjectId);
        context.setSubjectType(SubjectType.USER);
        context.setPermissionVersion(version);
        return context;
    }

    private InterfacePermissionSnapshot createSnapshot(String tenantId, String subjectKey, String version) {
        InterfacePermissionSnapshot snapshot = new InterfacePermissionSnapshot();
        snapshot.setTenantId(tenantId);
        snapshot.setSubjectKey(subjectKey);
        snapshot.setPermissionVersion(version);
        snapshot.setGeneratedAtEpochMilli(System.currentTimeMillis());
        return snapshot;
    }
}
