# dual-layer-cache-framework

双层缓存框架（L1 Caffeine + L2 Redis）的统一使用规范。

## 概述

**位置**: `common/src/main/java/cn/ac/fage/accessmesh/common/cache/`

**核心组件**:
- `GenericCacheManager<K, V>` - 通用缓存管理器接口
- `AbstractGenericCacheManager<K, V>` - 抽象实现类
- `CacheProperties` - L1/L2 配置属性
- `CacheAutoConfiguration` - 自动配置类

## 缓存规范

### 键格式
```
namespace:tenantId:key
```

示例：
- `perm:condition:rules:1:123` - 租户1的条件规则123
- `perm:user:effective-roles:1:456` - 租户1的用户456的有效角色

### 操作顺序

| 操作 | 顺序 | 原因 |
|------|------|------|
| **写入** | 先 L2 后 L1 | 确保 Redis 优先，分布式一致性 |
| **失效** | 先 L2 后 L1 | 防止竞态条件（L1 清除但 L2 还有旧数据） |
| **读取** | 先 L1 → L2 → Loader | 本地优先，减少网络开销 |

### TTL 配置

| 层级 | 默认 TTL | 说明 |
|------|----------|------|
| L1 (Caffeine) | 10-30 分钟 | 本地缓存，快速过期 |
| L2 (Redis) | 30-60 分钟 | 分布式缓存，较长有效期 |
| 空值/失败标记 | TTL 的 50% | 防止缓存穿透，较短有效期 |

## API

### 接口定义

```java
public interface GenericCacheManager<K, V> {
    // 配置方法
    String getNamespace();
    int getL1TtlMinutes();
    int getL2TtlMinutes();
    long getL1MaximumSize();
    Class<V> getValueClass();

    // 单条操作
    V get(Long tenantId, K key, BiFunction<Long, K, V> loader);
    void put(Long tenantId, K key, V value);
    void evict(Long tenantId, K key);

    // 批量操作
    Map<K, V> getBatch(Long tenantId, Set<K> keys, 
                       BiFunction<Long, Set<K>, Map<K, V>> loader);
    void putBatch(Long tenantId, Map<K, V> data);
    void evictBatch(Long tenantId, Set<K> keys);

    // 全量失效
    void evictAll(Long tenantId);

    // 辅助方法
    String buildCacheKey(Long tenantId, K key);
}
```

### 使用示例

```java
// 1. 创建具体实现类
@Component
public class ConditionRulesCacheManager 
    extends AbstractGenericCacheManager<Long, JsonNode> {

    private final CacheProperties cacheProperties;

    public ConditionRulesCacheManager(
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            CacheProperties cacheProperties) {
        super(redisTemplate, objectMapper, meterRegistry);
        this.cacheProperties = cacheProperties;
    }

    @Override
    public String getNamespace() {
        return "perm:condition:rules";
    }

    @Override
    public Class<JsonNode> getValueClass() {
        return JsonNode.class;
    }

    @Override
    public int getL1TtlMinutes() {
        return cacheProperties.getL1().getExpireMinutes();
    }

    @Override
    public int getL2TtlMinutes() {
        return cacheProperties.getL2().getTtlMinutes();
    }

    @Override
    public long getL1MaximumSize() {
        return cacheProperties.getL1().getMaximumSize();
    }
}
```

```java
// 2. 在业务服务中使用
@Service
public class PermissionConditionDomainServiceImpl {

    private final ConditionRulesCacheManager rulesCacheManager;

    // 单条查询
    public JsonNode getRules(Long tenantId, Long conditionId) {
        return rulesCacheManager.get(tenantId, conditionId, (tid, cid) -> {
            PermissionCondition cond = conditionMapper.selectOneById(cid);
            if (cond == null) return null;
            try {
                return objectMapper.readTree(cond.getConditionRules());
            } catch (Exception e) {
                log.warn("Failed to parse conditionRules: {}", cid);
                return null;
            }
        });
    }

    // 批量查询
    public Map<Long, JsonNode> getRulesBatch(Long tenantId, Set<Long> conditionIds) {
        return rulesCacheManager.getBatch(tenantId, conditionIds, (tid, ids) -> {
            List<PermissionCondition> conditions = conditionMapper.selectListByIds(ids);
            Map<Long, JsonNode> result = new HashMap<>();
            for (PermissionCondition cond : conditions) {
                try {
                    result.put(cond.getId(), objectMapper.readTree(cond.getConditionRules()));
                } catch (Exception e) {
                    log.warn("Failed to parse: {}", cond.getId());
                }
            }
            return result;
        });
    }

    // 失效缓存
    public void evictConditionCache(Long tenantId, Long conditionId) {
        rulesCacheManager.evict(tenantId, conditionId);
    }

    // 批量失效
    public void evictConditionCacheBatch(Long tenantId, Set<Long> conditionIds) {
        rulesCacheManager.evictBatch(tenantId, conditionIds);
    }
}
```

