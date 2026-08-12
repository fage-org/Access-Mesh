package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 设置用户主要组织请求记录类
 * <p>
 * 用于设置用户的主要组织（主归属组织）的请求参数。
 * 主要组织通常是用户的主要工作部门。
 * </p>
 *
 * @param userId 用户ID（必填）
 * @param orgId  组织ID（必填，将设为主要组织）
 */
public record UserOrgSetPrimaryReq(
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId,

    /**
     * 组织ID（将设为主要组织）
     */
    @NotNull(message = "组织ID不能为空")
    Long orgId
) {}