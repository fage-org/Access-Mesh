package org.dromara.permission.operation;

import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;

public interface ConditionEvaluator {

    boolean evaluate(MatchedPermission permission, PermissionContext ctx);
}
