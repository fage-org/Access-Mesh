package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantDomainService.GrantCheckKey;
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
    private OperationPermissionMapper operationPermissionMapper;
    @Mock
    private PermQueryEngine permQueryEngine;

    private PermissionGrantDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionGrantDomainServiceImpl(
            typeResolutionService,
            permQueryEngine,
            operationPermissionMapper
        );
    }

    @Test
    void canGrantPermissionShouldAllowInheritedGrantCoverage() {
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));

        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        OperationPermission manageOp = operation(102L, 1, "MANAGE", 8L, 1L);

        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", PermConstants.CodeType.DEFAULT, null), 100L));

        // T-PERM-057 收编：授权事实来自引擎 LIST 管线（MANAGE 授予行 canGrant=true，
        // 经 inheritMask 覆盖 VIEW 目标操作）
        RolePermEntry grantedEntry = new RolePermEntry(
            500L, 20L, 100L, "sys:user", 1, 8L, "MANAGE", 9L, "MANUAL", true, null, false, null, false);
        when(permQueryEngine.query(any(PermQuery.class))).thenReturn(PermResult.builder(true, null)
            .instanceEntries(List.of(grantedEntry))
            .operationMap(Map.of(viewOp.getId(), viewOp, manageOp.getId(), manageOp))
            .build());

        boolean allowed = service.canGrantPermission(1L, 10L, "MENU", "sys:user", PermConstants.CodeType.DEFAULT, "VIEW", false, null);

        assertTrue(allowed);
    }

    @Test
    void canGrantPermissionShouldDenyWhenCodeTypeDiffers() {
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));

        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        // 请求 codeType=ID 解析到不同实体（200），与授予行实体（100）不匹配 → 拒绝
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", "ID", null), 200L));

        RolePermEntry grantedEntry = new RolePermEntry(
            500L, 20L, 100L, "sys:user", 1, 1L, "VIEW", 1L, "MANUAL", true, null, false, null, false);
        when(permQueryEngine.query(any(PermQuery.class))).thenReturn(PermResult.builder(true, null)
            .instanceEntries(List.of(grantedEntry))
            .operationMap(Map.of(viewOp.getId(), viewOp))
            .build());

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
        verify(permQueryEngine, never()).query(any(PermQuery.class));
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
