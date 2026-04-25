package cn.ac.fage.accessmesh.perm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "perm.gateway")
public class PermGatewayProperties {

    /** Whether to enable permission checking filter */
    private boolean enabled = true;

    /** URL patterns to skip permission check */
    private String[] excludePaths = {};

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String[] getExcludePaths() { return excludePaths; }
    public void setExcludePaths(String[] excludePaths) { this.excludePaths = excludePaths; }
}
