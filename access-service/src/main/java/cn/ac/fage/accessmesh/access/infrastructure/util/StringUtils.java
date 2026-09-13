package cn.ac.fage.accessmesh.access.infrastructure.util;

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

    /**
     * 过滤参数规整：空白串归一为 null，其余去首尾空白
     * <p>
     * 与 SQL {@code <if>} 判空语义一致（规整为 null 走 Mapper 动态 SQL 的空分支），
     * 管理查询入口的过滤参数规整统一复用本方法。
     * </p>
     *
     * @param value 待规整字符串
     * @return null 或纯空白返回 null，否则返回去除首尾空白的结果
     */
    public static String normalizeFilterParam(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
