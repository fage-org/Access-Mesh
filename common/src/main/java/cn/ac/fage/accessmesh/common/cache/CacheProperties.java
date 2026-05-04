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

    private L1Config l1 = new L1Config();
    private L2Config l2 = new L2Config();

    public L1Config getL1() {
        return l1;
    }

    public void setL1(L1Config l1) {
        this.l1 = l1;
    }

    public L2Config getL2() {
        return l2;
    }

    public void setL2(L2Config l2) {
        this.l2 = l2;
    }

    /**
     * L1 (Caffeine) 缓存配置
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

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public int getExpireMinutes() {
            return expireMinutes;
        }

        public void setExpireMinutes(int expireMinutes) {
            this.expireMinutes = expireMinutes;
        }
    }

    /**
     * L2 (Redis) 缓存配置
     */
    public static class L2Config {
        /**
         * L2 缓存 TTL（分钟）
         */
        private int ttlMinutes = 30;

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }
}