package org.dromara.permission.operation;

import org.dromara.permission.model.permission.ConflictDetail;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;

import java.util.List;

public interface ConflictDetector {

    List<ConflictDetail> detect(List<MatchedPermission> matchedPermissions, PermissionContext ctx);
}
