package cn.ac.fage.accessmesh.permission.util;

/**
 * String utility methods for null-safe string operations.
 */
public final class StringUtils {

    private StringUtils() {
        // Utility class
    }

    /**
     * Check if the string is not null and not blank (contains non-whitespace characters).
     * This is the recommended method for most string validation scenarios.
     *
     * @param str the string to check
     * @return true if the string has meaningful content, false otherwise
     */
    public static boolean hasText(String str) {
        return str != null && !str.isBlank();
    }

    /**
     * Check if the string is null or blank (empty or only whitespace).
     *
     * @param str the string to check
     * @return true if the string is null or blank, false otherwise
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * Check if the string is not null and not empty.
     * Use this when you need to distinguish between empty and blank strings.
     *
     * @param str the string to check
     * @return true if the string has any content (including whitespace), false otherwise
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    /**
     * Check if the string is null or empty.
     *
     * @param str the string to check
     * @return true if the string is null or empty, false otherwise
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Return the string if not blank, otherwise return the default value.
     *
     * @param str the string to check
     * @param defaultValue the default value to return if blank
     * @return the original string if has text, otherwise the default
     */
    public static String defaultIfBlank(String str, String defaultValue) {
        return hasText(str) ? str : defaultValue;
    }

    /**
     * Trim the string, returning null if the result is blank.
     *
     * @param str the string to trim
     * @return trimmed string with content, or null if blank
     */
    public static String trimToNull(String str) {
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}