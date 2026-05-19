# PermQueryEngine 查询链路问题分析与修复方案

## 问题清单

### 问题1：重复调用问题

#### 1.1 核心问题：`getDeniedIds` 方法中 `resolveTypeValue` 重复调用

**位置**: `PermQueryEngine.java:257` 和 `:261`

**调用链**:
```
getDeniedIds()
  → line 257: resolveTypeValue(tenantId, "resource_type", resourceTypeCode)  // 第一次
  → line 261: resolveOperationId(tenantId, resourceTypeCode, operationCode)
               → TypeResolutionServiceImpl.resolveOperationId():202
                 → resolveTypeValue(tenantId, "resource_type", resourceTypeCode)  // 第二次，重复！
```

**影响**: 每次批量权限校验都会产生 1 次冗余的 resolveTypeValue 调用。resolveTypeValue 有缓存，但仍有缓存查询开销。

#### 1.2 相关问题：批量方法内部的单次 resolveTypeValue 调用

**位置**: `TypeResolutionServiceImpl.java` 多处

| 方法 | 行号 | 内部调用 resolveTypeValue | 是否合理 |
|------|------|--------------------------|---------|
| `resolveOperationId` | 202 | ✓ | 合理，单次解析需要 |
| `resolveResourceId` | 180 | ✓ | 合理 |
| `resolveUserId` | 167 | ✓ | 合理 |
| `resolveRoleId` | 230 | ✓ | 合理 |
| `batchResolveOperationIds` | 285 | ✓ | **可优化**：批量调用时每个 resourceTypeCode 都调用一次 |
| `batchResolveUserIds` | 381 | ✓ | 合理 |
| `batchResolveRoleIds` | 408 | ✓ | 合理 |

**问题**: `batchResolveOperationIds` 在循环调用场景下会重复调用 resolveTypeValue（见问题1.3）。

#### 1.3 调用方循环重复调用

**位置**: `PermQueryEngine.java:resolveOperationIds()` (line 472-479)

```java
for (String rtCode : q.resourceTypeCodes()) {
    Map<String, Long> map = typeResolutionService.batchResolveOperationIds(
        q.tenantId(), rtCode, q.operationCodes());  // 每次内部调用 resolveTypeValue
    result.addAll(map.values());
}
```

**影响**: 如果 `resourceTypeCodes` 有多个值，每个都会触发一次 resolveTypeValue 调用。

#### 1.4 PermissionServiceImpl.queryScopes 重复调用

**位置**: `PermissionServiceImpl.java:418-427`

```java
// line 418-419: 批量解析类型值
Map<String, Integer> scopeTypeValueMap = typeResolutionService.batchResolveTypeValues(...);

// line 426-427: 循环中再次调用 batchResolveOperationIds
for (String scopeTypeCode : req.scopeResourceTypeCodes()) {
    Map<String, Long> scopeOpIdMap = typeResolutionService.batchResolveOperationIds(
        tenantId, scopeTypeCode, ...);  // 内部调用 resolveTypeValue
}
```

**影响**: 同一个 resourceTypeCode 可能被 batchResolveTypeValues 和 batchResolveOperationIds 分别处理。

---

### 问题2：`binaryBit` 和 `inheritMask` 使用状态分析

#### 2.1 当前使用情况

| 字段 | 使用位置 | 使用方式 | 状态 |
|------|---------|---------|------|
| `binaryBit` | `OperationPermission.getEffectiveBits()` | 计算 effectiveBits | ✓ 已使用 |
| `binaryBit` | `OperationPermission.matchesBit()` | 位匹配判断 | ✓ 已使用 |
| `binaryBit` | `OperationPermissionUtils.covers()` | 权限覆盖判断 | ✓ 已使用 |
| `binaryBit` | `OperationPermissionUtils.filterByOperation()` | 批量过滤 | ✓ 已使用 |
| `binaryBit` | `PermQueryEngine.query()` matchesBit 过滤 | 权限条目过滤 | ✓ 已使用 |
| `binaryBit` | `PermQueryEngine.getDeniedIds()` | 权限覆盖检查 | ✓ 已使用 |
| `inheritMask` | `OperationPermission.getEffectiveBits()` | 与 binaryBit 合并 | ✓ 已使用 |
| `inheritMask` | `OperationPermissionUtils.effectiveBits()` | 与 binaryBit 合并 | ✓ 已使用 |

**结论**: `binaryBit` 和 `inheritMask` **在位运算层面已正确使用**。