## 关键特性

### 1. 空值缓存（防止穿透）

```java
// 空值使用 NULL_MARKER 标记，较短 TTL
protected static final String NULL_MARKER = "__NULL__";

// 当 loader 返回 null 时，缓存空值标记
if (value == null) {
    putNullMarker(tenantId, key);  // TTL = L2Ttl / 2
}
```

### 2. 解析失败标记（防止重复解析）

```java
// JSON 解析失败时缓存失败标记
protected static final String PARSE_FAILED_MARKER = "__PARSE_FAILED__";

try {
    return objectMapper.readValue(json, valueType);
} catch (JsonProcessingException e) {
    // 缓存失败标记，避免重复尝试解析无效 JSON
    redisTemplate.opsForValue().set(fullKey, PARSE_FAILED_MARKER, 
        Duration.ofMinutes(getL2TtlMinutes() / 2));
    return null;
}
```

### 3. 键验证（防止注入和超长键）

```java
private static final int MAX_KEY_LENGTH = 500;
private static final Pattern ILLEGAL_CHAR_PATTERN = 
    Pattern.compile("[\\x00-\\x1F\\x7F]");  // 控制字符

protected String validateAndCleanKey(String key) {
    // 移除控制字符
    String cleaned = ILLEGAL_CHAR_PATTERN.matcher(key).replaceAll("");
    // 长度限制（超长键使用哈希后缀）
    if (cleaned.length() > MAX_KEY_LENGTH) {
        String hash = Integer.toHexString(cleaned.hashCode());
        cleaned = cleaned.substring(0, MAX_KEY_LENGTH - hash.length() - 1) + ":" + hash;
    }
    return cleaned;
}
```

### 4. Redis 异常处理（降级策略）

```java
// 所有 L2 操作都有 try-catch
protected V getFromL2(String fullKey) {
    try {
        String json = redisTemplate.opsForValue().get(fullKey);
        if (json == null) return null;
        return objectMapper.readValue(json, valueType);
    } catch (Exception e) {
        log.error("L2 cache get failed: {}", fullKey, e);
        incrementCounter(l2ErrorCounter);
        return null;  // 降级：返回 null，依赖 L1 或 Loader
    }
}
```

### 5. SCAN 替代 KEYS（生产安全）

```java
// evictAll 使用 SCAN 分批删除，避免阻塞 Redis
public void evictAll(Long tenantId) {
    String pattern = getNamespace() + ":" + tenantId + ":*";
    ScanOptions options = ScanOptions.scanOptions()
        .match(pattern)
        .count(100)  // 每次扫描 100 个键
        .build();
    
    List<String> keysToDelete = new ArrayList<>();
    try (var cursor = redisTemplate.scan(options)) {
        while (cursor.hasNext()) {
            keysToDelete.add(cursor.next());
            if (keysToDelete.size() >= 100) {
                redisTemplate.delete(keysToDelete);
                keysToDelete.clear();
            }
        }
        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }
    }
    l1Cache.invalidateAll();
}
```

### 6. Pipeline 批量写入（性能优化）

```java
// 批量写入使用 Pipeline，一次网络往返
protected void batchPutToL2(Map<String, V> data) {
    long ttlSeconds = getL2TtlMinutes() * 60;
    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
        for (Map.Entry<String, V> entry : data.entrySet()) {
            byte[] keyBytes = entry.getKey().getBytes();
            byte[] valueBytes = objectMapper.writeValueAsBytes(entry.getValue());
            connection.setEx(keyBytes, ttlSeconds, valueBytes);  // 带 TTL
        }
        return null;
    });
}
```

