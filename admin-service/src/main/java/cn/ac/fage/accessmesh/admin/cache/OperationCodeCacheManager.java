package cn.ac.fage.accessmesh.admin.cache;

import cn.ac.fage.accessmesh.common.cache.AbstractGenericCacheManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 操作权限ID缓存管理器
 * 缓存结构：tenantId -> opCode -> operationPermissionId
 * 使用双层缓存（L1 Caffeine + L2 Redis）确保多实例一致性
 */
@Component
public class OperationCodeCacheManager extends AbstractGenericCacheManager<Long, Map<String, Long>> {

    public OperationCodeCacheManager(StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry) {
        super(redisTemplate, objectMapper, meterRegistry);
    }

    @Override
    public String getNamespace() {
        return "admin:op_code";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Map<String, Long>> getValueClass() {
        // 使用 Map 作为值类型
        return (Class<Map<String, Long>>) (Class<?>) Map.class;
    }

    @Override
    protected String keyToString(Long key) {
        return String.valueOf(key);
    }

    @Override
    public long getL1MaximumSize() {
        return 100; // 每个租户缓存最多100个操作映射
    }

    @Override
    public int getL1TtlMinutes() {
        return 30; // L1 缓存30分钟
    }

    @Override
    public int getL2TtlMinutes() {
        return 60; // L2 缓存60分钟
    }
}