#### 2.2 潜在问题：inheritMask 语义继承在数据库查询阶段未处理

**设计意图**（根据产品文档）:
- `inheritMask` 表示操作隐含的其他权限
- 例如：`MANAGE.binaryBit = 8`, `MANAGE.inheritMask = 7`（包含 VIEW=1, CREATE=2, EDIT=4）
- 用户拥有 MANAGE 权限时，应自动拥有 VIEW、CREATE、EDIT 权限

**当前实现问题**:

在 `PermQueryEngine.queryInstance()` 查询阶段：
```java
List<RoleResourcePermission> perms = rolePermMapper.selectInstancePerms(
    tenantId, roleIds, entityIds, opIds);  // opIds = 目标操作ID集合
```

**问题**: 如果用户请求 VIEW 权限校验：
- `opIds = [VIEW_ID]`
- 查询只返回 `operationPermissionId = VIEW_ID` 的条目
- 用户可能拥有 MANAGE 权限（inheritMask 包含 VIEW），但查询不会返回 MANAGE 条目
- 后续 matchesBit 过滤无法检测到隐含权限

**实际影响分析**:

查看 `PermQueryEngine.getDeniedIds()` 的 matchesBit 过滤逻辑（line 299-306）：
```java
if (!instanceEntries.isEmpty()) {
    Map<Long, OperationPermission> opCache = entityBatchLoadService.batchLoadOperations(
        tenantId, Set.of(operationId));  // 只加载目标操作，不加载授予的操作
    OperationPermission targetOp = opCache.get(operationId);
    if (targetOp != null) {
        instanceEntries = OperationPermissionUtils.filterByOperation(instanceEntries, opCache, targetOp);
    }
}
```

**关键问题**: `opCache` 只包含目标操作（`Set.of(operationId)`），不包含授予的操作权限。

`OperationPermissionUtils.filterByOperation()` 的逻辑：
```java
public static List<RolePermEntry> filterByOperation(...) {
    for (RolePermEntry e : entries) {
        OperationPermission granted = opCache.get(e.operationPermissionId());
        if (granted == null) continue;  // ← granted 不在 opCache 中，跳过！
        if (covers(granted, targetOp)) {
            result.add(e);
        }
    }
}
```

**问题确认**: 如果授予的是 MANAGE 权限，而 opCache 只包含 VIEW，则 `granted = opCache.get(MANAGE_ID) = null`，权限条目被跳过。

**结论**: inheritMask 的语义继承**在当前实现中未生效**，需要在数据库查询阶段扩展操作ID集合。

---

## 修复方案对比

### 问题1修复方案

#### 方案A：最小修复 — 仅修复 `getDeniedIds` 重复调用

**修改内容**:
- `PermQueryEngine.getDeniedIds()`: 先调用 `resolveTypeValue`，然后将结果传给 `resolveOperationId`

**实现**:
```java
// 先解析 resourceTypeValue
Integer resourceTypeValue = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
if (resourceTypeValue == null) {
    return new LinkedHashSet<>(resourceIds);
}
// 使用已解析的值直接查询操作
OperationPermission op = operationPermissionMapper.selectByResourceTypeAndCode(tenantId, resourceTypeValue, operationCode);
Long operationId = op != null ? op.getId() : null;
```

**优点**:
- 改动最小，只影响一个方法
- 立即消除冗余调用
- 不破坏现有架构

**缺点**:
- 不解决调用方循环重复调用问题（1.3, 1.4）
- 引入 Mapper 直接依赖，绕过 TypeResolutionService

**复杂度**: Low
**性能提升**: 1次 resolveTypeValue 调用/每次 getDeniedIds

---

#### 方案B：扩展 TypeResolutionService API

**新增方法**:
```java
// TypeResolutionService 接口新增
Long resolveOperationIdByTypeValue(Long tenantId, Integer resourceTypeValue, String operationCode);
```

**修改内容**:
- `TypeResolutionServiceImpl`: 新增方法实现，接收已解析的 typeValue
- `PermQueryEngine.getDeniedIds()`: 先 resolveTypeValue，再调用新方法

**优点**:
- 保持服务分层，不绕过 TypeResolutionService
- 为其他调用方提供可选优化接口

**缺点**:
- 扩展接口增加维护成本
- 需要同步修改接口和实现类

**复杂度**: Low-Medium
**性能提升**: 同方案A

---

