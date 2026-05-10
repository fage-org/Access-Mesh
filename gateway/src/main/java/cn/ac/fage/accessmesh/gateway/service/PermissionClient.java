package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.model.AuthCheckRequest;
import cn.ac.fage.accessmesh.gateway.model.AuthCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
     * 发送AuthCheckRequest（包含从上下文获取的clientIp）
     * 并解析PermResult<CheckInterfaceResp>响应。
     * 使用内部密钥请求头标识请求来源为Gateway。
     * </p>
     *
     * @param request  权限校验请求，包含服务编码、路径、客户端IP等
     * @param tenantId 租户ID
     * @return 权限校验响应Mono
     */
    public Mono<AuthCheckResponse> checkInterface(AuthCheckRequest request, Long tenantId) {
        // 从上下文提取客户端IP
        if (request.getClientIp() == null && request.getContext() != null) {
            request.setClientIp(request.getContext().getIp());
        }

        log.debug("调用permission-center进行接口权限校验: serviceCode={}, path={}",
            request.getServiceCode(), request.getPath());

        return webClient.post()
            .uri(checkInterfacePath)
            .header("X-Tenant-Id", tenantId != null ? tenantId.toString() : "")
            .header("X-Internal-Secret", internalSecret)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(AuthCheckResponse.class)
            .doOnSuccess(resp -> {
                if (resp != null && resp.isAllowed()) {
                    log.debug("权限校验通过: matchedRoleId={}, opCode={}",
                        resp.getData() != null ? resp.getData().getMatchedRoleId() : null,
                        resp.getData() != null ? resp.getData().getMatchedOperationCode() : null);
                } else {
                    String reason = resp != null && resp.getData() != null
                        ? resp.getData().getDenyReason() : "unknown";
                    log.warn("权限校验拒绝: reason={}", reason);
                }
            })
            .doOnError(e -> log.error("permission-center调用失败: serviceCode={}, path={}",
                request.getServiceCode(), request.getPath()));
    }
}