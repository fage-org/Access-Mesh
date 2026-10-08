package cn.ac.fage.accessmesh.access.bootstrap;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("testcontainers")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker=true)
@TestPropertySource(properties={"spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false","spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false","accessmesh.sync.scheduler.enabled=false",
    "access.platform.bootstrap.enabled=false","access.tenant.gate-repair.enabled=false"})
class TenantOpeningPgIT {
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry,TenantOpeningPgIT.class); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformBootstrapInitializer bootstrap;
    @Autowired cn.ac.fage.accessmesh.access.tenant.service.TenantGateRepairAppService repair;
    @org.springframework.boot.test.mock.mockito.SpyBean cn.ac.fage.accessmesh.common.security.RedisTenantGateStore gates;
    @Autowired cn.ac.fage.accessmesh.access.tenant.service.TenantAppService tenants;
    private String platformToken;

    @BeforeEach
    void loginPlatform() throws Exception {
        // 通用 ItInfra 的租户 1 类型夹具不属于此产品开通旅程；本类首次使用前移除。
        if (jdbc.queryForObject("SELECT count(*) FROM sys_tenant",Long.class)==0) {
            jdbc.update("DELETE FROM operation_permission WHERE tenant_id=1");
            jdbc.update("DELETE FROM type_definition WHERE tenant_id=1");
            jdbc.update("DELETE FROM system_config WHERE tenant_id=1");
        }
        bootstrap.initialize("operator","Operator-pass123");
        JsonNode captcha=call("/api/access/platform-auth/captcha",Map.of()).path("data");
        String id=captcha.path("captchaId").asText();
        JsonNode login=call("/api/access/platform-auth/login",Map.of("username","operator","password","Operator-pass123",
            "captchaId",id,"captchaCode",redis.opsForValue().get("captcha:"+id)));
        assertThat(login.path("code").asInt()).isEqualTo(200);
        platformToken=login.path("data").path("accessToken").asText();
    }

    @Test
    void shouldCreateTwoIsolatedTenantsWithCompleteBaselineAndOneTimeCredentials() throws Exception {
        String code="tenant-"+UUID.randomUUID().toString().substring(0,8);
        JsonNode first=call("/api/access/tenant/create",Map.of("code",code,"name","Tenant A"));
        assertThat(first.path("code").asInt()).as(first.toString()).isEqualTo(200);
        JsonNode data=first.path("data");
        long tenant=data.path("tenant").path("id").asLong();
        long admin=data.path("tenant").path("adminUserId").asLong();
        assertThat(tenant).isPositive();
        assertThat(data.path("tenant").path("accessState").asText()).isEqualTo("ENABLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM type_definition WHERE tenant_id=?",Long.class,tenant)).isEqualTo(33L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operation_permission WHERE tenant_id=?",Long.class,tenant)).isEqualTo(123L);
        assertThat(jdbc.queryForObject("SELECT force_reset_pwd FROM sys_user WHERE tenant_id=? AND id=?",Boolean.class,tenant,admin)).isTrue();
        String hash=jdbc.queryForObject("SELECT password FROM sys_user WHERE tenant_id=? AND id=?",String.class,tenant,admin);
        assertThat(cn.dev33.satoken.secure.BCrypt.checkpw(data.path("initialPassword").asText(),hash)).isTrue();
        JsonNode second=call("/api/access/tenant/create",Map.of("code",code+"-b","name","Tenant B"));
        assertThat(second.path("code").asInt()).as(second.toString()).isEqualTo(200);
        assertThat(second.path("data").path("tenant").path("id").asLong()).isNotEqualTo(tenant);
        assertThat(call("/api/access/tenant/create",Map.of("code",code,"name","duplicate")).path("code").asInt()).isEqualTo(11111);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_log WHERE action='TENANT_CREATE' AND target_tenant_id=?",Long.class,tenant)).isEqualTo(1L);
    }

    @Test
    void shouldRollbackTheWholeTenantGraphWhenAuditFails() throws Exception {
        String code="rollback-"+UUID.randomUUID().toString().substring(0,8);
        long before=jdbc.queryForObject("SELECT count(*) FROM sys_user",Long.class);
        jdbc.execute("ALTER TABLE platform_audit_log ADD CONSTRAINT tenant_audit_failure_probe CHECK(action <> 'TENANT_CREATE') NOT VALID");
        try {
            JsonNode response=call("/api/access/tenant/create",Map.of("code",code,"name","Rollback"));
            assertThat(response.path("code").asInt()).isNotEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_tenant WHERE code=?",Long.class,code)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_user",Long.class)).isEqualTo(before);
        } finally {
            jdbc.execute("ALTER TABLE platform_audit_log DROP CONSTRAINT tenant_audit_failure_probe");
        }
    }

    @Test
    void tenantCodeLoginRequiresDifferentPasswordAndFreshSessionAfterResume() throws Exception {
        String code="journey-"+UUID.randomUUID().toString().substring(0,8);
        JsonNode opened=call("/api/access/tenant/create",Map.of("code",code,"name","Journey"));
        assertThat(opened.path("code").asInt()).as(opened.toString()).isEqualTo(200);
        JsonNode tenant=opened.path("data").path("tenant");
        long id=tenant.path("id").asLong(),admin=tenant.path("adminUserId").asLong();
        String initial=opened.path("data").path("initialPassword").asText();
        JsonNode login=tenantLogin(code,initial);
        assertThat(login.path("forceResetPwd").asBoolean()).isTrue();
        String first=login.path("accessToken").asText();
        JsonNode forced=asTenant(first,"/api/access/notice/my-notices",Map.of());
        assertThat(forced.path("code").asInt()).isEqualTo(10012);
        assertThat(forced.path("requestId").isTextual()).isTrue();
        assertThat(forced.path("traceId").asText()).isEqualTo(forced.path("requestId").asText());
        assertThat(asTenant(first,"/api/access/auth/oauth2/authorize",Map.of()).path("code").asInt()).isEqualTo(10012);
        assertThat(asTenant(first,"/api/access/user/reset-password",Map.of("userId",admin,"newPassword",initial)).path("code").asInt()).isEqualTo(10011);
        assertThat(asTenant(first,"/api/access/user/reset-password",Map.of("userId",admin,"newPassword","Customer-pass123")).path("code").asInt()).isEqualTo(200);
        assertThat(asTenant(first,"/api/access/auth/userinfo",Map.of()).path("code").asInt()).isEqualTo(401);
        String current=tenantLogin(code,"Customer-pass123").path("accessToken").asText();
        assertThat(asTenant(current,"/api/access/notice/my-notices",Map.of()).path("code").asInt()).isEqualTo(200);
        assertThat(call("/api/access/tenant/update-status",Map.of("id",id,"status",0)).path("code").asInt()).isEqualTo(200);
        assertThat(asTenant(current,"/api/access/auth/userinfo",Map.of()).path("code").asInt()).isEqualTo(11112);
        assertThat(call("/api/access/tenant/update-status",Map.of("id",id,"status",1)).path("code").asInt()).isEqualTo(200);
        assertThat(asTenant(current,"/api/access/auth/userinfo",Map.of()).path("code").asInt()).isEqualTo(401);
        String fresh=tenantLogin(code,"Customer-pass123").path("accessToken").asText();
        assertThat(fresh).isNotEqualTo(current);
        assertThat(asTenant(fresh,"/api/access/auth/userinfo",Map.of()).path("code").asInt()).isEqualTo(200);
        assertThat(asTenant(current,"/api/access/auth/userinfo",Map.of()).path("code").asInt()).isEqualTo(401);
        redis.delete(cn.ac.fage.accessmesh.common.security.TenantGateProtocol.key(id));
        assertThat(asTenant(fresh,"/api/access/auth/userinfo",Map.of()).path("code").asInt()).isEqualTo(11113);
        repair.repair(java.util.List.of(id));
        assertThat(asTenant(fresh,"/api/access/auth/userinfo",Map.of()).path("code").asInt()).isEqualTo(200);
    }

    @Test
    void deletedOriginalAdminRecoveryIsRejectedAndAuditedWithoutReplacement() throws Exception {
        String code="deleted-"+UUID.randomUUID().toString().substring(0,8);
        JsonNode opened=call("/api/access/tenant/create",Map.of("code",code,"name","Deleted admin"));
        assertThat(opened.path("code").asInt()).isEqualTo(200);
        long id=opened.path("data").path("tenant").path("id").asLong();
        long admin=opened.path("data").path("tenant").path("adminUserId").asLong();
        jdbc.update("UPDATE sys_user SET delete_flag=id WHERE tenant_id=? AND id=?",id,admin);
        assertThat(call("/api/access/tenant/reset-admin-password",Map.of("id",id)).path("code").asInt()).isEqualTo(10001);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_user WHERE tenant_id=? AND delete_flag=0",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_log WHERE target_tenant_id=? AND action='TENANT_ADMIN_PASSWORD_RESET' AND outcome='FAILURE'",Long.class,id)).isEqualTo(1L);
    }

    @Test
    void delayedResumePublicationCannotOverwriteLaterSuspension() throws Exception {
        String code="race-"+UUID.randomUUID().toString().substring(0,8);
        long id=call("/api/access/tenant/create",Map.of("code",code,"name","Race")).path("data").path("tenant").path("id").asLong();
        assertThat(call("/api/access/tenant/update-status",Map.of("id",id,"status",0)).path("code").asInt()).isEqualTo(200);
        long operator=jdbc.queryForObject("SELECT id FROM platform_account WHERE username='operator'",Long.class);
        var reserved=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation->{
            var publication=(cn.ac.fage.accessmesh.common.security.RedisTenantGateStore.Publication)invocation.getArgument(0);
            var state=(cn.ac.fage.accessmesh.common.security.TenantGateState)invocation.getArgument(1);
            if(publication.tenantId()==id && state.status()==cn.ac.fage.accessmesh.common.security.TenantGateState.Status.ENABLED) {
                reserved.countDown();
                if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test publication barrier timed out");
            }
            return invocation.callRealMethod();
        }).when(gates).publish(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var resume=executor.submit(()->{
                cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext.bind(cn.ac.fage.accessmesh.access.infrastructure.RequestContext.platform(operator));
                try { return tenants.updateStatus(new cn.ac.fage.accessmesh.access.tenant.dto.TenantStatusReq(id,1)); }
                finally { cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext.clear(); }
            });
            try {
                assertThat(reserved.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(call("/api/access/tenant/update-status",Map.of("id",id,"status",0)).path("code").asInt()).isEqualTo(200);
            } finally { release.countDown(); }
            org.assertj.core.api.Assertions.assertThatThrownBy(()->resume.get(10,java.util.concurrent.TimeUnit.SECONDS))
                .hasCauseInstanceOf(cn.ac.fage.accessmesh.access.tenant.service.TenantGateUnavailableException.class);
            assertThat(gates.read(id).status()).isEqualTo(cn.ac.fage.accessmesh.common.security.TenantGateState.Status.DISABLED);
            assertThat(jdbc.queryForObject("SELECT status FROM sys_tenant WHERE id=?",Integer.class,id)).isZero();
        } finally {
            release.countDown();
            org.mockito.Mockito.doCallRealMethod().when(gates).publish(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void failedSuspensionRemainsBlockedUntilRepairAndDoesNotCommitEpoch() throws Exception {
        String code="rollback-state-"+UUID.randomUUID().toString().substring(0,8);
        long id=call("/api/access/tenant/create",Map.of("code",code,"name","State rollback")).path("data").path("tenant").path("id").asLong();
        jdbc.execute("ALTER TABLE platform_audit_log ADD CONSTRAINT tenant_disable_failure_probe CHECK(action <> 'TENANT_DISABLE') NOT VALID");
        try {
            assertThat(call("/api/access/tenant/update-status",Map.of("id",id,"status",0)).path("code").asInt()).isNotEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT status FROM sys_tenant WHERE id=?",Integer.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT session_epoch FROM sys_tenant WHERE id=?",Long.class,id)).isEqualTo(1L);
            assertThat(gates.read(id).status()).isEqualTo(cn.ac.fage.accessmesh.common.security.TenantGateState.Status.UNAVAILABLE);
            repair.repair(java.util.List.of(id));
            assertThat(gates.read(id).permitsSession(1)).isTrue();
        } finally { jdbc.execute("ALTER TABLE platform_audit_log DROP CONSTRAINT tenant_disable_failure_probe"); }
    }

    @Test
    void credentialRecoveryDoesNotEnableAdminOrRestoreRemovedRoles() throws Exception {
        String code="credentials-"+UUID.randomUUID().toString().substring(0,8);
        JsonNode opened=call("/api/access/tenant/create",Map.of("code",code,"name","Credentials only"));
        assertThat(opened.path("code").asInt()).isEqualTo(200);
        long id=opened.path("data").path("tenant").path("id").asLong();
        long admin=opened.path("data").path("tenant").path("adminUserId").asLong();
        jdbc.update("UPDATE sys_user SET status=0,force_reset_pwd=false WHERE tenant_id=? AND id=?",id,admin);
        jdbc.update("UPDATE user_role SET delete_flag=id WHERE tenant_id=? AND abstract_user_id=? AND delete_flag=0",id,admin);
        String counter="login:fail:"+id+":admin";
        redis.opsForValue().set(counter,"5",java.time.Duration.ofMinutes(30));
        JsonNode reset=call("/api/access/tenant/reset-admin-password",Map.of("id",id));
        assertThat(reset.path("code").asInt()).as(reset.toString()).isEqualTo(200);
        assertThat(reset.path("data").path("userStatus").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM sys_user WHERE tenant_id=? AND id=?",Integer.class,id,admin)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_role WHERE tenant_id=? AND abstract_user_id=? AND delete_flag=0",Long.class,id,admin)).isZero();
        assertThat(jdbc.queryForObject("SELECT force_reset_pwd FROM sys_user WHERE tenant_id=? AND id=?",Boolean.class,id,admin)).isTrue();
        assertThat(redis.hasKey(counter)).isFalse();
        String hash=jdbc.queryForObject("SELECT password FROM sys_user WHERE tenant_id=? AND id=?",String.class,id,admin);
        assertThat(cn.dev33.satoken.secure.BCrypt.checkpw(reset.path("data").path("password").asText(),hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_log WHERE action='TENANT_ADMIN_PASSWORD_RESET' AND outcome='SUCCESS' AND target_tenant_id=? AND target_id=?",Long.class,id,String.valueOf(admin))).isEqualTo(1L);
    }

    private JsonNode tenantLogin(String code,String password) throws Exception {
        JsonNode captcha=asTenant(null,"/api/access/auth/captcha",Map.of()).path("data");
        String id=captcha.path("captchaId").asText();
        JsonNode response=asTenant(null,"/api/access/auth/login",Map.of("tenantCode",code,"username","admin",
            "password",password,"captchaId",id,"captchaCode",redis.opsForValue().get("captcha:"+id)));
        assertThat(response.path("code").asInt()).as(response.toString()).isEqualTo(200);
        return response.path("data");
    }
    private JsonNode asTenant(String token,String path,Object body) throws Exception {
        var request=post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        if(token!=null) request.header("Authorization","Bearer "+token);
        return json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsByteArray());
    }

    private JsonNode call(String path,Object body) throws Exception {
        var request=post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        if(platformToken!=null) request.header("Authorization","Bearer "+platformToken);
        return json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsByteArray());
    }
}
