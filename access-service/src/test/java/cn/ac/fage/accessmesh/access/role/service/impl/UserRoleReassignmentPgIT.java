package cn.ac.fage.accessmesh.access.role.service.impl;

import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.role.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.access.user.service.UserManageAppService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserAssignRoleReq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T-ADMIN-030：重指派的持久化、有效角色读取与整批回滚行为。 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "logging.level.cn.ac.fage.accessmesh=WARN"
})
class UserRoleReassignmentPgIT {
    private static final Long TENANT = 1L;
    private static final LocalDateTime PAST = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime FUTURE = LocalDateTime.of(2090, 1, 1, 0, 0);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, UserRoleReassignmentPgIT.class);
    }

    @Autowired private UserManageAppService service;
    @Autowired private SubjectDomainService subjects;
    @Autowired private JdbcTemplate jdbc;
    private String prefix;
    private Long user;
    private Long role;

    @BeforeEach
    void setUp() {
        prefix = "reassign-" + UUID.randomUUID();
        Long operator = user("operator");
        Long operatorRole = role("operator-role");
        binding(operator, operatorRole, null, null, null);
        jdbc.update("INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_type, granted_bits, scope_all, grant_source) "
                + "VALUES (?, ?, 5, 16, true, 'MANUAL')", TENANT, operatorRole);
        user = user("user");
        role = role("role");
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, operator));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    void shouldRestoreExpiredBindingAndCachedRoles_whenAssignedAgain() {
        Long id = binding(user, role, null, null, PAST);
        assertThat(subjects.resolveEffectiveRoles(TENANT, user)).doesNotContain(role);
        assertThat(subjects.selectUserRoleProjections(TENANT, user, LocalDateTime.now())).isEmpty();

        assign(item("user", "role", null, null, null));

        assertWindow(id, null, null);
        assertThat(subjects.resolveEffectiveRoles(TENANT, user)).contains(role);
        assertThat(subjects.selectUserRoleProjections(TENANT, user, LocalDateTime.now()))
                .extracting(p -> p.roleExternalId()).containsExactly(prefix + "role");
        assertThat(countBindings(user, role)).isEqualTo(1);
        var after = jdbc.queryForMap("SELECT * FROM user_role WHERE id = ?", id);
        assign(item("user", "role", null, null, null));
        assertThat(jdbc.queryForMap("SELECT * FROM user_role WHERE id = ?", id)).isEqualTo(after);
    }

    @Test
    void shouldClearFutureWindow_whenBatchAssignedAgain() {
        Long id = binding(user, role, null, FUTURE, FUTURE.plusDays(10));
        Long fresh = user("fresh");
        service.assignRolesBatch(TENANT, new UserRoleBatchAssignReq(
                List.of(prefix + "user", prefix + "fresh"), "USER", null, "BASIC_ROLE", prefix + "role", null));
        assertWindow(id, null, null);
        assertThat(countBindings(user, role)).isEqualTo(1);
        assertThat(countBindings(fresh, role)).isEqualTo(1);
        assertThat(subjects.resolveEffectiveRoles(TENANT, user)).contains(role);
    }

    @Test
    void shouldPreserveOtherRelations_whenAddingDistinctRelation() {
        Long relation = role("relation");
        Long other = role("other-relation");
        Long old = binding(user, role, relation, null, PAST);
        Long manual = binding(user, role, null, null, PAST);
        assign(item("user", "role", other, null, null));
        assertThat(countBindings(user, role)).isEqualTo(3);
        assertWindow(old, null, PAST);
        assertWindow(manual, null, PAST);
        assign(item("user", "role", null, null, null));
        assertWindow(manual, null, null);
        assertWindow(old, null, PAST);
        assertThat(countBindings(user, role)).isEqualTo(3);
    }

    @Test
    void shouldRejectChangedNonNullRelationAndKeepBatchAtomic() {
        Long relation = role("relation");
        Long id = binding(user, role, relation, null, PAST);
        Long manual = binding(user, role, null, null, PAST);
        assign(item("user", "role", relation, null, PAST));
        assertThat(countBindings(user, role)).isEqualTo(2);
        assertThatThrownBy(() -> assign(item("user", "role", null, null, null),
                item("user", "role", relation, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("revoke")
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(AccessErrorCode.VALIDATION_FAILED.getCode());
        assertWindow(id, null, PAST);
        assertWindow(manual, null, PAST);
    }

    @Test
    void shouldRollbackWindowUpdate_whenRenewalHitsMutex() {
        Long id = binding(user, role, null, null, PAST);
        Long other = role("other");
        binding(user, other, null, null, null);
        mutex(role, other);
        assertThatThrownBy(() -> assign(item("user", "role", null, null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode());
        assertWindow(id, null, PAST);
    }

    @Test
    void shouldUseReplacementWindow_whenAssigningMutexRoleInSameBatch() {
        Long id = binding(user, role, null, FUTURE, FUTURE.plusDays(10));
        Long other = role("other");
        mutex(role, other);
        assign(item("user", "role", null, FUTURE.plusDays(20), FUTURE.plusDays(30)),
                item("user", "other", null, FUTURE, FUTURE.plusDays(10)));
        assertWindow(id, FUTURE.plusDays(20), FUTURE.plusDays(30));
        assertThat(countBindings(user, other)).isEqualTo(1);
    }

    private void assign(UserAssignRoleReq.AssignItem... items) {
        service.assignRole(TENANT, new UserAssignRoleReq(List.of(items)));
    }

    @Test
    void shouldRejectNonNullRewindow_whenBatchAssignIncludesFreshUser() {
        Long relation = role("relation");
        Long id = binding(user, role, relation, null, PAST);
        Long fresh = user("fresh");
        assertThatThrownBy(() -> service.assignRolesBatch(TENANT, new UserRoleBatchAssignReq(
                List.of(prefix + "fresh", prefix + "user"), "USER", null, "BASIC_ROLE", prefix + "role", relation)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(AccessErrorCode.VALIDATION_FAILED.getCode());
        assertWindow(id, null, PAST);
        assertThat(countBindings(fresh, role)).isZero();
    }

    @Test
    void shouldKeepOtherProvidersWindow_whenReplacingNullRelation() {
        Long id = binding(user, role, null, FUTURE, FUTURE.plusDays(10));
        Long related = binding(user, role, role("relation"), FUTURE, FUTURE.plusDays(10));
        Long other = role("other");
        mutex(role, other);
        assertThatThrownBy(() -> assign(
                item("user", "role", null, FUTURE.plusDays(20), FUTURE.plusDays(30)),
                item("user", "other", null, FUTURE, FUTURE.plusDays(10))))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode());
        assertWindow(id, FUTURE, FUTURE.plusDays(10));
        assertWindow(related, FUTURE, FUTURE.plusDays(10));
        assertThat(countBindings(user, other)).isZero();
    }

    @Test
    void shouldRollbackRenewal_whenInsertFailsAfterWindowUpdate() {
        Long id = binding(user, role, null, null, PAST);
        Long other = role("other");
        jdbc.execute("CREATE FUNCTION fail_reassignment_insert() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN IF NEW.target_id = " + other + " THEN RAISE EXCEPTION 'reassignment fault'; END IF; "
                + "RETURN NEW; END $$");
        try {
            jdbc.execute("CREATE TRIGGER fail_reassignment_insert BEFORE INSERT ON user_role "
                    + "FOR EACH ROW EXECUTE FUNCTION fail_reassignment_insert()");
            assertThatThrownBy(() -> assign(item("user", "role", null, null, null),
                    item("user", "other", null, null, null)))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasStackTraceContaining("reassignment fault");
            assertWindow(id, null, PAST);
            assertThat(countBindings(user, other)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_reassignment_insert ON user_role");
            jdbc.execute("DROP FUNCTION fail_reassignment_insert()");
        }
    }

    @Test
    void shouldInsertNullBindingAndDeduplicateRetry_whenOnlyRelatedBindingExists() {
        Long old = binding(user, role, role("relation"), null, PAST);
        var request = item("user", "role", null, null, null);
        assign(request, request);
        assertThat(countBindings(user, role)).isEqualTo(2);
        assertWindow(old, null, PAST);
    }

    @Test
    void shouldProtectLocalProjection_whenNullBindingWouldBeUpdated() {
        Long id = binding(user, role, null, null, PAST);
        jdbc.update("UPDATE user_role SET owner_service_code = 'access-service' WHERE id = ?", id);
        assertThatThrownBy(() -> assign(item("user", "role", null, null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(AccessErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
        assertWindow(id, null, PAST);
    }

    private UserAssignRoleReq.AssignItem item(String subject, String target, Long relation,
                                               LocalDateTime from, LocalDateTime to) {
        return new UserAssignRoleReq.AssignItem("USER", prefix + subject, null, "BASIC_ROLE",
                prefix + target, relation, from, to);
    }

    private Long user(String suffix) {
        return jdbc.queryForObject("INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled) "
                + "VALUES (?, 1, ?, ?, true) RETURNING id", Long.class, TENANT, prefix + suffix, suffix);
    }

    private Long role(String suffix) {
        return jdbc.queryForObject("INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status) "
                + "VALUES (?, 6, ?, ?, 1) RETURNING id", Long.class, TENANT, prefix + suffix, suffix);
    }

    private Long binding(Long subject, Long target, Long relation, LocalDateTime from, LocalDateTime to) {
        return jdbc.queryForObject("INSERT INTO user_role "
                + "(tenant_id, abstract_user_id, target_type, target_id, relation_id, valid_from, valid_to) "
                + "VALUES (?, ?, 'ROLE', ?, ?, ?, ?) RETURNING id", Long.class,
                TENANT, subject, target, relation, from, to);
    }

    private void assertWindow(Long id, LocalDateTime from, LocalDateTime to) {
        var windows = jdbc.query("SELECT valid_from, valid_to FROM user_role WHERE id = ?", (rs, row) -> {
            var start = rs.getObject(1, java.time.OffsetDateTime.class);
            var end = rs.getObject(2, java.time.OffsetDateTime.class);
            return new Window(start == null ? null : start.withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime(),
                    end == null ? null : end.withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime());
        }, id);
        assertThat(windows).containsExactly(new Window(from, to));
    }

    private record Window(LocalDateTime from, LocalDateTime to) {}

    private long countBindings(Long subject, Long target) {
        return jdbc.queryForObject("SELECT count(*) FROM user_role WHERE tenant_id = ? "
                + "AND abstract_user_id = ? AND target_id = ? AND delete_flag = 0", Long.class, TENANT, subject, target);
    }

    private void mutex(Long first, Long second) {
        jdbc.update("INSERT INTO permission_conflict_rule "
                + "(tenant_id, conflict_type, first_abstract_role_id, second_abstract_role_id) "
                + "VALUES (?, 'ROLE_MUTEX', ?, ?)", TENANT, Math.min(first, second), Math.max(first, second));
    }
}
