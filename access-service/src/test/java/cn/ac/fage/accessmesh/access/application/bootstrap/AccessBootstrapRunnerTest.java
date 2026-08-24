package cn.ac.fage.accessmesh.access.application.bootstrap;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * bootstrap 触发器单测（T-ACCESS-020）：密码缺失/空白 fail-fast（不触发 initializer）；
 * 正常密码委托事务化 initializer 并清理租户上下文；默认关闭的装配语义经 ApplicationContextRunner 验证。
 */
class AccessBootstrapRunnerTest {

    private final AccessBootstrapInitializer initializer = mock(AccessBootstrapInitializer.class);

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("enabled=true 且密码空白 → fail-fast，不触发 initializer")
    void blankPasswordFailsFastWithoutTouchingInitializer() {
        AccessBootstrapProperties properties = new AccessBootstrapProperties();
        properties.setEnabled(true);
        properties.setAdminPassword("   ");

        AccessBootstrapRunner runner = new AccessBootstrapRunner(properties, initializer);
        assertThatThrownBy(() -> runner.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ACCESS_BOOTSTRAP_ADMIN_PASSWORD");
        verifyNoInteractions(initializer);
    }

    @Test
    @DisplayName("密码就绪 → 委托 initializer，完成后清理租户上下文")
    void delegatesToInitializerAndClearsTenantContext() {
        AccessBootstrapProperties properties = new AccessBootstrapProperties();
        properties.setEnabled(true);
        properties.setAdminPassword("bootstrap-secret");

        new AccessBootstrapRunner(properties, initializer).run(null);
        verify(initializer).initialize("bootstrap-secret");
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    @DisplayName("装配语义：enabled 缺省 → Runner 不装配；enabled=true → 装配（@ConditionalOnProperty 生效）")
    void runnerWiringFollowsEnabledFlag() {
        // 默认关闭（验收项"默认关闭的幂等 ApplicationRunner"）——注解被删/属性名漂移时本测试报警
        new ApplicationContextRunner()
            .withUserConfiguration(AccessBootstrapRunner.class, AccessBootstrapProperties.class)
            .withBean(AccessBootstrapInitializer.class, () -> mock(AccessBootstrapInitializer.class))
            .run(context -> assertThat(context).doesNotHaveBean(AccessBootstrapRunner.class));

        new ApplicationContextRunner()
            .withPropertyValues("access.bootstrap.enabled=true")
            .withUserConfiguration(AccessBootstrapRunner.class, AccessBootstrapProperties.class)
            .withBean(AccessBootstrapInitializer.class, () -> mock(AccessBootstrapInitializer.class))
            .run(context -> assertThat(context).hasSingleBean(AccessBootstrapRunner.class));
    }
}
