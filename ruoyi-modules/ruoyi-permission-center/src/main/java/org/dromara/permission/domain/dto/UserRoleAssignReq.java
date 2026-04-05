package org.dromara.permission.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserRoleAssignReq {
    @NotNull(message = "tenantId不能为空")
    private Long tenantId;

    @NotNull(message = "abstractUserId不能为空")
    private Long abstractUserId;

    private String requestId;

    private String changeSource;

    private String changeReason;

    @NotEmpty(message = "roleIds不能为空")
    private List<Long> roleIds;

    private LocalDateTime validFrom;
    private LocalDateTime validTo;
}
