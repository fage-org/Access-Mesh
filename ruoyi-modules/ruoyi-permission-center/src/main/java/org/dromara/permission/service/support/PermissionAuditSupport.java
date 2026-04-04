package org.dromara.permission.service.support;

import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.permission.domain.PermissionBaseEntity;

import java.time.LocalDateTime;

public final class PermissionAuditSupport {

    private PermissionAuditSupport() {
    }

    public static Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    public static void markDeleted(PermissionBaseEntity entity, Long entityId, LocalDateTime now) {
        entity.setDeleteFlag(entityId);
        entity.setDeletedAt(now);
        entity.setUpdatedAt(now);
        Long userId = currentUserId();
        if (userId != null) {
            entity.setDeletedBy(userId);
        }
    }
}
