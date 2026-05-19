# PermQueryEngine 重构实施计划（阶段2设计）

## 阶段1：ResolveContext ✅ 已完成

已修改文件：
- `ResolveContext.java` - 新增类型解析上下文类
- `PermQueryEngine.java` - query() 和 getDeniedIds() 使用 ResolveContext

---

## 阶段2：binaryBit 位运算模型设计

### 用户确认的设计要点

1. **存储方案**：替换 operation_permission_id 为 granted_bits（纯位值存储）
2. **存储内容**：只存 binaryBit（继承关系可变，不固化）
3. **查询方式**：
   - 预加载 OperationPermission（按 tenantId + resourceType）
   - 内存计算覆盖位掩码（从 inheritMask 推算）
   - PostgreSQL 位操作查询：`WHERE granted_bits & target_bit_mask != 0`

---

### 数据模型设计

#### role_resource_permission 表变更

```sql
-- 当前结构
CREATE TABLE role_resource_permission (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    abstract_role_id BIGINT NOT NULL,
    resource_entity_id BIGINT,
    resource_type INT NOT NULL,
    operation_permission_id BIGINT,  -- ← 将被替换
    condition_id BIGINT,
    depend_on BIGINT,
    can_grant BOOLEAN DEFAULT FALSE,
    grant_source VARCHAR(50),
    scope_all BOOLEAN DEFAULT FALSE,
    delete_flag BIGINT DEFAULT 0,
    ...
);

-- 新结构
ALTER TABLE role_resource_permission 
  DROP COLUMN operation_permission_id,
  ADD COLUMN granted_bits BIGINT NOT NULL DEFAULT 0;

-- granted_bits 存储授予的 binaryBit 值
-- 例如：VIEW=1, CREATE=2, EDIT=4, MANAGE=8
-- 用户授予 MANAGE 权限：granted_bits = 8
```

**说明**：
- granted_bits 存储单个操作的 binaryBit 值
- 一条权限记录对应一个授予的操作位
- 多操作授予需要多条记录（与当前设计一致）

#### 历史数据迁移

```sql
-- 数据迁移脚本
UPDATE role_resource_permission rrp
SET granted_bits = COALESCE(
    (SELECT op.binary_bit 
     FROM operation_permission op 
     WHERE op.id = rrp.operation_permission_id 
       AND op.tenant_id = rrp.tenant_id
       AND op.delete_flag = 0),
    0
)
WHERE rrp.delete_flag = 0;

-- 验证迁移
SELECT COUNT(*) FROM role_resource_permission 
WHERE granted_bits = 0 AND delete_flag = 0;

-- 删除旧列
ALTER TABLE role_resource_permission DROP COLUMN operation_permission_id;
```

---

### OperationPermission 缓存服务设计

