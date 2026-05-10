package cn.ac.fage.accessmesh.permission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 权限缓存配置属性类
 * <p>
 * 配置权限数据的L1（本地缓存）和L2（分布式缓存）参数。
 * 通过perm.cache前缀的配置文件进行配置。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "perm.cache")
public class PermCacheProperties {

    private L1Config l1 = new L1Config();
    private L2Config l2 = new L2Config();

    /**
     * 获取L1缓存配置
     * <p>
     * L1缓存为本地Caffeine缓存，提供快速访问。
     * </p>
     *
     * @return L1缓存配置对象
     */
    public L1Config getL1() { return l1; }

    /**
     * 设置L1缓存配置
     *
     * @param l1 L1缓存配置对象
     */
    public void setL1(L1Config l1) { this.l1 = l1; }

    /**
     * 获取L2缓存配置
     * <p>
     * L2缓存为Redis分布式缓存，用于缓存共享和持久化。
     * </p>
     *
     * @return L2缓存配置对象
     */
    public L2Config getL2() { return l2; }

    /**
     * 设置L2缓存配置
     *
     * @param l2 L2缓存配置对象
     */
    public void setL2(L2Config l2) { this.l2 = l2; }

    /**
     * L1缓存配置类
     * <p>
     * 配置Caffeine本地缓存的参数。
     * </p>
     */
    public static class L1Config {
        /**
         * 最大缓存条目数量
         * <p>
         * 默认值：1000
         * </p>
         */
        private int maximumSize = 1000;

        /**
         * 缓存过期时间（分钟）
         * <p>
         * 默认值：10分钟
         * </p>
         */
        private int expireMinutes = 10;

        public int getMaximumSize() { return maximumSize; }
        public void setMaximumSize(int maximumSize) { this.maximumSize = maximumSize; }

        public int getExpireMinutes() { return expireMinutes; }
        public void setExpireMinutes(int expireMinutes) { this.expireMinutes = expireMinutes; }
    }

    /**
     * L2缓存配置类
     * <p>
     * 配置Redis分布式缓存的参数。
     * </p>
     */
    public static class L2Config {
        /**
         * 缓存存活时间（分钟）
         * <p>
         * 默认值：30分钟
         * </p>
         */
        private int ttlMinutes = 30;

        public int getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    }
}