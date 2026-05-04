package cn.ac.fage.accessmesh.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Tenant isolation interceptor — applies to all requests
        // Note: Login authentication is handled by Gateway (AuthTokenFilter + PermissionFilter)
        registry.addInterceptor(new TenantInterceptor())
            .addPathPatterns("/**");
    }
}
