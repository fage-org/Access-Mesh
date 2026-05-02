package cn.ac.fage.accessmesh.permission.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    private final PermCacheProperties permCacheProperties;

    public CacheConfig(PermCacheProperties permCacheProperties) {
        this.permCacheProperties = permCacheProperties;
    }

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
