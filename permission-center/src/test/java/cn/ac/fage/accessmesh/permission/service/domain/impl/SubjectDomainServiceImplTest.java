package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.entity.UserRole;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper;
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
        verify(cacheService).putBatch(PermCacheCatalog.EFFECTIVE_ROLES, 1L, Map.of(2L, Set.of()));
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
