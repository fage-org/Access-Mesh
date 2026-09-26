package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.DefaultCacheService;
import cn.ac.fage.accessmesh.common.cache.spi.DistributedCacheStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** I02～I06 读取部件验收；execute 接线验收归 085/087。 */
class QueryReadSupportTest {
    private final TypeResolutionService types = mock(TypeResolutionService.class);
    private final OperationPermissionDomainService operations = mock(OperationPermissionDomainService.class);
    private final ResourceEntityDomainService resources = mock(ResourceEntityDomainService.class);
    private final AbstractRoleMapper roles = mock(AbstractRoleMapper.class);
    private final RoleResourcePermissionMapper grants = mock(RoleResourcePermissionMapper.class);
    private final CacheService cache = mock(CacheService.class);
    private final QueryReadSupport reads = new QueryReadSupport(types, operations, resources, roles, grants, cache,
        new RolePermEntryMapper());

    static RunState run(ListGrantRead source) {
        return new RunState(new QueryRequest(1L, new Roles(Set.of(10L)), CallerContext.of(null),
            new ReadOptions(source), List.of()), Clock.systemUTC());
    }

    static OperationPermission operation(long id, int type, String code, long bit) {
        OperationPermission row = new OperationPermission();
        row.setId(id); row.setTenantId(1L); row.setResourceType(type); row.setCode(code);
        row.setName(code); row.setBinaryBit(bit); row.setInheritMask(0L);
        return row;
    }

    static RoleResourcePermission grant(long id, long role) {
        RoleResourcePermission row = new RoleResourcePermission();
        row.setId(id); row.setTenantId(1L); row.setAbstractRoleId(role); row.setResourceType(1);
        row.setGrantedBits(2L); row.setScopeAll(true); row.setCanGrant(false); row.setGrantSource("MANUAL");
        return row;
    }

    @Test
    void should_readTwentyTypesTogetherAndRememberEmptyBuckets_whenFreshDefinitionsRequested() {
        Set<Integer> requested = IntStream.rangeClosed(1, 20).boxed().collect(Collectors.toSet());
        when(operations.selectByTenantAndResourceTypes(1L, requested)).thenReturn(List.of(operation(11, 1, "VIEW", 2)));
        RunState run = run(ListGrantRead.DATABASE);
        assertThat(reads.freshOperations(run, requested)).hasSize(20).containsEntry(20, List.of());
        assertThat(reads.freshOperations(run, Set.of(1, 20)).get(1)).extracting(OperationDefinition::id).containsExactly(11L);
        verify(operations, times(1)).selectByTenantAndResourceTypes(anyLong(), anySet());
    }

    @Test
    void should_skipAllOutputReads_whenMinimalOutputIncludesUnusedExtraKeys() {
        OutputSpec output = new OutputSpec(FactDetail.NONE, false, false, false, false,
            Set.of(new TypeOperation("REPORT", "VIEW")), false);
        assertThat(reads.outputOperations(run(ListGrantRead.DATABASE), output, Set.of(1))).isEmpty();
        assertThat(reads.resourceDescriptions(run(ListGrantRead.DATABASE), output, Set.of(100L))).isEmpty();
        assertThat(reads.roleDescriptions(run(ListGrantRead.DATABASE), output, Set.of(10L))).isEmpty();
        verifyNoInteractions(types, operations, resources, roles, grants, cache);
    }

    @Test
    void should_loadExtraTargetDefinition_whenNoGrantsOfThatTypeExist() {
        when(types.batchResolveTypeValues(1L, "resource_type", Set.of("REPORT"))).thenReturn(Map.of("REPORT", 1));
        when(operations.selectByTenantAndResourceTypes(1L, Set.of(1))).thenReturn(List.of(operation(11, 1, "VIEW", 2)));
        OutputSpec output = new OutputSpec(FactDetail.KEPT, false, true, false, false,
            Set.of(new TypeOperation("REPORT", "VIEW")), false);
        assertThat(reads.outputOperations(run(ListGrantRead.DATABASE), output, Set.of()).get(1))
            .extracting(OperationDefinition::code).containsExactly("VIEW");
        verifyNoInteractions(grants);
    }

