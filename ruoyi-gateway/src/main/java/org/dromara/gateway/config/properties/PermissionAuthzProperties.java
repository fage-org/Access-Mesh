package org.dromara.gateway.config.properties;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关接口鉴权配置
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
@NoArgsConstructor
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "gateway.authz")
public class PermissionAuthzProperties {

    /**
     * 是否启用接口鉴权入口
     */
    private boolean enabled = false;

    /**
     * 拉不到快照或无匹配规则时是否放行
     */
    private boolean failOpen = true;

    private String tenantHeader = "X-Tenant-Id";

    private String subjectTypeHeader = "X-Subject-Type";

    private String permissionVersionHeader = "X-Permission-Version";

    private String serviceCodeHeader = "X-Service-Code";

    /**
     * 是否启用 HTTP 客户端（调用 permission-center）
     */
    private boolean httpClientEnabled = false;

    /**
     * permission-center 服务地址
     */
    private String permissionCenterUrl = "http://localhost:9202";

    /**
     * 缓存最大容量
     */
    private int cacheMaxSize = 10000;

    /**
     * 缓存过期时间（分钟）
     */
    private int cacheExpireMinutes = 5;

    /**
     * 灰度租户列表（仅对这些租户启用鉴权）
     */
    private List<String> grayscaleTenants = new ArrayList<>();

    /**
     * 灰度路由列表（仅对这些路由启用鉴权）
     */
    private List<String> grayscaleRoutes = new ArrayList<>();

    /**
     * 灰度用户列表（仅对这些用户启用鉴权）
     */
    private List<String> grayscaleUsers = new ArrayList<>();

    /**
     * 灰度比例（0-100，按请求比例灰度）
     */
    private int grayscalePercentage = 0;

    /**
     * 是否启用精确鉴权（调用 permission-center 进行精确鉴权）
     */
    private boolean preciseCheckEnabled = false;

    /**
     * 回滚版本（用于紧急回滚，设置为旧版本号则使用旧版本快照）
     */
    private String rollbackVersion = null;

    /**
     * 排除的租户列表（不进行权限检查）
     */
    private List<String> excludedTenants = new ArrayList<>();

    /**
     * 排除的路由列表（不进行权限检查）
     */
    private List<String> excludedRoutes = new ArrayList<>();

    /**
     * 检查是否在灰度范围内
     *
     * @param tenantId 租户ID
     * @param route 路由路径
     * @return 是否在灰度范围内
     */
    public boolean isInGrayscale(String tenantId, String route) {
        // 如果灰度列表为空，则对所有租户生效
        if (grayscaleTenants.isEmpty() && grayscaleRoutes.isEmpty()) {
            return true;
        }

        // 检查租户灰度
        boolean tenantMatch = grayscaleTenants.isEmpty() || grayscaleTenants.contains(tenantId);

        // 检查路由灰度
        boolean routeMatch = grayscaleRoutes.isEmpty() || grayscaleRoutes.stream()
            .anyMatch(pattern -> matchRoute(pattern, route));

        return tenantMatch && routeMatch;
    }

    /**
     * 简单的路由匹配
     */
    private boolean matchRoute(String pattern, String route) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return route.startsWith(prefix);
        }
        return pattern.equals(route);
    }

    /**
     * 检查是否被排除
     *
     * @param tenantId 租户ID
     * @param route    路由路径
     * @return 是否被排除
     */
    public boolean isExcluded(String tenantId, String route) {
        // 检查租户排除
        if (excludedTenants.contains(tenantId)) {
            return true;
        }
        // 检查路由排除
        return excludedRoutes.stream().anyMatch(pattern -> matchRoute(pattern, route));
    }

    /**
     * 检查是否需要进行权限检查
     *
     * @param tenantId 租户ID
     * @param route    路由路径
     * @return 是否需要进行权限检查
     */
    public boolean shouldCheck(String tenantId, String route) {
        if (!enabled) {
            return false;
        }
        if (isExcluded(tenantId, route)) {
            return false;
        }
        return isInGrayscale(tenantId, route);
    }
}
