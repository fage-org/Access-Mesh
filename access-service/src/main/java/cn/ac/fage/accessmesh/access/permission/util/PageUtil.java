package cn.ac.fage.accessmesh.access.permission.util;

/**
 * 分页工具类
 * <p>
 * 提供分页参数标准化和计算的静态工具方法。
 * 用于统一处理分页请求的参数验证和边界控制。
 * </p>
 */
public final class PageUtil {

    /**
     * 默认每页条数
     */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大每页条数
     * <p>
     * 用于防止大量数据查询导致的性能问题。
     * </p>
     */
    public static final int MAX_PAGE_SIZE = 200;

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private PageUtil() {}

    /**
     * 标准化页码
     * <p>
     * 将无效页码（null或小于1）标准化为默认值1。
     * 用于处理前端传入的分页参数。
     * </p>
     *
     * @param raw 原始页码值
     * @return 标准化后的页码，无效时返回1
     */
    public static int pageNum(Integer raw) {
        return raw != null && raw > 0 ? raw : 1;
    }

    /**
     * 标准化每页条数
     * <p>
     * 将无效条数（null或小于等于0）标准化为默认值。
     * 同时强制上限为MAX_PAGE_SIZE，防止DoS攻击。
     * </p>
     *
     * @param raw 原始每页条数
     * @return 标准化后的每页条数，无效时返回DEFAULT_PAGE_SIZE，超过上限返回MAX_PAGE_SIZE
     */
    public static int pageSize(Integer raw) {
        if (raw == null || raw <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(raw, MAX_PAGE_SIZE);
    }

    /**
     * 计算分页偏移量
     * <p>
     * 根据页码和每页条数计算数据库查询的偏移量。
     * 偏移量 = (pageNum - 1) * pageSize。
     * </p>
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页偏移量
     */
    public static int offset(int pageNum, int pageSize) {
        return (pageNum - 1) * pageSize;
    }

    /**
     * 判断是否有下一页
     * <p>
     * 根据当前偏移量、已获取数量和总数判断是否还有更多数据。
     * </p>
     *
     * @param offset       当前偏移量
     * @param fetchedCount 已获取的条数
     * @param total        数据总数
     * @return 有下一页返回true，否则返回false
     */
    public static boolean hasNext(int offset, int fetchedCount, long total) {
        return offset + fetchedCount < total;
    }
}