package org.dromara.permission.model.permission;

import lombok.Data;

import java.util.List;

@Data
public class RolePermissionBatchRevokeRequest {
    private Long tenantId;
    private Long abstractRoleId;
    private String requestId;
    private String changeSource;
    private String changeReason;
    private List<RolePermissionRevokeItem> items;

    @Data
    public static class RolePermissionRevokeItem {
        private Long resourceEntityId;
        private Long operationPermissionId;
    }
}
