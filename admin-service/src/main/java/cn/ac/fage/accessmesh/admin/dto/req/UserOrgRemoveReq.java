package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 移除用户组织关联请求记录类
 * <p>
 * 用于移除用户与组织关联关系的请求参数。
 * 用户将从指定组织中移除。
 * </p>
 *
 * @param userId 用户ID（必填）
 * @param orgId  组织ID（必填）
 */
public record UserOrgRemoveReq(
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId,

    /**
     * 组织ID
     */
    @NotNull(message = "组织ID不能为空")
    Long orgId
) {}