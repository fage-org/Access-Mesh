package cn.ac.fage.accessmesh.permission.service.cache.impl;

import cn.ac.fage.accessmesh.common.cache.AbstractGenericCacheManager;
import cn.ac.fage.accessmesh.permission.config.PermCacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 条件规则缓存管理器
 * <p>
 * 用于缓存解析后的条件规则 JSON 数据。
 * </p>
 *
 * <h3>缓存键格式：</h3>
 * <pre>
 * perm:condition-rules:{tenantId}:{conditionId}
 * </pre>
 */
@Component
public class ConditionRulesCacheManager extends AbstractGenericCacheManager<Long, JsonNode> {

    private static final String NAMESPACE = "perm:condition-rules";
    private final PermCacheProperties cacheProperties;

    public ConditionRulesCacheManager(StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry,
                                      PermCacheProperties cacheProperties) {
        super(redisTemplate, objectMapper, meterRegistry);
        this.cacheProperties = cacheProperties;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public int getL1TtlMinutes() {
        return cacheProperties.getL1().getExpireMinutes();
    }

    @Override
    public int getL2TtlMinutes() {
        return cacheProperties.getL2().getTtlMinutes();
    }

    /**
     * 问题9：避免硬编码，从配置获取最大容量
     */
    @Override
    public long getL1MaximumSize() {
        // 条件规则数量通常不会太大，使用配置值
        return cacheProperties.getL1().getMaximumSize();
    }

    @Override
    public Class<JsonNode> getValueClass() {
        return JsonNode.class;
    }

    @Override
    protected String keyToString(Long key) {
        return String.valueOf(key);
    }

    /**
     * 根据条件 ID 失效缓存
     *
     * @param tenantId    租户 ID
     * @param conditionId 条件 ID
     */
    public void evictByConditionId(Long tenantId, Long conditionId) {
        evict(tenantId, conditionId);
    }

    /**
     * 批量根据条件 ID 失效缓存
     *
     * @param tenantId     租户 ID
     * @param conditionIds 条件 ID 集合
     */
    public void evictByConditionIds(Long tenantId, java.util.Set<Long> conditionIds) {
        evictBatch(tenantId, conditionIds);
    }
}