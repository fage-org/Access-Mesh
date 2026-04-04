package org.dromara.permission.model.permission;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GrantPermissionRequest {
    @NotNull
    private Long tenantId;
    @NotNull
    private Long abstractRoleId;
    @NotNull
    private Long resourceEntityId;
    @NotNull
    private Long operationPermissionId;
    private Long bizDomainId;
    private Boolean canManage;
    private Long conditionId;
    private String requestId;
    private String changeSource;
    private String changeReason;
}
