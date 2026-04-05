package org.dromara.permission.event;

import org.dromara.permission.model.permission.ConflictDetail;
import org.dromara.permission.model.permission.PermissionContext;

import java.util.List;

public interface PermissionConflictEventPublisher {

    void publish(PermissionContext context, List<ConflictDetail> conflicts);
}
