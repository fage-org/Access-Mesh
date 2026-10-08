package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.audit.mapper.PlatformAuditLogMapper;
import cn.ac.fage.accessmesh.access.audit.service.domain.impl.PlatformAuditDomainServiceImpl;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountCreateReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformStatusReq;
import cn.ac.fage.accessmesh.access.auth.mapper.PlatformAccountMapper;
import cn.ac.fage.accessmesh.access.auth.security.PlatformAccountGuard;
import cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore;
import cn.ac.fage.accessmesh.access.auth.service.domain.impl.PlatformAccountDomainServiceImpl;
import cn.ac.fage.accessmesh.access.auth.service.impl.PlatformAccountAppServiceImpl;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TimestamptzLocalDateTimeTypeHandler;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.bootstrap.PlatformBootstrapInitializer;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.secure.BCrypt;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 平台管理的真 SQL、事务和并发边界；身份由已验证平台上下文夹具提供。 */
@SpringJUnitConfig(PlatformAccountsPgIT.Config.class)
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class PlatformAccountsPgIT {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { ItInfra.register(registry, PlatformAccountsPgIT.class); }

    @Configuration
    @EnableTransactionManagement
    @Import({PlatformAccountDomainServiceImpl.class, PlatformAccountGuard.class,
        PlatformAuditDomainServiceImpl.class, PlatformAccountAppServiceImpl.class, PlatformBootstrapInitializer.class,
        LoginFailureStore.class})
    static class Config {
        @Bean DataSource dataSource(Environment env) {
            return new DriverManagerDataSource(env.getRequiredProperty("spring.datasource.url"),
                env.getRequiredProperty("spring.datasource.username"), env.getRequiredProperty("spring.datasource.password"));
        }
        @Bean JdbcTemplate jdbc(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisConnectionFactory(Environment env) {
            var config = new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                env.getRequiredProperty("spring.data.redis.host"), env.getRequiredProperty("spring.data.redis.port", Integer.class));
            config.setPassword(env.getRequiredProperty("spring.data.redis.password"));
            config.setDatabase(env.getRequiredProperty("spring.data.redis.database", Integer.class));
            return new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(config);
        }
        @Bean org.springframework.data.redis.core.StringRedisTemplate redis(org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory connectionFactory) {
            return new org.springframework.data.redis.core.StringRedisTemplate(connectionFactory);
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            var configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.getTypeHandlerRegistry().register(TimestamptzLocalDateTimeTypeHandler.class);
            configuration.addMapper(PlatformAccountMapper.class);
            configuration.addMapper(PlatformAuditLogMapper.class);
            var bean = new SqlSessionFactoryBean();
            bean.setDataSource(dataSource);
            bean.setConfiguration(configuration);
            // 语句自 2026-10-08 评审修复起迁 XML（§13.5），切片上下文需显式加载对应 XML
            bean.setMapperLocations(new ClassPathResource[]{
                new ClassPathResource("mapper/auth/PlatformAccountMapper.xml"),
                new ClassPathResource("mapper/audit/PlatformAuditLogMapper.xml")});
            return bean.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean PlatformAccountMapper accounts(SqlSessionTemplate session) { return session.getMapper(PlatformAccountMapper.class); }
        @Bean PlatformAuditLogMapper audit(SqlSessionTemplate session) { return session.getMapper(PlatformAuditLogMapper.class); }
    }

    @Autowired PlatformAccountAppService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformBootstrapInitializer bootstrap;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;

    @BeforeEach
    void seedAdministrators() {
        jdbc.update("DELETE FROM platform_audit_log");
        jdbc.update("DELETE FROM platform_account");
        jdbc.update("INSERT INTO platform_account(id,username,name,password) VALUES (7001,'operator-a','A','unused'),(7002,'operator-b','B','unused')");
    }

    @Test
    void shouldRollbackStatusAndGenerationWhenAuditInsertFails() {
        jdbc.execute("ALTER TABLE platform_audit_log ADD CONSTRAINT audit_failure_probe CHECK(action <> 'PLATFORM_ACCOUNT_STATUS_CHANGE')");
        var previous = AccessRequestContext.snapshot();
        try {
            AccessRequestContext.bind(RequestContext.platform(7001L));
            assertThatThrownBy(() -> service.updateStatus(new PlatformStatusReq(7002L, 0)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM platform_account WHERE id=7002", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT credential_version FROM platform_account WHERE id=7002", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_log", Long.class)).isZero();
        } finally {
            AccessRequestContext.restore(previous);
            jdbc.execute("ALTER TABLE platform_audit_log DROP CONSTRAINT audit_failure_probe");
        }
    }

    @Test
    void shouldSerializeConcurrentSelfDisableAndKeepOneAdministrator() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> disableSelf(7001L, ready, start));
            var second = executor.submit(() -> disableSelf(7002L, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("disabled", "last-admin");
        } finally {
            start.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_account WHERE status=1", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_log WHERE action='PLATFORM_ACCOUNT_STATUS_CHANGE'", Long.class)).isEqualTo(1L);
    }

    @Test
    void temporaryLoginLockDoesNotChangeLastEnabledAdministratorProtection() {
        redis.opsForValue().set(LoginFailureStore.platformKey("operator-b"),"5",java.time.Duration.ofMinutes(30));
        var previous=AccessRequestContext.snapshot();
        try {
            AccessRequestContext.bind(RequestContext.platform(7001L));
            service.updateStatus(new PlatformStatusReq(7001L,0));
            assertThat(jdbc.queryForObject("SELECT status FROM platform_account WHERE id=7001",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM platform_account WHERE id=7002",Integer.class)).isEqualTo(1);
            assertThat(redis.opsForValue().get(LoginFailureStore.platformKey("operator-b"))).isEqualTo("5");
        } finally { AccessRequestContext.restore(previous); redis.delete(LoginFailureStore.platformKey("operator-b")); }
    }

    @Test
    void shouldPersistOnlyHashAndAuditForNewAccountAndReset() {
        var previous = AccessRequestContext.snapshot();
        try {
            AccessRequestContext.bind(RequestContext.platform(7001L));
            var created = service.create(new PlatformAccountCreateReq("new-operator", "New operator"));
            long id = created.account().id();
            String firstHash = jdbc.queryForObject("SELECT password FROM platform_account WHERE id=?", String.class, id);
            assertThat(BCrypt.checkpw(created.initialPassword(), firstHash)).isTrue();
            assertThat(created.account().forceResetPwd()).isTrue();
            redis.opsForValue().set(LoginFailureStore.platformKey("new-operator"), "5");
            var reset = service.resetPassword(id);
            assertThat(redis.hasKey(LoginFailureStore.platformKey("new-operator"))).isFalse();
            String secondHash = jdbc.queryForObject("SELECT password FROM platform_account WHERE id=?", String.class, id);
            assertThat(BCrypt.checkpw(reset.password(), secondHash)).isTrue();
            assertThat(jdbc.queryForObject("SELECT credential_version FROM platform_account WHERE id=?", Long.class, id)).isEqualTo(2L);
            String audit = jdbc.queryForObject("SELECT string_agg(summary,';') FROM platform_audit_log WHERE target_id=?", String.class, Long.toString(id));
            assertThat(audit).doesNotContain(created.initialPassword(), reset.password(), firstHash, secondHash);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_log WHERE target_id=? AND operator_id=7001", Long.class, Long.toString(id))).isEqualTo(2L);
        } finally {
            AccessRequestContext.restore(previous);
        }
    }

    private String disableSelf(long id, CountDownLatch ready, CountDownLatch start) throws Exception {
        var previous = AccessRequestContext.snapshot();
        try {
            AccessRequestContext.bind(RequestContext.platform(id));
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start gate timed out");
            service.updateStatus(new PlatformStatusReq(id, 0));
            return "disabled";
        } catch (BizException exception) {
            if (exception.getErrorCode() != 11103) throw exception;
            return "last-admin";
        } finally {
            AccessRequestContext.restore(previous);
        }
    }

    @Test
    void shouldBootstrapOnlyOnePlatformAccountWithoutAnyTenant() {
        jdbc.update("DELETE FROM platform_account");
        bootstrap.initialize("first-operator", "first-secret-123");
        String hash = jdbc.queryForObject("SELECT password FROM platform_account WHERE username='first-operator'", String.class);
        assertThat(BCrypt.checkpw("first-secret-123", hash)).isTrue();
        bootstrap.initialize("another-name", "another-secret-456");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_account", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT password FROM platform_account WHERE username='first-operator'", String.class)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_tenant", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_user", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_log WHERE action='PLATFORM_BOOTSTRAP'", Long.class)).isEqualTo(1L);
    }

    @Test
    void shouldReloadActorAfterWaitingForAnotherAdministrator() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor(); var holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (var lock = holder.createStatement()) { lock.execute("SELECT pg_advisory_xact_lock(11103001)"); }
            try {
                var waiting = executor.submit(() -> {
                    var previous = AccessRequestContext.snapshot();
                    try {
                        AccessRequestContext.bind(RequestContext.platform(7002L));
                        return service.resetPassword(7001L);
                    } finally {
                        AccessRequestContext.restore(previous);
                    }
                });
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() ->
                    jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory'", Long.class) == 1L);
                // 请求已完成锁前身份检查，另一管理员此时提交停用。
                try (var update = holder.createStatement()) {
                    update.executeUpdate("UPDATE platform_account SET status=0,credential_version=credential_version+1 WHERE id=7002");
                }
                holder.commit();
                assertThatThrownBy(() -> waiting.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(SecurityException.class);
                assertThat(jdbc.queryForObject("SELECT credential_version FROM platform_account WHERE id=7001", Long.class)).isEqualTo(1L);
            } finally {
                holder.rollback();
            }
        }
    }
}
