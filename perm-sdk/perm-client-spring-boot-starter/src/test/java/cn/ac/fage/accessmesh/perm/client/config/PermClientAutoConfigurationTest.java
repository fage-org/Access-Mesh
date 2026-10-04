package cn.ac.fage.accessmesh.perm.client.config;

import cn.ac.fage.accessmesh.perm.common.feign.FeignCredentialInterceptor;
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

    @Test
    void shouldAssembleCredentialOnlyClient_withoutInternalSecretOrSelfDeclaredService() {
        runner.withPropertyValues("perm.credential-id=sc-test", "perm.credential-secret=sk-test",
            "perm.allow-insecure=true").run(context -> {
                assertThat(context).hasSingleBean(FeignCredentialInterceptor.class);
                var template = new RequestTemplate();
                template.uri("/api/access/auth/check");
                context.getBean(FeignCredentialInterceptor.class).apply(template);
                assertThat(template.headers()).containsKeys("X-Credential-Id", "X-Credential-Secret")
                    .doesNotContainKeys("X-Internal-Secret", "X-Service-Code", "X-Tenant-Id");
            });
    }

    @Test
    void shouldNotRestoreRetiredSecretInjector_whenOnlyOldConfigurationIsPresent() {
        runner.withPropertyValues("perm.internal-secret=old-secret", "perm.service-code=legacy")
            .run(context -> assertThat(context).doesNotHaveBean(feign.RequestInterceptor.class));
    }

    @Test
    void shouldFailFast_whenCredentialSecretIsMissing() {
        runner.withPropertyValues("perm.credential-id=sc-test", "perm.allow-insecure=true")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldDisableClient_whenExplicitlyDisabled() {
        runner.withPropertyValues("perm.client.enabled=false", "perm.credential-id=sc-test")
            .run(context -> assertThat(context).doesNotHaveBean(FeignCredentialInterceptor.class));
    }
}
