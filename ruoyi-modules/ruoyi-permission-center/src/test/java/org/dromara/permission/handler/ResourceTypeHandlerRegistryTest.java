package org.dromara.permission.handler;

import org.dromara.permission.condition.PermissionConditionExpressionEvaluator;
import org.dromara.permission.condition.PermissionConditionPresetHandlerRegistry;
import org.dromara.permission.condition.builtin.InternalIpConditionHandler;
import org.dromara.permission.condition.builtin.WorkdayOnlyConditionHandler;
import org.dromara.permission.constant.ResourceTypeConstants;
import org.dromara.permission.event.PermissionConflictEventPublisher;
import org.dromara.permission.handler.types.ApiResourceTypeHandler;
import org.dromara.permission.handler.types.DataResourceTypeHandler;
import org.dromara.permission.handler.types.MenuResourceTypeHandler;
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
import org.dromara.permission.service.OperationInheritanceService;
import org.dromara.permission.service.ResourceApiMappingService;
import org.dromara.permission.service.impl.OperationInheritanceServiceImpl;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.dromara.permission.service.support.TypeDefinitionReader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ResourceTypeHandlerRegistryTest {

    @Mock private ResourceApiMappingService resourceApiMappingService;
    @Mock private org.dromara.permission.mapper.PcResourceEntityMapper resourceEntityMapper;
    @Mock private org.dromara.permission.mapper.PcOperationPermissionMapper operationPermissionMapper;
    @Mock private org.dromara.permission.mapper.PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock private org.dromara.permission.mapper.PcPermissionConditionMapper permissionConditionMapper;
    @Mock private org.dromara.permission.mapper.PcPermissionConflictRuleMapper permissionConflictRuleMapper;
    @Mock private org.dromara.permission.mapper.PcResourceDependencyMapper resourceDependencyMapper;
    @Mock private TypeDefinitionReader typeDefinitionReader;

    @Test
    void getHandler_returnsSpecializedHandlerOrDefault() {
        OperationInheritanceService operationInheritanceService = new OperationInheritanceServiceImpl();
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
        PermissionConflictEventPublisher conflictEventPublisher = (ctx, conflicts) -> { };
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
        when(typeDefinitionReader.findTypeName(anyLong(), eq(ResourceTypeConstants.TYPE_KEY), anyInt()))
            .thenAnswer(invocation -> Optional.ofNullable(switch ((Integer) invocation.getArgument(2)) {
                case 1 -> ResourceTypeConstants.MENU;
                case 2 -> ResourceTypeConstants.API;
                case 3 -> ResourceTypeConstants.DATA;
                case 4 -> ResourceTypeConstants.BUTTON;
                default -> null;
            }));

        ResourceTypeHandlerRegistry registry = new ResourceTypeHandlerRegistry(List.of(
            defaultHandler,
            new ApiResourceTypeHandler(
                defaultInheritanceExpander,
                defaultConditionEvaluator,
                defaultConflictDetector,
                defaultDependencyChecker,
                new ApiPermissionMatcher(bridgeSupport),
                new ApiGrantValidator(resourceApiMappingService),
                new ApiSnapshotAssembler(bridgeSupport)
            ),
            new MenuResourceTypeHandler(
                defaultPermissionMatcher,
                new MenuInheritanceExpander(bridgeSupport),
                defaultConditionEvaluator,
                defaultConflictDetector,
                defaultDependencyChecker,
                defaultGrantValidator,
                defaultSnapshotAssembler
            ),
            new DataResourceTypeHandler(
                defaultPermissionMatcher,
                new DataInheritanceExpander(bridgeSupport),
                defaultConditionEvaluator,
                defaultConflictDetector,
                defaultDependencyChecker,
                defaultGrantValidator,
                new DataSnapshotAssembler(bridgeSupport)
            )
        ), typeDefinitionReader);

        assertInstanceOf(MenuResourceTypeHandler.class, registry.getHandler(1L, 1));
        assertInstanceOf(ApiResourceTypeHandler.class, registry.getHandler(1L, 2));
        assertInstanceOf(DataResourceTypeHandler.class, registry.getHandler(1L, 3));
        assertSame(defaultHandler, registry.getHandler(1L, 4));
        assertSame(defaultHandler, registry.getHandler(1L, null));
        assertThrows(RuntimeException.class, () -> registry.getHandler(1L, 999));
    }
}
