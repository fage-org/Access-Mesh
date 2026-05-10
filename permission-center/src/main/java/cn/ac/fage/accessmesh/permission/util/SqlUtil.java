package cn.ac.fage.accessmesh.permission.util;

/**
 * SQL工具类
 * <p>
 * 提供SQL查询相关的工具方法。
 * 主要用于处理LIKE查询中的特殊字符转义。
 * </p>
 */
public final class SqlUtil {

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private SqlUtil() {}

    /**
     * 转义LIKE查询特殊字符
     * <p>
     * 转义SQL LIKE语句中的特殊字符（%和_），
     * 使它们作为普通字符而不是通配符匹配。
     * 用于防止用户输入的搜索关键词被误解析为通配符。
     * </p>
     *
     * @param keyword 用户提供的搜索关键词
     * @return 转义后的关键词，可用于LIKE '%keyword%'模式
     */
    public static String escapeLikeKeyword(String keyword) {
        if (keyword == null) return null;
        return keyword.replace("\\", "\\\\")
                      .replace("%", "\\%")
                      .replace("_", "\\_");
    }

    /**
     * 构建LIKE查询模式
     * <p>
     * 构建带有转义通配符的LIKE模式字符串。
     * 等价于 LIKE '%escapedKeyword%' ESCAPE '\'。
     * </p>
     *
     * @param keyword 搜索关键词
     * @return LIKE模式字符串
     */
    public static String likePattern(String keyword) {
        return "%" + escapeLikeKeyword(keyword) + "%";
    }
}