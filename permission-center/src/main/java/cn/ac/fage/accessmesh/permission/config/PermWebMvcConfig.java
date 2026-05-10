package cn.ac.fage.accessmesh.permission.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Permission-center MVC配置类
 * <p>
 * 注册拦截器，执行顺序如下：
 * 1. HeaderSignatureInterceptor - 验证Gateway请求头签名（最高优先级）
 * 2. PermTenantInterceptor - 从请求头提取租户ID
 * 3. InternalApiSecretInterceptor - 保护内部管理API
 * </p>
 */
@Configuration
public class PermWebMvcConfig implements WebMvcConfigurer {

    private final PermTenantInterceptor permTenantInterceptor;
    private final InternalApiSecretInterceptor internalApiSecretInterceptor;
    private final HeaderSignatureInterceptor headerSignatureInterceptor;

    /**
     * 构造MVC配置
     * <p>
     * 注入所有需要的拦截器实例。
     * </p>
     *
     * @param permTenantInterceptor       租户拦截器
     * @param internalApiSecretInterceptor 内部API密钥拦截器
     * @param headerSignatureInterceptor   请求头签名拦截器
     */
    @Autowired
    public PermWebMvcConfig(PermTenantInterceptor permTenantInterceptor,
                            InternalApiSecretInterceptor internalApiSecretInterceptor,
                            HeaderSignatureInterceptor headerSignatureInterceptor) {
        this.permTenantInterceptor = permTenantInterceptor;
        this.internalApiSecretInterceptor = internalApiSecretInterceptor;
        this.headerSignatureInterceptor = headerSignatureInterceptor;
    }

    /**
     * 注册拦截器
     * <p>
     * 按优先级顺序注册三个拦截器：
     * - 签名拦截器验证Gateway注入的请求头真实性
     * - 租户拦截器提取并设置租户上下文
     * - 内部密钥拦截器保护管理端点
     * </p>
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 首先注册签名拦截器，覆盖所有API路径
        // 验证来自Gateway的请求头是真实的
        registry.addInterceptor(headerSignatureInterceptor)
                .addPathPatterns("/api/**", "/internal/**", "/actuator/**")
                .order(1);

        // 注册租户拦截器，覆盖所有API路径
        registry.addInterceptor(permTenantInterceptor)
                .addPathPatterns("/api/**", "/internal/**", "/actuator/**")
                .order(2);

        // 注册内部API密钥拦截器，保护管理端点
        // 保护所有/api/perm/**路径（包括/api/perm/auth/**）
        // Gateway调用认证端点时必须发送X-Internal-Secret请求头
        registry.addInterceptor(internalApiSecretInterceptor)
                .addPathPatterns("/api/perm/**")
                .order(3);
    }
}