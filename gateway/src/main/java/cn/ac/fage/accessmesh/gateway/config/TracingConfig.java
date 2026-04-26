package cn.ac.fage.accessmesh.gateway.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.reactive.ServerHttpObservationFilter;

/**
 * Micrometer Tracing configuration for WebFlux gateway.
 *
 * Spring Boot auto-configures the Tracer bean when micrometer-tracing-bridge-otel
 * is on the classpath. This class adds reactive observation support so that
 * every request passing through the gateway gets a span automatically.
 */
@Configuration
public class TracingConfig {

    /**
     * Enables observation-based tracing for WebFlux routes.
     * Each request gets a server span with the route ID as operation name.
     */
    @Bean
    public ServerHttpObservationFilter serverHttpObservationFilter(ObservationRegistry observationRegistry) {
        return new ServerHttpObservationFilter(observationRegistry);
    }
}
