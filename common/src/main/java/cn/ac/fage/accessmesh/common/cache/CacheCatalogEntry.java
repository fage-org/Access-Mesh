package cn.ac.fage.accessmesh.common.cache;

import com.fasterxml.jackson.databind.JavaType;

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

    private CacheCatalogEntry(Builder<V> builder) {
        this.code = builder.code;
        this.mode = builder.mode;
        this.l1TtlMinutes = builder.l1TtlMinutes;
        this.l1MaxSize = builder.l1MaxSize;
        this.l2TtlMinutes = builder.l2TtlMinutes;
        this.valueType = builder.valueType;
    }

    public String getCode() {
        return code;
    }

    public CacheMode getMode() {
        return mode;
    }

    public int getL1TtlMinutes() {
        return l1TtlMinutes;
    }

    public long getL1MaxSize() {
        return l1MaxSize;
    }

    public int getL2TtlMinutes() {
        return l2TtlMinutes;
    }

    public JavaType getValueType() {
        return valueType;
    }

    /**
     * 创建 Builder
     *
     * @param <V> 缓存值类型
     * @return Builder 实例
     */
    public static <V> Builder<V> builder() {
        return new Builder<>();
    }

    /**
     * Builder 类
     */
    public static final class Builder<V> {

        private String code;
        private CacheMode mode = CacheMode.L1_L2;
        private int l1TtlMinutes = 10;
        private long l1MaxSize = 1000;
        private int l2TtlMinutes = 30;
        private JavaType valueType;

        public Builder<V> code(String code) {
            this.code = code;
            return this;
        }

        public Builder<V> mode(CacheMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder<V> l1TtlMinutes(int l1TtlMinutes) {
            this.l1TtlMinutes = l1TtlMinutes;
            return this;
        }

        public Builder<V> l1MaxSize(long l1MaxSize) {
            this.l1MaxSize = l1MaxSize;
            return this;
        }

        public Builder<V> l2TtlMinutes(int l2TtlMinutes) {
            this.l2TtlMinutes = l2TtlMinutes;
            return this;
        }

        public Builder<V> valueType(TypeRef<V> typeRef) {
            this.valueType = typeRef.getType();
            return this;
        }

        /**
         * 直接设置 JavaType（用于复杂类型）
         *
         * @param javaType Jackson JavaType
         * @return this
         */
        public Builder<V> valueType(JavaType javaType) {
            this.valueType = javaType;
            return this;
        }

        /**
         * 构建 CacheCatalogEntry
         * <p>
         * 必须设置 code 和 valueType
         * </p>
         *
         * @return CacheCatalogEntry 实例
         * @throws IllegalStateException 如果缺少必填字段
         */
        public CacheCatalogEntry<V> build() {
            if (code == null || code.isEmpty()) {
                throw new IllegalStateException("code is required");
            }
            if (valueType == null) {
                throw new IllegalStateException("valueType is required");
            }
            return new CacheCatalogEntry<>(this);
        }
    }
}