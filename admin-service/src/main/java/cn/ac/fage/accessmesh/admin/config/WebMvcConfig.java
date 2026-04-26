package cn.ac.fage.accessmesh.admin.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Tenant isolation interceptor — applies to all requests
        registry.addInterceptor(new TenantInterceptor())
            .addPathPatterns("/**");

        // Sa-Token login interceptor — skips auth endpoints
        registry.addInterceptor(new SaInterceptor(handle -> {
                }))
            .addPathPatterns("/**")
            .excludePathPatterns("/auth/captcha", "/auth/login", "/auth/oauth2/token", "/auth/oauth2/refresh");
    }
}
