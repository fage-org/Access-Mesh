package cn.ac.fage.accessmesh.access.sync;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncScope;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 资源 FULL/增量共序、失败重试及空清单的真实事务验证。 */
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
class ResourcePublicationPgIT {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 21, 0, 0);
    private static int nextType = 1200;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, ResourcePublicationPgIT.class); }
    @Autowired private ResourceEntitySyncAppService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @SpyBean private TreeWriteLockSupport locks;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private ServiceCredentialDomainService credentials;
    @AfterEach void clear() { AccessRequestContext.clear(); }

    @Test void shouldKeepLegacyOnlyUntilScopeAcceptsOrderedPublication() {
        Fixture f = fixture();
        assertThat(single(f, "a", null, 1).applied()).isTrue();
        assertThat(scopeCount(f)).isZero();
        var first = full(f, "10", List.of(item("a", 1)));
        assertThat(first.detail().staleCount()).isEqualTo(1);
        assertThat(maximum(f)).isEqualTo(10);
        assertThat(single(f, "a", null, 2).reason()).isEqualTo("PUBLICATION_GENERATION_REQUIRED");
        assertThat(single(f, "a", "11", 2).applied()).isTrue();
    }

    @Test void shouldKeepOutOfOrderOtherKey_andRejectOlderFullBeforeCleanup() {
        Fixture f = fixture();
        assertThat(single(f, "b", "43", 1).applied()).isTrue();
        assertThat(single(f, "a", "42", 1).applied()).isTrue();
        var old = full(f, "42", List.of(item("a", 1)));
        assertThat(old.stale()).isTrue();
        assertThat(old.detail().staleCount()).isEqualTo(1);
        assertThat(old.detail().failedCount()).isZero();
        assertThat(active(f, "b")).isEqualTo(1);
        assertThat(maximum(f)).isEqualTo(43);
        assertThat(full(f, "44", List.of(item("a", 1))).detail().deactivatedCount()).isEqualTo(1);
        assertThat(single(f, "b", "43", 2).stale()).isTrue();
        assertThat(active(f, "b")).isZero();
        assertThat(single(f, "b", "45", 2).applied()).isTrue();
    }

    @Test void shouldAllowIdenticalRetry_butRejectSameGenerationConflictAndOldRetry() {
        Fixture f = fixture();
        assertThat(full(f, "10", List.of(item("a", 1), item("b", 1))).detail().appliedCount()).isEqualTo(2);
        assertThat(full(f, "10", List.of(item("b", 1), item("a", 1))).detail().staleCount()).isEqualTo(2);
        assertThat(full(f, "10", List.of()).reason()).isEqualTo("PUBLICATION_GENERATION_CONFLICT");
        assertThat(single(f, "c", "11", 1).applied()).isTrue();
        assertThat(full(f, "10", List.of(item("a", 1), item("b", 1))).stale()).isTrue();
        assertThat(active(f, "c")).isEqualTo(1);
    }

    @Test void shouldRetryPartialOriginalSnapshot_afterItsParentWasCreated() {
        Fixture f = fixture();
        var child = new ResourceEntitySyncItem("child", null, "child", null, "parent", null, null, 1,
                null, null, null, new SyncVersionRef(AT, 1L));
        var items = List.of(item("parent", 1), child);
        var first = full(f, "10", items);
        assertThat(first.detail().appliedCount()).isEqualTo(1);
        assertThat(first.detail().failedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_full_status FROM resource_publication_state WHERE source_service=?", String.class, f.service())).isEqualTo("PARTIAL");
        var retry = full(f, "10", items);
        assertThat(retry.detail().failedCount()).isZero();
        assertThat(retry.detail().staleCount()).isEqualTo(1);
        assertThat(retry.detail().appliedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity c JOIN resource_entity p ON p.id=c.parent_id WHERE c.resource_type=? AND c.code='child' AND p.code='parent' AND c.delete_flag=0 AND p.delete_flag=0", Integer.class, f.type())).isEqualTo(1);
    }

    @Test void shouldClearOnlyOwnedScope_onCompleteEmptySnapshot() {
        Fixture f = fixture();
        Fixture other = fixture();
        assertThat(single(f, "owned", "1", 1).applied()).isTrue();
        assertThat(single(other, "other", "1", 1).applied()).isTrue();
        jdbc.update("INSERT INTO resource_entity(tenant_id,resource_type,code,code_type,name) VALUES(1,?,'untracked','default','untracked')", f.type());
        assertThat(full(f, "2", List.of()).detail().deactivatedCount()).isEqualTo(1);
        assertThat(active(f, "owned")).isZero();
        assertThat(active(f, "untracked")).isEqualTo(1);
        assertThat(active(other, "other")).isEqualTo(1);
        assertThat(full(f, "1", List.of()).stale()).isTrue();
    }

    @Test void shouldRejectConflictingEqualItemVersion_andNotConsumeFailedSingleGeneration() {
        Fixture f = fixture();
        assertThat(single(f, "a", "1", 2).applied()).isTrue();
        assertThat(single(f, "a", "2", 1).stale()).isTrue();
        assertThat(maximum(f)).isEqualTo(1);
        var conflict = new ResourceEntitySyncItem("a", null, "changed", null, null, null, null, 1,
                null, null, null, new SyncVersionRef(AT, 2L));
        var response = full(f, "3", List.of(conflict));
        assertThat(response.detail().itemResults().getFirst().reason()).isEqualTo("SYNC_VERSION_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT name FROM resource_entity WHERE resource_type=? AND code='a' AND delete_flag=0", String.class, f.type())).isEqualTo("a");
        assertThat(full(f, "4", List.of(item("a", 2))).detail().staleCount()).isEqualTo(1);
    }

    @Test void shouldRejectDuplicateKeysAndMissingGeneration_beforeAnyPublicationState() {
        Fixture f = fixture();
        assertThat(full(f, "1", List.of(item("a", 1), item("a", 2))).reason()).isEqualTo("DUPLICATE_BUSINESS_KEY");
        assertThat(scopeCount(f)).isZero();
        assertThat(active(f, "a")).isZero();
        assertThat(full(f, null, List.of()).reason()).isEqualTo("PUBLICATION_GENERATION_REQUIRED");
        assertThat(scopeCount(f)).isZero();
    }

    @Test void shouldRollbackFactsMetadataAndMode_whenScopeWriteFails() {
        Fixture f = fixture();
        jdbc.execute("""
                CREATE FUNCTION reject_resource_publication() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected resource publication failure'; END $$;
                CREATE TRIGGER reject_resource_publication BEFORE INSERT ON resource_publication_state
                FOR EACH ROW EXECUTE FUNCTION reject_resource_publication();
                """);
        try {
            assertThatThrownBy(() -> full(f, "1", List.of(item("a", 1))))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(active(f, "a")).isZero();
            assertThat(scopeCount(f)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_metadata WHERE source_service=?", Integer.class, f.service())).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_resource_publication ON resource_publication_state; DROP FUNCTION reject_resource_publication()");
        }
        assertThat(full(f, "1", List.of(item("a", 1))).detail().appliedCount()).isEqualTo(1);
    }

    @Test void shouldReadPublicationStateAfterWaitingForConcurrentIncrementCommit() throws Exception {
        Fixture f = fixture();
        assertThat(single(f, "a", null, 1).applied()).isTrue();
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch atFullLock = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        doAnswer(call -> {
            if (Thread.currentThread().getName().equals("publication-full-waiter")) atFullLock.countDown();
            return call.callRealMethod();
        }).when(locks).lockTreeWrites(1L, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var increment = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                var result = single(f, "b", "43", 1);
                written.countDown();
                await(commit);
                return result;
            }));
            try {
                await(written);
                var snapshot = pool.submit(() -> {
                    Thread.currentThread().setName("publication-full-waiter");
                    return full(f, "42", List.of(item("a", 1)));
                });
                await(atFullLock);
                commit.countDown();
                assertThat(increment.get(20, TimeUnit.SECONDS).applied()).isTrue();
                assertThat(snapshot.get(20, TimeUnit.SECONDS).stale()).isTrue();
                assertThat(active(f, "b")).isEqualTo(1);
            } finally { commit.countDown(); }
        }
    }

    @Test void shouldConfirmEqualNumericExtraAcrossJsonbRepresentation_withoutLosingPrecision() {
        Fixture f = fixture();
        var extra = java.util.Map.<String, Object>of("scientific", 1e20,
                "exact", new java.math.BigDecimal("9007199254740993.0000000000000000001"));
        AccessRequestContext.bind(RequestContext.service(1L, f.service()));
        var request = new ResourceEntitySyncReq("UPSERT", f.typeCode(), "a", null, "a", null, null, null,
                null, 1, extra, f.service(), null, null, new SyncVersionRef(AT, 1L), "1");
        assertThat(service.sync(1L, request, null).applied()).isTrue();
        var same = new ResourceEntitySyncItem("a", null, "a", null, null, null, null, 1, extra,
                null, null, new SyncVersionRef(AT, 1L));
        assertThat(full(f, "2", List.of(same)).detail().failedCount()).isZero();
        var changed = new ResourceEntitySyncItem("a", null, "a", null, null, null, null, 1,
                java.util.Map.of("scientific", 1e20, "exact", new java.math.BigDecimal("9007199254740993.0000000000000000002")),
                null, null, new SyncVersionRef(AT, 1L));
        assertThat(full(f, "3", List.of(changed)).detail().itemResults().getFirst().reason()).isEqualTo("SYNC_VERSION_CONFLICT");
    }

    @Test void shouldConfirmEqualFullVersionAfterDatabaseMicrosecondRounding() {
        Fixture f = fixture();
        AccessRequestContext.bind(RequestContext.service(1L, f.service()));
        var request = new ResourceEntitySyncReq("UPSERT", f.typeCode(), "a", null, "a", null, null, null,
                null, 1, null, f.service(), null, null, new SyncVersionRef(AT.plusNanos(501), 1L), "1");
        assertThat(service.sync(1L, request, null).applied()).isTrue();
        var same = new ResourceEntitySyncItem("a", null, null, null, null, null, null, null, null,
                null, null, new SyncVersionRef(AT.plusNanos(1000), 1L));
        var response = full(f, "2", List.of(same));
        assertThat(response.detail().staleCount()).isEqualTo(1);
        assertThat(response.detail().failedCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT last_publication_generation FROM sync_metadata WHERE source_service=?", Long.class, f.service())).isEqualTo(2);
    }

    @Test void shouldAcceptExplicitEmptyHttpList_butRejectNullMissingListOrGeneration() throws Exception {
        Fixture f = fixture();
        var credential = credentials.issue(1L, f.service(), null, 100L);
        var body = json.valueToTree(new ResourceEntityFullSyncReq(new ResourceEntitySyncScope(f.service(), f.typeCode()), List.of(), "1"));
        for (String missing : List.of("items", "publicationGeneration", "none", "null-items")) {
            var input = (com.fasterxml.jackson.databind.node.ObjectNode) body.deepCopy();
            if (missing.equals("null-items")) input.putNull("items");
            else if (!missing.equals("none")) input.remove(missing);
            var response = mvc.perform(post("/api/access/resource-entity/full-sync")
                    .header("X-Credential-Id", credential.entity().getCredentialId())
                    .header("X-Credential-Secret", credential.plainSecret())
                    .header("X-Service-Code", f.service()).header("X-Tenant-Id", "1")
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).as(missing).isEqualTo(missing.equals("none") ? 200 : 400);
            if (missing.equals("none")) assertThat(json.readTree(response.getContentAsString()).path("data").path("applied").asBoolean()).isTrue();
        }
        assertThat(maximum(f)).isEqualTo(1);
    }

    // 超大规模清理用例（65540 行）已拆档至 ResourcePublicationHeavyPgIT（testcontainers-heavy
    // 标签，日常形态 -DskipHeavyIT=true 跳过、收口必跑——T-PERM-079）

    private SyncResultResp single(Fixture f, String code, String generation, long version) {
        AccessRequestContext.bind(RequestContext.service(1L, f.service()));
        try {
            return service.sync(1L, new ResourceEntitySyncReq("UPSERT", f.typeCode(), code, null, code,
                    null, null, null, null, 1, null, f.service(), null, null, new SyncVersionRef(AT, version), generation), null);
        } finally { AccessRequestContext.clear(); }
    }
    private SyncResultResp full(Fixture f, String generation, List<ResourceEntitySyncItem> items) {
        AccessRequestContext.bind(RequestContext.service(1L, f.service()));
        try { return service.fullSync(1L, new ResourceEntityFullSyncReq(new ResourceEntitySyncScope(f.service(), f.typeCode()), items, generation), null); }
        finally { AccessRequestContext.clear(); }
    }
    private ResourceEntitySyncItem item(String code, long version) {
        return new ResourceEntitySyncItem(code, null, code, null, null, null, null, 1, null, null, null, new SyncVersionRef(AT, version));
    }
    private int active(Fixture f, String code) {
        return jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE tenant_id=1 AND resource_type=? AND code=? AND delete_flag=0", Integer.class, f.type(), code);
    }
    private long maximum(Fixture f) { return jdbc.queryForObject("SELECT max_generation FROM resource_publication_state WHERE source_service=?", Long.class, f.service()); }
    private int scopeCount(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM resource_publication_state WHERE source_service=?", Integer.class, f.service()); }
    private Fixture fixture() {
        int type = nextType++;
        String source = "publication-" + UUID.randomUUID();
        String code = "PUB_" + type;
        jdbc.update("INSERT INTO service_config(tenant_id,service_code,name) VALUES(1,?,'publication')", source);
        jdbc.update("INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name,extra) VALUES(1,'resource_type',?,?,'publication',CAST(? AS jsonb))",
                code, type, "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"" + source + "\"}");
        return new Fixture(source, code, type);
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).as("publication transaction gate reached").isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private record Fixture(String service, String typeCode, int type) {}
}
