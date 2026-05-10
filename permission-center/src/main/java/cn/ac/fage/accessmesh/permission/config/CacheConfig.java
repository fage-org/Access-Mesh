package cn.ac.fage.accessmesh.permission.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置类
 * <p>
 * 配置Caffeine本地缓存管理器。
 * 用于权限数据的L1缓存，提供快速的本地缓存访问。
 * </p>
 */
@Configuration
public class CacheConfig {

    private final PermCacheProperties permCacheProperties;

    /**
     * 构造缓存配置
     * <p>
     * 注入权限缓存属性配置。
     * </p>
     *
     * @param permCacheProperties 权限缓存属性配置
     */
    public CacheConfig(PermCacheProperties permCacheProperties) {
        this.permCacheProperties = permCacheProperties;
    }

    /**
     * 创建主缓存管理器
     * <p>
     * 创建Caffeine缓存管理器作为主缓存。
     * 配置缓存大小和过期时间，启用统计记录。
     * </p>
     *
     * @return Caffeine缓存管理器
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        PermCacheProperties.L1Config l1 = permCacheProperties.getL1();
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(l1.getMaximumSize())
            .expireAfterWrite(l1.getExpireMinutes(), TimeUnit.MINUTES)
            .recordStats());
        return manager;
    }
}