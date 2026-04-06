package org.dromara.permission.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 资源依赖列表请求
 */
@Data
public class ResourceDependencyListReq {
    @NotNull(message = "tenantId不能为空")
    private Long tenantId;

    private Long resourceEntityId;

    private Long dependsOnResourceEntityId;

    private Long sourceOperationPermissionId;

    private Long requiredOperationPermissionId;

    /**
     * 图查询模式：AROUND / UPSTREAM / DOWNSTREAM
     */
    private String graphMode;
}
