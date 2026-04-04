package org.dromara.permission.domain.vo;

import lombok.Data;

@Data
public class PermissionRevokeVo {
    private boolean success;
    private Long permissionId;
}
