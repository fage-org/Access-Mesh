package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.model.AuthCheckRequest;
import cn.ac.fage.accessmesh.gateway.model.AuthCheckResponse;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * HTTP client for calling permission-center interface check endpoint.
 */
@Service
public class PermissionClient {

    private static final Logger log = LoggerFactory.getLogger(PermissionClient.class);

    private final WebClient webClient;
    private final String checkInterfacePath;

    public PermissionClient(GatewayProperties gatewayProperties) {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
            .responseTimeout(Duration.ofSeconds(5));

        this.webClient = WebClient.builder()
            .baseUrl(gatewayProperties.getPermission().getServiceUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        this.checkInterfacePath = gatewayProperties.getPermission().getCheckInterfacePath();
    }

    /**
     * Call permission-center to check interface access.
     */
    public Mono<AuthCheckResponse> checkInterface(AuthCheckRequest request) {
        log.debug("Calling permission-center for interface check: serviceCode={}, path={}",
            request.getServiceCode(), request.getPath());

        return webClient.post()
            .uri(checkInterfacePath)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(AuthCheckResponse.class)
            .doOnSuccess(resp -> {
                if (resp != null && resp.isAllowed()) {
                    log.debug("Permission check allowed: matchedRoleId={}",
                        resp.getData() != null ? resp.getData().getMatchedRoleId() : null);
                } else {
                    String reason = resp != null && resp.getData() != null
                        ? resp.getData().getReason() : "unknown";
                    log.warn("Permission check denied: reason={}", reason);
                }
            })
            .doOnError(e -> log.error("Permission-center call failed: serviceCode={}, path={}",
                request.getServiceCode(), request.getPath()));
    }
}
