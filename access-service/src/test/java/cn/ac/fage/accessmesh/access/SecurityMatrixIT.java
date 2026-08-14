package cn.ac.fage.accessmesh.access;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.CallerType;
import cn.ac.fage.accessmesh.access.permission.scheduler.UserRoleOrphanCleanupTask;
import cn.ac.fage.accessmesh.access.permission.service.AbstractUserSyncAppService;
import cn.ac.fage.accessmesh.access.permission.service.DomainConfigAppService;
import cn.ac.fage.accessmesh.common.mybatis.TenantIdProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全策略矩阵集成测试（T-ACCESS-004 验收：按公开认证、用户管理、Gateway 权限查询、
 * 外部 sync/full-sync 和权限管理建立可测试的安全策略矩阵）。
 * <p>
 * 全量 Spring Context + 真实安全拦截器链（InternalApiSecret → HeaderSignature →
 * RequestContext），业务 service 层 @MockBean（矩阵验证目标为身份层与上下文绑定）。
 * </p>
 * <table>
 *   <tr><th>入口分类</th><th>用例</th><th>期望</th></tr>
 *   <tr><td>公开认证 /auth/**</td><td>匿名 /auth/captcha</td><td>200 ANONYMOUS</td></tr>
 *   <tr><td>运维 /actuator/**</td><td>匿名 /actuator/health</td><td>200 ANONYMOUS（G4）</td></tr>
 *   <tr><td>用户管理</td><td>无会话 /user/page</td><td>401 显式门禁（G3）</td></tr>
 *   <tr><td>外部 sync/full-sync</td><td>内部凭证 + sourceService 匹配</td><td>200 SERVICE + serviceCode 绑定（G2）</td></tr>
 *   <tr><td>外部 sync/full-sync</td><td>无内部凭证</td><td>403</td></tr>
 *   <tr><td>权限管理</td><td>内部凭证 + 伪造 X-User-Id 无签名</td><td>403（G1）</td></tr>
 *   <tr><td>权限管理</td><td>内部凭证 + X-User-Id 有效签名</td><td>200 USER（验签才绑定操作者）</td></tr>
 *   <tr><td>跨请求</td><td>租户串扰</td><td>请求间上下文隔离</td></tr>
 * </table>
 */
@SpringBootTest(
    classes = {AccessServiceApplication.class, AccessServiceApplicationTest.TestDataSourceConfig.class},
    // MOCK：模拟 Servlet Web 环境（不启动真实端口），@AutoConfigureMockMvc 依赖 WebApplicationContext
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,com.alibaba.cloud.nacos.NacosConfigAutoConfiguration,com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-security-matrix",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-security-matrix",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-security-matrix"
})
class SecurityMatrixIT {

    private static final String SIGN_SECRET = "test-signature-secret-for-security-matrix";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-security-matrix";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantIdProvider tenantIdProvider;

    @MockBean
    private UserRoleOrphanCleanupTask userRoleOrphanCleanupTask;

    /** 认证业务层 mock（/auth/captcha 走真实服务会触达 Redis——mock 连接工厂无 connection）。 */
    @MockBean
    private cn.ac.fage.accessmesh.access.admin.service.AuthService authService;

    /** sync 业务层 mock（矩阵验证目标为身份层放行）。 */
    @MockBean
    private AbstractUserSyncAppService abstractUserSyncAppService;

    /** 权限管理业务层 mock（矩阵验证目标为身份层放行）。 */
    @MockBean
    private DomainConfigAppService domainConfigAppService;

    @BeforeEach
    void mockTenantIds() {
        when(tenantIdProvider.getTenantIds()).thenReturn(java.util.Collections.emptySet());
    }

    @Test
    @DisplayName("公开认证：/auth/** 匿名可访问（ANONYMOUS 上下文）")
    void authPublicPath_allowsAnonymous() throws Exception {
        mockMvc.perform(post("/auth/captcha"))
            .andExpect(status().isOk());
        assertThat(AccessRequestContext.getCallerType()).isNull();
    }

