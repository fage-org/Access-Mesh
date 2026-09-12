package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.GrantOriginDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantPlanDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-PERM-062：类型授权根领域服务——指针解析（fail-closed）/角色解析（缺省引导角色 + 20001/20003）、
 * 指针注入（客户端自带键拒绝）、种子行构造（AUTHORITY_ROOT 形状）、所有者变更先清后种迁移。
 */
@ExtendWith(MockitoExtension.class)
class GrantOriginDomainServiceImplTest {

    private static final String POINTER_JSON =
        "{\"grantOriginRole\":{\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"bootstrap-admin\"}}";

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private RoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private PermissionGrantPlanDomainService permissionGrantPlanDomainService;

    private GrantOriginDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GrantOriginDomainServiceImpl(new ObjectMapper(), typeResolutionService,
            abstractRoleMapper, roleResourcePermissionMapper, operationPermissionMapper,
            permissionGrantPlanDomainService);
    }

    // ===== 指针解析 =====

    @Test
    void shouldParseValidPointer() {
        GrantOriginDomainService.GrantOriginRole pointer = service.parseGrantOriginPointer(POINTER_JSON);
        assertEquals("BASIC_ROLE", pointer.roleTypeCode());
        assertEquals("bootstrap-admin", pointer.roleExternalId());
    }

    @Test
    void shouldReturnNullWhenPointerAbsent() {
        assertNull(service.parseGrantOriginPointer(null));
        assertNull(service.parseGrantOriginPointer("  "));
        assertNull(service.parseGrantOriginPointer("{\"managedMode\":\"SYNC\"}"));
        // 显式 null 与缺键同歧义，fail-closed 拒绝（对齐 managedMode/syncSourceService 先例）
        BizException ex = assertThrows(BizException.class, () ->
            service.parseGrantOriginPointer("{\"grantOriginRole\":null}"));
        assertEquals(PermissionErrorCode.INVALID_PARAM.getCode(), ex.getErrorCode());
    }

    @Test
    void shouldRejectMalformedPointer() {
        for (String bad : List.of(
            "{\"grantOriginRole\":\"BASIC_ROLE\"}",
            "{\"grantOriginRole\":{}}",
            "{\"grantOriginRole\":{\"roleTypeCode\":\"BASIC_ROLE\"}}",
            "{\"grantOriginRole\":{\"roleTypeCode\":\" \",\"roleExternalId\":\"x\"}}",
            "{bad json")) {
            BizException ex = assertThrows(BizException.class, () ->
                service.parseGrantOriginPointer(bad));
            assertEquals(PermissionErrorCode.INVALID_PARAM.getCode(), ex.getErrorCode());
        }
    }

    // ===== 角色解析 =====

    @Test
    void shouldResolveOwnerWithPointer() {
        when(typeResolutionService.resolveRoleId(1L, "BASIC_ROLE", "bootstrap-admin", null)).thenReturn(55L);
        when(abstractRoleMapper.selectValidById(55L, 1L)).thenReturn(enabledRole(55L));
        assertEquals(55L, service.resolveOwnerRoleId(1L, POINTER_JSON));
    }

    @Test
    void shouldFallbackToBootstrapAdminWhenPointerMissing() {
        // 缺省引导角色（permission 域不反向依赖 application 包，行为由切片 IT 锁定与 bootstrap-admin 同值）
        when(typeResolutionService.resolveRoleId(1L,
            GrantOriginDomainService.DEFAULT_OWNER_ROLE_TYPE_CODE,
            GrantOriginDomainService.DEFAULT_OWNER_ROLE_EXTERNAL_ID, null)).thenReturn(55L);
        when(abstractRoleMapper.selectValidById(55L, 1L)).thenReturn(enabledRole(55L));
        assertEquals(55L, service.resolveOwnerRoleId(1L, null));
    }

    @Test
    void shouldRejectMissingAndDisabledOwner() {
        when(typeResolutionService.resolveRoleId(anyLong(), any(), any(), isNull())).thenReturn(null);
        BizException missing = assertThrows(BizException.class, () -> service.resolveOwnerRoleId(1L, POINTER_JSON));
        assertEquals(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), missing.getErrorCode());

        when(typeResolutionService.resolveRoleId(anyLong(), any(), any(), isNull())).thenReturn(55L);
        AbstractRole disabled = enabledRole(55L);
        disabled.setStatus(0);
        when(abstractRoleMapper.selectValidById(55L, 1L)).thenReturn(disabled);
        BizException disabledEx = assertThrows(BizException.class, () -> service.resolveOwnerRoleId(1L, POINTER_JSON));
        assertEquals(PermissionErrorCode.ROLE_DISABLED.getCode(), disabledEx.getErrorCode());
    }

    // ===== 指针注入 =====

    @Test
    void shouldMergePointerIntoClientExtra() {
        String merged = service.mergeGrantOriginPointer(
            "{\"managedMode\":\"SYNC\"}", "BASIC_ROLE", "order-admin");
        assertTrue(merged.contains("\"managedMode\":\"SYNC\""));
        assertTrue(merged.contains("\"grantOriginRole\":{\"roleTypeCode\":\"BASIC_ROLE\","
            + "\"roleExternalId\":\"order-admin\"}"));
    }

    @Test
    void shouldCreateExtraWhenClientExtraBlank() {
        String merged = service.mergeGrantOriginPointer(null, "BASIC_ROLE", "bootstrap-admin");
        assertTrue(merged.contains("grantOriginRole"));
    }

    @Test
    void shouldDetectPointerKeyPresenceLeniently() {
        // 键存在性探测：命中任意形态该键（含显式 null/结构非法）返回 true；坏 JSON false（探测非校验）
        assertTrue(service.hasGrantOriginPointerKey(POINTER_JSON));
        assertTrue(service.hasGrantOriginPointerKey("{\"grantOriginRole\":null}"));
        assertTrue(service.hasGrantOriginPointerKey("{\"grantOriginRole\":\"x\"}"));
        assertTrue(service.hasGrantOriginPointerKey("{\"managedMode\":\"SYNC\",\"grantOriginRole\":{}}"));
        org.junit.jupiter.api.Assertions.assertFalse(service.hasGrantOriginPointerKey(null));
        org.junit.jupiter.api.Assertions.assertFalse(service.hasGrantOriginPointerKey("  "));
        org.junit.jupiter.api.Assertions.assertFalse(service.hasGrantOriginPointerKey("{\"managedMode\":\"SYNC\"}"));
        org.junit.jupiter.api.Assertions.assertFalse(service.hasGrantOriginPointerKey("{bad json"));
    }

    @Test
    void shouldRejectClientSuppliedPointerKey() {
        // 指针是服务端管理键：客户端自带一律拒绝（合法输入通道是请求字段）
        BizException ex = assertThrows(BizException.class, () ->
            service.mergeGrantOriginPointer(POINTER_JSON, "BASIC_ROLE", "order-admin"));
        assertEquals(PermissionErrorCode.INVALID_PARAM.getCode(), ex.getErrorCode());
    }

    // ===== 种子行构造 =====

    @Test
    void shouldSeedAuthorityRootRowsWithWeldedShape() {
        service.seedAuthorityRootGrants(1L, 55L, 12, List.of(1L, 2L, 4L, 8L), 100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoleResourcePermission>> captor = ArgumentCaptor.forClass(List.class);
        verify(permissionGrantPlanDomainService).seedGrants(eq(1L), eq(55L), captor.capture());
        List<RoleResourcePermission> grants = captor.getValue();
        assertEquals(4, grants.size());
        for (RoleResourcePermission grant : grants) {
            assertEquals("AUTHORITY_ROOT", grant.getGrantSource());
            assertEquals(Boolean.TRUE, grant.getScopeAll());
            assertEquals(Boolean.TRUE, grant.getCanGrant());
            assertNull(grant.getConditionId());
            assertNull(grant.getResourceEntityId());
            assertNull(grant.getDependOn());
            assertEquals(12, grant.getResourceType());
            assertEquals(0L, grant.getDeleteFlag());
        }
        assertEquals(Set.of(1L, 2L, 4L, 8L),
            grants.stream().map(RoleResourcePermission::getGrantedBits).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldSkipSeedingWhenNoBits() {
        service.seedAuthorityRootGrants(1L, 55L, 12, List.of(), 100L);
        verify(permissionGrantPlanDomainService, never()).seedGrants(anyLong(), anyLong(), any());
    }

    // ===== 所有者变更迁移（先清后种） =====

    @Test
    void shouldRematerializeByClearingAndReseedingAllOperationBits() {
        RoleResourcePermission staleRow = new RoleResourcePermission();
        staleRow.setId(900L);
        staleRow.setAbstractRoleId(66L);
        when(roleResourcePermissionMapper.selectValidAuthorityRootsByTypes(1L, Set.of(12)))
            .thenReturn(List.of(staleRow));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(12)))
            .thenReturn(List.of(operation(16L), operation(32L)));

        Set<Long> affected = service.rematerializeAuthorityRootGrants(1L, 12, 77L, 100L);

        // 旧 owner 的残留行同事务清理；返回受影响角色集合（markRoles 面）
        assertEquals(Set.of(66L), affected);
        verify(roleResourcePermissionMapper).softDeleteBatch(eq(1L), eq(List.of(900L)), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoleResourcePermission>> captor = ArgumentCaptor.forClass(List.class);
        verify(permissionGrantPlanDomainService).seedGrants(eq(1L), eq(77L), captor.capture());
        assertEquals(Set.of(16L, 32L),
            captor.getValue().stream().map(RoleResourcePermission::getGrantedBits)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldSeedEvenWhenNoStaleRowsExist() {
        when(roleResourcePermissionMapper.selectValidAuthorityRootsByTypes(1L, Set.of(12)))
            .thenReturn(List.of());
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(12)))
            .thenReturn(List.of(operation(2L)));

        Set<Long> affected = service.rematerializeAuthorityRootGrants(1L, 12, 77L, 100L);

        assertTrue(affected.isEmpty());
        verify(roleResourcePermissionMapper, never()).softDeleteBatch(anyLong(), any(), any());
        verify(permissionGrantPlanDomainService).seedGrants(eq(1L), eq(77L), any());
    }

    private AbstractRole enabledRole(Long id) {
        AbstractRole role = new AbstractRole();
        role.setId(id);
        role.setStatus(1);
        return role;
    }

    private OperationPermission operation(Long bit) {
        OperationPermission op = new OperationPermission();
        op.setResourceType(12);
        op.setBinaryBit(bit);
        return op;
    }
}
