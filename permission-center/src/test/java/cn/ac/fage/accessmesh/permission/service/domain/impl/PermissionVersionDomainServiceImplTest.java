package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.entity.PermissionVersion;
import cn.ac.fage.accessmesh.permission.mapper.PermissionVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionVersionDomainServiceImplTest {

    @Mock private PermissionVersionMapper versionMapper;
    @Mock private CacheService cacheService;

    private PermissionVersionDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionVersionDomainServiceImpl(versionMapper, cacheService);
    }

    @Test
    void batchGetCurrentVersionsShouldUseBatchCacheAndBackfillMisses() {
        Set<Long> roleIds = Set.of(1L, 2L, 3L);

        when(cacheService.getBatch(PermCacheCatalog.PERMISSION_VERSION, 1L, roleIds))
            .thenReturn(Map.of(1L, 5L));
        when(versionMapper.selectAllByRolesOrdered(1L, Set.of(2L, 3L)))
            .thenReturn(List.of(permissionVersion(2L, 7L), permissionVersion(2L, 6L)));

        Map<Long, Long> result = service.batchGetCurrentVersions(1L, roleIds);

        assertEquals(Map.of(1L, 5L, 2L, 7L, 3L, 1L), result);
        verify(cacheService).putBatch(PermCacheCatalog.PERMISSION_VERSION, 1L, Map.of(2L, 7L, 3L, 1L));
    }

    private PermissionVersion permissionVersion(Long roleId, Long versionNo) {
        PermissionVersion permissionVersion = new PermissionVersion();
        permissionVersion.setAbstractRoleId(roleId);
        permissionVersion.setVersionNo(versionNo);
        return permissionVersion;
    }
}