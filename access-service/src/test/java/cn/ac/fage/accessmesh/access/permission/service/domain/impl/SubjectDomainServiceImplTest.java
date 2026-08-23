package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectDomainServiceImplTest {

    @Mock private UserRoleMapper userRoleMapper;
    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private AbstractUserMapper abstractUserMapper;
    @Mock private CacheService cacheService;

    private SubjectDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubjectDomainServiceImpl(
            abstractUserMapper,
            abstractRoleMapper,
            userRoleMapper,
            cacheService,
            new ObjectMapper()
        );
        // T-ACCESS-008：beginRead 委托真实实现——mock 默认返回 null 令牌会导致
        // putBatch(token) 断言失真
        org.mockito.Mockito.lenient().when(cacheService.beginRead(any(
                cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry.class)))
            .thenAnswer(inv -> new cn.ac.fage.accessmesh.common.cache.DefaultCacheService(
                null, null, null, new cn.ac.fage.accessmesh.common.cache.CacheProperties(), null)
                .beginRead(inv.getArgument(0)));
    }

    @Test
    void batchResolveEffectiveRolesShouldUseBatchCacheGetAndPut() {
        Set<Long> userIds = new LinkedHashSet<>(Set.of(1L, 2L));

        when(cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, 1L, userIds))
            .thenReturn(Map.of(1L, Set.of(10L)));
        when(userRoleMapper.selectValidByUserIdsWithValidity(eq(1L), eq(Set.of(2L)), any(LocalDateTime.class)))
            .thenReturn(List.of());

        Map<Long, Set<Long>> result = service.batchResolveEffectiveRoles(1L, userIds);

        assertEquals(Set.of(10L), result.get(1L));
        assertEquals(Set.of(), result.get(2L));
        verify(cacheService).getBatch(PermCacheCatalog.EFFECTIVE_ROLES, 1L, userIds);
        // T-ACCESS-008：回填走读取令牌（剩余 TTL），验证令牌绑定同一 catalog
        var tokenCaptor = org.mockito.ArgumentCaptor.forClass(
            cn.ac.fage.accessmesh.common.cache.CacheReadToken.class);
        verify(cacheService).putBatch(tokenCaptor.capture(), eq(1L), eq(Map.of(2L, Set.of())));
        assertEquals(PermCacheCatalog.EFFECTIVE_ROLES, tokenCaptor.getValue().catalog());
    }

    /**
     * T-ACCESS-017 特征测试（链路 3）：缓存全命中时必须零 SQL、零回填——
     * EFFECTIVE_ROLES 全 hit 是快照链路的常态路径，任何回源都会放大 DB 压力。
     */
    @Test
    void batchResolveEffectiveRolesShouldSkipDbAndBackfillWhenAllCached() {
        Set<Long> userIds = Set.of(1L, 2L);

        when(cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, 1L, userIds))
            .thenReturn(Map.of(1L, Set.of(10L), 2L, Set.of(20L)));

        Map<Long, Set<Long>> result = service.batchResolveEffectiveRoles(1L, userIds);

        assertEquals(Set.of(10L), result.get(1L));
        assertEquals(Set.of(20L), result.get(2L));
        verify(userRoleMapper, never()).selectValidByUserIdsWithValidity(anyLong(), any(), any(LocalDateTime.class));
        // 零回填：两个 putBatch 重载（token / catalog）都不得调用，
        // 全 hit 也不应进入 miss 分支（beginRead 仅在存在未命中用户时发起）
        verify(cacheService, never()).putBatch(any(cn.ac.fage.accessmesh.common.cache.CacheReadToken.class), anyLong(), any());
        verify(cacheService, never()).putBatch(any(cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry.class), anyLong(), any());
        verify(cacheService, never()).beginRead(any());
    }

    @Test
    void invalidateRoleCacheBatchShouldDelegateToBatchEvict() {
        Set<Long> userIds = Set.of(1L, 2L, 3L);

        service.invalidateRoleCacheBatch(1L, userIds);

        verify(cacheService).evictBatch(PermCacheCatalog.EFFECTIVE_ROLES, 1L, userIds);
    }

    /**
     * T-PERM-018 P2：批量失效多角色关联用户缓存，固定 ≤3 SQL（直接用户 + 祖先组角色 + 组角色用户），
     * 一次 evictBatch，消除按角色循环 N+1。
     */
    @Test
    void invalidateRoleCacheByRolesShouldBatchLoadUsersAndAncestorGroupRoles() {
        Set<Long> roleIds = Set.of(20L, 21L);

        UserRole directRel = new UserRole();
        directRel.setAbstractUserId(1L);
        UserRole ancestorRel = new UserRole();
        ancestorRel.setAbstractUserId(2L);

        when(userRoleMapper.selectValidByTargetIdsAndType(1L, roleIds, PermConstants.TargetType.ROLE))
            .thenReturn(List.of(directRel));
        when(abstractRoleMapper.selectAncestorGroupRoleIdsBatch(1L, roleIds)).thenReturn(List.of(50L));
        when(userRoleMapper.selectValidByTargetIdsAndType(eq(1L), eq(Set.of(50L)), eq(PermConstants.TargetType.GROUP_ROLE)))
            .thenReturn(List.of(ancestorRel));

        service.invalidateRoleCacheByRoles(1L, roleIds);

        // 合并直接用户 + 组角色用户，一次 evictBatch
        verify(cacheService).evictBatch(eq(PermCacheCatalog.EFFECTIVE_ROLES), eq(1L), eq(Set.of(1L, 2L)));
    }

    @Test
    void invalidateRoleCacheByRolesShouldSkipEvictWhenNoUsersOrAncestors() {
        Set<Long> roleIds = Set.of(20L);

        when(userRoleMapper.selectValidByTargetIdsAndType(1L, roleIds, PermConstants.TargetType.ROLE))
            .thenReturn(List.of());
        when(abstractRoleMapper.selectAncestorGroupRoleIdsBatch(1L, roleIds)).thenReturn(List.of());

        service.invalidateRoleCacheByRoles(1L, roleIds);

        verify(cacheService, never()).evictBatch(any(), anyLong(), any());
    }

    @Test
    void invalidateRoleCacheByRolesShouldNoopOnEmptyRoleIds() {
        service.invalidateRoleCacheByRoles(1L, Set.of());

        verifyNoInteractionsAll();
    }

    private void verifyNoInteractionsAll() {
        org.mockito.Mockito.verifyNoInteractions(userRoleMapper, abstractRoleMapper, cacheService);
    }
}
