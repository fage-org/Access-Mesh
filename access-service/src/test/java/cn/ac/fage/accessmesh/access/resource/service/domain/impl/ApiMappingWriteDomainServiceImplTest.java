package cn.ac.fage.accessmesh.access.resource.service.domain.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.resource.dto.RequiredPermission;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.resource.enums.ApiMappingSource;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ApiMappingWriteDomainService.Write;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.access.type.service.domain.TypeDefinitionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiMappingWriteDomainServiceImplTest {
    @Mock ResourceApiMappingMapper mappings;
    @Mock ResourceEntityMapper resources;
    @Mock ServiceConfigMapper services;
    @Mock TypeDefinitionDomainService types;
    @Mock OperationPermissionDomainService operations;
    @Mock TreeWriteLockSupport locks;
    ApiMappingWriteDomainServiceImpl writer;

    @BeforeEach
    void setup() {
        writer = new ApiMappingWriteDomainServiceImpl(mappings, resources, services, types, operations, locks);
        ServiceConfig service = new ServiceConfig();
        service.setStatus(1);
        service.setApiAuthMode("LEGACY_API");
        lenient().when(services.selectByTenantAndServiceCode(1L, "svc-a")).thenReturn(service);
        lenient().when(types.selectByTenantAndTypeKey(1L, "resource_type"))
            .thenReturn(List.of(type("API", 1), type("REPORT", 2)));
        lenient().when(resources.selectValidByIds(1L, Set.of(10L))).thenReturn(List.of(resource(1)));
        lenient().when(operations.selectByTenantAndResourceTypes(1L, Set.of(2)))
            .thenReturn(List.of(operation(21L, 2, "VIEW", 2L, -1L)));
    }

    @Test
    void should_persistResolvedOperationAndOwnSource_whenRequirementIsValid() {
        ResourceApiMapping row = mapping(null, null);
        writer.saveAll(1L, "svc-a", ApiMappingSource.SERVICE_SYNC,
            List.of(new Write(row, new RequiredPermission("REPORT", "VIEW"))));
        assertThat(row.getRequiredOperationId()).isEqualTo(21L);
        assertThat(row.getMaintainSource()).isEqualTo("SERVICE_SYNC");
        verify(mappings).insert(row);
    }

    @Test
    void should_preserveNoRequirement_whenLegacyMappingIsSaved() {
        ResourceApiMapping row = mapping(null, null);
        writer.saveAll(1L, "svc-a", ApiMappingSource.MANUAL, List.of(new Write(row, null)));
        assertThat(row.getRequiredOperationId()).isNull();
        verify(mappings).insert(row);
    }

    @Test
    void should_requireBusinessOperation_whenServiceUsesAdmissionMode() {
        ServiceConfig config = new ServiceConfig();
        config.setApiAuthMode("OPERATION_ADMISSION");
        when(services.selectByTenantAndServiceCode(1L, "svc-a")).thenReturn(config);
        assertThatThrownBy(() -> writer.saveAll(1L, "svc-a", ApiMappingSource.MANUAL,
            List.of(new Write(mapping(null, null), null)))).isInstanceOf(BizException.class);
        verify(mappings, never()).insert(any(ResourceApiMapping.class));
    }

    @Test
    void should_rejectMissingServiceConfig_whenCreatingMapping() {
        when(services.selectByTenantAndServiceCode(1L, "svc-a")).thenReturn(null);
        assertThatThrownBy(() -> writer.saveAll(1L, "svc-a", ApiMappingSource.MANUAL,
            List.of(new Write(mapping(null, null), null)))).isInstanceOf(BizException.class);
    }

    @Test
    void should_rejectWholeBatchBeforeWriting_whenAnyOperationIsUnknown() {
        assertThatThrownBy(() -> writer.saveAll(1L, "svc-a", ApiMappingSource.MANUAL, List.of(
            new Write(mapping(null, null), new RequiredPermission("REPORT", "VIEW")),
            new Write(mapping(null, null), new RequiredPermission("REPORT", "MISSING")))))
            .isInstanceOf(BizException.class);
        verify(mappings, never()).insert(any(ResourceApiMapping.class));
    }

    @Test
    void should_rejectNonApiRegistration_whenResourceHasBusinessType() {
        when(resources.selectValidByIds(1L, Set.of(10L))).thenReturn(List.of(resource(2)));
        assertThatThrownBy(() -> writer.saveAll(1L, "svc-a", ApiMappingSource.MANUAL,
            List.of(new Write(mapping(null, null), null)))).isInstanceOf(BizException.class);
    }

    @Test
    void should_rejectApiAccessRequirement_whenProvidedExplicitly() {
        assertThatThrownBy(() -> writer.saveAll(1L, "svc-a", ApiMappingSource.MANUAL,
            List.of(new Write(mapping(null, null), new RequiredPermission("API", "ACCESS")))))
            .isInstanceOf(BizException.class);
    }

    @Test
    void should_rejectSourceConflict_whenSameRouteIsManuallyOwned() {
        when(mappings.selectByTenantAndServiceCode(1L, "svc-a"))
            .thenReturn(List.of(mapping(20L, "MANUAL")));
        assertThatThrownBy(() -> writer.saveAll(1L, "svc-a", ApiMappingSource.SERVICE_SYNC,
            List.of(new Write(mapping(null, null), new RequiredPermission("REPORT", "VIEW")))))
            .isInstanceOf(BizException.class);
        verify(mappings, never()).insert(any(ResourceApiMapping.class));
    }

    @Test
    void should_rejectCrossTenantResource_whenNoValidRegistrationWasFound() {
        when(resources.selectValidByIds(1L, Set.of(10L))).thenReturn(List.of());
        assertThatThrownBy(() -> writer.saveAll(1L, "svc-a", ApiMappingSource.MANUAL,
            List.of(new Write(mapping(null, null), null)))).isInstanceOf(BizException.class);
    }

    private ResourceApiMapping mapping(Long id, String source) {
        ResourceApiMapping row = new ResourceApiMapping();
        row.setId(id);
        row.setTenantId(1L);
        row.setServiceCode("svc-a");
        row.setResourceEntityId(10L);
        row.setHttpMethod("GET");
        row.setPathPattern("/base/demo");
        row.setMaintainSource(source);
        row.setEnabled(true);
        return row;
    }

    private ResourceEntity resource(int type) {
        ResourceEntity row = new ResourceEntity();
        row.setId(10L);
        row.setTenantId(1L);
        row.setResourceType(type);
        row.setStatus(1);
        return row;
    }

    private TypeDefinition type(String code, int value) {
        TypeDefinition row = new TypeDefinition();
        row.setTypeCode(code);
        row.setTypeValue(value);
        return row;
    }

    private OperationPermission operation(long id, int type, String code, long bit, long mask) {
        OperationPermission row = new OperationPermission();
        row.setId(id);
        row.setResourceType(type);
        row.setCode(code);
        row.setBinaryBit(bit);
        row.setInheritMask(mask);
        return row;
    }
}
