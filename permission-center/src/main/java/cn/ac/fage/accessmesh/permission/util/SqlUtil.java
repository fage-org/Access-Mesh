package cn.ac.fage.accessmesh.permission.util;

/**
 * SQL utility helpers.
 */
public final class SqlUtil {

    private SqlUtil() {}

    /**
     * Escapes SQL LIKE special characters (% and _) in user input
     * so they are treated as literal characters rather than wildcards.
     *
     * @param keyword the user-provided search keyword
     * @return the escaped keyword safe for LIKE '%keyword%' patterns
     */
    public static String escapeLikeKeyword(String keyword) {
        if (keyword == null) return null;
        return keyword.replace("\\", "\\\\")
                      .replace("%", "\\%")
                      .replace("_", "\\_");
    }

    /**
     * Builds a LIKE pattern with escaped wildcards.
     * Equivalent to LIKE '%escapedKeyword%' ESCAPE '\'.
     */
    public static String likePattern(String keyword) {
        return "%" + escapeLikeKeyword(keyword) + "%";
    }
}
