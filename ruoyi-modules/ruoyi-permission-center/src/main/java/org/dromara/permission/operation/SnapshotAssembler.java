package org.dromara.permission.operation;

import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.model.permission.SnapshotEntry;

import java.util.List;

public interface SnapshotAssembler {

    List<SnapshotEntry> assemble(List<MatchedPermission> permissions, PermissionContext ctx);
}
