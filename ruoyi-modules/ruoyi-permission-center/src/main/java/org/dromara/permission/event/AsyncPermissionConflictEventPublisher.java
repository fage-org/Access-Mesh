package org.dromara.permission.event;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.model.permission.ConflictDetail;
import org.dromara.permission.model.permission.PermissionContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AsyncPermissionConflictEventPublisher implements PermissionConflictEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Qualifier("permissionConflictEventExecutor")
    private final TaskExecutor taskExecutor;

    @Override
    public void publish(PermissionContext context, List<ConflictDetail> conflicts) {
        if (context == null || conflicts == null || conflicts.isEmpty()) {
            return;
        }
        PermissionConflictDetectedEvent event = new PermissionConflictDetectedEvent(
            context.getTenantId(),
            context.getUserId(),
            context.getBizDomainId(),
            context.getAction(),
            context.getRequestId(),
            new ArrayList<>(conflicts)
        );
        taskExecutor.execute(() -> applicationEventPublisher.publishEvent(event));
    }
}
