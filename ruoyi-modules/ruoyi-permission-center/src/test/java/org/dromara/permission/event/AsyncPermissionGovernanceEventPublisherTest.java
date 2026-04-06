package org.dromara.permission.event;

import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.dto.DependencyCheckReq;
import org.dromara.permission.domain.vo.ConflictViolationVo;
import org.dromara.permission.model.permission.DependencyCheckResult;
import org.dromara.permission.model.permission.DependencyGap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AsyncPermissionGovernanceEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private TaskExecutor taskExecutor;

    @Test
    void publishConflictDetected_dispatchesScanAndAlertEvents() {
        AsyncPermissionGovernanceEventPublisher publisher =
            new AsyncPermissionGovernanceEventPublisher(applicationEventPublisher, taskExecutor);
        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(1L);
        req.setBizDomainId(2L);
        req.setRequestId("req-1");
        ConflictViolationVo violation = new ConflictViolationVo();
        violation.setResourceEntityId(99L);

        publisher.publishConflictDetected(req, List.of(violation), 9L);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        ArgumentCaptor<PermissionConflictScanDetectedEvent> eventCaptor =
            ArgumentCaptor.forClass(PermissionConflictScanDetectedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        verify(applicationEventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(PermissionGovernanceAuditAlertEvent.class));
        assertEquals(9L, eventCaptor.getValue().getTotalViolations());
    }

    @Test
    void publishDependencyBroken_emptyGap_skipsDispatch() {
        AsyncPermissionGovernanceEventPublisher publisher =
            new AsyncPermissionGovernanceEventPublisher(applicationEventPublisher, taskExecutor);
        DependencyCheckReq req = new DependencyCheckReq();
        req.setTenantId(1L);

        publisher.publishDependencyBroken(req, DependencyCheckResult.ok());

        verify(taskExecutor, never()).execute(org.mockito.ArgumentMatchers.any());
        verify(applicationEventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
        assertTrue(true);
    }
}