#### 方案C：统一 ResolveContext 批量预解析

**设计**:
```java
// 新增 ResolveContext 类
public class ResolveContext {
    private final Long tenantId;
    private final Map<String, Integer> resourceTypeValueCache = new HashMap<>();
    private final Map<String, Long> operationIdCache = new HashMap<>();
    
    // 预解析所有需要的类型值
    public void prepareResourceTypes(Set<String> typeCodes) {
        // 批量解析并缓存
    }
    
    // 从缓存获取
    public Integer getResourceTypeValue(String typeCode) {
        return resourceTypeValueCache.get(typeCode);
    }
}
```

**修改内容**:
- `PermQueryEngine.query()`: 创建 ResolveContext，预解析所有类型
- `PermQueryEngine.getDeniedIds()`: 使用 ResolveContext
- `PermissionServiceImpl.queryScopes()`: 使用 ResolveContext

**优点**:
- 全面解决所有重复调用问题（1.1, 1.3, 1.4）
- 统一的缓存上下文，避免跨方法重复
- 可扩展到其他类型解析（domainId, userId 等）

**缺点**:
- 改动范围大，涉及多个类
- 引入新概念，增加理解成本
- 需要处理上下文生命周期

**复杂度**: Medium-High
**性能提升**: 取消所有重复的 resolveTypeValue 调用

---

#### 方案D：batchResolveOperationIds 支持批量 resourceTypeCode

**新增方法**:
```java
// TypeResolutionService 接口新增
Map<String, Map<String, Long>> batchResolveOperationIdsByTypes(
    Long tenantId, Set<String> resourceTypeCodes, Set<String> operationCodes);
// 返回: resourceTypeCode -> (operationCode -> operationId)
```

**修改内容**:
- `TypeResolutionServiceImpl`: 新增方法，内部一次批量查询所有类型值
- `PermQueryEngine.resolveOperationIds()`: 改用新方法

**优点**:
- 解决 PermQueryEngine 循环重复调用问题（1.3）
- 保持批量解析语义
- 改动适中

**缺点**:
- 新增方法增加接口复杂度
- 不解决 PermissionServiceImpl.queryScopes 的重复调用

**复杂度**: Medium
**性能提升**: N 次 resolveTypeValue → 1 次批量查询

---

### 问题2修复方案

#### 方案E：扩展操作ID查询范围（inheritMask 语义生效）

**设计思路**: 查询时，不仅查询目标操作ID，还要查询所有可能覆盖目标操作的权限

**实现方式**:

```java
// 新增 OperationPermissionMapper 方法
Set<Long> selectCoveringOperationIds(Long tenantId, Integer resourceType, Long targetBinaryBit);
// 返回所有 (binaryBit | inheritMask) & targetBinaryBit != 0 的操作ID

// PermQueryEngine.queryInstance() 修改
Set<Long> expandedOpIds = expandOperationIds(tenantId, resourceType, opIds);
List<RolePermEntry> entries = queryInstance(tenantId, roleIds, entityIds, expandedOpIds);
// 然后用 matchesBit 过滤确认
```

**优点**:
- inheritMask 语义正确生效
- 用户拥有 MANAGE 时，VIEW 校验通过
- 符合产品设计意图

**缺点**:
- 查询范围扩大，可能影响性能
- 需要新增 Mapper 方法
- matchesBit 过滤仍需执行（二次确认）

**复杂度**: Medium
**性能影响**: 查询范围扩大，需评估

---

#### 方案F：内存扩展操作ID（预加载所有操作权限）

**设计思路**: 预加载所有操作权限到内存，在内存中计算覆盖关系

**实现**:
```java
// PermQueryEngine 新增缓存
private Map<Long, Map<Integer, List<OperationPermission>>> operationByTenantAndType;

// 扩展操作ID方法
private Set<Long> expandOperationIdsInMemory(Long tenantId, Integer resourceType, Set<Long> targetOpIds) {
    List<OperationPermission> allOps = getAllOperations(tenantId, resourceType);
    Set<Long> result = new HashSet<>(targetOpIds);
    for (Long targetId : targetOpIds) {
        OperationPermission target = allOps.stream().filter(o -> o.getId().equals(targetId)).findFirst().orElse(null);
        if (target == null) continue;
        for (OperationPermission op : allOps) {
            if ((op.getEffectiveBits() & target.getBinaryBit()) != 0) {
                result.add(op.getId());
            }
        }
    }
    return result;
}
```

