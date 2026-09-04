package cn.ac.fage.accessmesh.access.infrastructure;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 树结构写路径分布式锁（T-PERM-044：四棵树 move 并发成环窗口收口）。
 * <p>
 * 角色/组织/菜单/资源实体四棵树的「查子孙 → 校验不成环 → 改 parent」为 check-then-act，
 * 两个写请求同瞬交叉移动同一子树时双双通过校验、双双落库形成 parent 环。环落库后
 * 子孙/祖先递归 CTE 与内存祖先链遍历不收敛（连接挂死 / JVM 死循环），本锁消除窗口本身：
 * 同一 (树, 租户) 的 parent 写路径在同一把锁上串行，后进锁者校验时能看到先进锁者已提交的
 * parent 关系，环无法落库。
 * </p>
 * <p>
 * 实现选型（2026-09-04 定案变更：原 advisory lock 方案改为 Redisson）：{@code RLock.lock()}
 * 不带 leaseTime，由 watchdog 自动续期（默认 30s、每 10s 续），防持锁进程崩溃后死锁；
 * <b>释放挂 {@code TransactionSynchronization.afterCompletion}</b>——事务 commit/rollback 后
 * 同线程回调 unlock，堵死「解锁先于提交」缝隙（unlock 若写在事务方法体内，与 AOP 代理的
 * 实际提交之间存在空隙，后进锁者校验读不到未提交数据，窗口复现）。unlock 失败仅告警，
 * 由 watchdog 停止后的 lease TTL 兜底释放。
 * </p>
 * <p>
 * 已知边界（定案接受）：①watchdog 依赖与 Redis 的连接——网络分区/JVM 长暂停超 30s 无续期
 * 时锁被逐出而事务仍在跑，窗口复现（概率极低）；②Redis 不可用时取锁失败即写入口失败
 * （fail-closed；跳锁等于窗口大开，不可选）。正确性依赖 READ_COMMITTED（PG 语句级快照，
 * 拿到锁后的校验查询可见他人已提交数据）；若改 REPEATABLE_READ 需把锁提到事务外层。
 * </p>
 * <p>
 * 锁 key：{@code accessmesh:tree-write-lock:{treeKey}:{tenantId}}（Redisson 可重入锁，
 * 每事务对同一 (树, 租户) 至多调用一次——当前 5 个写入口均满足；多次调用会被重入计数
 * 正确处理但 afterCompletion 只解一层，勿在嵌套调用中依赖重入）。
 * </p>
 */
@Component
public class TreeWriteLockSupport {

    private static final Logger log = LoggerFactory.getLogger(TreeWriteLockSupport.class);

    private static final String LOCK_KEY_PREFIX = "accessmesh:tree-write-lock:";

    /** 树标识（锁 key 分段；显式编号，枚举顺序调整不改变 key） */
    public enum TreeLockTarget {
        ABSTRACT_ROLE(1),
        SYS_ORG(2),
        SYS_MENU(3),
        RESOURCE_ENTITY(4);

        final int key;

        TreeLockTarget(int key) {
            this.key = key;
        }

        /** 锁 key 的树段（供锁行为探测/诊断复用同一 key 空间） */
        public int key() {
            return key;
        }
    }

    private final RedissonClient redissonClient;

    public TreeWriteLockSupport(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 对指定树获取本租户的写锁（阻塞等待，watchdog 自动续期），须在持有该树 parent 写入的
     * 事务内、任何树结构校验查询之前调用；锁在事务 commit/rollback 后经 afterCompletion 释放。
     * <p>
     * 必须在事务内调用：事务外调用时没有事务边界承载释放时机（本方法 fail-fast 拒绝），
     * 锁只能靠 lease TTL 兜底过期、保护范围与业务写入脱节。
     * </p>
     */
    public void lockTreeWrites(Long tenantId, TreeLockTarget target) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "lockTreeWrites 必须在事务内调用（释放依赖事务边界的 afterCompletion 回调）: " + target);
        }
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + target.key + ":" + tenantId);
        lock.lock();
        // 拿锁成功后注册释放回调；注册本身失败（极端）时立即解锁回滚语义，
        // 不让无释放时机的锁悬挂到 TTL
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    try {
                        lock.unlock();
                    } catch (Exception e) {
                        // 释放失败仅告警：持锁线程已结束、watchdog 随之停止，lease TTL 兜底过期
                        log.warn("tree write lock release failed (lease TTL will reclaim): key={}, status={}",
                            lock.getName(), status, e);
                    }
                }
            });
        } catch (RuntimeException e) {
            try {
                lock.unlock();
            } catch (Exception suppressed) {
                log.warn("tree write lock rollback unlock failed: key={}", lock.getName(), suppressed);
            }
            throw e;
        }
    }
}
