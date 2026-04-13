package org.dromara.auth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.auth.service.LoginEnhancementService;
import org.dromara.auth.identity.service.impl.PermissionVersionClient;
import org.dromara.auth.identity.service.impl.SubjectMappingClient;
import org.springframework.stereotype.Service;
import org.dromara.system.api.model.LoginUser;

/**
 * 登录增强服务实现
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginEnhancementServiceImpl implements LoginEnhancementService {

    private final PermissionVersionClient permissionVersionClient;
    private final SubjectMappingClient subjectMappingClient;

    @Override
    public void enhance(LoginUser loginUser) {
        String tenantId = loginUser.getTenantId();
        Long userId = loginUser.getUserId();

        try {
            // 查询主体映射，获取 abstractUserId 和 permissionVersion
            SubjectMappingClient.SubjectMappingResult mapping = subjectMappingClient.findOrCreateMapping(
                tenantId,
                "sys_user",
                String.valueOf(userId),
                loginUser.getNickname() != null ? loginUser.getNickname() : loginUser.getUsername()
            );

            if (mapping != null) {
                loginUser.setAbstractUserId(mapping.abstractUserId());
                loginUser.setPermissionVersion(mapping.permissionVersion());
                log.debug("Enhanced login user: userId={}, abstractUserId={}, permissionVersion={}",
                    userId, mapping.abstractUserId(), mapping.permissionVersion());
            } else {
                // 回退：仅查询版本
                var versionInfo = permissionVersionClient.queryVersion(tenantId);
                String version = versionInfo != null ? versionInfo.getPermissionVersion() : tenantId + "-v0";
                loginUser.setPermissionVersion(version);
                log.debug("Enhanced login user with version only: userId={}, permissionVersion={}",
                    userId, version);
            }
        } catch (Exception e) {
            log.warn("Failed to enhance login user: userId={}, error={}", userId, e.getMessage());
            // 设置默认版本
            loginUser.setPermissionVersion(tenantId + "-v0");
        }
    }
}
