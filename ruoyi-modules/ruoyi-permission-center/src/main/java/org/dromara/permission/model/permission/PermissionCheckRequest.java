package org.dromara.permission.model.permission;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class PermissionCheckRequest {
    @NotNull
    private Long tenantId;
    @NotNull
    private Long abstractUserId;
    @NotNull
    private Long resourceEntityId;
    @NotNull
    private Long operationPermissionId;
    private Long bizDomainId;
    private InheritMode inheritMode = InheritMode.NONE;
    private Boolean checkDependency = Boolean.TRUE;
    private Map<String, Object> context;
}
