package cn.ac.fage.accessmesh.common.security;

import java.util.Set;
import java.util.stream.Stream;

/** 独立平台认证的精确端点单源，不使用整个 /api/access/** 的通配豁免。 */
public final class PlatformEndpoints {
    private PlatformEndpoints() {}

    private static final Set<String> PUBLIC = Set.of(
        "/api/access/platform-auth/captcha", "/api/access/platform-auth/login", "/api/access/platform-auth/logout");
    private static final Set<String> SELF = Set.of(
        "/api/access/platform-auth/me", "/api/access/platform-auth/change-password");
    private static final Set<String> MANAGEMENT = Set.of(
        "/api/access/platform-account/page", "/api/access/platform-account/create",
        "/api/access/platform-account/update", "/api/access/platform-account/update-status",
        "/api/access/platform-account/reset-password", "/api/access/platform-audit/page",
        "/api/access/tenant/page", "/api/access/tenant/detail", "/api/access/tenant/create",
        "/api/access/tenant/update", "/api/access/tenant/update-status", "/api/access/tenant/reset-admin-password");

    public static boolean matches(String method, String path) {
        return "POST".equalsIgnoreCase(method) && containsPath(path);
    }

    public static boolean containsPath(String path) {
        return PUBLIC.contains(path) || SELF.contains(path) || MANAGEMENT.contains(path);
    }

    public static boolean isPublic(String path) { return PUBLIC.contains(path); }

    public static boolean allowsForcedReset(String path) {
        return PUBLIC.contains(path) || SELF.contains(path);
    }

    public static String[] paths() {
        return Stream.of(PUBLIC, SELF, MANAGEMENT).flatMap(Set::stream).toArray(String[]::new);
    }
}
