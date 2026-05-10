package cn.ac.fage.accessmesh.perm.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 权限网关自动配置类
 * <p>
 * 自动启用权限网关配置属性绑定。
 * 当配置属性 perm.gateway.enabled 为 true 时启用（默认启用）。
 * </p>
 */
@Configuration
@EnableConfigurationProperties(PermGatewayProperties.class)
@ConditionalOnProperty(name = "perm.gateway.enabled", havingValue = "true", matchIfMissing = true)
public class PermGatewayAutoConfiguration {
}
