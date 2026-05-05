---
name: permission-query-pipeline
description: >-
  统一权限查询引擎使用规范。
  TRIGGER when: 涉及 PermQueryEngine、PermQuery、PermResult、权限查询、权限校验、
  OperationCodeConstants、ResourceTypeCode、批量权限检查、validate、hasPermission、getDeniedIds。
origin: project
metadata:
  project: AccessMesh
  version: "4.0.0"
---

# 统一权限查询引擎规范

## 核心组件

| 组件 | 职责 | 使用场景 |
|------|------|---------|
| `PermQueryEngine` | 统一查询入口 `query(PermQuery)` + 业务层API | 所有权限查询的唯一入口 |
| `PermQuery` | 统一入参 DTO，8个预设工厂 | 调用方构造查询参数 |
| `PermResult` | 统一返回对象 | 调用方获取结果 |
| `OperationCodeConstants` | 操作码常量（CREATE/MANAGE/DELETE等） | 业务层权限校验参数 |
| `ResourceTypeCode` | 资源类型常量（ROLE/USER/SERVICE等） | 业务层权限校验参数 |

## 业务层 API（Service Impl 使用）

```java
// 注入 PermQueryEngine
private final PermQueryEngine engine;

// 单实例校验（无权限抛 SecurityException）
engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);

// 批量校验（任意一个无权限抛 SecurityException）
engine.validateBatch(tenantId, operatorId, ResourceTypeCode.ROLE, roleIds, OperationCodeConstants.DELETE);

// 非抛出检查（返回 boolean）
boolean allowed = engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.MANAGE);

// 获取被拒绝的 ID（批量非抛出）
Set<Long> denied = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.DOMAIN, domainIds, OperationCodeConstants.VIEW);
```

## Domain 层 API（复杂查询场景）

使用 `PermQuery` 的预设工厂方法：

```java
// check — 权限判定
PermQuery q = PermQuery.forAuthCheck(tenantId, userId, resourceTypeCode, resourceCode, operationCode);
PermResult r = engine.query(q);
return PermResultUtils.toAuthCheckResp(r);

// batchCheck — 批量判定
for (var item : items) {
    PermQuery q = PermQuery.forAuthCheck(tenantId, userId, item.resourceTypeCode(), item.resourceCode(), item.operationCode());
    resultsByCode.put(item.resourceCode(), engine.query(q));
}
return PermResultUtils.toBatchAuthCheckResp(resultsByCode);

// checkInterface — 接口权限
Set<Long> entityIds = matchApiPaths(tenantId, path, method);
PermQuery q = PermQuery.forInterfaceCheck(tenantId, userId, Set.of("API"), entityIds, "ACCESS");
return PermResultUtils.toCheckInterfaceResp(engine.query(q), cacheTtl);

// queryResources — 资源筛选
PermQuery q = PermQuery.forResourceQuery(tenantId, userId, resourceTypeCodes, operationCodes);
return PermResultUtils.toQueryResourcesResp(engine.query(q), cacheTtl);

// validate — 管理操作校验
PermQuery q = PermQuery.forValidate(tenantId, operatorId, resourceTypeCode, resourceCode, operationCode);
PermResultUtils.validateOrThrow(engine.query(q));

// canGrant — 授权检查
PermQuery q = PermQuery.forCanGrant(tenantId, operatorId, resourceTypeCodes, operationCodes);
PermResult r = engine.query(q);
```

## 工厂方法预设

| 工厂方法 | type级 | instance级 | scopeAll短路 | 评估条件 | 评估冲突 | 附属信息 |
|---------|--------|-----------|-------------|---------|---------|---------|
| forAuthCheck | ✅ | ✅ | ✅ | ✅ | ✅ | 无 |
| forInterfaceCheck | ❌ | ✅ | - | ✅ | ✅ | 全部 |
| forResourceQuery | ❌ | ✅ | - | ❌ | ❌ | resource+op |
| forPermissionView | ❌ | ✅ | - | ❌ | ❌ | 全部 |
| forCanGrant | ✅ | ✅ | ❌ | ❌ | ❌ | 全部 |
| forValidate | ✅ | ✅ | ✅ | ❌ | ❌ | 无 |
| forScopeQuery | ✅ | ✅ | ❌ | ❌ | ❌ | 全部 |
| forFullQuery | ✅ | ✅ | ❌ | ✅ | ✅ | 全部 |

