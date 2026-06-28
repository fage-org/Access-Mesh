package cn.ac.fage.accessmesh.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 网关应用程序入口
 * <p>
 * Gateway 微服务的 Spring Boot 主启动类。
 * 提供 API 网关功能，包括路由转发、认证校验、权限校验等。
 * 使用 Spring Cloud Gateway 实现响应式网关，启用服务发现。
 * </p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class GatewayApplication {

    /**
     * 应用程序主入口
     * <p>
     * 启动 Spring Boot 应用，初始化所有配置和组件。
     * </p>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
