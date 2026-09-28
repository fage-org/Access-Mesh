package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
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
 * 权限校验HTTP客户端（T-ACCESS-059 操作准入链）。
 * <p>
 * 调用access-service的 interface-admission 族端点（快照拉取＋在线判定回源）。
 * 使用负载均衡的WebClient支持lb://服务URL格式；身份为平台内部密钥形态
 * （X-Internal-Secret + X-Tenant-Id，网关属平台信任域——service-authentication §3.2）。
 * 旧 check-interface / interface-snapshot 消费随无迁移期切换删除（服务端端点已随
 * T-ACCESS-062 删除 退役）。
 * </p>
 */
@Service
public class PermissionClient {

    private static final Logger log = LoggerFactory.getLogger(PermissionClient.class);

    private final WebClient webClient;
    private final String interfaceAdmissionPath;
    private final String interfaceAdmissionSnapshotPath;

    @Value("${perm.internal-secret:}")
    private String internalSecret;

    /**
     * 构造权限校验客户端
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
        this.interfaceAdmissionPath = gatewayProperties.getPermission().getInterfaceAdmissionPath();
        this.interfaceAdmissionSnapshotPath = gatewayProperties.getPermission().getInterfaceAdmissionSnapshotPath();
    }

    /**
     * 拉取操作准入快照（网关本地判定主路径）。
     *
     * @param subjectTypeCode 主体类型编码
     * @param userId          用户ID，转换为subjectExternalId
     * @param serviceCode     服务编码
     * @param tenantId        租户ID
     * @return 操作准入快照 Mono
     */
    public Mono<R<InterfaceAdmissionSnapshotResp>> interfaceAdmissionSnapshot(
        String subjectTypeCode, Long userId, String serviceCode, Long tenantId) {

        InterfaceAdmissionSnapshotReq req = new InterfaceAdmissionSnapshotReq(
            subjectTypeCode, String.valueOf(userId), serviceCode);

        log.debug("拉取操作准入快照: userId={}, serviceCode={}", userId, serviceCode);

        return webClient.post()
            .uri(interfaceAdmissionSnapshotPath)
            .header("X-Tenant-Id", tenantId != null ? tenantId.toString() : "")
            .header("X-Internal-Secret", internalSecret)
            .bodyValue(req)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<R<InterfaceAdmissionSnapshotResp>>() {})
            .doOnSuccess(result -> {
                if (result != null && result.getData() != null) {
                    log.debug("操作准入快照已拉取: userId={}, serviceCode={}, routes={}",
                        userId, serviceCode,
                        result.getData().routes() != null ? result.getData().routes().size() : 0);
                }
            })
            .doOnError(e -> log.error("操作准入快照拉取失败: userId={}, serviceCode={}", userId, serviceCode));
    }

    /**
     * 在线操作准入判定（本地无通过分支回源/灰度强制在线）。
     *
     * @param subjectTypeCode 主体类型编码
     * @param userId          用户ID，转换为subjectExternalId
     * @param serviceCode     服务编码
     * @param httpMethod      HTTP方法
     * @param path            请求路径
     * @param clientIp        客户端IP
     * @param tenantId        租户ID
     * @return 准入判定响应 Mono
     */
    public Mono<R<InterfaceAdmissionResp>> interfaceAdmission(
        String subjectTypeCode, Long userId, String serviceCode, String httpMethod, String path,
        String clientIp, Long tenantId) {

        // context 仅承载 clientIp（跨进程时钟一致性由部署统一时区保证）
        Map<String, Object> context = new HashMap<>();
        if (clientIp != null) {
            context.put("clientIp", clientIp);
        }

        InterfaceAdmissionReq req = new InterfaceAdmissionReq(
            subjectTypeCode, String.valueOf(userId), serviceCode, httpMethod, path, context);

        log.debug("调用access-service在线操作准入: userId={}, serviceCode={}, path={}", userId, serviceCode, path);

        return webClient.post()
            .uri(interfaceAdmissionPath)
            .header("X-Tenant-Id", tenantId != null ? tenantId.toString() : "")
            .header("X-Internal-Secret", internalSecret)
            .bodyValue(req)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<R<InterfaceAdmissionResp>>() {})
            .doOnSuccess(result -> {
                if (result != null && result.getData() != null) {
                    InterfaceAdmissionResp resp = result.getData();
                    if ("MAY_ENTER".equals(resp.decision())) {
                        log.debug("操作准入通过: requiredPermission={}", resp.requiredPermission());
                    } else {
                        log.debug("操作准入拒绝: reason={}", resp.reason());
                    }
                }
            })
            .doOnError(e -> log.error("在线操作准入调用失败: userId={}, serviceCode={}, path={}",
                userId, serviceCode, path));
    }
}
