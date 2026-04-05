package org.dromara.permission.operation;

import org.dromara.permission.model.permission.InheritMode;
import org.dromara.permission.model.permission.PermissionContext;

import java.util.Set;

public interface InheritanceExpander {

    Set<Long> expand(Long resourceEntityId, InheritMode mode, PermissionContext ctx);
}
