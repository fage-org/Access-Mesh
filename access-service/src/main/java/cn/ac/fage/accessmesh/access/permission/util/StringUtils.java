package cn.ac.fage.accessmesh.access.permission.util;

/**
 * 字符串工具类
 * <p>
 * 提供空值安全的字符串操作方法。
 * 用于统一的字符串验证和处理。
 * </p>
 */
public final class StringUtils {

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private StringUtils() {
        // 工具类
    }

    /**
     * 判断字符串是否为空或空白
     * <p>
     * 判断字符串为null、空字符串或仅包含空白字符。
     * </p>
     *
     * @param str 待检查的字符串
     * @return 字符串为null或空白返回true，否则返回false
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * 判断字符串是否非空
     * <p>
     * 判断字符串不为null且不为空字符串。
     * 当需要区分空字符串和空白字符串时使用此方法。
     * </p>
     *
     * @param str 待检查的字符串
     * @return 字符串有任何内容（包括空白字符）返回true，否则返回false
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }
}