**优点**:
- inheritMask 语义生效
- 避免新增数据库查询
- 内存计算效率高

**缺点**:
- 需要预加载所有操作权限（内存开销）
- 缓存失效策略复杂
- 操作权限变更时需更新缓存

**复杂度**: Medium-High
**性能影响**: 预加载开销，内存占用增加

---

#### 方案G：不改查询，改 matchesBit 过滤逻辑

**设计思路**: 查询时不扩展 opIds，但在过滤阶段加载授予操作权限

**实现**:
```java
// PermQueryEngine.getDeniedIds() 修改
if (!instanceEntries.isEmpty()) {
    // 收集所有授予的操作权限ID
    Set<Long> grantedOpIds = instanceEntries.stream()
        .map(RolePermEntry::operationPermissionId)
        .collect(Collectors.toSet());
    
    // 加载授予的操作权限 + 目标操作
    Set<Long> allOpIds = new HashSet<>(grantedOpIds);
    allOpIds.add(operationId);
    Map<Long, OperationPermission> opCache = entityBatchLoadService.batchLoadOperations(tenantId, allOpIds);
    
    OperationPermission targetOp = opCache.get(operationId);
    if (targetOp != null) {
        instanceEntries = OperationPermissionUtils.filterByOperation(instanceEntries, opCache, targetOp);
    }
}
```

**优点**:
- inheritMask 语义生效
- 不改变查询逻辑
- 利用现有位运算机制
- 改动最小

**缺点**:
- 只对 instanceEntries 非空时有效
- scopeAll 条目不受此优化影响
- 增加一次 batchLoadOperations 调用（但 grantedOpIds 通常较小）

**复杂度**: Low
**性能影响**: 微小增加（额外加载授予操作）

---

## 方案组合推荐

### 推荐组合1：最小修复（低风险）

| 问题 | 采用方案 | 理由 |
|------|---------|------|
| 问题1.1 | 方案A 或 方案B | 最小改动，立即见效 |
| 问题1.3, 1.4 | 不修复（低频场景） | 循环调用频率低，优先级 P2 |
| 问题2 | 方案G | 改动最小，语义生效 |

**总改动文件**: 1-2 个
**总改动行数**: ~20 行

---

### 推荐组合2：全面优化（中风险）

| 问题 | 采用方案 | 理由 |
|------|---------|------|
| 问题1 | 方案C 或 方案D | 统一解决所有重复调用 |
| 问题2 | 方案G 或 方案E | inheritMask 语义生效 |

**总改动文件**: 3-5 个
**总改动行数**: ~50-100 行

---

## 未覆盖问题（标记 TODO）

| 问题 | 优先级 | 建议 |
|------|-------|------|
| 问题1.3: PermQueryEngine 循环调用 | P2 | 多 resourceTypeCode 场景较少 |
| 问题1.4: PermissionServiceImpl.queryScopes 重复 | P2 | queryScopes 调用频率低 |
| 问题2: scopeAll 条目的 inheritMask 继承 | P3 | 需单独处理 scopeAll 匹配逻辑 |

---

## 验证建议

### 问题1验证
```java
// 测试用例：验证 resolveTypeValue 调用次数
@Test
void testGetDeniedIds_NoDuplicateResolveTypeValue() {
    // Mock typeResolutionService，验证调用次数 = 1
    when(typeResolutionService.resolveTypeValue(...)).thenReturn(1);
    engine.getDeniedIds(1L, 100L, "USER", Set.of(1L, 2L), "MANAGE");
    verify(typeResolutionService, times(1)).resolveTypeValue(any(), any(), any());
}
```

### 问题2验证
```java
// 测试用例：验证 inheritMask 语义生效
@Test
void testInheritMask_ManageImpliesView() {
    // 设置：MANAGE.binaryBit = 8, inheritMask = 7
    // 用户拥有 MANAGE 权限
    // 校验 VIEW 权限应通过
}
```

---

## 附录：问题根因分析

### 问题1根因
- `resolveOperationId` 方法内部需要 `resourceTypeValue`，但未提供接收已解析值的接口
- 导致调用方即使已解析 `resourceTypeValue`，仍需重复调用

### 问题2根因
- 设计时假设查询阶段会返回所有相关权限条目
- 实际查询按 `operationPermissionId` 精确匹配
- `inheritMask` 的语义继承未在查询阶段体现

---

> **请选择修复方案组合**，我将按选定方案实现修复。