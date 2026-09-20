package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.service.PermissionManifestAppService;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 迁移后的实际编译发布消费，补足纯 DDL 验证不能证明的 Mapper/事务兼容性。 */
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
class ManifestMigrationPublishPgIT {
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, ManifestMigrationPublishPgIT.class); }
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PermissionManifestAppService manifests;
    @AfterEach void clear() { AccessRequestContext.clear(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPublishIntoMigratedGraph_withoutConsumingOldRules(boolean hasLegacyRows) throws Exception {
        // Only this test class owns the database. Reconstruct the actual old affected tables before each migration.
        jdbc.execute("DROP TABLE IF EXISTS resource_dependency_legacy; DROP TABLE resource_dependency, permission_dependency_declaration, service_manifest_sync, resource_publication_state, sync_metadata, role_resource_permission");
        jdbc.execute(Files.readString(Path.of("src", "test", "resources", "schema", "auto-grant-before-071.sql")));
        if (hasLegacyRows) jdbc.execute("""
                INSERT INTO resource_dependency(tenant_id,resource_entity_id,depends_on_resource_entity_id,
                    required_operation_bits,auto_grant,maintain_source,description)
                VALUES(1,111,222,2,false,'ADMIN_UI','preserve only'),(1,333,444,4,true,'SDK_SCAN','never activate');
                """);
        jdbc.execute(Files.readString(Path.of("..", "docs", "ops", "auto-grant-migrate-071.sql")));
        String source = "migration-" + UUID.randomUUID();
        int value = hasLegacyRows ? 1501 : 1500;
        String code = "MIGRATION_" + value;
        jdbc.update("INSERT INTO service_config(tenant_id,service_code,name) VALUES(1,?,'migration')", source);
        jdbc.update("INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name,extra) VALUES(1,'resource_type',?,?,'migration',CAST(? AS jsonb))",
                code, value, "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"" + source + "\"}");
        jdbc.update("INSERT INTO operation_permission(tenant_id,resource_type,code,name,binary_bit,inherit_mask) VALUES(1,?,'VIEW','View',2,0)", value);
        jdbc.update("INSERT INTO resource_entity(tenant_id,resource_type,code,code_type,name) VALUES(1,?,'a','default','a'),(1,?,'b','default','b')", value, value);
        var declaration = new PermissionManifestReq.Dependency("new-declaration",
                new PermissionManifestReq.ResourceKey(code, "a", null), List.of("VIEW"),
                List.of(new PermissionManifestReq.Requirement(new PermissionManifestReq.ResourceKey(code, "b", null), List.of("VIEW"))), null);
        AccessRequestContext.bind(RequestContext.service(1L, source));
        var result = manifests.fullSync(1L, new PermissionManifestReq(1, "1", "revision-1", List.of(declaration)));
        assertThat(result.detail().appliedCount()).isEqualTo(1);
        assertThat(result.detail().failedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_dependency WHERE delete_flag=0 AND maintain_source='MANIFEST' AND declaration_id IS NOT NULL", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_dependency_legacy", Integer.class)).isEqualTo(hasLegacyRows ? 2 : 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_resource_permission", Integer.class)).isZero();
    }
}
