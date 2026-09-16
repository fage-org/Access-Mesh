package cn.ac.fage.accessmesh.access.projection;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.projection.LocalProjectionDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地投影批量 SQL 的真实 PostgreSQL 验证（Testcontainers，Docker 可用时执行）。
 * <p>
 * 回归：批量 UPDATE VALUES 子句必须显式 CAST——全 unknown 参数的 VALUES 列表会被
 * PG 推断为 text 列，text→boolean 与 COALESCE(text, jsonb) 均报 42804；stringtype=unspecified
 * 不救 VALUES 推断。本测试在真实 PG 上执行 batchUpsertAdminUsers（已有行路径走 batchUpdateValues）
 * 与 batchDeleteAdminUsers（级联软删 user_role），Docker 不可用时由 Testcontainers 跳过。
 * </p>
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-batch-sql-0123456789",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-batch-sql",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-batch-sql"
})
class LocalProjectionBatchSqlIT {

    private static final Long TENANT = 1L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, LocalProjectionBatchSqlIT.class);
    }

    @Autowired
    private LocalProjectionDomainService localProjectionDomainService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("batchUpsert 已有行路径：batchUpdateValues 真实 PG 执行（JSONB extra 环回 + boolean enabled 不报 42804）")
    void batchUpsertUpdatesExistingRowsOnRealPostgres() {
        Long sysUserId = 900001L;

        // 第一次：插入新投影行。投影不写 extra（username 投影退役，T-ACCESS-035）——
        // flex 全列插入落显式 NULL（同 SysUser gender 显式赋值先例的反面），断言锁住「投影不携带 extra」
        Map<Long, Long> first = localProjectionDomainService.batchUpsertAdminUsers(TENANT,
            List.of(new LocalProjectionDomainService.UpsertUserKey(sysUserId, "张三", true)));
        assertThat(first).containsKey(sysUserId);
        Long abstractUserId = first.get(sysUserId);
        assertThat(abstractUserId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT extra FROM abstract_user WHERE id = ?", Object.class, abstractUserId)).isNull();

        // jdbc 直写构造非空 extra——锁 batchUpdateValues 的 COALESCE 值保留与 42804 非空
        // CAST 载体（SQL 级防御性质；LOCAL_USER 行无 permission 域写入口，非生产通道模拟）
        jdbcTemplate.update(
            "UPDATE abstract_user SET extra = CAST('{\"k\":\"perm-track\"}' AS JSONB) WHERE id = ?", abstractUserId);

        // 第二次：走已有行批量刷新（batchUpdateValues：CAST 后的 VALUES 单条 SQL）。
        // 若 VALUES 缺 CAST，PG 报 42804 整批回滚 → 本调用抛异常即测试失败。
        // 加载值 extra 非空 → CAST(#{u.extra} AS JSONB) 被非空参数覆盖（42804 载体保留）
        Map<Long, Long> second = localProjectionDomainService.batchUpsertAdminUsers(TENANT,
            List.of(new LocalProjectionDomainService.UpsertUserKey(sysUserId, "张三丰", false)));
        assertThat(second).containsEntry(sysUserId, abstractUserId);

        // 落库断言：name/enabled 已刷新；extra 经 COALESCE 环回保留 permission 域写入值（不被投影批量刷新清空）
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT name, enabled, extra FROM abstract_user WHERE id = ?", abstractUserId);
        assertThat(row.get("name")).isEqualTo("张三丰");
        assertThat(row.get("enabled")).isEqualTo(Boolean.FALSE);
        assertThat(String.valueOf(row.get("extra"))).contains("perm-track");

        Map<String, Object> resRow = jdbcTemplate.queryForMap(
            "SELECT name, status FROM resource_entity WHERE tenant_id = ? AND resource_type = 6"
                + " AND code = ? AND code_type = 'default' AND delete_flag = 0",
            TENANT, String.valueOf(sysUserId));
        assertThat(resRow.get("name")).isEqualTo("张三丰");
        assertThat(resRow.get("status")).isEqualTo(0);
    }

    @Test
    @DisplayName("batchUpsert 已有行 extra=NULL：真实 PG 批量刷新不报 42804 且保持 NULL（T-ACCESS-035 后新建行的生产形态）")
    void batchUpsertKeepsNullExtraOnRealPostgres() {
        Long sysUserId = 900003L;

        // 新建行：投影不写 extra → 显式 NULL（T-ACCESS-035 后全部新行形态）
        Map<Long, Long> first = localProjectionDomainService.batchUpsertAdminUsers(TENANT,
            List.of(new LocalProjectionDomainService.UpsertUserKey(sysUserId, "李四", true)));
        Long abstractUserId = first.get(sysUserId);
        assertThat(abstractUserId).isNotNull();

        // 已有行批量刷新：v.extra=NULL 走 CAST(NULL AS JSONB) + COALESCE——缺 CAST 时全 unknown
        // 参数推断为 text 的 42804 路径在该组合下同样可达，本用例补齐此前无真实 PG 覆盖的组合
        Map<Long, Long> second = localProjectionDomainService.batchUpsertAdminUsers(TENANT,
            List.of(new LocalProjectionDomainService.UpsertUserKey(sysUserId, "李四丰", false)));
        assertThat(second).containsEntry(sysUserId, abstractUserId);

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT name, enabled, extra FROM abstract_user WHERE id = ?", abstractUserId);
        assertThat(row.get("name")).isEqualTo("李四丰");
        assertThat(row.get("enabled")).isEqualTo(Boolean.FALSE);
        assertThat(row.get("extra")).isNull();
    }

    @Test
    @DisplayName("batchDelete 级联：abstract_user + 全部 user_role + USER 资源同一事务软删")
    void batchDeleteCascadesUserRoles() {
        Long sysUserId = 900002L;
        Map<Long, Long> ids = localProjectionDomainService.batchUpsertAdminUsers(TENANT,
            List.of(new LocalProjectionDomainService.UpsertUserKey(sysUserId, "级联测试", true)));
        Long abstractUserId = ids.get(sysUserId);

        // 造一条功能角色关系（非 ORG/POSITION 成员关系，验证级联覆盖）
        jdbcTemplate.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id, relation_id,"
                + " owner_service_code, created_at, updated_at, delete_flag)"
                + " VALUES (?, ?, 'ROLE', 5001, 5001, 'access-service', now(), now(), 0)",
            TENANT, abstractUserId);

        localProjectionDomainService.batchDeleteAdminUsers(TENANT, Set.of(sysUserId));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM abstract_user WHERE id = ? AND delete_flag = 0", Integer.class, abstractUserId))
            .isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE abstract_user_id = ? AND delete_flag = 0",
            Integer.class, abstractUserId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_entity WHERE tenant_id = ? AND resource_type = 6"
                + " AND code = ? AND code_type = 'default' AND delete_flag = 0",
            Integer.class, TENANT, String.valueOf(sysUserId))).isZero();
    }
}
