package cn.ac.fage.accessmesh.access.admin.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求记录类
 * <p>
 * 用于账号密码登录的请求参数。
 * 包含租户ID、用户名、密码、验证码、客户端ID。
 * </p>
 *
 * @param tenantId    租户ID（必填）
 * @param username    用户名（必填）
 * @param password    密码（必填）
 * @param captchaId   验证码ID（可选）
 * @param captchaCode 验证码内容（可选）
 * @param clientId    客户端ID（必填）
 */
public record LoginReq(
    /**
     * 租户ID
     */
    @NotBlank(message = "租户ID不能为空")
    String tenantId,

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    String username,

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    String password,

    /**
     * 验证码ID（对应验证码生成接口返回的captchaId）
     */
    String captchaId,

    /**
     * 验证码内容（用户输入的验证码）
     */
    String captchaCode,

    /**
     * 客户端ID（用于区分不同的登录来源）
     */
    @NotBlank(message = "客户端ID不能为空")
    String clientId
) {}