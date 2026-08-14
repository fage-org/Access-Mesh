package cn.ac.fage.accessmesh.access.permission.config;

import cn.ac.fage.accessmesh.access.infrastructure.RequestContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 统一安全 MVC 配置（T-ACCESS-004，替换 admin WebMvcConfig 与 permission PermWebMvcConfig 双链）。
 * <p>
 * 全库唯一 WebMvcConfigurer，注册统一安全链，执行顺序如下：
 * </p>
 * <ol>
 *   <li>order=1 InternalApiSecretInterceptor — 仅作用于 /api/perm/**。
 *       内部凭证（X-Internal-Secret）通过则在 request 写 INTERNAL_AUTHENTICATED=true；
 *       失败直接 403 阻断后续。</li>
 *   <li>order=2 HeaderSignatureInterceptor — 覆盖 /api/**, /internal/**, /actuator/**。
 *       X-User-Id 头恒需验签（含内部凭证场景）；通过后写 SIGNATURE_VERIFIED attribute。</li>
 *   <li>order=3 RequestContextInterceptor — 覆盖 /**。唯一绑定
 *       {@link cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext} 的入口：
 *       按安全策略矩阵建立 tenantId/operatorId/callerType/serviceCode，公开路径匿名放行，
 *       未登录非公开路径 401；注入 MDC 并在 afterCompletion 统一清理。</li>
 * </ol>
 * <p>安全决策原则：基于已验证的 attribute（仅前置拦截器可写）而非未验证的请求头。
 * 任何拦截器对外暴露的"信任决策"都不允许直接读未经验证的 X-* 请求头。</p>
 */
@Configuration
public class SecurityWebMvcConfig implements WebMvcConfigurer {

    private final InternalApiSecretInterceptor internalApiSecretInterceptor;
    private final HeaderSignatureInterceptor headerSignatureInterceptor;
    private final RequestContextInterceptor requestContextInterceptor;

    public SecurityWebMvcConfig(InternalApiSecretInterceptor internalApiSecretInterceptor,
                                HeaderSignatureInterceptor headerSignatureInterceptor,
                                RequestContextInterceptor requestContextInterceptor) {
        this.internalApiSecretInterceptor = internalApiSecretInterceptor;
        this.headerSignatureInterceptor = headerSignatureInterceptor;
        this.requestContextInterceptor = requestContextInterceptor;
    }

    /**
     * 注册统一安全链。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // order=1：内部密钥拦截器必须最先执行，成功时写 attribute 供后续决策。
        // 仅覆盖 /api/perm/**（不跨 actuator/internal）；失败直接 403 终止链。
        registry.addInterceptor(internalApiSecretInterceptor)
                .addPathPatterns("/api/perm/**")
                .order(1);

        // order=2：签名拦截器，覆盖所有受控路径。
        // X-User-Id 恒需验签（T-ACCESS-004 修复 G1：内部凭证路径不再无条件信任用户头）。
        // 评审 P2-2（2026-08-14）：/actuator/** 排除出签名链——公开端点契约不依赖头，
        // 监控探针带 X-Tenant-Id 头访问 health 时不被路径 4 误拒。
        registry.addInterceptor(headerSignatureInterceptor)
                .addPathPatterns("/api/**", "/internal/**")
                .order(2);

        // order=3：统一请求上下文拦截器，覆盖全部路径（公开路径内部放行）。
        // 唯一 AccessRequestContext 绑定入口；afterCompletion 清理上下文与 MDC。
        registry.addInterceptor(requestContextInterceptor)
                .addPathPatterns("/**")
                .order(3);
    }
}
