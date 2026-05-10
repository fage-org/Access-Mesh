package cn.ac.fage.accessmesh.permission.util;

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
     * 判断字符串是否有文本内容
     * <p>
     * 判断字符串不为null且包含非空白字符。
     * 这是大多数字符串验证场景推荐使用的方法。
     * </p>
     *
     * @param str 待检查的字符串
     * @return 字符串有实际内容返回true，否则返回false
     */
    public static boolean hasText(String str) {
        return str != null && !str.isBlank();
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

    /**
     * 判断字符串是否为空
     * <p>
     * 判断字符串为null或空字符串。
     * </p>
     *
     * @param str 待检查的字符串
     * @return 字符串为null或空返回true，否则返回false
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 返回非空白字符串或默认值
     * <p>
     * 如果字符串有文本内容则返回原字符串，否则返回默认值。
     * </p>
     *
     * @param str         待检查的字符串
     * @param defaultValue 默认值
     * @return 原字符串如果有文本内容，否则返回默认值
     */
    public static String defaultIfBlank(String str, String defaultValue) {
        return hasText(str) ? str : defaultValue;
    }

    /**
     * 去除空白并转换为null
     * <p>
     * 去除字符串两端的空白字符，如果结果为空白则返回null。
     * 用于规范化字符串输入。
     * </p>
     *
     * @param str 待处理的字符串
     * @return 去除空白后有内容的字符串，或null
     */
    public static String trimToNull(String str) {
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}