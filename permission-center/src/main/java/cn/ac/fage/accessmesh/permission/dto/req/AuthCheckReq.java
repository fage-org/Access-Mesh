package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuthCheckReq(
    @NotNull(message = "租户ID不能为空")
    Long tenantId,
    @NotNull(message = "用户ID不能为空")
    Long abstractUserId,
    @NotNull(message = "资源ID不能为空")
    Long resourceEntityId,
    @NotNull(message = "操作权限ID不能为空")
    Long operationPermissionId,
    Long bizDomainId,
    String codeType,
    String inheritMode,
    String clientIp
) {}
