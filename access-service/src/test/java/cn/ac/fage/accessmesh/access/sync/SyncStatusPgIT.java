package cn.ac.fage.accessmesh.access.sync;

import cn.ac.fage.accessmesh.access.bootstrap.AccessBootstrapInitializer;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static cn.ac.fage.accessmesh.access.it.GatewayTestSignatures.hmac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 已应用状态的真实 HTTP/SQL：空 FULL、租户隔离与门禁。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "access.platform.bootstrap.enabled=false",
    "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false", "logging.level.cn.ac.fage.accessmesh=WARN"
})
class SyncStatusPgIT {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.redis.core.StringRedisTemplate tenantFixtureRedis;
    @org.junit.jupiter.api.BeforeEach
    void enableTenantFixture() {
        cn.ac.fage.accessmesh.access.it.TenantTestSupport.enableFixture(jdbc,tenantFixtureRedis,1L);
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, SyncStatusPgIT.class); }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccessBootstrapInitializer bootstrap;
    @Value("${perm.internal-secret}") String internalSecret;
    @Value("${perm.signature.secret}") String signatureSecret;

    @Test
    void emptyAppliedResourceFullIsVisibleAndOtherTenantIsExcluded() throws Exception {
        bootstrap.initialize(1L, "SyncStatus123!");
        long admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE tenant_id=1 AND username='admin' AND delete_flag=0", Long.class);
        String scope = SyncKeyCodecUtil.resourceEntityScopeKey("REPORT");
        String hash = SyncKeyCodecUtil.sha256Hex(scope);
        jdbc.update("INSERT INTO resource_publication_state(tenant_id,source_service,scope_key,scope_key_hash,max_generation,last_full_generation,last_full_payload_hash,last_full_status) VALUES (1,'t65',?,?,7,7,?,'SUCCESS'),(2,'t65',?,?,8,8,?,'PARTIAL')",
            scope, hash, "a".repeat(64), scope, hash, "b".repeat(64));
        String token = StpUtil.getStpLogic().createLoginSession(admin);
        cn.ac.fage.accessmesh.access.it.TenantTestSupport.stampSession(token,tenantFixtureRedis,1L);
        StpUtil.getSessionByLoginId(admin).set("tenantId", 1L);
        long now = System.currentTimeMillis() / 1000;
        try {
            var response = mvc.perform(post("/api/access/sync-status/list")
                    .header("Authorization", "Bearer " + token).header("X-Internal-Secret", internalSecret)
                    .header("X-User-Id", admin).header("X-Tenant-Id", "1")
                    .header("X-Signature-Timestamp", now).header("X-User-Signature", hmac(signatureSecret, String.valueOf(admin), "1", now))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"sourceService\":\"t65\",\"pageNum\":1,\"pageSize\":20}"))
                .andExpect(status().isOk()).andReturn().getResponse();
            var data = new ObjectMapper().readTree(response.getContentAsString()).path("data");
            assertThat(data.path("total").asInt()).isEqualTo(1);
            assertThat(data.path("items").get(0).path("trackedItems").asInt()).isZero();
            assertThat(data.path("items").get(0).path("lastFullStatus").asText()).isEqualTo("SUCCESS");
            assertThat(data.path("items").get(0).path("maxGeneration").asText()).isEqualTo("7");
        } finally {
            StpUtil.logout(admin);
        }
    }

    @Test
    void metadataAggregatesScopesAndUnprivilegedUserIsDenied() throws Exception {
        bootstrap.initialize(1L, "SyncStatus123!");
        long admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE tenant_id=1 AND username='admin' AND delete_flag=0", Long.class);
        String source = "t65-users";
        String scope = SyncKeyCodecUtil.abstractUserScopeKey("EMPLOYEE");
        for (String external : new String[]{"one", "two"}) {
            String business = SyncKeyCodecUtil.abstractUserBusinessKey("EMPLOYEE", external);
            String key = SyncKeyCodecUtil.syncKey(source, "ABSTRACT_USER", business);
            jdbc.update("INSERT INTO sync_metadata(tenant_id,entity_kind,source_service,scope_key,scope_key_hash,business_key,business_key_hash,sync_key,sync_key_hash,target_status,last_sync_occurred_at,last_sync_sequence_no) VALUES (1,'ABSTRACT_USER',?,?,?,?,?,?,?,'ACTIVE',now(),1)",
                source, scope, SyncKeyCodecUtil.sha256Hex(scope), business, SyncKeyCodecUtil.sha256Hex(business), key, SyncKeyCodecUtil.sha256Hex(key));
        }
        var data = query(admin, source, 200).path("data");
        assertThat(data.path("total").asInt()).isEqualTo(1);
        assertThat(data.path("items").get(0).path("trackedItems").asInt()).isEqualTo(2);
        assertThat(data.path("items").get(0).path("lastFullStatus").isNull()).isTrue();
        long user = 900065L;
        jdbc.update("INSERT INTO abstract_user(id,tenant_id,user_type,external_id,name,enabled) VALUES (?,1,3,?,'sync reader',true)", user, String.valueOf(user));
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,name,user_type) VALUES (?,1,'t65-no-view',?,'sync reader',3)", user, cn.dev33.satoken.secure.BCrypt.hashpw("SyncReader123!"));
        assertThat(query(user, source, 403).path("code").asInt()).isEqualTo(403);
    }

    private com.fasterxml.jackson.databind.JsonNode query(long user, String source, int expectedStatus) throws Exception {
        String token = StpUtil.getStpLogic().createLoginSession(user);
        cn.ac.fage.accessmesh.access.it.TenantTestSupport.stampSession(token,tenantFixtureRedis,1L);
        StpUtil.getSessionByLoginId(user).set("tenantId", 1L);
        long now = System.currentTimeMillis() / 1000;
        try {
            var response = mvc.perform(post("/api/access/sync-status/list")
                    .header("Authorization", "Bearer " + token).header("X-Internal-Secret", internalSecret)
                    .header("X-User-Id", user).header("X-Tenant-Id", "1")
                    .header("X-Signature-Timestamp", now).header("X-User-Signature", hmac(signatureSecret, String.valueOf(user), "1", now))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"sourceService\":\"" + source + "\",\"pageNum\":1,\"pageSize\":20}"))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse();
            return new ObjectMapper().readTree(response.getContentAsString());
        } finally {
            StpUtil.logout(user);
        }
    }
}
