package org.dromara.permission.operation.defaults;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.model.permission.ConflictDetail;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.operation.ConflictDetector;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
@RequiredArgsConstructor
public class DefaultConflictDetector implements ConflictDetector {

    private final PermissionBridgeSupport permissionBridgeSupport;

    @Override
    public List<ConflictDetail> detect(List<MatchedPermission> matchedPermissions, PermissionContext ctx) {
        if (matchedPermissions.isEmpty()) {
            return List.of();
        }
        return permissionBridgeSupport.detectConflictsForSnapshot(ctx.getTenantId(), matchedPermissions, ctx.getResources());
    }
}
