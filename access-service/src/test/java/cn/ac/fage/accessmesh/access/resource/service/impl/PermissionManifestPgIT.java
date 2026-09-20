package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.service.PermissionManifestAppService;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.Dependency;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.Requirement;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.ResourceKey;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 声明事务与真实编译存储；角色物化由 072 验证。 */
@Tag("testcontainers")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false"
})
class PermissionManifestPgIT {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, PermissionManifestPgIT.class); }
    private static int nextType = 930;
    @Autowired private PermissionManifestAppService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private ServiceCredentialDomainService credentials;
    @AfterEach void cleanup() { AccessRequestContext.clear(); }

    @Test
    void shouldPublishThroughCredentialHttpRoute_withoutUserApiGrant() throws Exception {
        Fixture f = fixture(true);
        var credential = credentials.issue(1L, f.service(), null, 100L);
        var result = mvc.perform(post("/api/access/integration/permission-manifest/full-sync")
                .header("X-Credential-Id", credential.entity().getCredentialId())
                .header("X-Credential-Secret", credential.plainSecret())
                .header("X-Tenant-Id", "1").header("X-Service-Code", f.service())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("1", List.of(dependency(f, "one", "b", "VIEW", null))))))
                .andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(200);
        var envelope = json.readTree(result.getContentAsString());
        assertThat(envelope.path("code").asInt()).isEqualTo(200);
        assertThat(envelope.path("data").path("detail").path("appliedCount").asInt()).isEqualTo(1);
        assertThat(edgeCount(f)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code='POST:/api/access/integration/permission-manifest/full-sync' AND delete_flag=0",
                Integer.class)).isZero();
    }

    @Test
    void shouldMergeDeclarationsAndPreserveGraphOnRepeat_whileSavingDescriptions() {
        Fixture f = fixture(true);
        var original = request("1", List.of(dependency(f, "first", "b", "VIEW", "initial"),
                dependency(f, "second", "b", "UPDATE", null)));
        assertThat(publish(f, original).detail().appliedCount()).isEqualTo(2);
        assertThat(edgeCount(f)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT required_operation_bits FROM resource_dependency WHERE owner_service_code=? AND delete_flag=0",
                Long.class, f.service())).isEqualTo(6);
        long edgeId = jdbc.queryForObject("SELECT id FROM resource_dependency WHERE owner_service_code=? AND delete_flag=0", Long.class, f.service());
        assertThat(publish(f, original).detail().staleCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT description FROM resource_dependency WHERE owner_service_code=? AND delete_flag=0", String.class, f.service())).isEqualTo("initial");
        var renamed = request("2", List.of(dependency(f, "first", "b", "VIEW", "renamed"),
                dependency(f, "second", "b", "UPDATE", null)));
        assertThat(publish(f, renamed).applied()).isTrue();
        assertThat(jdbc.queryForObject("SELECT id FROM resource_dependency WHERE owner_service_code=? AND delete_flag=0", Long.class, f.service())).isEqualTo(edgeId);
        assertThat(jdbc.queryForObject("SELECT description FROM resource_dependency WHERE owner_service_code=? AND delete_flag=0", String.class, f.service())).isEqualTo("renamed");
        assertThat(jdbc.queryForObject("SELECT declaration_payload->>'description' FROM permission_dependency_declaration WHERE source_service=? AND declaration_key='first' AND delete_flag=0",
                String.class, f.service())).isEqualTo("renamed");
        var stale = publish(f, original);
        assertThat(stale.stale()).isTrue();
        assertThat(stale.detail().staleCount()).isEqualTo(2);
        assertThat(stale.detail().failedCount()).isZero();
        assertThat(stale.detail().itemResults()).hasSize(2).allMatch(item -> item.stale() && !item.applied());
        assertThat(publish(f, request("2", List.of())).reason()).isEqualTo("PUBLICATION_GENERATION_CONFLICT");
        assertThat(edgeCount(f)).isEqualTo(1);
    }

    @Test
    void shouldRetryPartialWithOriginalGeneration_afterMissingResourceArrives() {
        Fixture f = fixture(false);
        resource(f, "c");
        var req = request("10", List.of(dependency(f, "missing", "b", "VIEW", null),
                dependency(f, "present", "c", "VIEW", null)));
        var first = publish(f, req);
        assertThat(first.detail().appliedCount()).isEqualTo(1);
        assertThat(first.detail().failedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sync_status FROM service_manifest_sync WHERE source_service=?", String.class, f.service())).isEqualTo("PARTIAL");
        resource(f, "b");
        assertThat(publish(f, req).detail().failedCount()).isZero();
        assertThat(edgeCount(f)).isEqualTo(2);
        jdbc.update("UPDATE resource_entity SET delete_flag=id WHERE tenant_id=1 AND resource_type=? AND code='b'", f.type());
        jdbc.update("UPDATE service_manifest_sync SET is_dirty=true WHERE source_service=?", f.service());
        assertThat(publish(f, req).detail().failedCount()).isEqualTo(1);
        assertThat(edgeCount(f)).isEqualTo(1);
    }

    @Test
    void shouldClearOnlyCurrentService_onExplicitEmptySnapshot() {
        Fixture first = fixture(true);
        Fixture other = fixture(true);
        assertThat(publish(first, request("1", List.of(dependency(first, "one", "b", "VIEW", null)))).applied()).isTrue();
        assertThat(publish(other, request("1", List.of(dependency(other, "one", "b", "VIEW", null)))).applied()).isTrue();
        var cleared = publish(first, request("2", List.of()));
        assertThat(cleared.detail().deactivatedCount()).isEqualTo(1);
        assertThat(edgeCount(first)).isZero();
        assertThat(edgeCount(other)).isEqualTo(1);
    }

    @Test
    void shouldRollbackDeclarationsAndGraph_whenFinalStateWriteFails() {
        Fixture f = fixture(true);
        jdbc.execute("""
                CREATE FUNCTION reject_manifest_state() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected manifest state failure'; END $$;
                CREATE TRIGGER reject_manifest_state BEFORE INSERT ON service_manifest_sync
                FOR EACH ROW EXECUTE FUNCTION reject_manifest_state();
                """);
        try {
            assertThatThrownBy(() -> publish(f, request("1", List.of(dependency(f, "one", "b", "VIEW", null)))))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(edgeCount(f)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM permission_dependency_declaration WHERE source_service=?", Integer.class, f.service())).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_manifest_state ON service_manifest_sync; DROP FUNCTION reject_manifest_state()");
        }
    }

    @Test
    void shouldKeepNewerEmptySnapshot_whenPublicationsRace() throws Exception {
        Fixture f = fixture(true);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var old = pool.submit(() -> { await(start); return publish(f, request("1", List.of(dependency(f, "one", "b", "VIEW", null)))); });
            var newer = pool.submit(() -> { await(start); return publish(f, request("2", List.of())); });
            start.countDown();
            var oldResult = old.get(20, TimeUnit.SECONDS);
            assertThat(oldResult.applied() || oldResult.stale()).isTrue();
            assertThat(newer.get(20, TimeUnit.SECONDS).applied()).isTrue();
        }
        assertThat(edgeCount(f)).isZero();
        assertThat(jdbc.queryForObject("SELECT publication_generation FROM service_manifest_sync WHERE source_service=?", Long.class, f.service())).isEqualTo(2);
    }

    @Test
    void shouldRejectUntrustedAndDisabledService_withoutPublicationRows() {
        Fixture f = fixture(true);
        var req = request("1", List.of(dependency(f, "one", "b", "VIEW", null)));
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        assertThat(service.fullSync(1L, req).reason()).isEqualTo("SERVICE_IDENTITY_REQUIRED");
        jdbc.update("UPDATE service_config SET status=0 WHERE service_code=?", f.service());
        assertThat(publish(f, req).reason()).isEqualTo("SERVICE_NOT_ENABLED");
        assertThat(edgeCount(f)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM service_manifest_sync WHERE source_service=?", Integer.class, f.service())).isZero();
    }

    @Test
    void shouldPublishLargeSnapshotWithinOneTransaction_withoutParameterOverflow() {
        Fixture f = fixture(false);
        int targetCount = 4370; // 单条 saveAll 绑定参数 4370×15 超过 PostgreSQL 驱动上限。
        jdbc.update("""
                INSERT INTO resource_entity(tenant_id,resource_type,code,code_type,name)
                SELECT 1,?,'bulk-' || i,'default','bulk' FROM generate_series(1,?) i
                """, f.type(), targetCount);
        var targets = java.util.stream.IntStream.rangeClosed(1, targetCount).mapToObj(i ->
                new Requirement(new ResourceKey(f.typeCode(), "bulk-" + i, null), List.of("VIEW"))).toList();
        var req = request("1", List.of(new Dependency("many", new ResourceKey(f.typeCode(), "a", null),
                List.of("VIEW"), targets, null)));
        assertThat(publish(f, req).detail().appliedCount()).isEqualTo(targetCount);
        assertThat(edgeCount(f)).isEqualTo(targetCount);
        assertThat(publish(f, request("2", List.of())).detail().deactivatedCount()).isEqualTo(targetCount);
        assertThat(edgeCount(f)).isZero();
    }

    private static void await(CountDownLatch latch) throws InterruptedException { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
    private PermissionManifestReq request(String generation, List<Dependency> dependencies) { return new PermissionManifestReq(1, generation, "release", dependencies); }
    private Dependency dependency(Fixture f, String key, String target, String operation, String description) {
        return new Dependency(key, new ResourceKey(f.typeCode(), "a", null), List.of("VIEW"),
                List.of(new Requirement(new ResourceKey(f.typeCode(), target, null), List.of(operation))), description);
    }
    private SyncResultResp publish(Fixture f, PermissionManifestReq request) {
        AccessRequestContext.bind(RequestContext.service(1L, f.service()));
        try { return service.fullSync(1L, request); }
        finally { AccessRequestContext.clear(); }
    }
    private long edgeCount(Fixture f) {
        return jdbc.queryForObject("SELECT count(*) FROM resource_dependency WHERE tenant_id=1 AND owner_service_code=? AND delete_flag=0", Long.class, f.service());
    }
    private Fixture fixture(boolean targetExists) {
        int type = nextType++;
        String serviceCode = "manifest-" + UUID.randomUUID();
        String typeCode = "MANIFEST_" + type;
        jdbc.update("INSERT INTO service_config(tenant_id,service_code,name) VALUES (1,?,'manifest')", serviceCode);
        jdbc.update("INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name,extra) VALUES (1,'resource_type',?,?,'manifest',CAST(? AS jsonb))",
                typeCode, type, "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"" + serviceCode + "\"}");
        jdbc.update("INSERT INTO operation_permission(tenant_id,resource_type,code,name,binary_bit) VALUES (1,?,'VIEW','view',2),(1,?,'UPDATE','update',4)", type, type);
        var result = new Fixture(serviceCode, type, typeCode);
        resource(result, "a");
        if (targetExists) resource(result, "b");
        return result;
    }
    private void resource(Fixture f, String code) {
        // 停用资源仍参与编译，存在性仅以未软删判断。
        jdbc.update("INSERT INTO resource_entity(tenant_id,resource_type,code,code_type,name,status) VALUES (1,?,?,'default',?,0)", f.type(), code, code);
    }
    private record Fixture(String service, int type, String typeCode) {}
}
