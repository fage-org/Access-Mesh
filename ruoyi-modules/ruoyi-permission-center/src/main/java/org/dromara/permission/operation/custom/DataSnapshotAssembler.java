package org.dromara.permission.operation.custom;

import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.model.permission.SnapshotEntry;
import org.dromara.permission.operation.SnapshotAssembler;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataSnapshotAssembler implements SnapshotAssembler {

    private final PermissionBridgeSupport permissionBridgeSupport;

    public DataSnapshotAssembler(PermissionBridgeSupport permissionBridgeSupport) {
        this.permissionBridgeSupport = permissionBridgeSupport;
    }

    @Override
    public List<SnapshotEntry> assemble(List<MatchedPermission> permissions, PermissionContext ctx) {
        List<SnapshotEntry> entries = permissionBridgeSupport.assembleDefaultSnapshotEntries(permissions);
        for (SnapshotEntry entry : entries) {
            PcResourceEntity resource = ctx.getResources().get(entry.getResourceId());
            if (resource == null || resource.getExtra() == null || resource.getExtra().isBlank()) {
                continue;
            }
            Map<String, Object> extra = entry.getExtra() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(entry.getExtra());
            extra.put("dataScope", resource.getExtra());
            entry.setExtra(extra);
        }
        return entries;
    }
}
