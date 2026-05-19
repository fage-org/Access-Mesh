# PermQueryEngine 查询链路重构方案（最终版）

## 概述

基于用户确认的方案：
- 问题1：采用方案C（ResolveContext 统一预解析）
- 问题2：采用用户自定义方案（binaryBit 存储模型 + PostgreSQL 位操作查询）

---

## 问题1修复设计：ResolveContext

### ResolveContext 类设计

```java
/**
 * 类型解析上下文 —— 避免同一查询链路中的重复解析
 */
public class ResolveContext {
    private final Long tenantId;
    private final TypeResolutionService typeResolutionService;
    
    // 缓存
    private Map<String, Integer> resourceTypeValueCache;
    private Map<String, Long> domainIdCache;
    private Map<String, Long> operationIdCache;
    private Map<ResourceResolveKey, Long> resourceIdCache;
    
    // 预解析方法（批量加载）
    public void prepareResourceTypes(Set<String> typeCodes);
    public void prepareOperations(String resourceTypeCode, Set<String> opCodes);
    public void prepareResources(List<ResourceResolveRequest> requests);
    
    // 获取方法（从缓存取，无则单次解析）
    public Integer getResourceTypeValue(String typeCode);
    public Long getOperationId(String resourceTypeCode, String operationCode);
    public Long getResourceId(String resourceTypeCode, String resourceCode, String codeType);
}
```

### 使用方式

```java
// PermQueryEngine.query() 修改
public PermResult query(PermQuery q) {
    ResolveContext ctx = new ResolveContext(q.tenantId(), typeResolutionService);
    ctx.prepareResourceTypes(q.resourceTypeCodes());
    // 后续使用 ctx.getResourceTypeValue() 避免重复
}
```

---

## 问题2修复设计：binaryBit 存储模型

### 当前问题

| 当前设计 | 问题 |
|---------|------|
| `role_resource_permission.operationPermissionId` 存储 `OperationPermission.id` | 查询需精确匹配 ID |
| inheritMask 只在内存合并计算 | 数据库查询阶段无法利用继承语义 |
| 每次校验需多次单查询 | 性能瓶颈 |

### 新设计目标

1. **存储授予位值**：`role_resource_permission.grantedBits` 存储 binaryBit（或 effectiveBits）
2. **数据库位操作查询**：`WHERE grantedBits & targetBit != 0`
3. **预加载 OperationPermission**：按 tenantId + resourceType 缓存所有操作（最多63个）
4. **内存计算继承**：从 inheritMask 推算需要查询的 bit 集合

### 数据模型变更

#### OperationPermission 表（不变）

```sql
-- 保持现有结构
CREATE TABLE operation_permission (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    resource_type INT,
    code VARCHAR(50),
    name VARCHAR(100),
    binary_bit BIGINT,      -- 独占位值 (1, 2, 4, 8...)
    inherit_mask BIGINT,    -- 继承掩码
    ...
);
```

#### role_resource_permission 表变更

```sql
-- 当前结构
CREATE TABLE role_resource_permission (
    id BIGINT PRIMARY KEY,
    abstract_role_id BIGINT,
    resource_entity_id BIGINT,
    resource_type INT,
    operation_permission_id BIGINT,  -- ← 当前存储 OperationPermission.id
    ...
);

-- 新结构（方案A：保留 ID，新增位字段）
ALTER TABLE role_resource_permission ADD COLUMN granted_bits BIGINT;
-- granted_bits = OperationPermission.binaryBit
-- 查询时：WHERE granted_bits & target_bit != 0
-- operation_permission_id 保留用于关联

-- 方案B：替换 ID 为位值（用户原意）
-- 但这会丢失 OperationPermission 关联，无法获取 code/name
-- 建议：采用方案A，保留关联
```

**推荐方案A**，理由：
- 保留 operation_permission_id 关联，可获取 code/name
- 新增 granted_bits 支持位操作查询
- 现有数据迁移简单：`granted_bits = (SELECT binary_bit FROM operation_permission WHERE id = operation_permission_id)`

### 查询流程重构

#### 新流程

```
1. 预加载阶段
   ├─ 按 tenantId + resourceType 加载所有 OperationPermission（缓存60分钟）
   ├─ 从 OperationPermission 计算每个操作的 effectiveBits (binaryBit | inheritMask)
   └─ 构建反向索引：binaryBit → OperationPermission

2. 计算目标位值
   ├─ 目标操作 code → targetBinaryBit
   └─ 计算覆盖集：所有 effectiveBits & targetBinaryBit != 0 的 binaryBit 值
   └─ 结果：targetBitMask = 覆盖集内所有 binaryBit 的 OR 值

3. 数据库位操作查询
   ├─ PostgreSQL: WHERE granted_bits & targetBitMask != 0
   └─ 返回所有覆盖目标操作的权限条目

4. 精确过滤（内存）
   ├─ 对返回条目，用 effectiveBits 精确匹配
   ├─ 过滤掉 grantedBits & targetBinaryBit == 0 的条目
   └─ 确保语义正确
```

