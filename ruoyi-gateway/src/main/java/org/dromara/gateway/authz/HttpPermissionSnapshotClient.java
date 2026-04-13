package org.dromara.gateway.authz;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.authcenter.api.model.InterfacePermissionSnapshot;
import org.dromara.authcenter.api.model.PermissionVersionInfo;
import org.dromara.authcenter.api.model.PrincipalContext;
import org.dromara.authcenter.api.request.InterfacePermissionSnapshotRequest;
import org.dromara.authcenter.api.request.PermissionVersionQueryRequest;
import org.dromara.gateway.config.properties.PermissionAuthzProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * HTTP 权限快照客户端
 *
 * 通过 HTTP 调用 permission-center 获取权限快照和版本信息
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gateway.authz", name = "http-client-enabled", havingValue = "true", matchIfMissing = false)
public class HttpPermissionSnapshotClient implements PermissionSnapshotClient {

    private final PermissionAuthzProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 默认重试次数
     */
    private static final int DEFAULT_RETRY_TIMES = 2;

    /**
     * 重试间隔（毫秒）
     */
    private static final long RETRY_INTERVAL_MS = 100;

    /**
     * HTTP 超时时间（毫秒）
     */
    private static final int HTTP_TIMEOUT_MS = 5000;

    public HttpPermissionSnapshotClient(PermissionAuthzProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public InterfacePermissionSnapshot loadSnapshot(PrincipalContext principalContext) {
        String url = properties.getPermissionCenterUrl() + "/api/perm/policy/interface-snapshot";

        InterfacePermissionSnapshotRequest request = new InterfacePermissionSnapshotRequest();
        request.setPrincipalContext(principalContext);

        try {
            String responseBody = executeWithRetry(url, request);
            if (responseBody == null) {
                return createFallbackSnapshot(principalContext);
            }

            // 解析响应
            var response = objectMapper.readTree(responseBody);
            int code = response.path("code").asInt();
            if (code != 200) {
                log.warn("Permission center returned error code: {}, message: {}",
                    code, response.path("msg").asText());
                return createFallbackSnapshot(principalContext);
            }

            String dataJson = response.path("data").toString();
            return objectMapper.readValue(dataJson, InterfacePermissionSnapshot.class);

        } catch (Exception e) {
            log.warn("Failed to load snapshot from permission-center: {}", e.getMessage());
            return createFallbackSnapshot(principalContext);
        }
    }

    /**
     * 查询权限版本
     *
     * @param tenantId 租户ID
     * @param subjectId 主体ID
     * @param subjectType 主体类型
     * @return 权限版本信息
     */
    public PermissionVersionInfo queryVersion(String tenantId, String subjectId, String subjectType) {
        String url = properties.getPermissionCenterUrl() + "/api/perm/version/query";

        PermissionVersionQueryRequest request = new PermissionVersionQueryRequest();
        request.setTenantId(tenantId);
        request.setSubjectId(subjectId);
        request.setSubjectType(org.dromara.authcenter.api.enums.SubjectType.fromCode(subjectType));

        try {
            String responseBody = executeWithRetry(url, request);
            if (responseBody == null) {
                return createFallbackVersion(tenantId);
            }

            var response = objectMapper.readTree(responseBody);
            int code = response.path("code").asInt();
            if (code != 200) {
                log.warn("Permission center returned error code: {}", code);
                return createFallbackVersion(tenantId);
            }

            String dataJson = response.path("data").toString();
            return objectMapper.readValue(dataJson, PermissionVersionInfo.class);

        } catch (Exception e) {
            log.warn("Failed to query version from permission-center: {}", e.getMessage());
            return createFallbackVersion(tenantId);
        }
    }

    /**
     * 带重试的 HTTP 请求
     */
    private String executeWithRetry(String url, Object requestBody) {
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            log.error("Failed to serialize request body", e);
            return null;
        }

        Exception lastException = null;
        for (int i = 0; i <= DEFAULT_RETRY_TIMES; i++) {
            try {
                HttpResponse response = HttpRequest.post(url)
                    .timeout(HTTP_TIMEOUT_MS)
                    .contentType("application/json")
                    .body(jsonBody)
                    .execute();

                if (response.isOk()) {
                    return response.body();
                } else {
                    log.warn("HTTP request failed with status: {}", response.getStatus());
                }
            } catch (Exception e) {
                lastException = e;
                log.debug("HTTP request attempt {} failed: {}", i + 1, e.getMessage());
            }

            // 重试前等待
            if (i < DEFAULT_RETRY_TIMES) {
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_INTERVAL_MS * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (lastException != null) {
            log.warn("All retry attempts failed: {}", lastException.getMessage());
        }
        return null;
    }

    /**
     * 创建降级快照
     */
    private InterfacePermissionSnapshot createFallbackSnapshot(PrincipalContext principalContext) {
        InterfacePermissionSnapshot snapshot = new InterfacePermissionSnapshot();
        snapshot.setTenantId(principalContext.getTenantId());
        snapshot.setSubjectKey(principalContext.getSubjectKey());
        snapshot.setPermissionVersion(principalContext.getPermissionVersion());
        snapshot.setGeneratedAtEpochMilli(System.currentTimeMillis());
        log.debug("Using fallback snapshot for tenant={}, subject={}",
            principalContext.getTenantId(), principalContext.getSubjectKey());
        return snapshot;
    }

    /**
     * 创建降级版本
     */
    private PermissionVersionInfo createFallbackVersion(String tenantId) {
        PermissionVersionInfo versionInfo = new PermissionVersionInfo();
        versionInfo.setTenantId(tenantId);
        versionInfo.setPermissionVersion(tenantId + "-v0");
        versionInfo.setUpdatedAtEpochMilli(System.currentTimeMillis());
        log.debug("Using fallback version for tenant={}", tenantId);
        return versionInfo;
    }
}
