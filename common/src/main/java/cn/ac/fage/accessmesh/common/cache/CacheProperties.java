package cn.ac.fage.accessmesh.common.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通用缓存配置属性类
 * <p>
 * 提供 L1(Caffeine) 和 L2(Redis) 缓存的默认配置。
 * 可通过 application.yml 中的 `accessmesh.cache` 前缀进行自定义。
 * </p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "accessmesh.cache")
public class CacheProperties {

    /** L1本地缓存配置 */
    private L1Config l1 = new L1Config();
    /** L2分布式缓存配置 */
    private L2Config l2 = new L2Config();

    /**
     * L1 (Caffeine) 缓存配置
     * <p>
     * 配置本地Caffeine缓存的最大容量和过期时间。
     * </p>
     */
    @Getter
    @Setter
    public static class L1Config {
        /**
         * L1 缓存最大容量
         */
        private long maximumSize = 1000;

        /**
         * L1 缓存过期时间（分钟）
         */
        private int expireMinutes = 10;
    }

    /**
     * L2 (Redis) 缓存配置
     * <p>
     * 配置Redis分布式缓存的过期时间。
     * </p>
     */
    @Getter
    @Setter
    public static class L2Config {
        /**
         * L2 缓存 TTL（分钟）
         */
        private int ttlMinutes = 30;
    }
}