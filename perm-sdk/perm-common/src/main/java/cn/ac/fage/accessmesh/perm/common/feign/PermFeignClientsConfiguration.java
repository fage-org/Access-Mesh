package cn.ac.fage.accessmesh.perm.common.feign;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

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
 * <p>
 * 编解码兜底（codex sol r2 P2-4）：spring-cloud-openfeign 默认 SpringEncoder/SpringDecoder
 * 经 {@code ObjectFactory<HttpMessageConverters>} 取 bean（字节码实证 {@code initConverters}
 * 直接 getObject，无 bean 即 NoSuchBeanDefinition）——Web 栈传递退役（T-ACCESS-100）后
 * WebFlux/无 Web 宿主不再有该 bean，且 jsr310 模块随 starter-web 退出闭包。本配置在
 * <b>子上下文内</b>提供 {@code @ConditionalOnMissingBean}（默认搜索含父上下文：Servlet
 * 宿主已有 converters 时复用宿主的）的兜底实例——专属 ObjectMapper（jsr310 + ISO-8601
 * 时间串，与服务端 Boot 默认序列化一致；未知字段宽容），不触碰宿主全局 ObjectMapper。
 * </p>
 */
public class PermFeignClientsConfiguration {

    /** 仅在配置了凭证标识时装配（与拦截器配置校验配套：半配/缺信任域声明在 bean 创建时 fail-fast）。 */
    @Bean
    @ConditionalOnProperty(name = "perm.credential-id")
    public FeignCredentialInterceptor feignCredentialInterceptor() {
        return new FeignCredentialInterceptor();
    }

    /**
     * 权限客户端 JSON 编解码兜底：宿主（Servlet 应用）已有 HttpMessageConverters 时
     * 复用宿主实例；WebFlux/无 Web 宿主无 bean 时在本子上下文提供——SDK 客户端调用
     * 不依赖宿主 Web 栈形态。时间字段按 ISO-8601 编解码（jackson-datatype-jsr310，
     * 随本模块 compile 传递给宿主）。
     */
    @Bean
    @ConditionalOnMissingBean(HttpMessageConverters.class)
    public HttpMessageConverters permissionFeignMessageConverters() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new HttpMessageConverters(new MappingJackson2HttpMessageConverter(mapper));
    }
}
