package org.dromara.auth.identity.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.auth.model.SubjectMappingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 主体映射客户端
 *
 * 调用 permission-center 的主体映射接口
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubjectMappingClient {

    private final RestTemplate restTemplate;

    @Value("${permission-center.base-url:http://permission-center}")
    private String permissionCenterBaseUrl;

    /**
     * 查找或创建主体映射
     */
    public SubjectMappingResult findOrCreateMapping(String tenantId, String userTypeCode, String externalId, String name) {
        try {
            Map<String, String> request = Map.of(
                "tenantId", tenantId,
                "userTypeCode", userTypeCode,
                "externalId", externalId,
                "name", name
            );

            String url = permissionCenterBaseUrl + "/api/perm/subject/mapping";
            var response = restTemplate.postForObject(url, request, SubjectMappingResponse.class);

            if (response != null) {
                log.debug("Subject mapping: tenantId={}, externalId={}, abstractUserId={}, permissionVersion={}",
                    tenantId, externalId, response.getAbstractUserId(), response.getPermissionVersion());
                return new SubjectMappingResult(
                    response.getAbstractUserId(),
                    response.getPermissionVersion()
                );
            }
        } catch (Exception e) {
            log.warn("Failed to query subject mapping from permission-center: {}", e.getMessage());
        }
        return null;
    }

    public record SubjectMappingResult(Long abstractUserId, String permissionVersion) {}
}
