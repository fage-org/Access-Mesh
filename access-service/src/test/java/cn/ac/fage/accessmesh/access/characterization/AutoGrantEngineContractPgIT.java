package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.engine.dto.PermBatchQuery;
import cn.ac.fage.accessmesh.access.engine.dto.PermEvalContext;
import cn.ac.fage.accessmesh.access.engine.dto.PermQuery;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 078 校准物化结果的引擎消费契约；直接装配候选结果，不冒充 072 推导/撤销实现。 */
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
class AutoGrantEngineContractPgIT {
    private static final long TENANT = 1L;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 21, 12, 0);
    /** type_definition 终值分配表（schema 头部）：role_type BASIC_ROLE=6。 */
    private static final int ROLE_TYPE_BASIC_ROLE = 6;
    /** type_definition 终值分配表（schema 头部）：user_type USER=1。 */
    private static final int USER_TYPE_USER = 1;
    /**
     * 动态建型 type_value 起始段位：910 远高于种子 resource_type 终值（最大 31，见 schema
     * 头部分配表）与管理面自动分配段（全量行 max+1 顺延），避免与 bootstrap 种子及其他
     * 测试类动态建型撞 tenant+type_key+type_value 唯一约束。
     */
    private static final int CUSTOM_TYPE_VALUE_BASE = 910;
    private static final String TYPE_CODE_PREFIX = "AUTO_TEST_";
    /** operation_permission.binary_bit 测试位段（VIEW/UPDATE/EXPORT 各占独立位，掩码表达覆盖关系）。 */
    private static final long BIT_VIEW = 2L;
    private static final long BIT_UPDATE = 4L;
    private static final long BIT_EXPORT = 8L;
    private static int nextType = CUSTOM_TYPE_VALUE_BASE;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, AutoGrantEngineContractPgIT.class);
    }

    @Autowired private PermQueryEngine engine;
    @Autowired private JdbcTemplate jdbc;
    // 审计持久化异步实现另测；本类只锁定引擎发出的通知，避免异步 target spy 校验竞态。
    @MockBean private AuditDomainService audit;

    @Test
    void shouldKeepConditionsSpecificToEachCanonicalOperation() {
        Fixture f = fixture();
        operation(f, "VIEW", BIT_VIEW, 0);
        operation(f, "EXPORT", BIT_EXPORT, 0);
        long denied = condition(false);
        grant(f, BIT_VIEW, null, "AUTO_DEP");
        grant(f, BIT_EXPORT, denied, "AUTO_DEP");
        assertThat(check(f, "VIEW")).isTrue();
        assertThat(check(f, "EXPORT")).isFalse();
        assertThat(batch(f, "VIEW", "EXPORT")).containsExactly(true, false);
    }

    @Test
    void shouldKeepOrVariantsAndManualPermission_whenUnconditionalAutoFactIsRemoved() {
        Fixture f = fixture();
        operation(f, "VIEW", BIT_VIEW, 0);
        long falseCondition = condition(false);
        long trueCondition = condition(true);
        long unconditional = grant(f, BIT_VIEW, null, "AUTO_DEP");
        long falseFact = grant(f, BIT_VIEW, falseCondition, "AUTO_DEP");
        long trueFact = grant(f, BIT_VIEW, trueCondition, "AUTO_DEP");
        assertThat(check(f, "VIEW")).isTrue();
        removeFact(unconditional);
        assertThat(check(f, "VIEW")).as("无条件行移除后仍按条件变体 OR 判定").isTrue();
        removeFact(trueFact);
        assertThat(check(f, "VIEW")).as("仅剩不满足条件的自动事实").isFalse();
        long manual = grant(f, BIT_VIEW, null, "MANUAL");
        assertThat(check(f, "VIEW")).isTrue();
        removeFact(falseFact);
        assertThat(check(f, "VIEW")).as("独立 MANUAL 不依赖 AUTO_DEP 行").isTrue();
        assertThat(jdbc.queryForObject("SELECT delete_flag FROM role_resource_permission WHERE id=?",
                Long.class, manual)).isZero();
    }

    @Test
    void shouldApplyMutexToEachEntryPointCollection_withoutCompressingCoveredFacts() {
        Fixture f = fixture();
        long view = operation(f, "VIEW", BIT_VIEW, 0);
        long update = operation(f, "UPDATE", BIT_UPDATE, BIT_VIEW);
        grant(f, BIT_VIEW, null, "AUTO_DEP");
        grant(f, BIT_UPDATE, null, "AUTO_DEP");
        jdbc.update("""
                INSERT INTO permission_conflict_rule(tenant_id,conflict_type,first_operation_permission_id,second_operation_permission_id)
                VALUES (?,'PERM_MUTEX',?,?)
                """, TENANT, view, update);
        // VIEW 判定收集 VIEW 与覆盖它的 UPDATE；UPDATE 判定只收集 UPDATE。
        assertThat(check(f, "VIEW")).isFalse();
        assertThat(check(f, "UPDATE")).isTrue();
        verify(audit, times(1)).asyncRecordLog(any(AuditDomainService.OperationLogEntry.class));
        clearInvocations(audit);
        assertThat(batch(f, "VIEW", "UPDATE")).containsExactly(false, true);
        verify(audit, times(1)).asyncRecordLog(any(AuditDomainService.OperationLogEntry.class));
        clearInvocations(audit);
        PermQuery list = PermQuery.forUserView(TENANT, f.user());
        list.setEvalContext(context());
        assertThat(engine.query(list).instanceEntries()).isEmpty();
        verify(audit, times(1)).asyncRecordLog(any(AuditDomainService.OperationLogEntry.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_resource_permission WHERE abstract_role_id=? AND delete_flag=0",
                Integer.class, f.role())).as("互斥不改变已保留的物化事实").isEqualTo(2);
    }

    @Test
    void shouldKeepDistinctConditionIdentities_whenExpressionsAreEqual() {
        Fixture f = fixture();
        operation(f, "VIEW", BIT_VIEW, 0);
        long first = grant(f, BIT_VIEW, condition(true), "AUTO_DEP");
        long second = grant(f, BIT_VIEW, condition(true), "AUTO_DEP");
        PermQuery q = PermQuery.forAuthCheck(TENANT, f.user(), f.typeCode(), "target", "VIEW");
        q.setEvalContext(context());
        assertThat(engine.query(q).matchedPermissionIds()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void shouldNotInferTransitiveOperationCoverage() {
        Fixture f = fixture();
        operation(f, "VIEW", BIT_VIEW, 0);
        operation(f, "UPDATE", BIT_UPDATE, BIT_VIEW);
        operation(f, "EXPORT", BIT_EXPORT, BIT_UPDATE);
        grant(f, BIT_EXPORT, null, "AUTO_DEP");
        assertThat(batch(f, "VIEW", "UPDATE", "EXPORT")).containsExactly(false, true, true);
        // 独立 VIEW 事实必须保留，不能因 EXPORT 覆盖 UPDATE、UPDATE 覆盖 VIEW 将其压缩。
        grant(f, BIT_VIEW, null, "AUTO_DEP");
        assertThat(check(f, "VIEW")).isTrue();
    }

    @Test
    void shouldKeepMutuallyCoveringFactsAsDistinctOperations() {
        Fixture f = fixture();
        operation(f, "VIEW", BIT_VIEW, BIT_UPDATE);
        operation(f, "UPDATE", BIT_UPDATE, BIT_VIEW);
        long view = grant(f, BIT_VIEW, null, "AUTO_DEP");
        long update = grant(f, BIT_UPDATE, null, "AUTO_DEP");
        assertThat(batch(f, "VIEW", "UPDATE")).containsExactly(true, true);
        PermQuery q = PermQuery.forAuthCheck(TENANT, f.user(), f.typeCode(), "target", "VIEW");
        q.setEvalContext(context());
        assertThat(engine.query(q).matchedPermissionIds()).containsExactlyInAnyOrder(view, update);
    }

    private boolean check(Fixture f, String operation) {
        PermQuery query = PermQuery.forAuthCheck(TENANT, f.user(), f.typeCode(), "target", operation);
        query.setEvalContext(context());
        return engine.query(query).allowed();
    }

    private List<Boolean> batch(Fixture f, String... operations) {
        PermBatchQuery query = PermBatchQuery.forAuthCheckBatch(TENANT, f.user(),
                List.of(operations).stream().map(op -> new PermBatchQuery.Item(
                        f.typeCode(), "target", op, null, null, false)).toList());
        query.setEvalContext(context());
        return engine.queryBatch(query).outcomes().stream().map(item -> item.allowed()).toList();
    }

    private static PermEvalContext context() {
        return new PermEvalContext(null, AT, Map.of());
    }

    private Fixture fixture() {
        int type = nextType++;
        String key = UUID.randomUUID().toString();
        String typeCode = TYPE_CODE_PREFIX + type;
        jdbc.update("INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name) VALUES (?,'resource_type',?,?,'auto test')",
                TENANT, typeCode, type);
        long role = jdbc.queryForObject("INSERT INTO abstract_role(tenant_id,role_type,external_id,name) VALUES (?,?,?,'auto role') RETURNING id",
                Long.class, TENANT, ROLE_TYPE_BASIC_ROLE, key);
        long user = jdbc.queryForObject("INSERT INTO abstract_user(tenant_id,user_type,external_id,name) VALUES (?,?,?,'auto user') RETURNING id",
                Long.class, TENANT, USER_TYPE_USER, key);
        jdbc.update("INSERT INTO user_role(tenant_id,abstract_user_id,target_type,target_id) VALUES (?,?,'ROLE',?)", TENANT, user, role);
        long resource = jdbc.queryForObject("INSERT INTO resource_entity(tenant_id,resource_type,code,code_type,name) VALUES (?,?,'target','default','target') RETURNING id",
                Long.class, TENANT, type);
        return new Fixture(type, typeCode, role, user, resource);
    }

    private long operation(Fixture f, String code, long bit, long mask) {
        return jdbc.queryForObject("INSERT INTO operation_permission(tenant_id,resource_type,code,name,binary_bit,inherit_mask) VALUES (?,?,?,?,?,?) RETURNING id",
                Long.class, TENANT, f.type(), code, code, bit, mask);
    }

    private long condition(boolean satisfied) {
        String rules = satisfied ? "{\"logic\":\"AND\",\"items\":[]}"
                : "{\"logic\":\"AND\",\"items\":[{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\"2000-01-01\",\"end\":\"2001-01-01\"}}]}";
        return jdbc.queryForObject("INSERT INTO permission_condition(tenant_id,code,name,condition_rules,enabled,source) VALUES (?,?,'condition',?,true,'MANAGED') RETURNING id",
                Long.class, TENANT, UUID.randomUUID().toString(), rules);
    }

    private long grant(Fixture f, long bit, Long condition, String source) {
        return jdbc.queryForObject("""
                INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_entity_id,resource_type,
                granted_bits,condition_id,scope_all,can_grant,depend_on,grant_source)
                VALUES (?,?,?,?,?,?,false,false,NULL,?) RETURNING id
                """, Long.class, TENANT, f.role(), f.resource(), f.type(), bit, condition, source);
    }

    private void removeFact(long id) {
        jdbc.update("UPDATE role_resource_permission SET delete_flag=id,deleted_at=now() WHERE id=?", id);
    }

    private record Fixture(int type, String typeCode, long role, long user, long resource) {}
}
