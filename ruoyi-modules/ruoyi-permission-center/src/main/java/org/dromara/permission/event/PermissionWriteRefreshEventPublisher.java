package org.dromara.permission.event;

import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.model.permission.PermissionContext;

public interface PermissionWriteRefreshEventPublisher {

    void publish(PermissionContext context, PcPermissionVersion version);
}
