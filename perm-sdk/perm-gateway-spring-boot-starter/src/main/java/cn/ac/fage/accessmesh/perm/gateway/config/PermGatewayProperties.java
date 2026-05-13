package cn.ac.fage.accessmesh.perm.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 权限网关配置属性类
 * <p>
 * 提供网关权限校验过滤器的配置选项。
 * 可通过 application.yml 中的 `perm.gateway` 前缀进行自定义。
 * </p>
 */
@Getter
@Setter
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
}