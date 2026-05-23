package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionExplainReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserResourceTreeReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionExplainResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionTreeResp;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.PermViewAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
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
        when(engine.hasPermission(anyLong(), anyLong(), eq(ResourceTypeCode.ROLE), eq(null), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(typeResolutionService.resolveRoleId(1L, "ADMIN", "r-nonexistent", "default")).thenReturn(null);

        var resp = service.getRolePermissions(1L, "default", "ADMIN", "r-nonexistent", false);

        assertEquals(null, resp);
    }

    @Test
    void shouldDenyExplainWhenUserNotFound() {
        when(engine.hasPermission(anyLong(), anyLong(), eq(ResourceTypeCode.SYSTEM_CONFIG), eq((Long) null), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(typeResolutionService.resolveUserId(1L, "USER", "u-999")).thenReturn(null);

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.USER, "USER", "u-999", null, null, null,
            "API", "api-1", null, "VIEW", null, null, null
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertNotNull(resp);
        assertFalse(resp.allowed());
        assertEquals("USER_NOT_FOUND", resp.reason());
    }

    @Test
    void shouldReturnEmptyTreeWhenUserNotFound() {
        when(engine.hasPermission(anyLong(), anyLong(), eq(ResourceTypeCode.USER), eq(999L), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(subjectDomainService.resolveEffectiveRoles(1L, 999L)).thenReturn(Set.of());

        UserResourceTreeReq req = new UserResourceTreeReq("USER", "u-999", null, null, null, null);
        List<ResourcePermissionTreeResp> tree = service.getUserResourceTree(1L, 999L, req);

        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }
}
