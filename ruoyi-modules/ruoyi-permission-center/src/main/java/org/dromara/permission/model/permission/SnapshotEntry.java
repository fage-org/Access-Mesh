package org.dromara.permission.model.permission;

import lombok.Data;

@Data
public class SnapshotEntry {
    private Long roleId;
    private Long resourceId;
    private String resourceCode;
    private Long operationId;
    private String operationCode;
    private Long conditionId;
    private Boolean canManage;
}
