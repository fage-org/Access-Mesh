package org.dromara.permission.operation.custom;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.model.permission.GrantPermissionRequest;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.model.permission.ValidationResult;
import org.dromara.permission.operation.GrantValidator;
import org.dromara.permission.service.ResourceApiMappingService;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@RequiredArgsConstructor
public class ApiGrantValidator implements GrantValidator {

    private final ResourceApiMappingService resourceApiMappingService;

    @Override
    public ValidationResult validate(GrantPermissionRequest request, PermissionContext ctx) {
        if (ctx.getResource() == null || ctx.getResource().getId() == null) {
            return ValidationResult.ok();
        }
        boolean exists = !resourceApiMappingService.listEnabledMappings(request.getTenantId(),
            Collections.singleton(ctx.getResource().getId())).isEmpty();
        if (exists) {
            return ValidationResult.ok();
        }
        return ValidationResult.fail("API resource missing enabled route mapping");
    }
}
