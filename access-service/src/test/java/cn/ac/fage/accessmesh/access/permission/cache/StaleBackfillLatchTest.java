package cn.ac.fage.accessmesh.access.permission.cache;

import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.SubjectDomainServiceImpl;
import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.DefaultCacheService;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import cn.ac.fage.accessmesh.common.cache.spi.DistributedCacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 闩锁并发测试：旧授权读取开始后发生权限事务提交与失效、随后旧读取完成并尝试回填
 * （T-ACCESS-008 验收）。
 * <p>
 * 用 CountDownLatch 卡住 DB 读取（模拟慢 SQL），在读取期间执行失效（模拟并发权限撤销），
 * 以可控单调时钟推进时间，验证：
 * <ul>
 *   <li>回填只写入「读取起点 + catalog TTL」剩余的 TTL（不得重新获得完整 10s）</li>
 *   <li>预算耗尽时不写入</li>
 *   <li>批量回填共享同一读取起点</li>
 * </ul>
 * </p>
 */
class StaleBackfillLatchTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 10L;

    /** 捕获写入 TTL 的 L2 fake（真实 DefaultCacheService + 真实 SubjectDomainServiceImpl 读路径） */
    private static class CapturingL2Store implements DistributedCacheStore {
        final Map<String, Object> values = new HashMap<>();
        final Map<String, Duration> ttls = new HashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <V> V get(CacheCatalogEntry<V> catalog, String fullKey) {
            return (V) values.get(fullKey);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> Map<String, V> getBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
            Map<String, V> result = new HashMap<>();
            for (String key : fullKeys) {
                V v = get(catalog, key);
                if (v != null) {
                    result.put(key, v);
                }
            }
            return result;
        }

        @Override
        public <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value) {
            put(catalog, fullKey, value, null);
        }

        @Override
        public <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value, Duration effectiveTtl) {
            if (effectiveTtl == null || effectiveTtl.isZero() || effectiveTtl.isNegative()) {
                return;
            }
            values.put(fullKey, value);
            ttls.put(fullKey, effectiveTtl);
        }

        @Override
        public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data) {
            putBatch(catalog, data, null);
        }

        @Override
        public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data, Duration effectiveTtl) {
            data.forEach((k, v) -> put(catalog, k, v, effectiveTtl));
        }

        @Override
        public <V> void evict(CacheCatalogEntry<V> catalog, String fullKey) {
            values.remove(fullKey);
            ttls.remove(fullKey);
        }

        @Override
        public <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId) {
            values.clear();
            ttls.clear();
        }
    }

    private final AtomicLong clock = new AtomicLong();
    private CapturingL2Store l2Store;
    private CacheService cacheService;
    private UserRoleMapper userRoleMapper;
    private AbstractRoleMapper abstractRoleMapper;
    private SubjectDomainServiceImpl subjectDomainService;

    @BeforeEach
    void setUp() {
        clock.set(0);
        l2Store = new CapturingL2Store();
        cacheService = new DefaultCacheService(null, l2Store, null, new CacheProperties(),
            null, clock::get);

        userRoleMapper = mock(UserRoleMapper.class);
        abstractRoleMapper = mock(AbstractRoleMapper.class);
        AbstractUserMapper abstractUserMapper = mock(AbstractUserMapper.class);
        subjectDomainService = new SubjectDomainServiceImpl(abstractUserMapper, abstractRoleMapper,
            userRoleMapper, cacheService, new ObjectMapper());
    }

    private void advanceMs(long millis) {
        clock.addAndGet(millis * 1_000_000L);
    }

    private String fullKey(Object identifier) {
        return cn.ac.fage.accessmesh.common.cache.CacheKeyUtil.build(
            TENANT_ID, PermCacheCatalog.EFFECTIVE_ROLES.getCode(), identifier);
    }

    private UserRole role(Long userId, Long roleId) {
        UserRole ur = new UserRole();
        ur.setAbstractUserId(userId);
        ur.setTargetType(PermConstants.TargetType.ROLE);
        ur.setTargetId(roleId);
        return ur;
    }

    /**
     * 场景：旧授权读取开始（beginRead @T0）→ 权限事务提交并失效（@T0+1s）→
     * 旧读取完成（@T0+3s）尝试回填 → 只能写入剩余 7s。
     */
    @Test
    void staleBackfillAfterInvalidation_shouldWriteOnlyRemainingTtl() throws Exception {
        CountDownLatch dbReadStarted = new CountDownLatch(1);
        CountDownLatch invalidationDone = new CountDownLatch(1);
        AtomicReference<Duration> writtenTtl = new AtomicReference<>();

        // DB 读取阻塞：beginRead 之后、putBatch 之前等待失效发生
        when(userRoleMapper.selectValidByUserIdsWithValidity(eq(TENANT_ID), org.mockito.ArgumentMatchers.<Long>anySet(), any(LocalDateTime.class)))
            .thenAnswer(inv -> {
                dbReadStarted.countDown();
                invalidationDone.await(5, TimeUnit.SECONDS);
                return List.of(role(USER_ID, 20L));
            });
        when(abstractRoleMapper.selectEnabledIdsByIds(eq(TENANT_ID), org.mockito.ArgumentMatchers.<Long>anySet()))
            .thenAnswer(inv -> new java.util.ArrayList<>((Set<Long>) inv.getArgument(1)));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> read = executor.submit(() ->
                subjectDomainService.resolveEffectiveRoles(TENANT_ID, USER_ID));

            assertThat(dbReadStarted.await(5, TimeUnit.SECONDS)).isTrue();
            // 此时读取已开始（令牌起点 T0=0）；模拟权限事务提交后的失效
            advanceMs(1_000);
            cacheService.evictBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT_ID, Set.of(USER_ID));
            // 旧读取完成前再耗时 2s（累计 3s）
            advanceMs(2_000);
            invalidationDone.countDown();
            read.get(5, TimeUnit.SECONDS);

            writtenTtl.set(l2Store.ttls.get(fullKey(USER_ID)));
        } finally {
            executor.shutdownNow();
        }

        // 回填只获得剩余 TTL（10s - 3s = 7s），不得重新获得完整 10s
        assertThat(writtenTtl.get()).isNotNull();
        assertThat(writtenTtl.get()).isEqualTo(Duration.ofSeconds(7));
    }

    /**
     * 场景：预算耗尽（读取起点 + 10s 已过）后旧读取完成 → 不写入。
     */
    @Test
    void staleBackfillAfterBudgetExhausted_shouldNotWrite() throws Exception {
        CountDownLatch dbReadStarted = new CountDownLatch(1);
        CountDownLatch invalidationDone = new CountDownLatch(1);

        when(userRoleMapper.selectValidByUserIdsWithValidity(eq(TENANT_ID), org.mockito.ArgumentMatchers.<Long>anySet(), any(LocalDateTime.class)))
            .thenAnswer(inv -> {
                dbReadStarted.countDown();
                invalidationDone.await(5, TimeUnit.SECONDS);
                return List.of(role(USER_ID, 20L));
            });
        when(abstractRoleMapper.selectEnabledIdsByIds(eq(TENANT_ID), org.mockito.ArgumentMatchers.<Long>anySet()))
            .thenAnswer(inv -> new java.util.ArrayList<>((Set<Long>) inv.getArgument(1)));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> read = executor.submit(() ->
                subjectDomainService.resolveEffectiveRoles(TENANT_ID, USER_ID));

            assertThat(dbReadStarted.await(5, TimeUnit.SECONDS)).isTrue();
            advanceMs(1_000);
            cacheService.evictBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT_ID, Set.of(USER_ID));
            // 累计超过 catalog 10s 预算
            advanceMs(9_500);
            invalidationDone.countDown();
            read.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(l2Store.values).doesNotContainKey(fullKey(USER_ID));
    }

    /**
     * 场景：批量回填多个用户共享同一读取起点，全部条目只写剩余 TTL。
     */
    @Test
    void batchBackfill_shouldShareSingleReadStart() throws Exception {
        CountDownLatch dbReadStarted = new CountDownLatch(1);
        CountDownLatch invalidationDone = new CountDownLatch(1);

        when(userRoleMapper.selectValidByUserIdsWithValidity(eq(TENANT_ID), org.mockito.ArgumentMatchers.<Long>anySet(), any(LocalDateTime.class)))
            .thenAnswer(inv -> {
                dbReadStarted.countDown();
                invalidationDone.await(5, TimeUnit.SECONDS);
                return List.of(role(10L, 20L), role(11L, 21L), role(12L, 22L));
            });
        when(abstractRoleMapper.selectEnabledIdsByIds(eq(TENANT_ID), org.mockito.ArgumentMatchers.<Long>anySet()))
            .thenAnswer(inv -> new java.util.ArrayList<>((Set<Long>) inv.getArgument(1)));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> read = executor.submit(() ->
                subjectDomainService.batchResolveEffectiveRoles(TENANT_ID, Set.of(10L, 11L, 12L)));

            assertThat(dbReadStarted.await(5, TimeUnit.SECONDS)).isTrue();
            advanceMs(1_000);
            cacheService.evictBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT_ID, Set.of(10L, 11L, 12L));
            advanceMs(2_000);
            invalidationDone.countDown();
            read.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // 批量全部条目共享读取起点（剩余 7s），任何条目都不得重新获得完整 10s
        assertThat(l2Store.ttls.get(fullKey(10L))).isEqualTo(Duration.ofSeconds(7));
        assertThat(l2Store.ttls.get(fullKey(11L))).isEqualTo(Duration.ofSeconds(7));
        assertThat(l2Store.ttls.get(fullKey(12L))).isEqualTo(Duration.ofSeconds(7));
    }
}
