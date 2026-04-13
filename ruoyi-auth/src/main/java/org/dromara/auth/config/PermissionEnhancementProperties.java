package org.dromara.auth.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限增强配置
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
@NoArgsConstructor
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "auth.permission")
public class PermissionEnhancementProperties {

    /**
     * 是否启用登录版本写入
     */
    private boolean versionWriteEnabled = true;

    /**
     * 是否启用主体映射
     */
    private boolean subjectMappingEnabled = true;

    /**
     * 降级模式（不调用 permission-center）
     */
    private boolean fallbackMode = false;

    /**
     * permission-center 服务地址
     */
    private String permissionCenterUrl = "http://localhost:9202";

    /**
     * HTTP 调用超时时间（毫秒）
     */
    private int httpTimeoutMs = 5000;

    /**
     * HTTP 调用重试次数
     */
    private int httpRetryTimes = 2;

    /**
     * 灰度租户列表（仅对这些租户启用版本写入）
     */
    private List<String> grayscaleTenants = new ArrayList<>();

    /**
     * 排除的租户列表（不进行版本写入）
     */
    private List<String> excludedTenants = new ArrayList<>();

    /**
     * 检查是否需要版本写入
     *
     * @param tenantId 租户ID
     * @return 是否需要版本写入
     */
    public boolean shouldWriteVersion(String tenantId) {
        if (!versionWriteEnabled) {
            return false;
        }
        if (excludedTenants.contains(tenantId)) {
            return false;
        }
        return grayscaleTenants.isEmpty() || grayscaleTenants.contains(tenantId);
    }
}
