package org.dromara.permission.model.permission;

import lombok.Data;

@Data
public class RevokeResult {
    private boolean success;
    private Long permissionId;

    public static RevokeResult success(Long permissionId) {
        RevokeResult result = new RevokeResult();
        result.setSuccess(true);
        result.setPermissionId(permissionId);
        return result;
    }
}
