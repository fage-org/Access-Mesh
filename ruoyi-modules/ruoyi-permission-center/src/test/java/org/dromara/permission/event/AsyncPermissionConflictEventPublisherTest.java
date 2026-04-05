package org.dromara.permission.event;

import org.dromara.permission.model.permission.ConflictDetail;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AsyncPermissionConflictEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private TaskExecutor taskExecutor;

    @Test
    void publish_submitsAsyncEvent() {
        AsyncPermissionConflictEventPublisher publisher =
            new AsyncPermissionConflictEventPublisher(applicationEventPublisher, taskExecutor);
        PermissionContext context = new PermissionContext(1L, 2L, 3L, InheritMode.NONE, Map.of());
        context.setAction("check");
        context.setRequestId("req-1");
        ConflictDetail detail = new ConflictDetail();
        detail.setConflictRuleId(9L);

        publisher.publish(context, List.of(detail));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        ArgumentCaptor<PermissionConflictDetectedEvent> eventCaptor =
            ArgumentCaptor.forClass(PermissionConflictDetectedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(1L, eventCaptor.getValue().getTenantId());
        assertEquals(2L, eventCaptor.getValue().getUserId());
        assertEquals("check", eventCaptor.getValue().getAction());
        assertEquals("req-1", eventCaptor.getValue().getRequestId());
        assertEquals(1, eventCaptor.getValue().getConflicts().size());
        assertEquals(9L, eventCaptor.getValue().getConflicts().get(0).getConflictRuleId());
    }

    @Test
    void publish_emptyConflicts_skipsDispatch() {
        AsyncPermissionConflictEventPublisher publisher =
            new AsyncPermissionConflictEventPublisher(applicationEventPublisher, taskExecutor);

        publisher.publish(new PermissionContext(1L, 2L, 3L, InheritMode.NONE, Map.of()), List.of());

        verify(taskExecutor, never()).execute(org.mockito.ArgumentMatchers.any());
        verify(applicationEventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
        assertTrue(true);
    }
}
