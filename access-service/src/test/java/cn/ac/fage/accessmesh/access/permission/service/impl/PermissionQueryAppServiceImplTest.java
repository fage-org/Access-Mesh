package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.SnapshotAssembler;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限查询应用服务测试类
 */
@ExtendWith(MockitoExtension.class)
class PermissionQueryAppServiceImplTest {

    @Mock private SubjectDomainService subjectDomainService;
    @Mock private PermissionConflictDomainService permissionConflictDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private CacheService cacheService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private PermQueryEngine engine;
    @Mock private SnapshotAssembler snapshotAssembler;

    private PermissionQueryAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionQueryAppServiceImpl(
            subjectDomainService, permissionConflictDomainService,
            typeResolutionService, cacheService,
            domainClassifyService,
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

        // T-PERM-057 收编：一次引擎调用（LIST 管线完成父判定 + dependOn 过滤 + 条件/互斥评估；
        // parentMatched/rawEntries/instanceEntries 回传供四态组装）
        PermResult scopeResult = PermResult.builder(true, null)
            .instanceEntries(List.of(scopeEntry))
            .rawEntries(List.of(scopeEntry))
            .parentMatchedOperationCodes(Set.of("VIEW"))
            .parentMatchedPermissionIds(Set.of(401L))
            .resourceMap(Map.of(300L, scopeResource))
            .operationMap(Map.of(601L, viewOp, 602L, manageOp)).build();
        when(engine.query(any(PermQuery.class))).thenReturn(scopeResult);

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

    @Test
    void shouldReturnEmptyWhenInstanceItemsAllFilteredOut() {
        // P2 修复：INSTANCE 收集后 items 全空（资源缺失/已删）→ EMPTY，符合 T-PERM-009 契约
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

        RolePermEntry parentEntry = new RolePermEntry(
            401L, 200L, 100L, "sys:user", 1, 1L, "VIEW", 1L, "MANUAL", false, null, false, null, null);

        // scope 条目指向 resourceEntityId=300，但 resourceMap 不含 300（资源缺失）→ items 收集为空
        RolePermEntry scopeEntry = new RolePermEntry(
            501L, 200L, 300L, "dept-a", 2, 1L, "VIEW", 1L, "MANUAL", false, null, false, null, null);

        OperationPermission viewOp = new OperationPermission();
        viewOp.setId(601L); viewOp.setResourceType(2);
        viewOp.setCode("VIEW"); viewOp.setBinaryBit(1L); viewOp.setInheritMask(0L);

        // T-PERM-057 收编：一次引擎调用；resourceMap 为空 → scopeEntry 的资源缺失
        PermResult scopeResult = PermResult.builder(true, null)
            .instanceEntries(List.of(scopeEntry))
            .rawEntries(List.of(scopeEntry))
            .parentMatchedOperationCodes(Set.of("VIEW"))
            .parentMatchedPermissionIds(Set.of(401L))
            .resourceMap(Map.of())
            .operationMap(Map.of(601L, viewOp)).build();
        when(engine.query(any(PermQuery.class))).thenReturn(scopeResult);

        QueryScopesResp resp = service.queryScopes(1L, req);

        assertEquals(1, resp.scopeGroups().size());
        QueryScopesResp.ScopeGroup group = resp.scopeGroups().get(0);
        assertEquals(ScopeMode.EMPTY, group.scopeMode());
        assertTrue(group.items().isEmpty());
    }

    /** grok 外评 P1 修复锁：条件评估清空（allowed=false, CONDITION_NOT_MET_OR_CONFLICT）
     * 不得整表拒绝——rawEntries 有覆盖即 EMPTY、matchedParentOperations 照常回传
     * （旧实现一律压成 NO_PERMISSION + 全格 DENIED，业务方把 EMPTY 误当 403）。 */
    @Test
    void shouldClassifyEmptyNotDeniedWhenConditionClearsScopeEntries() {
        QueryScopesReq req = new QueryScopesReq(
            "USER", "u-1", "REPORT", "report:sales", "default",
            List.of("VIEW"), List.of("DATA"), List.of("READ"), "default", null, Map.of()
        );

        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "REPORT", "report:sales", "default", null)).thenReturn(100L);
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("DATA")))
            .thenReturn(Map.of("DATA", 2));
        when(typeResolutionService.batchResolveOperationIds(1L, "DATA", Set.of("READ")))
            .thenReturn(Map.of("READ", 601L));

        // DATA 授权行挂时间条件当前不满足：评估后清空（引擎 deny CONDITION_NOT_MET_OR_CONFLICT），
        // 但 rawEntries 含该行 + 操作定义已装载（装载源=rawEntries 超集）+ 父判定命中
        RolePermEntry dataEntry = new RolePermEntry(
            501L, 200L, 300L, "data-1", 2, 1L, "READ", 1L, "MANUAL", false, 301L, true, null, false);
        OperationPermission readOp = new OperationPermission();
        readOp.setId(601L); readOp.setResourceType(2);
        readOp.setCode("READ"); readOp.setBinaryBit(1L); readOp.setInheritMask(0L);
        PermResult result = PermResult.builder(false, "CONDITION_NOT_MET_OR_CONFLICT")
            .rawEntries(List.of(dataEntry))
            .parentMatchedOperationCodes(Set.of("VIEW"))
            .parentMatchedPermissionIds(Set.of(401L))
            .operationMap(Map.of(601L, readOp))
            .build();
        when(engine.query(any(PermQuery.class))).thenReturn(result);

        QueryScopesResp resp = service.queryScopes(1L, req);

        assertNull(resp.reason(), "评估清空不是整体拒绝");
        assertEquals(1, resp.matchedParentOperations().size(), "父操作命中照常回传");
        assertEquals(1, resp.scopeGroups().size());
        assertEquals(ScopeMode.EMPTY, resp.scopeGroups().get(0).scopeMode(),
            "有覆盖但评估清空 = EMPTY（勿压 DENIED）");
    }

    // ===== interfaceSnapshot tests (T-PERM-018：缓存下沉，移除令牌/notModified) =====

    @Nested
    @MockitoSettings(strictness = Strictness.LENIENT)
    class InterfaceSnapshotTests {

        private PermResult buildPermResult() {
            return PermResult.builder(true, null)
                .instanceEntries(List.of())
                .build();
        }

        @Test
        void shouldBuildSnapshotFromEngineEveryCallWithoutToken() {
            // T-PERM-018：access-service 每次实时构建全量快照，不再有 permissionVersion/notModified
            when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
            when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(200L));
            when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));
            when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(2);
            when(engine.query(any(PermQuery.class))).thenReturn(buildPermResult());
            when(snapshotAssembler.buildSnapshot(eq(1L), any(PermResult.class), eq("example-service"), eq(2)))
                .thenReturn(List.of(
                    new InterfaceSnapshotResp.ApiPermissionEntry("example-service", "POST", "/api/user/list", false, null, null, ScopeMode.INSTANCE)
                ));

            InterfaceSnapshotResp first = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
                "USER", "u-1", "example-service"));
            InterfaceSnapshotResp second = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
                "USER", "u-1", "example-service"));

            // 每次都返回全量 entries（无 notModified 短路）
            assertEquals(1, first.allowedApis().size());
            assertEquals(1, second.allowedApis().size());
            verify(snapshotAssembler, times(2)).buildSnapshot(eq(1L), any(PermResult.class), eq("example-service"), eq(2));
        }

        @Test
        void shouldReturnEmptySnapshotWhenNoEffectiveRoles() {
            when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
            when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of());
            when(permissionConflictDomainService.filterRoleMutex(1L, Set.of())).thenReturn(Set.of());

            InterfaceSnapshotResp resp = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
                "USER", "u-1", "example-service"));

            assertTrue(resp.allowedApis().isEmpty());
            // 无有效角色短路，不调引擎
            verify(engine, times(0)).query(any(PermQuery.class));
            verify(snapshotAssembler, times(0)).buildSnapshot(any(), any(), any(), any());
        }
    }

    // ===== queryResources tests =====

    @Nested
    @MockitoSettings(strictness = Strictness.LENIENT)
    class QueryResourcesTests {

        @Test
        void shouldReturnScopeAllEntryWhenUserHasScopeAllPermission() {
            lenient().when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
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
                null, null, null, null, null);
            var resp = service.queryResources(1L, req);

            assertEquals(1, resp.items().size());
            assertEquals(ScopeMode.ALL, resp.items().get(0).scopeMode());
            assertEquals("REPORT", resp.items().get(0).resourceTypeCode());
        }

        @Test
        void shouldExcludeDependentEntriesFromQueryResourcesItems() {
            // T-PERM-058：子权限行不进清单面——独立 INSTANCE 条目呈现会误导调用方
            // （子行实例 ≠ 独立可访问，其授权只在 query-scopes 主资源上下文内生效）
            lenient().when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
            lenient().when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1)))
                .thenReturn(Map.of(1, "REPORT"));

            // 主行实例 200（dependOn=null）+ 子行实例 300（dependOn=501）
            RolePermEntry mainEntry = new RolePermEntry(
                401L, 20L, 200L, "report:1", 1, 1L, "VIEW", 1L,
                "MANUAL", false, null, false, null, false);
            RolePermEntry dependentEntry = new RolePermEntry(
                402L, 20L, 300L, "city:gd", 1, 1L, "VIEW", 1L,
                "MANUAL", false, null, false, 501L, false);

            OperationPermission viewOp = new OperationPermission();
            viewOp.setId(101L); viewOp.setResourceType(1);
            viewOp.setCode("VIEW"); viewOp.setBinaryBit(1L);

            ResourceEntity mainRes = new ResourceEntity();
            mainRes.setId(200L); mainRes.setResourceType(1);
            mainRes.setCode("report:1"); mainRes.setCodeType("default");
            ResourceEntity childRes = new ResourceEntity();
            childRes.setId(300L); childRes.setResourceType(1);
            childRes.setCode("city:gd"); childRes.setCodeType("default");

            PermResult r = PermResult.builder(true, null)
                .instanceEntries(List.of(mainEntry, dependentEntry))
                .resourceMap(Map.of(200L, mainRes, 300L, childRes))
                .operationMap(Map.of(101L, viewOp)).build();

            lenient().when(engine.query(any(PermQuery.class))).thenReturn(r);

            var req = new QueryResourcesReq(
                "USER", "u-1", List.of("REPORT"), List.of("VIEW"),
                null, null, null, null, null);
            var resp = service.queryResources(1L, req);

            // 旧实现：两条 INSTANCE 条目（子行误报独立可访问）
            assertEquals(1, resp.items().size(), "子权限行不得进清单面（旧实现独立 INSTANCE 条目误报）");
            assertEquals("report:1", resp.items().get(0).resourceCode());
        }
    }
}
