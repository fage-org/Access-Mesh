package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.model.AuthCheckRequest;
import cn.ac.fage.accessmesh.gateway.model.AuthCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * HTTP client for calling permission-center interface check endpoint.
 * Uses load-balanced WebClient to support lb:// service URLs.
 */
@Service
public class PermissionClient {

    private static final Logger log = LoggerFactory.getLogger(PermissionClient.class);

    private final WebClient webClient;
    private final String checkInterfacePath;

    public PermissionClient(WebClient.Builder loadBalancedWebClientBuilder,
                            GatewayProperties gatewayProperties) {
        String baseUrl = gatewayProperties.getPermission().getServiceUrl();
        // Strip lb:// prefix for WebClient — LoadBalancer handles service resolution
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
     * Call permission-center to check interface access.
     * Sends AuthCheckRequest (with clientIp populated from context)
     * and parses PermResult<CheckInterfaceResp> response.
     */
    public Mono<AuthCheckResponse> checkInterface(AuthCheckRequest request, Long tenantId) {
        // Flatten clientIp from context if not already set
        if (request.getClientIp() == null && request.getContext() != null) {
            request.setClientIp(request.getContext().getIp());
        }

        log.debug("Calling permission-center for interface check: serviceCode={}, path={}",
            request.getServiceCode(), request.getPath());

        return webClient.post()
            .uri(checkInterfacePath)
            .header("X-Tenant-Id", tenantId != null ? tenantId.toString() : "")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(AuthCheckResponse.class)
            .doOnSuccess(resp -> {
                if (resp != null && resp.isAllowed()) {
                    log.debug("Permission check allowed: matchedRoleId={}, opCode={}",
                        resp.getData() != null ? resp.getData().getMatchedRoleId() : null,
                        resp.getData() != null ? resp.getData().getMatchedOperationCode() : null);
                } else {
                    String reason = resp != null && resp.getData() != null
                        ? resp.getData().getDenyReason() : "unknown";
                    log.warn("Permission check denied: reason={}", reason);
                }
            })
            .doOnError(e -> log.error("Permission-center call failed: serviceCode={}, path={}",
                request.getServiceCode(), request.getPath()));
    }
}
