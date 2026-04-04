package org.dromara.permission.model.permission;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RevokePermissionRequest {
    @NotNull
    private Long tenantId;
    @NotNull
    private Long abstractRoleId;
    @NotNull
    private Long resourceEntityId;
    @NotNull
    private Long operationPermissionId;
    private String requestId;
    private String changeSource;
    private String changeReason;
}
