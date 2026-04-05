package org.dromara.permission.operation.custom;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.operation.PermissionMatcher;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ApiPermissionMatcher implements PermissionMatcher {

    private final PermissionBridgeSupport permissionBridgeSupport;

    @Override
    public List<MatchedPermission> match(Set<Long> roleIds, Set<Long> resourceIds, Long operationPermissionId, PermissionContext ctx) {
        // PermissionService.check is a resource-level exact authorization API.
        // Route -> API resource resolution belongs to interface decision / gateway flows.
        return permissionBridgeSupport.loadMatchedPermissions(ctx.getTenantId(), roleIds, resourceIds);
    }
}
