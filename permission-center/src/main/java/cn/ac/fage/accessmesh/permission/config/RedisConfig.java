package cn.ac.fage.accessmesh.permission.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 * <p>
 * 配置RedisTemplate用于权限缓存的L2分布式缓存。
 * 使用String序列化器作为Key序列化器，Jackson序列化器作为Value序列化器。
 * </p>
 */
@Configuration
public class RedisConfig {

    /**
     * 创建RedisTemplate实例
     * <p>
     * 配置Key和Value的序列化器：
     * - Key使用StringRedisSerializer，便于查看和管理
     * - Value使用GenericJackson2JsonRedisSerializer，支持复杂对象序列化
     * </p>
     *
     * @param connectionFactory Redis连接工厂
     * @return 配置好的RedisTemplate实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建ObjectMapper实例
     * <p>
     * 用于JSON序列化和反序列化操作。
     * </p>
     *
     * @return ObjectMapper实例
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}