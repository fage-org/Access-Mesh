package cn.ac.fage.accessmesh.permission.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration for permission-center.
 * Registers PermTenantInterceptor to extract X-Tenant-Id from every request.
 * Registers InternalApiSecretInterceptor to secure internal management APIs.
 */
@Configuration
public class PermWebMvcConfig implements WebMvcConfigurer {

    private final PermTenantInterceptor permTenantInterceptor;
    private final InternalApiSecretInterceptor internalApiSecretInterceptor;

    @Autowired
    public PermWebMvcConfig(PermTenantInterceptor permTenantInterceptor,
                            InternalApiSecretInterceptor internalApiSecretInterceptor) {
        this.permTenantInterceptor = permTenantInterceptor;
        this.internalApiSecretInterceptor = internalApiSecretInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register tenant interceptor for all API paths
        registry.addInterceptor(permTenantInterceptor)
                .addPathPatterns("/api/**", "/internal/**");

        // Register internal API secret interceptor for management endpoints
        // Protects all /api/perm/** paths (now including /api/perm/auth/**)
        // Gateway must send X-Internal-Secret header when calling auth endpoints
        registry.addInterceptor(internalApiSecretInterceptor)
                .addPathPatterns("/api/perm/**");
    }
}
