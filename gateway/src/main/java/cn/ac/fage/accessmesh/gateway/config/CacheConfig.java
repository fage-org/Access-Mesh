package cn.ac.fage.accessmesh.gateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 网关Caffeine本地缓存配置类
 * <p>
 * 配置权限校验的L1本地缓存，用于减少对permission-center的调用频率。
 * 缓存命中时直接返回结果，未命中时调用远程服务并缓存结果。
 * </p>
 */
@Configuration
public class CacheConfig {

    private final GatewayProperties gatewayProperties;

    /**
     * 构造缓存配置
     * <p>
     * 注入网关属性配置，获取缓存参数。
     * </p>
     *
     * @param gatewayProperties 网关配置属性
     */
    public CacheConfig(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    /**
     * 创建权限校验缓存实例
     * <p>
     * 根据配置创建Caffeine缓存实例：
     * - maximumSize: 最大缓存条目数
     * - expireAfterWrite: 写入后过期时间
     * - recordStats: 记录缓存统计信息用于监控
     * </p>
     *
     * @return Caffeine缓存实例
     */
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