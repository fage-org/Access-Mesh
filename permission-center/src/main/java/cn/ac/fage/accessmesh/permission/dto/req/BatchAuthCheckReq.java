package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BatchAuthCheckReq(
    @NotNull(message = "租户ID不能为空")
    Long tenantId,
    @NotNull(message = "用户ID不能为空")
    Long abstractUserId,
    @NotEmpty(message = "检查项不能为空")
    List<AuthCheckItem> items,
    String clientIp
) {
    public record AuthCheckItem(
        Long resourceEntityId,
        Long operationPermissionId,
        Long bizDomainId,
        String codeType,
        String inheritMode
    ) {}
}
