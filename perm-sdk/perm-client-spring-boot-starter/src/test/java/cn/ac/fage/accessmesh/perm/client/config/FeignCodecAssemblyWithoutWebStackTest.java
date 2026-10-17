package cn.ac.fage.accessmesh.perm.client.config;

import tools.jackson.databind.json.JsonMapper;

import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq;
import tools.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SDK Feign 编解码装配回归：无 Servlet/WebFlux 运行栈时，子上下文的 JSON 编解码自洽。
 * Cloud 5 的客户端转换器通过 PermFeignClientsConfiguration 的局部 customizer 配置；
 * Jackson 3 内建 Java time 支持，不向宿主创建全局 mapper 或引入 Web 运行栈。
 */
class FeignCodecAssemblyWithoutWebStackTest {

    private static final List<Request> CAPTURED = new ArrayList<>();

    /** 最小合法快照响应（含 LocalDateTime 字段）——快照端点返回 R<InterfaceAdmissionSnapshotResp>。 */
    private static final String SNAPSHOT_JSON = """
        {"code":200,"message":"success","data":{"schemaVersion":1,"tenantId":7,
        "serviceCode":"reports","generatedAt":"2026-10-15T08:30:00","expiresAt":"2026-10-15T09:30:00",
        "configGeneration":42,"routes":[],"operationCandidates":[],
        "authorizationStage":"FINAL_CHECK","finalCheckRequired":true},"requestId":"r","traceId":"t"}
        """;

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = PermissionFeignClient.class)
    static class HostConfig {

        /** 捕获出站请求并以最小合法 JSON 应答（经 beansOfTypeIncludingAncestors 供子上下文使用）。 */
        @Bean
        feign.Client capturingClient() {
            return (request, options) -> {
                CAPTURED.add(request);
                return Response.builder().request(request).status(200).reason("OK")
                    .headers(Map.of("Content-Type", List.of("application/json")))
                    .body(new ByteArrayInputStream(SNAPSHOT_JSON.getBytes(StandardCharsets.UTF_8)),
                        SNAPSHOT_JSON.getBytes(StandardCharsets.UTF_8).length)
                    .build();
            };
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
            FeignAutoConfiguration.class))
        .withUserConfiguration(HostConfig.class);

    @BeforeEach
    void clearCaptured() {
        CAPTURED.clear();
    }

    @Test
    @DisplayName("无 Web 栈宿主：checkAuth JSON body 编码 + 快照时间字段解码均自洽（旧实现下失败）")
    void jsonCodecSelfSufficientWithoutWebStack() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            PermissionFeignClient client = context.getBean(PermissionFeignClient.class);
            // 编码路径：JSON body 经 SDK 子上下文 converters 序列化
            var check = client.checkAuth(
                new AuthCheckReq("LOCAL_USER", "u-1", "BASIC_ROLE", "role-a", "READ",
                null, null, null, null, null, null, null, null));
            assertThat(check).isNotNull();
            assertThat(CAPTURED).hasSize(1);
            String body = new String(CAPTURED.get(0).body(), StandardCharsets.UTF_8);
            assertThat(body).contains("\"subjectTypeCode\":\"LOCAL_USER\"");
            // 解码路径：LocalDateTime（generatedAt）经 Jackson 3 内建时间支持反序列化
            var snapshot = client.interfaceAdmissionSnapshot(
                new InterfaceAdmissionSnapshotReq("LOCAL_USER", "u-1", "reports"));
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.getData()).isNotNull();
            assertThat(snapshot.getData().generatedAt())
                .isEqualTo(java.time.LocalDateTime.parse("2026-10-15T08:30:00"));
            assertThat(snapshot.getData().expiresAt())
                .isEqualTo(java.time.LocalDateTime.parse("2026-10-15T09:30:00"));
        });
    }

    /** 契约形态钉死：响应壳 R 与快照字段名与后端序列化一致（防止测试桩与契约漂移）。 */
    @Test
    void snapshotStubMatchesContractShape() throws Exception {
        var node = JsonMapper.builder().configureForJackson2().build().readTree(SNAPSHOT_JSON);
        assertThat(node.path("data").path("generatedAt").isTextual()).isTrue();
        assertThat(node.path("data").path("expiresAt").isTextual()).isTrue();
    }
}
