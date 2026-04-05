package org.dromara.permission.operation.defaults;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.model.permission.DependencyCheckResult;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.operation.DependencyChecker;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Primary
@RequiredArgsConstructor
public class DefaultDependencyChecker implements DependencyChecker {

    private final PermissionBridgeSupport permissionBridgeSupport;

    @Override
    public DependencyCheckResult check(Long resourceEntityId, Long operationPermissionId, PermissionContext ctx) {
        List<Long> roleIds = ctx.getRoles().stream().map(role -> role.getRoleId()).collect(Collectors.toList());
        return permissionBridgeSupport.checkDependencies(ctx, resourceEntityId, operationPermissionId, roleIds);
    }
}
