package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.engine.query.*;
import cn.ac.fage.accessmesh.access.grant.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.grant.service.PermissionGrantAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.SyncVersionRef;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "accessmesh.sync.scheduler.enabled=false",
    "access.tenant.gate-repair.enabled=false", "logging.level.cn.ac.fage.accessmesh=WARN"
})
class ResourceAssociationAuthorizationPgIT {
    static final String REPORT = "ASSOC_RUNTIME_REPORT", FIELD = "ASSOC_RUNTIME_FIELD", SCOPE = "ASSOC_RUNTIME_SCOPE";
    static final String SOURCE = "association-runtime";
    static final long OPERATOR = 61001, READER = 61002, OPERATOR_ROLE = 62001, READER_ROLE = 62002;
    static final long A = 63001, B = 63002, FA = 63101, FB = 63102, X = 63201;
    @DynamicPropertySource static void infrastructure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, ResourceAssociationAuthorizationPgIT.class);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired QueryExecutionEngine engine;
    @Autowired PermissionGrantAppService grants;
    @Autowired ResourceEntitySyncAppService sync;
    @Autowired CacheService cache;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport locks;

    @BeforeEach void prepare() {
        cache.evictAll(AccessCacheCatalog.EFFECTIVE_ROLES, 1L);
        cache.evictAll(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, 1L);
        cache.evictAll(AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, 1L);
        jdbc.execute("""
            DELETE FROM resource_association;
            DELETE FROM permission_change_log WHERE change_reason='resource-association-cleanup';
            DELETE FROM role_resource_permission WHERE abstract_role_id IN (62001,62002);
            DELETE FROM user_role WHERE abstract_user_id IN (61001,61002);
            DELETE FROM abstract_user WHERE id IN (61001,61002);
            DELETE FROM abstract_role WHERE id IN (62001,62002);
            DELETE FROM resource_entity WHERE resource_type BETWEEN 9101 AND 9103;
            DELETE FROM operation_permission WHERE resource_type BETWEEN 9101 AND 9103;
            DELETE FROM type_definition WHERE id BETWEEN 91001 AND 91003;
            DELETE FROM domain_config WHERE biz_domain_id=91004;
            DELETE FROM biz_domain WHERE id=91004;
            DELETE FROM sync_metadata WHERE source_service='association-runtime';
            DELETE FROM resource_publication_state WHERE source_service='association-runtime';
            DELETE FROM service_config WHERE service_code='association-runtime';
            INSERT INTO service_config(tenant_id,service_code,name) VALUES(1,'association-runtime','runtime');
            INSERT INTO type_definition(id,tenant_id,type_key,type_code,type_value,name,extra) VALUES
              (91001,1,'resource_type','ASSOC_RUNTIME_REPORT',9101,'report','{"managedMode":"SYNC","syncSourceService":"association-runtime"}'),
              (91002,1,'resource_type','ASSOC_RUNTIME_FIELD',9102,'field','{"managedMode":"SYNC","syncSourceService":"association-runtime","associationMode":"EXCLUSIVE","associationParentTypeCodes":["ASSOC_RUNTIME_REPORT"]}'),
              (91003,1,'resource_type','ASSOC_RUNTIME_SCOPE',9103,'scope','{"managedMode":"MANAGED","associationMode":"SHARED","associationParentTypeCodes":["ASSOC_RUNTIME_REPORT"]}');
            INSERT INTO operation_permission(tenant_id,resource_type,code,name,binary_bit,inherit_mask) VALUES
              (1,9101,'VIEW','view',2,0),(1,9102,'VIEW','view',2,0),(1,9103,'VIEW','view',2,0),(1,9102,'UPDATE','update',4,0);
            INSERT INTO resource_entity(id,tenant_id,resource_type,code,name,source_entity_id) VALUES
              (63001,1,9101,'report-a','A','a-id'),(63002,1,9101,'report-b','B','b-id'),
              (63101,1,9102,'field-a','amount','fa-id'),(63102,1,9102,'field-b','amount','fb-id'),
              (63201,1,9103,'region-x','X',NULL);
            INSERT INTO resource_association(tenant_id,primary_resource_id,exclusive_resource_id) VALUES(1,63001,63101),(1,63002,63102);
            INSERT INTO resource_association(tenant_id,primary_resource_id,shared_type_id) VALUES(1,63001,91003);
            INSERT INTO biz_domain(id,tenant_id,code,name) VALUES(91004,1,'ASSOC_RUNTIME','runtime');
            INSERT INTO domain_config(tenant_id,biz_domain_id,config_type,extra) VALUES
              (1,91004,'CLASSIFY','{"resourceTypeCodes":["ASSOC_RUNTIME_REPORT","ASSOC_RUNTIME_FIELD","ASSOC_RUNTIME_SCOPE"]}'),
              (1,91004,'SUB_PERM','{"allowed":[{"parent_type":"ASSOC_RUNTIME_REPORT","child_types":["ASSOC_RUNTIME_FIELD","ASSOC_RUNTIME_SCOPE"]}]}');
            """);
        int roleType = jdbc.queryForObject("SELECT type_value FROM type_definition WHERE tenant_id=1 AND type_key='role_type' AND type_code='BASIC_ROLE' AND delete_flag=0", Integer.class);
        int userType = jdbc.queryForObject("SELECT type_value FROM type_definition WHERE tenant_id=1 AND type_key='user_type' AND type_code='USER' AND delete_flag=0", Integer.class);
        jdbc.update("INSERT INTO abstract_role(id,tenant_id,role_type,external_id,name) VALUES(62001,1,?,'runtime-operator','operator'),(62002,1,?,'runtime-reader','reader')", roleType, roleType);
        jdbc.update("INSERT INTO abstract_user(id,tenant_id,user_type,external_id,name) VALUES(61001,1,?,'runtime-operator','operator'),(61002,1,?,'runtime-reader','reader')", userType, userType);
        jdbc.update("INSERT INTO user_role(tenant_id,abstract_user_id,target_type,target_id) VALUES(1,61001,'ROLE',62001),(1,61002,'ROLE',62002)");
        for (String managementType : List.of("ROLE", "RESOURCE")) {
            int type = jdbc.queryForObject("SELECT type_value FROM type_definition WHERE tenant_id=1 AND type_key='resource_type' AND type_code=? AND delete_flag=0", Integer.class, managementType);
            long bit = jdbc.queryForObject("SELECT binary_bit FROM operation_permission WHERE tenant_id=1 AND resource_type=? AND code='MANAGE' AND delete_flag=0", Long.class, type);
            permission(OPERATOR_ROLE, type, null, bit, null, false);
        }
        for (int type : List.of(9101,9102,9103)) permission(OPERATOR_ROLE, type, null, 2, null, true);
        AccessRequestContext.bind(RequestContext.user(1L, OPERATOR));
    }

    @AfterEach void clear() { AccessRequestContext.clear(); }

    @Test void shouldFilterDirectAndDependentFactsByOwnershipAndWholeTypeReference() {
        long pa = permission(READER_ROLE, 9101, A, 2, null, false);
        permission(READER_ROLE, 9101, B, 2, null, false);
        permission(READER_ROLE, 9102, FA, 2, pa, false);
        permission(READER_ROLE, 9102, FB, 2, null, false);
        permission(READER_ROLE, 9103, X, 2, null, false);
        assertThat(scope(A, FIELD).items()).extracting(QueryScopesResp.ScopeItem::resourceCode).containsExactly("field-a");
        assertThat(scope(B, FIELD).items()).extracting(QueryScopesResp.ScopeItem::resourceCode).containsExactly("field-b");
        assertThat(scope(A, SCOPE).items()).extracting(QueryScopesResp.ScopeItem::resourceCode).containsExactly("region-x");
        assertThat(scope(B, SCOPE).scopeMode()).isEqualTo(ScopeMode.DENIED);
    }

    @Test void shouldProjectExclusiveAllAsCurrentOwnerInstancesAndKeepSharedAllMeaning() {
        permission(READER_ROLE, 9101, A, 2, null, false);
        permission(READER_ROLE, 9102, null, 2, null, false);
        permission(READER_ROLE, 9103, null, 2, null, false);
        assertThat(scope(A, FIELD).scopeMode()).isEqualTo(ScopeMode.INSTANCE);
        assertThat(scope(A, FIELD).items()).extracting(QueryScopesResp.ScopeItem::resourceCode).containsExactly("field-a");
        assertThat(scope(A, SCOPE).scopeMode()).isEqualTo(ScopeMode.ALL);
        jdbc.update("UPDATE resource_entity SET status=0 WHERE id=?", FA);
        assertThat(scope(A, FIELD).scopeMode()).isEqualTo(ScopeMode.EMPTY);
        jdbc.update("UPDATE resource_entity SET status=0 WHERE id=?", A);
        assertThat(scope(A, SCOPE).scopeMode()).isEqualTo(ScopeMode.DENIED);
    }

    @Test void shouldCheckAssociationsBeforeBroadGrantShortCircuitAndKeepClausePairing() {
        permission(READER_ROLE, 9102, null, 2, null, false);
        var parent = parent(A);
        var results = engine.execute(new QueryRequest(1L, new User(READER), CallerContext.of("127.0.0.1"), ReadOptions.defaults(), List.of(
            decision("a", FIELD, "VIEW", new ByEntityId(FA), parent),
            decision("b", FIELD, "VIEW", new ByEntityId(FB), parent),
            decision("ghost", FIELD, "VIEW", new ByCode("ghost", "default", null), parent))));
        assertThat(((DecisionResult) results.orderedResults().get(0)).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(((DecisionResult) results.orderedResults().get(1)).outcome()).isEqualTo(DecisionResult.Decision.DENY);
        assertThat(((DecisionResult) results.orderedResults().get(1)).reason()).isEqualTo(DecisionResult.Reason.RESOURCE_NOT_IN_PARENT_CONTEXT);
        assertThat(((DecisionResult) results.orderedResults().get(2)).outcome()).isEqualTo(DecisionResult.Decision.DENY);
        jdbc.update("DELETE FROM role_resource_permission WHERE abstract_role_id=? AND resource_type=9102", READER_ROLE);
        permission(READER_ROLE, 9102, null, 4, null, false);
        var mixed = QueryItem.decision("mixed", new TargetSet(List.of(
            new TargetClause(new TypeOperation(FIELD,"VIEW"), new ByEntityId(FA)),
            new TargetClause(new TypeOperation(FIELD,"UPDATE"), new ByEntityId(FB))), Inheritance.SELF, TypeFallback.ALLOW, parent), OutputSpec.minimal());
        assertThat(((DecisionResult) engine.execute(new QueryRequest(1L, new User(READER), CallerContext.of("127.0.0.1"), ReadOptions.defaults(), List.of(mixed)))
            .orderedResults().getFirst()).outcome()).isEqualTo(DecisionResult.Decision.DENY);
    }

    @Test void shouldRejectWrongOwnerAndUnreferencedChildrenWithAtomicGrantPlan() {
        var wrong = new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(key(REPORT,"report-a"), null,
            List.of(key(FIELD,"field-a"), key(FIELD,"field-b")))), List.of(), List.of());
        assertThatThrownBy(() -> grants.applyGrantPlan(1L, new ApplyGrantPlanReq(null,"BASIC_ROLE","runtime-reader",wrong)))
            .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(20080));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_resource_permission WHERE abstract_role_id=?", Long.class, READER_ROLE)).isZero();
        var missing = new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(key(REPORT,"report-b"), null,
            List.of(key(SCOPE,"region-x")))), List.of(), List.of());
        assertThatThrownBy(() -> grants.applyGrantPlan(1L,new ApplyGrantPlanReq(null,"BASIC_ROLE","runtime-reader",missing)))
            .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(20080));
    }

    @Test void shouldClearOnlySpecificParentChildrenOnUnbindAndNotRestoreThemOnRebind() {
        long pa = permission(READER_ROLE,9101,A,2,null,false);
        long pb = permission(READER_ROLE,9101,B,2,null,false);
        long all = permission(READER_ROLE,9101,null,2,null,false);
        long scoped = permission(READER_ROLE,9103,X,2,pa,false);
        long scopedAll = permission(READER_ROLE,9103,null,2,pa,false);
        long other = permission(READER_ROLE,9103,X,2,pb,false);
        long sharedParent = permission(READER_ROLE,9103,X,2,all,false);
        long direct = permission(READER_ROLE,9103,X,2,null,false);
        long broad = permission(READER_ROLE,9103,null,2,null,false);
        long field = permission(READER_ROLE,9102,FA,2,pa,false);
        assertThat(scope(A,SCOPE,ListGrantRead.ROLE_SNAPSHOT).scopeMode()).isEqualTo(ScopeMode.ALL);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            publishReferences(List.of(),1);
            status.setRollbackOnly();
        });
        assertThat(active(scoped)).isTrue(); assertThat(active(scopedAll)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permission_change_log WHERE change_reason='resource-association-cleanup'",Long.class)).isZero();
        publishReferences(List.of(),1);
        assertThat(active(scoped)).isFalse(); assertThat(active(scopedAll)).isFalse();
        assertThat(List.of(other,sharedParent,direct,broad,field)).allSatisfy(id -> assertThat(active(id)).isTrue());
        assertThat(scope(A,SCOPE).scopeMode()).isEqualTo(ScopeMode.DENIED);
        publishReferences(List.of(SCOPE),2);
        assertThat(active(scoped)).isFalse(); assertThat(active(scopedAll)).isFalse();
        assertThat(scope(A,SCOPE).scopeMode()).isEqualTo(ScopeMode.ALL);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permission_change_log WHERE change_reason='resource-association-cleanup'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(diff_snapshot::jsonb->'removedPermissionIds') FROM permission_change_log WHERE change_reason='resource-association-cleanup'",Integer.class)).isEqualTo(2);
    }

    @Test void shouldKeepNoParentItemsIndependentInMixedRequests() {
        permission(READER_ROLE,9102,null,2,null,false);
        var output = new OutputSpec(FactDetail.RAW_AND_KEPT,true,true,false,PresentationExpansion.NONE,Set.of(),false);
        var scoped = QueryItem.facts("scoped",new TargetSet(List.of(new TargetClause(new TypeOperation(FIELD,"VIEW"),new ByEntityId(FA))),
            Inheritance.SELF,TypeFallback.ALLOW,parent(A)),Evaluation.full(),output);
        var ordinary = QueryItem.facts("ordinary",new TargetSet(List.of(new TargetClause(new TypeOperation(FIELD,"VIEW"),new ByEntityId(FB))),
            Inheritance.SELF,TypeFallback.ALLOW,null),Evaluation.full(),output);
        var result = engine.execute(new QueryRequest(1L,new User(READER),CallerContext.of(null),ReadOptions.defaults(),List.of(scoped,ordinary)));
        assertThat(((GrantSetResult)result.orderedResults().get(0)).details().descriptions().exclusiveScopes()).containsKey(9102);
        var plain = (GrantSetResult)result.orderedResults().get(1);
        assertThat(plain.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.PRESENT);
        assertThat(plain.details().descriptions().exclusiveScopes()).isEmpty();
    }

    @Test void shouldStopInheritedChecksAndPresentationAtForeignOrDisabledNodes() {
        permission(READER_ROLE,9101,A,2,null,false);
        permission(READER_ROLE,9102,FA,2,null,false);
        jdbc.update("UPDATE resource_entity SET parent_id=? WHERE id=?",FA,FB);
        jdbc.update("INSERT INTO resource_entity(id,tenant_id,resource_type,code,name,source_entity_id,parent_id) VALUES(63103,1,9102,'field-child','child','fc-id',?)",FB);
        jdbc.update("INSERT INTO resource_association(tenant_id,primary_resource_id,exclusive_resource_id) VALUES(1,?,63103)",A);
        var inherited = QueryItem.decision("inherit",new TargetSet(List.of(new TargetClause(new TypeOperation(FIELD,"VIEW"),
            new ByEntityId(63103L))),Inheritance.SELF_AND_ANCESTORS,TypeFallback.DISALLOW,parent(A)),OutputSpec.minimal());
        assertThat(((DecisionResult) engine.execute(new QueryRequest(1L,new User(READER),CallerContext.of(null),ReadOptions.defaults(),
            List.of(inherited))).orderedResults().getFirst()).outcome()).isEqualTo(DecisionResult.Decision.DENY);
        var output = new OutputSpec(FactDetail.RAW_AND_KEPT,true,true,true,PresentationExpansion.BOTH,Set.of(),false);
        var list = QueryItem.grantListFacts("tree",parent(A),Evaluation.full(),output);
        var result = engine.execute(new QueryRequest(1L,new User(READER),CallerContext.of(null),new ReadOptions(ListGrantRead.DATABASE),List.of(list)));
        assertThat(((GrantSetResult)result.orderedResults().getFirst()).details().presentation()).extracting(PresentationEntry::displayedEntityId)
            .doesNotContain(FB,63103L);
        jdbc.update("UPDATE resource_entity SET parent_id=?,status=0 WHERE id=?",FA,FB);
        jdbc.update("UPDATE resource_association SET primary_resource_id=? WHERE exclusive_resource_id=?",A,FB);
        assertThat(((DecisionResult) engine.execute(new QueryRequest(1L,new User(READER),CallerContext.of(null),ReadOptions.defaults(),
            List.of(inherited))).orderedResults().getFirst()).outcome()).isEqualTo(DecisionResult.Decision.DENY);
    }

    @Test void shouldFilterCachedInstanceFactsOnDisableAndDeleteOwnedGrantsOnPrimaryDeletion() {
        long pa = permission(READER_ROLE,9101,A,2,null,false);
        long field = permission(READER_ROLE,9102,FA,2,pa,false);
        long region = permission(READER_ROLE,9103,X,2,pa,false);
        long shared = permission(READER_ROLE,9103,X,2,null,false);
        assertThat(scope(A,FIELD,ListGrantRead.ROLE_SNAPSHOT).scopeMode()).isEqualTo(ScopeMode.INSTANCE);
        jdbc.update("UPDATE resource_entity SET status=0 WHERE id=?",FA);
        assertThat(scope(A,FIELD,ListGrantRead.ROLE_SNAPSHOT).scopeMode()).isEqualTo(ScopeMode.DENIED);
        AccessRequestContext.bind(RequestContext.service(1L,SOURCE));
        var delete = new ResourceEntitySyncReq("DELETE",REPORT,"report-a","default",null,null,null,null,null,null,null,SOURCE,
            "report","a-id",new SyncVersionRef(LocalDateTime.of(2026,10,22,0,0),1L),null,null,null);
        assertThat(sync.sync(1L,delete,null).applied()).isTrue();
        assertThat(List.of(pa,field,region)).allSatisfy(id -> assertThat(active(id)).isFalse());
        assertThat(active(shared)).isTrue();
        assertThat(jdbc.queryForObject("SELECT delete_flag=0 FROM resource_entity WHERE id=?",Boolean.class,FB)).isTrue();
        assertThat(jdbc.queryForObject("SELECT delete_flag=0 FROM resource_entity WHERE id=?",Boolean.class,FA)).isFalse();
        assertThat(scope(A,FIELD,ListGrantRead.ROLE_SNAPSHOT).scopeMode()).isEqualTo(ScopeMode.DENIED);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true,false})
    void shouldSerializeGrantAndUnbindUntilCommit(boolean grantFirst) throws Exception {
        var plan = new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(key(REPORT,"report-a"),null,
            List.of(key(SCOPE,"region-x")))),List.of(),List.of());
        Runnable grant = () -> {
            AccessRequestContext.bind(RequestContext.user(1L,OPERATOR));
            grants.applyGrantPlan(1L,new ApplyGrantPlanReq(null,"BASIC_ROLE","runtime-reader",plan));
        };
        Runnable unlink = () -> publishReferences(List.of(),1);
        var written = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var atLock = new java.util.concurrent.CountDownLatch(1);
        var contenderThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (Thread.currentThread() == contenderThread.get()) atLock.countDown();
            return invocation.callRealMethod();
        }).when(locks).lockTreeWrites(1L,cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                try {
                    new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                        (grantFirst ? grant : unlink).run();
                        written.countDown();
                        try { assertThat(release.await(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                    });
                } finally { AccessRequestContext.clear(); }
            });
            try {
                assertThat(written.await(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> {
                    contenderThread.set(Thread.currentThread());
                    try { (grantFirst ? unlink : grant).run(); return (RuntimeException)null; }
                    catch (RuntimeException error) { return error; }
                    finally { AccessRequestContext.clear(); }
                });
                assertThat(atLock.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(second.isDone()).isFalse();
                release.countDown(); first.get(20,java.util.concurrent.TimeUnit.SECONDS);
                RuntimeException error = second.get(20,java.util.concurrent.TimeUnit.SECONDS);
                if (grantFirst) assertThat(error).isNull();
                else assertThat(error).isInstanceOfSatisfying(BizException.class,e -> assertThat(e.getErrorCode()).isEqualTo(20080));
            } finally { release.countDown(); }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_resource_permission WHERE abstract_role_id=? AND resource_type=9103 AND delete_flag=0",Long.class,READER_ROLE)).isZero();
    }

    private long permission(long role, int type, Long entity, long bit, Long parent, boolean canGrant) {
        return jdbc.queryForObject("INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_type,resource_entity_id,granted_bits,depend_on,scope_all,can_grant) VALUES(1,?,?,?,?,?,?,?) RETURNING id",
            Long.class,role,type,entity,bit,parent,entity==null,canGrant);
    }
    private boolean active(long id) { return jdbc.queryForObject("SELECT delete_flag=0 FROM role_resource_permission WHERE id=?",Boolean.class,id); }
    private ParentRequirement parent(long id) { return new ParentRequirement(REPORT,new ByEntityId(id),Set.of("VIEW")); }
    private QueryScopesResp.ScopeGroup scope(long parentId,String type) {
        return scope(parentId,type,ListGrantRead.DATABASE);
    }
    private QueryScopesResp.ScopeGroup scope(long parentId,String type,ListGrantRead source) {
        TypeOperation requirement = new TypeOperation(type,"VIEW");
        OutputSpec output = new OutputSpec(FactDetail.RAW_AND_KEPT,true,true,false,PresentationExpansion.NONE,Set.of(requirement),false);
        var request = new QueryRequest(1L,new User(READER),CallerContext.of("127.0.0.1"),new ReadOptions(source),
            List.of(QueryItem.grantListFacts("scope",parent(parentId),Evaluation.full(),output)));
        return ScopeCoverageProjector.project((GrantSetResult) engine.execute(request).orderedResults().getFirst(),List.of(requirement)).getFirst();
    }
    private QueryItem decision(String key,String type,String operation,ResourceRef resource,ParentRequirement parent) {
        return QueryItem.decision(key,new TargetSet(List.of(new TargetClause(new TypeOperation(type,operation),resource)),
            Inheritance.SELF,TypeFallback.ALLOW,parent),OutputSpec.minimal());
    }
    private ApplyGrantPlanReq.GrantRecordKey key(String type,String code) {
        return new ApplyGrantPlanReq.GrantRecordKey(type,code,"default","VIEW",ScopeMode.INSTANCE,null,null,false);
    }
    private void publishReferences(List<String> references,long version) {
        AccessRequestContext.bind(RequestContext.service(1L,SOURCE));
        var request = new ResourceEntitySyncReq("UPSERT",REPORT,"report-a","default","A",null,null,null,null,1,null,SOURCE,
            "report","a-id",new SyncVersionRef(LocalDateTime.of(2026,10,22,0,0),version),null,null,references);
        assertThat(sync.sync(1L,request,null).applied()).isTrue();
        AccessRequestContext.bind(RequestContext.user(1L,OPERATOR));
    }
}
