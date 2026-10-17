package cn.ac.fage.accessmesh.perm.registration;

import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
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
 * <p>
 * codex sol r3 证据限制修复：旧形态未设 {@code perm.registration.enabled=true}，
 * {@code PermRegistrationAutoConfiguration} 未装配——真实 registration 子上下文与
 * 凭证拦截器根本不在场，「不携带凭证头」断言空转（无注入可拒）。现启用真实装配
 * 并以真实接口的注入阳性对照证明身份门在目标子上下文内真实判别（旧空转形态下
 * 阳性对照失败）。
 * </p>
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
            return (body, type, template) -> { /* 本用例组无 body 参数编码需求 */ };
        }

        @Bean
        feign.codec.Decoder dummyDecoder() {
            // 按声明返回类型给占位值：替身接口返回 String、真实接口返回 R（解码后代理转型需要）
            return (response, type) -> type == String.class
                ? "ok"
                : cn.ac.fage.accessmesh.common.model.R.ok();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
            JacksonAutoConfiguration.class, ValidationAutoConfiguration.class,
            FeignAutoConfiguration.class,
            cn.ac.fage.accessmesh.perm.registration.config.PermRegistrationAutoConfiguration.class))
        .withUserConfiguration(CaptureConfig.class)
        .withPropertyValues("perm.registration.enabled=true", "perm.allow-insecure=true",
            "perm.credential-id=sc-test", "perm.credential-secret=sk-test");

    @BeforeEach
    void clearCaptured() {
        CAPTURED.clear();
    }

    @Test
    @DisplayName("同简名异包宿主接口复用 permissionRegistration contextId + M2M 同形路径 → 不携带凭证头"
        + "（真实装配在场：拦截器存在且判别 targetType；旧空转形态下缺 enabled 断言无注入可拒）")
    void sameSimpleNameHostClientDoesNotReceiveCredentials() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            // 真实装配在场证明（r3：旧形态此断言即失败——自动配置未启用）
            assertThat(context).hasBean("permissionRegistrationPublisher");
            assertThat(context.getBean(
                cn.ac.fage.accessmesh.perm.registration.feign.PermissionManifestClient.class)).isNotNull();
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

    @Test
    @DisplayName("阳性对照：真实 PermissionManifestClient 同 contextId 调用 M2M 端点且未预置凭证头"
        + "（null 头参不落模板）→ 拦截器注入静态凭证——证明身份门在真实子上下文内正向生效"
        + "（旧空转形态下本用例失败：无注入可观察）")
    void realManifestClientReceivesInterceptorInjection() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            cn.ac.fage.accessmesh.perm.registration.feign.PermissionManifestClient realClient =
                new org.springframework.cloud.openfeign.FeignClientBuilder(context)
                    .forType(cn.ac.fage.accessmesh.perm.registration.feign.PermissionManifestClient.class,
                        "permissionRegistration")
                    .url("http://other.example.com")
                    .build();
            realClient.fullSync(null, null,
                new PermissionManifestReq(1, "42", "r", List.of()));
            assertThat(CAPTURED).hasSize(1);
            assertThat(CAPTURED.get(0).headers()).containsKeys("X-Credential-Id", "X-Credential-Secret");
            assertThat(CAPTURED.get(0).headers().get("X-Credential-Id")).containsExactly("sc-test");
            assertThat(CAPTURED.get(0).headers().get("X-Credential-Secret")).containsExactly("sk-test");
        });
    }
}
