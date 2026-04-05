package org.dromara.permission.operation.custom;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.model.permission.InheritMode;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.operation.InheritanceExpander;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInheritanceExpander implements InheritanceExpander {

    private final PermissionBridgeSupport permissionBridgeSupport;

    @Override
    public Set<Long> expand(Long resourceEntityId, InheritMode mode, PermissionContext ctx) {
        PcResourceEntity resource = ctx.getResource();
        if (resource == null || !resourceEntityId.equals(resource.getId())) {
            resource = permissionBridgeSupport.loadResource(ctx.getTenantId(), resourceEntityId);
        }
        return permissionBridgeSupport.expandResourceIds(resource, mode);
    }
}
