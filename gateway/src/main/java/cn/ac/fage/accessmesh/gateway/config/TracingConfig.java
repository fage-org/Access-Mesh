package cn.ac.fage.accessmesh.gateway.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.reactive.ServerHttpObservationFilter;

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
     * 创建HTTP观测过滤器
     * <p>
     * 启用基于观测的WebFlux路由追踪。
     * 每个请求获得一个服务端span，以路由ID作为操作名称。
     * 用于分布式链路追踪和性能监控。
     * </p>
     *
     * @param observationRegistry 观测注册表
     * @return HTTP观测过滤器实例
     */
    @Bean
    public ServerHttpObservationFilter serverHttpObservationFilter(ObservationRegistry observationRegistry) {
        return new ServerHttpObservationFilter(observationRegistry);
    }
}