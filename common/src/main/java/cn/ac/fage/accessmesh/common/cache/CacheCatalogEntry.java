package cn.ac.fage.accessmesh.common.cache;

import com.fasterxml.jackson.databind.JavaType;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * 类型化缓存描述符
 * <p>
 * 每种缓存只需一个静态常量，携带完整的配置信息：
 * - code: 缓存键中间段，如 "perm:effective-roles"
 * - mode: 缓存模式（L1_L2 / L2_ONLY / L1_ONLY）
 * - TTL 配置（L1/L2 各独立，java.time.Duration，秒级精度）
 * - valueType: Jackson JavaType，支持复杂泛型
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * public final class PermCacheCatalog {
 *     public static final CacheCatalogEntry<Set<Long>> EFFECTIVE_ROLES =
 *         CacheCatalogEntry.<Set<Long>>builder()
 *             .code("perm:effective-roles")
 *             .mode(CacheMode.L2_ONLY)
 *             .l2Ttl(Duration.ofSeconds(10))
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
     * L1 缓存 TTL
     * <p>
     * 仅 L1_L2 和 L1_ONLY 模式有效；秒级精度
     * </p>
     */
    private final Duration l1Ttl;

    /**
     * L1 缓存最大容量
     * <p>
     * 仅 L1_L2 和 L1_ONLY 模式有效
     * </p>
     */
    private final long l1MaxSize;

    /**
     * L2 缓存 TTL
     * <p>
     * 仅 L1_L2 和 L2_ONLY 模式有效；秒级精度
     * </p>
     */
    private final Duration l2Ttl;

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
        Duration l1Ttl,
        Long l1MaxSize,
        Duration l2Ttl,
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
        this.l1Ttl = l1Ttl != null ? l1Ttl : Duration.ofMinutes(10);
        this.l1MaxSize = l1MaxSize != null ? l1MaxSize : 1000L;
        this.l2Ttl = l2Ttl != null ? l2Ttl : Duration.ofMinutes(30);
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
