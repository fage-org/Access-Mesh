package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewResult;
import cn.ac.fage.accessmesh.access.permission.dto.req.PermissionExplainReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserResourceTreeReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PermissionExplainResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourcePermissionTreeResp;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.PermViewAssembler;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限视图应用服务测试类
 */
@ExtendWith(MockitoExtension.class)
class PermissionViewAppServiceImplTest {

    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    @Mock private SubjectDomainService subjectDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private PermQueryEngine engine;
    @Mock private PermViewAssembler permViewAssembler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PermissionViewAppServiceImpl service;
    private MockedStatic<OperatorContext> operatorContextMock;

    @BeforeEach
    void setUp() {
        operatorContextMock = mockStatic(OperatorContext.class);
        operatorContextMock.when(OperatorContext::getOperatorId).thenReturn(1L);

        service = new PermissionViewAppServiceImpl(
            abstractRoleMapper, resourceEntityMapper, operationPermissionMapper,
            rolePermMapper, subjectDomainService,
            typeResolutionService, auditDomainService, objectMapper, engine, permViewAssembler
        );
    }

    @AfterEach
    void tearDown() {
        operatorContextMock.close();
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-999")).thenReturn(null);

        UserPermissionViewReq req = new UserPermissionViewReq(
            PermConstants.TargetType.USER, "USER", "u-999", null,
            null, null, null, null, null, null,
            false, false, false, null, 1, 20
        );
        PermissionEffectivePermissionsResp resp = service.getEffectivePermissions(1L, req);

        assertNotNull(resp);
        assertEquals(PermConstants.TargetType.USER, resp.targetType());
        assertTrue(resp.items().isEmpty());
    }

