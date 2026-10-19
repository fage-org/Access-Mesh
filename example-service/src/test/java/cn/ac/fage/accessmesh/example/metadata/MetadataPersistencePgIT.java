package cn.ac.fage.accessmesh.example.metadata;

import cn.ac.fage.accessmesh.example.it.ExamplePgInfra;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoReportMapper;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoReportFieldMapper;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReport;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReportField;
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

    @BeforeEach
    void clearFixtures() {
        // JdbcTemplate fixtures are outside Flex's Mapper transaction and must be cleaned explicitly.
        jdbc.update("DELETE FROM demo_report_field");
        jdbc.update("DELETE FROM demo_report");
        jdbc.update("DELETE FROM demo_dataset");
        jdbc.update("INSERT INTO demo_dataset(id,tenant_id,dataset_code) VALUES (1,1,'sales-records'),(2,2,'sales-records')");
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
