package org.dromara.permission.event;

import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.model.permission.InheritMode;
import org.dromara.permission.model.permission.PermissionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AsyncPermissionWriteRefreshEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private TaskExecutor taskExecutor;

    @Test
    void publish_submitsAsyncRefreshEvent() {
        AsyncPermissionWriteRefreshEventPublisher publisher =
            new AsyncPermissionWriteRefreshEventPublisher(applicationEventPublisher, taskExecutor);
        PermissionContext context = new PermissionContext(1L, 2L, 3L, InheritMode.NONE, Map.of());
        context.setAction("grant");
        context.setRequestId("req-refresh");
        context.setChangeSource("ADMIN");
        PcPermissionVersion version = new PcPermissionVersion();
        version.setTenantId(1L);
        version.setVersionNo(8L);
        version.setTriggerEntityType("role_resource_permission");
        version.setTriggerEntityId(900L);

        publisher.publish(context, version);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        ArgumentCaptor<PermissionWriteRefreshEvent> eventCaptor =
            ArgumentCaptor.forClass(PermissionWriteRefreshEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(1L, eventCaptor.getValue().getTenantId());
        assertEquals(8L, eventCaptor.getValue().getVersionNo());
        assertEquals("grant", eventCaptor.getValue().getAction());
        assertEquals("req-refresh", eventCaptor.getValue().getRequestId());
        assertEquals("ADMIN", eventCaptor.getValue().getChangeSource());
    }

    @Test
    void publish_withActiveTransaction_dispatchesOnlyAfterCommit() {
        AsyncPermissionWriteRefreshEventPublisher publisher =
            new AsyncPermissionWriteRefreshEventPublisher(applicationEventPublisher, taskExecutor);
        PermissionContext context = new PermissionContext(1L, 2L, 3L, InheritMode.NONE, Map.of());
        context.setAction("grant");
        PcPermissionVersion version = new PcPermissionVersion();
        version.setTenantId(1L);
        version.setVersionNo(8L);
        version.setTriggerEntityType("role_resource_permission");
        version.setTriggerEntityId(900L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish(context, version);

            verify(taskExecutor, never()).execute(org.mockito.ArgumentMatchers.any());
            assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty());

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(taskExecutor).execute(org.mockito.ArgumentMatchers.any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publish_nullVersion_skipsDispatch() {
        AsyncPermissionWriteRefreshEventPublisher publisher =
            new AsyncPermissionWriteRefreshEventPublisher(applicationEventPublisher, taskExecutor);

        publisher.publish(new PermissionContext(1L, 2L, 3L, InheritMode.NONE, Map.of()), null);

        verify(taskExecutor, never()).execute(org.mockito.ArgumentMatchers.any());
        verify(applicationEventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
