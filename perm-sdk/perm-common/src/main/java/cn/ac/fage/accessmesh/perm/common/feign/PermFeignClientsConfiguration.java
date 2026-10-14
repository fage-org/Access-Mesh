package cn.ac.fage.accessmesh.perm.common.feign;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 权限客户端专用 Feign 装配（T-ACCESS-098）：经 {@code @FeignClient(configuration=...)}
 * 挂载到权限客户端（PermissionFeignClient/PermissionManifestClient）各自的 Feign 子上下文。
 * <p>
 * 凭证拦截器只进权限客户端的子上下文——宿主应用上下文与宿主其他 Feign 客户端均不可见
 * （spring-cloud NamedContextFactory#getInstances 按 beansOfTypeIncludingAncestors 收集：
 * 宿主上下文里的 RequestInterceptor bean 会命中全部客户端，故本类禁止以
 * @Component/@Import 注册进宿主上下文）。子上下文经 setParent 继承宿主 Environment
 * （@Value 与条件注解可解析应用配置源）。
 * </p>
 * <p>
 * 刻意不标注 @Configuration：本类只应作为 @FeignClient configuration 属性被 Feign 子上下文
 * 注册；若被宿主组件扫描收编会回到全局装配（每个 @Bean 进入宿主上下文）。
 * </p>
 */
public class PermFeignClientsConfiguration {

    /** 仅在配置了凭证标识时装配（与拦截器配置校验配套：半配/缺信任域声明在 bean 创建时 fail-fast）。 */
    @Bean
    @ConditionalOnProperty(name = "perm.credential-id")
    public FeignCredentialInterceptor feignCredentialInterceptor() {
        return new FeignCredentialInterceptor();
    }
}
