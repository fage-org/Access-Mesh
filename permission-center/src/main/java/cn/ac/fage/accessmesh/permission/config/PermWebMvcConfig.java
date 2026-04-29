package cn.ac.fage.accessmesh.permission.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration for permission-center.
 * Registers PermTenantInterceptor to extract X-Tenant-Id from every request.
 */
@Configuration
public class PermWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PermTenantInterceptor())
                .addPathPatterns("/api/**", "/internal/**");
    }
}
