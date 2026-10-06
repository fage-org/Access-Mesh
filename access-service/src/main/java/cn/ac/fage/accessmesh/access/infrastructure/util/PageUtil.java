package cn.ac.fage.accessmesh.access.infrastructure.util;

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
    private static final int DEFAULT_PAGE_SIZE = 20;

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
     * null 标准化为默认值 1；显式小于等于 0 抛出参数错误
     * （2026-10-06 拍板：分页下界与 @Min(1) 族统一，显式非法值拒绝而非静默归一）。
     * </p>
     *
     * @param raw 原始页码值
     * @return 标准化后的页码，null 时返回 1
     */
    public static int pageNum(Integer raw) {
        if (raw == null) {
            return 1;
        }
        if (raw <= 0) {
            throw new IllegalArgumentException("pageNum 必须为正整数（显式 <=0 拒绝）");
        }
        return raw;
    }

    /**
     * 标准化每页条数
     * <p>
     * null 标准化为默认值；显式小于等于 0 抛出参数错误（2026-10-06 拍板：
     * 分页下界统一，显式非法值拒绝而非静默归一）。
     * 同时强制上限为MAX_PAGE_SIZE，防止DoS攻击。
     * </p>
     *
     * @param raw 原始每页条数
     * @return 标准化后的每页条数，null 时返回DEFAULT_PAGE_SIZE，显式非法/超过上限抛出参数错误
     */
    public static int pageSize(Integer raw) {
        if (raw == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (raw <= 0) {
            throw new IllegalArgumentException("pageSize 必须为正整数（显式 <=0 拒绝）");
        }
        if (raw > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize 最大为 " + MAX_PAGE_SIZE);
        }
        return raw;
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
        long offset = (long) (pageNum - 1) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("pageNum 超出支持的分页范围");
        }
        return (int) offset;
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
        return (long) offset + fetchedCount < total;
    }
}