package cn.ac.fage.accessmesh.example.metadata;

import cn.ac.fage.accessmesh.example.it.ExamplePgInfra;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoReportMapper;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoReportFieldMapper;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReport;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReportField;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.metadata.service.DemoMetadataAppService;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.*;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "example.publication.enabled=false",
    "spring.main.web-application-type=none",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false", "spring.config.import=optional:classpath:/test-nope.yml"
})
class MetadataPersistencePgIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        ExamplePgInfra.PG.register(registry, MetadataPersistencePgIT.class);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DemoReportMapper reports;
    @Autowired DemoReportFieldMapper fields;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DemoMetadataDomainService metadata;
    @Autowired DemoMetadataAppService app;
    @MockitoBean BusinessPermChecker permissionChecker;
    @MockitoBean cn.ac.fage.accessmesh.example.metadata.publication.DemoResourceSyncClient syncClient;
    @Autowired cn.ac.fage.accessmesh.example.metadata.service.DemoPublicationAppService publisher;
    @Autowired cn.ac.fage.accessmesh.example.metadata.domain.DemoPublicationDomainService publications;
    @MockitoBean cn.ac.fage.accessmesh.example.perm.ExamplePermissionProperties credentials;
    @Autowired tools.jackson.databind.json.JsonMapper json;

    @BeforeEach
    void clearFixtures() {
        // JdbcTemplate fixtures are outside Flex's Mapper transaction and must be cleaned explicitly.
        jdbc.update("DELETE FROM demo_report_field");
        jdbc.update("DELETE FROM demo_publication");
        jdbc.update("DELETE FROM demo_scope");
        jdbc.update("DELETE FROM demo_report");
        jdbc.update("DELETE FROM demo_dataset");
        jdbc.update("INSERT INTO demo_dataset(id,tenant_id,dataset_code) VALUES (1,1,'sales-records'),(2,2,'sales-records')");
        jdbc.queryForObject("SELECT setval(pg_get_serial_sequence('demo_dataset','id'),2)", Long.class);
        when(credentials.require("1")).thenReturn(new cn.ac.fage.accessmesh.example.perm.ExamplePermissionProperties.Credential("test-tenant-1", "test-only-secret"));
    }

    @Test
    void mapperTransactionRollsBackBothWritesAndCanCommitTheRetry() {
        var transaction = new TransactionTemplate(transactions);
        assertThrows(ExpectedRollback.class, () -> transaction.executeWithoutResult(status -> {
            insertMetadata();
            throw new ExpectedRollback();
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_report", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_report_field", Long.class));
        transaction.executeWithoutResult(status -> insertMetadata());
        var persisted = reports.selectByCode(1, "atomic");
        assertNotNull(persisted);
        assertEquals("remark", fields.selectByReport(1, persisted.getId()).getFirst().getFieldKey());
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM demo_report_field", Long.class));
    }

    private void insertMetadata() {
        var report = new DemoReport();
        report.setTenantId(1L); report.setDatasetId(1L); report.setReportCode("atomic");
        report.setName("atomic"); report.setFieldResourceTypeCode("DEMO_REPORT_FIELD"); report.setCreatedBy(42L);
        assertEquals(1, reports.insert(report));
        assertNotNull(report.getId());
        var field = new DemoReportField();
        field.setTenantId(1L); field.setReportId(report.getId()); field.setFieldKey("remark"); field.setCreatedBy(42L);
        assertEquals(1, fields.insert(field));
    }

    private static class ExpectedRollback extends RuntimeException {}

    @Test
    void shouldRejectScopeTypeUsedByReportFieldsAndKeepTransactionAtomic() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> metadata.createReport(1, 42, "r1", "报表", "sales-records", "CUSTOM_FIELD"));
        var denied = assertThrows(BizException.class, () -> tx.executeWithoutResult(s ->
            metadata.saveScope(1, 42, "CUSTOM_FIELD", "1", "default", "团队 T1", true, 0)));
        assertEquals(30102, denied.getErrorCode());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_scope", Long.class));
        // The reservation is tenant-local, and custom names remain supported.
        tx.executeWithoutResult(s -> metadata.saveScope(2, 42, "CUSTOM_FIELD", "1", "default", "团队", true, 0));
        tx.executeWithoutResult(s -> metadata.createReport(1, 42, "r2", "另一报表", "sales-records", "CUSTOM_FIELD"));
    }

    @Test
    void shouldRejectFieldTypeUsedByScopesIncludingHistoricalAndReservedTypes() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> metadata.saveScope(1, 42, "CUSTOM_SCOPE", "T1", null, "团队", true, 0));
        jdbc.update("UPDATE demo_scope SET delete_flag=id");
        for (String type : java.util.List.of("CUSTOM_SCOPE", "DEMO_REPORT", "DEMO_POLICY", "DEMO_DATASET", "DEMO_TASK")) {
            var denied = assertThrows(BizException.class, () -> tx.executeWithoutResult(s ->
                metadata.createReport(1, 42, "invalid", "报表", "sales-records", type)), type);
            assertEquals(30102, denied.getErrorCode());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_report", Long.class));
    }

    @Test
    void shouldSerializeCompetingPurposeRegistrationsUntilCommit() throws Exception {
        var tx = new TransactionTemplate(transactions);
        var pending = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Integer>>();
        tx.executeWithoutResult(s -> {
            metadata.createReport(1, 42, "concurrent", "报表", "sales-records", "SHARED_TYPE");
            var future = CompletableFuture.supplyAsync(() -> {
                try {
                    tx.executeWithoutResult(other -> metadata.saveScope(1, 42, "SHARED_TYPE", "T1", null, "团队", true, 0));
                    return 200;
                } catch (BizException e) { return e.getErrorCode(); }
            });
            pending.set(future);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean blocked = false;
            while (System.nanoTime() < deadline && !future.isDone()) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database()
                      AND pid<>pg_backend_pid() AND cardinality(pg_blocking_pids(pid))>0)
                    """, Boolean.class));
                if (blocked) break;
                try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            assertTrue(blocked, "the competing write must wait for the actual transaction, not see uncommitted absence");
        });
        assertEquals(30102, pending.get().get(10, TimeUnit.SECONDS));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_scope", Long.class));
    }

    @Test
    void shouldRollbackMetadataAndFrozenPublicationTogether() {
        var tx = new TransactionTemplate(transactions);
        assertThrows(ExpectedRollback.class, () -> tx.executeWithoutResult(s -> {
            metadata.createReport(1, 42, "rollback", "报表", "sales-records", "FIELDS");
            metadata.registerFields(1, 42, "rollback", 0, List.of("amount", "remark"));
            throw new ExpectedRollback();
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_report", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_report_field", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_publication", Long.class));
    }

    @Test
    void shouldKeepFieldIdentityAndPublicationHistoryAcrossRemoveAndRecreate() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> metadata.createReport(1, 42, "catalog", "报表", "sales-records", "FIELDS"));
        var first = tx.execute(s -> metadata.registerFields(1, 42, "catalog", 0, List.of("amount", "remark")));
        long amount = first.stream().filter(f -> f.getFieldKey().equals("amount")).findFirst().orElseThrow().getId();
        String originalPayload = jdbc.queryForObject("SELECT payload::text FROM demo_publication WHERE resource_type_code='FIELDS' AND resource_code=? AND source_version=1", String.class, Long.toString(amount));
        tx.executeWithoutResult(s -> metadata.removeFields(1, 42, "catalog", 1, List.of(amount)));
        var recreated = tx.execute(s -> metadata.registerFields(1, 42, "catalog", 2, List.of("amount"))).getFirst();
        assertNotEquals(amount, recreated.getId());
        assertEquals(3L, metadata.getReport(1, "catalog").getCatalogVersion());
        assertEquals(originalPayload, jdbc.queryForObject("SELECT payload::text FROM demo_publication WHERE resource_type_code='FIELDS' AND resource_code=? AND source_version=1", String.class, Long.toString(amount)));
        assertEquals("DELETE", jdbc.queryForObject("SELECT payload->>'operation' FROM demo_publication WHERE resource_type_code='FIELDS' AND resource_code=? AND source_version=2", String.class, Long.toString(amount)));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_publication WHERE payload ? 'parentResourceCode'", Long.class));
        var conflict = assertThrows(BizException.class, () -> tx.executeWithoutResult(s ->
            metadata.updateField(1, 42, "catalog", 1, recreated.getId(), false)));
        assertEquals(30103, conflict.getErrorCode());
        assertTrue(recreated.getEnabled());
    }

    @Test
    void shouldAuthorizeActualTargetBeforeStartingWriteTransaction() {
        var caller = new Caller(1, 42, "127.0.0.1");
        when(permissionChecker.check(eq("1"), eq("42"), eq("127.0.0.1"), any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new BusinessPermChecker.Decision(false, "NO_PERMISSION");
        });
        var request = new ReportCreateReq("protected", "报表", "sales-records", "FIELDS");
        assertEquals(30004, assertThrows(BizException.class, () -> app.create(caller, request)).getErrorCode());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_publication", Long.class));
        verify(permissionChecker).check("1", "42", "127.0.0.1", BusinessPermChecker.Target.of("DEMO_REPORT", null, "CREATE"));
        when(permissionChecker.check(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new BusinessPermChecker.Decision(true, null);
        });
        var result = app.create(caller, request);
        assertEquals("PENDING", result.publicationStatus());
        assertEquals(1L, result.version());
        var registered = app.register(caller, new FieldRegisterReq("protected", 0L, List.of("amount")));
        assertEquals(1L, registered.catalogVersion());
        verify(permissionChecker).check("1", "42", "127.0.0.1", BusinessPermChecker.Target.of("DEMO_REPORT", "protected", "UPDATE"));
    }

    @Test
    void shouldReplayFrozenPayloadAfterLostResponseUsingPersistedAttempt() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> metadata.createReport(1, 42, "retry", "报表", "sales-records", "FIELDS"));
        var response503 = feign.Response.builder().status(503).reason("temporarily unavailable")
            .request(feign.Request.create(feign.Request.HttpMethod.POST, "http://test.invalid/sync", java.util.Map.of(), null,
                java.nio.charset.StandardCharsets.UTF_8, null)).headers(java.util.Map.of()).build();
        when(syncClient.sync(any(), eq("test-tenant-1"), eq("test-only-secret")))
            .thenThrow(feign.FeignException.errorStatus("sync", response503))
            .thenAnswer(invocation -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return cn.ac.fage.accessmesh.common.model.R.ok(new cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp(
                    true, false, false, null, "SYNC_VERSION_REPLAYED"));
            });
        publisher.publishNext();
        assertEquals(1, publications.latest(1, "DEMO_REPORT", "retry", null).getAttempt());
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at>=updated_at+interval '10 seconds' FROM demo_publication", Boolean.class));
        assertEquals("PENDING", metadata.getReport(1, "retry").getPublicationStatus());
        jdbc.update("UPDATE demo_publication SET next_attempt_at=clock_timestamp()-interval '1 second'");
        // A new worker instance has no in-memory retry state and consumes the persisted event.
        var restarted = new cn.ac.fage.accessmesh.example.metadata.service.impl.DemoPublicationAppServiceImpl(
            publications, syncClient, credentials, permissionChecker, json, transactions);
        restarted.publishNext();
        var calls = org.mockito.ArgumentCaptor.forClass(tools.jackson.databind.JsonNode.class);
        verify(syncClient, times(2)).sync(calls.capture(), eq("test-tenant-1"), eq("test-only-secret"));
        assertEquals(calls.getAllValues().get(0), calls.getAllValues().get(1));
        assertEquals("PUBLISHED", metadata.getReport(1, "retry").getPublicationStatus());
        assertEquals(2, publications.latest(1, "DEMO_REPORT", "retry", null).getAttempt());
    }

    @Test
    void shouldNotMarkNewVersionReadyFromLateOldResponse() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> metadata.createReport(1, 42, "late", "before", "sales-records", "FIELDS"));
        when(syncClient.sync(any(), anyString(), anyString())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            tx.executeWithoutResult(s -> metadata.updateReport(1, 42, "late", 1, "after", true));
            return cn.ac.fage.accessmesh.common.model.R.ok(new cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp(true, true, false, null, null));
        });
        publisher.publishNext();
        var current = metadata.getReport(1, "late");
        assertEquals(2L, current.getVersion());
        assertEquals("PENDING", current.getPublicationStatus());
        assertEquals(2L, publications.latest(1, "DEMO_REPORT", "late", null).getSourceVersion());
    }

    @Test
    void shouldFailClosedOnStaleAndExposeOnlyLatestTenantPublication() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> metadata.createReport(1, 42, "stale", "报表", "sales-records", "FIELDS"));
        when(syncClient.sync(any(), anyString(), anyString())).thenReturn(cn.ac.fage.accessmesh.common.model.R.ok(
            new cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp(true, false, true, "STALE_VERSION", "SYNC_VERSION_STALE")));
        publisher.publishNext();
        assertEquals("FAILED", metadata.getReport(1, "stale").getPublicationStatus());
        assertEquals(0L, publications.page(2, null, 1, 20).total());
        assertEquals(1L, publications.page(1, "FAILED", 1, 20).total());
        String payload=publications.latest(1, "DEMO_REPORT", "stale", null).getPayload();
        tx.executeWithoutResult(s -> publications.retry(1, 42, "DEMO_REPORT", "stale", null));
        assertEquals(payload, publications.latest(1, "DEMO_REPORT", "stale", null).getPayload());
        assertEquals("PENDING", metadata.getReport(1, "stale").getPublicationStatus());
    }

    @Test
    void shouldPublishScopeVersionsAndRejectConflictingUpdates() {
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(s -> metadata.saveScope(1,42,"CUSTOM_SCOPE","team-t1"," BIZ ","团队",true,0));
        when(syncClient.sync(any(),anyString(),anyString())).thenReturn(cn.ac.fage.accessmesh.common.model.R.ok(
            new cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp(true,true,false,null,null)));
        publisher.publishNext();
        assertEquals("PUBLISHED",jdbc.queryForObject("SELECT publication_status FROM demo_scope",String.class));
        assertEquals(30103,assertThrows(BizException.class,() -> tx.executeWithoutResult(s ->
            metadata.saveScope(1,42,"CUSTOM_SCOPE","team-t1","BIZ","冲突",false,0))).getErrorCode());
        tx.executeWithoutResult(s -> metadata.saveScope(1,42,"CUSTOM_SCOPE","team-t1","BIZ","停用",false,1));
        publisher.publishNext();
        var sent=org.mockito.ArgumentCaptor.forClass(tools.jackson.databind.JsonNode.class);
        verify(syncClient,times(2)).sync(sent.capture(),anyString(),anyString());
        assertEquals("BIZ",sent.getAllValues().get(0).path("codeType").asText());
        assertEquals(2L,sent.getAllValues().get(1).path("syncVersion").path("sequenceNo").asLong());
        assertEquals(0,sent.getAllValues().get(1).path("status").asInt());
        assertFalse(jdbc.queryForObject("SELECT enabled FROM demo_scope",Boolean.class));
        assertEquals("PUBLISHED",jdbc.queryForObject("SELECT publication_status FROM demo_scope",String.class));
    }

    @Test
    void mapperKeepsTenantAndSoftDeleteBoundariesAndMapsUtc() {
        long first = report(1, "first");
        report(2, "second");
        var result = reports.selectByCode(1, "shared");
        assertEquals(first, result.getId());
        assertEquals("first", result.getName());
        assertEquals(LocalDateTime.of(2026, 10, 19, 0, 0), result.getCreatedAt());
        assertEquals("second", reports.selectByCode(2, "shared").getName());
        assertNull(reports.selectByCode(3, "shared"));
        jdbc.update("UPDATE demo_report SET delete_flag=id WHERE id=?", first);
        assertNull(reports.selectByCode(1, "shared"));
    }

    @Test
    void fieldLookupRequiresBothTenantAndReportOwnership() {
        long first = report(1, "first");
        long second = report(2, "second");
        jdbc.update("INSERT INTO demo_report_field(tenant_id,report_id,field_key) VALUES (1,?,'amount'),(2,?,'amount')", first, second);
        // A corrupt cross-tenant logical reference must not become readable through the Mapper.
        jdbc.update("INSERT INTO demo_report_field(tenant_id,report_id,field_key) VALUES (2,?,'remark')", first);
        var visible = fields.selectByReport(1, first);
        assertEquals(1, visible.size());
        assertEquals("amount", visible.getFirst().getFieldKey());
        assertEquals(visible.getFirst().getId().toString(), visible.getFirst().getResourceCode());
        assertTrue(fields.selectByReport(2, first).isEmpty());
        assertEquals(1, fields.selectByReport(2, second).size());
    }

    @Test
    void insertRejectsForeignDatasetAndReportReferences() {
        var foreignReport = new DemoReport();
        foreignReport.setTenantId(2L); foreignReport.setDatasetId(1L); foreignReport.setReportCode("foreign");
        foreignReport.setName("foreign"); foreignReport.setFieldResourceTypeCode("DEMO_REPORT_FIELD");
        assertEquals(0, reports.insert(foreignReport));
        assertNull(foreignReport.getId());

        long ownedReport = report(1, "owned");
        var foreignField = new DemoReportField();
        foreignField.setTenantId(2L); foreignField.setReportId(ownedReport); foreignField.setFieldKey("remark");
        assertEquals(0, fields.insert(foreignField));
        assertNull(foreignField.getId());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM demo_report_field", Long.class));
    }

    private long report(long tenant, String name) {
        return jdbc.queryForObject("INSERT INTO demo_report(tenant_id,dataset_id,report_code,name,field_resource_type_code,created_at) VALUES (?,?,'shared',?,'DEMO_REPORT_FIELD',TIMESTAMPTZ '2026-10-19 08:00:00+08') RETURNING id", Long.class, tenant, tenant, name);
    }
}
