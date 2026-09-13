package cn.ac.fage.accessmesh.access.bootstrap;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.user.dto.req.AbstractUserCreateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.AbstractUserUpdateReq;
import cn.ac.fage.accessmesh.access.user.dto.resp.AbstractUserResp;
import cn.ac.fage.accessmesh.access.user.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 空库首管理员经 perm 轨主体端点由恒拒转放行（T-ACCESS-034 正向锁，真实 PG + Redis）。
 * <p>
 * bootstrap 固定图 USER 段本无 MANAGE 位（六细码）——旧实现（updateUser/deleteUsers 查
 * USER:MANAGE）下空库首管理员经 /api/perm/abstract-user/update|remove 对任何非自身用户
 * 恒拒，两入口在空库不可用（死锁）。本任务换绑 UPDATE/DELETE/ENABLE 后由拒转放行是
 * <b>预期的行为变化</b>，非等价改写：本用例在旧实现下必红（SecurityException 恒拒），
 * 换绑后走通「创建外部用户 → 改名/改启停 → 删除」全链。
 * </p>
 */
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
    "access.bootstrap.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class FirstAdminUserTrackPgIT {

    private static final Long TENANT = BootstrapGraphDefinition.TENANT_ID;
    private static final String BOOTSTRAP_PASSWORD = "FirstAdmin-IT-2026!";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, FirstAdminUserTrackPgIT.class);
    }

    @Autowired
    private AccessBootstrapInitializer initializer;
    @Autowired
    private UserManageAppService userManageAppService;
    @Autowired
    private PermQueryEngine permQueryEngine;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("空库首管理员经 abstract-user update/remove 由恒拒转放行（旧实现 MANAGE 门禁下本用例必红）")
    void firstAdminShouldPassUserTrackUpdateAndRemove() {
        // 空库 bootstrap 固定图（本测试类独享数据库，状态①）
        initializer.initialize(BOOTSTRAP_PASSWORD);

        Long adminSubjectId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        assertThat(adminSubjectId).isNotNull();

        // 反证锁：固定图对 USER:MANAGE 零持有（操作位种子已随 T-ACCESS-034 删除，
        // 引擎对该码 fail-closed 拒绝）——放行只能来自换绑后的细码门禁，而非 MANAGE 残留
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, adminSubjectId, "USER", null, "MANAGE")).isFalse();

        bindOperator(adminSubjectId);

        // 首管理员经管理角色持有 USER:CREATE（固定图）→ 创建外部主体
        AbstractUserResp target = userManageAppService.createUser(
            TENANT, new AbstractUserCreateReq("USER", "t034-first-admin-target", "放行锁目标用户", true, null));
        assertThat(target.id()).isNotNull();

        // updateUser 字段分档：name-only 查 USER:UPDATE——旧实现查 MANAGE 恒拒，此处必须放行
        AbstractUserResp renamed = userManageAppService.updateUser(
            TENANT, new AbstractUserUpdateReq(target.id(), "放行锁目标用户-改名", null, null));
        assertThat(renamed.name()).isEqualTo("放行锁目标用户-改名");

        // updateUser 字段分档：enabled-only 查 USER:ENABLE——同样由拒转放行
        AbstractUserResp disabled = userManageAppService.updateUser(
            TENANT, new AbstractUserUpdateReq(target.id(), null, false, null));
        assertThat(disabled.enabled()).isFalse();

        // deleteUsers 换绑 USER:DELETE——旧实现查 MANAGE 恒拒，此处必须放行且投影同事务软删
        userManageAppService.deleteUsers(TENANT, List.of(target.id()));
        assertThat(jdbc.queryForObject(
            "SELECT delete_flag FROM abstract_user WHERE tenant_id = ? AND id = ?",
            Long.class, TENANT, target.id())).isNotZero();
        assertThat(jdbc.queryForObject(
            "SELECT delete_flag FROM resource_entity WHERE tenant_id = ? AND resource_type = 6 AND code = ?",
            Long.class, TENANT, String.valueOf(target.id()))).isNotZero();
    }

    private void bindOperator(Long operatorId) {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, operatorId));
    }
}
