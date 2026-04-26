package cn.ac.fage.accessmesh.gateway.config;

import io.micrometer.tracing.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer Tracing configuration.
 *
 * traceId is automatically generated and propagated via the
 * micrometer-tracing-bridge-otel dependency. No custom bean needed
 * for basic usage — this class exists as a placeholder for future
 * tracing customization (e.g. custom span tags).
 */
@Configuration
public class TracingConfig {

    // Tracer bean is auto-configured by Spring Boot when
    // micrometer-tracing-bridge-otel is on the classpath.
    // Downstream filters inject it to extract traceId for error responses.
}
