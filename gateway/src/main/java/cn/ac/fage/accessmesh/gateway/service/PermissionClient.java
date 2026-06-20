package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.perm.common.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
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
    private final String interfaceSnapshotPath;

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
        this.interfaceSnapshotPath = gatewayProperties.getPermission().getInterfaceSnapshotPath();
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

    /**
     * 拉取用户接口权限快照（T-PERM-001 快照模式）
     * <p>
     * 调用 {@code POST /api/perm/auth/interface-snapshot}，获取用户在指定服务下可访问的接口集合，
     * 供 Gateway 本地内存匹配。支持传入 {@code permissionVersion} 做条件请求——服务端令牌未变化时
     * 返回 {@code notModified=true}，Gateway 沿用本地快照。
     * </p>
     *
     * @param subjectTypeCode  主体类型编码
     * @param userId           用户ID，转换为 subjectExternalId
     * @param serviceCode      服务编码
     * @param permissionVersion 上次本地快照的权限令牌，首次拉取传 null
     * @param tenantId         租户ID
     * @return 接口快照响应 Mono
     */
    public Mono<PermResult<InterfaceSnapshotResp>> interfaceSnapshot(
        String subjectTypeCode, Long userId, String serviceCode, String permissionVersion, Long tenantId) {

        InterfaceSnapshotReq req = new InterfaceSnapshotReq(
            subjectTypeCode,
            String.valueOf(userId),
            serviceCode,
            permissionVersion
        );

        log.debug("拉取接口权限快照: userId={}, serviceCode={}, hasVersion={}",
            userId, serviceCode, permissionVersion != null);

        return webClient.post()
            .uri(interfaceSnapshotPath)
            .header("X-Tenant-Id", tenantId != null ? tenantId.toString() : "")
            .header("X-Internal-Secret", internalSecret)
            .bodyValue(req)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<PermResult<InterfaceSnapshotResp>>() {})
            .doOnSuccess(result -> {
                if (result != null && result.getData() != null) {
                    InterfaceSnapshotResp resp = result.getData();
                    if (resp.notModified()) {
                        log.debug("快照未修改，沿用本地令牌: userId={}, serviceCode={}", userId, serviceCode);
                    } else {
                        log.debug("快照已刷新: userId={}, serviceCode={}, allowedApis={}",
                            userId, serviceCode,
                            resp.allowedApis() != null ? resp.allowedApis().size() : 0);
                    }
                }
            })
            .doOnError(e -> log.error("接口快照拉取失败: userId={}, serviceCode={}", userId, serviceCode));
    }
}