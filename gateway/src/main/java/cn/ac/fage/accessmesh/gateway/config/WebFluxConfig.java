package cn.ac.fage.accessmesh.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux configuration — codec limits and custom converters.
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // Default max in-memory size is 256KB, increase for large request bodies
        configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB
    }
}
