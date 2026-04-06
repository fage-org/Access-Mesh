package org.dromara.permission.event;

import lombok.Getter;
import org.dromara.permission.domain.vo.ConflictViolationVo;

import java.util.List;

@Getter
public class PermissionConflictScanDetectedEvent {

    private final Long tenantId;
    private final Long bizDomainId;
    private final Long abstractUserId;
    private final Long abstractRoleId;
    private final Long resourceEntityId;
    private final String requestId;
    private final long totalViolations;
    private final List<ConflictViolationVo> violations;

    public PermissionConflictScanDetectedEvent(Long tenantId, Long bizDomainId, Long abstractUserId,
                                               Long abstractRoleId, Long resourceEntityId, String requestId,
                                               long totalViolations,
                                               List<ConflictViolationVo> violations) {
        this.tenantId = tenantId;
        this.bizDomainId = bizDomainId;
        this.abstractUserId = abstractUserId;
        this.abstractRoleId = abstractRoleId;
        this.resourceEntityId = resourceEntityId;
        this.requestId = requestId;
        this.totalViolations = totalViolations;
        this.violations = violations;
    }
}
