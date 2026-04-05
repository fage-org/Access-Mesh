package org.dromara.permission.model.permission;

import lombok.Data;

@Data
public class RevokeResult {
    private boolean success;
    private boolean changed;
    private Long permissionId;

    public static RevokeResult success(Long permissionId) {
        RevokeResult result = new RevokeResult();
        result.setSuccess(true);
        result.setChanged(true);
        result.setPermissionId(permissionId);
        return result;
    }

    public static RevokeResult noOp(Long permissionId) {
        RevokeResult result = new RevokeResult();
        result.setSuccess(true);
        result.setChanged(false);
        result.setPermissionId(permissionId);
        return result;
    }
}
