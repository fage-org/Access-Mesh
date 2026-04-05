package org.dromara.permission.event;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.model.permission.PermissionContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class AsyncPermissionWriteRefreshEventPublisher implements PermissionWriteRefreshEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Qualifier("permissionWriteRefreshEventExecutor")
    private final TaskExecutor taskExecutor;

    @Override
    public void publish(PermissionContext context, PcPermissionVersion version) {
        if (context == null || version == null) {
            return;
        }
        PermissionWriteRefreshEvent event = new PermissionWriteRefreshEvent(
            version.getTenantId(),
            version.getVersionNo(),
            version.getTriggerEntityType(),
            version.getTriggerEntityId(),
            context.getAction(),
            context.getRequestId(),
            context.getChangeSource()
        );
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(event);
                }
            });
            return;
        }
        dispatch(event);
    }

    private void dispatch(PermissionWriteRefreshEvent event) {
        taskExecutor.execute(() -> applicationEventPublisher.publishEvent(event));
    }
}
