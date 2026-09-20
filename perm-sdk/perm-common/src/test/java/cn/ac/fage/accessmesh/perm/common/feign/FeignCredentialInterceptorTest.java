package cn.ac.fage.accessmesh.perm.common.feign;

import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SDK 凭证头注入拦截器测试（T-PERM-070）：注入条件（/api/access/ 前缀）、
 * 显式头不覆盖、半配 fail-fast、启动声明式 TLS 护栏三态（2026-09-20 拍板：
 * 配置凭证必须显式声明 perm.allow-insecure，缺省拒启；true/false 均为有效声明）。
 */
class FeignCredentialInterceptorTest {

    private FeignCredentialInterceptor interceptor(String credentialId, String credentialSecret,
                                                   String allowInsecure) {
        FeignCredentialInterceptor interceptor = new FeignCredentialInterceptor();
        ReflectionTestUtils.setField(interceptor, "credentialId", credentialId);
        ReflectionTestUtils.setField(interceptor, "credentialSecret", credentialSecret);
        ReflectionTestUtils.setField(interceptor, "allowInsecure", allowInsecure);
        return interceptor;
    }

    @Test
    @DisplayName("配置齐全（allow-insecure=true）→ M2M 三端点注入双凭证头（清单镜像 common 单源）")
    void shouldInjectCredentialHeadersOnM2mPaths() {
        FeignCredentialInterceptor interceptor = interceptor("sc-a", "sk-b", "true");
        interceptor.validateConfiguration();

        for (String path : java.util.List.of(
            "/api/access/resource-entity/sync",
            "/api/access/resource-entity/full-sync",
            "/api/access/integration/permission-manifest/full-sync")) {
            RequestTemplate template = new RequestTemplate();
            template.uri(path);

            interceptor.apply(template);

            assertThat(template.headers().get(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID))
                .as("M2M 端点 %s 必须注入凭证标识", path).containsExactly("sc-a");
            assertThat(template.headers().get(FeignCredentialInterceptor.HEADER_CREDENTIAL_SECRET))
                .as("M2M 端点 %s 必须注入凭证 secret", path).containsExactly("sk-b");
        }
    }

    @Test
    @DisplayName("非 M2M 端点（auth/check 等运行时鉴权/管理面）→ 不注入（宽注入面会击穿接入方鉴权链）")
    void shouldSkipNonM2mAccessPaths() {
        FeignCredentialInterceptor interceptor = interceptor("sc-a", "sk-b", "true");
        interceptor.validateConfiguration();

        for (String path : java.util.List.of(
            "/api/access/auth/check",
            "/api/access/auth/batch-check",
            "/api/access/auth/query-resources",
            "/api/access/resource-dependency/batch-sync",
            "/api/access/service-credential/list",
            "/api/example/other")) {
            RequestTemplate template = new RequestTemplate();
            template.uri(path);

            interceptor.apply(template);

            assertThat(template.headers()).as("非 M2M 端点 %s 不得携带凭证头", path)
                .doesNotContainKeys(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID,
                    FeignCredentialInterceptor.HEADER_CREDENTIAL_SECRET);
        }
    }

    @Test
    @DisplayName("完整 URL 形态（http://host/api/access/...）→ 按路径段精确匹配")
    void shouldMatchByPathSegmentOnAbsoluteUrl() {
        FeignCredentialInterceptor interceptor = interceptor("sc-a", "sk-b", "true");
        interceptor.validateConfiguration();

        RequestTemplate hit = new RequestTemplate();
        hit.target("http://access-service:9100");
        hit.uri("/api/access/resource-entity/full-sync");
        interceptor.apply(hit);
        assertThat(hit.headers()).containsKey(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID);

        RequestTemplate miss = new RequestTemplate();
        miss.target("http://access-service:9100");
        miss.uri("/api/access/auth/check");
        interceptor.apply(miss);
        assertThat(miss.headers()).doesNotContainKey(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID);
    }

    @Test
    @DisplayName("调用方已显式声明凭证头 → 不覆盖（多租户差异化场景）")
    void shouldNotOverrideExplicitHeaders() {
        FeignCredentialInterceptor interceptor = interceptor("sc-a", "sk-b", "true");
        interceptor.validateConfiguration();

        RequestTemplate template = new RequestTemplate();
        template.uri("/api/access/resource-entity/full-sync");
        template.header(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID, "sc-explicit");
        template.header(FeignCredentialInterceptor.HEADER_CREDENTIAL_SECRET, "sk-explicit");

        interceptor.apply(template);

        assertThat(template.headers().get(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID))
            .containsExactly("sc-explicit");
        assertThat(template.headers().get(FeignCredentialInterceptor.HEADER_CREDENTIAL_SECRET))
            .containsExactly("sk-explicit");
    }

    @Test
    @DisplayName("半配（credential-id 有/secret 缺）→ 启动失败 fail-fast")
    void shouldFailFast_whenSecretMissing() {
        FeignCredentialInterceptor interceptor = interceptor("sc-a", "", "true");
        assertThatThrownBy(interceptor::validateConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("成对配置");
    }

    @Test
    @DisplayName("启动声明式 TLS 护栏：配置凭证但未声明 allow-insecure → 拒启")
    void shouldFailFast_whenAllowInsecureNotDeclared() {
        FeignCredentialInterceptor interceptor = interceptor("sc-a", "sk-b", "");
        assertThatThrownBy(interceptor::validateConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("perm.allow-insecure");
    }

    @Test
    @DisplayName("护栏三态：allow-insecure=true/false 均为有效声明（缺省才拒）")
    void shouldAcceptExplicitDeclarations() {
        interceptor("sc-a", "sk-b", "true").validateConfiguration();
        interceptor("sc-a", "sk-b", "false").validateConfiguration();
        // 无异常即通过
        assertThat(true).isTrue();
    }
}
