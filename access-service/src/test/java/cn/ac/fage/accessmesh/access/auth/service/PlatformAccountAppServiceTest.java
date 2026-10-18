package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformStatusReq;
import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import cn.ac.fage.accessmesh.access.auth.security.PlatformAccountGuard;
import cn.ac.fage.accessmesh.access.auth.security.PlatformActor;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import cn.ac.fage.accessmesh.access.auth.service.impl.PlatformAccountAppServiceImpl;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PlatformAccountAppServiceTest {
    @Test
    void overlappingPlatformResetIsRejectedBeforeTheManagementRowLock() {
        var accounts = mock(PlatformAccountDomainService.class);
        var guard = mock(PlatformAccountGuard.class);
        var audit = mock(PlatformAuditDomainService.class);
        var locks = mock(cn.ac.fage.accessmesh.access.infrastructure.PasswordResetLockSupport.class);
        doThrow(new BizException(10015, "重置进行中")).when(locks).lockPlatformAccount(2L);
        var service = new PlatformAccountAppServiceImpl(accounts, guard, audit,
            mock(cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore.class),
            mock(org.springframework.transaction.PlatformTransactionManager.class), locks);
        assertThatThrownBy(() -> service.resetPassword(2L)).isInstanceOf(BizException.class)
            .extracting("errorCode").isEqualTo(10015);
        verify(guard).requireOperator();
        verifyNoInteractions(accounts, audit);
    }

    @Test
    void tenantAdminRecoveryUsesTheSameUserLockBeforeTheTenantRowLock() {
        var tenants = mock(cn.ac.fage.accessmesh.access.tenant.service.domain.TenantDomainService.class);
        var guard = mock(PlatformAccountGuard.class);
        var users = mock(cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService.class);
        var locks = mock(cn.ac.fage.accessmesh.access.infrastructure.PasswordResetLockSupport.class);
        var tenant = new cn.ac.fage.accessmesh.access.tenant.entity.SysTenant();
        tenant.setAdminUserId(77L);
        when(tenants.findById(1L)).thenReturn(tenant);
        doThrow(new BizException(10015, "重置进行中")).when(locks).lockTenantUser(1L, 77L);
        var service = new cn.ac.fage.accessmesh.access.tenant.service.impl.TenantAppServiceImpl(
            tenants, guard, users,
            mock(cn.ac.fage.accessmesh.access.bootstrap.TenantBaselineInitializer.class),
            mock(cn.ac.fage.accessmesh.access.bootstrap.AccessBootstrapInitializer.class),
            mock(PlatformAuditDomainService.class),
            mock(cn.ac.fage.accessmesh.common.security.RedisTenantGateStore.class),
            mock(cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore.class),
            mock(org.springframework.transaction.PlatformTransactionManager.class), locks);
        assertThatThrownBy(() -> service.resetAdminPassword(1L)).isInstanceOf(BizException.class)
            .extracting("errorCode").isEqualTo(10015);
        verify(tenants, never()).lock(anyLong());
        verifyNoInteractions(users);
    }

    @Test
    void shouldKeepTheLastEnabledPlatformAdministrator() {
        var accounts = mock(PlatformAccountDomainService.class);
        var guard = mock(PlatformAccountGuard.class);
        var audit = mock(PlatformAuditDomainService.class);
        when(guard.requireOperator()).thenReturn(new PlatformActor(2L, "admin"));
        PlatformAccount account = new PlatformAccount();
        account.setId(2L);
        account.setStatus(1);
        when(accounts.findById(2L)).thenReturn(account);
        when(accounts.countEnabled()).thenReturn(1L);
        var service = new PlatformAccountAppServiceImpl(accounts, guard, audit,
            mock(cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore.class),
            mock(org.springframework.transaction.PlatformTransactionManager.class),
            mock(cn.ac.fage.accessmesh.access.infrastructure.PasswordResetLockSupport.class));
        assertThatThrownBy(() -> service.updateStatus(new PlatformStatusReq(2L, 0)))
            .isInstanceOf(BizException.class).hasMessageContaining("最后");
        verify(accounts, never()).updateStatus(anyLong(), anyInt(), anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void shouldRejectTenantIdentityEvenWhenItsNumericIdMatchesAPlatformAccount() {
        var accounts = mock(PlatformAccountDomainService.class);
        var guard = new PlatformAccountGuard(accounts);
        var previous = AccessRequestContext.snapshot();
        try {
            AccessRequestContext.bind(RequestContext.user(1L, 2L));
            assertThatThrownBy(guard::requireOperator).isInstanceOf(SecurityException.class);
            verifyNoInteractions(accounts);
        } finally {
            AccessRequestContext.restore(previous);
        }
    }
}
