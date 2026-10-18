package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.util.BusinessKeyUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 非等待重置锁：只保护当前事务，提交后的密码交付不在锁范围内。 */
@Component
public class PasswordResetLockSupport {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetLockSupport.class);
    private final RedissonClient redisson;

    public PasswordResetLockSupport(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public void lockTenantUser(Long tenantId, long userId) {
        if (tenantId == null) throw new IllegalArgumentException("Tenant identity is required");
        acquire(BusinessKeyUtil.passwordResetLockKey(tenantId, userId));
    }

    public void lockPlatformAccount(long accountId) {
        acquire(BusinessKeyUtil.passwordResetLockKey(null, accountId));
    }

    private void acquire(String key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Password reset lock requires a synchronized transaction");
        }
        RLock lock = redisson.getLock(key);
        // 不指定 leaseTime，沿用 Redisson watchdog；不等待前一请求提交后再执行一次重置。
        if (!lock.tryLock()) {
            throw new BizException(AccessErrorCode.PASSWORD_RESET_IN_PROGRESS.getCode(),
                AccessErrorCode.PASSWORD_RESET_IN_PROGRESS.getMessage());
        }
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    release(lock);
                }
            });
        } catch (RuntimeException exception) {
            release(lock);
            throw exception;
        }
    }

    private void release(RLock lock) {
        try {
            lock.unlock();
        } catch (RuntimeException exception) {
            // 提交结果已确定；解锁失败不能把成功改成失败并诱发自动重试。
            log.warn("Password reset lock release failed: key={}", lock.getName(), exception);
        }
    }
}
