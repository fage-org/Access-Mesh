package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService.LoginLogEntry;
import cn.ac.fage.accessmesh.access.auth.dto.LoginReq;
import cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.it.TenantTestSupport;
import cn.ac.fage.accessmesh.access.menu.service.UserMenuQueryAppService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAccessGuard;
import cn.ac.fage.accessmesh.access.tenant.service.domain.TenantDomainService;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LoginTransactionBoundaryTest {
    private final List<String> events = new ArrayList<>();
    private final BoundaryManager manager = new BoundaryManager(events);
    private final UserDomainService users = mock(UserDomainService.class);
    private final TenantDomainService tenants = mock(TenantDomainService.class);
    private final TenantAccessGuard tenantAccess = mock(TenantAccessGuard.class);
    private final LoginLogDomainService audit = mock(LoginLogDomainService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AuthAppServiceImpl service = new AuthAppServiceImpl(users, mock(UserOrgDomainService.class),
        tenants, tenantAccess, mock(LoginFailureStore.class), audit, redis, mock(UserMenuQueryAppService.class), manager);
    private final LoginReq request = new LoginReq(TenantTestSupport.CODE, "boundary-user", "Pass@123", "captcha", "8888", null);
    private LoginLogEntry recorded;

    private void prepare(boolean auditFails) {
        when(tenants.findByCode(TenantTestSupport.CODE)).thenReturn(TenantTestSupport.activeTenant());
        var user = new SysUser();
        user.setId(9L); user.setTenantId(1L); user.setUsername(request.username());
        user.setPassword("test-hash"); user.setStatus(1); user.setForceResetPwd(false);
        when(users.lockValidByUsername(1L, request.username())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return user;
        });
        when(tenantAccess.captureLoginProcess(anyLong(), anyLong())).thenReturn("a".repeat(40));
        when(redis.execute(any(DefaultRedisScript.class), anyList())).thenReturn("8888");
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            recorded = call.getArgument(0);
            events.add("audit");
            if (auditFails) throw new TransactionSystemException("audit commit failed");
            return null;
        }).when(audit).recordLoginLog(any());
    }

    @Test void auditFailureDoesNotReplaceSuccessfulLogin() {
        prepare(true);
        try (var bcrypt = mockStatic(BCrypt.class); var tokens = mockStatic(StpUtil.class)) {
            bcrypt.when(() -> BCrypt.checkpw(anyString(), anyString())).thenReturn(true);
            tokens.when(StpUtil::getSession).thenReturn(mock(SaSession.class));
            tokens.when(StpUtil::getTokenSession).thenAnswer(call -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                return mock(SaSession.class);
            });
            tokens.when(StpUtil::getTokenValue).thenReturn("issued-token");
            assertThat(service.login(request).accessToken()).isEqualTo("issued-token");
        }
        assertThat(events).containsExactly("begin", "commit", "audit");
        assertThat(recorded.status()).isEqualTo(1);
    }

    @Test void auditFailureDoesNotReplaceAuthenticationRejection() {
        prepare(true);
        try (var bcrypt = mockStatic(BCrypt.class)) {
            bcrypt.when(() -> BCrypt.checkpw(anyString(), anyString())).thenReturn(false);
            assertThatThrownBy(() -> service.login(request)).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AccessErrorCode.PASSWORD_INCORRECT.getCode()));
        }
        assertThat(events).containsExactly("begin", "rollback", "audit");
        assertThat(recorded.status()).isZero();
        assertThat(recorded.failReason()).isEqualTo("密码错误");
    }

    @Test void transactionCompletionFailureRecordsFixedFailureWithoutReplacingOriginalException() {
        prepare(true);
        var failure = new TransactionSystemException("private transaction detail");
        manager.commitFailure = failure;
        try (var bcrypt = mockStatic(BCrypt.class); var tokens = mockStatic(StpUtil.class)) {
            bcrypt.when(() -> BCrypt.checkpw(anyString(), anyString())).thenReturn(true);
            tokens.when(StpUtil::getSession).thenReturn(mock(SaSession.class));
            tokens.when(StpUtil::getTokenSession).thenAnswer(call -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                return mock(SaSession.class);
            });
            tokens.when(StpUtil::getTokenValue).thenReturn("issued-token");
            assertThatThrownBy(() -> service.login(request)).isSameAs(failure);
        }
        assertThat(events).containsExactly("begin", "commit", "audit");
        assertThat(recorded.status()).isZero();
        assertThat(recorded.failReason()).isEqualTo("登录事务未正常完成");
        verify(audit).recordLoginLog(any());
    }

    @Test void existingCallerTransactionIsRejectedBeforeAuthenticationOrAudit() {
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> service.login(request)))
            .isInstanceOf(IllegalStateException.class).hasMessage("login must own its transaction boundary");
        verifyNoInteractions(users, tenants, audit, redis);
    }

    /** 测试应用对事务完成与异常的处理，不模拟持久化；真实连接和行锁由 PG 用例承担。 */
    private static final class BoundaryManager extends AbstractPlatformTransactionManager {
        private final List<String> events;
        private RuntimeException commitFailure;
        BoundaryManager(List<String> events) { this.events = events; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { events.add("begin"); }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
            if (commitFailure != null) throw commitFailure;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
}
