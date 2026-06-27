package cn.ac.fage.accessmesh.permission.aop;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.cache.PermInvalidationPublisher;
import cn.ac.fage.accessmesh.permission.cache.PermissionChangeContext;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

/**
 * 权限变更切面
 * <p>
 * 拦截标注 {@link PermissionChange} 的 AppService 写方法，统一处理缓存失效与广播：
 * <ol>
 *   <li>入口绑定 {@link PermissionChangeContext}（仅最外层成为 owner）</li>
 *   <li>业务方法体执行（体内通过 {@code mark*} 登记影响范围）</li>
 *   <li>owner 在事务提交后（afterCommit）flush：evict 相关缓存 + 广播 {@link PermInvalidateEvent}</li>
 *   <li>afterCompletion 清理 ThreadLocal（异常回滚同样清理）</li>
 * </ol>
 * </p>
 * <p>
 * <b>铁律 P1-B 达标</b>：afterCommit 注册由本切面（框架侧）统一完成，业务方法体内不再手写
 * {@code TransactionSynchronizationManager}。
 * </p>
 */
@Aspect
@Component
public class PermissionChangeAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionChangeAspect.class);

    private final SubjectDomainService subjectDomainService;
    private final CacheService cacheService;
    private final PermInvalidationPublisher publisher;

    public PermissionChangeAspect(SubjectDomainService subjectDomainService,
                                  CacheService cacheService,
                                  PermInvalidationPublisher publisher) {
        this.subjectDomainService = subjectDomainService;
        this.cacheService = cacheService;
        this.publisher = publisher;
    }

    @Around("@annotation(pc)")
    public Object around(ProceedingJoinPoint joinPoint, PermissionChange pc) throws Throwable {
        boolean owner = PermissionChangeContext.bindIfAbsent();
        try {
            Object result = joinPoint.proceed();
            if (owner) {
                PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
                if (acc != null && !acc.isEmpty()) {
                    scheduleFlush(acc);
                } else {
                    // 成功但未登记变更（no-op 路径：空入参/已删/无受影响项）：
                    // 立即清理 ThreadLocal，避免残留到线程池后续请求导致 bindIfAbsent 误判非 owner、
                    // 真实变更 mark 落入旧 context 且无 owner flush/clear（失效与广播被跳过）。
                    PermissionChangeContext.clear();
                }
            }
            return result;
        } catch (Throwable t) {
            if (owner) {
                // 回滚或非事务异常：清理 ThreadLocal，不 flush（数据已回滚，无需失效）
                PermissionChangeContext.clear();
            }
            throw t;
        }
    }

    /**
     * 安排 flush：事务激活时注册 afterCommit + afterCompletion；无事务时立即 flush + clear。
     */
    private void scheduleFlush(PermissionChangeContext.Accumulator acc) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    PermissionChangeAspect.this.flush(acc);
                }

                @Override
                public void afterCompletion(int status) {
                    PermissionChangeContext.clear();
                }
            });
        } else {
            // 无事务上下文（不应发生在 @Transactional 写方法，但防御性处理）
            flush(acc);
            PermissionChangeContext.clear();
        }
    }

    /**
     * 执行缓存失效 + 广播。afterCommit 已是提交后，读已提交数据；evict 即时失效（非再注册 sync）。
     */
    private void flush(PermissionChangeContext.Accumulator acc) {
        Long tenantId = acc.tenantId();
        if (tenantId == null) {
            log.debug("PermissionChange flush skipped: tenantId not set in accumulator");
            return;
        }
        Set<Long> roleIds = acc.roleIds();
        Set<Long> userIds = acc.userIds();
        Set<Long> conditionIds = acc.conditionIds();
        Set<Long> roleSnapshotIds = acc.roleSnapshotIds();
        Set<String> serviceCodes = acc.serviceCodes();

        try {
            // 1. 角色维度失效（反查受影响用户，失效 EFFECTIVE_ROLES）—— T-PERM-018 P2 批量化，消除按角色循环 N+1
            if (!roleIds.isEmpty()) {
                subjectDomainService.invalidateRoleCacheByRoles(tenantId, roleIds);
            }
            // 角色权限变更（grant/revoke/资源删除）→ 失效 ROLE_PERM_SNAPSHOT（roleId 级精确）
            if (!roleIds.isEmpty()) {
                cacheService.evictBatch(PermCacheCatalog.ROLE_PERM_SNAPSHOT, tenantId, roleIds);
            }
            // 2. 用户维度失效
            if (!userIds.isEmpty()) {
                subjectDomainService.invalidateRoleCacheBatch(tenantId, userIds);
            }
            // 3. 条件规则失效（即时 evictBatch，消除原 evictConditionCache 的双重注册）
            if (!conditionIds.isEmpty()) {
                cacheService.evictBatch(PermCacheCatalog.CONDITION_RULES, tenantId, conditionIds);
            }
            // 4. 角色权限快照直清（角色删除场景）
            if (!roleSnapshotIds.isEmpty()) {
                cacheService.evictBatch(PermCacheCatalog.ROLE_PERM_SNAPSHOT, tenantId, roleSnapshotIds);
            }
            // 5. 广播失效事件（含 serviceCodes：API mapping/资源/sync 变更触发 Gateway 清本地快照，T-PERM-006 实现）
            publisher.publish(tenantId, roleIds, userIds, serviceCodes);
        } catch (Exception e) {
            // flush 失败不抛异常（事务已提交）；靠 TTL 兜底最终一致
            log.error("PermissionChange flush failed (tenantId={}): {}", tenantId, e.getMessage(), e);
        }
    }
}
