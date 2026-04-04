package org.dromara.permission.model.permission;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SnapshotRequest {
    @NotNull
    private Long tenantId;
    @NotNull
    private Long abstractUserId;
    private Long bizDomainId;
    private Boolean includeConditional = Boolean.FALSE;
}
