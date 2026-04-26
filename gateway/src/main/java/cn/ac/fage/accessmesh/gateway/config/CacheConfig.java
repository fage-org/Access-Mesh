package cn.ac.fage.accessmesh.gateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine L1 cache configuration for gateway permission checks.
 */
@Configuration
public class CacheConfig {

    private final GatewayProperties gatewayProperties;

    public CacheConfig(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Bean
    public Cache<String, Boolean> permissionCheckCache() {
        GatewayProperties.Cache.L1 l1 = gatewayProperties.getCache().getL1();
        return Caffeine.newBuilder()
            .maximumSize(l1.getMaxSize())
            .expireAfterWrite(l1.getTtlSeconds(), TimeUnit.SECONDS)
            .recordStats()
            .build();
    }
}
