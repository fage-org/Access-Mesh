package org.dromara.permission.model.permission;

import lombok.Data;

import java.util.List;

@Data
public class UserRoleBatchRevokeRequest {
    private Long tenantId;
    private Long abstractUserId;
    private List<Long> roleIds;
}
