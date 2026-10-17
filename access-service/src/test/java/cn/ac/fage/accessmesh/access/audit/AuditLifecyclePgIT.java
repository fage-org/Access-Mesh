package cn.ac.fage.accessmesh.access.audit;

import cn.ac.fage.accessmesh.access.platform.mapper.SysJobMapper;
import cn.ac.fage.accessmesh.access.audit.mapper.SysLoginLogMapper;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.infrastructure.MybatisFlexTenantConfig;
import cn.ac.fage.accessmesh.access.infrastructure.MybatisFlexTypeHandlerConfig;
import com.mybatisflex.spring.boot.v4.MybatisFlexAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ac.fage.accessmesh.access.audit.mapper.*;
import cn.ac.fage.accessmesh.access.audit.dto.req.*;
import cn.ac.fage.accessmesh.access.audit.config.AuditLogSettings;
import cn.ac.fage.accessmesh.access.audit.service.domain.impl.*;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditRetentionDomainService.Kind;
import cn.ac.fage.accessmesh.access.audit.service.impl.AuditRetentionAppServiceImpl;
import cn.ac.fage.accessmesh.access.audit.service.AuditRetentionAppService;
import cn.ac.fage.accessmesh.access.tenant.mapper.TenantMapper;
import cn.ac.fage.accessmesh.access.tenant.service.domain.impl.TenantDomainServiceImpl;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAccessGuard;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SysTaskExecutionMapper;
import cn.ac.fage.accessmesh.access.infrastructure.task.*;
import cn.ac.fage.accessmesh.access.infrastructure.task.impl.TaskExecutionDomainServiceImpl;
import cn.ac.fage.accessmesh.access.platform.service.domain.impl.DeploymentJobDomainServiceImpl;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
@Tag("testcontainers")
@SpringJUnitConfig(classes = AuditLifecyclePgIT.MapperContext.class, initializers = ConfigDataApplicationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class AuditLifecyclePgIT {

    @TestConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class, MybatisFlexAutoConfiguration.class, com.mybatisflex.spring.boot.v4.FlexTransactionAutoConfiguration.class})
    @Import({MybatisFlexTenantConfig.class, MybatisFlexTypeHandlerConfig.class,
        AuditExportDomainServiceImpl.class, AuditRetentionDomainServiceImpl.class,
        TenantDomainServiceImpl.class, TaskExecutionDomainServiceImpl.class,
        DeploymentJobDomainServiceImpl.class, AuditRetentionAppServiceImpl.class})
    @MapperScan(basePackageClasses = {OperationLogMapper.class, SysJobMapper.class, TenantMapper.class, SysTaskExecutionMapper.class})
    static class MapperContext {
        @org.springframework.context.annotation.Bean AuditLogSettings auditLogSettings() {
            return new AuditLogSettings(new org.springframework.mock.env.MockEnvironment()
                .withProperty("access.audit.retention.enabled", "true").withProperty("access.audit.retention.operation-days", "30"));
        }
        @org.springframework.context.annotation.Bean TenantAccessGuard tenantAccessGuard() { return mock(TenantAccessGuard.class); }
    }


    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, AuditLifecyclePgIT.class);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired OperationLogMapper operations;
    @Autowired PermissionChangeLogMapper changes;
    @Autowired SysLoginLogMapper logins;
    @Autowired AuditRetentionMapper retention;
    @Autowired SysJobMapper jobs;
    @Autowired AuditRetentionAppService cleanup;
    @Autowired TaskExecutionDomainService executions;
    @Autowired PlatformTransactionManager transactionManager;
    private final LocalDateTime cutoff = LocalDateTime.of(2026, 9, 17, 18, 0);

    @Test void exportUsesSameFiltersAndTenantAndStableOrdering() {
        jdbc.update("INSERT INTO operation_log (tenant_id,module,action,operator_id,target_type,request_id,created_at) VALUES (6101,'ADMIN','EDIT',9,'sys_user','trace', '2026-09-17 18:00:00+00'),(6101,'ADMIN','OTHER',9,'sys_user','trace',now()),(6102,'ADMIN','EDIT',9,'sys_user','trace',now())");
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            try (var rows = operations.exportByCondition(6101L,"ADMIN","EDIT",9L,cutoff,cutoff,"sys_user",10,"trace")) {
                var list = new java.util.ArrayList<cn.ac.fage.accessmesh.access.audit.entity.OperationLog>();
                rows.forEach(list::add);
                assertThat(list).hasSize(1);
                assertThat(list.getFirst().getRequestId()).isEqualTo("trace");
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
        });
        jdbc.update("INSERT INTO permission_change_log (request_id,tenant_id,entity_type,entity_id,operation,diff_snapshot,affected_abstract_user_ids,affected_abstract_role_ids,change_source,created_at) VALUES ('audit-test',6101,'ROLE',22,'UPDATE','{\"eventType\":\"ROLE_EDIT\"}', ARRAY[9]::bigint[],ARRAY[22]::bigint[],'MANUAL','2026-09-17 18:00:00+00')");
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            try (var rows = changes.exportByCondition(6101L,"ROLE",22L,9L,22L,cutoff,cutoff,List.of("ROLE_EDIT"),"MANUAL",10)) {
                assertThat(rows.iterator().next().getEntityId()).isEqualTo(22L);
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
        });
    }
    @Test void cursorSnapshotRemainsCompleteWhenCleanupCommitsBetweenFetches() {
        jdbc.update("INSERT INTO operation_log(request_id,tenant_id,module,action,created_at) SELECT 'audit-snapshot',6701,'ADMIN','OLD',now() FROM generate_series(1,100)");
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            try (var rows = operations.exportByCondition(6701L,null,null,null,null,null,null,101,null)) {
                var iterator = rows.iterator();
                assertThat(iterator.hasNext()).isTrue();
                iterator.next();
                try (var connection = java.sql.DriverManager.getConnection(ItInfra.jdbcUrl(AuditLifecyclePgIT.class),ItInfra.username(),ItInfra.password());
                     var statement = connection.createStatement()) {
                    assertThat(statement.executeUpdate("DELETE FROM operation_log WHERE tenant_id=6701")).isEqualTo(100);
                }
                int remaining = 0;
                while (iterator.hasNext()) { iterator.next(); remaining++; }
                assertThat(remaining).isEqualTo(99);
            } catch (java.io.IOException | java.sql.SQLException e) { throw new RuntimeException(e); }
        });
    }

    @Test void deletesAreTenantScopedBoundedAndKeepExactCutoffForAllThreeTables() {
        jdbc.update("INSERT INTO operation_log(request_id,tenant_id,module,action,created_at) SELECT 'audit-test',6201,'ADMIN','OLD', '2026-09-16 18:00:00+00'::timestamptz FROM generate_series(1,501)");
        jdbc.update("INSERT INTO operation_log(request_id,tenant_id,module,action,created_at) VALUES ('audit-test',6201,'ADMIN','BOUNDARY','2026-09-17 18:00:00+00'),('audit-test',6202,'ADMIN','OTHER','2026-09-16 18:00:00+00')");
        assertThat(retention.deleteOperations(6201L,cutoff)).isEqualTo(500);
        assertThat(retention.deleteOperations(6201L,cutoff)).isEqualTo(1);
        assertThat(retention.deleteOperations(6201L,cutoff)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operation_log WHERE tenant_id IN (6201,6202)",Integer.class)).isEqualTo(2);
        jdbc.update("INSERT INTO sys_login_log(tenant_id,username,login_type,status,login_at) VALUES (6201,'old','password',1,'2026-09-16 18:00:00+00'),(6201,'edge','password',1,'2026-09-17 18:00:00+00'),(6202,'other','password',1,'2026-09-16 18:00:00+00')");
        assertThat(retention.deleteLogins(6201L,cutoff)).isEqualTo(1);
        assertThat(logins.countByTenantId(6201L,cutoff,cutoff)).isEqualTo(1);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            try (var rows = logins.exportByCondition(6201L,cutoff,cutoff,10)) {
                assertThat(rows.iterator().next().getUsername()).isEqualTo("edge");
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
        });
        jdbc.update("INSERT INTO permission_change_log(change_source,request_id,tenant_id,entity_type,entity_id,operation,created_at) VALUES ('MANUAL','audit-test',6201,'ROLE',1,'UPDATE','2026-09-16 18:00:00+00'),('MANUAL','audit-test',6201,'ROLE',2,'UPDATE','2026-09-17 18:00:00+00'),('MANUAL','audit-test',6202,'ROLE',3,'UPDATE','2026-09-16 18:00:00+00')");
        assertThat(retention.deleteChanges(6201L,cutoff)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permission_change_log WHERE tenant_id IN (6201,6202)",Integer.class)).isEqualTo(2);
    }
    @Test void disabledTenantPausesAndStaleAttemptCannotDeleteAfterResume() {
        jdbc.update("INSERT INTO sys_tenant(id,code,name,status) VALUES (6301,'audit-pause','audit',0)");
        jdbc.update("INSERT INTO operation_log(request_id,tenant_id,module,action,created_at) VALUES ('audit-test',6301,'ADMIN','OLD','2026-09-16 18:00:00+00')");
        String key = executions.buildScheduledKey(11L,cutoff.plusDays(30));
        int attempt = executions.tryClaim(6301L,key,"audit-test");
        var context = new TaskExecutionContext(6301L,11L,key,attempt,cutoff.plusDays(30));
        assertThat(cleanup.deleteBatch(context,Kind.OPERATION,cutoff)).isZero();
        jdbc.update("UPDATE sys_tenant SET status=1 WHERE id=6301");
        jdbc.update("UPDATE sys_task_execution SET lease_until=now()-interval '1 second' WHERE tenant_id=6301");
        int next = executions.tryClaim(6301L,key,"audit-test-next");
        assertThatThrownBy(() -> cleanup.deleteBatch(context,Kind.OPERATION,cutoff)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operation_log WHERE tenant_id=6301",Integer.class)).isEqualTo(1);
        assertThat(cleanup.deleteBatch(new TaskExecutionContext(6301L,11L,key,next,context.scheduledTime()),Kind.OPERATION,cutoff)).isEqualTo(1);
    }
    @Test void batchHoldsTenantAndAttemptLocksUntilCommitAndRollbackRestoresDeletedRows() {
        assertThat(transactionManager).isInstanceOf(com.mybatisflex.spring.FlexTransactionManager.class);
        jdbc.update("INSERT INTO sys_tenant(id,code,name,status) VALUES (6501,'audit-lock','audit',1)");
        jdbc.update("INSERT INTO operation_log(request_id,tenant_id,module,action,created_at) VALUES ('audit-test',6501,'ADMIN','OLD','2026-09-16 18:00:00+00')");
        String key = executions.buildScheduledKey(12L,cutoff.plusDays(30));
        int attempt = executions.tryClaim(6501L,key,"audit-lock-test");
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            assertThat(cleanup.deleteBatch(new TaskExecutionContext(6501L,12L,key,attempt,cutoff.plusDays(30)),Kind.OPERATION,cutoff)).isEqualTo(1);
            assertLockedFromOtherConnection("SELECT id FROM sys_tenant WHERE id=6501 FOR UPDATE NOWAIT");
            assertLockedFromOtherConnection("SELECT id FROM sys_task_execution WHERE tenant_id=6501 FOR UPDATE NOWAIT");
            tx.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operation_log WHERE tenant_id=6501",Integer.class)).isEqualTo(1);
    }
    private void assertLockedFromOtherConnection(String sql) {
        // A separate thread also bypasses transaction-aware data source thread binding.
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try (var connection = java.sql.DriverManager.getConnection(ItInfra.jdbcUrl(AuditLifecyclePgIT.class), ItInfra.username(), ItInfra.password()); var statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.executeQuery(sql)).as(sql).isInstanceOf(java.sql.SQLException.class)
                    .satisfies(e -> assertThat(((java.sql.SQLException)e).getSQLState()).isEqualTo("55P03"));
            } catch (java.sql.SQLException e) { throw new RuntimeException(e); }
        }).orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
    }

    @Test void completeExportMasksBeforeCsvAndRejectsTooManyRows() {
        jdbc.update("INSERT INTO operation_log(request_id,tenant_id,module,action,summary,created_at) VALUES ('audit-test',6601,'ADMIN','EDIT','alice@example.com','2026-09-17 18:00:00+00'),('audit-test',6601,'ADMIN','EDIT','ordinary','2026-09-18 18:00:00+00')");
        var gate = mock(cn.ac.fage.accessmesh.access.engine.query.QueryGate.class);
        when(gate.hasPermissionByCode(6601L,9L,"OPERATION_LOG",null,"VIEW")).thenReturn(true);
        var domain = new AuditExportDomainServiceImpl(operations,changes,logins);
        var settings = new AuditLogSettings(new org.springframework.mock.env.MockEnvironment().withProperty("access.audit.export.max-rows","1"));
        var app = new cn.ac.fage.accessmesh.access.audit.service.impl.AuditExportAppServiceImpl(domain,gate,null,settings);
        cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext.bind(cn.ac.fage.accessmesh.access.infrastructure.RequestContext.user(6601L,9L));
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                var data = app.operations(new OperationLogExportReq(null,null,null,cutoff,cutoff,null,null));
                assertThat(new String(data, java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("alice@example.com").contains("ADMIN");
                assertThatThrownBy(() -> app.operations(new OperationLogExportReq(null,null,null,null,null,null,null)))
                    .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class).extracting("errorCode").isEqualTo(11201);
            });
        } finally { cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext.clear(); }
    }

    @Test void registrationIsIdempotentAndDisablingDoesNotAffectOrdinaryJobs() {
        jobs.upsertAuditRetentionJobs(List.of(6401L,6402L),"0 0 18 * * *");
        jobs.upsertAuditRetentionJobs(List.of(6401L,6402L),"0 0 19 * * *");
        jdbc.update("INSERT INTO sys_job(tenant_id,job_name,invoke_target,cron_expression) VALUES (6401,'ordinary','other.run','0 0 18 * * *')");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_job WHERE tenant_id IN (6401,6402)",Integer.class)).isEqualTo(3);
        jobs.disableAuditRetentionJobs();
        assertThat(jobs.selectValidByInvokeTarget(6401L,"auditRetentionTask.cleanup").getStatus()).isZero();
        assertThat(jobs.selectValidByInvokeTarget(6401L,"other.run").getStatus()).isEqualTo(1);
    }
}
