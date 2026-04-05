package org.dromara.permission.model.permission;

import lombok.Data;

import java.util.List;

@Data
public class UserRoleBatchRevokeRequest {
    private Long tenantId;
    private Long abstractUserId;
    private String requestId;
    private String changeSource;
    private String changeReason;
    private List<Long> roleIds;
}
