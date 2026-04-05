package org.dromara.permission.condition;

import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.model.permission.PermissionContext;

public interface PermissionConditionPresetHandler {

    String getCode();

    boolean evaluate(PcPermissionCondition condition, PermissionContext context);
}
