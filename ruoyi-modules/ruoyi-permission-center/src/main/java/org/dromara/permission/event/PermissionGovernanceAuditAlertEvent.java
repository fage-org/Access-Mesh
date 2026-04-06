package org.dromara.permission.event;

import lombok.Getter;

@Getter
public class PermissionGovernanceAuditAlertEvent {

    private final Long tenantId;
    private final String eventType;
    private final String requestId;
    private final String summary;
    private final int detailCount;

    public PermissionGovernanceAuditAlertEvent(Long tenantId, String eventType, String requestId,
                                               String summary, int detailCount) {
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.requestId = requestId;
        this.summary = summary;
        this.detailCount = detailCount;
    }
}
