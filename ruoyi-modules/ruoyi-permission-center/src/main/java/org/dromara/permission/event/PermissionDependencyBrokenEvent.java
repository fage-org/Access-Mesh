package org.dromara.permission.event;

import lombok.Getter;
import org.dromara.permission.model.permission.DependencyGap;

import java.util.List;

@Getter
public class PermissionDependencyBrokenEvent {

    private final Long tenantId;
    private final Long bizDomainId;
    private final Long abstractUserId;
    private final Long abstractRoleId;
    private final Long resourceEntityId;
    private final Long operationPermissionId;
    private final String requestId;
    private final List<DependencyGap> gaps;

    public PermissionDependencyBrokenEvent(Long tenantId, Long bizDomainId, Long abstractUserId, Long abstractRoleId,
                                           Long resourceEntityId, Long operationPermissionId, String requestId,
                                           List<DependencyGap> gaps) {
        this.tenantId = tenantId;
        this.bizDomainId = bizDomainId;
        this.abstractUserId = abstractUserId;
        this.abstractRoleId = abstractRoleId;
        this.resourceEntityId = resourceEntityId;
        this.operationPermissionId = operationPermissionId;
        this.requestId = requestId;
        this.gaps = gaps;
    }
}
