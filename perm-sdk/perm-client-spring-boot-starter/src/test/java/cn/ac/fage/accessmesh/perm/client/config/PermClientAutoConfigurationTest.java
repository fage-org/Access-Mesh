package cn.ac.fage.accessmesh.perm.client.config;

import cn.ac.fage.accessmesh.perm.client.feign.FeignInternalSyncInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starter 上下文装配测试（T-ACCESS-010 评审 P1 修复回归）。
 * <p>
 * 背景：{@code @EnableFeignClients} 只注册 Feign 接口，业务服务的组件扫描覆盖不到
 * SDK 包——{@link FeignInternalSyncInterceptor} 必须由自动配置显式装配，否则
 * SDK 调用不携带 {@code X-Internal-Secret}/{@code X-Service-Code} 会被
 * access-service 拒绝。手工 {@code new} 拦截器的单元测试（FeignInternalSyncInterceptorTest）
 * 绕过了装配过程，无法暴露该缺陷，本测试补齐装配层验证。
 * </p>
 * <p>
 * runner 额外注册 {@link PropertyPlaceholderAutoConfiguration}：真实 Boot 应用必有
 * 占位符解析器（strict 模式），而纯注解测试上下文没有时，未解析的
 * {@code ${perm.service-code}} 会以字面量静默注入、不失败——必须复现真实语义
 * 才能验证 fail-fast。
 * </p>
 */
class PermClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            PropertyPlaceholderAutoConfiguration.class,
            PermClientAutoConfiguration.class));

    @Test
    @DisplayName("配置 internal-secret + service-code 时拦截器被装配（@Import 而非组件扫描）")
    void interceptorIsImportedWhenSecretConfigured() {
        runner.withPropertyValues(
                "perm.internal-secret=test-secret",
                "perm.service-code=example-service")
            .run(context -> {
                assertThat(context).hasSingleBean(FeignInternalSyncInterceptor.class);
                assertThat(reflectField(context.getBean(FeignInternalSyncInterceptor.class), "serviceCode"))
                    .isEqualTo("example-service");
                assertThat(reflectField(context.getBean(FeignInternalSyncInterceptor.class), "internalSecret"))
                    .isEqualTo("test-secret");
            });
    }

    @Test
    @DisplayName("未配置 internal-secret 时拦截器不注册（@ConditionalOnProperty 在 @Import 下照常评估）")
    void interceptorNotRegisteredWithoutSecret() {
        runner.run(context ->
            assertThat(context).doesNotHaveBean(FeignInternalSyncInterceptor.class));
    }

    @Test
    @DisplayName("perm.service-code 缺失时启动失败（无默认值 fail-fast，防冒充退役服务身份）")
    void missingServiceCodeFailsFast() {
        runner.withPropertyValues("perm.internal-secret=test-secret")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .as("缺失 perm.service-code 应因占位符解析失败启动失败（原因链含未解析占位符）")
                    .rootCause()
                    .hasMessageContaining("Could not resolve placeholder 'perm.service-code'");
            });
    }

    @Test
    @DisplayName("perm.client.enabled=false 时自动配置整体停用")
    void autoConfigurationDisabledByFlag() {
        runner.withPropertyValues(
                "perm.client.enabled=false",
                "perm.internal-secret=test-secret",
                "perm.service-code=example-service")
            .run(context ->
                assertThat(context).doesNotHaveBean(FeignInternalSyncInterceptor.class));
    }

    private static Object reflectField(Object bean, String name) {
        try {
            Field f = bean.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(bean);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
