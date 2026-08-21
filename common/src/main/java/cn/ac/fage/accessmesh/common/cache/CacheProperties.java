package cn.ac.fage.accessmesh.common.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一缓存配置属性类
 * <p>
 * 提供：
 * - 全局默认配置（default）
 * - Catalog 级运维覆盖（catalogs）
 * </p>
 * <p>
 * TTL 统一使用 {@link java.time.Duration}（Spring Boot Duration 文法，如 {@code 15s}、{@code 5m}），
 * 支持秒级精度；不保留分钟字段或兼容别名。
 * </p>
 *
 * <h3>配置示例：</h3>
 * <pre>
 * accessmesh:
 *   cache:
 *     enabled: true
 *     default-config:
 *       l1-ttl: 10m
 *       l1-maximum-size: 1000
 *       l2-ttl: 30m
 *     catalogs:
 *       "[perm:effective-roles]":
 *         l2-ttl: 10s
 *       "[gw:interface-snapshot]":
 *         l1-ttl: 15s
 *         l1-maximum-size: 50000
 * </pre>
 *
 * <h3>注意：</h3>
 * <p>
 * Catalog 代码中的 TTL/mode 默认值是主定义，YAML 配置仅用于运维调优覆盖。
 * </p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "accessmesh.cache")
public class CacheProperties {

    /**
     * 是否启用统一缓存模块
     */
    private boolean enabled = true;

    /**
     * 全局默认配置
     */
    private DefaultConfig defaultConfig = new DefaultConfig();

    /**
     * Catalog 级配置覆盖
     * <p>
     * Key: catalogCode（如 "perm:effective-roles"，YAML 中需加引号括号）
     * Value: 该 catalog 的配置覆盖
     * </p>
     */
    private Map<String, CatalogOverride> catalogs = new HashMap<>();

    /**
     * 全局默认配置
     */
    @Getter
    @Setter
    public static class DefaultConfig {

        /**
         * L1 缓存默认过期时间（Duration，秒级精度）
         */
        private Duration l1Ttl = Duration.ofMinutes(10);

        /**
         * L1 缓存默认最大容量
         */
        private long l1MaximumSize = 1000;

        /**
         * L2 缓存默认 TTL（Duration，秒级精度）
         */
        private Duration l2Ttl = Duration.ofMinutes(30);
    }

    /**
     * Catalog 级配置覆盖
     */
    @Getter
    @Setter
    public static class CatalogOverride {

        /**
         * L1 缓存过期时间覆盖（Duration，秒级精度）
         */
        private Duration l1Ttl;

        /**
         * L1 缓存最大容量覆盖
         */
        private Long l1MaximumSize;

        /**
         * L2 缓存 TTL 覆盖（Duration，秒级精度）
         */
        private Duration l2Ttl;
    }

    /**
     * 获取 catalog 的实际 L1 TTL
     * <p>
     * 优先级：catalog YAML 覆盖 > catalog 代码默认值 > 全局默认
     * </p>
     *
     * @param catalogCode catalog 编码
     * @param defaultValue catalog 代码中的默认值
     * @return 实际 TTL
     */
    public Duration getEffectiveL1Ttl(String catalogCode, Duration defaultValue) {
        CatalogOverride override = catalogs.get(catalogCode);
        if (override != null && override.getL1Ttl() != null) {
            return override.getL1Ttl();
        }
        return defaultValue != null && !defaultValue.isNegative() && !defaultValue.isZero()
            ? defaultValue
            : defaultConfig.getL1Ttl();
    }

    /**
     * 获取 catalog 的实际 L1 最大容量
     * <p>
     * 优先级：catalog YAML 覆盖 > catalog 代码默认值 > 全局默认
     * </p>
     *
     * @param catalogCode catalog 编码
     * @param defaultValue catalog 代码中的默认值
     * @return 实际最大容量
     */
    public long getEffectiveL1MaxSize(String catalogCode, long defaultValue) {
        CatalogOverride override = catalogs.get(catalogCode);
        if (override != null && override.getL1MaximumSize() != null) {
            return override.getL1MaximumSize();
        }
        return defaultValue > 0 ? defaultValue : defaultConfig.getL1MaximumSize();
    }

    /**
     * 获取 catalog 的实际 L2 TTL
     * <p>
     * 优先级：catalog YAML 覆盖 > catalog 代码默认值 > 全局默认
     * </p>
     *
     * @param catalogCode catalog 编码
     * @param defaultValue catalog 代码中的默认值
     * @return 实际 TTL
     */
    public Duration getEffectiveL2Ttl(String catalogCode, Duration defaultValue) {
        CatalogOverride override = catalogs.get(catalogCode);
        if (override != null && override.getL2Ttl() != null) {
            return override.getL2Ttl();
        }
        return defaultValue != null && !defaultValue.isNegative() && !defaultValue.isZero()
            ? defaultValue
            : defaultConfig.getL2Ttl();
    }
}
