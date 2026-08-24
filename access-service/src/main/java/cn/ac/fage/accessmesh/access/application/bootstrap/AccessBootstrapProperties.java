package cn.ac.fage.accessmesh.access.application.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 空库 bootstrap 配置（T-ACCESS-020）。
 * <p>
 * {@code access.bootstrap.enabled} 默认 false；{@code access.bootstrap.admin-password} 绑定
 * 环境变量 {@code ACCESS_BOOTSTRAP_ADMIN_PASSWORD}（BCrypt 哈希落库，无明文，不写日志）。
 * enabled=true 时密码缺失/空白由 {@link AccessBootstrapRunner} fail-fast。
 * 仅单实例启用（无分布式锁，多实例并发建号由唯一约束兜底拒绝）。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "access.bootstrap")
public class AccessBootstrapProperties {

    private boolean enabled = false;

    private String adminPassword = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}
