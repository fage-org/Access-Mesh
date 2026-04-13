package org.dromara.auth.identity.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.authcenter.api.model.PermissionVersionInfo;
import org.dromara.authcenter.api.request.PermissionVersionQueryRequest;
import org.dromara.authcenter.api.enums.SubjectType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 权限版本客户端
 *
 * 调用 permission-center 的版本查询接口
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionVersionClient {

    private final RestTemplate restTemplate;

    @Value("${permission-center.base-url:http://permission-center}")
    private String permissionCenterBaseUrl;

    /**
     * 查询权限版本
     */
    public PermissionVersionInfo queryVersion(String tenantId) {
        try {
            PermissionVersionQueryRequest request = new PermissionVersionQueryRequest();
            request.setTenantId(tenantId);
            request.setSubjectType(SubjectType.USER);
            request.setSubjectId("0");

            String url = permissionCenterBaseUrl + "/api/perm/version/query";
            var response = restTemplate.postForObject(url, request, PermissionVersionInfo.class);

            if (response != null) {
                log.debug("Queried permission version for tenant {}: {}", tenantId, response.getPermissionVersion());
                return response;
            }
        } catch (Exception e) {
            log.warn("Failed to query permission version from permission-center: {}", e.getMessage());
        }
        return null;
    }
}
