package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncScope;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceAssociationOwnerReq;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class ResourceAssociationDomainPgIT {
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, ResourceAssociationDomainPgIT.class);
    }

    @Autowired ResourceAssociationDomainService associations;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ResourceEntitySyncAppService sync;
    @Autowired cn.ac.fage.accessmesh.access.type.service.TypeDefinitionAppService typeService;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    cn.ac.fage.accessmesh.access.engine.query.QueryGate gate;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport locks;
    @Autowired cn.ac.fage.accessmesh.access.resource.mapper.ResourceAssociationMapper relationMapper;
    @Autowired cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper resourceMapper;
    private TransactionTemplate tx;
    private static final String SOURCE = "association-business";

    @BeforeEach
    void prepare() {
        tx = new TransactionTemplate(transactions);
        jdbc.update("DELETE FROM resource_association");
        jdbc.update("DELETE FROM sync_metadata WHERE source_service=?", SOURCE);
        jdbc.update("DELETE FROM resource_publication_state WHERE source_service=?", SOURCE);
        jdbc.update("DELETE FROM role_resource_permission WHERE resource_type BETWEEN 991 AND 993");
        jdbc.update("DELETE FROM abstract_role WHERE id=991020");
        jdbc.update("DELETE FROM resource_entity WHERE resource_type BETWEEN 991 AND 993");
        jdbc.update("DELETE FROM type_definition WHERE id BETWEEN 991010 AND 991012");
        jdbc.update("DELETE FROM service_config WHERE service_code=?", SOURCE);
        jdbc.update("INSERT INTO service_config(tenant_id,service_code,name) VALUES (1,?,?)", SOURCE, SOURCE);
        jdbc.execute("""
            INSERT INTO type_definition(id,tenant_id,type_key,type_code,type_value,name,extra) VALUES
            (991010,1,'resource_type','ASSOCIATION_REPORT',991,'report',
             '{"managedMode":"SYNC","syncSourceService":"association-business"}'),
            (991011,1,'resource_type','ASSOCIATION_FIELD',992,'field',
             '{"managedMode":"SYNC","syncSourceService":"association-business","associationMode":"EXCLUSIVE","associationParentTypeCodes":["ASSOCIATION_REPORT"]}'),
            (991012,1,'resource_type','ASSOCIATION_SCOPE',993,'scope',
             '{"managedMode":"MANAGED","associationMode":"SHARED","associationParentTypeCodes":["ASSOCIATION_REPORT"]}');
            INSERT INTO resource_entity(id,tenant_id,resource_type,code,name,source_entity_id) VALUES
            (991001,1,991,'report-a','A','10'),(991002,1,991,'report-b','B','20'),
            (991003,1,992,'field-a','field',NULL),(991004,2,991,'foreign-report','foreign','foreign-10'),
            (991005,1,993,'region-r1','R1',NULL);
            """);
        AccessRequestContext.bind(RequestContext.service(1L, SOURCE));
    }

    @AfterEach
    void clearCaller() { AccessRequestContext.clear(); }

    @Test
    void shouldPublishExclusiveOwnershipAtomicallyAndRejectOldIncarnationReplay() {
        AccessRequestContext.bind(RequestContext.service(1L, SOURCE));
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-a", "10", 1, null, java.util.List.of("ASSOCIATION_SCOPE")), null).applied()).isTrue();
        var oldField = publication("UPSERT", "ASSOCIATION_FIELD", "published-field", "field-10", 1,
            new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "report-a", null, "10"), null);
        assertThat(sync.sync(1L, oldField, null).applied()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_association WHERE exclusive_resource_id IS NOT NULL AND delete_flag=0", Long.class)).isEqualTo(1);
        assertThat(sync.sync(1L, publication("DELETE", "ASSOCIATION_REPORT", "report-a", "10", 2, null, null), null).applied()).isTrue();
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-a", "20", 3, null, java.util.List.of("ASSOCIATION_SCOPE")), null).applied()).isTrue();
        var replay = sync.sync(1L, oldField, null);
        assertThat(replay.accepted()).isFalse();
        assertThat(replay.reason()).isEqualTo("RESOURCE_ASSOCIATION_IDENTITY_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code='published-field' AND delete_flag=0", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT source_entity_id FROM resource_entity WHERE code='report-a' AND delete_flag=0", String.class)).isEqualTo("20");
        var neverArrived = publication("UPSERT", "ASSOCIATION_FIELD", "late-old-field", "late-field-10", 1,
            new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "report-a", null, "10"), null);
        assertThat(sync.sync(1L, neverArrived, null).reason()).isEqualTo("RESOURCE_ASSOCIATION_IDENTITY_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code='late-old-field'", Long.class)).isZero();
    }

    @Test
    void shouldNotConsumeFieldVersionWhenOwnerHasNotArrived() {
        AccessRequestContext.bind(RequestContext.service(1L, SOURCE));
        var field = publication("UPSERT", "ASSOCIATION_FIELD", "waiting-field", "field-30", 1,
            new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "late-report", null, "30"), null);
        var first = sync.sync(1L, field, null);
        assertThat(first.retryClass()).isEqualTo("DEPENDENCY_MISSING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_metadata WHERE source_service=?", Long.class, SOURCE)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code='waiting-field'", Long.class)).isZero();
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "late-report", "30", 1, null, null), null).applied()).isTrue();
        assertThat(sync.sync(1L, field, null).applied()).isTrue();
    }

    private ResourceEntitySyncReq publication(String operation, String type, String code, String identity,
            long version, ResourceAssociationOwnerReq owner, java.util.List<String> references) {
        return new ResourceEntitySyncReq(operation, type, code, null, code, null, null, null, null, 1, null,
            SOURCE, "association-test", identity, new SyncVersionRef(LocalDateTime.of(2026, 10, 21, 0, 0), version),
            null, owner, references);
    }

    @Test
    void shouldCascadeFullMissingPrimaryWithoutDeletingSharedInstancesOrOtherReferences() {
        AccessRequestContext.bind(RequestContext.service(1L, SOURCE));
        var scope = new ResourceEntitySyncScope(SOURCE, "ASSOCIATION_REPORT");
        assertThat(sync.fullSync(1L, new ResourceEntityFullSyncReq(scope,
            java.util.List.of(reportItem("report-a", "10", 1), reportItem("report-b", "20", 1)), "1"), null).accepted()).isTrue();
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_FIELD", "full-owned-field", "field-40", 1,
            new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "report-a", null, "10"), null), null).applied()).isTrue();
        assertThat(sync.fullSync(1L, new ResourceEntityFullSyncReq(scope,
            java.util.List.of(reportItem("report-b", "20", 2)), "2"), null).accepted()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code IN ('report-a','full-owned-field') AND delete_flag=0", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code IN ('report-b','region-r1') AND delete_flag=0", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_association WHERE delete_flag=0 AND primary_resource_id=991002", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_metadata WHERE source_service=? AND target_status='DELETED' AND last_incremental_hash IS NOT NULL", Long.class, SOURCE)).isZero();
    }

    @Test
    void shouldKeepFullItemFailureAtomicAndRetainItsPreviousReference() {
        AccessRequestContext.bind(RequestContext.service(1L, SOURCE));
        var scope = new ResourceEntitySyncScope(SOURCE, "ASSOCIATION_REPORT");
        assertThat(sync.fullSync(1L, new ResourceEntityFullSyncReq(scope,
            java.util.List.of(reportItem("report-a", "10", 1)), "1"), null).accepted()).isTrue();
        var wrong = new ResourceEntitySyncItem("report-a", null, "must-not-change", null, null, null, null, 1, null,
            "association-test", "10", new SyncVersionRef(LocalDateTime.of(2026, 10, 21, 0, 0), 2L), null,
            java.util.List.of("ASSOCIATION_FIELD"));
        var result = sync.fullSync(1L, new ResourceEntityFullSyncReq(scope, java.util.List.of(wrong), "2"), null);
        assertThat(result.detail().itemResults()).anySatisfy(item -> {
            assertThat(item.applied()).isFalse();
            assertThat(item.reason()).isEqualTo("RESOURCE_ASSOCIATION_INVALID");
        });
        assertThat(jdbc.queryForObject("SELECT name FROM resource_entity WHERE id=991001", String.class)).isEqualTo("report-a");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_association WHERE delete_flag=0 AND primary_resource_id=991001", Long.class)).isEqualTo(1);
    }

    private ResourceEntitySyncItem reportItem(String code, String identity, long version) {
        return new ResourceEntitySyncItem(code, null, code, null, null, null, null, 1, null, "association-test", identity,
            new SyncVersionRef(LocalDateTime.of(2026, 10, 21, 0, 0), version), null, java.util.List.of("ASSOCIATION_SCOPE"));
    }

    @Test
    void shouldKeepExclusiveResourceTreesInsideTheSameOwner() {
        var ownerA = new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "report-a", null, "10");
        var ownerB = new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "report-b", null, "20");
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_FIELD", "tree-a", "tree-id-a", 1, ownerA, null), null).applied()).isTrue();
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_FIELD", "tree-b", "tree-id-b", 1, ownerB, null), null).applied()).isTrue();
        var rejected = new ResourceEntitySyncReq("UPSERT", "ASSOCIATION_FIELD", "tree-child", null, "child",
            "ASSOCIATION_FIELD", "tree-b", null, null, 1, null, SOURCE, "association-test", "tree-child-id",
            new SyncVersionRef(LocalDateTime.of(2026, 10, 21, 0, 0), 1L), null, ownerA, null);
        assertThat(sync.sync(1L, rejected, null).reason()).isEqualTo("RESOURCE_ASSOCIATION_INVALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code='tree-child'", Long.class)).isZero();
        var accepted = new ResourceEntitySyncReq("UPSERT", "ASSOCIATION_FIELD", "tree-child", null, "child",
            "ASSOCIATION_FIELD", "tree-a", null, null, 1, null, SOURCE, "association-test", "tree-child-id",
            rejected.syncVersion(), null, ownerA, null);
        assertThat(sync.sync(1L, accepted, null).applied()).isTrue();
        assertThat(jdbc.queryForObject("SELECT parent_id FROM resource_entity WHERE code='tree-child'", Long.class))
            .isEqualTo(jdbc.queryForObject("SELECT id FROM resource_entity WHERE code='tree-a'", Long.class));
    }

    @Test
    void shouldBindExclusivelyAndRejectReparenting() {
        var request = publication("UPSERT", "ASSOCIATION_FIELD", "bound-field", "bound-id", 1,
            new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "report-a", null, "10"), null);
        assertThat(sync.sync(1L, request, null).applied()).isTrue();
        assertThat(sync.sync(1L, request, null).reason()).isEqualTo("SYNC_VERSION_REPLAYED");
        assertThat(count()).isEqualTo(1);
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_FIELD", "bound-field", "bound-id", 2,
            new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "report-b", null, "20"), null), null).reason())
            .isEqualTo("RESOURCE_ASSOCIATION_INVALID");
        assertThat(jdbc.queryForObject("SELECT primary_resource_id FROM resource_association WHERE delete_flag=0", Long.class))
            .isEqualTo(991001L);
    }

    @Test
    void shouldReplaceWholeTypeReferencesWithoutTouchingAnotherReportOrInstances() {
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-a", "10", 1, null, java.util.List.of("ASSOCIATION_SCOPE")), null).applied()).isTrue();
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-b", "20", 1, null, java.util.List.of("ASSOCIATION_SCOPE")), null).applied()).isTrue();
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-a", "10", 2, null, java.util.List.of()), null).applied()).isTrue();
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT primary_resource_id FROM resource_association WHERE delete_flag=0", Long.class))
            .isEqualTo(991002L);
        assertThat(jdbc.queryForObject("SELECT delete_flag FROM resource_entity WHERE id=991005", Long.class)).isZero();
    }

    @Test
    void shouldRejectForeignTenantWrongTargetAndUntrustedSourceWithoutWriting() {
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_FIELD", "bad-field", "bad-id", 1,
            new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "foreign-report", null, "foreign-10"), null), null).reason())
            .isEqualTo("ASSOCIATION_OWNER_NOT_FOUND");
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_FIELD", "bad-field", "bad-id", 1,
            new ResourceAssociationOwnerReq("ASSOCIATION_SCOPE", "region-r1", null, "foreign-10"), null), null).reason())
            .isEqualTo("RESOURCE_ASSOCIATION_INVALID");
        AccessRequestContext.bind(RequestContext.service(1L, "other-service"));
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-a", "10", 1, null, java.util.List.of("ASSOCIATION_SCOPE")), null).retryClass())
            .isEqualTo("SECURITY_DENIED");
        AccessRequestContext.bind(RequestContext.service(1L, SOURCE));
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-a", "10", 1, null, java.util.List.of("ASSOCIATION_FIELD")), null).reason())
            .isEqualTo("RESOURCE_ASSOCIATION_INVALID");
        assertThat(count()).isZero();
    }

    @Test
    void shouldRollbackMapperWritesWithTheCallingTransaction() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_FIELD", "rollback-field", "rollback-id", 1,
                new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "report-a", null, "10"), null), null).applied()).isTrue();
            Long fieldId = resourceMapper.selectByTypeCodeAndCodeType(1L, 992, "rollback-field", "default").getId();
            assertThat(relationMapper.selectByExclusiveIds(1L, Set.of(fieldId))).hasSize(1);
            throw new IllegalStateException("injected after relation write");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code='rollback-field'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_metadata WHERE source_service=?", Long.class, SOURCE)).isZero();
    }

    @Test
    void shouldProtectReferencedTypesEvenWhenTheirDirectoryIsEmpty() {
        jdbc.update("DELETE FROM resource_entity WHERE id=991005");
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-a", "10", 1, null, java.util.List.of("ASSOCIATION_SCOPE")), null).applied()).isTrue();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> associations.assertTypesDeletable(1L, Set.of(991012L))))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> associations.assertTypeChangeAllowed(1L, 991012L, "{}")))
            .isInstanceOf(BizException.class);
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-a", "10", 2, null, java.util.List.of()), null).applied()).isTrue();
        tx.executeWithoutResult(s -> associations.assertTypesDeletable(1L, Set.of(991012L)));
    }

    @Test
    void shouldBlockModeChangesForBusinessGrantsButNotAuthorityRoots() {
        jdbc.update("DELETE FROM resource_entity WHERE id=991005");
        jdbc.update("INSERT INTO abstract_role(id,tenant_id,role_type,external_id,name) VALUES (991020,1,1,'association-role','role')");
        jdbc.update("INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_type,granted_bits,scope_all,can_grant,grant_source) VALUES (1,991020,993,1,true,true,'AUTHORITY_ROOT')");
        tx.executeWithoutResult(s -> associations.assertTypeChangeAllowed(1L, 991012L, "{}"));
        jdbc.update("UPDATE role_resource_permission SET grant_source='MANUAL' WHERE abstract_role_id=991020");
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> associations.assertTypeChangeAllowed(1L, 991012L, "{}")))
            .isInstanceOf(BizException.class);
    }

    @Test
    void shouldExpandOnlyExclusiveResourcesWhenDeletingAPrimary() {
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_FIELD", "owned-field", "owned-id", 1,
            new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "report-a", null, "10"), null), null).applied()).isTrue();
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-a", "10", 1, null, java.util.List.of("ASSOCIATION_SCOPE")), null).applied()).isTrue();
        assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-b", "20", 1, null, java.util.List.of("ASSOCIATION_SCOPE")), null).applied()).isTrue();
        Long fieldId = jdbc.queryForObject("SELECT id FROM resource_entity WHERE code='owned-field'", Long.class);
        Set<Long> deleted = tx.execute(s -> {
            Set<Long> ids = associations.expandResourceDeletion(1L, Set.of(991001L));
            associations.removeResourceAssociations(1L, ids, 7L);
            return ids;
        });
        assertThat(deleted).containsExactlyInAnyOrder(991001L, fieldId);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT primary_resource_id FROM resource_association WHERE delete_flag=0", Long.class))
            .isEqualTo(991002L);
    }

    @Test
    void shouldAllowOnlyOneOwnerWhenTwoTransactionsRace() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Boolean> first = competingBind(991001L, ready, start);
        CompletableFuture<Boolean> second = competingBind(991002L, ready, start);
        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            start.countDown();
        }
        assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder(true, false);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void shouldSerializeTypeModeChangesWithReferencePublication() throws Exception {
        jdbc.update("DELETE FROM resource_entity WHERE id=991005");
        org.mockito.Mockito.when(gate.hasPermissionByCode(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        RuntimeException failure = raceBeforePublicationCommit(
            () -> assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_REPORT", "report-a", "10", 1, null,
                java.util.List.of("ASSOCIATION_SCOPE")), null).applied()).isTrue(),
            () -> {
                AccessRequestContext.bind(RequestContext.user(1L, 42L));
                typeService.updateType(1L, new cn.ac.fage.accessmesh.access.type.dto.req.TypeUpdateReq(
                    991012L, null, null, null, "{\"managedMode\":\"MANAGED\",\"associationMode\":\"NONE\"}", null, null), 42L);
            });
        assertThat(failure).isInstanceOf(BizException.class);
        assertThat(((BizException) failure).getErrorCode()).isEqualTo(20081);
        assertThat(jdbc.queryForObject("SELECT extra->>'associationMode' FROM type_definition WHERE id=991012", String.class)).isEqualTo("SHARED");
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void shouldSerializePrimaryDeletionWithAnUncommittedFieldPublication() throws Exception {
        RuntimeException failure = raceBeforePublicationCommit(
            () -> assertThat(sync.sync(1L, publication("UPSERT", "ASSOCIATION_FIELD", "commit-field", "commit-id", 1,
                new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", "report-a", null, "10"), null), null).applied()).isTrue(),
            () -> assertThat(sync.sync(1L, publication("DELETE", "ASSOCIATION_REPORT", "report-a", "10", 1, null, null), null).applied()).isTrue());
        assertThat(failure).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code IN ('report-a','commit-field') AND delete_flag=0", Long.class)).isZero();
        assertThat(count()).isZero();
    }

    private RuntimeException raceBeforePublicationCommit(Runnable publish, Runnable contender) throws Exception {
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch contenderAtLock = new CountDownLatch(1);
        var contenderThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (Thread.currentThread() == contenderThread.get()) contenderAtLock.countDown();
            return invocation.callRealMethod();
        }).when(locks).lockTreeWrites(1L, cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var writer = pool.submit(() -> {
                AccessRequestContext.bind(RequestContext.service(1L, SOURCE));
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(s -> {
                        publish.run();
                        written.countDown();
                        awaitCommitGate(release);
                    });
                } finally { AccessRequestContext.clear(); }
            });
            try {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15))
                    .until(() -> writer.isDone() || written.getCount() == 0);
                if (written.getCount() != 0) writer.get(1, TimeUnit.SECONDS);
                var second = pool.submit(() -> {
                    contenderThread.set(Thread.currentThread());
                    AccessRequestContext.bind(RequestContext.service(1L, SOURCE));
                    try { contender.run(); return (RuntimeException) null; }
                    catch (RuntimeException failure) { return failure; }
                    finally { AccessRequestContext.clear(); }
                });
                assertThat(contenderAtLock.await(15, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                writer.get(20, TimeUnit.SECONDS);
                return second.get(20, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
    }

    private static void awaitCommitGate(CountDownLatch release) {
        try { assertThat(release.await(25, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private CompletableFuture<Boolean> competingBind(long owner, CountDownLatch ready, CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start gate timed out");
                AccessRequestContext.bind(RequestContext.service(1L, SOURCE));
                String code = owner == 991001L ? "report-a" : "report-b";
                String identity = owner == 991001L ? "10" : "20";
                return sync.sync(1L, publication("UPSERT", "ASSOCIATION_FIELD", "raced-field", "raced-id", 1,
                    new ResourceAssociationOwnerReq("ASSOCIATION_REPORT", code, null, identity), null), null).applied();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            } finally {
                AccessRequestContext.clear();
            }
        });
    }

    private long count() {
        return jdbc.queryForObject("SELECT count(*) FROM resource_association WHERE delete_flag=0", Long.class);
    }
}
