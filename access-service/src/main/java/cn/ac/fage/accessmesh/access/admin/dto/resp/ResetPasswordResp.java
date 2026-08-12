package cn.ac.fage.accessmesh.access.admin.dto.resp;

/**
 * 重置密码响应记录类
 * <p>
 * 重置密码成功后返回生效的密码。
 * 当前端未指定新密码时，返回系统自动生成的随机密码。
 * </p>
 *
 * @param newPassword 生效的新密码（明文，仅本次返回）
 */
public record ResetPasswordResp(
    /**
     * 生效的新密码（明文）
     * <p>
     * 如果请求中指定了新密码则返回该值，否则返回系统生成的随机密码。
     * 仅在重置时返回一次，后续无法再获取明文密码。
     * </p>
     */
    String newPassword
) {}
