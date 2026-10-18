package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.util.BusinessKeyUtil;
import cn.ac.fage.accessmesh.common.lock.RedisTransactionLocks;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/** 非等待重置锁：只保护当前事务，提交后的密码交付不在锁范围内。 */
@Component
public class PasswordResetLockSupport {
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
        if (!RedisTransactionLocks.tryLock(redisson, key)) {
            throw new BizException(AccessErrorCode.PASSWORD_RESET_IN_PROGRESS.getCode(),
                AccessErrorCode.PASSWORD_RESET_IN_PROGRESS.getMessage());
        }
    }
}
