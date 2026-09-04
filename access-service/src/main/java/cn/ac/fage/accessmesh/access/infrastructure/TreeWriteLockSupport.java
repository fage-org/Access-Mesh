package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.infrastructure.mapper.TreeWriteLockMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 树结构写路径 advisory lock（T-PERM-044：四棵树 move 并发成环窗口收口）。
 * <p>
 * 角色/组织/菜单/资源实体四棵树的「查子孙 → 校验不成环 → 改 parent」为 check-then-act，
 * 两个写请求同瞬交叉移动同一子树时双双通过校验、双双落库形成 parent 环。环落库后
 * 子孙/祖先递归 CTE 与内存祖先链遍历不收敛（连接挂死 / JVM 死循环），本锁消除窗口本身：
 * 同一 (树, 租户) 的 parent 写路径在同一把事务级 advisory lock 上串行，后进锁者校验时
 * 能看到先进锁者已提交的 parent 关系，环无法落库。
 * </p>
 * <p>
 * 选型依据（用户决策 2026-09-04）：{@code pg_advisory_xact_lock} 由 PostgreSQL 在事务
 * commit/rollback 时<b>原子释放</b>，不存在分布式锁的三类缝隙——解锁先于提交（显式 unlock
 * 与声明式事务提交的时序缝隙）、锁超时被逐出而事务仍在跑（需看门狗续期）、锁服务故障与
 * 数据可用性分裂。锁与数据同库同生命周期，正确性由数据库保证而非代码时序。代价：持锁
 * 事务（如 full-sync 批量）期间并发 move 在连接上排队等待，管理操作低频可接受。
 * </p>
 * <p>
 * 键空间：双 int 键 {@code pg_advisory_xact_lock(treeKey, tenantKey)}，64 位组合键 =
 * {@code (treeKey << 32) | tenantKey}。treeKey 从 1 起的高位区段与既有单 bigint 键
 * {@code TaskLeaseTakeoverScheduler#ADVISORY_LOCK_KEY}（8_009_001 &lt; 2^32，低位区段）
 * 不重叠。
 * </p>
 */
@Component
public class TreeWriteLockSupport {

    /** 树标识（advisory lock 双 int 键高位段；显式编号，枚举顺序调整不改变键值） */
    public enum TreeLockTarget {
        ABSTRACT_ROLE(1),
        SYS_ORG(2),
        SYS_MENU(3),
        RESOURCE_ENTITY(4);

        final int key;

        TreeLockTarget(int key) {
            this.key = key;
        }

        /** advisory lock 双 int 键的树段（供锁行为探测/诊断复用同一键空间） */
        public int key() {
            return key;
        }
    }

    private final TreeWriteLockMapper treeWriteLockMapper;

    public TreeWriteLockSupport(TreeWriteLockMapper treeWriteLockMapper) {
        this.treeWriteLockMapper = treeWriteLockMapper;
    }

    /**
     * 对指定树获取本租户的写锁（阻塞等待），须在持有该树 parent 写入的事务内、
     * 任何树结构校验查询之前调用。
     * <p>
     * 必须在事务内调用：锁语句经 MyBatis 与业务 SQL 共享事务绑定连接，随事务结束由
     * 数据库释放。事务外调用时锁随语句所在隐式事务立即释放（等于没锁），此处 fail-fast
     * 拦截误用。
     * </p>
     */
    public void lockTreeWrites(Long tenantId, TreeLockTarget target) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "lockTreeWrites 必须在事务内调用（事务外 advisory xact lock 随语句立即释放）: " + target);
        }
        // 返回值恒为 1（count 包装丢弃锁函数 void 结果列），调用方忽略
        treeWriteLockMapper.lockTreeWrites(target.key(), Math.toIntExact(tenantId));
    }
}
