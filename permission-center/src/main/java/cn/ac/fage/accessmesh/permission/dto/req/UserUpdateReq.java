package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 用户更新请求体
 * <p>
 * 用于更新用户的信息，包括名称、启用状态和扩展属性。
 * </p>
 *
 * @param userId  用户ID，必填
 * @param name    用户名称，可选
 * @param enabled 是否启用，可选
 * @param extra   扩展属性JSON，可选
 */
public record UserUpdateReq(
    @NotNull Long userId,
    String name,
    Boolean enabled,
    String extra
) {}