package cn.ac.fage.accessmesh.access.platform.service;

import cn.ac.fage.accessmesh.access.auth.dto.Oauth2ClientUpdateReq;
import cn.ac.fage.accessmesh.access.auth.service.Oauth2ClientAppService;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.engine.query.QueryGate;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** 实际事务的旧值覆盖与父子生命周期交错；PG 阻塞状态固定竞争时序。 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
    "logging.level.cn.ac.fage.accessmesh=WARN"
})
class BackendWriteConcurrencyPgIT {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, BackendWriteConcurrencyPgIT.class);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private Oauth2ClientAppService clients;
    @Autowired private DictAppService dictionaries;
    @Autowired private cn.ac.fage.accessmesh.access.user.service.UserManageAppService users;
    @Autowired private cn.ac.fage.accessmesh.access.user.service.AbstractUserSyncAppService sync;
    @MockitoBean private AdminPermissionValidator permissionValidator;
    @MockitoBean private QueryGate gate;
    @MockitoBean private cn.ac.fage.accessmesh.access.sync.guard.SyncTypeGuard syncTypes;

    @BeforeEach
    void allowIndependentPermissionChecks() {
        org.mockito.Mockito.when(gate.hasPermissionByCode(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.when(gate.getDeniedResourceCodes(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Set.of());
        org.mockito.Mockito.when(syncTypes.validate(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dictionaryParentAndChildWritesMustShareALifecycleBoundary(boolean deletionFirst) throws Exception {
        String code = "dict-" + UUID.randomUUID();
        long id = jdbc.queryForObject("""
            INSERT INTO sys_dict_type(tenant_id, dict_type, dict_name, status, delete_flag)
            VALUES (1, ?, 'dictionary', 1, 0) RETURNING id
            """, Long.class, code);
        String originalCode = "from-" + UUID.randomUUID();
        jdbc.update("INSERT INTO sys_dict_type(tenant_id,dict_type,dict_name) VALUES (1,?,'original')", originalCode);
        long dataId = jdbc.queryForObject("""
            INSERT INTO sys_dict_data(tenant_id,dict_type,dict_label,dict_value)
            VALUES (1,?,'label','value') RETURNING id
            """, Long.class, originalCode);
        Runnable create = () -> dictionaries.updateDictData(
            new cn.ac.fage.accessmesh.access.platform.dto.req.DictDataUpdateReq(dataId, id, "label", "value", 1, 1, null));
        Runnable remove = () -> dictionaries.deleteDictType(new cn.ac.fage.accessmesh.access.infrastructure.dto.IdsReq(java.util.List.of(id)));
        RuntimeException failure = raceApplicationTransactions(RequestContext.user(1L, 99L),
            deletionFirst ? remove : create, deletionFirst ? create : remove);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM sys_dict_data d JOIN sys_dict_type t
              ON t.tenant_id=d.tenant_id AND t.dict_type=d.dict_type
            WHERE t.id=? AND t.delete_flag<>0 AND d.delete_flag=0
            """, Integer.class, id)).isZero();
        assertThat(failure).isInstanceOf(BizException.class);
        assertThat(((BizException) failure).getErrorCode()).isEqualTo(deletionFirst
            ? cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode.DICT_TYPE_NOT_FOUND.getCode()
            : cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode.DICT_TYPE_HAS_DATA.getCode());
    }

    @Test
    void dictionaryCreationMustApplyRequiredSchemaDefaults() {
        long typeId = jdbc.queryForObject("INSERT INTO sys_dict_type(tenant_id,dict_type,dict_name) VALUES (1,?,'defaults') RETURNING id",
            Long.class, "defaults-" + UUID.randomUUID());
        AccessRequestContext.bind(RequestContext.user(1L, 99L));
        try {
            long dataId = dictionaries.createDictData(
                new cn.ac.fage.accessmesh.access.platform.dto.req.DictDataCreateReq(typeId, "label", "value", null, null, null));
            var row = jdbc.queryForMap("SELECT sort_order,is_default,status FROM sys_dict_data WHERE id=?", dataId);
            assertThat(row.get("sort_order")).isEqualTo(0);
            assertThat(row.get("is_default")).isEqualTo(false);
            assertThat(row.get("status")).isEqualTo(1);
        } finally {
            AccessRequestContext.clear();
        }
    }

    @Test
    void deletedSubjectMustNotReceiveANewRoleAfterItsCleanup() throws Exception {
        String external = "subject-" + UUID.randomUUID();
        String role = "role-" + UUID.randomUUID();
        long user = jdbc.queryForObject("""
            INSERT INTO abstract_user(tenant_id,user_type,external_id,name,enabled)
            VALUES (1,(SELECT type_value FROM type_definition WHERE tenant_id=1 AND type_key='user_type' AND type_code='USER' AND delete_flag=0),?,'subject',true)
            RETURNING id
            """, Long.class, external);
        jdbc.update("""
            INSERT INTO abstract_role(tenant_id,role_type,external_id,name,status)
            VALUES (1,6,?,'role',1)
            """, role);
        RuntimeException failure = raceApplicationTransactions(RequestContext.user(1L, 99L),
            () -> users.deleteUsers(1L, java.util.List.of(user)),
            () -> users.assignRolesBatch(1L, new cn.ac.fage.accessmesh.access.role.dto.req.UserRoleBatchAssignReq(
                java.util.List.of(external), "USER", null, "BASIC_ROLE", role, null)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_role WHERE tenant_id=1 AND abstract_user_id=? AND delete_flag=0",
            Integer.class, user)).isZero();
        assertThat(failure).isInstanceOf(BizException.class);
    }

    @Test
    void newerFullSyncMustApplyAgainstTheCommittedSingleSyncFact() throws Exception {
        String source = "source-" + UUID.randomUUID();
        String external = "sync-" + UUID.randomUUID();
        var time = java.time.LocalDateTime.of(2026, 10, 18, 0, 0);
        var first = new cn.ac.fage.accessmesh.access.sync.dto.AbstractUserSyncReq("UPSERT", "USER", external,
            "version-one", true, null, source, "user", external,
            new cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef(time, 1L));
        var second = new cn.ac.fage.accessmesh.access.sync.dto.AbstractUserFullSyncReq(
            new cn.ac.fage.accessmesh.access.sync.dto.AbstractUserSyncScope(source, "USER"),
            java.util.List.of(new cn.ac.fage.accessmesh.access.sync.dto.AbstractUserSyncItem(external,
                "version-two", true, null, "user", external,
                new cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef(time, 2L))));
        var result = new java.util.concurrent.atomic.AtomicReference<cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp>();
        RuntimeException failure = raceApplicationTransactions(RequestContext.service(1L, source),
            () -> assertThat(sync.sync(1L, first, null).applied()).isTrue(),
            () -> result.set(sync.fullSync(1L, second, null)));
        assertThat(failure).isNull();
        assertThat(result.get().applied()).isTrue();
        assertThat(jdbc.queryForObject("SELECT name FROM abstract_user WHERE tenant_id=1 AND external_id=? AND delete_flag=0",
            String.class, external)).isEqualTo("version-two");
    }

    private RuntimeException raceApplicationTransactions(RequestContext context, Runnable first, Runnable second) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> {
                AccessRequestContext.bind(context);
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(status -> {
                        first.run();
                        held.countDown();
                        awaitGate(release);
                    });
                } finally {
                    AccessRequestContext.clear();
                }
            });
            try {
                await().atMost(Duration.ofSeconds(15)).until(() -> holder.isDone() || held.getCount() == 0);
                if (held.getCount() != 0) holder.get(1, TimeUnit.SECONDS);
                var result = pool.submit(() -> {
                    AccessRequestContext.bind(context);
                    try {
                        second.run();
                        return (RuntimeException) null;
                    } catch (RuntimeException failure) {
                        return failure;
                    } finally {
                        AccessRequestContext.clear();
                    }
                });
                await().atMost(Duration.ofSeconds(15)).until(() -> result.isDone() || jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()
                    AND wait_event_type='Lock' AND cardinality(pg_blocking_pids(pid))>0
                    """, Integer.class) > 0);
                release.countDown();
                holder.get(20, TimeUnit.SECONDS);
                return result.get(20, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clientRenameMustNotRestoreRotatedSecretOrDeletedRecord(boolean deleted) throws Exception {
        long id = jdbc.queryForObject("""
            INSERT INTO sys_oauth2_client(tenant_id, client_id, client_secret, client_type, client_name,
                grant_types, access_token_ttl, refresh_token_ttl, status, delete_flag)
            VALUES (1, ?, 'OLD_HASH', 'CONFIDENTIAL', 'original', 'authorization_code', 3600, 86400, 1, 0)
            RETURNING id
            """, Long.class, "concurrent-" + UUID.randomUUID());
        RuntimeException failure = raceWithCommittedChange("sys_oauth2_client", deleted
            ? "UPDATE sys_oauth2_client SET delete_flag=id WHERE id=?"
            : "UPDATE sys_oauth2_client SET client_secret='NEW_HASH', status=0 WHERE id=?", id,
            () -> clients.updateClient(new Oauth2ClientUpdateReq(id, null, "renamed",
            null, null, null, null, null, null, null, null, null, null)));

        if (deleted) {
            assertThat(jdbc.queryForObject("SELECT delete_flag FROM sys_oauth2_client WHERE id=?", Long.class, id))
                .isEqualTo(id);
            assertThat(failure).isInstanceOf(BizException.class);
        } else {
            assertThat(failure).isNull();
            var row = jdbc.queryForMap("SELECT client_secret, status, client_name FROM sys_oauth2_client WHERE id=?", id);
            assertThat(row.get("client_secret")).isEqualTo("NEW_HASH");
            assertThat(row.get("status")).isEqualTo(0);
            assertThat(row.get("client_name")).isEqualTo("renamed");
        }
    }

    private RuntimeException raceWithCommittedChange(String table, String mutationSql, long id, Runnable writer) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> {
                // 独立显式 JDBC 事务：不能假定 JdbcTemplate 使用 Flex 的同一事务连接。
                try (var connection = jdbc.getDataSource().getConnection()) {
                    connection.setAutoCommit(false);
                    try (var statement = connection.prepareStatement(mutationSql)) {
                        statement.setLong(1, id);
                        assertThat(statement.executeUpdate()).isEqualTo(1);
                    }
                    held.countDown();
                    awaitGate(release);
                    connection.commit();
                }
                return true;
            });
            try {
                awaitGate(held);
                var result = pool.submit(() -> {
                    TenantContextHolder.setTenantId(1L);
                    AccessRequestContext.bind(RequestContext.user(1L, 99L));
                    try {
                        writer.run();
                        return (RuntimeException) null;
                    } catch (RuntimeException failure) {
                        return failure;
                    } finally {
                        AccessRequestContext.clear();
                        TenantContextHolder.clear();
                    }
                });
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                    if (result.isDone()) throw new AssertionError("writer exited before reaching the database lock", result.get());
                    assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()
                    AND wait_event_type='Lock' AND cardinality(pg_blocking_pids(pid))>0 AND query LIKE ?
                    """, Integer.class, "%" + table + "%")).isPositive();
                });
                release.countDown();
                holder.get(20, TimeUnit.SECONDS);
                return result.get(20, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
    }

    private static void awaitGate(CountDownLatch gate) {
        try {
            assertThat(gate.await(25, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
