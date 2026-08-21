package cn.ac.fage.accessmesh.access.permission.cache;

import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.SubjectDomainServiceImpl;
import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.DefaultCacheService;
import cn.ac.fage.accessmesh.common.cache.spi.DistributedCacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 权限缓存不可用时绕过缓存查数据库测试（T-ACCESS-008）。
 * <p>
 * Store 层对 Redis 故障的语义是"吞掉异常"：读返回 null、批量读返回空、写静默跳过
 * （见 RedissonBucketStore 各 catch 分支）。本测试的故障 store 复刻该语义，
 * 验证真实读路径（SubjectDomainServiceImpl.resolveEffectiveRoles）在缓存完全
 * 不可用时直接查 DB 返回可信结果；DB 也无结果时按正常业务语义返回（无角色 = 空），
 * 不会因缓存故障放大为异常。
 * </p>
 */
class AuthorizationCacheBypassToDbTest {

    private static final Long TENANT_ID = 1L;

    /** 复刻真实 store 故障语义的 L2 store：读 null/空、写 no-op（等价 Redis 完全不可用） */
    private static class FailingL2Store implements DistributedCacheStore {
        @Override
        public <V> V get(CacheCatalogEntry<V> catalog, String fullKey) {
            return null;
        }

        @Override
        public <V> Map<String, V> getBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
            return Map.of();
        }

        @Override
        public <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value) {
            // 静默跳过（真实 store 捕获异常后同样不写）
        }

        @Override
        public <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value, Duration effectiveTtl) {
            // 静默跳过
        }

        @Override
        public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data) {
            // 静默跳过
        }

        @Override
        public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data, Duration effectiveTtl) {
            // 静默跳过
        }

        @Override
        public <V> void evict(CacheCatalogEntry<V> catalog, String fullKey) {
            // 静默跳过
        }

        @Override
        public <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId) {
            // 静默跳过
        }
    }

    private CacheService cacheService;
    private UserRoleMapper userRoleMapper;
    private AbstractRoleMapper abstractRoleMapper;
    private SubjectDomainServiceImpl subjectDomainService;

    @BeforeEach
    void setUp() {
        cacheService = new DefaultCacheService(null, new FailingL2Store(), null,
            new CacheProperties(), null);
        userRoleMapper = mock(UserRoleMapper.class);
        abstractRoleMapper = mock(AbstractRoleMapper.class);
        AbstractUserMapper abstractUserMapper = mock(AbstractUserMapper.class);
        subjectDomainService = new SubjectDomainServiceImpl(abstractUserMapper, abstractRoleMapper,
            userRoleMapper, cacheService, new ObjectMapper());
    }

    private UserRole role(Long userId, Long roleId) {
        UserRole ur = new UserRole();
        ur.setAbstractUserId(userId);
        ur.setTargetType(PermConstants.TargetType.ROLE);
        ur.setTargetId(roleId);
        return ur;
    }

    @Test
    void cacheDown_shouldBypassToDbAndReturnTrustedResult() {
        when(userRoleMapper.selectValidByUserIdsWithValidity(eq(TENANT_ID), org.mockito.ArgumentMatchers.<Long>anySet(), any(LocalDateTime.class)))
            .thenReturn(List.of(role(10L, 20L)));
        when(abstractRoleMapper.selectEnabledIdsByIds(eq(TENANT_ID), org.mockito.ArgumentMatchers.<Long>anySet()))
            .thenAnswer(inv -> new java.util.ArrayList<>((Set<Long>) inv.getArgument(1)));

        // Redis 不可用（读空、写跳过）→ 绕过缓存查 DB
        Set<Long> roles = subjectDomainService.resolveEffectiveRoles(TENANT_ID, 10L);

        assertThat(roles).containsExactly(20L);
    }

    @Test
    void cacheDown_andDbEmpty_shouldReturnEmptyNotError() {
        when(userRoleMapper.selectValidByUserIdsWithValidity(eq(TENANT_ID), org.mockito.ArgumentMatchers.<Long>anySet(), any(LocalDateTime.class)))
            .thenReturn(List.of());

        Set<Long> roles = subjectDomainService.resolveEffectiveRoles(TENANT_ID, 10L);

        // 无角色 = 空集合（后续 fail-closed 由引擎 NO_ROLE 语义处理），不因缓存故障抛异常
        assertThat(roles).isEmpty();
    }
}
