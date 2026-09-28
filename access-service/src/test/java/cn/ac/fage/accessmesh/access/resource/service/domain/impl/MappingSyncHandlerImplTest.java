package cn.ac.fage.accessmesh.access.resource.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.access.projection.PermConstants;
import cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.sync.strategy.SyncContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class MappingSyncHandlerImplTest {

    @Mock private ResourceApiMappingMapper resourceApiMappingMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;

    private MappingSyncHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new MappingSyncHandlerImpl(resourceApiMappingMapper, resourceEntityMapper, org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.resource.service.domain.ApiMappingWriteDomainService.class));
    }

    @Test
    void shouldThrowSystemExceptionWhenSyncedResourceWasNotCreated() {
        ServiceConfigSyncReq req = new ServiceConfigSyncReq(
            "svc-a",
            "/base",
            "FULL",
            List.of(new ServiceConfigSyncReq.GroupItem(
                "default",
                "默认",
                List.of(new ServiceConfigSyncReq.ApiItem("demo", "GET", "/demo", "demo:read", "demo api"))
            ))
        );
        SyncContext context = SyncContext.of(1L, new ServiceConfig(), req, 100L, "/base", 1);
        when(resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
            eq(1L), eq(1), eq(Set.of("demo:read")), eq(Set.of(PermConstants.CodeType.DEFAULT))
        )).thenReturn(List.of());

        SystemException exception = assertThrows(SystemException.class, () -> handler.syncMappings(context));

        assertEquals(AccessErrorCode.SYNC_RESOURCE_NOT_FOUND.getCode(), exception.getErrorCode());
    }

    @Test
    void should_preserveManualMapping_whenBoundResourceWasServiceSynced() {
        ResourceApiMapping mapping = mapping("MANUAL");
        ResourceEntity resource = resource();
        when(resourceApiMappingMapper.selectByTenantAndServiceCode(1L, "svc-a"))
            .thenReturn(List.of(mapping));
        org.mockito.Mockito.lenient().when(resourceEntityMapper.selectValidByIds(1L, Set.of(10L)))
            .thenReturn(List.of(resource));

        assertEquals(0, handler.cleanupObsoleteMappings(1L, "svc-a", Set.of()));
        verify(resourceApiMappingMapper, never()).softDeleteBatch(any(), any(), any());
    }

    @Test
    void should_removeOwnedMapping_whenItsResourceIsMissing() {
        when(resourceApiMappingMapper.selectByTenantAndServiceCode(1L, "svc-a"))
            .thenReturn(List.of(mapping("SERVICE_SYNC")));
        org.mockito.Mockito.lenient().when(resourceEntityMapper.selectValidByIds(1L, Set.of(10L)))
            .thenReturn(List.of());

        assertEquals(1, handler.cleanupObsoleteMappings(1L, "svc-a", Set.of()));
        verify(resourceApiMappingMapper).softDeleteBatch(eq(1L), eq(List.of(20L)), any());
    }

    private ResourceApiMapping mapping(String source) {
        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setId(20L);
        mapping.setTenantId(1L);
        mapping.setResourceEntityId(10L);
        mapping.setServiceCode("svc-a");
        mapping.setMaintainSource(source);
        mapping.setHttpMethod("GET");
        mapping.setPathPattern("/base/demo");
        return mapping;
    }

    private ResourceEntity resource() {
        ResourceEntity resource = new ResourceEntity();
        resource.setId(10L);
        resource.setCode("demo:read");
        resource.setOwnerServiceCode("svc-a");
        resource.setMaintainSource(PermConstants.MaintainSource.SERVICE_SYNC);
        return resource;
    }
}
