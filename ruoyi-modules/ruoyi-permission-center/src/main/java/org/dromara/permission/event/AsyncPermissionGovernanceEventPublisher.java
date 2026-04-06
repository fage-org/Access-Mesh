package org.dromara.permission.event;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.dto.DependencyCheckReq;
import org.dromara.permission.domain.vo.ConflictViolationVo;
import org.dromara.permission.model.permission.DependencyCheckResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AsyncPermissionGovernanceEventPublisher implements PermissionGovernanceEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Qualifier("permissionGovernanceEventExecutor")
    private final TaskExecutor taskExecutor;

    @Override
    public void publishConflictDetected(ConflictDetectReq request, List<ConflictViolationVo> violations, long totalViolations) {
        if (request == null || violations == null || violations.isEmpty()) {
            return;
        }
        PermissionConflictScanDetectedEvent event = new PermissionConflictScanDetectedEvent(
            request.getTenantId(),
            request.getBizDomainId(),
            request.getAbstractUserId(),
            request.getAbstractRoleId(),
            request.getResourceEntityId(),
            request.getRequestId(),
            totalViolations,
            new ArrayList<>(violations)
        );
        PermissionGovernanceAuditAlertEvent alertEvent = new PermissionGovernanceAuditAlertEvent(
            request.getTenantId(),
            "CONFLICT_DETECTED",
            request.getRequestId(),
            "Detected " + totalViolations + " permission conflicts",
            (int) totalViolations
        );
        dispatch(event, alertEvent);
    }

    @Override
    public void publishDependencyBroken(DependencyCheckReq request, DependencyCheckResult result) {
        if (request == null || result == null || result.isSatisfied() || result.getGaps() == null || result.getGaps().isEmpty()) {
            return;
        }
        PermissionDependencyBrokenEvent event = new PermissionDependencyBrokenEvent(
            request.getTenantId(),
            request.getBizDomainId(),
            request.getAbstractUserId(),
            request.getAbstractRoleId(),
            request.getResourceEntityId(),
            request.getOperationPermissionId(),
            request.getRequestId(),
            new ArrayList<>(result.getGaps())
        );
        PermissionGovernanceAuditAlertEvent alertEvent = new PermissionGovernanceAuditAlertEvent(
            request.getTenantId(),
            "DEPENDENCY_BROKEN",
            request.getRequestId(),
            "Detected " + result.getGaps().size() + " dependency gaps",
            result.getGaps().size()
        );
        dispatch(event, alertEvent);
    }

    private void dispatch(Object event, PermissionGovernanceAuditAlertEvent alertEvent) {
        taskExecutor.execute(() -> {
            applicationEventPublisher.publishEvent(event);
            applicationEventPublisher.publishEvent(alertEvent);
        });
    }
}
