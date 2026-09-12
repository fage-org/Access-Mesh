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
    @Mock
    private cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock
    private cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper typeDefinitionMapper;

    private PermissionGrantDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionGrantDomainServiceImpl(
            typeResolutionService,
            permQueryEngine,
            operationPermissionMapper,
            roleResourcePermissionMapper,
            typeDefinitionMapper
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

    // ========== T-PERM-062：20040 reason 细分（TYPE_GRANT_ORIGIN_MISSING，仅自定义类型） ==========

    @Test
    void shouldRefineToGrantOriginMissingForCustomTypeWithZeroGrantableRows() {
        // 自定义类型租户内零条可转授覆盖行（种子缺失/被清除）→ NO_PERMISSION 改判
        // TYPE_GRANT_ORIGIN_MISSING（旧实现无细分，本用例必红）
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("ORDER")))
            .thenReturn(Map.of("ORDER", 12));
        OperationPermission orderView = operation(201L, 12, "VIEW", 2L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(12), Set.of("VIEW")))
            .thenReturn(List.of(orderView));
        // 操作者持有另一类型（typeValue=5）的条目：有角色有授权行，但对目标类型零覆盖 → 主路径 NO_PERMISSION
        OperationPermission otherView = operation(202L, 5, "VIEW", 1L, 0L);
        RolePermEntry otherEntry = new RolePermEntry(
            500L, 20L, null, null, 5, 1L, "VIEW", 1L, "MANUAL", false, null, true, null, false);
        when(permQueryEngine.query(any(PermQuery.class))).thenReturn(PermResult.builder(true, null)
            .instanceEntries(List.of(otherEntry))
            .operationMap(Map.of(otherView.getId(), otherView))
            .build());
        when(typeDefinitionMapper.selectValidByTenant(1L)).thenReturn(List.of(customTypeRow(12)));
        when(roleResourcePermissionMapper.selectGrantableCoveringCandidates(eq(1L), eq(Set.of(12)), any()))
            .thenReturn(List.of());
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(12)))
            .thenReturn(List.of(orderView));

        Map<String, PermissionGrantDomainService.GrantCheckResult> results = service.checkCanGrant(
            1L, 10L, Set.of(new GrantCheckKey("ORDER", null, null, "VIEW", true)), null);

        assertEquals("TYPE_GRANT_ORIGIN_MISSING",
            results.values().iterator().next().reason());
    }

    @Test
    void shouldKeepNoPermissionWhenGrantableOriginExistsTenantWide() {
        // 候选行存在（任意角色持有覆盖可转授行）→ 授权根在，操作者持有面不够维持 NO_PERMISSION
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("ORDER")))
            .thenReturn(Map.of("ORDER", 12));
        OperationPermission orderView = operation(201L, 12, "VIEW", 2L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(12), Set.of("VIEW")))
            .thenReturn(List.of(orderView));
        OperationPermission otherView = operation(202L, 5, "VIEW", 1L, 0L);
        RolePermEntry otherEntry = new RolePermEntry(
            500L, 20L, null, null, 5, 1L, "VIEW", 1L, "MANUAL", false, null, true, null, false);
        when(permQueryEngine.query(any(PermQuery.class))).thenReturn(PermResult.builder(true, null)
            .instanceEntries(List.of(otherEntry))
            .operationMap(Map.of(otherView.getId(), otherView))
            .build());
        when(typeDefinitionMapper.selectValidByTenant(1L)).thenReturn(List.of(customTypeRow(12)));
        RoleResourcePermission originRow = permission(900L, "AUTHORITY_ROOT", null, 2L);
        originRow.setResourceType(12);
        originRow.setScopeAll(true);
        originRow.setCanGrant(true);
        when(roleResourcePermissionMapper.selectGrantableCoveringCandidates(eq(1L), eq(Set.of(12)), any()))
            .thenReturn(List.of(originRow));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(12)))
            .thenReturn(List.of(orderView));

        Map<String, PermissionGrantDomainService.GrantCheckResult> results = service.checkCanGrant(
            1L, 10L, Set.of(new GrantCheckKey("ORDER", null, null, "VIEW", true)), null);

        assertEquals("NO_PERMISSION", results.values().iterator().next().reason());
    }

    @Test
    void shouldNotRefineBuiltinTypeZeroGrantableRows() {
        // is_system 类型零可转授行是转授链收窄的设计状态（T-PERM-027），reason 维持原值；
        // 且不触发租户级候选行查询（用户定案 2026-09-12：细分仅自定义类型）
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("SERVICE")))
            .thenReturn(Map.of("SERVICE", 4));
        OperationPermission serviceView = operation(203L, 4, "VIEW", 2L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(4), Set.of("VIEW")))
            .thenReturn(List.of(serviceView));
        OperationPermission otherView = operation(202L, 5, "VIEW", 1L, 0L);
        RolePermEntry otherEntry = new RolePermEntry(
            500L, 20L, null, null, 5, 1L, "VIEW", 1L, "MANUAL", false, null, true, null, false);
        when(permQueryEngine.query(any(PermQuery.class))).thenReturn(PermResult.builder(true, null)
            .instanceEntries(List.of(otherEntry))
            .operationMap(Map.of(otherView.getId(), otherView))
            .build());
        cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition builtin = customTypeRow(4);
        builtin.setIsSystem(true);
        when(typeDefinitionMapper.selectValidByTenant(1L)).thenReturn(List.of(builtin));

        Map<String, PermissionGrantDomainService.GrantCheckResult> results = service.checkCanGrant(
            1L, 10L, Set.of(new GrantCheckKey("SERVICE", null, null, "VIEW", true)), null);

        assertEquals("NO_PERMISSION", results.values().iterator().next().reason());
        verify(roleResourcePermissionMapper, never()).selectGrantableCoveringCandidates(any(), any(), any());
    }

    private cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition customTypeRow(int typeValue) {
        cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition row =
            new cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition();
        row.setTenantId(1L);
        row.setTypeKey("resource_type");
        row.setTypeCode("ORDER");
        row.setTypeValue(typeValue);
        row.setIsSystem(false);
        return row;
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
