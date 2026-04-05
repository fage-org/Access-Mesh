package org.dromara.permission.operation.defaults;

import org.dromara.permission.model.permission.GrantPermissionRequest;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.model.permission.ValidationResult;
import org.dromara.permission.operation.GrantValidator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class DefaultGrantValidator implements GrantValidator {

    @Override
    public ValidationResult validate(GrantPermissionRequest request, PermissionContext ctx) {
        return ValidationResult.ok();
    }
}
