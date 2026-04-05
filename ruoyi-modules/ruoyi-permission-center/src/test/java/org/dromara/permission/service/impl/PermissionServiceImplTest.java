package org.dromara.permission.service.impl;

import org.dromara.permission.condition.PermissionConditionExpressionEvaluator;
import org.dromara.permission.condition.PermissionConditionPresetHandlerRegistry;
import org.dromara.permission.condition.builtin.InternalIpConditionHandler;
import org.dromara.permission.condition.builtin.WorkdayOnlyConditionHandler;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.constant.ResourceTypeConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.PcPermissionConflictRule;
import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.domain.PcResourceApiMapping;
import org.dromara.permission.domain.PcAbstractUser;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.ChangeLogParam;
import org.dromara.permission.domain.dto.UserRoleAssignReq;
import org.dromara.permission.domain.dto.UserRoleRevokeReq;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcOperationPermissionMapper;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.dromara.permission.mapper.PcPermissionConflictRuleMapper;
import org.dromara.permission.mapper.PcResourceDependencyMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.mapper.PcAbstractUserMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.event.PermissionConflictEventPublisher;
import org.dromara.permission.event.PermissionWriteRefreshEventPublisher;
import org.dromara.permission.handler.DefaultResourceTypeHandler;
import org.dromara.permission.handler.ResourceTypeHandlerRegistry;
import org.dromara.permission.handler.types.ApiResourceTypeHandler;
import org.dromara.permission.handler.types.DataResourceTypeHandler;
import org.dromara.permission.handler.types.MenuResourceTypeHandler;
import org.dromara.permission.model.permission.DenyReason;
import org.dromara.permission.model.permission.GrantPermissionRequest;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionCheckRequest;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.model.permission.PermissionVersionQueryRequest;
import org.dromara.permission.model.permission.ResolvedRole;
import org.dromara.permission.model.permission.RolePermissionBatchGrantRequest;
import org.dromara.permission.model.permission.RolePermissionBatchRevokeRequest;
import org.dromara.permission.model.permission.RevokePermissionRequest;
import org.dromara.permission.model.permission.SnapshotRequest;
import org.dromara.permission.model.permission.UserRoleBatchAssignRequest;
import org.dromara.permission.model.permission.UserRoleBatchRevokeRequest;
import org.dromara.permission.operation.custom.ApiGrantValidator;
import org.dromara.permission.operation.custom.ApiPermissionMatcher;
import org.dromara.permission.operation.custom.ApiSnapshotAssembler;
import org.dromara.permission.operation.custom.DataInheritanceExpander;
import org.dromara.permission.operation.custom.DataSnapshotAssembler;
import org.dromara.permission.operation.custom.MenuInheritanceExpander;
import org.dromara.permission.operation.defaults.DefaultConditionEvaluator;
import org.dromara.permission.operation.defaults.DefaultConflictDetector;
import org.dromara.permission.operation.defaults.DefaultDependencyChecker;
import org.dromara.permission.operation.defaults.DefaultGrantValidator;
import org.dromara.permission.operation.defaults.DefaultInheritanceExpander;
import org.dromara.permission.operation.defaults.DefaultPermissionMatcher;
import org.dromara.permission.operation.defaults.DefaultSnapshotAssembler;
import org.dromara.permission.service.ChangeLogService;
import org.dromara.permission.service.DomainScopeValidator;
import org.dromara.permission.service.OperationInheritanceService;
import org.dromara.permission.service.PermissionVersionService;
import org.dromara.permission.service.ResourceApiMappingService;
import org.dromara.permission.service.RoleResolverService;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.dromara.permission.service.support.TypeDefinitionReader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionServiceImplTest {

    private static final int MENU_TYPE = 1;
    private static final int API_TYPE = 2;
    private static final int DATA_TYPE = 3;

    @Mock private RoleResolverService roleResolverService;
    @Mock private OperationInheritanceService operationInheritanceService;
    @Mock private DomainScopeValidator domainScopeValidator;
    @Mock private ChangeLogService changeLogService;
    @Mock private PermissionVersionService permissionVersionService;
    @Mock private PcResourceEntityMapper resourceEntityMapper;
    @Mock private PcOperationPermissionMapper operationPermissionMapper;
    @Mock private PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock private PcPermissionConditionMapper permissionConditionMapper;
    @Mock private PcPermissionConflictRuleMapper permissionConflictRuleMapper;
    @Mock private PcResourceDependencyMapper resourceDependencyMapper;
    @Mock private PcAbstractRoleMapper abstractRoleMapper;
    @Mock private PcAbstractUserMapper abstractUserMapper;
    @Mock private PcUserRoleMapper userRoleMapper;
    @Mock private ResourceApiMappingService resourceApiMappingService;
    @Mock private TypeDefinitionReader typeDefinitionReader;
    @Mock private PermissionConflictEventPublisher conflictEventPublisher;
    @Mock private PermissionWriteRefreshEventPublisher permissionWriteRefreshEventPublisher;

    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(domainScopeValidator.resolveGrantBizDomainId(any(), any(), any())).thenReturn(null);
        PermissionBridgeSupport bridgeSupport = new PermissionBridgeSupport(
            operationInheritanceService,
            resourceApiMappingService,
            resourceEntityMapper,
            operationPermissionMapper,
            roleResourcePermissionMapper,
            permissionConditionMapper,
            permissionConflictRuleMapper,
            resourceDependencyMapper
        );
        DefaultPermissionMatcher defaultPermissionMatcher = new DefaultPermissionMatcher(bridgeSupport);
        DefaultInheritanceExpander defaultInheritanceExpander = new DefaultInheritanceExpander(bridgeSupport);
        PermissionConditionPresetHandlerRegistry presetHandlerRegistry = new PermissionConditionPresetHandlerRegistry(
            List.of(new WorkdayOnlyConditionHandler(), new InternalIpConditionHandler()));
        DefaultConditionEvaluator defaultConditionEvaluator = new DefaultConditionEvaluator(
            presetHandlerRegistry, new PermissionConditionExpressionEvaluator());
        DefaultConflictDetector defaultConflictDetector = new DefaultConflictDetector(bridgeSupport, conflictEventPublisher);
        DefaultDependencyChecker defaultDependencyChecker = new DefaultDependencyChecker(bridgeSupport);
        DefaultGrantValidator defaultGrantValidator = new DefaultGrantValidator();
        DefaultSnapshotAssembler defaultSnapshotAssembler = new DefaultSnapshotAssembler(bridgeSupport);
        DefaultResourceTypeHandler defaultHandler = new DefaultResourceTypeHandler(
            defaultPermissionMatcher,
            defaultInheritanceExpander,
            defaultConditionEvaluator,
            defaultConflictDetector,
            defaultDependencyChecker,
            defaultGrantValidator,
            defaultSnapshotAssembler
        );
        ApiResourceTypeHandler apiHandler = new ApiResourceTypeHandler(
            defaultInheritanceExpander,
            defaultConditionEvaluator,
            defaultConflictDetector,
            defaultDependencyChecker,
            new ApiPermissionMatcher(bridgeSupport),
            new ApiGrantValidator(resourceApiMappingService),
            new ApiSnapshotAssembler(bridgeSupport)
        );
        MenuResourceTypeHandler menuHandler = new MenuResourceTypeHandler(
            defaultPermissionMatcher,
            new MenuInheritanceExpander(bridgeSupport),
            defaultConditionEvaluator,
            defaultConflictDetector,
            defaultDependencyChecker,
            defaultGrantValidator,
            defaultSnapshotAssembler
        );
        DataResourceTypeHandler dataHandler = new DataResourceTypeHandler(
            defaultPermissionMatcher,
            new DataInheritanceExpander(bridgeSupport),
            defaultConditionEvaluator,
            defaultConflictDetector,
            defaultDependencyChecker,
            defaultGrantValidator,
            new DataSnapshotAssembler(bridgeSupport)
        );
        mockTypeDefinitions();
        ResourceTypeHandlerRegistry registry = new ResourceTypeHandlerRegistry(List.of(defaultHandler, apiHandler, menuHandler, dataHandler), typeDefinitionReader);
        service = new PermissionServiceImpl(
            roleResolverService,
            operationInheritanceService,
            domainScopeValidator,
            changeLogService,
            permissionVersionService,
            registry,
            bridgeSupport,
            permissionWriteRefreshEventPublisher,
            resourceEntityMapper,
            operationPermissionMapper,
            roleResourcePermissionMapper,
            permissionConditionMapper,
            permissionConflictRuleMapper,
            resourceDependencyMapper,
            abstractRoleMapper,
            abstractUserMapper,
            userRoleMapper
        );
    }

    @Test
    void check_noRoles_returnsNoRole() {
        PermissionCheckRequest request = baseCheckRequest();
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(Collections.emptyList());

        var result = service.check(request);

        assertFalse(result.isGranted());
        assertEquals(DenyReason.NO_ROLE, result.getDenyReason());
    }

    @Test
    void check_typeMismatch_throws() {
        PermissionCheckRequest request = baseCheckRequest();
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 2, 1L, 0L));

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.check(request));
        assertEquals(PermissionErrorCode.RESOURCE_OPERATION_TYPE_MISMATCH, ex.getErrorCode());
    }

    @Test
    void check_conflict_returnsConflict() {
        PermissionCheckRequest request = baseCheckRequest();
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));

        PcRoleResourcePermission targetGrant = grant(500L, 200L, 300L, 400L, null);
        PcRoleResourcePermission otherGrant = grant(501L, 200L, 300L, 401L, null);
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(targetGrant, otherGrant));
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenReturn(List.of(
                matched(500L, 200L, 300L, 400L, null),
                matched(501L, 200L, 300L, 401L, null)
            ));
        when(resourceEntityMapper.selectList(any())).thenReturn(List.of(resource(300L, 1)));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(
            operation(400L, 1, 1L, 0L),
            operation(401L, 1, 2L, 0L)
        ));

        PcPermissionConflictRule rule = new PcPermissionConflictRule();
        rule.setId(1L);
        rule.setFirstOperationPermissionId(400L);
        rule.setSecondOperationPermissionId(401L);
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(List.of(rule));

        var result = service.check(request);

        assertFalse(result.isGranted());
        assertEquals(DenyReason.CONFLICT, result.getDenyReason());
        verify(conflictEventPublisher).publish(any(), any());
    }

    @Test
    void check_conflictUsesEffectiveMatchedPermissions_only_noFalseConflict() {
        PermissionCheckRequest request = baseCheckRequest();
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));

        PcRoleResourcePermission targetGrant = grant(500L, 200L, 300L, 400L, null);
        PcRoleResourcePermission filteredGrant = grant(501L, 200L, 300L, 401L, 700L);
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(targetGrant, filteredGrant));
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenReturn(List.of(
                matched(500L, 200L, 300L, 400L, null),
                matched(501L, 200L, 300L, 401L, 700L)
            ));
        when(resourceEntityMapper.selectList(any())).thenReturn(List.of(resource(300L, 1)));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(
            operation(400L, 1, 1L, 0L),
            operation(401L, 1, 2L, 0L)
        ));
        PcPermissionConflictRule rule = new PcPermissionConflictRule();
        rule.setId(1L);
        rule.setFirstOperationPermissionId(400L);
        rule.setSecondOperationPermissionId(401L);
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(List.of(rule));

        var result = service.check(request);

        assertTrue(result.isGranted());
        assertEquals(1, result.getGrantedBy().size());
        assertEquals(400L, result.getGrantedBy().get(0).getOperationId());
    }

    @Test
    void check_inheritedGrantWithConflictingSiblingOperation_returnsConflict() {
        PermissionCheckRequest request = baseCheckRequest();
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));

        PcRoleResourcePermission editGrant = grant(500L, 200L, 300L, 401L, null);
        PcRoleResourcePermission deleteGrant = grant(501L, 200L, 300L, 402L, null);
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(editGrant, deleteGrant));
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenReturn(List.of(matched(500L, 200L, 300L, 401L, null)));
        when(resourceEntityMapper.selectList(any())).thenReturn(List.of(resource(300L, 1)));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(
            operation(401L, 1, 4L, 1L),
            operation(402L, 1, 8L, 0L)
        ));
        PcPermissionConflictRule rule = new PcPermissionConflictRule();
        rule.setId(11L);
        rule.setFirstOperationPermissionId(401L);
        rule.setSecondOperationPermissionId(402L);
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(List.of(rule));

        var result = service.check(request);

        assertFalse(result.isGranted());
        assertEquals(DenyReason.CONFLICT, result.getDenyReason());
    }

    @Test
    void check_conflictOnExpandedChild_doesNotRemoveParentPermission() {
        PermissionCheckRequest request = baseCheckRequest();
        request.setInheritMode(org.dromara.permission.model.permission.InheritMode.CHILDREN);
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        PcResourceEntity parent = resource(300L, 1);
        parent.setPath("/300");
        PcResourceEntity child = resource(301L, 1);
        child.setPath("/300/301");
        when(resourceEntityMapper.selectOne(any())).thenReturn(parent);
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(resourceEntityMapper.selectList(any())).thenReturn(
            List.of(child),
            List.of(parent, child)
        );
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(
            grant(500L, 200L, 300L, 400L, null),
            grant(501L, 200L, 301L, 400L, null),
            grant(502L, 200L, 301L, 401L, null)
        ));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(
            operation(400L, 1, 1L, 0L),
            operation(401L, 1, 2L, 0L)
        ));
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenReturn(List.of(
                matched(500L, 200L, 300L, 400L, null),
                matched(501L, 200L, 301L, 400L, null),
                matched(502L, 200L, 301L, 401L, null)
            ));
        PcPermissionConflictRule rule = new PcPermissionConflictRule();
        rule.setId(10L);
        rule.setFirstOperationPermissionId(400L);
        rule.setSecondOperationPermissionId(401L);
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(List.of(rule));

        var result = service.check(request);

        assertTrue(result.isGranted());
        assertEquals(1, result.getGrantedBy().size());
        assertEquals(300L, result.getGrantedBy().get(0).getResourceId());
        assertEquals(400L, result.getGrantedBy().get(0).getOperationId());
    }

    @Test
    void check_approvedConditionalPermission_usesContextAndGrants() {
        PermissionCheckRequest request = baseCheckRequest();
        request.setContext(Map.of("condition:WORKDAY_ONLY", true));
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(grant(500L, 200L, 300L, 400L, 700L)));
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenReturn(List.of(matched(500L, 200L, 300L, 400L, 700L)));
        when(permissionConditionMapper.selectBatchIds(anyCollection()))
            .thenReturn(List.of(condition(700L, "WORKDAY_ONLY", PermissionConstants.CONDITION_SOURCE_PRESET,
                PermissionConstants.CONDITION_STATUS_APPROVED)));
        when(resourceEntityMapper.selectList(any())).thenReturn(List.of(resource(300L, 1)));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(operation(400L, 1, 1L, 0L)));
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(Collections.emptyList());

        var result = service.check(request);

        assertTrue(result.isGranted());
        assertEquals(1, result.getGrantedBy().size());
    }

    @Test
    void check_customExpressionCondition_usesExpressionEngine() {
        PermissionCheckRequest request = baseCheckRequest();
        request.setContext(Map.of("enabled", true, "level", 3));
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(grant(500L, 200L, 300L, 400L, 700L)));
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenReturn(List.of(matched(500L, 200L, 300L, 400L, 700L)));
        when(permissionConditionMapper.selectBatchIds(anyCollection()))
            .thenReturn(List.of(condition(700L, "['enabled'] and ['level'] >= 3",
                PermissionConstants.CONDITION_SOURCE_CUSTOM, PermissionConstants.CONDITION_STATUS_APPROVED)));
        when(resourceEntityMapper.selectList(any())).thenReturn(List.of(resource(300L, 1)));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(operation(400L, 1, 1L, 0L)));
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(Collections.emptyList());

        var result = service.check(request);

        assertTrue(result.isGranted());
        assertEquals(1, result.getGrantedBy().size());
    }

    @Test
    void check_dependencyConditionalPermissionNotSatisfied_returnsDependencyFail() {
        PermissionCheckRequest request = baseCheckRequest();
        request.setCheckDependency(true);
        request.setContext(Map.of("condition:LEVEL_OK", false));
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(
            operation(400L, 1, 1L, 0L),
            operation(401L, 1, 2L, 0L)
        );
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(
            List.of(grant(500L, 200L, 300L, 400L, null)),
            List.of(grant(501L, 200L, 301L, 401L, 700L))
        );
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenReturn(List.of(matched(500L, 200L, 300L, 400L, null)))
            .thenReturn(List.of(matched(501L, 200L, 301L, 401L, 700L)));
        when(permissionConditionMapper.selectBatchIds(anyCollection()))
            .thenReturn(List.of(condition(700L, "LEVEL_OK", PermissionConstants.CONDITION_SOURCE_PRESET,
                PermissionConstants.CONDITION_STATUS_APPROVED)));
        when(resourceEntityMapper.selectList(any())).thenReturn(
            List.of(resource(300L, 1)),
            List.of(resource(301L, 1))
        );
        when(operationPermissionMapper.selectList(any())).thenReturn(
            List.of(operation(400L, 1, 1L, 0L)),
            List.of(operation(401L, 1, 2L, 0L))
        );
        when(resourceDependencyMapper.selectList(any())).thenReturn(List.of(dependency(300L, 301L, 400L, 401L)), Collections.emptyList());

        var result = service.check(request);

        assertFalse(result.isGranted());
        assertEquals(DenyReason.DEPENDENCY_FAIL, result.getDenyReason());
    }

    @Test
    void check_parentInheritance_allowsParentGrantForChildResource() {
        PermissionCheckRequest request = baseCheckRequest();
        request.setResourceEntityId(301L);
        request.setInheritMode(org.dromara.permission.model.permission.InheritMode.PARENT);
        PcResourceEntity child = resource(301L, 1);
        child.setPath("/300/301");
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(child);
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(grant(500L, 200L, 300L, 400L, null)));
        when(resourceEntityMapper.selectList(any())).thenReturn(List.of(resource(300L, 1)));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(operation(400L, 1, 1L, 0L)));
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenReturn(List.of(matched(500L, 200L, 300L, 400L, null)));
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(Collections.emptyList());

        var result = service.check(request);

        assertTrue(result.isGranted());
        assertEquals(300L, result.getGrantedBy().get(0).getResourceId());
    }

    @Test
    void check_bothInheritance_expandsParentAndChildren() {
        PermissionCheckRequest request = baseCheckRequest();
        request.setInheritMode(org.dromara.permission.model.permission.InheritMode.BOTH);
        PcResourceEntity current = resource(300L, 1);
        current.setPath("/100/300");
        PcResourceEntity child = resource(301L, 1);
        child.setPath("/100/300/301");
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(current);
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(resourceEntityMapper.selectList(any())).thenReturn(
            List.of(child),
            List.of(resource(100L, 1), current, child)
        );
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(
            grant(500L, 200L, 100L, 400L, null),
            grant(501L, 200L, 301L, 400L, null)
        ));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(operation(400L, 1, 1L, 0L)));
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenReturn(List.of(
                matched(500L, 200L, 100L, 400L, null),
                matched(501L, 200L, 301L, 400L, null)
            ));
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(Collections.emptyList());

        var result = service.check(request);

        assertTrue(result.isGranted());
        assertEquals(2, result.getGrantedBy().size());
    }

    @Test
    void grant_insertNew_success() {
        GrantPermissionRequest request = new GrantPermissionRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        request.setResourceEntityId(300L);
        request.setOperationPermissionId(400L);
        request.setCanManage(true);

        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(Collections.emptyList());
        doNothing().when(domainScopeValidator).validateGrantScope(eq(1L), eq(null), any(), any(), any());
        when(roleResourcePermissionMapper.insert(any(PcRoleResourcePermission.class))).thenAnswer(invocation -> {
            PcRoleResourcePermission entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });

        var result = service.grant(request);

        assertTrue(result.isSuccess());
        assertEquals(900L, result.getPermissionId());
        verify(changeLogService).log(any(ChangeLogParam.class));
        verify(permissionVersionService).bumpVersion(1L, "role_resource_permission", 900L, "grant");
    }

    @Test
    void grant_pendingCondition_throwsConditionNotApproved() {
        GrantPermissionRequest request = new GrantPermissionRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        request.setResourceEntityId(300L);
        request.setOperationPermissionId(400L);
        request.setConditionId(700L);

        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(permissionConditionMapper.selectOne(any()))
            .thenReturn(condition(700L, "", PermissionConstants.CONDITION_SOURCE_CUSTOM,
                PermissionConstants.CONDITION_STATUS_PENDING));

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.grant(request));

        assertEquals(PermissionErrorCode.CONDITION_NOT_APPROVED, ex.getErrorCode());
    }

    @Test
    void grant_sameExistingPermission_isNoOp() {
        GrantPermissionRequest request = new GrantPermissionRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        request.setResourceEntityId(300L);
        request.setOperationPermissionId(400L);
        request.setCanManage(false);

        PcRoleResourcePermission existing = grant(900L, 200L, 300L, 400L, null);
        existing.setCanManage(false);
        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(existing));
        doNothing().when(domainScopeValidator).validateGrantScope(eq(1L), eq(null), any(), any(), any());

        var result = service.grant(request);

        assertTrue(result.isSuccess());
        assertFalse(result.isChanged());
        verify(roleResourcePermissionMapper, never()).updateById(any(PcRoleResourcePermission.class));
        verify(changeLogService, never()).log(any(ChangeLogParam.class));
        verify(permissionVersionService, never()).bumpVersion(any(), any(), any(), any());
        verify(permissionWriteRefreshEventPublisher, never()).publish(any(), any());
    }

    @Test
    void grant_softDeletedPermission_restoresRecord() {
        GrantPermissionRequest request = new GrantPermissionRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        request.setResourceEntityId(300L);
        request.setOperationPermissionId(400L);
        request.setCanManage(true);
        request.setConditionId(700L);

        PcRoleResourcePermission deleted = grant(901L, 200L, 300L, 400L, null);
        deleted.setDeleteFlag(901L);
        deleted.setDeletedBy(99L);
        deleted.setDeletedAt(java.time.LocalDateTime.now().minusDays(1));
        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(deleted));
        when(permissionConditionMapper.selectOne(any()))
            .thenReturn(condition(700L, "", PermissionConstants.CONDITION_SOURCE_CUSTOM,
                PermissionConstants.CONDITION_STATUS_APPROVED));
        doNothing().when(domainScopeValidator).validateGrantScope(eq(1L), eq(null), any(), any(), any());
        when(permissionVersionService.bumpVersion(1L, "role_resource_permission", 901L, "grant"))
            .thenReturn(version(1L, 7L));

        var result = service.grant(request);

        assertTrue(result.isChanged());
        verify(roleResourcePermissionMapper).updateById(any(PcRoleResourcePermission.class));
        verify(permissionVersionService).bumpVersion(1L, "role_resource_permission", 901L, "grant");
        verify(permissionWriteRefreshEventPublisher).publish(any(), any());
    }

    @Test
    void grantRolePermissions_batchDelegatesWithinSingleServiceBoundary() {
        RolePermissionBatchGrantRequest request = new RolePermissionBatchGrantRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        RolePermissionBatchGrantRequest.RolePermissionGrantItem item1 = new RolePermissionBatchGrantRequest.RolePermissionGrantItem();
        item1.setResourceEntityId(300L);
        item1.setOperationPermissionId(400L);
        RolePermissionBatchGrantRequest.RolePermissionGrantItem item2 = new RolePermissionBatchGrantRequest.RolePermissionGrantItem();
        item2.setResourceEntityId(301L);
        item2.setOperationPermissionId(401L);
        request.setItems(List.of(item1, item2));

        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1), resource(301L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(
            operation(400L, 1, 1L, 0L), operation(401L, 1, 2L, 0L));
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(Collections.emptyList(), Collections.emptyList());
        when(domainScopeValidator.resolveGrantBizDomainId(any(), any(), any())).thenReturn(10L);
        doNothing().when(domainScopeValidator).validateGrantScope(eq(1L), eq(10L), any(), any(), any());
        when(roleResourcePermissionMapper.insert(any(PcRoleResourcePermission.class))).thenAnswer(invocation -> {
            PcRoleResourcePermission entity = invocation.getArgument(0);
            entity.setId(entity.getOperationPermissionId());
            return 1;
        });

        service.grantRolePermissions(request);

        verify(permissionVersionService).bumpVersion(1L, "role_resource_permission", 400L, "grant");
        verify(permissionVersionService).bumpVersion(1L, "role_resource_permission", 401L, "grant");
        ArgumentCaptor<ChangeLogParam> captor = ArgumentCaptor.forClass(ChangeLogParam.class);
        verify(changeLogService, times(3)).log(captor.capture());
        ChangeLogParam batchLog = captor.getAllValues().stream()
            .filter(param -> "batch_role_resource_permission".equals(param.getEntityType()))
            .findFirst()
            .orElseThrow();
        assertEquals("BATCH_GRANT", batchLog.getOperation());
        assertEquals(200L, batchLog.getEntityId());
        assertEquals(10L, batchLog.getBizDomainId());
        assertNotNull(batchLog.getNewSnapshot());
    }

    @Test
    void grantRolePermissions_noOpBatch_skipsAuditLog() {
        RolePermissionBatchGrantRequest request = new RolePermissionBatchGrantRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        RolePermissionBatchGrantRequest.RolePermissionGrantItem item = new RolePermissionBatchGrantRequest.RolePermissionGrantItem();
        item.setResourceEntityId(300L);
        item.setOperationPermissionId(400L);
        request.setItems(List.of(item));

        PcRoleResourcePermission existing = grant(900L, 200L, 300L, 400L, null);
        existing.setCanManage(false);
        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(existing));
        doNothing().when(domainScopeValidator).validateGrantScope(eq(1L), eq(null), any(), any(), any());

        service.grantRolePermissions(request);

        verify(changeLogService, never()).log(argThat(param -> "batch_role_resource_permission".equals(param.getEntityType())));
        verify(permissionVersionService, never()).bumpVersion(any(), any(), any(), any());
    }

    @Test
    void grantRolePermissions_rejectedItem_throwsAndStopsBatch() {
        RolePermissionBatchGrantRequest request = new RolePermissionBatchGrantRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        RolePermissionBatchGrantRequest.RolePermissionGrantItem item = new RolePermissionBatchGrantRequest.RolePermissionGrantItem();
        item.setResourceEntityId(300L);
        item.setOperationPermissionId(400L);
        request.setItems(List.of(item));

        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, API_TYPE));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, API_TYPE, 1L, 0L));
        when(resourceApiMappingService.listEnabledMappings(eq(1L), anyCollection())).thenReturn(Collections.emptyList());

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.grantRolePermissions(request));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
        verify(changeLogService, never()).log(argThat(param -> "batch_role_resource_permission".equals(param.getEntityType())));
    }

    @Test
    void revoke_existing_success() {
        RevokePermissionRequest request = new RevokePermissionRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        request.setResourceEntityId(300L);
        request.setOperationPermissionId(400L);

        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(roleResourcePermissionMapper.selectOne(any())).thenReturn(grant(900L, 200L, 300L, 400L, null));

        var result = service.revoke(request);

        assertTrue(result.isSuccess());
        assertEquals(900L, result.getPermissionId());
        verify(roleResourcePermissionMapper).updateById(any(PcRoleResourcePermission.class));
        verify(changeLogService).log(any(ChangeLogParam.class));
        verify(permissionVersionService).bumpVersion(1L, "role_resource_permission", 900L, "revoke");
    }

    @Test
    void revoke_missingPermission_isNoOp() {
        RevokePermissionRequest request = new RevokePermissionRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        request.setResourceEntityId(300L);
        request.setOperationPermissionId(400L);

        when(roleResourcePermissionMapper.selectOne(any())).thenReturn(null);

        var result = service.revoke(request);

        assertTrue(result.isSuccess());
        assertFalse(result.isChanged());
        verify(changeLogService, never()).log(any(ChangeLogParam.class));
        verify(permissionVersionService, never()).bumpVersion(any(), any(), any(), any());
    }

    @Test
    void revokeRolePermissions_writesBatchAuditLog() {
        RolePermissionBatchRevokeRequest request = new RolePermissionBatchRevokeRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        RolePermissionBatchRevokeRequest.RolePermissionRevokeItem item = new RolePermissionBatchRevokeRequest.RolePermissionRevokeItem();
        item.setResourceEntityId(300L);
        item.setOperationPermissionId(400L);
        request.setItems(List.of(item));

        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, 1, 1L, 0L));
        when(roleResourcePermissionMapper.selectOne(any())).thenReturn(grant(900L, 200L, 300L, 400L, null));
        when(domainScopeValidator.resolveGrantBizDomainId(any(), any(), any())).thenReturn(10L);

        service.revokeRolePermissions(request);

        ArgumentCaptor<ChangeLogParam> captor = ArgumentCaptor.forClass(ChangeLogParam.class);
        verify(changeLogService, times(2)).log(captor.capture());
        ChangeLogParam batchLog = captor.getAllValues().stream()
            .filter(param -> "batch_role_resource_permission".equals(param.getEntityType()))
            .findFirst()
            .orElseThrow();
        assertEquals("BATCH_REVOKE", batchLog.getOperation());
        assertEquals(10L, batchLog.getBizDomainId());
        assertNotNull(batchLog.getOldSnapshot());
    }

    @Test
    void revokeRolePermissions_noExistingRecord_skipsBatchAuditLog() {
        RolePermissionBatchRevokeRequest request = new RolePermissionBatchRevokeRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        RolePermissionBatchRevokeRequest.RolePermissionRevokeItem item = new RolePermissionBatchRevokeRequest.RolePermissionRevokeItem();
        item.setResourceEntityId(300L);
        item.setOperationPermissionId(400L);
        request.setItems(List.of(item));

        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, 1));
        when(roleResourcePermissionMapper.selectOne(any())).thenReturn(null);

        service.revokeRolePermissions(request);

        verify(changeLogService, never()).log(argThat(param -> "batch_role_resource_permission".equals(param.getEntityType())));
        verify(permissionVersionService, never()).bumpVersion(any(), any(), any(), any());
    }

    @Test
    void buildSnapshot_skipsConditionalAndBuildsEntries() {
        SnapshotRequest request = new SnapshotRequest();
        request.setTenantId(1L);
        request.setAbstractUserId(100L);
        request.setIncludeConditional(false);

        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(version(1L, 3L));
        when(permissionVersionService.buildVersionToken(1L, 3L)).thenReturn("1-v3");
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(
            grant(900L, 200L, 300L, 400L, null),
            grant(901L, 200L, 301L, 401L, 700L)
        ));
        when(resourceEntityMapper.selectList(any())).thenReturn(List.of(resource(300L, 1), resource(301L, 1)));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(
            operation(400L, 1, 1L, 0L), operation(401L, 1, 2L, 0L)
        ));
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(Collections.emptyList());

        var snapshot = service.buildSnapshot(request);

        assertEquals("1-v3", snapshot.getVersionToken());
        assertEquals(1, snapshot.getEntries().size());
        assertEquals("RES-300", snapshot.getEntries().get(0).getResourceCode());
    }

    @Test
    void buildSnapshot_includeConditional_onlyKeepsApprovedConditions() {
        SnapshotRequest request = new SnapshotRequest();
        request.setTenantId(1L);
        request.setAbstractUserId(100L);
        request.setIncludeConditional(true);

        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(version(1L, 3L));
        when(permissionVersionService.buildVersionToken(1L, 3L)).thenReturn("1-v3");
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(
            grant(900L, 200L, 300L, 400L, 700L),
            grant(901L, 200L, 301L, 401L, 701L)
        ));
        when(resourceEntityMapper.selectList(any())).thenReturn(List.of(resource(300L, 1), resource(301L, 1)));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(
            operation(400L, 1, 1L, 0L), operation(401L, 1, 2L, 0L)
        ));
        when(permissionConditionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
            condition(700L, "WORKDAY_ONLY", PermissionConstants.CONDITION_SOURCE_PRESET,
                PermissionConstants.CONDITION_STATUS_APPROVED),
            condition(701L, "LEVEL_CHECK", PermissionConstants.CONDITION_SOURCE_CUSTOM,
                PermissionConstants.CONDITION_STATUS_PENDING)
        ));
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(Collections.emptyList());

        var snapshot = service.buildSnapshot(request);

        assertEquals(1, snapshot.getEntries().size());
        assertEquals(700L, snapshot.getEntries().get(0).getConditionId());
    }

    @Test
    void buildSnapshot_apiResource_assemblesRouteEntry() {
        SnapshotRequest request = new SnapshotRequest();
        request.setTenantId(1L);
        request.setAbstractUserId(100L);
        request.setIncludeConditional(false);

        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(version(1L, 5L));
        when(permissionVersionService.buildVersionToken(1L, 5L)).thenReturn("1-v5");
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(
            grant(900L, 200L, 300L, 400L, null)
        ));
        when(resourceEntityMapper.selectList(any())).thenReturn(List.of(resource(300L, API_TYPE)));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(
            operation(400L, API_TYPE, 1L, 0L)
        ));
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, API_TYPE)).thenReturn(Collections.emptyList());
        when(resourceApiMappingService.listEnabledMappings(eq(1L), anyCollection())).thenReturn(List.of(apiMapping(600L, 300L)));

        var snapshot = service.buildSnapshot(request);

        assertEquals(1, snapshot.getEntries().size());
        assertEquals("system-service", snapshot.getEntries().get(0).getServiceCode());
        assertEquals("GET", snapshot.getEntries().get(0).getHttpMethod());
        assertEquals("/api/system/user/list", snapshot.getEntries().get(0).getPathPattern());
    }

    @Test
    void grant_apiResourceWithoutMapping_rejected() {
        GrantPermissionRequest request = new GrantPermissionRequest();
        request.setTenantId(1L);
        request.setAbstractRoleId(200L);
        request.setResourceEntityId(300L);
        request.setOperationPermissionId(400L);

        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, API_TYPE));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, API_TYPE, 1L, 0L));
        doNothing().when(domainScopeValidator).validateGrantScope(eq(1L), eq(null), any(), any(), any());
        when(resourceApiMappingService.listEnabledMappings(eq(1L), anyCollection())).thenReturn(Collections.emptyList());

        var result = service.grant(request);

        assertFalse(result.isSuccess());
        assertEquals(List.of("API resource missing enabled route mapping"), result.getRejectReasons());
    }

    @Test
    void check_apiRequestContext_doesNotGrantOtherResourcePermission() {
        PermissionCheckRequest request = baseCheckRequest();
        request.setContext(Map.of("requestPath", "/api/system/user/list", "httpMethod", "GET", "serviceCode", "system-service"));
        request.setResourceEntityId(300L);
        request.setOperationPermissionId(400L);
        when(roleResolverService.resolve(1L, 100L, null)).thenReturn(List.of(resolvedRole(200L, 1)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(300L, API_TYPE));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(400L, API_TYPE, 1L, 0L));
        when(roleResourcePermissionMapper.selectList(any())).thenReturn(List.of(grant(500L, 200L, 301L, 400L, null)));

        var result = service.check(request);

        assertFalse(result.isGranted());
        assertEquals(DenyReason.NO_PERMISSION, result.getDenyReason());
    }

    @Test
    void queryVersion_returnsToken() {
        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(version(1L, 6L));
        when(permissionVersionService.buildVersionToken(1L, 6L)).thenReturn("1-v6");

        PermissionVersionQueryRequest request = new PermissionVersionQueryRequest();
        request.setTenantId(1L);

        var result = service.queryVersion(request);

        assertEquals(6L, result.getVersionNo());
        assertEquals("1-v6", result.getVersionToken());
    }

    @Test
    void assignUserRoles_bumpsVersion() {
        UserRoleBatchAssignRequest request = new UserRoleBatchAssignRequest();
        request.setTenantId(1L);
        request.setAbstractUserId(100L);
        request.setRequestId("req-assign");
        request.setChangeSource("ADMIN");
        request.setChangeReason("assign roles");
        request.setRoleIds(List.of(200L));

        when(abstractUserMapper.selectOne(any())).thenReturn(buildUser(100L));
        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(userRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(permissionVersionService.bumpVersion(1L, "user_role", 100L, "assign-user-roles"))
            .thenReturn(version(1L, 8L));

        service.assignUserRoles(request);

        verify(userRoleMapper).insert(any(PcUserRole.class));
        ArgumentCaptor<ChangeLogParam> captor = ArgumentCaptor.forClass(ChangeLogParam.class);
        verify(changeLogService).log(captor.capture());
        assertEquals("batch_user_role", captor.getValue().getEntityType());
        assertEquals("BATCH_ASSIGN", captor.getValue().getOperation());
        assertNotNull(captor.getValue().getNewSnapshot());
        assertEquals("req-assign", captor.getValue().getRequestId());
        assertEquals("ADMIN", captor.getValue().getChangeSource());
        assertEquals("assign roles", captor.getValue().getChangeReason());
        verify(permissionVersionService).bumpVersion(1L, "user_role", 100L, "assign-user-roles");
        verify(permissionWriteRefreshEventPublisher).publish(any(), any());
    }

    @Test
    void assignUserRoles_sameAssignment_isNoOp() {
        UserRoleBatchAssignRequest request = new UserRoleBatchAssignRequest();
        request.setTenantId(1L);
        request.setAbstractUserId(100L);
        request.setRoleIds(List.of(200L));
        request.setValidFrom(java.time.LocalDateTime.of(2025, 1, 1, 0, 0));
        request.setValidTo(java.time.LocalDateTime.of(2025, 12, 31, 0, 0));

        PcUserRole existing = grantUserRole(300L, 100L, 200L);
        existing.setValidFrom(request.getValidFrom());
        existing.setValidTo(request.getValidTo());
        when(abstractUserMapper.selectOne(any())).thenReturn(buildUser(100L));
        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(existing));

        service.assignUserRoles(request);

        verify(userRoleMapper, never()).insert(any(PcUserRole.class));
        verify(userRoleMapper, never()).updateById(any(PcUserRole.class));
        verify(changeLogService, never()).log(any(ChangeLogParam.class));
        verify(permissionVersionService, never()).bumpVersion(any(), any(), any(), any());
    }

    @Test
    void assignUserRoles_softDeletedAssignment_restoresRecord() {
        UserRoleBatchAssignRequest request = new UserRoleBatchAssignRequest();
        request.setTenantId(1L);
        request.setAbstractUserId(100L);
        request.setRoleIds(List.of(200L));

        PcUserRole deleted = grantUserRole(300L, 100L, 200L);
        deleted.setDeleteFlag(300L);
        deleted.setDeletedBy(99L);
        deleted.setDeletedAt(java.time.LocalDateTime.now().minusDays(1));
        when(abstractUserMapper.selectOne(any())).thenReturn(buildUser(100L));
        when(abstractRoleMapper.selectOne(any())).thenReturn(role(200L, null, 1));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(deleted));
        when(permissionVersionService.bumpVersion(1L, "user_role", 100L, "assign-user-roles"))
            .thenReturn(version(1L, 10L));

        service.assignUserRoles(request);

        verify(userRoleMapper).updateById(any(PcUserRole.class));
        verify(permissionVersionService).bumpVersion(1L, "user_role", 100L, "assign-user-roles");
        verify(permissionWriteRefreshEventPublisher).publish(any(), any());
    }

    @Test
    void revokeUserRoles_bumpsVersion() {
        UserRoleBatchRevokeRequest request = new UserRoleBatchRevokeRequest();
        request.setTenantId(1L);
        request.setAbstractUserId(100L);
        request.setRequestId("req-revoke");
        request.setChangeSource("ADMIN");
        request.setChangeReason("revoke roles");
        request.setRoleIds(List.of(200L));

        when(userRoleMapper.selectOne(any())).thenReturn(grantUserRole(300L, 100L, 200L));
        when(permissionVersionService.bumpVersion(1L, "user_role", 100L, "revoke-user-roles"))
            .thenReturn(version(1L, 9L));

        service.revokeUserRoles(request);

        verify(userRoleMapper).updateById(any(PcUserRole.class));
        ArgumentCaptor<ChangeLogParam> captor = ArgumentCaptor.forClass(ChangeLogParam.class);
        verify(changeLogService).log(captor.capture());
        assertEquals("batch_user_role", captor.getValue().getEntityType());
        assertEquals("BATCH_REVOKE", captor.getValue().getOperation());
        assertNotNull(captor.getValue().getOldSnapshot());
        assertEquals("req-revoke", captor.getValue().getRequestId());
        assertEquals("ADMIN", captor.getValue().getChangeSource());
        assertEquals("revoke roles", captor.getValue().getChangeReason());
        verify(permissionVersionService).bumpVersion(1L, "user_role", 100L, "revoke-user-roles");
        verify(permissionWriteRefreshEventPublisher).publish(any(), any());
    }

    @Test
    void revokeUserRoles_noExistingAssignment_skipsVersion() {
        UserRoleBatchRevokeRequest request = new UserRoleBatchRevokeRequest();
        request.setTenantId(1L);
        request.setAbstractUserId(100L);
        request.setRoleIds(List.of(200L));

        when(userRoleMapper.selectOne(any())).thenReturn(null);

        service.revokeUserRoles(request);

        verify(userRoleMapper, never()).updateById(any(PcUserRole.class));
        verify(changeLogService, never()).log(any(ChangeLogParam.class));
        verify(permissionVersionService, never()).bumpVersion(any(), any(), any(), any());
        verify(permissionWriteRefreshEventPublisher, never()).publish(any(), any());
    }

    @Test
    void assignUserRoles_invalidValidityWindow_throws() {
        UserRoleBatchAssignRequest request = new UserRoleBatchAssignRequest();
        request.setTenantId(1L);
        request.setAbstractUserId(100L);
        request.setRoleIds(List.of(200L));
        request.setValidFrom(java.time.LocalDateTime.of(2025, 12, 31, 0, 0));
        request.setValidTo(java.time.LocalDateTime.of(2025, 1, 1, 0, 0));

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.assignUserRoles(request));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
        verify(userRoleMapper, never()).insert(any(PcUserRole.class));
    }

    private PermissionCheckRequest baseCheckRequest() {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setTenantId(1L);
        request.setAbstractUserId(100L);
        request.setResourceEntityId(300L);
        request.setOperationPermissionId(400L);
        request.setContext(Map.of());
        request.setCheckDependency(false);
        return request;
    }

    private ResolvedRole resolvedRole(Long roleId, Integer roleType) {
        ResolvedRole role = new ResolvedRole();
        role.setRoleId(roleId);
        role.setRoleType(roleType);
        return role;
    }

    private PcResourceEntity resource(Long id, Integer resourceType) {
        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(id);
        resource.setTenantId(1L);
        resource.setCode("RES-" + id);
        resource.setResourceType(resourceType);
        resource.setPath("/" + id);
        resource.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return resource;
    }

    private PcOperationPermission operation(Long id, Integer resourceType, Long binaryBit, Long inheritMask) {
        PcOperationPermission operation = new PcOperationPermission();
        operation.setId(id);
        operation.setTenantId(1L);
        operation.setCode("OP-" + id);
        operation.setResourceType(resourceType);
        operation.setBinaryBit(binaryBit);
        operation.setInheritMask(inheritMask);
        operation.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return operation;
    }

    private PcRoleResourcePermission grant(Long id, Long roleId, Long resourceId, Long opId, Long conditionId) {
        PcRoleResourcePermission grant = new PcRoleResourcePermission();
        grant.setId(id);
        grant.setTenantId(1L);
        grant.setAbstractRoleId(roleId);
        grant.setResourceEntityId(resourceId);
        grant.setOperationPermissionId(opId);
        grant.setConditionId(conditionId);
        grant.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return grant;
    }

    private MatchedPermission matched(Long permissionId, Long roleId, Long resourceId, Long opId, Long conditionId) {
        MatchedPermission matched = new MatchedPermission();
        matched.setPermissionId(permissionId);
        matched.setRoleId(roleId);
        matched.setResourceId(resourceId);
        matched.setOperationId(opId);
        matched.setConditionId(conditionId);
        return matched;
    }

    private PcAbstractRole role(Long id, Long bizDomainId, Integer roleType) {
        PcAbstractRole role = new PcAbstractRole();
        role.setId(id);
        role.setTenantId(1L);
        role.setBizDomainId(bizDomainId);
        role.setRoleType(roleType);
        role.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return role;
    }

    private PcPermissionVersion version(Long tenantId, Long versionNo) {
        PcPermissionVersion version = new PcPermissionVersion();
        version.setTenantId(tenantId);
        version.setVersionNo(versionNo);
        version.setTriggerEntityType("role_resource_permission");
        version.setTriggerEntityId(900L);
        version.setRemark("test");
        version.setUpdatedAt(java.time.LocalDateTime.now());
        return version;
    }

    private PcAbstractUser buildUser(Long id) {
        PcAbstractUser user = new PcAbstractUser();
        user.setId(id);
        user.setTenantId(1L);
        user.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return user;
    }

    private PcPermissionCondition condition(Long id, String expression, String conditionSource, String status) {
        PcPermissionCondition condition = new PcPermissionCondition();
        condition.setId(id);
        condition.setTenantId(1L);
        condition.setCode(expression == null || expression.isBlank() ? "COND-" + id : expression);
        condition.setExpression(expression);
        condition.setConditionSource(conditionSource);
        condition.setStatus(status);
        condition.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return condition;
    }

    private PcResourceApiMapping apiMapping(Long id, Long resourceId) {
        PcResourceApiMapping mapping = new PcResourceApiMapping();
        mapping.setId(id);
        mapping.setTenantId(1L);
        mapping.setResourceEntityId(resourceId);
        mapping.setServiceCode("system-service");
        mapping.setHttpMethod("GET");
        mapping.setPathPattern("/api/system/user/list");
        mapping.setEnabled(Boolean.TRUE);
        mapping.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return mapping;
    }

    private org.dromara.permission.domain.PcResourceDependency dependency(Long resourceId, Long dependsOnResourceId,
                                                                          Long sourceOperationId, Long requiredOperationId) {
        org.dromara.permission.domain.PcResourceDependency dependency = new org.dromara.permission.domain.PcResourceDependency();
        dependency.setId(1L);
        dependency.setTenantId(1L);
        dependency.setResourceEntityId(resourceId);
        dependency.setDependsOnResourceEntityId(dependsOnResourceId);
        dependency.setSourceOperationPermissionId(sourceOperationId);
        dependency.setRequiredOperationPermissionId(requiredOperationId);
        dependency.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return dependency;
    }

    private PcUserRole grantUserRole(Long id, Long userId, Long roleId) {
        PcUserRole entity = new PcUserRole();
        entity.setId(id);
        entity.setTenantId(1L);
        entity.setAbstractUserId(userId);
        entity.setAbstractRoleId(roleId);
        entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return entity;
    }

    private void mockTypeDefinitions() {
        org.mockito.Mockito.lenient().when(typeDefinitionReader.findTypeName(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(ResourceTypeConstants.TYPE_KEY),
                org.mockito.ArgumentMatchers.anyInt()))
            .thenAnswer(invocation -> Optional.ofNullable(resolveTypeName(invocation.getArgument(2))));
    }

    private String resolveTypeName(Integer resourceType) {
        if (resourceType == null) {
            return null;
        }
        return switch (resourceType) {
            case MENU_TYPE -> ResourceTypeConstants.MENU;
            case API_TYPE -> ResourceTypeConstants.API;
            case DATA_TYPE -> ResourceTypeConstants.DATA;
            default -> null;
        };
    }
}
