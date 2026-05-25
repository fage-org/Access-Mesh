package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.perm.common.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 权限校验HTTP客户端
 * <p>
 * 调用permission-center的接口权限校验端点。
 * 使用负载均衡的WebClient支持lb://服务URL格式。
 * </p>
 */
@Service
public class PermissionClient {

    private static final Logger log = LoggerFactory.getLogger(PermissionClient.class);

    private final WebClient webClient;
    private final String checkInterfacePath;

    @Value("${perm.internal-secret:}")
    private String internalSecret;

    /**
     * 构造权限校验客户端
     * <p>
     * 根据配置的服务URL初始化WebClient。
     * 自动处理lb://前缀，LoadBalancer负责服务发现和负载均衡。
     * </p>
     *
     * @param loadBalancedWebClientBuilder 负载均衡WebClient构建器
     * @param gatewayProperties             Gateway配置属性
     */
    public PermissionClient(WebClient.Builder loadBalancedWebClientBuilder,
                            GatewayProperties gatewayProperties) {
        String baseUrl = gatewayProperties.getPermission().getServiceUrl();
        // 移除lb://前缀供WebClient使用 — LoadBalancer处理服务解析
        String resolvedUrl = baseUrl.replace("lb://", "");
        if (!resolvedUrl.startsWith("http://") && !resolvedUrl.startsWith("https://")) {
            resolvedUrl = "http://" + resolvedUrl;
        }

        this.webClient = loadBalancedWebClientBuilder
            .baseUrl(resolvedUrl)
            .build();
        this.checkInterfacePath = gatewayProperties.getPermission().getCheckInterfacePath();
    }

    /**
     * 调用permission-center检查接口访问权限
     * <p>
     * 发送CheckInterfaceReq（包含subjectTypeCode、subjectExternalId、服务编码、路径等）
     * 并解析PermResult<CheckInterfaceResp>响应。
     * 使用内部密钥请求头标识请求来源为Gateway。
     * </p>
     *
         * @param subjectTypeCode 主体类型编码
     * @param userId    用户ID，转换为subjectExternalId
     * @param serviceCode 服务编码
     * @param httpMethod HTTP方法
     * @param path      请求路径
     * @param clientIp  客户端IP
     * @param tenantId  租户ID
     * @return 权限校验响应Mono
     */
    public Mono<PermResult<CheckInterfaceResp>> checkInterface(
             String subjectTypeCode, Long userId, String serviceCode, String httpMethod, String path,
            String clientIp, Long tenantId) {

        // 构建context Map，包含clientIp和timestamp
        Map<String, Object> context = new HashMap<>();
        if (clientIp != null) {
            context.put("clientIp", clientIp);
        }
        context.put("timestamp", java.time.Instant.now().toString());

        CheckInterfaceReq req = new CheckInterfaceReq(
            subjectTypeCode,
            String.valueOf(userId),         // subjectExternalId
            serviceCode,
            httpMethod,
            path,
            context
        );

        log.debug("调用permission-center进行接口权限校验: userId={}, serviceCode={}, path={}",
            userId, serviceCode, path);

        return webClient.post()
            .uri(checkInterfacePath)
            .header("X-Tenant-Id", tenantId != null ? tenantId.toString() : "")
            .header("X-Internal-Secret", internalSecret)
            .bodyValue(req)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<PermResult<CheckInterfaceResp>>() {})
            .doOnSuccess(result -> {
                if (result != null && result.getData() != null) {
                    CheckInterfaceResp resp = result.getData();
                    if (resp.allowed()) {
                        log.debug("权限校验通过: matchedResources={}, cacheTtlSeconds={}",
                            resp.matchedResources() != null ? resp.matchedResources().size() : 0,
                            resp.cacheTtlSeconds());
                    } else {
                        log.warn("权限校验拒绝: reason={}", resp.reason());
                    }
                }
            })
            .doOnError(e -> log.error("permission-center调用失败: userId={}, serviceCode={}, path={}",
                userId, serviceCode, path));
    }
}