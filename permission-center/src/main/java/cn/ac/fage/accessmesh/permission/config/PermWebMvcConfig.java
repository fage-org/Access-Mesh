package cn.ac.fage.accessmesh.permission.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration for permission-center.
 * Registers interceptors in the following order:
 * 1. HeaderSignatureInterceptor - Validates gateway header signatures (highest priority)
 * 2. PermTenantInterceptor - Extracts tenant ID from headers
 * 3. InternalApiSecretInterceptor - Secures internal management APIs
 */
@Configuration
public class PermWebMvcConfig implements WebMvcConfigurer {

    private final PermTenantInterceptor permTenantInterceptor;
    private final InternalApiSecretInterceptor internalApiSecretInterceptor;
    private final HeaderSignatureInterceptor headerSignatureInterceptor;

    @Autowired
    public PermWebMvcConfig(PermTenantInterceptor permTenantInterceptor,
                            InternalApiSecretInterceptor internalApiSecretInterceptor,
                            HeaderSignatureInterceptor headerSignatureInterceptor) {
        this.permTenantInterceptor = permTenantInterceptor;
        this.internalApiSecretInterceptor = internalApiSecretInterceptor;
        this.headerSignatureInterceptor = headerSignatureInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register signature interceptor first for all API paths
        // This validates that headers from gateway are authentic
        registry.addInterceptor(headerSignatureInterceptor)
                .addPathPatterns("/api/**", "/internal/**")
                .order(1);

        // Register tenant interceptor for all API paths
        registry.addInterceptor(permTenantInterceptor)
                .addPathPatterns("/api/**", "/internal/**")
                .order(2);

        // Register internal API secret interceptor for management endpoints
        // Protects all /api/perm/** paths (now including /api/perm/auth/**)
        // Gateway must send X-Internal-Secret header when calling auth endpoints
        registry.addInterceptor(internalApiSecretInterceptor)
                .addPathPatterns("/api/perm/**")
                .order(3);
    }
}
