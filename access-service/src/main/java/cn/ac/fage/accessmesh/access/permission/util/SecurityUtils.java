package cn.ac.fage.accessmesh.access.permission.util;

/**
 * 安全工具类
 * <p>
 * 提供常量时间比较等安全相关工具方法，防止时序攻击。
 * </p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 常量时间字符串比较
     * <p>
     * 无论字符串是否匹配，比较时间恒定，防止时序攻击。
     * </p>
     *
     * @param a 字符串a
     * @param b 字符串b
     * @return 是否相等
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (a.length() != b.length()) {
            // 仍需遍历以保持恒定时间
            int len = a.length();
            int result = a.length() ^ b.length();
            for (int i = 0; i < len; i++) {
                result |= a.charAt(i) ^ b.charAt(i);
            }
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
