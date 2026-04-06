package org.dromara.permission.event;

import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.dto.DependencyCheckReq;
import org.dromara.permission.domain.vo.ConflictViolationVo;
import org.dromara.permission.model.permission.DependencyCheckResult;

import java.util.List;

public interface PermissionGovernanceEventPublisher {

    void publishConflictDetected(ConflictDetectReq request, List<ConflictViolationVo> violations, long totalViolations);

    void publishDependencyBroken(DependencyCheckReq request, DependencyCheckResult result);
}