```java
/**
 * OperationPermission 缓存服务
 * <p>
 * 按 tenantId + resourceType 分组缓存，每组最多63个操作。
 * 缓存时间：60分钟（操作定义通常不变）。
 * </p>
 */
@Service
public class OperationPermissionCacheService {
    
    private final OperationPermissionMapper operationPermissionMapper;
    private final CacheService cacheService;
    
    /**
     * 按资源类型加载所有操作权限
     */
    public Map<Long, OperationPermission> loadByResourceType(Long tenantId, Integer resourceType) {
        String cacheKey = buildCacheKey(tenantId, resourceType);
        Map<Long, OperationPermission> cached = cacheService.get(
            PermCacheCatalog.OPERATION_PERMISSIONS, tenantId, cacheKey);
        if (cached != null) {
            return cached;
        }
        List<OperationPermission> ops = operationPermissionMapper.selectValidByResourceType(
            tenantId, resourceType);
        Map<Long, OperationPermission> opMap = ops.stream()
            .collect(Collectors.toMap(OperationPermission::getId, op -> op));
        cacheService.put(PermCacheCatalog.OPERATION_PERMISSIONS, tenantId, cacheKey, opMap, 60 * 60);
        return opMap;
    }
    
    /**
     * 计算覆盖目标操作的所有 binaryBit 值
     * <p>
     * 从 inheritMask 推算：如果某操作的 effectiveBits 包含目标 binaryBit，
     * 则该操作的 binaryBit 应被包含在覆盖集中。
     * </p>
     */
    public Set<Long> computeCoveringBits(Long tenantId, Integer resourceType, Long targetBinaryBit) {
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        Set<Long> coveringBits = new HashSet<>();
        for (OperationPermission op : opMap.values()) {
            long effectiveBits = op.getEffectiveBits();
            if ((effectiveBits & targetBinaryBit) != 0) {
                coveringBits.add(op.getBinaryBit());
            }
        }
        return coveringBits;
    }
    
    /**
     * 计算目标位掩码（用于数据库查询）
     */
    public long computeTargetBitMask(Long tenantId, Integer resourceType, Long targetBinaryBit) {
        Set<Long> coveringBits = computeCoveringBits(tenantId, resourceType, targetBinaryBit);
        long mask = 0L;
        for (Long bit : coveringBits) {
            mask |= bit;
        }
        return mask;
    }
    
    /**
     * 按 binaryBit 查找 OperationPermission
     */
    public OperationPermission findByBinaryBit(Long tenantId, Integer resourceType, Long binaryBit) {
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        return opMap.values().stream()
            .filter(op -> Objects.equals(op.getBinaryBit(), binaryBit))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 批量按 binaryBit 查找 OperationPermission
     */
    public Map<Long, OperationPermission> findByBinaryBits(Long tenantId, Integer resourceType, Set<Long> binaryBits) {
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        Map<Long, OperationPermission> result = new HashMap<>();
        for (Long bit : binaryBits) {
            OperationPermission op = opMap.values().stream()
                .filter(o -> Objects.equals(o.getBinaryBit(), bit))
                .findFirst()
                .orElse(null);
            if (op != null) {
                result.put(bit, op);
            }
        }
        return result;
    }
    
    /**
     * 操作码转 binaryBit
     */
    public Long codeToBinaryBit(Long tenantId, Integer resourceType, String operationCode) {
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        return opMap.values().stream()
            .filter(op -> Objects.equals(op.getCode(), operationCode))
            .map(OperationPermission::getBinaryBit)
            .findFirst()
            .orElse(null);
    }
    
    private String buildCacheKey(Long tenantId, Integer resourceType) {
        return "op_perm:" + resourceType;
    }
}
```

---

### 位操作查询设计

#### Mapper 方法

```java
/**
 * RoleResourcePermissionMapper 新增方法
 */
public interface RoleResourcePermissionMapper extends BaseMapper<RoleResourcePermission> {
    
    /**
     * 按位掩码查询类型级权限（scopeAll=true）
     * <p>
     * PostgreSQL 位操作：WHERE granted_bits & bit_mask != 0
     * </p>
     */
    List<RoleResourcePermission> selectScopeAllPermsByBits(
        Long tenantId, Set<Long> roleIds, Set<Integer> resourceTypes, Long bitMask);
    
    /**
     * 按位掩码查询实例级权限
     */
    List<RoleResourcePermission> selectInstancePermsByBits(
        Long tenantId, Set<Long> roleIds, Set<Long> entityIds, Long bitMask);
    
    /**
     * 按精确位值查询权限（用于精确匹配）
     */
    List<RoleResourcePermission> selectByExactBits(
        Long tenantId, Set<Long> roleIds, Long exactBit);
}
```

#### SQL 查询示例

```sql
-- 位掩码查询（覆盖继承）
SELECT * FROM role_resource_permission
WHERE tenant_id = ?
  AND abstract_role_id IN (?)
  AND (granted_bits & 15) != 0  -- 15 = VIEW|CREATE|EDIT|MANAGE 的掩码
  AND scope_all = true
  AND delete_flag = 0;

-- 精确位查询
SELECT * FROM role_resource_permission
WHERE tenant_id = ?
  AND abstract_role_id IN (?)
  AND granted_bits = 8  -- 精确匹配 MANAGE
  AND delete_flag = 0;
```

---

### 权限查询流程重构

#### 新流程（位运算版本）

```
1. 预加载 OperationPermission
   ├─ 按 tenantId + resourceType 加载所有操作
   ├─ 构建 code → binaryBit 映射
   ├─ 构建 binaryBit → effectiveBits 映射
   └─ 缓存60分钟

2. 解析目标操作
   ├─ operationCode → targetBinaryBit
   └─ 计算覆盖位掩码：targetBitMask = computeTargetBitMask(resourceType, targetBinaryBit)

3. 数据库位操作查询
   ├─ WHERE granted_bits & targetBitMask != 0
   └─ 返回所有可能覆盖目标操作的权限条目

4. 精确过滤（内存）
   ├─ 对返回条目，计算 grantedEffectiveBits
   │   grantedEffectiveBits = grantedBinaryBit | grantedOp.inheritMask
   ├─ 过滤：(grantedEffectiveBits & targetBinaryBit) != 0
   └─ 确保语义正确（inheritMask 可变，需动态计算）

5. 后续处理
   ├─ 条件评估
   ├─ 冲突解决
   └─ 结果组装
```

