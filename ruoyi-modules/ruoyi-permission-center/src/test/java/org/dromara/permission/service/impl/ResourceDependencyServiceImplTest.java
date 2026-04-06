package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.domain.PcResourceDependency;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.dto.ChangeLogParam;
import org.dromara.permission.domain.dto.DependencyCheckReq;
import org.dromara.permission.domain.dto.ResourceDependencyListReq;
import org.dromara.permission.domain.dto.ResourceDependencySaveReq;
import org.dromara.permission.domain.vo.ResourceDependencyVo;
import org.dromara.permission.event.PermissionGovernanceEventPublisher;
import org.dromara.permission.event.PermissionWriteRefreshEventPublisher;
import org.dromara.permission.handler.ResourceTypeHandler;
import org.dromara.permission.handler.ResourceTypeHandlerRegistry;
import org.dromara.permission.model.permission.DependencyCheckResult;
import org.dromara.permission.model.permission.DependencyGap;
import org.dromara.permission.model.permission.DependencyPathNode;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.model.permission.ResolvedRole;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcResourceDependencyMapper;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.PermissionVersionService;
import org.dromara.permission.service.RoleResolverService;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ResourceDependencyServiceImplTest {

    @Mock
    private PcResourceDependencyMapper mapper;
    @Mock
    private PcAbstractRoleMapper abstractRoleMapper;
    @Mock
    private RoleResolverService roleResolverService;
    @Mock
    private PermissionBridgeSupport permissionBridgeSupport;
    @Mock
    private ResourceTypeHandlerRegistry resourceTypeHandlerRegistry;
    @Mock
    private PermissionGovernanceEventPublisher governanceEventPublisher;
    @Mock
    private ResourceTypeHandler resourceTypeHandler;
    @Mock
    private PermissionVersionService permissionVersionService;
    @Mock
    private PermissionWriteRefreshEventPublisher permissionWriteRefreshEventPublisher;
    @Mock
    private PermissionChangeLogService permissionChangeLogService;

    @InjectMocks
    private ResourceDependencyServiceImpl service;

    private PcResourceDependency buildDependency(Long id, Long tenantId, Long resourceId, Long dependsOnId,
                                                 Long sourceOperationId, Long requiredOperationId) {
        PcResourceDependency dependency = new PcResourceDependency();
        dependency.setId(id);
        dependency.setTenantId(tenantId);
        dependency.setResourceEntityId(resourceId);
        dependency.setDependsOnResourceEntityId(dependsOnId);
        dependency.setSourceOperationPermissionId(sourceOperationId);
        dependency.setRequiredOperationPermissionId(requiredOperationId);
        dependency.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return dependency;
    }

    @Test
    void graph_returnsTransitiveEdges() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            buildDependency(1L, 100L, 10L, 11L, null, 21L),
            buildDependency(2L, 100L, 11L, 12L, null, 22L),
            buildDependency(3L, 100L, 50L, 51L, null, 23L)
        ));
        ResourceDependencyListReq req = new ResourceDependencyListReq();
        req.setTenantId(100L);
        req.setResourceEntityId(10L);

        List<ResourceDependencyVo> result = service.graph(req);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(item -> item.getResourceEntityId().equals(10L)));
        assertTrue(result.stream().anyMatch(item -> item.getResourceEntityId().equals(11L)));
    }

    @Test
    void graph_upstreamMode_returnsDirectionalEdgesOnly() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            buildDependency(1L, 100L, 10L, 11L, null, 21L),
            buildDependency(2L, 100L, 11L, 12L, null, 22L),
            buildDependency(3L, 100L, 99L, 10L, null, 23L)
        ));
        ResourceDependencyListReq req = new ResourceDependencyListReq();
        req.setTenantId(100L);
        req.setResourceEntityId(10L);
        req.setGraphMode("UPSTREAM");

        List<ResourceDependencyVo> result = service.graph(req);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(item -> !item.getResourceEntityId().equals(99L)));
    }

    @Test
    void save_cycleDetected_throwsInvalidRequest() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            buildDependency(1L, 100L, 20L, 30L, null, 1L),
            buildDependency(2L, 100L, 30L, 40L, null, 1L)
        ));
        ResourceDependencySaveReq req = new ResourceDependencySaveReq();
        req.setTenantId(100L);
        req.setResourceEntityId(40L);
        req.setDependsOnResourceEntityId(20L);
        req.setRequiredOperationPermissionId(5L);

        assertThrows(PermissionServiceException.class, () -> service.save(req));
        verify(mapper, never()).insert(any(PcResourceDependency.class));
    }

    @Test
    void save_duplicateDependency_isNoOp() {
        PcResourceDependency existing = buildDependency(1L, 100L, 10L, 11L, null, 21L);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing), List.of(existing));
        when(permissionBridgeSupport.loadResource(100L, 10L)).thenReturn(new PcResourceEntity());
        when(permissionBridgeSupport.loadResource(100L, 11L)).thenReturn(new PcResourceEntity());
        when(permissionBridgeSupport.loadOperation(100L, 21L)).thenReturn(new PcOperationPermission());
        ResourceDependencySaveReq req = new ResourceDependencySaveReq();
        req.setTenantId(100L);
        req.setResourceEntityId(10L);
        req.setDependsOnResourceEntityId(11L);
        req.setRequiredOperationPermissionId(21L);

        service.save(req);

        verify(mapper, never()).insert(any(PcResourceDependency.class));
        verify(permissionVersionService, never()).bumpVersion(any(), any(), any(), any());
        verify(permissionChangeLogService, never()).writeChangeLog(any(ChangeLogParam.class));
    }

    @Test
    void save_missingDependsOnResource_throwsNotFound() {
        when(permissionBridgeSupport.loadResource(100L, 10L)).thenReturn(new PcResourceEntity());
        when(permissionBridgeSupport.loadResource(100L, 11L))
            .thenThrow(new PermissionServiceException(org.dromara.permission.model.permission.PermissionErrorCode.RESOURCE_NOT_FOUND));

        ResourceDependencySaveReq req = new ResourceDependencySaveReq();
        req.setTenantId(100L);
        req.setResourceEntityId(10L);
        req.setDependsOnResourceEntityId(11L);
        req.setRequiredOperationPermissionId(21L);

        assertThrows(PermissionServiceException.class, () -> service.save(req));
        verify(mapper, never()).insert(any(PcResourceDependency.class));
    }

    @Test
    void save_newDependency_recordsAuditAndVersionRefresh() {
        PcResourceEntity source = new PcResourceEntity();
        source.setId(10L);
        source.setTenantId(100L);
        source.setBizDomainId(8L);
        source.setResourceType(7);
        PcResourceEntity dependsOn = new PcResourceEntity();
        dependsOn.setId(11L);
        dependsOn.setTenantId(100L);
        dependsOn.setResourceType(7);
        PcOperationPermission required = new PcOperationPermission();
        required.setId(21L);
        required.setResourceType(7);
        when(permissionBridgeSupport.loadResource(100L, 10L)).thenReturn(source);
        when(permissionBridgeSupport.loadResource(100L, 11L)).thenReturn(dependsOn);
        when(permissionBridgeSupport.loadOperation(100L, 21L)).thenReturn(required);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(), List.of());
        when(mapper.insert(any(PcResourceDependency.class))).thenAnswer(invocation -> {
            PcResourceDependency entity = invocation.getArgument(0);
            entity.setId(66L);
            return 1;
        });
        PcPermissionVersion version = new PcPermissionVersion();
        version.setTenantId(100L);
        version.setVersionNo(5L);
        when(permissionVersionService.bumpVersion(100L, "resource_dependency", 66L, "save-resource-dependency"))
            .thenReturn(version);

        ResourceDependencySaveReq req = new ResourceDependencySaveReq();
        req.setTenantId(100L);
        req.setResourceEntityId(10L);
        req.setDependsOnResourceEntityId(11L);
        req.setRequiredOperationPermissionId(21L);
        req.setRequestId("req-save-dependency");

        service.save(req);

        verify(mapper).insert(any(PcResourceDependency.class));
        ArgumentCaptor<ChangeLogParam> changeLogCaptor = ArgumentCaptor.forClass(ChangeLogParam.class);
        verify(permissionChangeLogService).writeChangeLog(changeLogCaptor.capture());
        assertEquals("resource_dependency", changeLogCaptor.getValue().getEntityType());
        assertEquals("INSERT", changeLogCaptor.getValue().getOperation());
        assertEquals("req-save-dependency", changeLogCaptor.getValue().getRequestId());
        verify(permissionVersionService).bumpVersion(100L, "resource_dependency", 66L, "save-resource-dependency");
        verify(permissionWriteRefreshEventPublisher).publish(any(), eq(version));
    }

    @Test
    void remove_recordsAuditAndVersionRefresh() {
        PcResourceDependency dependency = buildDependency(7L, 100L, 10L, 11L, null, 21L);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(dependency);
        PcPermissionVersion version = new PcPermissionVersion();
        version.setTenantId(100L);
        version.setVersionNo(6L);
        when(permissionVersionService.bumpVersion(100L, "resource_dependency", 7L, "remove-resource-dependency"))
            .thenReturn(version);

        org.dromara.permission.domain.dto.IdsReq req = new org.dromara.permission.domain.dto.IdsReq();
        req.setTenantId(100L);
        req.setIds(List.of(7L));
        req.setRequestId("req-remove-dependency");

        service.remove(req);

        verify(mapper).updateById(any(PcResourceDependency.class));
        ArgumentCaptor<ChangeLogParam> changeLogCaptor = ArgumentCaptor.forClass(ChangeLogParam.class);
        verify(permissionChangeLogService).writeChangeLog(changeLogCaptor.capture());
        assertEquals("DELETE", changeLogCaptor.getValue().getOperation());
        assertEquals("req-remove-dependency", changeLogCaptor.getValue().getRequestId());
        verify(permissionVersionService).bumpVersion(100L, "resource_dependency", 7L, "remove-resource-dependency");
        verify(permissionWriteRefreshEventPublisher).publish(any(), eq(version));
    }

    @Test
    void check_userScopePublishesBrokenDependencyEvent() {
        ResolvedRole resolvedRole = new ResolvedRole();
        resolvedRole.setRoleId(30L);
        when(roleResolverService.resolve(100L, 80L, 10L)).thenReturn(List.of(resolvedRole));
        MatchedPermission effective = new MatchedPermission();
        effective.setResourceId(200L);
        effective.setOperationId(300L);
        when(permissionBridgeSupport.resolveCurrentEffectivePermissions(any(), eq(List.of(30L)), eq(Set.of(200L)), eq(300L)))
            .thenReturn(List.of(effective));
        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(200L);
        resource.setTenantId(100L);
        resource.setResourceType(7);
        PcOperationPermission operation = new PcOperationPermission();
        operation.setId(300L);
        when(permissionBridgeSupport.loadResource(100L, 200L)).thenReturn(resource);
        when(permissionBridgeSupport.loadOperation(100L, 300L)).thenReturn(operation);
        DependencyGap gap = new DependencyGap(201L, 301L, List.of(
            new DependencyPathNode(200L, 300L),
            new DependencyPathNode(201L, 301L)
        ));
        DependencyCheckResult result = DependencyCheckResult.fail(List.of(gap));
        when(permissionBridgeSupport.checkDependencies(any(), any(), any(), any())).thenReturn(result);

        DependencyCheckReq req = new DependencyCheckReq();
        req.setTenantId(100L);
        req.setAbstractUserId(80L);
        req.setBizDomainId(10L);
        req.setResourceEntityId(200L);
        req.setOperationPermissionId(300L);
        req.setRequestId("req-dep-1");

        DependencyCheckResult actual = service.check(req);

        assertFalse(actual.isSatisfied());
        assertEquals(1, actual.getGaps().size());
        verify(governanceEventPublisher).publishDependencyBroken(req, result);
    }

    @Test
    void check_withoutExactPair_scansGrantedPermissions() {
        ResolvedRole resolvedRole = new ResolvedRole();
        resolvedRole.setRoleId(30L);
        when(roleResolverService.resolve(100L, 80L, 10L)).thenReturn(List.of(resolvedRole));
        MatchedPermission granted = new MatchedPermission();
        granted.setResourceId(200L);
        granted.setOperationId(300L);
        when(permissionBridgeSupport.resolveCurrentEffectivePermissions(any(), eq(List.of(30L)), eq(null), eq(null)))
            .thenReturn(List.of(granted));
        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(200L);
        PcOperationPermission operation = new PcOperationPermission();
        operation.setId(300L);
        when(permissionBridgeSupport.loadResource(100L, 200L)).thenReturn(resource);
        when(permissionBridgeSupport.loadOperation(100L, 300L)).thenReturn(operation);

        DependencyGap gap = new DependencyGap(201L, 301L, List.of(
            new DependencyPathNode(200L, 300L),
            new DependencyPathNode(201L, 301L)
        ));
        when(permissionBridgeSupport.checkDependencies(any(), eq(200L), eq(300L), eq(List.of(30L))))
            .thenReturn(DependencyCheckResult.fail(List.of(gap)));

        DependencyCheckReq req = new DependencyCheckReq();
        req.setTenantId(100L);
        req.setAbstractUserId(80L);
        req.setBizDomainId(10L);

        DependencyCheckResult result = service.check(req);

        assertFalse(result.isSatisfied());
        assertEquals(1, result.getGaps().size());
        verify(permissionBridgeSupport).resolveCurrentEffectivePermissions(any(), eq(List.of(30L)), eq(null), eq(null));
    }

    @Test
    void check_resourceOperationMismatch_throwsInvalidRequest() {
        ResolvedRole resolvedRole = new ResolvedRole();
        resolvedRole.setRoleId(30L);
        when(roleResolverService.resolve(100L, 80L, null)).thenReturn(List.of(resolvedRole));
        MatchedPermission effective = new MatchedPermission();
        effective.setResourceId(200L);
        effective.setOperationId(300L);
        when(permissionBridgeSupport.resolveCurrentEffectivePermissions(any(), eq(List.of(30L)), eq(Set.of(200L)), eq(300L)))
            .thenReturn(List.of(effective));
        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(200L);
        PcOperationPermission operation = new PcOperationPermission();
        operation.setId(300L);
        when(permissionBridgeSupport.loadResource(100L, 200L)).thenReturn(resource);
        when(permissionBridgeSupport.loadOperation(100L, 300L)).thenReturn(operation);
        doThrow(new PermissionServiceException(org.dromara.permission.model.permission.PermissionErrorCode.RESOURCE_OPERATION_TYPE_MISMATCH))
            .when(permissionBridgeSupport).validateResourceOperationType(eq(resource), eq(operation));

        DependencyCheckReq req = new DependencyCheckReq();
        req.setTenantId(100L);
        req.setAbstractUserId(80L);
        req.setResourceEntityId(200L);
        req.setOperationPermissionId(300L);

        assertThrows(PermissionServiceException.class, () -> service.check(req));
    }

    @Test
    void check_exactPermissionNotCurrentlyEffective_returnsUnsatisfiedWithoutPublishingEvent() {
        ResolvedRole resolvedRole = new ResolvedRole();
        resolvedRole.setRoleId(30L);
        when(roleResolverService.resolve(100L, 80L, null)).thenReturn(List.of(resolvedRole));
        when(permissionBridgeSupport.resolveCurrentEffectivePermissions(any(), eq(List.of(30L)), eq(Set.of(200L)), eq(300L)))
            .thenReturn(List.of());

        DependencyCheckReq req = new DependencyCheckReq();
        req.setTenantId(100L);
        req.setAbstractUserId(80L);
        req.setResourceEntityId(200L);
        req.setOperationPermissionId(300L);

        DependencyCheckResult result = service.check(req);

        assertFalse(result.isSatisfied());
        assertEquals(1, result.getGaps().size());
        verify(governanceEventPublisher, never()).publishDependencyBroken(any(), any());
    }

    @Test
    void check_requiresSubject() {
        DependencyCheckReq req = new DependencyCheckReq();
        req.setTenantId(100L);
        req.setResourceEntityId(200L);
        req.setOperationPermissionId(300L);

        assertThrows(PermissionServiceException.class, () -> service.check(req));
    }
}
