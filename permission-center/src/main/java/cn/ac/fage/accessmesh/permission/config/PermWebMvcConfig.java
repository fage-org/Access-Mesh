package cn.ac.fage.accessmesh.permission.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Permission-center MVC配置类
 * <p>
 * 注册拦截器，执行顺序如下：
 * <ol>
 *   <li>order=1 InternalApiSecretInterceptor — 仅作用于 /api/perm/**。
 *       通过则在 request 写 INTERNAL_AUTHENTICATED=true；失败则直接 403 阻断后续。</li>
 *   <li>order=2 HeaderSignatureInterceptor — 覆盖 /api/**, /internal/**, /actuator/**。
 *       依据 INTERNAL_AUTHENTICATED attribute 与请求头组合做 4 路径决策树。</li>
 *   <li>order=3 PermTenantInterceptor — 提取并设置租户上下文。</li>
 * </ol>
 * </p>
 *
 * <p>安全决策原则：基于已验证的 attribute（仅前置拦截器可写）而非未验证的请求头。
 * 任何拦截器对外暴露的“信任决策”都不允许直接读未经验证的 X-* 请求头。</p>
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
     * 顺序与决策语义：
     * </p>
     * <ul>
     *   <li>order=1 InternalApiSecretInterceptor: 必须最先执行——成功时写
     *       {@link InternalApiSecretInterceptor#ATTR_INTERNAL_AUTHENTICATED} 标志。
     *       仅作用于 /api/perm/**（业务端点），不覆盖 /internal/**、/actuator/**。</li>
     *   <li>order=2 HeaderSignatureInterceptor: 读 INTERNAL_AUTHENTICATED attribute
     *       决定是否跳过 HMAC；这是“服务间内部调用”与“用户态调用”的分支根据。</li>
     *   <li>order=3 PermTenantInterceptor: 在身份层认证通过后再设置租户上下文。</li>
     * </ul>
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
        // 决策依据：INTERNAL_AUTHENTICATED attribute（仅 order=1 写入，请求方无法伪造）。
        registry.addInterceptor(headerSignatureInterceptor)
                .addPathPatterns("/api/**", "/internal/**", "/actuator/**")
                .order(2);

        // order=3：租户拦截器，提取并设置租户上下文。
        registry.addInterceptor(permTenantInterceptor)
                .addPathPatterns("/api/**", "/internal/**", "/actuator/**")
                .order(3);
    }
}
