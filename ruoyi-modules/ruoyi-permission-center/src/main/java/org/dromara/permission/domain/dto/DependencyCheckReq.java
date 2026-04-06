package org.dromara.permission.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 依赖检查请求
 */
@Data
public class DependencyCheckReq {
    @NotNull(message = "tenantId不能为空")
    private Long tenantId;

    private Long abstractUserId;

    private Long abstractRoleId;

    private Long bizDomainId;

    private Long resourceEntityId;

    private Long operationPermissionId;

    private String requestId;
}
