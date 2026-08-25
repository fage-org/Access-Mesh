package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 用户状态变更请求记录类
 * <p>
 * 用于批量启用或禁用用户。
 * status=1 表示启用，status=0 表示禁用。
 * </p>
 *
 * @param ids    用户ID列表（必填，至少包含一个ID）
 * @param status 目标状态（必填，1=启用，0=停用）
 */
public record UserUpdateStatusReq(
    /**
     * 用户ID列表
     */
    @NotEmpty(message = "用户ID列表不能为空")
    List<Long> ids,

    /**
     * 目标状态（1=启用，0=停用）
     */
    @NotNull(message = "状态不能为空")
    Integer status
) {}
