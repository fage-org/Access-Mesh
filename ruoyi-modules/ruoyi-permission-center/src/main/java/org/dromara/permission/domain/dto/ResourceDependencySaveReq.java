package org.dromara.permission.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 资源依赖保存请求（单条）
 */
@Data
public class ResourceDependencySaveReq {
    private Long id;

    @NotNull(message = "tenantId不能为空")
    private Long tenantId;

    private String requestId;

    private String changeSource;

    private String changeReason;

    @NotNull(message = "resourceEntityId不能为空")
    private Long resourceEntityId;

    @NotNull(message = "dependsOnResourceEntityId不能为空")
    private Long dependsOnResourceEntityId;

    private Long sourceOperationPermissionId;

    @NotNull(message = "requiredOperationPermissionId不能为空")
    private Long requiredOperationPermissionId;
}
