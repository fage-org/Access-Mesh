package cn.ac.fage.accessmesh.access.tenant.service;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;

public class TenantAccessDeniedException extends RuntimeException {
    private final int status;
    private final int code;

    private TenantAccessDeniedException(int status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public int code() { return code; }

    public static TenantAccessDeniedException disabled() {
        return new TenantAccessDeniedException(403,
            AccessErrorCode.TENANT_DISABLED.getCode(), AccessErrorCode.TENANT_DISABLED.getMessage());
    }

    /** 会话失效复用 HTTP 401 作业务码：前端按 401 统一走重新登录路径（与拦截器 writeJson 同口径） */
    public static TenantAccessDeniedException expired() {
        return new TenantAccessDeniedException(401, 401, "租户会话已失效，请重新登录");
    }

    public static TenantAccessDeniedException passwordChangeRequired() {
        return new TenantAccessDeniedException(403,
            AccessErrorCode.PASSWORD_RESET_REQUIRED.getCode(), AccessErrorCode.PASSWORD_RESET_REQUIRED.getMessage());
    }
}
