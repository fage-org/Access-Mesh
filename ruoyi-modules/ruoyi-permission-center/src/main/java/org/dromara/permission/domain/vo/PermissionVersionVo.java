package org.dromara.permission.domain.vo;

import lombok.Data;

@Data
public class PermissionVersionVo {
    private Long tenantId;
    private Long versionNo;
    private String versionToken;
    private String triggerEntityType;
    private Long triggerEntityId;
    private String remark;
}
