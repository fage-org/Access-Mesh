package org.dromara.permission.operation.defaults;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.operation.PermissionMatcher;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@Primary
@RequiredArgsConstructor
public class DefaultPermissionMatcher implements PermissionMatcher {

    private final PermissionBridgeSupport permissionBridgeSupport;

    @Override
    public List<MatchedPermission> match(Set<Long> roleIds, Set<Long> resourceIds, Long operationPermissionId, PermissionContext ctx) {
        return permissionBridgeSupport.loadMatchedPermissions(ctx.getTenantId(), roleIds, resourceIds);
    }
}
