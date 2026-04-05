package org.dromara.permission.model.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GrantResult {
    private boolean success;
    private boolean changed;
    private Long permissionId;
    private List<String> rejectReasons = new ArrayList<>();

    public static GrantResult success(Long permissionId) {
        GrantResult result = new GrantResult();
        result.setSuccess(true);
        result.setChanged(true);
        result.setPermissionId(permissionId);
        return result;
    }

    public static GrantResult noOp(Long permissionId) {
        GrantResult result = new GrantResult();
        result.setSuccess(true);
        result.setChanged(false);
        result.setPermissionId(permissionId);
        return result;
    }

    public static GrantResult rejected(List<String> reasons) {
        GrantResult result = new GrantResult();
        result.setSuccess(false);
        result.setChanged(false);
        result.setRejectReasons(reasons == null ? new ArrayList<>() : reasons);
        return result;
    }
}
