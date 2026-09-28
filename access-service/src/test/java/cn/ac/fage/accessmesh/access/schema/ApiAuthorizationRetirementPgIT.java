package cn.ac.fage.accessmesh.access.schema;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class ApiAuthorizationRetirementPgIT {
    private Connection connection;

    @BeforeEach
    void oldDatabase() throws Exception {
        String url = ItInfra.createStandaloneDatabase("retire062_" + UUID.randomUUID().toString().replace("-", ""));
        connection = DriverManager.getConnection(url, ItInfra.username(), ItInfra.password());
        execute(Files.readString(Path.of("..", "docs", "design", "schema", "access-service.sql")));
        execute("""
            ALTER TABLE service_config ADD COLUMN api_auth_mode varchar(32) NOT NULL DEFAULT 'OPERATION_ADMISSION';
            INSERT INTO service_config(tenant_id,service_code,name) VALUES (1,'example-service','Example');
            INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name)
                VALUES(2,'resource_type','API',700,'Tenant 2 API');
            INSERT INTO resource_entity(id,tenant_id,resource_type,code,name)
                SELECT 90001,tenant_id,type_value,'test-api','API registration' FROM type_definition
                WHERE tenant_id=1 AND type_key='resource_type' AND type_code='API';
            INSERT INTO resource_api_mapping(tenant_id,resource_entity_id,service_code,http_method,path_pattern)
                VALUES(1,90001,'example-service','POST','/api/example/report/view');
            INSERT INTO role_resource_permission(id,tenant_id,abstract_role_id,resource_type,granted_bits,scope_all,can_grant,grant_source)
                SELECT 91001,1,1,type_value,16,true,false,'MANUAL' FROM type_definition
                WHERE tenant_id=1 AND type_key='resource_type' AND type_code='API';
            INSERT INTO role_resource_permission(id,tenant_id,abstract_role_id,resource_type,granted_bits,scope_all,can_grant,grant_source)
                VALUES(91002,2,2,700,16,true,true,'AUTHORITY_ROOT');
            INSERT INTO role_resource_permission(id,tenant_id,abstract_role_id,resource_type,resource_entity_id,granted_bits,grant_source)
                SELECT 91003,1,1,type_value,90001,16,'AUTO_DEP' FROM type_definition
                WHERE tenant_id=1 AND type_key='resource_type' AND type_code='API';
            INSERT INTO role_resource_permission(id,tenant_id,abstract_role_id,resource_type,granted_bits,scope_all)
                VALUES(91004,1,1,700,2,true);
            """);
    }

    @AfterEach
    void close() throws Exception { if (connection != null) connection.close(); }

    @Test
    void shouldRetireAllSourcesAcrossTenantsAndRestoreExactRows_whenMigrationAndRollbackRun() throws Exception {
        String grants = snapshot("role_resource_permission");
        String service = snapshot("service_config");
        String resources = snapshot("resource_entity");
        String mappings = snapshot("resource_api_mapping");
        String business = scalar("SELECT to_jsonb(p)::text FROM role_resource_permission p WHERE id=91004");
        migrate();
        assertThat(scalar("SELECT count(*) FROM accessmesh_retirement_062.grants")).isEqualTo("3");
        assertThat(scalar("SELECT count(*) FROM role_resource_permission WHERE delete_flag=0")).isEqualTo("1");
        assertThat(scalar("SELECT to_jsonb(p)::text FROM role_resource_permission p WHERE id=91004")).isEqualTo(business);
        assertThat(snapshot("resource_entity")).isEqualTo(resources);
        assertThat(snapshot("resource_api_mapping")).isEqualTo(mappings);
        assertThat(scalar("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
            + "AND table_name='service_config' AND column_name='api_auth_mode'")).isEqualTo("0");
        rollback();
        assertThat(snapshot("role_resource_permission")).isEqualTo(grants);
        assertThat(snapshot("service_config")).isEqualTo(service);
        assertThat(scalar("SELECT rolled_back_at IS NOT NULL FROM accessmesh_retirement_062.manifest")).isEqualTo("t");
    }

    @Test
    void shouldAbortWithoutChangingData_whenBusinessChildReferencesApiParent() throws Exception {
        execute("UPDATE role_resource_permission SET depend_on=91001 WHERE id=91004");
        String before = snapshot("role_resource_permission");
        assertThatThrownBy(this::migrate).isInstanceOf(SQLException.class).hasMessageContaining("T062_BUSINESS_CHILD_REFERENCE");
        execute("ROLLBACK");
        assertThat(snapshot("role_resource_permission")).isEqualTo(before);
        assertThat(scalar("SELECT to_regnamespace('accessmesh_retirement_062') IS NULL")).isEqualTo("t");
    }

    @Test
    void shouldAbort_whenAnyActiveServiceHasNotMigrated() throws Exception {
        execute("UPDATE service_config SET api_auth_mode='LEGACY_API'");
        assertThatThrownBy(this::migrate).isInstanceOf(SQLException.class).hasMessageContaining("T062_UNMIGRATED_SERVICE");
        execute("ROLLBACK");
        assertThat(scalar("SELECT count(*) FROM role_resource_permission WHERE delete_flag=0")).isEqualTo("4");
    }

    @Test
    void shouldRollbackAllEarlierWrites_whenDroppingModeColumnFails() throws Exception {
        String before = snapshot("role_resource_permission");
        execute("CREATE VIEW mode_dependency AS SELECT api_auth_mode FROM service_config");
        assertThatThrownBy(this::migrate).isInstanceOf(SQLException.class);
        execute("ROLLBACK");
        assertThat(snapshot("role_resource_permission")).isEqualTo(before);
        assertThat(scalar("SELECT to_regnamespace('accessmesh_retirement_062') IS NULL")).isEqualTo("t");
    }

    @Test
    void shouldPreserveLedger_whenMigrationIsRepeated() throws Exception {
        migrate();
        String ledger = snapshot("accessmesh_retirement_062.grants");
        assertThatThrownBy(this::migrate).isInstanceOf(SQLException.class).hasMessageContaining("T062_ALREADY_RECORDED");
        execute("ROLLBACK");
        assertThat(snapshot("accessmesh_retirement_062.grants")).isEqualTo(ledger);
    }

    @Test
    void shouldRejectRollback_whenRetiredRowWasModified() throws Exception {
        migrate();
        execute("UPDATE role_resource_permission SET granted_bits=32 WHERE id=91001");
        assertThatThrownBy(this::rollback).isInstanceOf(SQLException.class).hasMessageContaining("T062_ROLLBACK_ROW_CHANGED");
        execute("ROLLBACK");
        assertThat(scalar("SELECT granted_bits FROM role_resource_permission WHERE id=91001")).isEqualTo("32");
    }

    private void migrate() throws Exception { execute(Files.readString(Path.of("..", "docs", "ops", "api-authorization-retire-062.sql"))); }
    private void rollback() throws Exception { execute(Files.readString(Path.of("..", "docs", "ops", "api-authorization-rollback-062.sql"))); }
    private void execute(String sql) throws SQLException { try (var s = connection.createStatement()) { s.execute(sql); } }
    private String scalar(String sql) throws SQLException {
        try (var s = connection.createStatement(); var rows = s.executeQuery(sql)) { rows.next(); return rows.getString(1); }
    }
    private String snapshot(String table) throws SQLException {
        return scalar("SELECT jsonb_agg(to_jsonb(r) ORDER BY id)::text FROM " + table + " r");
    }
}
