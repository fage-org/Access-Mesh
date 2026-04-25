package cn.ac.fage.accessmesh.perm.client.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "cn.ac.fage.accessmesh.perm.client.feign")
@ConditionalOnProperty(name = "perm.client.enabled", havingValue = "true", matchIfMissing = true)
public class PermClientAutoConfiguration {
}
