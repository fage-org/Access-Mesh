package cn.ac.fage.accessmesh.perm.client.config;

import cn.ac.fage.accessmesh.perm.common.feign.FeignCredentialInterceptor;
import cn.ac.fage.accessmesh.perm.common.feign.PermFeignClientsConfiguration;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PermClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
            PermClientAutoConfiguration.class));

    /** 权限客户端配置类的装配上下文（@FeignClient(configuration=...) 挂载形态的单元等价）。 */
    private final ApplicationContextRunner clientContextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
        .withUserConfiguration(PermFeignClientsConfiguration.class);

    @Test
    void shouldNotRegisterGlobalRequestInterceptorInHostContext() {
        // T-ACCESS-098：宿主上下文不注册任何 RequestInterceptor（凭证拦截器只进权限客户端
        // 的 Feign 子上下文，见 PermFeignClientsConfiguration）——宿主 bean 会经
        // beansOfTypeIncludingAncestors 命中宿主全部 Feign 客户端。旧全局装配下本断言失败。
        runner.withPropertyValues("perm.credential-id=sc-test", "perm.credential-secret=sk-test",
            "perm.allow-insecure=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(feign.RequestInterceptor.class);
            assertThat(context).doesNotHaveBean(FeignCredentialInterceptor.class);
        });
    }

    @Test
    void shouldAssembleCredentialInterceptorInPermissionClientContext() {
        clientContextRunner.withPropertyValues("perm.credential-id=sc-test",
            "perm.credential-secret=sk-test", "perm.allow-insecure=true").run(context -> {
            assertThat(context).hasSingleBean(FeignCredentialInterceptor.class);
            var template = new RequestTemplate();
            template.method("POST");
            template.uri("/api/access/auth/check");
            // 外评 P2-1 处置后注入以目标接口为判据——挂真实 PermissionFeignClient 元信息
            template.methodMetadata(new org.springframework.cloud.openfeign.support.SpringMvcContract()
                .parseAndValidateMetadata(cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient.class)
                .stream().filter(md -> md.configKey().contains("checkAuth")).findFirst().orElseThrow());
            context.getBean(FeignCredentialInterceptor.class).apply(template);
            assertThat(template.headers()).containsKeys("X-Credential-Id", "X-Credential-Secret")
                .doesNotContainKeys("X-Internal-Secret", "X-Service-Code", "X-Tenant-Id");
        });
    }

    @Test
    void shouldNotCreateInterceptorWithoutCredentialId() {
        clientContextRunner.withPropertyValues("perm.allow-insecure=true")
            .run(context -> assertThat(context).doesNotHaveBean(FeignCredentialInterceptor.class));
    }

    @Test
    void shouldNotRestoreRetiredSecretInjector_whenOnlyOldConfigurationIsPresent() {
        runner.withPropertyValues("perm.internal-secret=old-secret", "perm.service-code=legacy")
            .run(context -> assertThat(context).doesNotHaveBean(feign.RequestInterceptor.class));
    }

    @Test
    void shouldFailFast_whenCredentialSecretIsMissing() {
        // fail-fast 时点随装配边界移位：从宿主上下文刷新移到权限客户端 bean 创建（子上下文刷新）
        clientContextRunner.withPropertyValues("perm.credential-id=sc-test", "perm.allow-insecure=true")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldDisableClient_whenExplicitlyDisabled() {
        runner.withPropertyValues("perm.client.enabled=false", "perm.credential-id=sc-test")
            .run(context -> assertThat(context).doesNotHaveBean(FeignCredentialInterceptor.class));
    }
}
