package cn.ac.fage.accessmesh.gateway.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.webflux.autoconfigure.WebHttpHandlerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer链路追踪配置类
 * <p>
 * WebFlux网关的链路追踪配置。
 * Spring Boot在classpath包含micrometer-tracing-bridge-otel时
 * 自动配置Tracer Bean。此类添加响应式观测支持，
 * 使每个经过网关的请求自动获得一个span。
 * </p>
 */
@Configuration
public class TracingConfig {

    /**
     * 将观测注册表接入 WebFlux HTTP handler。
     * <p>
     * 启用基于观测的WebFlux路由追踪。
     * 每个请求获得一个服务端 span。
     * 用于分布式链路追踪和性能监控。
     * </p>
     *
     * @param observationRegistry 观测注册表
     * @return HTTP handler 构建定制器
     */
    @Bean
    public WebHttpHandlerBuilderCustomizer gatewayObservationCustomizer(ObservationRegistry observationRegistry) {
        return builder -> builder.observationRegistry(observationRegistry);
    }
}
