package cn.ac.fage.accessmesh.access.admin.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 短信登录请求记录类
 * <p>
 * 用于短信验证码登录的请求参数。
 * 包含租户ID、手机号、短信验证码、客户端ID。
 * </p>
 *
 * @param tenantId 租户ID（必填）
 * @param phone    手机号（必填）
 * @param smsCode  短信验证码（必填）
 * @param clientId 客户端ID（必填）
 */
public record SmsLoginReq(
    /**
     * 租户ID
     */
    @NotBlank(message = "租户ID不能为空")
    String tenantId,

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    @Size(max = 20, message = "手机号长度不能超过20")
    String phone,

    /**
     * 短信验证码
     */
    @NotBlank(message = "验证码不能为空")
    String smsCode,

    /**
     * 客户端ID
     */
    @NotBlank(message = "客户端ID不能为空")
    String clientId
) {}