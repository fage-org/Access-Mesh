package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewResult;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.PermViewAssembler;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限视图应用服务测试类
 * <p>
 * 原权限排查视图用例族（effective-permissions/role-permissions/explain/resource-tree）
 * 已随七端点删除（T-PERM-059，2026-09-10）；现仅覆盖登录权限串链路
 * （effective-permission-codes + 菜单派生资源访问事实）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionViewAppServiceImplTest {

    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private SubjectDomainService subjectDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;
    @Mock private PermViewAssembler permViewAssembler;

    private PermissionViewAppServiceImpl service;
    private MockedStatic<OperatorContext> operatorContextMock;

    @BeforeEach
    void setUp() {
        operatorContextMock = mockStatic(OperatorContext.class);
        operatorContextMock.when(OperatorContext::getOperatorId).thenReturn(1L);

        service = new PermissionViewAppServiceImpl(
            resourceEntityMapper, subjectDomainService, typeResolutionService, engine, permViewAssembler);
    }

    @AfterEach
    void tearDown() {
        operatorContextMock.close();
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
        // 判定面继承（读过滤面）锁：授权实例集必须经子孙扩展 CTE——mock 默认空列表会让
        // 无 verify 的实现恒绿（grok 外评指出），verify 钉住调用经被测路径
        verify(resourceEntityMapper).selectDescendantIdsBatch(1L, Set.of(200L));
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
