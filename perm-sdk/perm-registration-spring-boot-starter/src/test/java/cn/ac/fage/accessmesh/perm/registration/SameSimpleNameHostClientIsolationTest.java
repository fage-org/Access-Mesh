package cn.ac.fage.accessmesh.perm.registration;

import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PostMapping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 同简名异包宿主接口的凭证隔离回归锁（codex sol r2 P2-1，registration 通道）：
 * 与 {@code PermissionManifestClient} 同简名的宿主接口经官方 FeignClientBuilder
 * 复用 {@code permissionRegistration} 子上下文（contextId 共享）后，调用与 M2M
 * 端点同形的路径不得取得凭证头——feign configKey 只含接口简名，旧简名门禁下
 * 本用例失败；身份门（MethodMetadata.targetType 全限定名）下通过。
 */
class SameSimpleNameHostClientIsolationTest {

    private static final List<Request> CAPTURED = new ArrayList<>();

    /** 嵌套于本测试类：简名=PermissionManifestClient，FQCN 含外层类名（异包同简名形态）。 */
    interface PermissionManifestClient {

        @PostMapping("/api/access/integration/permission-manifest/full-sync")
        String fullSync();
    }

    @Configuration(proxyBeanMethods = false)
    static class CaptureConfig {

        @Bean
        feign.Client capturingClient() {
            return (request, options) -> {
                CAPTURED.add(request);
                return feign.Response.builder().request(request).status(200).reason("OK")
                    .headers(Map.of()).body("{}", StandardCharsets.UTF_8).build();
            };
        }

        @Bean
        feign.codec.Encoder emptyBodyEncoder() {
            return (body, type, template) -> { /* 本用例无 body 参数 */ };
        }

        @Bean
        feign.codec.Decoder dummyDecoder() {
            return (response, type) -> "ok";
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
            FeignAutoConfiguration.class,
            cn.ac.fage.accessmesh.perm.registration.config.PermRegistrationAutoConfiguration.class))
        .withUserConfiguration(CaptureConfig.class)
        .withPropertyValues("perm.credential-id=sc-test", "perm.credential-secret=sk-test",
            "perm.allow-insecure=true");

    @BeforeEach
    void clearCaptured() {
        CAPTURED.clear();
    }

    @Test
    @DisplayName("同简名异包宿主接口复用 permissionRegistration contextId + M2M 同形路径 → 不携带凭证头")
    void sameSimpleNameHostClientDoesNotReceiveCredentials() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            PermissionManifestClient hostClient =
                new org.springframework.cloud.openfeign.FeignClientBuilder(context)
                    .forType(PermissionManifestClient.class, "permissionRegistration")
                    .url("http://other.example.com")
                    .build();
            hostClient.fullSync();
            assertThat(CAPTURED).hasSize(1);
            assertThat(CAPTURED.get(0).headers())
                .doesNotContainKeys("X-Credential-Id", "X-Credential-Secret");
        });
    }
}