### 7. Micrometer 监控指标

```java
// 自动注册的监控指标
- cache.l1.hits          # L1 命中次数
- cache.l1.misses        # L1 未命中次数
- cache.l1.size          # L1 缓存大小
- cache.l1.hit_rate      # L1 命中率
- cache.l2.hits          # L2 命中次数
- cache.l2.misses        # L2 未命中次数
- cache.l2.errors        # L2 错误次数
- cache.l2.read.duration # L2 读延迟
- cache.l2.write.duration # L2 写延迟
```

## 配置

### application.yml

```yaml
accessmesh:
  cache:
    l1:
      maximum-size: 10000      # L1 最大容量
      expire-minutes: 10       # L1 过期时间（分钟）
    l2:
      ttl-minutes: 30          # L2 过期时间（分钟）
```

### pom.xml 依赖

```xml
<!-- common 模块已包含，其他模块只需依赖 common -->
<dependency>
    <groupId>cn.ac.fage.accessmesh</groupId>
    <artifactId>common</artifactId>
</dependency>
```

## 失效触发时机

**必须在数据变更时主动失效缓存**：

```java
@Service
public class ConditionManageServiceImpl {

    private final PermissionConditionDomainService conditionDomainService;

    @Transactional(rollbackFor = Exception.class)
    public ConditionResp updateCondition(Long tenantId, ConditionUpdateReq req, Long operatorId) {
        // ... 更新数据库
        conditionMapper.update(condition);
        
        // 失效缓存（事务提交后）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                conditionDomainService.evictConditionCache(tenantId, req.conditionId());
            }
        });
        
        return toConditionResp(condition);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConditionsByIds(Long tenantId, Set<Long> conditionIds, Long operatorId) {
        // ... 批量删除
        conditionMapper.softDeleteBatch(conditionIds);
        
        // 批量失效缓存
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                conditionDomainService.evictConditionCacheBatch(tenantId, conditionIds);
            }
        });
    }
}
```

## 方法选择

| 场景 | 方法 | 说明 |
|------|------|------|
| 单条查询，可能需要加载 | `get(tenantId, key, loader)` | 自动加载并缓存 |
| 单条查询，不加载 | `getOnly(tenantId, key)` | 仅查缓存，不触发加载 |
| 批量查询 | `getBatch(tenantId, keys, loader)` | 批量加载，减少网络往返 |
| 单条写入 | `put(tenantId, key, value)` | 同时写 L1 和 L2 |
| 批量写入 | `putBatch(tenantId, data)` | Pipeline 优化 |
| 单条失效 | `evict(tenantId, key)` | 先 L2 后 L1 |
| 批量失效 | `evictBatch(tenantId, keys)` | 批量删除 L2，循环失效 L1 |
| 全量失效 | `evictAll(tenantId)` | SCAN 分批删除 |

## 相关文件

- `common/cache/GenericCacheManager.java` - 接口定义
- `common/cache/AbstractGenericCacheManager.java` - 抽象实现（约 800 行）
- `common/cache/CacheProperties.java` - 配置属性
- `common/cache/CacheAutoConfiguration.java` - 自动配置
- `permission-center/service/cache/impl/ConditionRulesCacheManager.java` - 具体实现示例
- `permission-center/service/domain/impl/PermissionConditionDomainServiceImpl.java` - 使用示例

## 禁止事项

- ❌ 禁止在循环中调用 `get()` 单条查询（使用 `getBatch()`）
- ❌ 禁止使用 Redis KEYS 命令（使用 SCAN）
- ❌ 禁止先失效 L1 后失效 L2（顺序必须是 L2 → L1）
- ❌ 禁止先写 L1 后写 L2（顺序必须是 L2 → L1）
- ❌ 禁止在缓存键中包含未验证的用户输入
- ❌ 禁止在数据变更后不触发缓存失效
- ❌ 禁止使用 `ConcurrentHashMap` 替代 Caffeine（缺少 TTL、容量限制）