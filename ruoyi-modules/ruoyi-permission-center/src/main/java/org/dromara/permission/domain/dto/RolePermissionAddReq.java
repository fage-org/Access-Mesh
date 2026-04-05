package org.dromara.permission.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RolePermissionAddReq {
    @NotNull(message = "tenantId不能为空")
    private Long tenantId;

    @NotNull(message = "abstractRoleId不能为空")
    private Long abstractRoleId;

    private String requestId;

    private String changeSource;

    private String changeReason;

    @Valid
    @NotEmpty(message = "items不能为空")
    private List<RolePermissionItem> items;

    @Data
    public static class RolePermissionItem {
        @NotNull(message = "resourceEntityId不能为空")
        private Long resourceEntityId;

        @NotNull(message = "operationPermissionId不能为空")
        private Long operationPermissionId;

        private Boolean canManage;

        private Long conditionId;
    }
}
