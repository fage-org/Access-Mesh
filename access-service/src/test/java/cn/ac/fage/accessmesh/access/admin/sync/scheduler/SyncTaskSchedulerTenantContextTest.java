package cn.ac.fage.accessmesh.access.admin.sync.scheduler;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.access.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.access.admin.sync.handler.SyncTaskExecutionResult;
import cn.ac.fage.accessmesh.access.admin.sync.handler.SyncTaskHandler;
import cn.ac.fage.accessmesh.access.admin.sync.handler.SyncTaskHandlerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link SyncTaskScheduler#processOne(SysSyncTask)} 在 {@code @Scheduled} 触发的
 * 无 HTTP 上下文场景下，能按任务自身的 {@code tenantId} 设置
 * {@link TenantContextHolder}，以便下游 Feign 拦截器注入 {@code X-Tenant-Id}。
 */
@ExtendWith(MockitoExtension.class)
class SyncTaskSchedulerTenantContextTest {

    @Mock
    private SyncTaskDomainService domainService;

    @Mock
    private SyncTaskHandlerRegistry registry;

    private SyncTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        SyncSchedulerProperties properties = new SyncSchedulerProperties();
        scheduler = new SyncTaskScheduler(domainService, registry, properties);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private SysSyncTask task(long id, long tenantId) {
        SysSyncTask t = new SysSyncTask();
        t.setId(id);
        t.setTenantId(tenantId);
        t.setSyncAction("PERM_ABSTRACT_USER_SYNC");
        t.setRetryCount(0);
        t.setMaxRetries(5);
        return t;
    }

    private SyncTaskHandler captureHandler(AtomicReference<Long> seenTenant) {
        return new SyncTaskHandler() {
            @Override
            public String supportedAction() {
                return "PERM_ABSTRACT_USER_SYNC";
            }

            @Override
            public SyncTaskExecutionResult execute(SysSyncTask task) {
                seenTenant.set(TenantContextHolder.getTenantId());
                return SyncTaskExecutionResult.success();
            }
        };
    }

    @Test
    void processOne_setsTenantContextDuringHandlerExecution() {
        SysSyncTask t = task(1L, 42L);
        AtomicReference<Long> seenTenant = new AtomicReference<>();
        when(registry.resolve(anyString())).thenReturn(captureHandler(seenTenant));

        scheduler.processOne(t);

        assertThat(seenTenant.get()).isEqualTo(42L);
    }

    @Test
    void processOne_clearsTenantContextOnExit_whenNoOuterValue() {
        SysSyncTask t = task(2L, 100L);
        AtomicReference<Long> seenTenant = new AtomicReference<>();
        when(registry.resolve(anyString())).thenReturn(captureHandler(seenTenant));

        // 进入前 ThreadLocal 为空（@Scheduled 触发场景）
        assertThat(TenantContextHolder.getTenantId()).isNull();
        scheduler.processOne(t);
        // 退出后必须被清理，避免线程池租户串味
        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(seenTenant.get()).isEqualTo(100L);
    }

    @Test
    void processOne_restoresPreviousTenant_whenNested() {
        // 模拟外层已设置 tenant=10（嵌套调度场景）
        TenantContextHolder.setTenantId(10L);

        SysSyncTask t = task(3L, 20L);
        AtomicReference<Long> seenTenant = new AtomicReference<>();
        when(registry.resolve(anyString())).thenReturn(captureHandler(seenTenant));

        scheduler.processOne(t);

        // 内层 handler 看到 tenant=20
        assertThat(seenTenant.get()).isEqualTo(20L);
        // 退出后恢复为外层值 10，而非清空
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(10L);
    }

    @Test
    void processOne_clearsTenantContext_whenHandlerThrows() {
        SysSyncTask t = task(5L, 88L);
        AtomicReference<Long> seenInsideHandler = new AtomicReference<>();
        SyncTaskHandler handler = new SyncTaskHandler() {
            @Override
            public String supportedAction() {
                return "PERM_ABSTRACT_USER_SYNC";
            }

            @Override
            public SyncTaskExecutionResult execute(SysSyncTask task) {
                seenInsideHandler.set(TenantContextHolder.getTenantId());
                throw new RuntimeException("boom");
            }
        };
        when(registry.resolve(anyString())).thenReturn(handler);

        // 调用前 ThreadLocal 为空
        assertThat(TenantContextHolder.getTenantId()).isNull();
        scheduler.processOne(t);
        // handler 抛异常但 finally 依然清空
        assertThat(TenantContextHolder.getTenantId()).isNull();
        // handler 内部确实读到了任务的 tenantId
        assertThat(seenInsideHandler.get()).isEqualTo(88L);
    }
}