    @Test
    void should_rememberMissingTypeAndOperation_whenRepeatedInOneRun() {
        RunState run = run(ListGrantRead.DATABASE);
        when(types.batchResolveTypeValues(1L, "resource_type", Set.of("REPORT", "MISSING"))).thenReturn(Map.of("REPORT", 1));
        Set<TypeOperation> keys = Set.of(new TypeOperation("REPORT", "MISSING"), new TypeOperation("MISSING", "VIEW"));
        assertThat(reads.resolveOperations(run, keys)).isEmpty();
        assertThat(reads.resolveOperations(run, keys)).isEmpty();
        verify(types, times(1)).batchResolveTypeValues(anyLong(), anyString(), anySet());
        verify(operations, times(1)).selectByTenantAndResourceTypes(1L, Set.of(1));
    }

    @Test
    void should_keepCachedMasksSeparateAndCopyRows_whenFreshDefinitionsAlsoLoaded() {
        OperationPermission stale = operation(11, 1, "VIEW", 2);
        OperationPermission fresh = operation(11, 1, "VIEW", 4);
        String key = AccessCacheCatalog.operationPermissionsByTypeKey(1);
        when(cache.getBatch(AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, 1L, Set.of(key)))
            .thenReturn(Map.of(key, Map.of(11L, stale)));
        when(operations.selectByTenantAndResourceTypes(1L, Set.of(1))).thenReturn(List.of(fresh));
        RunState run = run(ListGrantRead.DATABASE);
        assertThat(reads.freshOperations(run, Set.of(1)).get(1).getFirst().binaryBit()).isEqualTo(4);
        assertThat(reads.maskOperations(run, Set.of(1)).get(1).getFirst().binaryBit()).isEqualTo(2);
        stale.setBinaryBit(8L); fresh.setBinaryBit(16L);
        assertThat(reads.freshOperations(run, Set.of(1)).get(1).getFirst().binaryBit()).isEqualTo(4);
        assertThat(reads.maskOperations(run, Set.of(1)).get(1).getFirst().binaryBit()).isEqualTo(2);
        RunState reverseOrder = run(ListGrantRead.DATABASE);
        assertThat(reads.maskOperations(reverseOrder, Set.of(1)).get(1).getFirst().binaryBit()).isEqualTo(8);
        assertThat(reads.freshOperations(reverseOrder, Set.of(1)).get(1).getFirst().binaryBit()).isEqualTo(16);
    }

    @Test
    void should_notTreatPartialIdReadAsFullType_whenTypeIsLoadedLater() {
        when(operations.selectValidByIds(1L, Set.of(11L))).thenReturn(List.of(operation(11, 1, "VIEW", 2)));
        when(operations.selectByTenantAndResourceTypes(1L, Set.of(1))).thenReturn(List.of(
            operation(11, 1, "VIEW", 8), operation(12, 1, "UPDATE", 4)));
        RunState run = run(ListGrantRead.DATABASE);
        reads.freshOperationsByIds(run, Set.of(11L));
        assertThat(reads.freshOperations(run, Set.of(1)).get(1)).extracting(OperationDefinition::binaryBit).containsExactly(2L, 4L);
        verify(operations).selectByTenantAndResourceTypes(1L, Set.of(1));
    }

    @Test
    void should_notReadMissingOperationIdAgain_whenRequestedTwice() {
        RunState run = run(ListGrantRead.DATABASE);
        assertThat(reads.freshOperationsByIds(run, Set.of(99L))).isEmpty();
        assertThat(reads.freshOperationsByIds(run, Set.of(99L))).isEmpty();
        verify(operations, times(1)).selectValidByIds(1L, Set.of(99L));
    }

