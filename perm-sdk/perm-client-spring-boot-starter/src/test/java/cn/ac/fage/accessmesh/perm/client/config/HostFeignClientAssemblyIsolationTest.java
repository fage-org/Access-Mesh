package cn.ac.fage.accessmesh.perm.client.config;

import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 宿主其他 Feign 客户端装配隔离回归锁（T-ACCESS-098）：凭证拦截器不得挂到宿主自身
 * Feign 客户端——spring-cloud 按 beansOfTypeIncludingAncestors 收集 RequestInterceptor，
 * 宿主上下文中的拦截器 bean 会命中全部客户端。真实 openfeign 运行时构造宿主
 * 其他服务的客户端，URL 中出现 /api/access/ 子串（前缀包含/query 携带形态）时
 * 不得携带凭证头（旧全局装配下前缀包含形态失败）。
 */
class HostFeignClientAssemblyIsolationTest {

    private static final List<Request> CAPTURED = new ArrayList<>();

    @FeignClient(name = "other-business-service", url = "http://other.example.com")
    interface OtherBusinessClient {

        @PostMapping("/v1/proxy/api/access/auth/check")
        String prefixContainedPath();

        @PostMapping("/fetch")
        String fetchWithQuery(@RequestParam("next") String next);

        /** 与 M2M 端点完全同形的路径——异域同路径形态（feign 拒绝绝对 URL，异域的真实形态即宿主其他客户端）。 */
        @PostMapping("/api/access/auth/check")
        String samePathAsM2mEndpoint();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = OtherBusinessClient.class)
    static class HostFeignConfig {

        /** 捕获最终出站请求（经 beansOfTypeIncludingAncestors 供子上下文使用），不发起真实连接。 */
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
            return (body, type, template) -> { /* 本组用例无 body 参数 */ };
        }

        @Bean
        feign.codec.Decoder dummyDecoder() {
            return (response, type) -> "ok";
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
            FeignAutoConfiguration.class, PermClientAutoConfiguration.class))
        .withUserConfiguration(HostFeignConfig.class)
        .withPropertyValues("perm.credential-id=sc-test", "perm.credential-secret=sk-test",
            "perm.allow-insecure=true");

    @BeforeEach
    void clearCaptured() {
        CAPTURED.clear();
    }

    @Test
    @DisplayName("宿主其他 Feign 客户端：前缀包含形态 → 出站请求不携带凭证头")
    void hostOtherClientDoesNotReceiveCredentials_prefixContainedPath() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(OtherBusinessClient.class).prefixContainedPath();
            assertThat(CAPTURED).hasSize(1);
            assertThat(CAPTURED.get(0).headers())
                .doesNotContainKeys("X-Credential-Id", "X-Credential-Secret");
        });
    }

    @Test
    @DisplayName("宿主其他 Feign 客户端：query 携带形态 → 出站请求不携带凭证头")
    void hostOtherClientDoesNotReceiveCredentials_queryForm() {
        runner.run(context -> {
            context.getBean(OtherBusinessClient.class).fetchWithQuery("/api/access/auth/check");
            assertThat(CAPTURED).hasSize(1);
            assertThat(CAPTURED.get(0).headers())
                .doesNotContainKeys("X-Credential-Id", "X-Credential-Secret");
        });
    }

    @Test
    @DisplayName("宿主其他 Feign 客户端：与 M2M 端点同形路径（异域同路径）→ 出站请求不携带凭证头"
        + "（路径匹配正确也无法救——装配隔离是唯一防线；旧全局装配下失败）")
    void hostOtherClientDoesNotReceiveCredentials_samePathAsM2mEndpoint() {
        runner.run(context -> {
            context.getBean(OtherBusinessClient.class).samePathAsM2mEndpoint();
            assertThat(CAPTURED).hasSize(1);
            assertThat(CAPTURED.get(0).headers())
                .doesNotContainKeys("X-Credential-Id", "X-Credential-Secret");
        });
    }
}
