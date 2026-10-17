package cn.ac.fage.accessmesh.perm.common.feign;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

/**
 * 权限客户端专用 Feign 子上下文配置，不向宿主或其他 Feign 客户端暴露凭证拦截器。
 * 不标注 Configuration/Component，不由宿主扫描注册；仅经 FeignClient configuration 引用。
 * Spring Cloud 5 通过 ClientHttpMessageConvertersCustomizer 构建客户端转换器。
 * 复用宿主 JsonMapper；无宿主 mapper 时以局部实例提供 ISO 日期和宽容读取，不创建全局 mapper bean。
 */
public class PermFeignClientsConfiguration {
    @Bean
    @ConditionalOnProperty(name = "perm.credential-id")
    public FeignCredentialInterceptor feignCredentialInterceptor() {
        return new FeignCredentialInterceptor();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public ClientHttpMessageConvertersCustomizer permissionFeignJsonCustomizer(ObjectProvider<JsonMapper> hostMappers) {
        JsonMapper mapper = hostMappers.getIfAvailable(() -> JsonMapper.builder().configureForJackson2()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());
        return builder -> builder.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper));
    }
}
