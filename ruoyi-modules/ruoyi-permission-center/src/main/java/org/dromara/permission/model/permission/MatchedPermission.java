package org.dromara.permission.model.permission;

import lombok.Data;

@Data
public class MatchedPermission {
    private Long permissionId;
    private Long roleId;
    private Long resourceId;
    private Long operationId;
    private Long conditionId;
    private Boolean canManage;
}
