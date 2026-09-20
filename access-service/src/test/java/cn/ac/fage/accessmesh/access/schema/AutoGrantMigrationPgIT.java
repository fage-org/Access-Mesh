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

/** 真实迁移前表结构夹具；不把测试库结果当作部署库盘点。 */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class AutoGrantMigrationPgIT {
    private static final Path SCRIPT = Path.of("..", "docs", "ops", "auto-grant-migrate-071.sql");
    private Connection connection;

    @BeforeEach void oldDatabase() throws Exception {
        String url = ItInfra.createStandaloneDatabase("migration_" + UUID.randomUUID().toString().replace("-", ""));
        connection = DriverManager.getConnection(url, ItInfra.username(), ItInfra.password());
        execute(Files.readString(Path.of("src", "test", "resources", "schema", "auto-grant-before-071.sql")));
    }
    @AfterEach void close() throws Exception { if (connection != null) connection.close(); }

    @Test void shouldUpgradeEmptyOldTables_withCanonicalNewStructures() throws Exception {
        migrate();
        assertThat(scalar("SELECT count(*) FROM resource_dependency_legacy")).isEqualTo("0");
        assertThat(scalar("SELECT count(*) FROM resource_dependency")).isEqualTo("0");
        assertThat(scalar("SELECT count(*) FROM information_schema.columns WHERE table_name='resource_dependency' AND column_name='auto_grant'")).isEqualTo("0");
        String current = Files.readString(Path.of("..", "docs", "design", "schema", "access-service.sql"));
        int start = current.indexOf("CREATE TABLE resource_dependency (");
        String structures = current.substring(start, current.indexOf("-- -----------------------------------------------------------------------------", start)).strip();
        assertThat(Files.readString(SCRIPT)).contains(structures);
        assertThat(scalar("SELECT pg_get_serial_sequence('resource_dependency','id') = pg_get_serial_sequence('resource_dependency_legacy','id')")).isEqualTo("f");
    }

    @Test void shouldPreserveEveryLegacyFieldAndDeletedRow_withoutActivatingAnyEdge() throws Exception {
        execute("""
                INSERT INTO resource_dependency(tenant_id,resource_entity_id,depends_on_resource_entity_id,
                    source_operation_bits,required_operation_bits,auto_grant,owner_service_code,maintain_source,
                    sync_key,description,created_by,updated_by,deleted_by,created_at,updated_at,deleted_at,delete_flag)
                SELECT 1,100+i,200+i,2,4,i%2=0,'old-service',source,'old-key-' || i,'old-description-' || i,
                    10,11,12,'2026-01-01Z','2026-01-02Z',CASE WHEN i=4 THEN '2026-01-03Z'::timestamptz END,
                    CASE WHEN i=4 THEN 4 ELSE 0 END
                FROM unnest(ARRAY['ADMIN_UI','SDK_SCAN','MANIFEST','SERVICE_SYNC']) WITH ORDINALITY AS t(source,i);
                INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_entity_id,resource_type,
                    granted_bits,grant_source,grant_dep_id,delete_flag) VALUES(1,1,1,1,2,'AUTO_DEP',4,1);
                """);
        String before = scalar("SELECT jsonb_agg(to_jsonb(d) ORDER BY id)::text FROM resource_dependency d");
        String oldGrant = scalar("SELECT to_jsonb(r)::text FROM role_resource_permission r");
        migrate();
        assertThat(scalar("SELECT jsonb_agg(to_jsonb(d) ORDER BY id)::text FROM resource_dependency_legacy d")).isEqualTo(before);
        assertThat(scalar("SELECT to_jsonb(r)::text FROM role_resource_permission r")).isEqualTo(oldGrant);
        for (String table : new String[]{"resource_dependency", "permission_dependency_declaration", "service_manifest_sync"}) {
            assertThat(scalar("SELECT count(*) FROM " + table)).isEqualTo("0");
        }
    }

    @Test void shouldAbortBeforeMutation_whenActiveAutoGrantExists() throws Exception {
        execute("INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_entity_id,resource_type,granted_bits,grant_source,grant_dep_id) VALUES(1,1,1,1,2,'AUTO_DEP',999)");
        String before = scalar("SELECT to_jsonb(r)::text FROM role_resource_permission r");
        assertThatThrownBy(this::migrate).isInstanceOf(SQLException.class).hasMessageContaining("ACTIVE_AUTO_DEP");
        execute("ROLLBACK");
        assertOldSchemaUntouched();
        assertThat(scalar("SELECT to_jsonb(r)::text FROM role_resource_permission r")).isEqualTo(before);
    }

    @Test void shouldAbortForUnclassifiedActiveGrantSource() throws Exception {
        execute("INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_entity_id,resource_type,granted_bits,grant_source) VALUES(1,1,1,1,2,'IMPORTED_UNKNOWN')");
        assertThatThrownBy(this::migrate).isInstanceOf(SQLException.class).hasMessageContaining("UNKNOWN_GRANT_SOURCE");
        execute("ROLLBACK");
        assertOldSchemaUntouched();
    }

    @Test void shouldRejectUnexpectedSchemaAndExistingTarget_withoutOverwriting() throws Exception {
        execute("ALTER TABLE resource_dependency ADD COLUMN unexpected text");
        assertThatThrownBy(this::migrate).isInstanceOf(SQLException.class).hasMessageContaining("SCHEMA_MISMATCH");
        execute("ROLLBACK");
        assertOldSchemaUntouched();
        execute("ALTER TABLE resource_dependency DROP COLUMN unexpected; CREATE TABLE resource_dependency_legacy(marker text); INSERT INTO resource_dependency_legacy VALUES('preserve')");
        assertThatThrownBy(this::migrate).isInstanceOf(SQLException.class).hasMessageContaining("TARGET_EXISTS");
        execute("ROLLBACK");
        assertThat(scalar("SELECT marker FROM resource_dependency_legacy")).isEqualTo("preserve");
    }

    @Test void shouldRollbackEarlierRenames_whenLateDdlFails() throws Exception {
        execute("CREATE TABLE uk_service_manifest_sync(marker text)");
        assertThatThrownBy(this::migrate).isInstanceOf(SQLException.class).hasMessageContaining("uk_service_manifest_sync");
        execute("ROLLBACK");
        assertOldSchemaUntouched();
    }

    private void assertOldSchemaUntouched() throws Exception {
        assertThat(scalar("SELECT to_regclass('resource_dependency_legacy') IS NULL")).isEqualTo("t");
        assertThat(scalar("SELECT to_regclass('permission_dependency_declaration') IS NULL")).isEqualTo("t");
        assertThat(scalar("SELECT count(*) FROM information_schema.columns WHERE table_name='resource_dependency' AND column_name='auto_grant'")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM information_schema.columns WHERE table_name='sync_metadata' AND column_name='last_publication_generation'")).isEqualTo("0");
    }
    private void migrate() throws Exception { execute(Files.readString(SCRIPT)); }
    private void execute(String sql) throws SQLException { try (var statement = connection.createStatement()) { statement.execute(sql); } }
    private String scalar(String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) { rows.next(); return rows.getString(1); }
    }
}