    @Test
    void should_useOneReadTokenBeforeDatabaseAndKeepCachePayload_whenSnapshotHasMixedMisses() {
        RunState run = run(ListGrantRead.ROLE_SNAPSHOT);
        RolePermEntry hot = new RolePermEntryMapper().toEntry(grant(100, 10));
        when(cache.getBatch(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, 1L, Set.of(10L, 20L, 30L)))
            .thenReturn(Map.of(10L, List.of(hot)));
        when(grants.selectValidByRoleIds(1L, Set.of(20L, 30L))).thenReturn(List.of(grant(200, 20)));
        @SuppressWarnings("unchecked") CacheReadToken<List<RolePermEntry>> token = mock(CacheReadToken.class);
        when(cache.beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT)).thenReturn(token);
        assertThat(reads.listGrants(run, Set.of(10L, 20L, 30L))).extracting(GrantFact::permissionId).containsExactlyInAnyOrder(100L, 200L);
        assertThat(reads.listGrants(run, Set.of(30L))).isEmpty();
        reads.listGrants(run, Set.of(40L));
        var order = inOrder(cache, grants);
        order.verify(cache).getBatch(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, 1L, Set.of(10L, 20L, 30L));
        order.verify(cache).beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT);
        order.verify(grants).selectValidByRoleIds(1L, Set.of(20L, 30L));
        order.verify(cache).putBatch(eq(token), eq(1L), argThat(data -> data.containsKey(30L) && data.get(30L).isEmpty()
            && data.get(20L).getFirst() instanceof RolePermEntry));
        verify(cache, times(1)).beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT);
        verify(cache, times(2)).putBatch(eq(token), eq(1L), anyMap());
        verify(grants, times(2)).selectValidByRoleIds(anyLong(), anySet());
    }

    @Test
    void should_bypassSnapshotEntirelyAndMemoizeRawFacts_whenDatabaseModeSelected() {
        when(grants.selectValidByRoleIds(1L, Set.of(10L))).thenReturn(List.of(grant(100, 10)));
        RunState run = run(ListGrantRead.DATABASE);
        assertThat(reads.listGrants(run, Set.of(10L))).extracting(GrantFact::permissionId).containsExactly(100L);
        reads.listGrants(run, Set.of(10L));
        verifyNoInteractions(cache);
        verify(grants, times(1)).selectValidByRoleIds(1L, Set.of(10L));
    }

    @Test
    void should_neverQueryUnboundedGrants_whenAnyRequiredDimensionIsEmpty() {
        RunState run = run(ListGrantRead.DATABASE);
        assertThat(reads.scopeGrants(run, Set.of(), Map.of(1, 2L))).isEmpty();
        assertThat(reads.scopeGrants(run, Set.of(10L), Map.of())).isEmpty();
        assertThat(reads.instanceGrants(run, Set.of(10L), Set.of(), Map.of(1, 2L))).isEmpty();
        assertThat(reads.instanceGrants(run, Set.of(10L), Set.of(100L), Map.of(1, 0L))).isEmpty();
        assertThat(reads.listGrants(run, Set.of())).isEmpty();
        verifyNoInteractions(grants, cache);
    }

    @Test
    void should_refuseInvalidTenantAndReleasedRun_beforeReading() {
        RunState invalid = new RunState(new QueryRequest(0L, new Roles(Set.of()), CallerContext.of(null), ReadOptions.defaults(), List.of()), Clock.systemUTC());
        assertThatThrownBy(() -> reads.freshOperations(invalid, Set.of(1))).isInstanceOf(QueryValidationException.class);
        RunState run = run(ListGrantRead.DATABASE);
        run.release();
        assertThatThrownBy(() -> reads.freshOperations(run, Set.of(1))).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(operations);
    }

    @Test
    void should_filterNullTypesAndChunkLargeRead_whenInputExceedsSqlBatch() {
        Set<Integer> requested = IntStream.rangeClosed(1, 501).boxed().collect(Collectors.toCollection(HashSet::new));
        requested.add(null);
        assertThat(reads.freshOperations(run(ListGrantRead.DATABASE), requested)).hasSize(501);
        verify(operations, times(2)).selectByTenantAndResourceTypes(eq(1L), anySet());
    }

    @Test
    void should_retryAfterTechnicalFailureAndRereadInNewRun_whenReadDidNotSucceed() {
        when(operations.selectByTenantAndResourceTypes(1L, Set.of(1)))
            .thenThrow(new IllegalStateException("DB unavailable")).thenReturn(List.of(operation(11, 1, "VIEW", 2)));
        RunState run = run(ListGrantRead.DATABASE);
        assertThatThrownBy(() -> reads.freshOperations(run, Set.of(1))).hasMessage("DB unavailable");
        assertThat(reads.freshOperations(run, Set.of(1)).get(1)).hasSize(1);
        reads.freshOperations(run(ListGrantRead.DATABASE), Set.of(1));
        verify(operations, times(3)).selectByTenantAndResourceTypes(1L, Set.of(1));
    }

    @Test
    void should_rejectMalformedExtraOperationPair_beforeAnyRead() {
        OutputSpec output = new OutputSpec(FactDetail.NONE, false, false, false, false,
            Set.of(new TypeOperation("REPORT", " ")), false);
        QueryRequest request = new QueryRequest(1L, new Roles(Set.of()), CallerContext.of(null), ReadOptions.defaults(),
            List.of(QueryItem.decision("check", new TypeLevel(List.of(new TypeOperation("REPORT", "VIEW"))), output)));
        assertThatThrownBy(() -> new QueryExecutionEngine(Clock.systemUTC()).execute(request))
            .isInstanceOf(QueryValidationException.class).hasMessageContaining("operationCode");
    }

    @Test
    void should_preserveFullOperationCachePayload_whenRefillingColdMaskDirectory() {
        OperationPermission row = operation(11, 1, "VIEW", 2);
        row.setCreatedBy(42L);
        row.setDeleteFlag(0L);
        when(operations.selectByTenantAndResourceTypes(1L, Set.of(1))).thenReturn(List.of(row));
        reads.maskOperations(run(ListGrantRead.DATABASE), Set.of(1));
        verify(cache).putBatch(eq(AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE), eq(1L), argThat(data -> {
            OperationPermission stored = data.get(AccessCacheCatalog.operationPermissionsByTypeKey(1)).get(11L);
            return Long.valueOf(1).equals(stored.getTenantId()) && Long.valueOf(42).equals(stored.getCreatedBy())
                && Long.valueOf(0).equals(stored.getDeleteFlag());
        }));
    }

    @Test
    void should_deductReadTimeAndNotRestartBudget_whenLaterMissesOrExpiredBudgetOccur() {
        AtomicLong clock = new AtomicLong();
        DistributedCacheStore store = mock(DistributedCacheStore.class);
        CacheService realCache = new DefaultCacheService(null, store, null, new CacheProperties(), null, clock::get);
        QueryReadSupport timed = new QueryReadSupport(types, operations, resources, roles, grants, realCache, new RolePermEntryMapper());
        when(grants.selectValidByRoleIds(1L, Set.of(10L))).thenAnswer(invocation -> {
            clock.set(Duration.ofSeconds(3).toNanos());
            return List.of(grant(100, 10));
        });
        when(grants.selectValidByRoleIds(1L, Set.of(20L))).thenAnswer(invocation -> {
            clock.set(Duration.ofSeconds(8).toNanos());
            return List.of(grant(200, 20));
        });
        when(grants.selectValidByRoleIds(1L, Set.of(30L))).thenAnswer(invocation -> {
            clock.set(Duration.ofSeconds(11).toNanos());
            return List.of(grant(300, 30));
        });
        RunState run = run(ListGrantRead.ROLE_SNAPSHOT);
        timed.listGrants(run, Set.of(10L));
        timed.listGrants(run, Set.of(20L));
        assertThat(timed.listGrants(run, Set.of(30L))).extracting(GrantFact::permissionId).containsExactly(300L);
        verify(store).putBatch(eq(AccessCacheCatalog.ROLE_PERM_SNAPSHOT), anyMap(), eq(Duration.ofSeconds(7)));
        verify(store).putBatch(eq(AccessCacheCatalog.ROLE_PERM_SNAPSHOT), anyMap(), eq(Duration.ofSeconds(2)));
        verify(store, times(2)).putBatch(eq(AccessCacheCatalog.ROLE_PERM_SNAPSHOT), anyMap(), any(Duration.class));
    }

    @Test
    void should_keepDatabaseTargetsIndependent_whenRoleSnapshotContainsDifferentFacts() {
        when(cache.getBatch(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, 1L, Set.of(10L)))
            .thenReturn(Map.of(10L, List.of(new RolePermEntryMapper().toEntry(grant(100, 10)))));
        when(grants.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(10L)), anyList()))
            .thenReturn(List.of(grant(200, 10)));
        RunState run = run(ListGrantRead.ROLE_SNAPSHOT);
        assertThat(reads.listGrants(run, Set.of(10L))).extracting(GrantFact::permissionId).containsExactly(100L);
        assertThat(reads.scopeGrants(run, Set.of(10L), Map.of(1, 2L))).extracting(GrantFact::permissionId).containsExactly(200L);
        reads.scopeGrants(run, Set.of(10L), Map.of(1, 2L));
        verify(grants, times(1)).selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(10L)), anyList());
        assertThatThrownBy(() -> reads.scopeGrants(run, Set.of(10L), Map.of(1, 2L)).clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_notMixTypeOperationPairs_whenBothTypesDefineBothCodes() {
        when(types.batchResolveTypeValues(1L, "resource_type", Set.of("REPORT", "USER")))
            .thenReturn(Map.of("REPORT", 1, "USER", 2));
        when(operations.selectByTenantAndResourceTypes(1L, Set.of(1, 2))).thenReturn(List.of(
            operation(11, 1, "VIEW", 2), operation(12, 1, "UPDATE", 4),
            operation(21, 2, "VIEW", 2), operation(22, 2, "UPDATE", 4)));
        TypeOperation reportView = new TypeOperation("REPORT", "VIEW");
        TypeOperation userUpdate = new TypeOperation("USER", "UPDATE");
        assertThat(reads.resolveOperations(run(ListGrantRead.DATABASE), Set.of(reportView, userUpdate)).values())
            .extracting(OperationDefinition::id).containsExactlyInAnyOrder(11L, 22L);
    }

    @Test
    void should_reuseTypeResolutionAndRememberExactResourceMisses_whenResolvingBusinessKeys() {
        when(types.batchResolveTypeValues(1L, "resource_type", Set.of("REPORT", "USER")))
            .thenReturn(Map.of("REPORT", 1, "USER", 2));
        ResourceEntity report = new ResourceEntity();
        report.setId(100L); report.setResourceType(1); report.setCode("same"); report.setCodeType("default");
        ResourceEntity other = new ResourceEntity();
        other.setId(200L); other.setResourceType(2); other.setCode("same"); other.setCodeType("default");
        when(resources.selectByTypesAndCodesAndCodeTypes(1L, Set.of(1, 2), Set.of("same"), Set.of("default", "special")))
            .thenReturn(List.of(report, other));
        ResourceResolveRequest reportKey = new ResourceResolveRequest("REPORT", "same", null, null);
        ResourceResolveRequest userKey = new ResourceResolveRequest("USER", "same", "special", null);
        RunState run = run(ListGrantRead.DATABASE);
        reads.resolveTypes(run, Set.of("REPORT", "USER"));
        assertThat(reads.resolveResources(run, List.of(reportKey, userKey))).containsOnlyKeys(reportKey.toKey()).containsEntry(reportKey.toKey(), 100L);
        assertThat(reads.resolveResources(run, List.of(userKey))).isEmpty();
        verify(types, times(1)).batchResolveTypeValues(anyLong(), anyString(), anySet());
        verify(types, never()).batchResolveResourceIds(anyLong(), anyList());
        verify(resources, times(1)).selectByTypesAndCodesAndCodeTypes(anyLong(), anySet(), anySet(), anySet());
    }

    @Test
    void should_rememberMissingDescriptions_whenOutputIsRequestedAgain() {
        RunState run = run(ListGrantRead.DATABASE);
        assertThat(reads.resourceDescriptions(run, OutputSpec.full(), Set.of(100L))).isEmpty();
        assertThat(reads.roleDescriptions(run, OutputSpec.full(), Set.of(10L))).isEmpty();
        reads.resourceDescriptions(run, OutputSpec.full(), Set.of(100L));
        reads.roleDescriptions(run, OutputSpec.full(), Set.of(10L));
        verify(resources, times(1)).selectValidByIds(1L, Set.of(100L));
        verify(roles, times(1)).selectValidByIds(1L, Set.of(10L));
        verifyNoInteractions(types);
    }
}
