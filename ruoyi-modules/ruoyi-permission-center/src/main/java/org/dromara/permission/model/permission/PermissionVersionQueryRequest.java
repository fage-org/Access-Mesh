package org.dromara.permission.model.permission;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PermissionVersionQueryRequest {
    @NotNull
    private Long tenantId;
}
