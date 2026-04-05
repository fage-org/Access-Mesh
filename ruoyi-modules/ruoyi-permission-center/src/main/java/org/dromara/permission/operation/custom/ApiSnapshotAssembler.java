package org.dromara.permission.operation.custom;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.model.permission.SnapshotEntry;
import org.dromara.permission.operation.SnapshotAssembler;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiSnapshotAssembler implements SnapshotAssembler {

    private final PermissionBridgeSupport permissionBridgeSupport;

    @Override
    public List<SnapshotEntry> assemble(List<MatchedPermission> permissions, PermissionContext ctx) {
        return permissionBridgeSupport.assembleApiSnapshotEntries(permissions, ctx);
    }
}
