package org.dromara.permission.domain.vo;

import lombok.Data;

@Data
public class PermissionCheckGrantedByVo {
    private Long permissionId;
    private Long roleId;
    private Long resourceId;
    private Long operationId;
    private Long conditionId;
    private Boolean canManage;
}
