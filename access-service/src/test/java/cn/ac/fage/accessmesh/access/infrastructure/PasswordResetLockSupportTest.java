package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.util.BusinessKeyUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetLockSupportTest {
    private final RedissonClient redis = mock(RedissonClient.class);
    private final PasswordResetLockSupport support = new PasswordResetLockSupport(redis);

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    private void transaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @Test
    void refusesAcquisitionWithoutTransactionOrSynchronization() {
        assertThatThrownBy(() -> support.lockTenantUser(1L, 77)).isInstanceOf(IllegalStateException.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> support.lockTenantUser(1L, 77)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(redis);
    }

    @Test
    void busyLockRejectsImmediatelyAndDoesNotUnlockAnotherOwner() {
        transaction();
        RLock lock = mock(RLock.class);
        when(redis.getLock(BusinessKeyUtil.passwordResetLockKey(1L, 77))).thenReturn(lock);
        when(lock.tryLockAsync()).thenReturn(new org.redisson.misc.CompletableFutureWrapper<>(false));
        assertThatThrownBy(() -> support.lockTenantUser(1L, 77))
            .isInstanceOf(BizException.class).extracting("errorCode").isEqualTo(10015);
        verify(lock).tryLockAsync();
        verify(lock, never()).unlockAsync();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void releasesOnlyAfterTransactionCompletion(int status) {
        transaction();
        RLock lock = mock(RLock.class);
        when(redis.getLock(BusinessKeyUtil.passwordResetLockKey(1L, 77))).thenReturn(lock);
        when(lock.tryLockAsync()).thenReturn(new org.redisson.misc.CompletableFutureWrapper<>(true));
        when(lock.unlockAsync()).thenReturn(org.redisson.misc.CompletableFutureWrapper.completedNull());
        support.lockTenantUser(1L, 77);
        verify(lock, never()).unlockAsync();
        var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.beforeCompletion();
        verify(lock, never()).unlockAsync();
        synchronization.afterCompletion(status);
        verify(lock).unlockAsync();
    }

    @Test
    void redisFailureDoesNotFallBackToAnUnlockedReset() {
        transaction();
        when(redis.getLock(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        assertThatThrownBy(() -> support.lockPlatformAccount(77)).hasMessage("Redis unavailable");
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void alreadyInterruptedRequestDoesNotStartAnAcquisition() {
        transaction();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> support.lockTenantUser(1L, 77)).isInstanceOf(IllegalStateException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(redis);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void failedAsyncAcquisitionDoesNotUnlockOrRegisterACompletion() {
        transaction();
        RLock lock = mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock);
        when(lock.tryLockAsync()).thenReturn(new org.redisson.misc.CompletableFutureWrapper<>(
            new IllegalStateException("Redis unavailable")));
        assertThatThrownBy(() -> support.lockPlatformAccount(77)).hasRootCauseMessage("Redis unavailable");
        verify(lock, never()).unlockAsync();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void releaseFailureDoesNotRewriteCommittedOutcome() {
        transaction();
        RLock lock = mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock);
        when(lock.tryLockAsync()).thenReturn(new org.redisson.misc.CompletableFutureWrapper<>(true));
        when(lock.unlockAsync()).thenReturn(new org.redisson.misc.CompletableFutureWrapper<>(
            new IllegalStateException("Redis unavailable")));
        support.lockPlatformAccount(77);
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(0))
            .doesNotThrowAnyException();
    }
}
