package org.dromara.permission.event;

import lombok.Getter;

@Getter
public class PermissionWriteRefreshEvent {

    private final Long tenantId;
    private final Long versionNo;
    private final String triggerEntityType;
    private final Long triggerEntityId;
    private final String action;
    private final String requestId;
    private final String changeSource;

    public PermissionWriteRefreshEvent(Long tenantId, Long versionNo, String triggerEntityType, Long triggerEntityId,
                                       String action, String requestId, String changeSource) {
        this.tenantId = tenantId;
        this.versionNo = versionNo;
        this.triggerEntityType = triggerEntityType;
        this.triggerEntityId = triggerEntityId;
        this.action = action;
        this.requestId = requestId;
        this.changeSource = changeSource;
    }
}
