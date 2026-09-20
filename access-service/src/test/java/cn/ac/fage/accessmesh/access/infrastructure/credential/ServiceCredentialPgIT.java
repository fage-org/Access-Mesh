package cn.ac.fage.accessmesh.access.infrastructure.credential;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.CallerType;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContextInterceptor;
import cn.ac.fage.accessmesh.access.infrastructure.SecurityAttributes;
import cn.ac.fage.accessmesh.access.infrastructure.ServicePrincipal;
import cn.ac.fage.accessmesh.access.infrastructure.config.ServiceAuthArbiter;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.service.domain.ServiceConfigDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.Tag;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务凭证容器轨全链回归锁（T-PERM-070）：issue 落库（真实 uk/索引）→ verify 三态
 * （20065/20066/20067/20068 真实 DB 行）→ 仲裁器 preHandle（真实凭证头 → principal）→
 * 上下文拦截器绑定 SERVICE（凭证行派生）。管理面 AppService 编排（门禁/服务注册校验）
 * 由单测锁定，本 IT 聚焦存储与认证链。
 */
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
})
class ServiceCredentialPgIT {

    private static final Long TENANT = 1L;
    private static final String SERVICE_CODE = "t070-example";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, ServiceCredentialPgIT.class);
    }

    @Autowired
    private ServiceCredentialDomainService domainService;
    @Autowired
    private ServiceConfigDomainService serviceConfigDomainService;
    @Autowired
    private ServiceAuthArbiter arbiter;
    @Autowired
    private RequestContextInterceptor requestContextInterceptor;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        jdbc.update("DELETE FROM service_credential WHERE service_code = ?", SERVICE_CODE);
        jdbc.update("DELETE FROM service_config WHERE service_code = ?", SERVICE_CODE);
    }

    private void insertActiveService() {
        jdbc.update("""
            INSERT INTO service_config (tenant_id, service_code, name, status, delete_flag, created_at, updated_at)
            VALUES (?, ?, 't070 example', 1, 0, now(), now())
            """, TENANT, SERVICE_CODE);
    }

    @Test
    @DisplayName("全链：issue 落库 → verify 绿 → 仲裁器 principal → 上下文绑定 SERVICE（凭证行派生）")
    void fullChain_issue_verify_arbiter_bindContext() throws Exception {
        insertActiveService();
        ServiceCredentialDomainService.IssuedCredential issued =
            domainService.issue(TENANT, SERVICE_CODE, null, 100L);

        // 库行断言：credential_id 全局唯一键落库、哈希非明文、状态启用
        String hash = jdbc.queryForObject(
            "SELECT secret_hash FROM service_credential WHERE credential_id = ?",
            String.class, issued.entity().getCredentialId());
        assertThat(hash).isNotBlank().isNotEqualTo(issued.plainSecret());

        // verify 绿
        ServiceCredentialDomainService.VerifyResult verify =
            domainService.verify(issued.entity().getCredentialId(), issued.plainSecret());
        assertThat(verify.success()).isTrue();
        assertThat(verify.principal().tenantId()).isEqualTo(TENANT);
        assertThat(verify.principal().serviceCode()).isEqualTo(SERVICE_CODE);

        // 仲裁器：真实凭证头 + M2M 白名单路径 → 放行并写 principal（自报头并存不采信）
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/resource-entity/sync");
        req.addHeader("X-Credential-Id", issued.entity().getCredentialId());
        req.addHeader("X-Credential-Secret", issued.plainSecret());
        req.addHeader("X-Service-Code", "attacker");
        req.addHeader("X-Tenant-Id", "999");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(arbiter.preHandle(req, resp, new Object())).isTrue();
        assertThat(req.getAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL))
            .isEqualTo(verify.principal());

        // 上下文拦截器：principal → SERVICE 绑定（凭证行派生，忽略自报头）
        assertThat(requestContextInterceptor.preHandle(req, resp, new Object())).isTrue();
        RequestContext ctx = AccessRequestContext.snapshot();
        assertThat(ctx.callerType()).isEqualTo(CallerType.SERVICE);
        assertThat(ctx.tenantId()).isEqualTo(TENANT);
        assertThat(ctx.serviceCode()).isEqualTo(SERVICE_CODE);
    }

    @Test
    @DisplayName("三态：停用→20067；过期→20066；服务停用→20068；删除→20065（真实 DB 行状态变迁）")
    void verifyStates_afterLifecycleTransitions() {
        insertActiveService();
        ServiceCredentialDomainService.IssuedCredential first =
            domainService.issue(TENANT, SERVICE_CODE, null, 100L);
        ServiceCredentialDomainService.IssuedCredential second =
            domainService.issue(TENANT, SERVICE_CODE, null, 100L);

        // 停用 → 20067（轮换收尾：新凭证并存生效后再停旧）
        assertThat(domainService.changeStatus(TENANT, first.entity().getId(), 0, 100L)).isEqualTo(1);
        assertThat(domainService.verify(first.entity().getCredentialId(), first.plainSecret()).failure())
            .isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_DISABLED);
        // 停用标记 rotated_at 已记
        LocalDateTime rotatedAt = jdbc.queryForObject(
            "SELECT rotated_at FROM service_credential WHERE id = ?", LocalDateTime.class, first.entity().getId());
        assertThat(rotatedAt).isNotNull();
        // 并存语义：第二张凭证不受影响
        assertThat(domainService.verify(second.entity().getCredentialId(), second.plainSecret()).success()).isTrue();

        // 过期 → 20066
        jdbc.update("UPDATE service_credential SET expires_at = now() - interval '1 minute' WHERE id = ?",
            second.entity().getId());
        assertThat(domainService.verify(second.entity().getCredentialId(), second.plainSecret()).failure())
            .isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_EXPIRED);

        // 服务停用 → 20068（凭证本身有效）
        ServiceCredentialDomainService.IssuedCredential third =
            domainService.issue(TENANT, SERVICE_CODE, null, 100L);
        jdbc.update("UPDATE service_config SET status = 0 WHERE service_code = ?", SERVICE_CODE);
        assertThat(domainService.verify(third.entity().getCredentialId(), third.plainSecret()).failure())
            .isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_SERVICE_INACTIVE);
        jdbc.update("UPDATE service_config SET status = 1 WHERE service_code = ?", SERVICE_CODE);

        // 删除 → 20065
        assertThat(domainService.remove(TENANT, third.entity().getId(), 100L)).isEqualTo(1);
        assertThat(domainService.verify(third.entity().getCredentialId(), third.plainSecret()).failure())
            .isEqualTo(AccessErrorCode.SERVICE_CREDENTIAL_INVALID);
    }

    @Test
    @DisplayName("credential_id 全局唯一约束：跨租户插同 id 撞 uk（认证先于租户解析的定位唯一性兜底）")
    void globalUniqueness_acrossTenants() {
        insertActiveService();
        ServiceCredentialDomainService.IssuedCredential issued =
            domainService.issue(TENANT, SERVICE_CODE, null, 100L);
        // 直接插第二行同 credential_id（跨租户）应撞全局唯一索引
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            jdbc.update("""
                INSERT INTO service_credential (tenant_id, service_code, credential_id, secret_hash, status, delete_flag, created_at, updated_at)
                VALUES (?, 'other-service', ?, 'x', 1, 0, now(), now())
                """, TENANT + 1, issued.entity().getCredentialId()))
            .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
