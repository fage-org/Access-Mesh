package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量创建用户请求记录类
 * <p>
 * 用于一次性创建多个用户的请求参数。
 * 常用于批量导入用户数据。
 * </p>
 *
 * @param users 用户创建请求列表（必填，至少包含一个用户）
 */
public record UserBatchCreateReq(
    /**
     * 用户创建请求列表
     */
    @Valid @NotEmpty(message = "用户列表不能为空")
    List<UserCreateReq> users
) {}