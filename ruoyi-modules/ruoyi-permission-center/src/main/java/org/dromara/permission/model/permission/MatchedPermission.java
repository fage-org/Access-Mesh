package org.dromara.permission.model.permission;

import lombok.Data;
import org.dromara.permission.domain.PcPermissionCondition;

@Data
public class MatchedPermission {
    private Long permissionId;
    private Long roleId;
    private Long resourceId;
    private Integer resourceType;
    private String resourceCode;
    private Long operationId;
    private String operationCode;
    private Long conditionId;
    private PcPermissionCondition condition;
    private Boolean canManage;
}
