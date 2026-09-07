package cn.ac.fage.accessmesh.access.permission.aop;

import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.service.impl.ResourceManageAppServiceImpl;
import cn.ac.fage.accessmesh.access.permission.service.impl.TypeDefinitionAppServiceImpl;
import cn.ac.fage.accessmesh.access.permission.service.impl.UserManageAppServiceImpl;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class OperationLogRuntimeContextAppServiceTest {

    @Mock
    private TypeDefinitionMapper typeDefinitionMapper;

    @Mock
    private cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper operationPermissionMapper;

    @Mock
    private ResourceEntityMapper resourceEntityMapper;

    @Mock
    private ResourceApiMappingMapper resourceApiMappingMapper;

    @Mock
    private ResourceEntityDomainService resourceEntityDomainService;

    @Mock
    private TypeResolutionService typeResolutionService;

    @Mock
    private DomainClassifyService domainClassifyService;

    @Mock
    private RoleResourcePermissionMapper roleResourcePermissionMapper;

    @Mock
    private AbstractUserMapper abstractUserMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private AbstractRoleMapper abstractRoleMapper;

    @Mock
    private SubjectDomainService subjectDomainService;

    @Mock
    private AuditDomainService auditDomainService;

    @Mock
    private cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService localProjectionDomainService;

    @Mock
    private PermQueryEngine engine;

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
        OperationLogRuntimeContext.clear();
    }

    @Test
    void shouldRecordActualSummaryForTypeDefinitionBatchDelete() {
        TypeDefinitionAppServiceImpl service = new TypeDefinitionAppServiceImpl(typeDefinitionMapper, operationPermissionMapper, engine,
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard.class),
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService.class),
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService.class),
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper.class),
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper.class),
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.class),
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.common.cache.CacheService.class));

        TypeDefinition first = new TypeDefinition();
        first.setId(1L);
        first.setIsSystem(false);
        TypeDefinition second = new TypeDefinition();
        second.setId(2L);
        second.setIsSystem(false);

        when(typeDefinitionMapper.selectValidByIds(1L, Set.of(1L, 2L))).thenReturn(List.of(first, second));

        service.deleteTypesByIds(1L, List.of(1L, 2L), 100L);

        OperationLogRuntimeContext.Snapshot snapshot = OperationLogRuntimeContext.snapshot();
        assertEquals("soft-deleted 2 type_definition row(s)", snapshot.summaryOverride());
        assertTrue(!snapshot.skip());
        verify(typeDefinitionMapper).softDeleteBatch(eq(1L), anyList(), any(LocalDateTime.class));
    }

    @Test
    void shouldSkipResourceDeleteLogWhenAllRequestedResourcesDenied() {
        ResourceManageAppServiceImpl service = new ResourceManageAppServiceImpl(
            resourceEntityMapper,
            resourceApiMappingMapper,
            resourceEntityDomainService,
            typeResolutionService,
            domainClassifyService,
            engine,
            roleResourcePermissionMapper,
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard.class),
            org.mockito.Mockito.mock(TreeWriteLockSupport.class)
        );

        ResourceEntity first = new ResourceEntity();
        first.setId(10L);
        first.setResourceType(1);
        first.setCode("a");
        first.setCodeType("default");
        ResourceEntity second = new ResourceEntity();
        second.setId(20L);
        second.setResourceType(1);
        second.setCode("b");
        second.setCodeType("default");

        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), eq(Set.of(1)), anySet(), anySet()))
            .thenReturn(List.of(first, second));
        when(engine.getDeniedEntityIds(1L, 99L, ResourceTypeCode.RESOURCE, Set.of(10L, 20L), OperationCodeConstants.MANAGE))
            .thenReturn(Set.of(10L, 20L));

        service.deleteResources(1L, List.of(
            new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq("MENU", "a", null),
            new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq("MENU", "b", null)), 99L);

        OperationLogRuntimeContext.Snapshot snapshot = OperationLogRuntimeContext.snapshot();
        assertTrue(snapshot.skip());
        verify(resourceEntityDomainService, never()).softDeleteBatch(eq(1L), anyList(), any(LocalDateTime.class));
        verify(roleResourcePermissionMapper, never()).softDeleteBatch(eq(1L), anyList(), any(LocalDateTime.class));
    }

    @Test
    void shouldRecordActualSummaryForUserRoleBatchRevoke() {
        UserManageAppServiceImpl service = new UserManageAppServiceImpl(
            abstractUserMapper,
            userRoleMapper,
            abstractRoleMapper,
            subjectDomainService,
            typeResolutionService,
            domainClassifyService,
            auditDomainService,
            new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(),
            localProjectionDomainService,
            new ObjectMapper(),
            engine
        );

        UserRoleBatchRevokeReq req = new UserRoleBatchRevokeReq(List.of(
            new UserRoleBatchRevokeReq.RevokeItem("USER", "u-1", "HR", "ROLE", "r-1", 55L)
        ));

        AbstractRole role = new AbstractRole();
        role.setId(11L);
        role.setName("Admin");

        UserRole relation = new UserRole();
        relation.setId(88L);
        relation.setAbstractUserId(22L);
        relation.setTargetId(11L);
        relation.setRelationId(55L);

        when(typeResolutionService.batchResolveRoleIds(1L, "ROLE", Set.of("r-1"), "HR"))
            .thenReturn(Map.of("r-1", 11L));
        when(typeResolutionService.batchResolveUserIds(1L, "USER", Set.of("u-1")))
            .thenReturn(Map.of("u-1", 22L));
        when(engine.getDeniedResourceCodes(1L, 200L, ResourceTypeCode.ROLE, Set.of("11"), OperationCodeConstants.MANAGE))
            .thenReturn(Set.of());
        when(abstractRoleMapper.selectValidByIds(1L, Set.of(11L))).thenReturn(List.of(role));
        when(userRoleMapper.selectValidByUserIdsTypeAndTargetIds(1L, Set.of(22L), ResourceTypeCode.ROLE, Set.of(11L)))
            .thenReturn(List.of(relation));

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(200L);

            service.revokeRolesBatch(1L, req);
        }

        OperationLogRuntimeContext.Snapshot snapshot = OperationLogRuntimeContext.snapshot();
        assertEquals("revoked 1 user-role relation(s), denied=0", snapshot.summaryOverride());
        assertTrue(!snapshot.skip());
        verify(userRoleMapper).softDeleteBatch(eq(1L), eq(List.of(88L)), any(LocalDateTime.class));
        verify(auditDomainService).recordChangeLog(any(AuditDomainService.ChangeLogContext.class), anyList());
    }
}