#### 关键代码

```java
// PermQueryEngine.query() 重构
public PermResult query(PermQuery q) {
    ResolveContext ctx = new ResolveContext(q.tenantId(), typeResolutionService);
    
    // 解析角色
    Set<Long> roleIds = resolveRoleIds(q);
    
    // 解析资源类型值
    Set<Integer> resourceTypes = resolveResourceTypes(q, ctx);
    
    // 预加载 OperationPermission（按资源类型）
    Map<Integer, OperationPermissionCacheService> opCacheByType = new HashMap<>();
    for (Integer resourceType : resourceTypes) {
        opCacheByType.put(resourceType, 
            operationPermissionCacheService.loadByResourceType(q.tenantId(), resourceType));
    }
    
    // 解析目标操作 binaryBit
    Set<Long> targetBinaryBits = resolveBinaryBits(q, opCacheByType);
    
    // 计算覆盖位掩码
    long targetBitMask = computeTargetBitMask(opCacheByType, targetBinaryBits);
    
    // 位操作查询类型级权限
    List<RolePermEntry> scopeAllEntries = queryScopeAllByBits(
        q.tenantId(), roleIds, resourceTypes, targetBitMask);
    
    // 精确过滤（使用 inheritMask 动态计算）
    scopeAllEntries = filterByEffectiveBits(scopeAllEntries, targetBinaryBits, opCacheByType);
    
    // ... 后续处理
}
```

---

### 实施步骤

#### 步骤2.1：新增 OperationPermissionCacheService

| 文件 | 内容 |
|------|------|
| `OperationPermissionCacheService.java` | 新增缓存服务类 |
| `PermCacheCatalog.java` | 新增 OPERATION_PERMISSIONS 缓存目录 |

#### 步骤2.2：数据模型变更

| 文件 | 内容 |
|------|------|
| SQL Migration | 新增 granted_bits 列，填充历史数据 |
| `RoleResourcePermission.java` | 新增 grantedBits 字段，移除 operationPermissionId |

#### 步骤2.3：Mapper 方法变更

| 文件 | 内容 |
|------|------|
| `RoleResourcePermissionMapper.java` | 新增位操作查询方法 |
| `RoleResourcePermissionMapper.xml` | 位操作 SQL 实现 |

#### 步骤2.4：PermQueryEngine 重构

| 文件 | 内容 |
|------|------|
| `PermQueryEngine.java` | 使用位运算查询替代 ID 查询 |
| `RolePermEntry.java` | grantedBits 字段替代 operationPermissionId |

#### 步骤2.5：调用方适配

| 文件 | 内容 |
|------|------|
| `PermissionServiceImpl.java` | 适配位运算结果 |
| `PermissionViewServiceImpl.java` | 适配位运算结果 |

---

### 测试验证

#### 测试用例1：inheritMask 语义生效

```java
@Test
void testInheritMask_ManageImpliesView() {
    // 设置：MANAGE.binaryBit = 8, MANAGE.inheritMask = 7
    // granted_bits = 8（授予 MANAGE）
    
    // 校验 VIEW 权限
    // targetBitMask = 1 | 8 = 9（VIEW + MANAGE）
    // 查询：WHERE granted_bits & 9 != 0 → 返回 granted_bits = 8 的条目
    // 精确过滤：effectiveBits(8|7=15) & 1 = 1 → 通过
}
```

#### 测试用例2：ResolveContext 无重复调用

```java
@Test
void testResolveContext_NoDuplicateResolveTypeValue() {
    // Mock typeResolutionService
    ResolveContext ctx = new ResolveContext(tenantId, typeResolutionService);
    ctx.prepareResourceTypes(Set.of("USER", "ROLE"));
    
    // 验证 batchResolveTypeValues 只调用一次
    verify(typeResolutionService, times(1)).batchResolveTypeValues(any(), any(), any());
    
    // 后续获取不应触发新调用
    ctx.getResourceTypeValue("USER");
    ctx.getResourceTypeValue("ROLE");
    verify(typeResolutionService, times(1)).batchResolveTypeValues(any(), any(), any());
}
```

---

### 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 数据迁移失败 | 历史权限丢失 | 迁移前备份，迁移后验证 |
| 位操作查询性能 | PostgreSQL 位操作开销 | 位掩码查询比 IN 查询更高效 |
| inheritMask 变更影响 | 缓存需更新 | 缓存60分钟，操作变更时失效 |

---

> **阶段2实施需要确认：是否继续执行？**