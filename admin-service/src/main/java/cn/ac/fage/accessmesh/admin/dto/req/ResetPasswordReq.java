package cn.ac.fage.accessmesh.admin.dto.req;

/**
 * Reset user password.
 */
public record ResetPasswordReq(Long userId, String newPassword) {}
