package cn.ac.fage.accessmesh.access.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/**
 * 登录请求记录类
 * <p>
 * 用于账号密码登录的请求参数。
 * 租户编码由服务端解析内部 ID；clientId 仅作为非可信审计来源标签。
 * </p>
 *
 * @param tenantCode  不可变租户编码（必填）
 * @param username    用户名（必填）
 * @param password    密码（必填）
 * @param captchaId   验证码ID（可选）
 * @param captchaCode 验证码内容（可选）
 * @param clientId    审计来源标签（可选，不参与 OAuth2 客户端校验）
 */
public record LoginReq(
    /**
     * 租户编码
     */
    @NotBlank(message = "租户编码不能为空")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "租户编码格式不正确")
    String tenantCode,

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
    @Size(max = 128)
    String clientId
) {}
