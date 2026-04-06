package org.dromara.permission.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcPermissionConflictRule;
import org.dromara.permission.domain.PcResourceDependency;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.mapper.PcOperationPermissionMapper;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.dromara.permission.mapper.PcPermissionConflictRuleMapper;
import org.dromara.permission.mapper.PcResourceDependencyMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.model.permission.ConflictDetail;
import org.dromara.permission.model.permission.DependencyCheckResult;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.service.OperationInheritanceService;
import org.dromara.permission.service.ResourceApiMappingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionBridgeSupportTest {

    @Mock
    private OperationInheritanceService operationInheritanceService;
    @Mock
    private ResourceApiMappingService resourceApiMappingService;
    @Mock
    private PcResourceEntityMapper resourceEntityMapper;
    @Mock
    private PcOperationPermissionMapper operationPermissionMapper;
    @Mock
    private PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock
    private PcPermissionConditionMapper permissionConditionMapper;
    @Mock
    private PcPermissionConflictRuleMapper permissionConflictRuleMapper;
    @Mock
    private PcResourceDependencyMapper resourceDependencyMapper;

    @InjectMocks
    private PermissionBridgeSupport support;

    @Test
    void detectConflicts_ignoresRulesFromOtherBizDomains() {
        PcResourceEntity resource = resource(200L, 20L, 7);
        List<MatchedPermission> matchedPermissions = List.of(
            matchedPermission(200L, 101L),
            matchedPermission(200L, 102L)
        );
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 7))
            .thenReturn(List.of(conflictRule(101L, 102L, 10L, 7)));

        List<ConflictDetail> conflicts = support.detectConflicts(1L, resource, matchedPermissions);

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void checkDependencies_filtersNestedConflictedPermissions() {
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenAnswer(invocation -> new ArrayList<>((List<MatchedPermission>) invocation.getArgument(0)));
        when(resourceDependencyMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(dependency(10L, 20L, 21L)))
            .thenReturn(Collections.emptyList());
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            grant(30L, 20L, 21L),
            grant(30L, 20L, 22L)
        ));
        when(operationPermissionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(operation(21L, 7));
        when(operationPermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            operation(21L, 7),
            operation(22L, 7)
        ));
        when(resourceEntityMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(resource(20L, 10L, 7));
        when(resourceEntityMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(resource(20L, 10L, 7)));
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 7))
            .thenReturn(List.of(conflictRule(21L, 22L, 10L, 7)));

        DependencyCheckResult result = support.checkDependencies(
            new PermissionContext(1L, 100L, 10L, null, null), 10L, 11L, List.of(30L));

        assertFalse(result.isSatisfied());
        assertEquals(1, result.getGaps().size());
        assertEquals(20L, result.getGaps().get(0).getResourceEntityId());
        assertEquals(21L, result.getGaps().get(0).getOperationPermissionId());
    }

    @Test
    void checkDependencies_supportsLongAcyclicChains() {
        when(operationInheritanceService.filterByInheritance(any(), any(), any()))
            .thenAnswer(invocation -> new ArrayList<>((List<MatchedPermission>) invocation.getArgument(0)));
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(1L, 7)).thenReturn(Collections.emptyList());
        when(resourceDependencyMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(dependency(1L, 2L, 102L)))
            .thenReturn(List.of(dependency(2L, 3L, 103L)))
            .thenReturn(List.of(dependency(3L, 4L, 104L)))
            .thenReturn(List.of(dependency(4L, 5L, 105L)))
            .thenReturn(List.of(dependency(5L, 6L, 106L)))
            .thenReturn(List.of(dependency(6L, 7L, 107L)))
            .thenReturn(Collections.emptyList());
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(grant(30L, 2L, 102L)))
            .thenReturn(List.of(grant(30L, 3L, 103L)))
            .thenReturn(List.of(grant(30L, 4L, 104L)))
            .thenReturn(List.of(grant(30L, 5L, 105L)))
            .thenReturn(List.of(grant(30L, 6L, 106L)))
            .thenReturn(List.of(grant(30L, 7L, 107L)));
        when(operationPermissionMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(operation(102L, 7))
            .thenReturn(operation(103L, 7))
            .thenReturn(operation(104L, 7))
            .thenReturn(operation(105L, 7))
            .thenReturn(operation(106L, 7))
            .thenReturn(operation(107L, 7));
        when(operationPermissionMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(operation(102L, 7)))
            .thenReturn(List.of(operation(103L, 7)))
            .thenReturn(List.of(operation(104L, 7)))
            .thenReturn(List.of(operation(105L, 7)))
            .thenReturn(List.of(operation(106L, 7)))
            .thenReturn(List.of(operation(107L, 7)));
        when(resourceEntityMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(resource(2L, 10L, 7))
            .thenReturn(resource(3L, 10L, 7))
            .thenReturn(resource(4L, 10L, 7))
            .thenReturn(resource(5L, 10L, 7))
            .thenReturn(resource(6L, 10L, 7))
            .thenReturn(resource(7L, 10L, 7));
        when(resourceEntityMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(resource(2L, 10L, 7)))
            .thenReturn(List.of(resource(3L, 10L, 7)))
            .thenReturn(List.of(resource(4L, 10L, 7)))
            .thenReturn(List.of(resource(5L, 10L, 7)))
            .thenReturn(List.of(resource(6L, 10L, 7)))
            .thenReturn(List.of(resource(7L, 10L, 7)));

        DependencyCheckResult result = support.checkDependencies(
            new PermissionContext(1L, 100L, 10L, null, null), 1L, 101L, List.of(30L));

        assertTrue(result.isSatisfied());
    }

    private PcPermissionConflictRule conflictRule(Long firstOpId, Long secondOpId, Long bizDomainId, Integer resourceType) {
        PcPermissionConflictRule rule = new PcPermissionConflictRule();
        rule.setId(1L);
        rule.setTenantId(1L);
        rule.setBizDomainId(bizDomainId);
        rule.setFirstOperationPermissionId(firstOpId);
        rule.setSecondOperationPermissionId(secondOpId);
        rule.setResourceTypeValue(resourceType);
        rule.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return rule;
    }

    private PcResourceDependency dependency(Long resourceId, Long dependsOnResourceId, Long requiredOperationId) {
        PcResourceDependency dependency = new PcResourceDependency();
        dependency.setTenantId(1L);
        dependency.setResourceEntityId(resourceId);
        dependency.setDependsOnResourceEntityId(dependsOnResourceId);
        dependency.setRequiredOperationPermissionId(requiredOperationId);
        dependency.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return dependency;
    }

    private PcRoleResourcePermission grant(Long roleId, Long resourceId, Long operationId) {
        PcRoleResourcePermission grant = new PcRoleResourcePermission();
        grant.setTenantId(1L);
        grant.setAbstractRoleId(roleId);
        grant.setResourceEntityId(resourceId);
        grant.setOperationPermissionId(operationId);
        grant.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return grant;
    }

    private PcOperationPermission operation(Long operationId, Integer resourceType) {
        PcOperationPermission operation = new PcOperationPermission();
        operation.setId(operationId);
        operation.setTenantId(1L);
        operation.setResourceType(resourceType);
        operation.setBinaryBit(operationId);
        operation.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return operation;
    }

    private PcResourceEntity resource(Long resourceId, Long bizDomainId, Integer resourceType) {
        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(resourceId);
        resource.setTenantId(1L);
        resource.setBizDomainId(bizDomainId);
        resource.setResourceType(resourceType);
        resource.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return resource;
    }

    private MatchedPermission matchedPermission(Long resourceId, Long operationId) {
        MatchedPermission permission = new MatchedPermission();
        permission.setResourceId(resourceId);
        permission.setOperationId(operationId);
        return permission;
    }
}
