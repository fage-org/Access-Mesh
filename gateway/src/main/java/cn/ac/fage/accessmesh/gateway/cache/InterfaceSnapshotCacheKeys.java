package cn.ac.fage.accessmesh.gateway.cache;

/**
 * Gateway 接口快照缓存 key 工具（T-PERM-006）
 * <p>
 * 缓存维度固定为 {@code (tenantId, subjectTypeCode, userId, serviceCode)}，与
 * {@code PermissionFilter} 读写和 Redis 失效订阅器解析保持同源。
 * </p>
 */
public final class InterfaceSnapshotCacheKeys {

    private static final String PREFIX = "perm:snapshot:";

    private InterfaceSnapshotCacheKeys() {
    }

    public static String build(Long tenantId, String subjectTypeCode, Long userId, String serviceCode) {
        return PREFIX + tenantId + ":" + subjectTypeCode + ":" + userId + ":" + serviceCode;
    }

    static ParsedKey parse(String key) {
        if (key == null || !key.startsWith(PREFIX)) {
            return null;
        }
        String raw = key.substring(PREFIX.length());
        String[] parts = raw.split(":", 4);
        if (parts.length != 4) {
            return null;
        }
        Long tenantId = parseLong(parts[0]);
        Long userId = parseLong(parts[2]);
        String serviceCode = parts[3];
        if (tenantId == null || userId == null || serviceCode == null || serviceCode.isBlank()) {
            return null;
        }
        return new ParsedKey(tenantId, parts[1], userId, serviceCode);
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record ParsedKey(Long tenantId, String subjectTypeCode, Long userId, String serviceCode) {
    }
}
