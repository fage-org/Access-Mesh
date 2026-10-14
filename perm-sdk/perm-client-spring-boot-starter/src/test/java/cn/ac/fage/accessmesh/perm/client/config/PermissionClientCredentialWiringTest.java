package cn.ac.fage.accessmesh.perm.client.config;

import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.feign.PermFeignClientsConfiguration;
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
import org.springframework.web.bind.annotation.RequestBody;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限客户端凭证装配正向连续性锁（T-ACCESS-098）：真实 openfeign 运行时下，
 * 按 @FeignClient(configuration=PermFeignClientsConfiguration.class) 声明的客户端
 * 在其 Feign 子上下文构建凭证拦截器（@Value 从宿主 Environment 经 setParent merge
 * 解析），M2M 端点出站请求携带双凭证头。与 HostFeignClientAssemblyIsolationTest
 * 互为对照：同一机制下权限客户端有凭证、宿主其他客户端无凭证。
 */
class PermissionClientCredentialWiringTest {

    private static final List<Request> CAPTURED = new ArrayList<>();

    /** 与 PermissionFeignClient 同形声明（name + configuration），加显式 url 免负载均衡依赖。 */
    @FeignClient(name = "access-service", contextId = "probeAccessService",
        url = "http://access-service.example.com",
        configuration = PermFeignClientsConfiguration.class)
    interface ProbeClient {

        @PostMapping("/api/access/auth/check")
        String check(@RequestBody AuthCheckReq req);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = ProbeClient.class)
    static class ProbeConfig {

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
            return (body, type, template) -> { /* 出站头断言不依赖 body */ };
        }

        @Bean
        feign.codec.Decoder dummyDecoder() {
            return (response, type) -> "ok";
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
            FeignAutoConfiguration.class))
        .withUserConfiguration(ProbeConfig.class)
        .withPropertyValues("perm.credential-id=sc-wired", "perm.credential-secret=sk-wired",
            "perm.allow-insecure=true");

    @BeforeEach
    void clearCaptured() {
        CAPTURED.clear();
    }

    @Test
    @DisplayName("权限客户端（M2M 端点）：子上下文装配拦截器 → 出站请求携带双凭证头")
    void permissionClientCarriesCredentials_viaPerClientAssembly() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(ProbeClient.class).check(new AuthCheckReq(
                "LOCAL_USER", "u-1", "REPORT", "r-1", "VIEW", null, null, null,
                null, null, null, null, null));
            assertThat(CAPTURED).hasSize(1);
            assertThat(CAPTURED.get(0).headers().get("X-Credential-Id")).containsExactly("sc-wired");
            assertThat(CAPTURED.get(0).headers().get("X-Credential-Secret")).containsExactly("sk-wired");
        });
    }

    @Test
    @DisplayName("真实客户端声明锁：PermissionFeignClient 的 @FeignClient 挂载 PermFeignClientsConfiguration")
    void permissionFeignClientMountsCredentialConfiguration() {
        assertThat(PermissionFeignClient.class.getAnnotation(FeignClient.class).configuration())
            .containsExactly(PermFeignClientsConfiguration.class);
    }
}
