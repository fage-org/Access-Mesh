package cn.ac.fage.accessmesh.permission.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 权限变更影响范围累积器（ThreadLocal）
 * <p>
 * 由 {@link PermissionChangeAspect} 在 AppService 写方法入口绑定，业务方法体（AppService 编排层 /
 * DomainService）通过 {@code mark*} 登记受影响的角色 / 用户 / 条件 / 角色快照，AOP 在事务提交后
 * （afterCommit）统一 flush：evict 相关缓存 + 广播 {@link PermInvalidateEvent}。
 * </p>
 * <p>
 * <b>铁律 P1-B</b>：业务侧（DomainService / AppService 业务方法内）禁止手写
 * {@code TransactionSynchronizationManager}。afterCommit 注册由 {@link PermissionChangeAspect}
 * 框架侧统一完成；业务侧仅通过本类的 {@code mark*} 登记影响范围。
 * </p>
 * <p>
 * 语义同 {@code TenantContextHolder}：静态 ThreadLocal，mark 方法在未绑定时 no-op（防越界 mark 导致泄漏）。
 * 嵌套调用（@PermissionChange 方法调另一 @PermissionChange 方法）由 {@link #bindIfAbsent()} 保证仅最外层
 * owner 负责 flush + clear。
 * </p>
 */
public final class PermissionChangeContext {

    private static final Logger log = LoggerFactory.getLogger(PermissionChangeContext.class);

    private static final ThreadLocal<Accumulator> HOLDER = new ThreadLocal<>();

    private PermissionChangeContext() {
    }

    /**
     * 绑定累积器（若当前线程未绑定）。仅最外层调用成为 owner。
     *
     * @return true 表示本次调用是 owner（需在 afterCommit flush + afterCompletion clear）
     */
    public static boolean bindIfAbsent() {
        if (HOLDER.get() == null) {
            HOLDER.set(new Accumulator());
            return true;
        }
        return false;
    }

    /**
     * 读取累积快照（owner 在 flush 时调用）。非 owner 或未绑定时返回 null。
     */
    public static Accumulator snapshot() {
        return HOLDER.get();
    }

    /**
     * 清理 ThreadLocal（owner 在 afterCompletion / 异常 catch 时调用）。
     */
    public static void clear() {
        HOLDER.remove();
    }

    // ===== mark API（业务侧登记影响范围）=====

    /**
     * 登记受影响角色（角色权限授予/撤销/组角色变更 → 失效 EFFECTIVE_ROLES + 广播）。
     * <p>
     * 注意：roleId 维度失效会经 {@code SubjectDomainService.invalidateRoleCacheByRole} 反查受影响用户，
     * 因此 markRoles 与 markUsers 可叠加（角色删除场景同时影响角色快照与用户）。
     * </p>
     */
    public static void markRoles(Long tenantId, Set<Long> roleIds) {
        Accumulator acc = HOLDER.get();
        if (acc == null) {
            log.debug("markRoles called without bound context (tenantId={}, roleIds={}) — no-op", tenantId, roleIds);
            return;
        }
        acc.ensureTenant(tenantId);
        if (roleIds != null) {
            acc.roleIds.addAll(roleIds);
        }
    }

    public static void markRoles(Long tenantId, Long roleId) {
        Accumulator acc = HOLDER.get();
        if (acc == null) {
            log.debug("markRoles called without bound context (tenantId={}, roleId={}) — no-op", tenantId, roleId);
            return;
        }
        acc.ensureTenant(tenantId);
        if (roleId != null) {
            acc.roleIds.add(roleId);
        }
    }

    /**
     * 登记受影响用户（用户角色关系变更 → 失效 EFFECTIVE_ROLES + 广播）。
     */
    public static void markUsers(Long tenantId, Set<Long> userIds) {
        Accumulator acc = HOLDER.get();
        if (acc == null) {
            log.debug("markUsers called without bound context (tenantId={}, userIds={}) — no-op", tenantId, userIds);
            return;
        }
        acc.ensureTenant(tenantId);
        if (userIds != null) {
            acc.userIds.addAll(userIds);
        }
    }

    /**
     * 登记受影响条件（条件规则变更 → 失效 CONDITION_RULES）。
     */
    public static void markConditions(Long tenantId, Set<Long> conditionIds) {
        Accumulator acc = HOLDER.get();
        if (acc == null) {
            log.debug("markConditions called without bound context (tenantId={}, conditionIds={}) — no-op", tenantId, conditionIds);
            return;
        }
        acc.ensureTenant(tenantId);
        if (conditionIds != null) {
            acc.conditionIds.addAll(conditionIds);
        }
    }

    /**
     * 登记需直清角色权限快照的角色（角色删除 → 失效 ROLE_PERM_SNAPSHOT）。
     * <p>
     * 角色删除场景角色本身不再有效，无法经 invalidateRoleCacheByRole 反查，需直清快照。
     * </p>
     */
    public static void markRoleSnapshots(Long tenantId, Set<Long> roleIds) {
        Accumulator acc = HOLDER.get();
        if (acc == null) {
            log.debug("markRoleSnapshots called without bound context (tenantId={}, roleIds={}) — no-op", tenantId, roleIds);
            return;
        }
        acc.ensureTenant(tenantId);
        if (roleIds != null) {
            acc.roleSnapshotIds.addAll(roleIds);
        }
    }

    /**
     * 累积快照（不可变视图，供 AOP flush 读取）。
     */
    public static final class Accumulator {
        private Long tenantId;
        private final Set<Long> roleIds = new HashSet<>();
        private final Set<Long> userIds = new HashSet<>();
        private final Set<Long> conditionIds = new HashSet<>();
        private final Set<Long> roleSnapshotIds = new HashSet<>();

        void ensureTenant(Long tenantId) {
            if (this.tenantId == null) {
                this.tenantId = tenantId;
            }
        }

        public Long tenantId() {
            return tenantId;
        }

        public Set<Long> roleIds() {
            return Collections.unmodifiableSet(roleIds);
        }

        public Set<Long> userIds() {
            return Collections.unmodifiableSet(userIds);
        }

        public Set<Long> conditionIds() {
            return Collections.unmodifiableSet(conditionIds);
        }

        public Set<Long> roleSnapshotIds() {
            return Collections.unmodifiableSet(roleSnapshotIds);
        }

        public boolean isEmpty() {
            return roleIds.isEmpty() && userIds.isEmpty()
                && conditionIds.isEmpty() && roleSnapshotIds.isEmpty();
        }
    }
}
