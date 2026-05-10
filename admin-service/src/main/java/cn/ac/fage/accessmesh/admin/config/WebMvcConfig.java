package cn.ac.fage.accessmesh.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC配置类
 * <p>
 * 配置admin-service的Web MVC拦截器。
 * 注册租户隔离拦截器，应用于所有请求。
 * </p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 注册拦截器
     * <p>
     * 注册租户隔离拦截器，应用于所有请求路径。
     * 注意：登录认证由Gateway处理（AuthTokenFilter + PermissionFilter）
     * </p>
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 租户隔离拦截器 — 应用所有请求
        // 注意：登录认证由Gateway处理（AuthTokenFilter + PermissionFilter）
        registry.addInterceptor(new TenantInterceptor())
            .addPathPatterns("/**");
    }
}