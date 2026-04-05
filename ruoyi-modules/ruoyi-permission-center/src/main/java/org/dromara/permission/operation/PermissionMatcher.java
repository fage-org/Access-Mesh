package org.dromara.permission.operation;

import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;

import java.util.List;
import java.util.Set;

public interface PermissionMatcher {

    List<MatchedPermission> match(Set<Long> roleIds, Set<Long> resourceIds, Long operationPermissionId, PermissionContext ctx);
}
