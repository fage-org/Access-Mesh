package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.util.SnapshotAssembler;
import cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限查询应用服务测试类
 */
@ExtendWith(MockitoExtension.class)
class PermissionQueryAppServiceImplTest {

    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private SubjectDomainService subjectDomainService;
    @Mock private PermissionConflictDomainService permissionConflictDomainService;
    @Mock private PermissionConditionDomainService permissionConditionDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private CacheService cacheService;
    @Mock private PermissionVersionDomainService permissionVersionDomainService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private PermQueryEngine engine;
    @Mock private SnapshotAssembler snapshotAssembler;

    private PermissionQueryAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionQueryAppServiceImpl(
            resourceEntityMapper, operationPermissionMapper,
            subjectDomainService, permissionConflictDomainService,
            permissionConditionDomainService, typeResolutionService, cacheService,
            permissionVersionDomainService, domainClassifyService,
            engine, snapshotAssembler
        );
    }

    // ===== queryScopes tests =====

    @Test
    void shouldClassifyScopeGroupAsInstanceWhenSpecificEntryMatches() {
        QueryScopesReq req = new QueryScopesReq(
            "USER", "u-1", "MENU", "sys:user", "default",
            List.of("VIEW"), List.of("DEPT"), List.of("VIEW"), "default", null, Map.of()
        );

        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("DEPT")))
            .thenReturn(Map.of("DEPT", 2));
        when(typeResolutionService.batchResolveOperationIds(1L, "DEPT", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 601L));
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of());
        when(permissionVersionDomainService.buildPermissionVersionKey(10L, 1L, Set.of())).thenReturn("v1");

        RolePermEntry parentEntry = new RolePermEntry(
            401L, 200L, 100L, "sys:user", 1, 1L, "VIEW", 1L, "MANUAL", false, null, false, null, null);

        RolePermEntry scopeEntry = new RolePermEntry(
            501L, 200L, 300L, "dept-a", 2, 8L, "MANAGE", 9L, "MANUAL", true, null, false, null, null);

        ResourceEntity scopeResource = new ResourceEntity();
        scopeResource.setId(300L); scopeResource.setCode("dept-a"); scopeResource.setCodeType("default");
        scopeResource.setName("部门A"); scopeResource.setResourceType(2); scopeResource.setDeleteFlag(0L);

        OperationPermission viewOp = new OperationPermission();
        viewOp.setId(601L); viewOp.setResourceType(2);
        viewOp.setCode("VIEW"); viewOp.setBinaryBit(1L); viewOp.setInheritMask(0L);

        OperationPermission manageOp = new OperationPermission();
        manageOp.setId(602L); manageOp.setResourceType(2);
        manageOp.setCode("MANAGE"); manageOp.setBinaryBit(8L); manageOp.setInheritMask(1L);

        PermResult parentResult = PermResult.builder(true, null)
            .instanceEntries(List.of(parentEntry)).build();
        PermResult scopeResult = PermResult.builder(true, null)
            .instanceEntries(List.of(scopeEntry))
            .resourceMap(Map.of(300L, scopeResource))
            .operationMap(Map.of(601L, viewOp, 602L, manageOp)).build();

        when(engine.query(any(PermQuery.class))).thenReturn(parentResult, scopeResult);
        when(permissionConditionDomainService.evaluate(any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(permissionConflictDomainService.filterPermMutex(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        QueryScopesResp resp = service.queryScopes(1L, req);

        // 分类模型：(DEPT, VIEW) 应为 INSTANCE，items 含 dept-a
        assertNull(resp.reason());
        assertEquals(1, resp.scopeGroups().size());
        QueryScopesResp.ScopeGroup group = resp.scopeGroups().get(0);
        assertEquals("DEPT", group.resourceTypeCode());
        assertEquals("VIEW", group.operationCode());
        assertEquals(ScopeMode.INSTANCE, group.scopeMode());
        assertEquals(1, group.items().size());
        assertEquals("dept-a", group.items().get(0).resourceCode());
    }

    // ===== interfaceSnapshot tests =====

    @Nested
    @MockitoSettings(strictness = Strictness.LENIENT)
    class InterfaceSnapshotTests {

        private Map<String, InterfaceSnapshot> snapshotCache;

        @BeforeEach
        void setUpSnapshot() {
            snapshotCache = new HashMap<>();
            lenient().when(cacheService.get(eq(PermCacheCatalog.INTERFACE_SNAPSHOT), eq(1L), any()))
                .thenAnswer(invocation -> snapshotCache.get(invocation.getArgument(2, String.class)));
            lenient().doAnswer(invocation -> {
                snapshotCache.put(
                    invocation.getArgument(2, String.class),
                    invocation.getArgument(3, InterfaceSnapshot.class)
                );
                return null;
            }).when(cacheService).put(eq(PermCacheCatalog.INTERFACE_SNAPSHOT), eq(1L), any(), any());
        }

        private PermResult buildPermResult() {
            return PermResult.builder(true, null)
                .instanceEntries(List.of())
                .build();
        }

        @Test
        void shouldReturnNotModifiedWhenPermissionTokenMatchesCurrentState() {
            when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
            when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(200L));
            when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));
            when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(2);
            when(engine.query(any(PermQuery.class))).thenReturn(buildPermResult());
            when(snapshotAssembler.buildSnapshot(eq(1L), any(PermResult.class), eq("admin-service"), eq(2)))
                .thenReturn(List.of(
                    new InterfaceSnapshotResp.ApiPermissionEntry("admin-service", "POST", "/api/user/list", false, null, false)
                ));

            InterfaceSnapshotResp first = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
                "USER", "u-1", "admin-service", null));

            InterfaceSnapshotResp second = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
                "USER", "u-1", "admin-service", first.permissionVersion()));

            assertFalse(first.notModified());
            assertEquals(1, first.allowedApis().size());
            assertTrue(second.notModified());
            assertEquals(first.permissionVersion(), second.permissionVersion());
            assertTrue(second.allowedApis().isEmpty());
            verify(snapshotAssembler, times(1)).buildSnapshot(eq(1L), any(PermResult.class), eq("admin-service"), eq(2));
        }

        @Test
        void shouldRebuildSnapshotWhenPermissionTokenChanges() {
            // T-PERM-003：令牌改为 roleIds 指纹，仅在角色集合变化时变化（不再反映权限内容变更）。
            // 本用例通过切换角色集合触发令牌变化，验证令牌变化后快照重建。
            when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
            when(subjectDomainService.resolveEffectiveRoles(1L, 10L))
                .thenReturn(Set.of(200L), Set.of(201L));
            when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));
            when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(201L))).thenReturn(Set.of(201L));
            when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(2);
            when(engine.query(any(PermQuery.class))).thenReturn(buildPermResult());
            when(snapshotAssembler.buildSnapshot(eq(1L), any(PermResult.class), eq("admin-service"), eq(2)))
                .thenReturn(List.of(
                    new InterfaceSnapshotResp.ApiPermissionEntry("admin-service", "POST", "/api/user/list", false, null, false)
                ))
                .thenReturn(List.of(
                    new InterfaceSnapshotResp.ApiPermissionEntry("admin-service", "POST", "/api/user/export", false, null, false)
                ));

            InterfaceSnapshotResp first = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
                "USER", "u-1", "admin-service", null));
            InterfaceSnapshotResp second = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
                "USER", "u-1", "admin-service", first.permissionVersion()));

            assertFalse(first.notModified());
            assertFalse(second.notModified());
            assertNotEquals(first.permissionVersion(), second.permissionVersion());
            assertEquals("/api/user/list", first.allowedApis().get(0).pathPattern());
            assertEquals("/api/user/export", second.allowedApis().get(0).pathPattern());
        }

        @Test
        void shouldIsolateSnapshotsByPermissionTokenForDifferentUsers() {
            when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
            when(typeResolutionService.resolveUserId(1L, "USER", "u-2")).thenReturn(11L);
            when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(200L));
            when(subjectDomainService.resolveEffectiveRoles(1L, 11L)).thenReturn(Set.of(201L));
            when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));
            when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(201L))).thenReturn(Set.of(201L));
            when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(2);
            when(engine.query(any(PermQuery.class))).thenReturn(buildPermResult());
            when(snapshotAssembler.buildSnapshot(eq(1L), any(PermResult.class), eq("admin-service"), eq(2)))
                .thenReturn(List.of(
                    new InterfaceSnapshotResp.ApiPermissionEntry("admin-service", "POST", "/api/user/list", false, null, false)
                ))
                .thenReturn(List.of(
                    new InterfaceSnapshotResp.ApiPermissionEntry("admin-service", "POST", "/api/user/export", false, null, false)
                ));

            InterfaceSnapshotResp first = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
                "USER", "u-1", "admin-service", null));
            InterfaceSnapshotResp second = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
                "USER", "u-2", "admin-service", null));

            assertNotEquals(first.permissionVersion(), second.permissionVersion());
            assertEquals("/api/user/list", first.allowedApis().get(0).pathPattern());
            assertEquals("/api/user/export", second.allowedApis().get(0).pathPattern());
        }
    }

    // ===== queryResources tests =====

    @Nested
    @MockitoSettings(strictness = Strictness.LENIENT)
    class QueryResourcesTests {

        @Test
        void shouldReturnScopeAllEntryWhenUserHasScopeAllPermission() {
            lenient().when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
            lenient().when(permissionVersionDomainService.buildPermissionVersionKey(10L, 1L, Set.of())).thenReturn("v1");
            lenient().when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
            lenient().when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1)))
                .thenReturn(Map.of(1, "REPORT"));

            RolePermEntry scopeAllEntry = new RolePermEntry(
                401L, 20L, null, null, 1, 1L, "VIEW", 1L,
                "MANUAL", false, null, false, null, true);

            OperationPermission viewOp = new OperationPermission();
            viewOp.setId(101L); viewOp.setResourceType(1);
            viewOp.setCode("VIEW"); viewOp.setBinaryBit(1L);

            PermResult r = PermResult.builder(true, null)
                .instanceEntries(List.of(scopeAllEntry))
                .operationMap(Map.of(101L, viewOp)).build();

            lenient().when(engine.query(any(PermQuery.class))).thenReturn(r);

            var req = new QueryResourcesReq(
                "USER", "u-1", List.of("REPORT"), List.of("VIEW"),
                null, null, null, null, null, null);
            var resp = service.queryResources(1L, req);

            assertEquals(1, resp.items().size());
            assertTrue(resp.items().get(0).scopeAll());
            assertEquals("REPORT", resp.items().get(0).resourceTypeCode());
        }
    }
}
