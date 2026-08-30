package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantDomainService.GrantCheckKey;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionGrantDomainServiceImplTest {

    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private SubjectDomainService subjectDomainService;
    @Mock
    private OperationPermissionMapper operationPermissionMapper;
    @Mock
    private RoleResourcePermissionMapper roleResourcePermissionMapper;

    private PermissionGrantDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionGrantDomainServiceImpl(
            typeResolutionService,
            subjectDomainService,
            operationPermissionMapper,
            roleResourcePermissionMapper
        );
    }

    @Test
    void canGrantPermissionShouldAllowInheritedGrantCoverage() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));

        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        OperationPermission manageOp = operation(102L, 1, "MANAGE", 8L, 1L);

        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        when(operationPermissionMapper.selectByTenantAndResourceType(1L, null)).thenReturn(List.of(viewOp, manageOp));
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", PermConstants.CodeType.DEFAULT, null), 100L));

        RoleResourcePermission perm = new RoleResourcePermission();
        perm.setAbstractRoleId(20L);
        perm.setResourceEntityId(100L);
        perm.setResourceType(1);
        perm.setGrantedBits(8L);
        perm.setCanGrant(true);
        perm.setScopeAll(false);

        when(roleResourcePermissionMapper.selectValidByRoleIds(1L, Set.of(20L))).thenReturn(List.of(perm));

        boolean allowed = service.canGrantPermission(1L, 10L, "MENU", "sys:user", PermConstants.CodeType.DEFAULT, "VIEW", false, null);

        assertTrue(allowed);
    }

    @Test
    void canGrantPermissionShouldDenyWhenCodeTypeDiffers() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));

        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        when(operationPermissionMapper.selectByTenantAndResourceType(1L, null)).thenReturn(List.of(viewOp));
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", "ID", null), 200L));

        RoleResourcePermission perm = new RoleResourcePermission();
        perm.setAbstractRoleId(20L);
        perm.setResourceEntityId(100L);
        perm.setResourceType(1);
        perm.setGrantedBits(1L);
        perm.setCanGrant(true);
        perm.setScopeAll(false);
        when(roleResourcePermissionMapper.selectValidByRoleIds(1L, Set.of(20L))).thenReturn(List.of(perm));

        boolean allowed = service.canGrantPermission(1L, 10L, "MENU", "sys:user", "ID", "VIEW", false, null);

        assertFalse(allowed);
    }

    @Test
    void shouldRejectSecondManualGrantEvenWhenConditionDiffers() {
        RoleResourcePermission existing = permission(501L, "MANUAL", null, 2L);
        RoleResourcePermission candidate = permission(null, "MANUAL", 99L, 2L);

        BizException exception = assertThrows(BizException.class, () ->
            service.validateSingleManualGrants(List.of(existing), List.of(candidate), Set.of()));

        assertEquals(20033, exception.getErrorCode());
    }

    @Test
    void shouldAllowReplacingRemovedManualGrantAndCoexistingAutoDependency() {
        RoleResourcePermission removed = permission(501L, "MANUAL", null, 2L);
        RoleResourcePermission autoDependency = permission(502L, "AUTO_DEP", null, 2L);
        RoleResourcePermission replacement = permission(null, "MANUAL", 99L, 2L);

        assertDoesNotThrow(() -> service.validateSingleManualGrants(
            List.of(removed, autoDependency), List.of(replacement), Set.of(501L)));
    }

    @Test
    void shouldRejectCombinationBitsForNewManualGrant() {
        RoleResourcePermission combination = permission(null, "MANUAL", null, 6L);

        BizException exception = assertThrows(BizException.class, () ->
            service.validateSingleManualGrants(List.of(), List.of(combination), Set.of()));

        assertEquals(20027, exception.getErrorCode());
    }

    @Test
    void shouldScopeChildGrantUniquenessToParentPermission() {
        RoleResourcePermission existingChild = permission(601L, "MANUAL", null, 2L);
        existingChild.setDependOn(701L);
        RoleResourcePermission sameParent = permission(null, "MANUAL", 99L, 2L);
        sameParent.setDependOn(701L);
        RoleResourcePermission otherParent = permission(null, "MANUAL", 99L, 2L);
        otherParent.setDependOn(702L);

        BizException exception = assertThrows(BizException.class, () ->
            service.validateSingleManualGrants(List.of(existingChild), List.of(sameParent), Set.of()));
        assertEquals(20033, exception.getErrorCode());
        assertDoesNotThrow(() ->
            service.validateSingleManualGrants(List.of(existingChild), List.of(otherParent), Set.of()));
    }

    @Test
    void shouldRejectConditionalPermissionWithCanGrantEnabled() {
        RoleResourcePermission permission = permission(null, "MANUAL", 99L, 2L);
        permission.setCanGrant(true);

        BizException exception = assertThrows(BizException.class, () ->
            service.validateSingleManualGrants(List.of(), List.of(permission), Set.of()));

        assertEquals(20041, exception.getErrorCode());
    }

    @Test
    void shouldReturnInvalidResultInsteadOfThrowingForMissingOperationCode() {
        GrantCheckKey invalid = new GrantCheckKey("MENU", "sys:user", "default", null, false);

        Map<String, PermissionGrantDomainService.GrantCheckResult> results =
            service.checkCanGrant(1L, 10L, Set.of(invalid), null);

        assertEquals("INVALID_PERMISSION_KEY", results.values().iterator().next().reason());
        verify(subjectDomainService, never()).resolveEffectiveRoles(anyLong(), anyLong());
    }

    private RoleResourcePermission permission(Long id, String source, Long conditionId, Long bits) {
        RoleResourcePermission permission = new RoleResourcePermission();
        permission.setId(id);
        permission.setTenantId(1L);
        permission.setAbstractRoleId(20L);
        permission.setResourceEntityId(100L);
        permission.setResourceType(1);
        permission.setGrantedBits(bits);
        permission.setDependOn(null);
        permission.setScopeAll(false);
        permission.setConditionId(conditionId);
        permission.setGrantSource(source);
        return permission;
    }

    private OperationPermission operation(Long id, Integer resourceType, String code, Long binaryBit, Long inheritMask) {
        OperationPermission operationPermission = new OperationPermission();
        operationPermission.setId(id);
        operationPermission.setResourceType(resourceType);
        operationPermission.setCode(code);
        operationPermission.setBinaryBit(binaryBit);
        operationPermission.setInheritMask(inheritMask);
        operationPermission.setDeleteFlag(0L);
        return operationPermission;
    }
}
