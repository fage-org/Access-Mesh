package cn.ac.fage.accessmesh.common.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 同步 Spring 事务的 Redis 锁获取与释放责任。
 * <p>在决定业务写入的首次读取前获取，持有至事务 afterCompletion；业务键和竞争错误留给调用方。
 * 同线程同事务可重入，不用于跨线程释放或独立嵌套事务中的同 key 重入。
 * 不提供固定租约、自动重试、业务锁序或数据库读取缓存清理。
 */
public final class RedisTransactionLocks {
    private static final Logger log = LoggerFactory.getLogger(RedisTransactionLocks.class);

    private RedisTransactionLocks() {}

    /** 在当前同步事务内等待获取；实际提交或回滚后释放。 */
    public static void lock(RedissonClient redis, String key) {
        acquire(redis, key, true);
    }

    /** 在当前同步事务内立即尝试；竞争失败返回 false，技术异常继续上抛。 */
    public static boolean tryLock(RedissonClient redis, String key) {
        return acquire(redis, key, false);
    }

    private static boolean acquire(RedissonClient redis, String key, boolean waitForLock) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Redis lock requires a synchronized transaction");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Transaction lock acquisition interrupted");
        }
        RLock lock = redis.getLock(key);
        boolean acquired;
        // Redisson 的同步 get 在中断时取消外层 Future，内部命令仍可能成功并续期。
        // join 不因线程中断丢弃结果；结果确定后先解除已取得的锁，再按中断失败退出。
        if (waitForLock) {
            lock.lockAsync().toCompletableFuture().join();
            acquired = true;
        } else {
            acquired = lock.tryLockAsync().toCompletableFuture().join();
        }
        if (Thread.currentThread().isInterrupted()) {
            if (acquired) release(lock);
            throw new IllegalStateException("Transaction lock acquisition interrupted");
        }
        if (!acquired) return false;
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
        return true;
    }

    private static void release(RLock lock) {
        try {
            // 在原持锁线程完成释放，不因已设置的中断标记取消解锁结果的等待。
            lock.unlockAsync().toCompletableFuture().join();
        } catch (RuntimeException exception) {
            // 不覆盖业务异常或已确定的提交结果；Redisson 的失败完成处理负责取消续期。
            log.warn("Transaction lock release failed: key={}", lock.getName(), exception);
        }
    }
}
