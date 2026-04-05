package org.dromara.permission.event;

import lombok.Getter;
import org.dromara.permission.model.permission.ConflictDetail;

import java.util.List;

@Getter
public class PermissionConflictDetectedEvent {

    private final Long tenantId;
    private final Long userId;
    private final Long bizDomainId;
    private final String action;
    private final String requestId;
    private final List<ConflictDetail> conflicts;

    public PermissionConflictDetectedEvent(Long tenantId, Long userId, Long bizDomainId, String action,
                                           String requestId, List<ConflictDetail> conflicts) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.bizDomainId = bizDomainId;
        this.action = action;
        this.requestId = requestId;
        this.conflicts = conflicts;
    }
}
