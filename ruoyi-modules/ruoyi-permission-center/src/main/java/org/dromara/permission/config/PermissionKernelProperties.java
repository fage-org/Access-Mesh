package org.dromara.permission.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限内核配置
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
@NoArgsConstructor
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "permission.kernel")
public class PermissionKernelProperties {

    /**
     * 是否启用精确鉴权
     */
    private boolean preciseCheckEnabled = true;

    /**
     * 是否启用条件求值
     */
    private boolean conditionEvaluationEnabled = true;

    /**
     * 是否启用冲突检测
     */
    private boolean conflictDetectionEnabled = true;

    /**
     * 是否启用依赖检查
     */
    private boolean dependencyCheckEnabled = true;

    /**
     * 是否启用审计写入
     */
    private boolean auditWriteEnabled = true;

    /**
     * 是否启用快照缓存
     */
    private boolean snapshotCacheEnabled = true;

    /**
     * 快照缓存最大容量
     */
    private int snapshotCacheMaxSize = 10000;

    /**
     * 快照缓存过期时间（分钟）
     */
    private int snapshotCacheExpireMinutes = 5;

    /**
     * 条件求值超时时间（毫秒）
     */
    private int conditionEvaluationTimeoutMs = 1000;

    /**
     * 依赖检查最大深度
     */
    private int dependencyCheckMaxDepth = 10;

    /**
     * 灰度租户列表
     */
    private List<String> grayscaleTenants = new ArrayList<>();

    /**
     * 排除的租户列表（不进行权限检查）
     */
    private List<String> excludedTenants = new ArrayList<>();

    /**
     * 检查是否在灰度范围内
     *
     * @param tenantId 租户ID
     * @return 是否在灰度范围内
     */
    public boolean isInGrayscale(String tenantId) {
        // 如果排除列表包含该租户，则不在灰度范围内
        if (excludedTenants.contains(tenantId)) {
            return false;
        }
        // 如果灰度列表为空，则对所有租户生效
        return grayscaleTenants.isEmpty() || grayscaleTenants.contains(tenantId);
    }
}