    @Test
    @DisplayName("运维：/actuator/** 匿名可访问（G4 修复：不再强制 X-Tenant-Id）")
    void actuatorPath_allowsAnonymous() throws Exception {
        // health 端点组件检查（Redis mock → DOWN → 503）属正常语义；
        // 矩阵验证目标为：匿名不被拦截器拒绝（非 401/403/400）。
        var result = mockMvc.perform(get("/actuator/health")).andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403, 400);
    }

    @Test
    @DisplayName("用户管理：无会话 /user/** → 401 显式门禁（G3 修复：不依赖服务层隐式异常）")
    void userAdmin_withoutSession_rejected401() throws Exception {
        mockMvc.perform(post("/user/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("评审 P1-1：会话型认证端点 /auth/userinfo 无会话 → 401（不再匿名放行）")
    void authSessionEndpoint_withoutLogin_rejected401() throws Exception {
        mockMvc.perform(post("/auth/userinfo"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("评审 P3：精确路径 /actuator 匿名放行（公开契约一致）")
    void actuatorRootPath_allowsAnonymous() throws Exception {
        var result = mockMvc.perform(get("/actuator")).andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403, 400);
    }

    // 评审 P1-1（/error ERROR dispatch 不 401 掩蔽）由 RequestContextInterceptorTest 单测覆盖
    // （MockHttpServletRequest.setDispatcherType(ERROR) 直接验证拦截器分支）

    @Test
    @DisplayName("外部 sync：内部凭证 + sourceService 与 X-Service-Code 一致 → 身份层放行（G2）")
    void sync_withInternalSecret_bindsServiceContext() throws Exception {
        mockMvc.perform(post("/api/perm/abstract-user/sync")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-Service-Code", "example-service")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operation\":\"UPSERT\",\"subjectTypeCode\":\"USER\","
                    + "\"subjectExternalId\":\"u1\",\"name\":\"U1\",\"enabled\":true,"
                    + "\"sourceService\":\"example-service\",\"sourceEntityType\":\"user\","
                    + "\"sourceEntityId\":\"u1\","
                    + "\"syncVersion\":{\"occurredAt\":\"2026-01-01T00:00:00\",\"sequenceNo\":1}}"))
            .andExpect(status().isOk());
        // afterCompletion 已清理：此断言为泄漏检测；SERVICE 上下文绑定与 serviceCode 语义
        // 由 RequestContextInterceptorTest 单元覆盖（职责划分）
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("外部 sync：无内部凭证 → 403（服务身份认证强制）")
    void sync_withoutInternalSecret_rejected403() throws Exception {
        mockMvc.perform(post("/api/perm/abstract-user/sync")
                .header("X-Tenant-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("权限管理：内部凭证 + 伪造 X-User-Id 无签名 → 403（G1：验签才绑定操作者）")
    void permManage_withInternalSecretAndForgedUserId_rejected403() throws Exception {
        mockMvc.perform(post("/api/perm/domain-config/list")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-User-Id", "999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"domainCode\":\"HR\"}"))
            .andExpect(status().isForbidden());
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("权限管理：内部凭证 + X-User-Id 有效签名 → 身份层放行（验签才绑定操作者）")
    void permManage_withInternalSecretAndVerifiedUserId_allowed() throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String sig = hmac("100", "1", ts);
        mockMvc.perform(post("/api/perm/domain-config/list")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-User-Id", "100")
                .header("X-User-Signature", sig)
                .header("X-Signature-Timestamp", String.valueOf(ts))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"domainCode\":\"HR\"}"))
            .andExpect(status().isOk());
        // 上下文绑定语义（验签才绑定操作者）由 RequestContextInterceptorTest 单元覆盖；
        // perform 返回后 afterCompletion 已清理
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("跨请求隔离：前一请求 afterCompletion 清理后，后一请求绑定自己的租户（防租户串扰）")
    void requestIsolation_preventsTenantCrosstalk() throws Exception {
        // 请求 A：租户 1（SERVICE 上下文）
        mockMvc.perform(post("/api/perm/abstract-user/sync")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-Service-Code", "svc-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operation\":\"UPSERT\",\"subjectTypeCode\":\"USER\","
                    + "\"subjectExternalId\":\"u1\",\"name\":\"U1\",\"enabled\":true,"
                    + "\"sourceService\":\"svc-a\",\"sourceEntityType\":\"user\","
                    + "\"sourceEntityId\":\"u1\","
                    + "\"syncVersion\":{\"occurredAt\":\"2026-01-01T00:00:00\",\"sequenceNo\":1}}"))
            .andExpect(status().isOk());
        // afterCompletion 已清理：同一线程上下文不得残留租户 1（防租户串扰的关键断言）
        assertThat(AccessRequestContext.getTenantId()).isNull();

        // 请求 B：租户 2（SERVICE 上下文）
        mockMvc.perform(post("/api/perm/abstract-user/sync")
                .header("X-Tenant-Id", "2")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-Service-Code", "svc-b")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operation\":\"UPSERT\",\"subjectTypeCode\":\"USER\","
                    + "\"subjectExternalId\":\"u1\",\"name\":\"U1\",\"enabled\":true,"
                    + "\"sourceService\":\"svc-b\",\"sourceEntityType\":\"user\","
                    + "\"sourceEntityId\":\"u1\","
                    + "\"syncVersion\":{\"occurredAt\":\"2026-01-01T00:00:00\",\"sequenceNo\":1}}"))
            .andExpect(status().isOk());
        // 请求 B 后上下文同样已清理（两次请求间无残留）
        assertThat(AccessRequestContext.get()).isNull();
    }

    /** 与生产相同算法生成 HMAC-SHA256（payload = userId|tenantId|timestamp）。 */
    private static String hmac(String userId, String tenantId, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = userId + "|" + tenantId + "|" + timestamp;
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
