package cn.ac.fage.accessmesh.example.perm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamplePermissionPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ExamplePermissionProperties.class)
    static class Config {}
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void shouldBindImmutableTenantMap_andHideSecretsInText() {
        runner.withPropertyValues("example.permission.allow-insecure=false",
            "example.permission.tenant-credentials[1].credential-id=sc-one",
            "example.permission.tenant-credentials[1].credential-secret=sk-one-private",
            "example.permission.tenant-credentials[7].credential-id=sc-seven",
            "example.permission.tenant-credentials[7].credential-secret=sk-seven-private")
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                var properties = ctx.getBean(ExamplePermissionProperties.class);
                assertThat(properties.require("7").credentialId()).isEqualTo("sc-seven");
                assertThat(properties.toString()).doesNotContain("sk-one-private", "sk-seven-private");
                assertThatThrownBy(() -> properties.tenantCredentials().clear()).isInstanceOf(UnsupportedOperationException.class);
            });
    }

    @Test
    void shouldAllowEmptyConfiguration_butDenyAllTenants() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThatThrownBy(() -> ctx.getBean(ExamplePermissionProperties.class).require("1"))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"missing-secret", "missing-declaration", "invalid-declaration"})
    void shouldFailStartup_onIncompleteOrInvalidConfiguration(String scenario) {
        var configured = runner.withPropertyValues("example.permission.tenant-credentials[1].credential-id=sc-one");
        if (!scenario.equals("missing-secret")) configured = configured.withPropertyValues(
            "example.permission.tenant-credentials[1].credential-secret=sk-one");
        if (!scenario.equals("missing-declaration")) configured = configured.withPropertyValues(
            "example.permission.allow-insecure=" + (scenario.equals("invalid-declaration") ? "flase" : "true"));
        configured.run(ctx -> assertThat(ctx).hasFailed());
    }
}