#### 位操作查询示例

```sql
-- PostgreSQL 位操作查询
SELECT * FROM role_resource_permission
WHERE tenant_id = ?
  AND abstract_role_id IN (?)
  AND (granted_bits & 15) != 0;  -- 15 = targetBitMask，覆盖所有可能包含 VIEW(1) 的权限

-- 解释：
-- VIEW.binaryBit = 1, MANAGE.binaryBit = 8, MANAGE.inheritMask = 7 (VIEW+CREATE+EDIT)
-- MANAGE.effectiveBits = 8 | 7 = 15
-- granted_bits = 8 的条目：8 & 1 = 0，不匹配 VIEW
-- 但 granted_bits = 15 的条目（授予 MANAGE）：15 & 1 = 1，匹配 VIEW
```

**问题发现**：上述示例有问题！
- 如果 granted_bits 存的是 MANAGE.binaryBit = 8
- 8 & 1 = 0，无法匹配 VIEW(target=1)
- 需要存储 effectiveBits 而不是 binaryBit

**修正方案**：

存储授予的 **effectiveBits** 而不是 binaryBit：
- granted_bits = OperationPermission.binaryBit | OperationPermission.inheritMask
- MANAGE: granted_bits = 8 | 7 = 15
- VIEW 校验：15 & 1 = 1 ✓ 匹配！

### OperationPermission 缓存策略

```java
/**
 * OperationPermission 缓存服务
 * 按 tenantId + resourceType 分组缓存，每组最多63个操作
 */
@Service
public class OperationPermissionCacheService {
    
    // 缓存结构：tenantId → resourceType → List<OperationPermission>
    // 缓存时间：60分钟（操作定义通常不变）
    private final CacheService cacheService;
    
    /**
     * 按资源类型加载所有操作权限
     */
    public Map<Long, OperationPermission> loadByResourceType(Long tenantId, Integer resourceType) {
        String cacheKey = "op_perm:" + tenantId + ":" + resourceType;
        // 查缓存 → 查数据库 → 回填缓存
    }
    
    /**
     * 计算覆盖目标操作的所有 binaryBit 值
     */
    public Set<Long> computeCoveringBits(Long tenantId, Integer resourceType, Long targetBinaryBit) {
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        Set<Long> coveringBits = new HashSet<>();
        for (OperationPermission op : opMap.values()) {
            if ((op.getEffectiveBits() & targetBinaryBit) != 0) {
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
     * 反查：从 grantedBits 找到对应的 OperationPermission
     */
    public OperationPermission findByBinaryBit(Long tenantId, Integer resourceType, Long binaryBit) {
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        return opMap.values().stream()
            .filter(op -> Objects.equals(op.getBinaryBit(), binaryBit))
            .findFirst()
            .orElse(null);
    }
}
```

---

## 实施计划

### 阶段1：ResolveContext 实现（问题1）

| 步骤 | 文件 | 内容 |
|------|------|------|
| 1.1 | `ResolveContext.java` | 新增类 |
| 1.2 | `PermQueryEngine.java` | query() 方法使用 ResolveContext |
| 1.3 | `PermQueryEngine.java` | getDeniedIds() 方法使用 ResolveContext |
| 1.4 | `PermissionServiceImpl.java` | queryScopes() 方法使用 ResolveContext |

### 阶段2：数据模型变更（问题2）

| 步骤 | 文件 | 内容 |
|------|------|------|
| 2.1 | SQL Migration | 新增 `granted_bits` 列，填充历史数据 |
| 2.2 | `RoleResourcePermission.java` | 新增 `grantedBits` 字段 |
| 2.3 | `OperationPermissionCacheService.java` | 新增缓存服务 |
| 2.4 | `RoleResourcePermissionMapper.java` | 新增位操作查询方法 |
| 2.5 | `PermQueryEngine.java` | 重构 query() 使用位操作查询 |

### 阶段3：测试验证

| 步骤 | 内容 |
|------|------|
| 3.1 | VerifyContext 测试：验证无重复调用 |
| 3.2 | inheritMask 测试：验证 MANAGE 包含 VIEW |
| 3.3 | 性能对比测试 |

---

## 需确认事项

1. **存储方案选择**：
   - 方案A：新增 `granted_bits` 字段，保留 `operation_permission_id`（推荐）
   - 方案B：替换 `operation_permission_id` 为 `granted_bits`（纯位值存储）

2. **granted_bits 存储内容**：
   - 存 `binaryBit`（需查询时扩展掩码）
   - 存 `effectiveBits = binaryBit | inheritMask`（直接位操作匹配）

3. **历史数据迁移**：
   - 现有数据如何填充 granted_bits
   - 迁移脚本设计

---

> **请确认上述设计要点后，我将开始实施。**