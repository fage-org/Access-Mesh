package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Set primary organization for user.
 */
public record UserOrgSetPrimaryReq(
    @NotNull(message = "用户ID不能为空")
    Long userId,
    @NotNull(message = "组织ID不能为空")
    Long orgId
) {}
