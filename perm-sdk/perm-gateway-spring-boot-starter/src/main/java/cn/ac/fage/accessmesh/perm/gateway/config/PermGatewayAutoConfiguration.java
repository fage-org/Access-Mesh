package cn.ac.fage.accessmesh.perm.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PermGatewayProperties.class)
@ConditionalOnProperty(name = "perm.gateway.enabled", havingValue = "true", matchIfMissing = true)
public class PermGatewayAutoConfiguration {
}
