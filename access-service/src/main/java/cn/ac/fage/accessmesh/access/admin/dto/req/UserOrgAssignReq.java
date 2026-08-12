package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 用户组织分配请求记录类
 * <p>
 * 用于为用户分配所属组织的请求参数。
 * 可同时设置主要组织。
 * </p>
 *
 * @param userId       用户ID（必填）
 * @param orgIds       组织ID列表（必填，至少一个）
 * @param primaryOrgId 主要组织ID（可选，默认第一个组织）
 */
public record UserOrgAssignReq(
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId,

    /**
     * 组织ID列表
     */
    @NotNull(message = "组织ID列表不能为空")
    List<Long> orgIds,

    /**
     * 主要组织ID（默认第一个组织）
     */
    Long primaryOrgId
) {}