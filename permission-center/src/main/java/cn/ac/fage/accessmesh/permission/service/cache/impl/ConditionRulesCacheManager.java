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
 * 使用双层缓存（L1 Caffeine + L2 Redis）提高条件规则查询效率。
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

    /**
     * 构造条件规则缓存管理器
     * <p>
     * 注入Redis模板、JSON序列化器、指标注册器和缓存配置。
     * </p>
     *
     * @param redisTemplate   Redis字符串操作模板
     * @param objectMapper    JSON序列化工具
     * @param meterRegistry   Micrometer指标注册器
     * @param cacheProperties 缓存配置属性
     */
    public ConditionRulesCacheManager(StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry,
                                      PermCacheProperties cacheProperties) {
        super(redisTemplate, objectMapper, meterRegistry);
        this.cacheProperties = cacheProperties;
    }

    /**
     * 获取缓存命名空间
     * <p>
     * 返回缓存键的前缀命名空间，用于区分不同类型的缓存。
     * </p>
     *
     * @return 缓存命名空间 "perm:condition-rules"
     */
    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * 获取L1缓存过期时间
     * <p>
     * 从配置属性获取本地Caffeine缓存的过期时间。
     * </p>
     *
     * @return L1缓存过期时间（分钟）
     */
    @Override
    public int getL1TtlMinutes() {
        return cacheProperties.getL1().getExpireMinutes();
    }

    /**
     * 获取L2缓存过期时间
     * <p>
     * 从配置属性获取Redis分布式缓存的过期时间。
     * </p>
     *
     * @return L2缓存过期时间（分钟）
     */
    @Override
    public int getL2TtlMinutes() {
        return cacheProperties.getL2().getTtlMinutes();
    }

    /**
     * 获取L1缓存最大容量
     * <p>
     * 从配置属性获取本地缓存的最大容量。
     * 条件规则数量通常不会太大，使用配置值。
     * </p>
     *
     * @return L1缓存最大容量
     */
    @Override
    public long getL1MaximumSize() {
        // 条件规则数量通常不会太大，使用配置值
        return cacheProperties.getL1().getMaximumSize();
    }

    /**
     * 获取值类型
     * <p>
     * 返回缓存值的Java类型，用于JSON反序列化。
     * </p>
     *
     * @return JsonNode类型的Class对象
     */
    @Override
    public Class<JsonNode> getValueClass() {
        return JsonNode.class;
    }

    /**
     * 键转字符串
     * <p>
     * 将条件ID转换为字符串形式的缓存键。
     * </p>
     *
     * @param key 条件ID
     * @return 条件ID的字符串形式
     */
    @Override
    protected String keyToString(Long key) {
        return String.valueOf(key);
    }

    /**
     * 根据条件ID失效缓存
     * <p>
     * 使指定条件的缓存失效，触发重新加载。
     * </p>
     *
     * @param tenantId    租户ID
     * @param conditionId 条件ID
     */
    public void evictByConditionId(Long tenantId, Long conditionId) {
        evict(tenantId, conditionId);
    }

    /**
     * 批量根据条件ID失效缓存
     * <p>
     * 使多个条件的缓存同时失效，触发批量重新加载。
     * </p>
     *
     * @param tenantId     租户ID
     * @param conditionIds 条件ID集合
     */
    public void evictByConditionIds(Long tenantId, java.util.Set<Long> conditionIds) {
        evictBatch(tenantId, conditionIds);
    }
}