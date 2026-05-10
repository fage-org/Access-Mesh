package cn.ac.fage.accessmesh.perm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 权限网关配置属性类
 * <p>
 * 提供网关权限校验过滤器的配置选项。
 * 可通过 application.yml 中的 `perm.gateway` 前缀进行自定义。
 * </p>
 */
@ConfigurationProperties(prefix = "perm.gateway")
public class PermGatewayProperties {

    /**
     * 是否启用权限校验过滤器
     */
    private boolean enabled = true;

    /**
     * 跳过权限校验的URL路径模式
     */
    private String[] excludePaths = {};

    /**
     * 获取是否启用权限校验
     *
     * @return 是否启用
     */
    public boolean isEnabled() { return enabled; }

    /**
     * 设置是否启用权限校验
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * 获取跳过权限校验的路径
     *
     * @return 跳过的路径数组
     */
    public String[] getExcludePaths() { return excludePaths; }

    /**
     * 设置跳过权限校验的路径
     *
     * @param excludePaths 跳过的路径数组
     */
    public void setExcludePaths(String[] excludePaths) { this.excludePaths = excludePaths; }
}
