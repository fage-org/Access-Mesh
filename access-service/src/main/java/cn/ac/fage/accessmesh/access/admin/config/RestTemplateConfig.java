package cn.ac.fage.accessmesh.access.admin.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate配置类
 * <p>
 * 配置支持负载均衡的RestTemplate实例。
 * 用于微服务之间的HTTP调用，支持服务发现。
 * </p>
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建负载均衡RestTemplate
     * <p>
     * 使用@LoadBalanced注解标记RestTemplate，
     * 使其支持通过服务名调用（如http://permission-center/api/...）。
     * </p>
     *
     * @return 负载均衡RestTemplate实例
     */
    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }
}