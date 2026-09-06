package cn.ac.fage.accessmesh.gateway;

import cn.ac.fage.accessmesh.common.config.CommonAutoConfiguration;
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
 *
 * <p>T-ACCESS-003 评审 P1 修复（2026-08-14）：排除 {@link CommonAutoConfiguration}。
 * common 的 GlobalExceptionHandler / RResponseAdvice 是 WebMvc（Servlet）组件，
 * Gateway 为 WebFlux 应用，加载后与 gateway 自身的 GlobalExceptionHandler 同名 Bean 冲突
 * 导致上下文无法启动（归并前既有缺陷，由新 Gateway 上下文测试暴露）。
 * 排除后 common 的缓存自动配置（CacheAutoConfiguration/RedissonCacheAutoConfiguration
 * 独立注册于 AutoConfiguration.imports）与 BizException/SystemException（普通类）不受影响。</p>
 */
@SpringBootApplication(exclude = CommonAutoConfiguration.class)
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
