package cn.ac.fage.accessmesh.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * 缓存键工具类
 * <p>
 * 统一生成 tenant-first 格式的缓存键：{tenantId}:{catalogCode}:{identifier}
 * </p>
 *
 * <h3>键格式示例：</h3>
 * <pre>
 * 1:perm:effective-roles:456
 * 1:perm:role-perm-snapshot:789
 * 1:admin:dict-types:en
 * </pre>
 *
 * <h3>键验证规则：</h3>
 * <ul>
 *   <li>最大长度 500 字符</li>
 *   <li>禁止控制字符（\x00-\x1F, \x7F）</li>
 *   <li>tenantId 和 identifier 不能为空</li>
 * </ul>
 */
public final class CacheKeyUtil {

    private static final Logger log = LoggerFactory.getLogger(CacheKeyUtil.class);

    private static final int MAX_KEY_LENGTH = 500;
    private static final Pattern ILLEGAL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final String SEPARATOR = ":";

    private CacheKeyUtil() {
    }

    /**
     * 构建完整缓存键
     * <p>
     * 格式：{tenantId}:{catalogCode}:{identifier}
     * </p>
     *
     * @param tenantId    租户ID
     * @param catalogCode 缓存目录编码（如 "perm:effective-roles"）
     * @param identifier  业务标识（如用户ID、角色ID）
     * @return 完整缓存键
     * @throws IllegalArgumentException 如果参数无效
     */
    public static String build(Long tenantId, String catalogCode, Object identifier) {
        validateTenantId(tenantId);
        validateCatalogCode(catalogCode);
        validateIdentifier(identifier);

        String key = tenantId + SEPARATOR + catalogCode + SEPARATOR + identifier;
        return validateAndCleanKey(key);
    }

    /**
     * 批量构建缓存键
     *
     * @param tenantId    租户ID
     * @param catalogCode 缓存目录编码
     * @param identifiers 业务标识集合
     * @return 键集合
     */
    public static java.util.Set<String> buildBatch(Long tenantId, String catalogCode,
                                                    java.util.Set<?> identifiers) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (Object id : identifiers) {
            keys.add(build(tenantId, catalogCode, id));
        }
        return keys;
    }

    /**
     * 构建 SCAN 模式匹配键
     * <p>
     * 用于批量删除某租户某目录下的所有缓存
     * </p>
     *
     * @param tenantId    租户ID
     * @param catalogCode 缓存目录编码
     * @return SCAN 模式，如 "1:perm:effective-roles:*"
     */
    public static String buildScanPattern(Long tenantId, String catalogCode) {
        validateTenantId(tenantId);
        validateCatalogCode(catalogCode);
        return tenantId + SEPARATOR + catalogCode + SEPARATOR + "*";
    }

    /**
     * 构建 catalog 级 SCAN 模式匹配键（跨租户全量失效）
     * <p>
     * 宽松超集模式：SCAN 结果必须再经 {@link #belongsToCatalog} 按完整键结构
     * 精确过滤（glob 无法约束 catalogCode 紧跟 tenantId 段，其他目录 identifier
     * 内嵌本目录编码的键也会被 SCAN 命中）。
     * </p>
     *
     * @param catalogCode 缓存目录编码
     * @return SCAN 模式，如 "*:perm:effective-roles:*"
     */
    public static String buildCatalogPattern(String catalogCode) {
        validateCatalogCode(catalogCode);
        return "*" + SEPARATOR + catalogCode + SEPARATOR + "*";
    }

    /**
     * 判断完整缓存键是否属于指定目录（跨租户清理的精确过滤）
     * <p>
     * 按完整键结构校验：首段必须是数字租户ID，且 catalogCode 紧跟租户段之后、
     * 以分隔符结束——防止其他目录 identifier 中内嵌本目录编码的键被误清
     * （如清理 {@code test:catalog-wide} 时 {@code 1:test:other:x:test:catalog-wide:y}
     * 不得命中）。精度与租户级 SCAN 前缀（{@code tenant:code:*}）一致。
     * </p>
     *
     * @param fullKey 完整缓存键（{tenantId}:{catalogCode}:{identifier}）
     * @param catalogCode 缓存目录编码
     * @return 是否属于该目录
     */
    public static boolean belongsToCatalog(String fullKey, String catalogCode) {
        if (fullKey == null || catalogCode == null || catalogCode.isEmpty()) {
            return false;
        }
        int tenantEnd = fullKey.indexOf(SEPARATOR);
        if (tenantEnd <= 0) {
            return false;
        }
        String tenant = fullKey.substring(0, tenantEnd);
        try {
            Long.parseLong(tenant);
        } catch (NumberFormatException e) {
            return false;
        }
        int codeStart = tenantEnd + 1;
        int codeEnd = codeStart + catalogCode.length();
        return fullKey.length() > codeEnd
            && fullKey.charAt(codeEnd) == SEPARATOR.charAt(0)
            && fullKey.regionMatches(codeStart, catalogCode, 0, catalogCode.length());
    }

    /**
     * 构建租户级 SCAN 模式
     * <p>
     * 用于删除某租户所有缓存
     * </p>
     *
     * @param tenantId 租户ID
     * @return SCAN 模式，如 "1:*"
     */
    public static String buildTenantPattern(Long tenantId) {
        validateTenantId(tenantId);
        return tenantId + SEPARATOR + "*";
    }

    // ==================== 验证方法 ====================

    private static void validateTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
    }

    private static void validateCatalogCode(String catalogCode) {
        if (catalogCode == null || catalogCode.isEmpty()) {
            throw new IllegalArgumentException("catalogCode cannot be null or empty");
        }
        if (catalogCode.contains(SEPARATOR + SEPARATOR)) {
            throw new IllegalArgumentException("catalogCode cannot contain consecutive separators");
        }
    }

    private static void validateIdentifier(Object identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier cannot be null");
        }
        String idStr = identifier.toString();
        if (idStr.isEmpty()) {
            throw new IllegalArgumentException("identifier cannot be empty string");
        }
    }

    private static String validateAndCleanKey(String key) {
        String cleaned = ILLEGAL_CHAR_PATTERN.matcher(key).replaceAll("");

        if (cleaned.length() > MAX_KEY_LENGTH) {
            String hash = Integer.toHexString(cleaned.hashCode());
            cleaned = cleaned.substring(0, MAX_KEY_LENGTH - hash.length() - 1) + SEPARATOR + hash;
            log.warn("Cache key truncated: original length={}, truncated to {}",
                key.length(), cleaned.length());
        }

        return cleaned;
    }
}