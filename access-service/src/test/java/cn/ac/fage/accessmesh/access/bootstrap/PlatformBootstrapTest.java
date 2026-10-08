package cn.ac.fage.accessmesh.access.bootstrap;

import cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformBootstrapTest {
    @Test
    void shouldNotReplaceExistingCredentialsEvenWhenEnvironmentIsEmpty() {
        var accounts = mock(PlatformAccountDomainService.class);
        var audit = mock(PlatformAuditDomainService.class);
        when(accounts.count()).thenReturn(1L);
        new PlatformBootstrapInitializer(accounts, audit).initialize(null, null);
        verify(accounts, never()).insert(any());
        verifyNoInteractions(audit);
    }

    @Test
    void shouldRejectMissingInitialSecretBeforeCreatingAnything() {
        var accounts = mock(PlatformAccountDomainService.class);
        var audit = mock(PlatformAuditDomainService.class);
        assertThatThrownBy(() -> new PlatformBootstrapInitializer(accounts, audit).initialize("admin", ""))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("ACCESS_PLATFORM_ADMIN_PASSWORD");
        verify(accounts, never()).insert(any());
        verifyNoInteractions(audit);
    }

    @Test
    void shouldEnableOnlyThePlatformBootstrapOnExplicitConfiguration() {
        var context = new ApplicationContextRunner()
            .withUserConfiguration(PlatformBootstrapProperties.class, PlatformBootstrapRunner.class)
            .withBean(PlatformBootstrapInitializer.class, () -> mock(PlatformBootstrapInitializer.class));
        context.run(app -> assertThat(app).doesNotHaveBean(PlatformBootstrapRunner.class));
        context.withPropertyValues("access.platform.bootstrap.enabled=true")
            .run(app -> assertThat(app).hasSingleBean(PlatformBootstrapRunner.class));
    }
}
