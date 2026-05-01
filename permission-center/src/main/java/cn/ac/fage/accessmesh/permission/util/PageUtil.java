package cn.ac.fage.accessmesh.permission.util;

/**
 * Pagination helper — resolves page parameters and computes offset.
 */
public final class PageUtil {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private PageUtil() {}

    public static int pageNum(Integer raw) {
        return raw != null && raw > 0 ? raw : DEFAULT_PAGE_NUM;
    }

    public static int pageSize(Integer raw) {
        return raw != null && raw > 0 ? raw : DEFAULT_PAGE_SIZE;
    }

    public static int pageSize(Integer raw, int defaultSize) {
        return raw != null && raw > 0 ? raw : defaultSize;
    }

    public static int offset(int pageNum, int pageSize) {
        return (pageNum - 1) * pageSize;
    }

    public static boolean hasNext(int offset, int fetchedCount, long total) {
        return offset + fetchedCount < total;
    }
}
