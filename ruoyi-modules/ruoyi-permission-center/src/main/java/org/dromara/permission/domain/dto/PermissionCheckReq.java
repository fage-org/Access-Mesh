package org.dromara.permission.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.permission.model.permission.InheritMode;

import java.util.Map;

@Data
public class PermissionCheckReq {
    @NotNull(message = "tenantId不能为空")
    private Long tenantId;

    @NotNull(message = "abstractUserId不能为空")
    private Long abstractUserId;

    @NotNull(message = "resourceEntityId不能为空")
    private Long resourceEntityId;

    @NotNull(message = "operationPermissionId不能为空")
    private Long operationPermissionId;

    private Long bizDomainId;
    private InheritMode inheritMode = InheritMode.NONE;
    private Boolean checkDependency = Boolean.TRUE;
    private Map<String, Object> context;
}
