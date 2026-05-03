package cn.ac.fage.accessmesh.permission.util;

/**
 * Pagination utilities.
 */
public final class PageUtil {

    private static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 200;

    private PageUtil() {}

    /**
     * Normalizes page number, defaults to 1 if invalid.
     */
    public static int pageNum(Integer raw) {
        return raw != null && raw > 0 ? raw : 1;
    }

    /**
     * Normalizes page size with upper limit.
     * Returns DEFAULT_PAGE_SIZE if raw is null or invalid.
     * Enforces MAX_PAGE_SIZE limit to prevent DoS.
     */
    public static int pageSize(Integer raw) {
        if (raw == null || raw <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(raw, MAX_PAGE_SIZE);
    }

    /**
     * Calculates offset for pagination.
     */
    public static int offset(int pageNum, int pageSize) {
        return (pageNum - 1) * pageSize;
    }

    /**
     * Determines if there is a next page.
     */
    public static boolean hasNext(int offset, int fetchedCount, long total) {
        return offset + fetchedCount < total;
    }
}
