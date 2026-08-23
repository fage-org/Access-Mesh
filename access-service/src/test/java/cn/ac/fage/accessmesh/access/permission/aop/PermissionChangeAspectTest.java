package cn.ac.fage.accessmesh.access.permission.aop;

import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.cache.PermInvalidationPublisher;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PermissionChangeAspect 单元测试
 * <p>
 * 重点回归：成功返回但未登记变更（no-op 路径）时必须清理 ThreadLocal，
 * 避免线程池线程残留导致后续请求 bindIfAbsent 误判非 owner、真实变更 flush 被跳过（P1）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionChangeAspectTest {

    @Mock private SubjectDomainService subjectDomainService;
    @Mock private CacheService cacheService;
    @Mock private PermInvalidationPublisher publisher;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private PermissionChange pc;

    private PermissionChangeAspect aspect;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        aspect = new PermissionChangeAspect(subjectDomainService, cacheService, publisher);
    }

    @AfterEach
    void tearDown() {
        // 防御性：测试间不泄漏 ThreadLocal
        PermissionChangeContext.clear();
    }

    /**
     * P1 回归：proceed 成功但未 mark（no-op 路径，如空入参/已删/无受影响项）时，
     * 必须清理 ThreadLocal，否则线程池线程残留导致下次 bindIfAbsent 返回 false。
     */
    @Test
    void shouldClearThreadLocalWhenSucceedsButNoChangeRegistered() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");

        try (MockedStatic<TransactionSynchronizationManager> txMock =
                 org.mockito.Mockito.mockStatic(TransactionSynchronizationManager.class)) {
            txMock.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(false);

            Object result = aspect.around(joinPoint, pc);
        }

        // ThreadLocal 必须被回收
        assertNull(PermissionChangeContext.snapshot());
        // 下次 bindIfAbsent 应再次成为 owner（证明无残留）
        assertTrue(PermissionChangeContext.bindIfAbsent());
        PermissionChangeContext.clear();
        // 空累积不应触发任何失效或广播
        verify(subjectDomainService, never()).invalidateRoleCacheByRoles(anyLong(), any());
        verify(publisher, never()).publish(anyLong(), any(), any(), any());
    }

    /**
     * T-ACCESS-017 特征测试（链路 5）：事务同步激活时，flush 不在方法返回前执行，
     * 而是注册 afterCommit 回调——提交后才失效缓存与广播，afterCompletion 清理 ThreadLocal。
     * 这是授权写路径（@Transactional + @PermissionChange）的主路径语义。
     */
    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void shouldDeferFlushToAfterCommitWhenTransactionActive() throws Throwable {
        when(joinPoint.proceed()).thenAnswer(inv -> {
            PermissionChangeContext.markRoles(1L, 200L);
            return "ok";
        });

        org.mockito.ArgumentCaptor<TransactionSynchronization> syncCaptor =
            org.mockito.ArgumentCaptor.forClass(TransactionSynchronization.class);

        try (MockedStatic<TransactionSynchronizationManager> txMock =
                 org.mockito.Mockito.mockStatic(TransactionSynchronizationManager.class)) {
            txMock.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);

            aspect.around(joinPoint, pc);

            // 方法返回 = 事务提交前：flush 不得执行
            verify(subjectDomainService, never()).invalidateRoleCacheByRoles(anyLong(), any());
            verify(publisher, never()).publish(anyLong(), any(), any(), any());
            // ThreadLocal 仍持有累积器（等 afterCompletion 清理）
            org.junit.jupiter.api.Assertions.assertNotNull(PermissionChangeContext.snapshot());

            txMock.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
        }

        TransactionSynchronization sync = syncCaptor.getValue();

        // 提交后（afterCommit）：失效 + 广播执行
        sync.afterCommit();
        verify(subjectDomainService).invalidateRoleCacheByRoles(eq(1L), eq(Set.of(200L)));
        verify(cacheService).evictBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), eq(Set.of(200L)));
        verify(cacheService).evictAll(eq(PermCacheCatalog.ORG_VISIBILITY), eq(1L));
        verify(publisher).publish(eq(1L), eq(Set.of(200L)), any(), any());

        // 完成后（afterCompletion）：ThreadLocal 清理
        sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertNull(PermissionChangeContext.snapshot());
    }

    /**
     * 无事务下 mark 登记后，afterCommit 等价的立即 flush + clear：evict + publish 应被调用。
     */
    @Test
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void shouldFlushAndClearWhenChangeRegisteredOutsideTransaction() throws Throwable {
        // 模拟目标方法体内调用 markRoles（通过 ProceedingJoinPoint.proceed 的 doAnswer）
        when(joinPoint.proceed()).thenAnswer(inv -> {
            PermissionChangeContext.markRoles(1L, 200L);
            return "ok";
        });

        try (MockedStatic<TransactionSynchronizationManager> txMock =
                 org.mockito.Mockito.mockStatic(TransactionSynchronizationManager.class)) {
            txMock.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(false);

            aspect.around(joinPoint, pc);
        }

        // flush 执行：批量失效角色缓存 + 失效 ROLE_PERM_SNAPSHOT + 租户级清除 ORG_VISIBILITY + 广播
        verify(subjectDomainService).invalidateRoleCacheByRoles(eq(1L), eq(Set.of(200L)));
        verify(cacheService).evictBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), eq(Set.of(200L)));
        verify(cacheService).evictAll(eq(PermCacheCatalog.ORG_VISIBILITY), eq(1L));
        verify(publisher).publish(eq(1L), eq(Set.of(200L)), any(), any());
        // clear 执行
        assertNull(PermissionChangeContext.snapshot());
    }
}
