package cn.ac.fage.accessmesh.gateway.cache;

/**
 * Gateway 接口快照缓存 identifier 工具（T-PERM-006 / T-ACCESS-008）
 * <p>
 * 快照维度固定为 {@code (tenantId, subjectTypeCode, userId, serviceCode)}。
 * T-ACCESS-008 迁移统一 CacheService 后，tenantId 由 CacheService 键前缀承载，
 * identifier = {@code subjectTypeCode:userId:serviceCode}；本工具的
 * {@link #build} 与 {@link #parse} 供 PermissionFilter 读写与失效器枚举保持同源。
 * </p>
 */
public final class InterfaceSnapshotCacheKeys {

    private InterfaceSnapshotCacheKeys() {
    }

    /**
     * 构建快照缓存 identifier。
     *
     * @param subjectTypeCode 主体类型编码
     * @param userId 用户ID
     * @param serviceCode 服务编码
     * @return identifier = subjectTypeCode:userId:serviceCode
     */
    public static String build(String subjectTypeCode, Long userId, String serviceCode) {
        return subjectTypeCode + ":" + userId + ":" + serviceCode;
    }

    /**
     * 解析 identifier。
     *
     * @param identifier 快照缓存 identifier
     * @return 解析结果；格式非法返回 null
     */
    static ParsedKey parse(String identifier) {
        if (identifier == null) {
            return null;
        }
        String[] parts = identifier.split(":", 3);
        if (parts.length != 3) {
            return null;
        }
        Long userId = parseLong(parts[1]);
        String subjectTypeCode = parts[0];
        String serviceCode = parts[2];
        if (userId == null || subjectTypeCode.isBlank() || serviceCode == null || serviceCode.isBlank()) {
            return null;
        }
        return new ParsedKey(subjectTypeCode, userId, serviceCode);
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record ParsedKey(String subjectTypeCode, Long userId, String serviceCode) {
    }
}
