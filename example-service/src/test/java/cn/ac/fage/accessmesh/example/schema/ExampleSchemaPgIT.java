package cn.ac.fage.accessmesh.example.schema;

import cn.ac.fage.accessmesh.example.it.ExamplePgInfra;
import cn.ac.fage.accessmesh.testsupport.PgTestInfra;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Original example DDL against PostgreSQL; schema tests do not replace API permission evidence. */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class ExampleSchemaPgIT {
    private static Connection connection;

    @BeforeAll
    static void schema() throws Exception {
        String url = ExamplePgInfra.PG.createStandaloneDatabase("example_schema_contract");
        connection = DriverManager.getConnection(url, PgTestInfra.USERNAME, PgTestInfra.PASSWORD);
        try (var statement = connection.createStatement()) {
            statement.execute(Files.readString(ExamplePgInfra.DDL));
        }
    }

    @BeforeEach void begin() throws SQLException { connection.setAutoCommit(false); }
    @AfterEach void rollback() throws SQLException { connection.rollback(); connection.setAutoCommit(true); }
    @AfterAll static void close() throws SQLException { if (connection != null) connection.close(); }

    @Test
    void startsWithOnlyExampleTablesAndNoImplicitBusinessData() throws SQLException {
        assertEquals(0L, number("SELECT count(*) FROM demo_dataset"));
        assertEquals(0L, number("SELECT count(*) FROM demo_report"));
        assertEquals(0L, number("SELECT count(*) FROM demo_record"));
        assertEquals(0L, number("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('sys_user','resource_entity','role_resource_permission','demo_datasource')"));
    }

    @Test
    void reportCodesAreUniqueOnlyWithinTheTenant() throws SQLException {
        report(1, "same");
        report(2, "same");
        var point = connection.setSavepoint();
        SQLException failure = assertThrows(SQLException.class, () -> report(1, "same"));
        assertEquals("23505", failure.getSQLState());
        connection.rollback(point);
        assertEquals(2L, number("SELECT count(*) FROM demo_report WHERE report_code='same'"));
    }

    @Test
    void fieldsBelongToEachReportAndRecreatedNamesReceiveNewIdentity() throws SQLException {
        long firstReport = report(1, "first");
        long secondReport = report(1, "second");
        long firstField = field(firstReport, "amount");
        long secondField = field(secondReport, "amount");
        var point = connection.setSavepoint();
        assertEquals("23505", assertThrows(SQLException.class, () -> field(firstReport, "amount")).getSQLState());
        connection.rollback(point);
        execute("UPDATE demo_report_field SET delete_flag=id WHERE id=" + firstField);
        long replacement = field(firstReport, "amount");
        assertNotEquals(firstField, replacement);
        assertEquals(3L, number("SELECT count(DISTINCT resource_code) FROM demo_report_field WHERE id IN (" + firstField + "," + secondField + "," + replacement + ")"));
    }

    @Test
    void revokedImportStillOwnsItsRequestKeyAndOtherTenantsAreIndependent() throws SQLException {
        batch(1, "11111111-1111-4111-8111-111111111111");
        execute("UPDATE demo_import_batch SET status='REVOKED' WHERE tenant_id=1");
        batch(2, "11111111-1111-4111-8111-111111111111");
        var point = connection.setSavepoint();
        assertEquals("23505", assertThrows(SQLException.class,
            () -> batch(1, "11111111-1111-4111-8111-111111111111")).getSQLState());
        connection.rollback(point);
        batch(1, "22222222-2222-4222-8222-222222222222");
    }

    @Test
    void recordsPreserveExactAmountsDatesAndUtcInstantsAndPermitReimportAfterSoftDelete() throws SQLException {
        String insert = "INSERT INTO demo_record (tenant_id,batch_id,record_code,owner_user_id,team_code,department_code,region_code,sale_date,product_name,amount,created_at) VALUES (1,1,'A',42,'T1','D1','R1',DATE '2026-10-19','sample',123.45,TIMESTAMPTZ '2026-10-19 08:00:00+08')";
        execute(insert);
        var point = connection.setSavepoint();
        assertEquals("23505", assertThrows(SQLException.class, () -> execute(insert)).getSQLState());
        connection.rollback(point);
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT amount,sale_date,created_at FROM demo_record WHERE tenant_id=1 AND record_code='A'")) {
            assertTrue(rows.next());
            assertEquals(new BigDecimal("123.45"), rows.getBigDecimal("amount"));
            assertEquals(LocalDate.of(2026, 10, 19), rows.getObject("sale_date", LocalDate.class));
            assertEquals(OffsetDateTime.parse("2026-10-19T00:00:00Z").toInstant(), rows.getObject("created_at", OffsetDateTime.class).toInstant());
        }
        execute("UPDATE demo_record SET delete_flag=id WHERE tenant_id=1 AND record_code='A'");
        execute(insert);
        assertEquals(1L, number("SELECT count(*) FROM demo_record WHERE tenant_id=1 AND record_code='A' AND delete_flag=0"));
    }

    @Test
    void scheduledExecutionKeysAndResultStatesHavePersistentConstraints() throws SQLException {
        String insert = "INSERT INTO demo_execution(tenant_id,execution_key,task_id,task_version,trigger_kind,scheduled_time,request_payload,created_by) VALUES (1,gen_random_uuid(),9,1,'CRON',TIMESTAMPTZ '2026-10-19 00:00:00Z','{}',42)";
        execute(insert);
        var point = connection.setSavepoint();
        assertEquals("23505", assertThrows(SQLException.class, () -> execute(insert)).getSQLState());
        connection.rollback(point);
        assertEquals("23514", assertThrows(SQLException.class,
            () -> execute("UPDATE demo_execution SET status='SUCCESS' WHERE tenant_id=1")).getSQLState());
        connection.rollback(point);
        assertEquals("23514", assertThrows(SQLException.class,
            () -> execute("UPDATE demo_execution SET task_version=NULL WHERE tenant_id=1")).getSQLState());
        connection.rollback(point);
        execute("UPDATE demo_execution SET status='SUCCESS',result_bytes=decode('61','hex'),result_row_count=1,expires_at=now()+interval '7 days' WHERE tenant_id=1");
        var successful = connection.setSavepoint();
        assertEquals("23514", assertThrows(SQLException.class,
            () -> execute("UPDATE demo_execution SET result_row_count=NULL WHERE tenant_id=1")).getSQLState());
        connection.rollback(successful);
        execute("UPDATE demo_execution SET status='EXPIRED',result_bytes=NULL WHERE tenant_id=1");
        assertEquals(1L, number("SELECT count(*) FROM demo_execution WHERE status='EXPIRED' AND result_bytes IS NULL"));
    }

    private static long report(long tenant, String code) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO demo_report(tenant_id,dataset_id,report_code,name,field_resource_type_code) VALUES (?,1,?,'sample','DEMO_REPORT_FIELD') RETURNING id")) {
            statement.setLong(1, tenant); statement.setString(2, code);
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private static long field(long report, String key) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO demo_report_field(tenant_id,report_id,field_key) VALUES (1,?,?) RETURNING id")) {
            statement.setLong(1, report); statement.setString(2, key);
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private static void batch(long tenant, String key) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO demo_import_batch(tenant_id,report_id,import_key,file_sha256,imported_fields,record_count,policy_version,catalog_version,created_by) VALUES (?,1,?::uuid,repeat('a',64),'[1]',1,1,1,42)")) {
            statement.setLong(1, tenant); statement.setString(2, key); statement.executeUpdate();
        }
    }

    private static void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static long number(String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getLong(1);
        }
    }
}
