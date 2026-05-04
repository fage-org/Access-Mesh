package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量创建用户请求
 */
public record UserBatchCreateReq(
    @Valid @NotEmpty(message = "用户列表不能为空")
    List<UserCreateReq> users
) {}