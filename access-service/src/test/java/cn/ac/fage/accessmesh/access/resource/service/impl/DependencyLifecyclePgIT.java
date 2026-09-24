package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.service.PermissionManifestAppService;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import cn.ac.fage.accessmesh.access.sync.dto.*;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import cn.ac.fage.accessmesh.access.type.dto.req.OperationKeyReq;
import cn.ac.fage.accessmesh.access.type.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.access.type.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.access.type.service.OperationAppService;
import cn.ac.fage.accessmesh.access.type.service.TypeDefinitionAppService;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

/** 真实写入口的声明生命周期；管理门禁放行，编译/锁/事务/SQL 均使用真实组件。 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false"
})
class DependencyLifecyclePgIT {
    private static final LocalDateTime AT = LocalDateTime.of(2026,9,21,0,0);
    private static int nextType = 1600;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, DependencyLifecyclePgIT.class); }
    @Autowired private PermissionManifestAppService manifests;
    @Autowired private ResourceEntitySyncAppService resources;
    @Autowired private OperationAppService operations;
    @Autowired private TypeDefinitionAppService types;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @SpyBean private TreeWriteLockSupport locks;
    @MockBean private PermQueryEngine engine;
    @BeforeEach void allowManagement() {
        when(engine.hasPermissionByCode(anyLong(), any(), anyString(), any(), anyString())).thenReturn(true);
        jdbc.execute("INSERT INTO abstract_role(tenant_id,role_type,external_id,name) SELECT 1,6,'bootstrap-admin','admin' WHERE NOT EXISTS (SELECT 1 FROM abstract_role WHERE tenant_id=1 AND role_type=6 AND external_id='bootstrap-admin' AND delete_flag=0)");
    }
    @AfterEach void clear() { AccessRequestContext.clear(); }

    @Test void shouldWithdrawBothSidesOfDeletedMiddle_thenRecoverOnlyByOriginalManifestReplay() {
        Fixture f = fixture();
        publish(f);
        assertThat(single(f,"DELETE","b",2,1).applied()).isTrue();
        assertThat(edges(f)).isZero();
        assertThat(rejected(f)).isEqualTo(2);
        assertThat(dirty(f)).isTrue();
        assertThat(single(f,"UPSERT","b",3,2).applied()).isTrue();
        assertThat(edges(f)).isZero();
        assertThat(rejected(f)).isEqualTo(2);
        assertThat(publish(f).detail().failedCount()).isZero();
        assertThat(edges(f)).isEqualTo(2);
        assertThat(dirty(f)).isFalse();
    }

    @Test void shouldKeepGraphIdentityOnDisableAndEnable() {
        Fixture f = fixture(); publish(f);
        var ids = jdbc.queryForList("SELECT id FROM resource_dependency WHERE owner_service_code=? AND delete_flag=0 ORDER BY id", Long.class, f.source());
        assertThat(single(f,"DISABLE","b",2,0).applied()).isTrue();
        assertThat(single(f,"UPSERT","b",3,1).applied()).isTrue();
        operations.updateOperation(1L,new OperationUpdateReq(f.code(),"VIEW","renamed",2L,0L),100L);
        assertThat(jdbc.queryForList("SELECT id FROM resource_dependency WHERE owner_service_code=? AND delete_flag=0 ORDER BY id", Long.class, f.source())).isEqualTo(ids);
        assertThat(dirty(f)).isFalse();
    }

    @Test void shouldClearFullScopeContributionsAndPreserveOtherService() {
        Fixture f = fixture(); Fixture other = fixture(); publish(f); publish(other);
        bind(f);
        assertThat(resources.fullSync(1L,new ResourceEntityFullSyncReq(new ResourceEntitySyncScope(f.source(),f.code()),List.of(),"2"),null).detail().deactivatedCount()).isEqualTo(3);
        assertThat(edges(f)).isZero(); assertThat(rejected(f)).isEqualTo(2);
        assertThat(edges(other)).isEqualTo(2); assertThat(dirty(other)).isFalse();
    }

    @Test void shouldRollbackResourceVersionAndGraph_whenLifecycleWriteFails() {
        Fixture f = fixture(); publish(f);
        jdbc.execute("""
                CREATE FUNCTION reject_dependency_dirty() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.is_dirty THEN RAISE EXCEPTION 'injected dirty failure'; END IF; RETURN NEW; END $$;
                CREATE TRIGGER reject_dependency_dirty BEFORE UPDATE ON service_manifest_sync
                FOR EACH ROW EXECUTE FUNCTION reject_dependency_dirty();
                """);
        try {
            assertThatThrownBy(() -> single(f,"DELETE","b",2,1)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(edges(f)).isEqualTo(2); assertThat(rejected(f)).isZero(); assertThat(dirty(f)).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE resource_type=? AND code='b' AND delete_flag=0",Integer.class,f.type())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT max_generation FROM resource_publication_state WHERE source_service=?",Long.class,f.source())).isEqualTo(1);
        } finally { jdbc.execute("DROP TRIGGER reject_dependency_dirty ON service_manifest_sync; DROP FUNCTION reject_dependency_dirty()"); }
        assertThat(single(f,"DELETE","b",2,1).applied()).isTrue();
        assertThat(edges(f)).isZero();
    }

    @Test void shouldRecompileOperationBitsAndRejectRemovedOperation_untilSourceRepublishes() {
        Fixture f = fixture(); publish(f);
        operations.updateOperation(1L,new OperationUpdateReq(f.code(),"VIEW",null,8L,null),100L);
        assertThat(jdbc.queryForList("SELECT required_operation_bits FROM resource_dependency WHERE owner_service_code=? AND delete_flag=0",Long.class,f.source())).containsOnly(8L);
        assertThat(dirty(f)).isTrue();
        assertThat(publish(f).detail().failedCount()).isZero();
        assertThat(dirty(f)).isFalse();
        operations.updateOperation(1L,new OperationUpdateReq(f.code(),"VIEW",null,null,1L),100L);
        assertThat(dirty(f)).isTrue();
        operations.deleteOperations(1L,List.of(new OperationKeyReq(f.code(),"VIEW")),100L);
        assertThat(edges(f)).isZero(); assertThat(rejected(f)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT reject_reason FROM permission_dependency_declaration WHERE source_service=? AND delete_flag=0",String.class,f.source())).containsOnly("OPERATION_INVALID");
        operations.createOperation(1L,f.code(),"VIEW","view",16L,0L,100L);
        assertThat(edges(f)).isZero(); assertThat(rejected(f)).isEqualTo(2);
        assertThat(publish(f).detail().failedCount()).isZero();
        assertThat(jdbc.queryForList("SELECT required_operation_bits FROM resource_dependency WHERE owner_service_code=? AND delete_flag=0",Long.class,f.source())).containsOnly(16L);
    }

    @Test void shouldRetireTypeDeclarations_andMarkOwnerChangeDirtyWithoutResolvingRejected() {
        Fixture f = fixture(); publish(f); bind(f);
        resources.fullSync(1L,new ResourceEntityFullSyncReq(new ResourceEntitySyncScope(f.source(),f.code()),List.of(),"2"),null);
        assertThat(publish(f).detail().failedCount()).isEqualTo(2);
        assertThat(dirty(f)).isFalse();
        String owner="changed-"+UUID.randomUUID();
        jdbc.update("INSERT INTO service_config(tenant_id,service_code,name) VALUES(1,?,'new owner')",owner);
        types.updateType(1L,new TypeUpdateReq(f.typeId(),null,null,null,"{\"managedMode\":\"SYNC\",\"syncSourceService\":\""+owner+"\"}", null, null),100L);
        assertThat(dirty(f)).isTrue(); assertThat(rejected(f)).isEqualTo(2); assertThat(edges(f)).isZero();
        types.deleteTypesByIds(1L,List.of(f.typeId()),100L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permission_dependency_declaration WHERE source_service=? AND delete_flag=0",Integer.class,f.source())).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCompileAfterConcurrentResourceOrOperationDeletionCommits(boolean deleteOperation) throws Exception {
        Fixture f = fixture(); publish(f);
        CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch manifestAtLock = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        doAnswer(call -> {
            if (Thread.currentThread().getName().equals("lifecycle-manifest-waiter")) manifestAtLock.countDown();
            return call.callRealMethod();
        }).when(locks).lockTreeWrites(1L, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var deletion = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                bind(f);
                try {
                    if (deleteOperation) operations.deleteOperations(1L,List.of(new OperationKeyReq(f.code(),"VIEW")),100L);
                    else assertThat(single(f,"DELETE","b",2,1).applied()).isTrue();
                    changed.countDown(); await(commit); return true;
                } finally { AccessRequestContext.clear(); }
            }));
            try {
                await(changed);
                var replay = pool.submit(() -> {
                    Thread.currentThread().setName("lifecycle-manifest-waiter");
                    try { return publish(f); } finally { AccessRequestContext.clear(); }
                });
                await(manifestAtLock); commit.countDown();
                assertThat(deletion.get(20,TimeUnit.SECONDS)).isTrue();
                var response = replay.get(20,TimeUnit.SECONDS);
                assertThat(response.detail().failedCount()).isEqualTo(2);
                assertThat(response.detail().itemResults()).allSatisfy(item ->
                        assertThat(item.reason()).isEqualTo(deleteOperation ? "OPERATION_INVALID" : "RESOURCE_MISSING"));
                assertThat(edges(f)).isZero();
            } finally { commit.countDown(); }
        }
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20,TimeUnit.SECONDS)).as("lifecycle transaction gate reached").isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    private cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp publish(Fixture f) {
        bind(f); return manifests.fullSync(1L,new PermissionManifestReq(1,"1","original",List.of(dependency(f,"ab","a","b"),dependency(f,"bc","b","c"))));
    }
    private Dependency dependency(Fixture f,String key,String from,String to) {
        return new Dependency(key,new ResourceKey(f.code(),from,null),List.of("VIEW"),List.of(new Requirement(new ResourceKey(f.code(),to,null),List.of("VIEW"))),null);
    }
    private cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp single(Fixture f,String action,String code,int generation,int status) {
        bind(f);return resources.sync(1L,new ResourceEntitySyncReq(action,f.code(),code,null,code,null,null,null,null,status,null,f.source(),null,null,new SyncVersionRef(AT,(long)generation),Integer.toString(generation)),null);
    }
    private int edges(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM resource_dependency WHERE owner_service_code=? AND delete_flag=0",Integer.class,f.source()); }
    private int rejected(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM permission_dependency_declaration WHERE source_service=? AND delete_flag=0 AND compile_status='REJECTED'",Integer.class,f.source()); }
    private boolean dirty(Fixture f) { return jdbc.queryForObject("SELECT is_dirty FROM service_manifest_sync WHERE source_service=?",Boolean.class,f.source()); }
    private void bind(Fixture f) { AccessRequestContext.bind(RequestContext.service(1L,f.source())); }
    private Fixture fixture() {
        int type=nextType++; String code="LIFECYCLE_"+type; String source="lifecycle-"+UUID.randomUUID();
        jdbc.update("INSERT INTO service_config(tenant_id,service_code,name) VALUES(1,?,'lifecycle')",source);
        long id=jdbc.queryForObject("INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name,extra) VALUES(1,'resource_type',?,?,'lifecycle',CAST(? AS jsonb)) RETURNING id",Long.class,code,type,"{\"managedMode\":\"SYNC\",\"syncSourceService\":\""+source+"\"}");
        jdbc.update("INSERT INTO operation_permission(tenant_id,resource_type,code,name,binary_bit,inherit_mask) VALUES(1,?,'VIEW','View',2,0)",type);
        var f=new Fixture(source,code,type,id);bind(f);
        var items=List.of("a","b","c").stream().map(c->new ResourceEntitySyncItem(c,null,c,null,null,null,null,1,null,null,null,new SyncVersionRef(AT,1L))).toList();
        assertThat(resources.fullSync(1L,new ResourceEntityFullSyncReq(new ResourceEntitySyncScope(source,code),items,"1"),null).detail().appliedCount()).isEqualTo(3);
        return f;
    }
    private record Fixture(String source,String code,int type,long typeId) {}
}
