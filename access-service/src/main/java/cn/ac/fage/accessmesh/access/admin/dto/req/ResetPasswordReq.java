package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 重置密码请求记录类
 * <p>
 * 用于管理员重置用户密码的请求参数。
 * newPassword 为可选，不传时系统自动生成随机密码并通过响应返回。
 * 指定时需满足长度校验（8-32位）。
 * </p>
 *
 * @param userId      用户ID（必填）
 * @param newPassword 新密码（可选，长度8-32位；不传则自动生成）
 */
public record ResetPasswordReq(
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId,

    /**
     * 新密码（可选，长度8-32位）
     * <p>
     * 不传时系统自动生成随机密码，响应中返回明文。
     * 传入时需满足 8-32 位长度要求。
     * </p>
     */
    @Size(min = 8, max = 32, message = "密码长度必须在8-32位之间")
    String newPassword
) {}