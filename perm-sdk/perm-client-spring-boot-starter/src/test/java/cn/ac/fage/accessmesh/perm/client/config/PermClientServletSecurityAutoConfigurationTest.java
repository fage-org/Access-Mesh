package cn.ac.fage.accessmesh.perm.client.config;

import cn.ac.fage.accessmesh.perm.client.security.GatewaySignatureFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.util.ClassUtils;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class PermClientServletSecurityAutoConfigurationTest {
    private static AutoConfigurations publishedConfigurations() {
        ClassLoader loader = PermClientServletSecurityAutoConfigurationTest.class.getClassLoader();
        Class<?>[] published = StreamSupport.stream(ImportCandidates.load(AutoConfiguration.class, loader).spliterator(), false)
            .filter(name -> name.startsWith("cn.ac.fage.accessmesh.perm.client.config."))
            .map(name -> ClassUtils.resolveClassName(name, loader)).toArray(Class<?>[]::new);
        return AutoConfigurations.of(published);
    }

    private WebApplicationContextRunner web() {
        return new WebApplicationContextRunner()
            .withConfiguration(publishedConfigurations())
            .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues("perm.client.signature.secret=test-signature-secret",
                "spring.cloud.openfeign.client.config.access-service.url=http://localhost:9999");
    }

    @Test
    void servletStarterRegistersSignatureFilterByDefault() {
        web().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(GatewaySignatureFilter.class);
            // 无发现客户端/loadbalancer Bean；标准 URL 配置仍可创建真实 Feign 代理。
            assertThat(context.getBean(cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient.class)).isNotNull();
        });
    }

    @Test
    void explicitDisableDoesNotRegisterFilter() {
        web().withPropertyValues("perm.client.enabled=false")
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(GatewaySignatureFilter.class));
    }

    @Test
    void nonWebConsumerDoesNotRequireServletFilter() {
        new ApplicationContextRunner().withConfiguration(publishedConfigurations())
            .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(GatewaySignatureFilter.class));
    }
}
