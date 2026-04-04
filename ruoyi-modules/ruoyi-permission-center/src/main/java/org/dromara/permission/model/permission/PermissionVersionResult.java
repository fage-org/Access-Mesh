package org.dromara.permission.model.permission;

import lombok.Data;

@Data
public class PermissionVersionResult {
    private Long tenantId;
    private Long versionNo;
    private String versionToken;
    private String triggerEntityType;
    private Long triggerEntityId;
    private String remark;
}
