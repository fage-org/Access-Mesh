package cn.ac.fage.accessmesh.permission.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 权限缓存配置属性类
 * <p>
 * 配置权限数据的L1（本地缓存）和L2（分布式缓存）参数。
 * 通过perm.cache前缀的配置文件进行配置。
 * </p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "perm.cache")
public class PermCacheProperties {

    private L1Config l1 = new L1Config();
    private L2Config l2 = new L2Config();

    /**
     * L1缓存配置类
     * <p>
     * 配置Caffeine本地缓存的参数。
     * </p>
     */
    @Getter
    @Setter
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
    }

    /**
     * L2缓存配置类
     * <p>
     * 配置Redis分布式缓存的参数。
     * </p>
     */
    @Getter
    @Setter
    public static class L2Config {
        /**
         * 缓存存活时间（分钟）
         * <p>
         * 默认值：30分钟
         * </p>
         */
        private int ttlMinutes = 30;
    }
}