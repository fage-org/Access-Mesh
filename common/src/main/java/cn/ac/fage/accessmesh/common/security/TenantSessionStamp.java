package cn.ac.fage.accessmesh.common.security;

import java.util.Set;

/** 原生租户令牌会话的不可变认证标记；旧令牌缺少标记时拒绝，不猜测租户或代次。 */
public record TenantSessionStamp(long epoch, boolean forceResetPwd, String redisProcessId) {
    public static final String PROCESS = "redisProcessId";
    public static final String EPOCH = "tenantEpoch";
    public static final String FORCE_RESET = "forceResetPwd";

    /** 强制改密期仅放行的自助认证／改密入口（与平台端 SELF 清单语义对齐） */
    private static final Set<String> SELF_PATHS = Set.of(
        "/api/access/auth/userinfo", "/api/access/auth/user-menu",
        "/api/access/auth/logout", "/api/access/user/reset-password");

    public static TenantSessionStamp from(Object epoch, Object forceReset, Object process) {
        if (epoch == null || !(forceReset instanceof Boolean forced)
            || !(process instanceof String processId) || !processId.matches("[a-f0-9]{40}")) {
            return null;
        }
        try {
            long value = Long.parseLong(epoch.toString());
            return value > 0 ? new TenantSessionStamp(value, forced, processId) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static boolean allowsForcedReset(String path) {
        return SELF_PATHS.contains(path);
    }
}
