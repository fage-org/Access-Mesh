package cn.ac.fage.accessmesh.admin.dto.auth;

/**
 * 验证码响应记录类
 * <p>
 * 包含验证码ID和验证码图片的Base64编码。
 * 用于登录时的验证码校验。
 * </p>
 *
 * @param captchaId 验证码ID，用于后续校验
 * @param image     验证码图片的Base64编码
 */
public record CaptchaResp(
    /**
     * 验证码ID，用于后续校验
     */
    String captchaId,

    /**
     * 验证码图片的Base64编码（含data:image/png;base64,前缀）
     */
    String image
) {}