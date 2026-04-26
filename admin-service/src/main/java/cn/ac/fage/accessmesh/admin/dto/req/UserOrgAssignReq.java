package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserOrgAssignReq(
    @NotNull(message = "用户ID不能为空")
    Long userId,
    @NotNull(message = "组织ID列表不能为空")
    List<Long> orgIds,
    Long primaryOrgId
) {}
