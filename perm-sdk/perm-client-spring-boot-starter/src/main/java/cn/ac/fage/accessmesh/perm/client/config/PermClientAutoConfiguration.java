package cn.ac.fage.accessmesh.perm.client.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * 权限客户端自动配置类
 * <p>
 * 自动启用Feign客户端扫描，用于权限中心远程调用。
 * 当配置属性 perm.client.enabled 为 true 时启用（默认启用）。
 * </p>
 */
@Configuration
@EnableFeignClients(basePackages = "cn.ac.fage.accessmesh.perm.client.feign")
@ConditionalOnProperty(name = "perm.client.enabled", havingValue = "true", matchIfMissing = true)
public class PermClientAutoConfiguration {
}
