package cn.ac.fage.accessmesh.access.user.service.impl;

import cn.ac.fage.accessmesh.access.bootstrap.AccessBootstrapInitializer;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.it.TenantTestSupport;
import cn.ac.fage.accessmesh.access.role.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.access.user.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.UserUpdateStatusReq;
import cn.ac.fage.accessmesh.access.infrastructure.dto.IdsReq;
import cn.ac.fage.accessmesh.access.role.service.RoleManageAppService;
import cn.ac.fage.accessmesh.access.user.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.user.service.UserWriteAppService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleBatchRevokeReq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 租户保底管理员的真实写事务、交接与审计行为。 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "accessmesh.sync.scheduler.enabled=false",
    "access.tenant.gate-repair.enabled=false", "logging.level.cn.ac.fage.accessmesh=WARN"
})
class TenantAdminContinuityPgIT {
    private static final AtomicLong TENANTS = new AtomicLong(8000);
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, TenantAdminContinuityPgIT.class);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired AccessBootstrapInitializer initializer;
    @Autowired UserManageAppService memberships;
    @Autowired UserWriteAppService users;
    @Autowired RoleManageAppService roleManagement;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired cn.ac.fage.accessmesh.access.user.service.domain.TenantAdminContinuityDomainService continuity;
    private long tenant;
    private long admin;
    private long role;

    @BeforeEach
    void createTenant() {
        tenant = TENANTS.incrementAndGet();
        jdbc.queryForObject("SELECT initialize_tenant_baseline(?)::text", String.class, tenant);
        TenantTestSupport.enableFixture(jdbc, redis, tenant);
        TenantContextHolder.setTenantId(tenant);
        initializer.initialize(tenant, "Admin-test-2026!");
        admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE tenant_id=? AND username='admin'", Long.class, tenant);
        role = jdbc.queryForObject("SELECT id FROM abstract_role WHERE tenant_id=? AND external_id='bootstrap-admin' AND delete_flag=0", Long.class, tenant);
        AccessRequestContext.bind(RequestContext.user(tenant, admin));
    }

    @AfterEach
    void clearContext() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    void shouldRejectLastMemberRevocationAndRollBack() {
        assertThatThrownBy(() -> revoke(admin)).isInstanceOf(BizException.class).hasMessageContaining("保底管理员");
        assertThat(bindingCount(admin)).isEqualTo(1L);
    }

    @Test
    void shouldRejectExpiryOnLastPermanentMember() {
        assertThatThrownBy(() -> memberships.assignRole(tenant, new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("LOCAL_USER", String.valueOf(admin), null,
                "BASIC_ROLE", "bootstrap-admin", null, null, LocalDateTime.now().plusDays(1))))))
            .isInstanceOf(BizException.class).hasMessageContaining("保底管理员");
        assertThat(jdbc.queryForObject("SELECT valid_to IS NULL FROM user_role WHERE tenant_id=? AND abstract_user_id=? AND target_type='ROLE' AND target_id=? AND delete_flag=0",
            Boolean.class, tenant, admin, role)).isTrue();
    }

    @Test
    void shouldAllowHandoffToForcedResetAndTemporarilyLockedSuccessor() {
        long successor = addSuccessor();
        redis.opsForValue().set(cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore.tenantKey(tenant, "successor"),
            "5", java.time.Duration.ofMinutes(30));
        assertThatCode(() -> revoke(admin)).doesNotThrowAnyException();
        assertThat(bindingCount(admin)).isZero();
        assertThat(bindingCount(successor)).isEqualTo(1L);
        assertThatCode(() -> initializer.initialize(tenant, "Ignored-test-2026!")).doesNotThrowAnyException();
        assertThat(bindingCount(admin)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"expires", "future", "disabled"})
    void shouldNotCountIneligibleSuccessor(String kind) {
        long successor = addSuccessor();
        if (kind.equals("disabled")) {
            users.updateStatus(new UserUpdateStatusReq(List.of(successor), 0));
        } else {
            changeWindow(successor, kind.equals("future") ? LocalDateTime.now().plusDays(1) : null,
                kind.equals("expires") ? LocalDateTime.now().plusDays(1) : null);
        }
        assertThatThrownBy(() -> revoke(admin)).isInstanceOf(BizException.class).hasMessageContaining("保底管理员");
        assertThat(bindingCount(admin)).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disable", "profile", "delete"})
    void shouldProtectLastPermanentAccountFromTemporaryAdministrator(String operation) {
        long successor = addSuccessor();
        changeWindow(admin, null, LocalDateTime.now().plusDays(1));
        assertThatThrownBy(() -> {
            switch (operation) {
                case "disable" -> users.updateStatus(new UserUpdateStatusReq(List.of(successor), 0));
                case "profile" -> users.updateUser(new UserUpdateReq(successor, null, null, null, 0, null, null));
                default -> users.deleteUser(new IdsReq(List.of(successor)));
            }
        }).isInstanceOf(BizException.class).hasMessageContaining("保底管理员");
        assertThat(jdbc.queryForObject("SELECT status=1 AND delete_flag=0 FROM sys_user WHERE tenant_id=? AND id=?",
            Boolean.class, tenant, successor)).isTrue();
    }

    @Test
    void shouldRejectDisablingBuiltinRole() {
        assertThatThrownBy(() -> roleManagement.updateRole(tenant, role, null, 0, null, null, null, admin))
            .isInstanceOf(BizException.class).hasMessageContaining("保底管理员");
        assertThat(jdbc.queryForObject("SELECT status FROM abstract_role WHERE tenant_id=? AND id=?", Integer.class, tenant, role))
            .isEqualTo(1);
    }

    @Test
    void shouldAllowInitializationAfterOriginalAccountIsDeleted() {
        long successor = addSuccessor();
        AccessRequestContext.bind(RequestContext.user(tenant, successor));
        users.deleteUser(new IdsReq(List.of(admin)));
        assertThatCode(() -> initializer.initialize(tenant, "Ignored-test-2026!")).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_user WHERE tenant_id=? AND username='admin' AND delete_flag=0",
            Long.class, tenant)).isZero();
    }

    @Test
    void shouldRecordActorTargetAndValidityWithoutCredentials() {
        long successor = addSuccessor();
        var row = jdbc.queryForMap("SELECT created_by,diff_snapshot::text AS diff FROM permission_change_log "
            + "WHERE tenant_id=? AND change_reason='tenant-admin-membership' ORDER BY id DESC LIMIT 1", tenant);
        assertThat(((Number) row.get("created_by")).longValue()).isEqualTo(admin);
        var item = json.readTree((String) row.get("diff")).get("items").get(0);
        assertThat(item.get("userId").asLong()).isEqualTo(successor);
        assertThat(item.get("role").get("roleExternalId").asString()).isEqualTo("bootstrap-admin");
        assertThat(item.get("after").get("validTo").isNull()).isTrue();
        assertThat((String) row.get("diff")).doesNotContain("password", "initialPassword");
    }

    @Test
    void shouldSerializeConcurrentExitsUntilCommit() throws Exception {
        long successor = addSuccessor();
        var firstChanged = new java.util.concurrent.CountDownLatch(1);
        var allowCommit = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                bind(admin);
                try {
                    new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                        revoke(admin);
                        firstChanged.countDown();
                        try {
                            if (!allowCommit.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("commit gate timeout");
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(ex);
                        }
                    });
                } finally { clearContext(); }
            });
            java.util.concurrent.Future<?> second = null;
            try {
                assertThat(firstChanged.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                second = executor.submit(() -> {
                    bind(successor);
                    try {
                        assertThatThrownBy(() -> revoke(successor)).isInstanceOf(BizException.class).hasMessageContaining("保底管理员");
                    } finally { clearContext(); }
                });
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() ->
                    jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND classid=106 "
                        + "AND database=(SELECT oid FROM pg_database WHERE datname=current_database()) AND NOT granted", Long.class) > 0);
            } finally { allowCommit.countDown(); }
            first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            if (second != null) second.get(15, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(bindingCount(admin)).isZero();
        assertThat(bindingCount(successor)).isEqualTo(1L);
    }

    @Test
    void shouldKeepAllPermanentMembersAcrossSqlReadBatches() {
        int count = cn.ac.fage.accessmesh.access.infrastructure.util.SqlBatches.BATCH_SIZE + 1;
        jdbc.update("""
            WITH allocated AS MATERIALIZED (
                SELECT nextval(pg_get_serial_sequence('abstract_user','id')) AS id FROM generate_series(1, ?)
            ), subjects AS (
                INSERT INTO abstract_user(id,tenant_id,user_type,external_id,enabled,owner_service_code)
                SELECT id, ?, (SELECT user_type FROM abstract_user WHERE id=?), id::text, true, 'access-service'
                FROM allocated RETURNING id
            ), accounts AS (
                INSERT INTO sys_user(id,tenant_id,username,name,password,status)
                SELECT id, ?, 'backup-' || id, '备用管理员', (SELECT password FROM sys_user WHERE id=?), 1
                FROM subjects RETURNING id
            )
            INSERT INTO user_role(tenant_id,abstract_user_id,target_type,target_id)
            SELECT ?, id, 'ROLE', ? FROM accounts
            """, count, tenant, admin, tenant, admin, tenant, role);
        assertThatCode(() -> revoke(admin)).doesNotThrowAnyException();
        assertThat(bindingCount(admin)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_role WHERE tenant_id=? AND target_type='ROLE' AND target_id=? AND delete_flag=0",
            Long.class, tenant, role)).isEqualTo(count);
        assertThat(continuity.snapshot(tenant, LocalDateTime.now()).qualifiedUserIds()).hasSize(count);
    }

    private void bind(long operator) {
        TenantContextHolder.setTenantId(tenant);
        AccessRequestContext.bind(RequestContext.user(tenant, operator));
    }

    private void changeWindow(long userId, LocalDateTime from, LocalDateTime to) {
        memberships.assignRole(tenant, new UserAssignRoleReq(List.of(new UserAssignRoleReq.AssignItem(
            "LOCAL_USER", String.valueOf(userId), null, "BASIC_ROLE", "bootstrap-admin", null, from, to))));
    }

    private long addSuccessor() {
        Long root = jdbc.queryForObject("SELECT root_org_id FROM sys_org_tree_config WHERE tenant_id=? AND is_default=true AND delete_flag=0", Long.class, tenant);
        long id = users.createUser(new UserCreateReq("successor", "接班人", null, null, 1, root, true)).id();
        memberships.assignRolesBatch(tenant, new UserRoleBatchAssignReq(List.of(String.valueOf(id)),
            "LOCAL_USER", null, "BASIC_ROLE", "bootstrap-admin", null));
        return id;
    }

    private void revoke(long userId) {
        memberships.revokeRolesBatch(tenant, new UserRoleBatchRevokeReq(List.of(
            new UserRoleBatchRevokeReq.RevokeItem("LOCAL_USER", String.valueOf(userId), null,
                "BASIC_ROLE", "bootstrap-admin", null))));
    }

    private long bindingCount(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM user_role WHERE tenant_id=? AND abstract_user_id=? AND target_type='ROLE' AND target_id=? AND delete_flag=0",
            Long.class, tenant, userId, role);
    }
}
