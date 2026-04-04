package org.dromara.permission.domain.vo;

import lombok.Data;

@Data
public class PermissionSnapshotEntryVo {
    private Long roleId;
    private Long resourceId;
    private String resourceCode;
    private Long operationId;
    private String operationCode;
    private Long conditionId;
    private Boolean canManage;
}