    @Test
    void shouldReturnNullWhenRoleNotFound() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), eq(ResourceTypeCode.ROLE), eq(null), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(typeResolutionService.resolveRoleId(1L, "ADMIN", "r-nonexistent", "default")).thenReturn(null);

        var resp = service.getRolePermissions(1L, "default", "ADMIN", "r-nonexistent", false);

        assertEquals(null, resp);
    }

    @Test
    void shouldDenyExplainWhenUserNotFound() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), eq(ResourceTypeCode.SYSTEM_CONFIG), eq((String) null), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(typeResolutionService.resolveUserId(1L, "USER", "u-999")).thenReturn(null);

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.USER, "USER", "u-999", null, null, null,
            "API", "api-1", "default", "VIEW", ScopeMode.INSTANCE, null, null, null
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertNotNull(resp);
        assertFalse(resp.allowed());
        assertEquals("USER_NOT_FOUND", resp.reason());
    }

    @Test
    void shouldExplainRoleScopeAllWithTypeLevelQuery() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), eq(ResourceTypeCode.SYSTEM_CONFIG), eq((String) null), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(typeResolutionService.resolveRoleId(1L, "ROLE", "role-1", "admin")).thenReturn(20L);
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.builder(true, null).build());

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.ROLE, null, null, "ROLE", "role-1", "admin",
            "MENU", null, null, "VIEW", ScopeMode.ALL, false, false, null
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertTrue(resp.allowed());
        assertEquals(ScopeMode.ALL, resp.permission().scopeMode());
        assertNull(resp.permission().resourceCode());
        ArgumentCaptor<PermQuery> captor = ArgumentCaptor.forClass(PermQuery.class);
        verify(engine).query(captor.capture());
        assertNull(captor.getValue().resourceCodes());
        assertTrue(captor.getValue().queryScopeAll());
        assertFalse(captor.getValue().queryInstance());
    }

    @Test
    void shouldExplainRoleInstanceWithoutScopeAllFallback() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), eq(ResourceTypeCode.SYSTEM_CONFIG), eq((String) null), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(typeResolutionService.resolveRoleId(1L, "ROLE", "role-1", "admin")).thenReturn(20L);
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.deny("NO_PERMISSION"));

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.ROLE, null, null, "ROLE", "role-1", "admin",
            "MENU", "sys:user", "default", "VIEW", ScopeMode.INSTANCE, false, false, null
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertFalse(resp.allowed());
        assertEquals("NO_PERMISSION", resp.reason());
        ArgumentCaptor<PermQuery> captor = ArgumentCaptor.forClass(PermQuery.class);
        verify(engine).query(captor.capture());
        assertFalse(captor.getValue().queryScopeAll());
        assertTrue(captor.getValue().queryInstance());
        assertEquals(Set.of("sys:user"), captor.getValue().resourceCodes());
        assertEquals("default", captor.getValue().codeType());
    }


    @Test
    void shouldReturnEmptyTreeWhenUserNotFound() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), eq(ResourceTypeCode.USER), eq("999"), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(subjectDomainService.resolveEffectiveRoles(1L, 999L)).thenReturn(Set.of());

        UserResourceTreeReq req = new UserResourceTreeReq("USER", "u-999", null, null, null, null);
        List<ResourcePermissionTreeResp> tree = service.getUserResourceTree(1L, 999L, req);

        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    @Test
    void getEffectivePermissionCodesShouldReturnInheritedEffectiveOperationCodes() {
        // 自查场景：operator 投影主体=1001，subject "1" 投影=1001；buildEffectiveView 无 USER:VIEW 门禁
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "1")).thenReturn(1L);
        when(subjectDomainService.resolveEffectiveRoles(1L, 1L)).thenReturn(Set.of(20L));

        RolePermEntry entry = new RolePermEntry(
            501L, 20L, 200L, null, 1, 4L,
            "UPDATE", 6L, "DIRECT", true, null, false, null, false
        );
        PermResult result = PermResult.builder(true, null)
            .instanceEntries(List.of(entry))
            .effectiveOperationEntries(List.of(
                new PermResult.EffectiveOperationEntry(
                    501L, 20L, 200L, 1, 4L, "UPDATE", 6L,
                    "UPDATE", 4L, "DIRECT", false),
                new PermResult.EffectiveOperationEntry(
                    501L, 20L, 200L, 1, 4L, "UPDATE", 6L,
                    "VIEW", 2L, "DIRECT", false)
            ))
            .build();
        when(engine.query(any(PermQuery.class))).thenReturn(result);
        when(permViewAssembler.assemble(eq(1L), eq(result), any()))
            .thenReturn(PermViewResult.builder()
                .entries(List.of(entry))
                .effectiveOperationEntries(result.effectiveOperationEntries())
                .resourceMap(Map.of())
                .operationMap(Map.of())
                .roleMap(Map.of())
                .build());
        when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1)))
            .thenReturn(Map.of(1, "USER"));

        UserEffectivePermissionCodesResp resp = service.getEffectivePermissionCodes(
            1L,
            new UserEffectivePermissionCodesReq("LOCAL_USER", "1", List.of("USER"))
        );

        assertTrue(resp.permissions().contains("USER:UPDATE"));
        assertTrue(resp.permissions().contains("USER:VIEW"));
    }

    @Test
    void getEffectiveResourceAccessShouldCollectScopeAllTypesAndInstanceIds() {
        // 自查：operator 投影主体=1001，subject "1" 投影=1001；buildEffectiveView 无 USER:VIEW 门禁
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "1")).thenReturn(1L);
        when(subjectDomainService.resolveEffectiveRoles(1L, 1L)).thenReturn(Set.of(20L));

        PermResult result = PermResult.builder(true, null)
            .effectiveOperationEntries(List.of(
                new PermResult.EffectiveOperationEntry(
                    501L, 20L, null, 2, 4L, "VIEW", 4L, "VIEW", 2L, "DIRECT", true),  // scopeAll → allScopeTypes=2
                new PermResult.EffectiveOperationEntry(
                    502L, 20L, 200L, 1, 2L, "VIEW", 2L, "VIEW", 1L, "DIRECT", false) // 实例 → resourceEntityIds=200
            ))
            .build();
        when(engine.query(any(PermQuery.class))).thenReturn(result);
        when(permViewAssembler.assemble(eq(1L), eq(result), any()))
            .thenReturn(PermViewResult.builder()
                .entries(List.of())
                .effectiveOperationEntries(result.effectiveOperationEntries())
                .resourceMap(Map.of())
                .operationMap(Map.of())
                .roleMap(Map.of())
                .build());

        PermissionViewAppService.EffectiveResourceAccess access = service.getEffectiveResourceAccess(
            1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "1", List.of("USER", "ORG")));

        assertTrue(access.allScopeTypes().contains(2));
        assertTrue(access.resourceEntityIds().contains(200L));
        assertFalse(access.allScopeTypes().contains(1));
        assertFalse(access.resourceEntityIds().contains(501L));
    }

    @Test
    void getEffectivePermissionCodesForManageShouldAllowSelfWithoutUserView() {
        // 自查：operator 投影主体=1001（sys=1 转换），subject "1" 投影=1001 → 豁免 USER:VIEW，不调用 engine.hasPermission
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "1")).thenReturn(1L);
        when(subjectDomainService.resolveEffectiveRoles(1L, 1L)).thenReturn(Set.of());

        UserEffectivePermissionCodesResp resp = service.getEffectivePermissionCodesForManage(
            1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "1", List.of("USER")));

        assertNotNull(resp);
        assertTrue(resp.permissions().isEmpty());
        verify(engine, never()).hasPermissionByCode(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void getEffectivePermissionCodesForManageShouldDenyOthersWithoutUserView() {
        // 查他人：operator 投影主体=1001，subject "2" 投影=1002，无 USER:VIEW → SecurityException（门禁用 abstract 主体，非 sys id）
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "2")).thenReturn(1002L);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.USER, "1002", OperationCodeConstants.VIEW))
            .thenReturn(false);

        assertThrows(SecurityException.class, () ->
            service.getEffectivePermissionCodesForManage(
                1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "2", List.of("USER"))));
    }

    @Test
    void getEffectivePermissionCodesForManageShouldAllowOthersWithUserView() {
        // 查他人：operator 投影主体=1001，subject "2" 投影=1002，有 USER:VIEW → 正常下发
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "2")).thenReturn(1002L);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.USER, "1002", OperationCodeConstants.VIEW))
            .thenReturn(true);
        when(subjectDomainService.resolveEffectiveRoles(1L, 1002L)).thenReturn(Set.of());

        UserEffectivePermissionCodesResp resp = service.getEffectivePermissionCodesForManage(
            1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "2", List.of("USER")));

        assertNotNull(resp);
        assertTrue(resp.permissions().isEmpty());
    }

}
