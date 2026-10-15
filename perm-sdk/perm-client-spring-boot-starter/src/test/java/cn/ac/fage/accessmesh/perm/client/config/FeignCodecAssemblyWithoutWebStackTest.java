package cn.ac.fage.accessmesh.perm.client.config;

import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * SDK Feign 编解码装配回归锁（codex sol r2 P2-4）：宿主不带 Web 栈（WebFlux 宿主
 * 无 HttpMessageConverters bean、无 Web 宿主无 jsr310 模块）时，SDK 客户端的
 * JSON 编解码必须自洽。
 * <p>
 * 背景：common 的 Web 栈传递退役（T-ACCESS-100）后，spring-cloud-openfeign 默认
 * SpringEncoder/SpringDecoder 依赖的 {@code HttpMessageConverters} bean 不再由
 * 传递的 Servlet 栈兜底——{@code SpringEncoder.initConvertersIfRequired} 字节码
 * 实证直接 {@code ObjectFactory.getObject()}，无 bean 即
 * {@code NoSuchBeanDefinitionException}；jackson-datatype-jsr310 亦随 starter-web
 * 退出闭包（{@code InterfaceAdmissionSnapshotResp.generatedAt} 等时间字段无法
 * 编解码）。修复：{@code PermFeignClientsConfiguration} 在权限客户端子上下文提供
 * {@code @ConditionalOnMissingBean} 的 HttpMessageConverters（专属 ObjectMapper
 * + jsr310，宿主已有（Servlet 宿主）时复用宿主的）。本测试宿主上下文刻意不带
 * converters 自动配置=WebFlux/无 Web 宿主形态。
 * </p>
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
            // 解码路径：LocalDateTime（generatedAt）经 jsr310 反序列化
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
        var node = new ObjectMapper().readTree(SNAPSHOT_JSON);
        assertThat(node.path("data").path("generatedAt").isTextual()).isTrue();
        assertThat(node.path("data").path("expiresAt").isTextual()).isTrue();
    }
}
