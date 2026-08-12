package cn.ac.fage.accessmesh.access.admin.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置类
 * <p>
 * 配置admin-service的缓存管理器。
 * 使用Redis作为主缓存（L2分布式缓存），
 * Caffeine作为本地缓存配置。
 * </p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 创建缓存管理器
     * <p>
     * 使用Redis作为主缓存管理器。
     * 配置缓存过期时间、Key和Value序列化器。
     * </p>
     *
     * @param connectionFactory Redis连接工厂
     * @return Redis缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Redis缓存配置（L2）作为主缓存
        RedisCacheConfiguration redisConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))  // 缓存30分钟过期
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();  // 不缓存null值

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(redisConfig)
            .build();
    }

    /**
     * 创建Caffeine配置
     * <p>
     * 配置本地缓存的过期时间和最大容量。
     * 用于需要更快速访问的场景。
     * </p>
     *
     * @return Caffeine配置对象
     */
    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)  // 5分钟写入过期
            .maximumSize(500);  // 最大500条
    }
}