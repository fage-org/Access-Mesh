package cn.ac.fage.accessmesh.access.infrastructure.config;

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
 *   <li>order=1 ServiceAuthArbiter（T-PERM-070 重构 InternalApiSecretInterceptor 单策略位）
 *       — 作用于 /api/access/** 豁免会话入口族（精确清单以 addInterceptors 的
 *       excludePathPatterns 为准，勿在此枚举防漂移；运行时鉴权六端点维持覆盖，
 *       T-ACCESS-042 URL 单命名空间）。双策略仲裁：凭证头（X-Credential-Id/
 *       X-Credential-Secret）→ ServicePrincipal attribute + M2M 白名单强制；
 *       无凭证头回落内部密钥（X-Internal-Secret 通过写 INTERNAL_AUTHENTICATED=true）；
 *       任一失败直接 403 阻断后续。</li>
 *   <li>order=2 HeaderSignatureInterceptor — 覆盖 /api/access/**, /internal/**。
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

    private final ServiceAuthArbiter serviceAuthArbiter;
    private final HeaderSignatureInterceptor headerSignatureInterceptor;
    private final RequestContextInterceptor requestContextInterceptor;

    public SecurityWebMvcConfig(ServiceAuthArbiter serviceAuthArbiter,
                                HeaderSignatureInterceptor headerSignatureInterceptor,
                                RequestContextInterceptor requestContextInterceptor) {
        this.serviceAuthArbiter = serviceAuthArbiter;
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
        // order=1：服务认证仲裁器必须最先执行，成功时写 attribute 供后续决策
        // （ServicePrincipal=CREDENTIAL 路径或 INTERNAL_AUTHENTICATED=旧密钥路径）。
        // 覆盖 /api/access/**（T-ACCESS-042 前为 /api/perm/**——管理面并入后统一「仅经
        // Gateway 或持凭证/密钥服务可达」）；豁免会话入口族与白名单自服务端点（精确清单
        // 见下方 excludePathPatterns，与 Gateway 白名单同源——保留公开/Sa-Token 会话/JWT
        // 自有信任模型，密钥拦截会架空服务层会话分支与 JWT 分支）；
        // 运行时鉴权六端点（check/batch-check/check-interface/query-resources/query-scopes/
        // interface-snapshot）维持覆盖（旧密钥服务凭证通道，与迁移前 /api/perm/auth/* 一致——
        // INTERNAL_AUTHENTICATED 属性由本拦截器写入，豁免会使签名拦截器按 tenant-only 拒绝；
        // 新凭证通道的 M2M 白名单不含运行时鉴权六端点，凭证请求对其 403，见 M2mCredentialEndpoints）。
        // 失败直接 403 终止链。
        registry.addInterceptor(serviceAuthArbiter)
                .addPathPatterns("/api/access/**")
                .excludePathPatterns(
                        "/api/access/auth/captcha",
                        "/api/access/auth/login",
                        "/api/access/auth/login/sms",
                        "/api/access/auth/logout",
                        "/api/access/auth/userinfo",
                        "/api/access/auth/user-menu",
                        "/api/access/auth/oauth2/**",
                        "/api/access/user/reset-password",
                        // T-ADMIN-029：公告自服务两端点（与 Gateway 白名单同源四载体之一）——
                        // 密钥拦截不豁免则白名单路径（Gateway 只注入密钥不注入用户/租户头）落
                        // 「纯服务调用」分支恒 400；豁免后走 Sa-Token 会话分支（tenant 从会话读，
                        // 与 Controller StpUtil 同源），reset-password 同款链路
                        "/api/access/notice/my-notices",
                        "/api/access/notice/read")
                .order(1);

        // order=2：签名拦截器，覆盖所有受控路径。
        // X-User-Id 恒需验签（T-ACCESS-004 修复 G1：内部凭证路径不再无条件信任用户头）。
        // 评审 P2-2（2026-08-14）：/actuator/** 排除出签名链——公开端点契约不依赖头，
        // 监控探针带 X-Tenant-Id 头访问 health 时不被路径 4 误拒。
        registry.addInterceptor(headerSignatureInterceptor)
                .addPathPatterns("/api/access/**", "/internal/**")
                .order(2);

        // order=3：统一请求上下文拦截器，覆盖全部路径（公开路径内部放行）。
        // 唯一 AccessRequestContext 绑定入口；afterCompletion 清理上下文与 MDC。
        registry.addInterceptor(requestContextInterceptor)
                .addPathPatterns("/**")
                .order(3);
    }
}
