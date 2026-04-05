package org.dromara.permission.model.permission;

import lombok.Data;

import java.util.List;

@Data
public class RolePermissionBatchGrantRequest {
    private Long tenantId;
    private Long abstractRoleId;
    private String requestId;
    private String changeSource;
    private String changeReason;
    private List<RolePermissionGrantItem> items;

    @Data
    public static class RolePermissionGrantItem {
        private Long resourceEntityId;
        private Long operationPermissionId;
        private Boolean canManage;
        private Long conditionId;
    }
}
