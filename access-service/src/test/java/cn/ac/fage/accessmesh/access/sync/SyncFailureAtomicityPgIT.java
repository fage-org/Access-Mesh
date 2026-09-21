package cn.ac.fage.accessmesh.access.sync;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import cn.ac.fage.accessmesh.access.role.service.UserRoleSyncAppService;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncScope;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

/** 同步入口真实事务：失败不记账、原版本重试、部分成功、技术故障与并发版本。 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
class SyncFailureAtomicityPgIT {
    private static final long TENANT = 1L;
    private static final String SOURCE = "atomicity-service";
    private static final LocalDateTime EVENT_TIME = LocalDateTime.of(2026, 9, 20, 0, 0);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, SyncFailureAtomicityPgIT.class);
    }

    @Autowired private UserRoleSyncAppService members;
    @Autowired private ResourceEntitySyncAppService resources;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private RedissonClient redisson;
    @SpyBean private TreeWriteLockSupport treeLocks;
    private String scope;

    @BeforeEach
    void setup() {
        scope = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO service_config(tenant_id, service_code, name, extra)
                VALUES (?, ?, 'sync atomicity',
                  '{"syncTypes":{"subjectTypeCodes":["USER"],"roleTypeCodes":["BASIC_ROLE"],"sourceTypes":["EXT_MEMBER"]}}')
                ON CONFLICT DO NOTHING
                """, TENANT, SOURCE);
        jdbc.update("""
                INSERT INTO type_definition(tenant_id, type_key, type_code, type_value, name, extra)
                VALUES (?, 'resource_type', 'ATOMIC_RESOURCE', 900, 'sync atomicity',
                  '{"managedMode":"SYNC","syncSourceService":"atomicity-service"}')
                ON CONFLICT DO NOTHING
                """, TENANT);
        bind();
    }

    @AfterEach
    void cleanup() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    void shouldApplyOriginalVersion_whenMissingDependenciesAreCreated() {
        UserRoleSyncReq req = request("BIND", scope, 1, null);
        assertThat(members.sync(TENANT, req, null).reason()).isEqualTo("SUBJECT_NOT_FOUND");
        assertThat(ledgerCount()).isZero();
        insertUser(scope);
        assertThat(members.sync(TENANT, req, null).reason()).isEqualTo("ROLE_NOT_FOUND");
        assertThat(ledgerCount()).isZero();
        insertRole(scope);
        assertThat(members.sync(TENANT, req, null).reason()).isEqualTo("RELATION_ROLE_NOT_FOUND");
        assertThat(ledgerCount()).isZero();
        insertRole(scope + "-relation");
        assertThat(members.sync(TENANT, req, null).applied()).isTrue();
        assertThat(factCount(scope)).isEqualTo(1);
        assertThat(ledgerCount()).isEqualTo(1);
        assertThat(members.sync(TENANT, req, null).stale()).isTrue();
    }

    @Test
    void shouldNotConsumeDisableVersion_whenResourceIsMissing() {
        ResourceEntitySyncReq req = resourceRequest("DISABLE", 1);
        assertThat(resources.sync(TENANT, req, null).reason()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(resourceLedgerCount()).isZero();
        jdbc.update("INSERT INTO resource_entity(tenant_id,resource_type,code,code_type,name) VALUES (?,900,?,'default','resource')",
                TENANT, scope);
        assertThat(resources.sync(TENANT, req, null).applied()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM resource_entity WHERE tenant_id=? AND code=? AND resource_type=900",
                Integer.class, TENANT, scope)).isZero();
        assertThat(resources.sync(TENANT, resourceRequest("DELETE", 2), null).applied()).isTrue();
        assertThat(resources.sync(TENANT, req, null).stale()).isTrue();
        assertThat(resourceLedgerCount()).isEqualTo(1);
    }

    @Test
    void shouldKeepPartialSuccessAndCleanup_whenFullSyncHasMissingSubject() {
        prepare(scope + "-omitted");
        assertThat(members.sync(TENANT, request("BIND", scope + "-omitted", 1, null), null).applied()).isTrue();
        prepare(scope);
        UserRoleFullSyncReq full = full(scope, scope + "-missing");
        SyncResultResp first = members.fullSync(TENANT, full, null);
        assertThat(first.detail().appliedCount()).isEqualTo(1);
        assertThat(first.detail().failedCount()).isEqualTo(1);
        assertThat(first.detail().deactivatedCount()).isEqualTo(1);
        assertThat(factCount(scope + "-omitted")).isZero();
        assertThat(ledgerCount()).isEqualTo(2);
        prepare(scope + "-missing");
        SyncResultResp retry = members.fullSync(TENANT, full, null);
        assertThat(retry.detail().appliedCount()).isEqualTo(1);
        assertThat(retry.detail().staleCount()).isEqualTo(1);
        assertThat(retry.detail().failedCount()).isZero();
        assertThat(factCount(scope + "-missing")).isEqualTo(1);
        assertThat(members.sync(TENANT, request("BIND", scope + "-omitted", 1, null), null).stale()).isTrue();
        assertThat(factCount(scope + "-omitted")).isZero();
    }

    @Test
    void shouldNotConsumeVersion_whenManualRelationshipRejectsBindAndUnbind() {
        prepare(scope);
        jdbc.update("""
                INSERT INTO user_role(tenant_id,abstract_user_id,target_type,target_id,relation_id)
                SELECT ?,u.id,'ROLE',r.id,rel.id FROM abstract_user u,abstract_role r,abstract_role rel
                WHERE u.tenant_id=? AND u.external_id=? AND r.tenant_id=? AND r.external_id=?
                  AND rel.tenant_id=? AND rel.external_id=?
                """, TENANT, TENANT, scope, TENANT, scope, TENANT, scope + "-relation");
        for (String operation : List.of("BIND", "UNBIND")) {
            assertThat(members.sync(TENANT, request(operation, scope, 1, null), null).reason())
                    .isEqualTo("OWNERSHIP_CONFLICT");
        }
        assertThat(ledgerCount()).isZero();
        assertThat(factCount(scope)).isEqualTo(1);
    }

    @Test
    void shouldRetrySameVersion_whenRoleMutexIsRemoved() {
        prepare(scope);
        long other = insertRole(scope + "-other");
        long target = jdbc.queryForObject("SELECT id FROM abstract_role WHERE tenant_id=? AND external_id=?",
                Long.class, TENANT, scope);
        jdbc.update("""
                INSERT INTO user_role(tenant_id,abstract_user_id,target_type,target_id,relation_id)
                SELECT ?,id,'ROLE',?,0 FROM abstract_user WHERE tenant_id=? AND external_id=?
                """, TENANT, other, TENANT, scope);
        Long rule = jdbc.queryForObject("""
                INSERT INTO permission_conflict_rule(tenant_id,conflict_type,first_abstract_role_id,second_abstract_role_id)
                VALUES (?,'ROLE_MUTEX',?,?) RETURNING id
                """, Long.class, TENANT, other, target);
        UserRoleSyncReq req = request("BIND", scope, 1, null);
        assertThat(members.sync(TENANT, req, null).reason()).isEqualTo("ROLE_MUTEX_CONFLICT");
        assertThat(ledgerCount()).isZero();
        jdbc.update("DELETE FROM permission_conflict_rule WHERE id=?", rule);
        assertThat(members.sync(TENANT, req, null).applied()).isTrue();
        assertThat(ledgerCount()).isEqualTo(1);
    }

    @Test
    void shouldRollbackFactsAndLedger_whenFullSyncFailsAfterWritingFacts() {
        prepare(scope);
        prepare(scope + "-fault");
        jdbc.execute("""
                CREATE FUNCTION fail_sync_backfill() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.target_id IS NOT NULL AND NEW.business_key LIKE '%-fault&%' THEN
                    RAISE EXCEPTION 'injected sync backfill failure';
                  END IF;
                  RETURN NEW;
                END $$
                """);
        jdbc.execute("CREATE TRIGGER fail_sync_backfill BEFORE UPDATE ON sync_metadata FOR EACH ROW EXECUTE FUNCTION fail_sync_backfill()");
        UserRoleFullSyncReq req = full(scope, scope + "-fault");
        try {
            assertThatThrownBy(() -> members.fullSync(TENANT, req, null))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(ledgerCount()).isZero();
            assertThat(factCount(scope)).isZero();
            assertThat(factCount(scope + "-fault")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_sync_backfill ON sync_metadata");
            jdbc.execute("DROP FUNCTION fail_sync_backfill()");
        }
        assertThat(members.fullSync(TENANT, req, null).detail().appliedCount()).isEqualTo(2);
    }

    @Test
    void shouldKeepNewerFactAndLedger_whenVersionsRace() throws Exception {
        prepare(scope);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var old = executor.submit(() -> concurrentSync(start, 1));
            var newer = executor.submit(() -> concurrentSync(start, 2));
            start.countDown();
            SyncResultResp oldResult = old.get(20, TimeUnit.SECONDS);
            assertThat(oldResult.applied() || oldResult.stale()).isTrue();
            assertThat(newer.get(20, TimeUnit.SECONDS).applied()).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT last_sync_sequence_no FROM sync_metadata WHERE tenant_id=? AND scope_key_hash=?",
                Long.class, TENANT, scopeHash())).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT ur.valid_to AT TIME ZONE 'UTC' FROM user_role ur JOIN abstract_user u ON u.id=ur.abstract_user_id
                WHERE ur.tenant_id=? AND u.external_id=? AND ur.delete_flag=0
                """, LocalDateTime.class, TENANT, scope)).isEqualTo(EVENT_TIME.plusYears(2));
        assertThat(members.sync(TENANT, request("BIND", scope, 1, null), null).stale()).isTrue();
    }

    private SyncResultResp concurrentSync(CountDownLatch start, long version) throws InterruptedException {
        bind();
        try {
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return members.sync(TENANT, request("BIND", scope, version, EVENT_TIME.plusYears(version)), null);
        } finally {
            cleanup();
        }
    }

    @Test
    void shouldReadCommittedFacts_whenFullSyncWaitsForSingleSyncTransaction() throws Exception {
        prepare(scope);
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch secondAtLock = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals("full-sync-contender")) {
                secondAtLock.countDown();
            }
            return invocation.callRealMethod();
        }).when(treeLocks).lockTreeWrites(TENANT, TreeWriteLockSupport.TreeLockTarget.ABSTRACT_ROLE);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                bind();
                try {
                    return new TransactionTemplate(transactionManager).execute(status -> {
                        SyncResultResp result = members.sync(TENANT, request("BIND", scope, 1, null), null);
                        firstWritten.countDown();
                        awaitGate(allowFirstCommit);
                        return result;
                    });
                } finally {
                    cleanup();
                }
            });
            try {
                awaitGate(firstWritten);
                // 公开 sync 已返回，但外层事务尚未提交，锁必须仍保护已写事实。
                assertThat(redisson.getLock(
                        TreeWriteLockSupport.lockKey(TENANT, TreeWriteLockSupport.TreeLockTarget.ABSTRACT_ROLE)).isLocked()).isTrue();
                var second = executor.submit(() -> {
                    Thread.currentThread().setName("full-sync-contender");
                    bind();
                    try {
                        return members.fullSync(TENANT, new UserRoleFullSyncReq(
                                new UserRoleSyncScope(SOURCE, "EXT_MEMBER", "BASIC_ROLE", scope),
                                List.of(new UserRoleSyncItem("USER", scope, "BASIC_ROLE", scope,
                                        "BASIC_ROLE:" + scope + "-relation", null, EVENT_TIME.plusYears(2),
                                        null, null, new SyncVersionRef(EVENT_TIME, 2L)))), null);
                    } finally {
                        cleanup();
                    }
                });
                awaitGate(secondAtLock);
                allowFirstCommit.countDown();
                assertThat(first.get(20, TimeUnit.SECONDS).applied()).isTrue();
                assertThat(second.get(20, TimeUnit.SECONDS).detail().appliedCount()).isEqualTo(1);
                assertThat(factCount(scope)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT last_sync_sequence_no FROM sync_metadata WHERE tenant_id=? AND scope_key_hash=?",
                        Long.class, TENANT, scopeHash())).isEqualTo(2);
                assertThat(jdbc.queryForObject("""
                        SELECT ur.valid_to AT TIME ZONE 'UTC' FROM user_role ur JOIN abstract_user u ON u.id=ur.abstract_user_id
                        WHERE ur.tenant_id=? AND u.external_id=? AND ur.delete_flag=0
                        """, LocalDateTime.class, TENANT, scope)).isEqualTo(EVENT_TIME.plusYears(2));
            } finally {
                allowFirstCommit.countDown();
            }
        }
    }

    private static void awaitGate(CountDownLatch gate) {
        try {
            assertThat(gate.await(20, TimeUnit.SECONDS)).as("transaction gate reached").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("transaction gate interrupted", e);
        }
    }

    private void bind() {
        AccessRequestContext.bind(RequestContext.service(TENANT, SOURCE));
        TenantContextHolder.setTenantId(TENANT);
    }

    private void prepare(String key) {
        insertUser(key);
        insertRole(key);
        insertRole(key + "-relation");
    }

    private void insertUser(String key) {
        jdbc.update("INSERT INTO abstract_user(tenant_id,user_type,external_id,name) VALUES (?,1,?,?)", TENANT, key, key);
    }

    private long insertRole(String key) {
        return jdbc.queryForObject("INSERT INTO abstract_role(tenant_id,role_type,external_id,name) VALUES (?,6,?,?) RETURNING id",
                Long.class, TENANT, key, key);
    }

    private UserRoleSyncReq request(String operation, String key, long version, LocalDateTime validTo) {
        return new UserRoleSyncReq(operation, "EXT_MEMBER", "USER", key, "BASIC_ROLE", scope, key,
                "BASIC_ROLE:" + key + "-relation", null, validTo, SOURCE, null, null,
                new SyncVersionRef(EVENT_TIME, version));
    }

    private UserRoleFullSyncReq full(String first, String second) {
        return new UserRoleFullSyncReq(new UserRoleSyncScope(SOURCE, "EXT_MEMBER", "BASIC_ROLE", scope),
                List.of(first, second).stream().map(key -> new UserRoleSyncItem("USER", key, "BASIC_ROLE", key,
                        "BASIC_ROLE:" + key + "-relation", null, null, null, null,
                        new SyncVersionRef(EVENT_TIME, 1L))).toList());
    }

    private ResourceEntitySyncReq resourceRequest(String operation, long version) {
        return new ResourceEntitySyncReq(operation, "ATOMIC_RESOURCE", scope, "default", null,
                null, null, null, null, null, null, SOURCE, null, null, new SyncVersionRef(EVENT_TIME, version));
    }

    private String scopeHash() {
        return SyncKeyCodecUtil.sha256Hex(SyncKeyCodecUtil.userRoleScopeKey("EXT_MEMBER", "BASIC_ROLE", scope));
    }

    private int ledgerCount() {
        return jdbc.queryForObject("SELECT count(*) FROM sync_metadata WHERE tenant_id=? AND scope_key_hash=? AND delete_flag=0",
                Integer.class, TENANT, scopeHash());
    }

    private int resourceLedgerCount() {
        return jdbc.queryForObject("SELECT count(*) FROM sync_metadata WHERE tenant_id=? AND business_key_hash=? AND delete_flag=0",
                Integer.class, TENANT, SyncKeyCodecUtil.sha256Hex(
                        SyncKeyCodecUtil.resourceEntityBusinessKey("ATOMIC_RESOURCE", scope, "default")));
    }

    private int factCount(String key) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM user_role ur JOIN abstract_user u ON ur.abstract_user_id=u.id
                WHERE ur.tenant_id=? AND u.external_id=? AND ur.delete_flag=0
                """, Integer.class, TENANT, key);
    }
}
