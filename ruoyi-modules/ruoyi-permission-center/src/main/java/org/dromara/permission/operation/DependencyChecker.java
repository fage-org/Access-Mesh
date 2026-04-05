package org.dromara.permission.operation;

import org.dromara.permission.model.permission.DependencyCheckResult;
import org.dromara.permission.model.permission.PermissionContext;

public interface DependencyChecker {

    DependencyCheckResult check(Long resourceEntityId, Long operationPermissionId, PermissionContext ctx);
}
