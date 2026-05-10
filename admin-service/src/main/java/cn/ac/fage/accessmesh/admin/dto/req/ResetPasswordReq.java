package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 重置密码请求记录类
 * <p>
 * 用于管理员重置用户密码的请求参数。
 * 支持密码长度校验（8-32位）。
 * </p>
 *
 * @param userId      用户ID（必填）
 * @param newPassword 新密码（必填，长度8-32位）
 */
public record ResetPasswordReq(
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId,

    /**
     * 新密码（长度必须在8-32位之间）
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度必须在8-32位之间")
    String newPassword
) {}