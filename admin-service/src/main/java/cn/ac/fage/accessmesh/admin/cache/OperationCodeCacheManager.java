package cn.ac.fage.accessmesh.admin.cache;

import cn.ac.fage.accessmesh.common.cache.AbstractGenericCacheManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 操作权限ID缓存管理器
 * <p>
 * 缓存结构：tenantId -> opCode -> operationPermissionId。
 * 使用双层缓存（L1 Caffeine + L2 Redis）确保多实例一致性。
 * 用于加速操作码到操作权限ID的映射查询。
 * </p>
 */
@Component
public class OperationCodeCacheManager extends AbstractGenericCacheManager<Long, Map<String, Long>> {

    /**
     * 构造操作权限ID缓存管理器
     * <p>
     * 注入Redis模板、JSON序列化器和指标注册器。
     * </p>
     *
     * @param redisTemplate  Redis字符串操作模板
     * @param objectMapper   JSON序列化工具
     * @param meterRegistry  Micrometer指标注册器
     */
    public OperationCodeCacheManager(StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry) {
        super(redisTemplate, objectMapper, meterRegistry);
    }

    /**
     * 获取缓存命名空间
     * <p>
     * 返回缓存键的前缀命名空间，用于区分不同类型的缓存。
     * </p>
     *
     * @return 缓存命名空间 "admin:op_code"
     */
    @Override
    public String getNamespace() {
        return "admin:op_code";
    }

    /**
     * 获取值类型
     * <p>
     * 返回缓存值的Java类型，用于反序列化。
     * </p>
     *
     * @return Map类型的Class对象
     */
    @Override
    @SuppressWarnings("unchecked")
    public Class<Map<String, Long>> getValueClass() {
        // 使用 Map 作为值类型
        return (Class<Map<String, Long>>) (Class<?>) Map.class;
    }

    /**
     * 键转字符串
     * <p>
     * 将租户ID转换为字符串形式的缓存键。
     * </p>
     *
     * @param key 租户ID
     * @return 租户ID的字符串形式
     */
    @Override
    protected String keyToString(Long key) {
        return String.valueOf(key);
    }

    /**
     * 获取L1缓存最大容量
     * <p>
     * 每个租户缓存最多100个操作映射。
     * </p>
     *
     * @return L1缓存最大容量100
     */
    @Override
    public long getL1MaximumSize() {
        return 100; // 每个租户缓存最多100个操作映射
    }

    /**
     * 获取L1缓存过期时间
     * <p>
     * 本地Caffeine缓存30分钟后过期。
     * </p>
     *
     * @return L1缓存过期时间30分钟
     */
    @Override
    public int getL1TtlMinutes() {
        return 30; // L1 缓存30分钟
    }

    /**
     * 获取L2缓存过期时间
     * <p>
     * Redis分布式缓存60分钟后过期。
     * </p>
     *
     * @return L2缓存过期时间60分钟
     */
    @Override
    public int getL2TtlMinutes() {
        return 60; // L2 缓存60分钟
    }
}