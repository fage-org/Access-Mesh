package org.dromara.permission.operation;

import org.dromara.permission.model.permission.GrantPermissionRequest;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.model.permission.ValidationResult;

public interface GrantValidator {

    ValidationResult validate(GrantPermissionRequest request, PermissionContext ctx);
}