## Engine 内部流程

```
query(PermQuery)
  ├─ 1. resolveRoleIds (L1→L2→DB)
  ├─ 2. resolveResourceTypes (批量 code→int)
  ├─ 3. resolveOperationIds (批量 code→opId)
  ├─ 4. queryTypeLevel (1 SQL, scopeAll=true)
  │     └─ scopeAll命中 && earlyReturn → 提前返回 ✅
  ├─ 5. resolveEntityIds + queryInstance (1 SQL)
  ├─ 6. matchesBit过滤 (内存)
  ├─ 7. evaluateConditions + filterConflicts
  ├─ 8. loadAncillary (1-3 SQL, 按需)
  └─ 9. build PermResult
```

## 工具类

| 工具类 | 方法 | 用途 |
|--------|------|------|
| `PermResultUtils` | `toAuthCheckResp()` | PermResult→AuthCheckResp |
| `PermResultUtils` | `toCheckInterfaceResp()` | PermResult→CheckInterfaceResp |
| `PermResultUtils` | `validateOrThrow()` | allowed? 抛BizException(403) |
| `OperationPermissionUtils` | `effectiveBits()` | 有效位计算 |
| `OperationPermissionUtils` | `covers(granted,target)` | 操作覆盖检查 |
| `OperationPermissionUtils` | `filterByOperation()` | 批量过滤 |
| `ConditionEvalUtils` | `evalDateRange()` | 日期评估 |
| `ConditionEvalUtils` | `evalTimeRange()` | 时间评估 |
| `ConditionEvalUtils` | `ipMatchesCidr()` | IP/CIDR匹配 |

## 常量类

### OperationCodeConstants（操作码）

```java
OperationCodeConstants.CREATE      // 创建
OperationCodeConstants.VIEW        // 查看
OperationCodeConstants.MANAGE      // 管理
OperationCodeConstants.UPDATE      // 更新
OperationCodeConstants.DELETE      // 删除
OperationCodeConstants.ASSIGN      // 分配
OperationCodeConstants.REVOKE      // 撤销
OperationCodeConstants.SYNC        // 同步
OperationCodeConstants.MANAGE_API_MAPPING // API映射管理
OperationCodeConstants.SYNC_INTERFACE     // 接口同步
OperationCodeConstants.GRANT       // 授权
```

### ResourceTypeCode（资源类型）

```java
ResourceTypeCode.ROLE              // 角色
ResourceTypeCode.USER              // 用户
ResourceTypeCode.SERVICE           // 服务
ResourceTypeCode.DOMAIN            // 业务域
ResourceTypeCode.TYPE_DEFINITION   // 类型定义
ResourceTypeCode.SYSTEM_CONFIG     // 系统配置
ResourceTypeCode.API               // API接口
```

## 禁止事项

- ❌ 禁止使用 `ResourcePermissionValidator`（已删除）— 使用 `PermQueryEngine`
- ❌ 禁止使用 `OperationType` 枚举（已删除）— 使用 `OperationCodeConstants`
- ❌ 禁止使用 `ResourcePermissionStrategy`（已删除）— ID转换由 Engine 内部处理
- ❌ 禁止直接调 `rolePermMapper.selectListByQuery()` 做权限判定 — 通过 Engine
- ❌ 禁止在 service impl 中写权限查询逻辑 — 通过 Engine
- ❌ 禁止 new `RolePermEntry(...)` — 使用 `RolePermEntryMapper`
- ❌ 禁止私有 `loadResources/loadOperations/loadRoles` — 使用 `EntityBatchLoadDomainService`

## 相关文件

| 文件 | 说明 |
|------|------|
| `PermQueryEngine.java` | 查询引擎 + 业务层API |
| `PermQuery.java` | 入参DTO+工厂 |
| `PermResult.java` | 返回对象 |
| `PermResultUtils.java` | 转换工具 |
| `OperationCodeConstants.java` | 操作码常量 |
| `ResourceTypeCode.java` | 资源类型常量 |
| `OperationPermissionUtils.java` | 位运算 |
| `ConditionEvalUtils.java` | 条件评估 |
| `RolePermEntryMapper.java` | 实体→VO |
| `EntityBatchLoadDomainService.java` | 批量加载 |