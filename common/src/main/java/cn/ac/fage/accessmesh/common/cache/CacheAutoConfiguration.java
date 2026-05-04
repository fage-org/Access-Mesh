package cn.ac.fage.accessmesh.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 缓存自动配置类
 * <p>
 * 当满足以下条件时自动启用：
 * <ul>
 *   <li>Caffeine 和 Redis 相关类存在</li>
 *   <li>配置属性 accessmesh.cache.enabled 为 true（默认）</li>
 * </ul>
 * </p>
 */
@AutoConfiguration
@ConditionalOnClass({com.github.benmanes.caffeine.cache.Caffeine.class, StringRedisTemplate.class})
@ConditionalOnProperty(prefix = "accessmesh.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    /**
     * 缓存配置属性 Bean
     * <p>
     * 如果用户未自定义配置，使用默认值
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheProperties cacheProperties() {
        return new CacheProperties();
    }

    /**
     * 提供 ObjectMapper 用于 JSON 序列化
     * <p>
     * 如果用户已配置 ObjectMapper，则使用用户的配置
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper cacheObjectMapper() {
        return new ObjectMapper();
    }
}