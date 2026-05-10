package cn.ac.fage.accessmesh.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通用缓存配置属性类
 * <p>
 * 提供 L1(Caffeine) 和 L2(Redis) 缓存的默认配置。
 * 可通过 application.yml 中的 `accessmesh.cache` 前缀进行自定义。
 * </p>
 */
@ConfigurationProperties(prefix = "accessmesh.cache")
public class CacheProperties {

    /** L1本地缓存配置 */
    private L1Config l1 = new L1Config();
    /** L2分布式缓存配置 */
    private L2Config l2 = new L2Config();

    /**
     * 获取L1缓存配置
     *
     * @return L1缓存配置对象
     */
    public L1Config getL1() {
        return l1;
    }

    /**
     * 设置L1缓存配置
     *
     * @param l1 L1缓存配置对象
     */
    public void setL1(L1Config l1) {
        this.l1 = l1;
    }

    /**
     * 获取L2缓存配置
     *
     * @return L2缓存配置对象
     */
    public L2Config getL2() {
        return l2;
    }

    /**
     * 设置L2缓存配置
     *
     * @param l2 L2缓存配置对象
     */
    public void setL2(L2Config l2) {
        this.l2 = l2;
    }

    /**
     * L1 (Caffeine) 缓存配置
     * <p>
     * 配置本地Caffeine缓存的最大容量和过期时间。
     * </p>
     */
    public static class L1Config {
        /**
         * L1 缓存最大容量
         */
        private long maximumSize = 1000;

        /**
         * L1 缓存过期时间（分钟）
         */
        private int expireMinutes = 10;

        /**
         * 获取L1缓存最大容量
         *
         * @return 最大容量
         */
        public long getMaximumSize() {
            return maximumSize;
        }

        /**
         * 设置L1缓存最大容量
         *
         * @param maximumSize 最大容量
         */
        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        /**
         * 获取L1缓存过期时间
         *
         * @return 过期时间（分钟）
         */
        public int getExpireMinutes() {
            return expireMinutes;
        }

        /**
         * 设置L1缓存过期时间
         *
         * @param expireMinutes 过期时间（分钟）
         */
        public void setExpireMinutes(int expireMinutes) {
            this.expireMinutes = expireMinutes;
        }
    }

    /**
     * L2 (Redis) 缓存配置
     * <p>
     * 配置Redis分布式缓存的过期时间。
     * </p>
     */
    public static class L2Config {
        /**
         * L2 缓存 TTL（分钟）
         */
        private int ttlMinutes = 30;

        /**
         * 获取L2缓存TTL
         *
         * @return TTL时间（分钟）
         */
        public int getTtlMinutes() {
            return ttlMinutes;
        }

        /**
         * 设置L2缓存TTL
         *
         * @param ttlMinutes TTL时间（分钟）
         */
        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }
}