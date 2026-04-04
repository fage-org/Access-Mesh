package org.dromara.permission.model.permission;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserRoleBatchAssignRequest {
    private Long tenantId;
    private Long abstractUserId;
    private List<Long> roleIds;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
}
