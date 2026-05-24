package cn.ac.fage.accessmesh.common.cache;

import com.fasterxml.jackson.databind.JavaType;
import lombok.Builder;
import lombok.Getter;

/**
 * 类型化缓存描述符
 * <p>
 * 每种缓存只需一个静态常量，携带完整的配置信息：
 * - code: 缓存键中间段，如 "perm:effective-roles"
 * - mode: 缓存模式（L1_L2 / L2_ONLY / L1_ONLY）
 * - TTL 配置（L1/L2 各独立）
 * - valueType: Jackson JavaType，支持复杂泛型
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * public final class PermCacheCatalog {
 *     public static final CacheCatalogEntry<Set<Long>> EFFECTIVE_ROLES =
 *         CacheCatalogEntry.<Set<Long>>builder()
 *             .code("perm:effective-roles")
 *             .mode(CacheMode.L1_L2)
 *             .l1TtlMinutes(5)
 *             .l1MaxSize(2000)
 *             .l2TtlMinutes(30)
 *             .valueType(new TypeRef<Set<Long>>() {})
 *             .build();
 * }
 * }</pre>
 *
 * @param <V> 缓存值类型
 */
@Getter
public final class CacheCatalogEntry<V> {

    /**
     * 缓存键中间段
     * <p>
     * 格式建议：业务域:数据类型，如 "perm:effective-roles"、"admin:dict-types"
     * </p>
     */
    private final String code;

    /**
     * 缓存模式
     */
    private final CacheMode mode;

    /**
     * L1 缓存 TTL（分钟）
     * <p>
     * 仅 L1_L2 和 L1_ONLY 模式有效
     * </p>
     */
    private final int l1TtlMinutes;

    /**
     * L1 缓存最大容量
     * <p>
     * 仅 L1_L2 和 L1_ONLY 模式有效
     * </p>
     */
    private final long l1MaxSize;

    /**
     * L2 缓存 TTL（分钟）
     * <p>
     * 仅 L1_L2 和 L2_ONLY 模式有效
     * </p>
     */
    private final int l2TtlMinutes;

    /**
     * 值类型
     * <p>
     * Jackson JavaType，支持复杂泛型如 Set<Long>、Map<String, Integer>
     * </p>
     */
    private final JavaType valueType;

    @Builder(builderClassName = "CacheCatalogEntryBuilder", builderMethodName = "builder")
    private CacheCatalogEntry(
        String code,
        CacheMode mode,
        Integer l1TtlMinutes,
        Long l1MaxSize,
        Integer l2TtlMinutes,
        JavaType valueType
    ) {
        if (code == null || code.isEmpty()) {
            throw new IllegalStateException("code is required");
        }
        if (valueType == null) {
            throw new IllegalStateException("valueType is required");
        }
        this.code = code;
        this.mode = mode != null ? mode : CacheMode.L1_L2;
        this.l1TtlMinutes = l1TtlMinutes != null ? l1TtlMinutes : 10;
        this.l1MaxSize = l1MaxSize != null ? l1MaxSize : 1000L;
        this.l2TtlMinutes = l2TtlMinutes != null ? l2TtlMinutes : 30;
        this.valueType = valueType;
    }

    /**
     * Builder 扩展方法：设置值类型（使用 TypeRef）
     *
     * @param typeRef TypeRef 实例
     * @return this
     */
    public static class CacheCatalogEntryBuilder<V> {
        public CacheCatalogEntryBuilder<V> l1MaxSize(long l1MaxSize) {
            this.l1MaxSize = l1MaxSize;
            return this;
        }

        /**
         * 设置值类型（使用 TypeRef 便捷方法）
         *
         * @param typeRef TypeRef 实例
         * @return Builder 实例
         */
        public CacheCatalogEntryBuilder<V> valueType(TypeRef<V> typeRef) {
            this.valueType = typeRef.getType();
            return this;
        }
    }
}