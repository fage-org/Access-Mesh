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

> **门禁主体契约（T-ORG-001 统一后）**：操作者 ID 即主体 ID
> （`operatorId = abstract_user.id = sys_user.id`），直接传给 engine 门禁，无任何运行时 ID 空间转换层
> （原 `OperatorSubjectResolver` 已删除）。

```java
// 注入 PermQueryEngine
private final PermQueryEngine engine;

// 统一主体 ID：operatorId 直接用于门禁/自查/委托链

// —— 业务编码轨（对外；USER/ROLE 等业务对象门禁与跨服务 SDK 统一使用）——
// resource_entity(USER).code = subjectId、resource_entity(ROLE).code = roleId（architecture §12.3）

// 非抛出检查（返回 boolean；code 传 null = 类型级校验）
boolean allowed = engine.hasPermissionByCode(tenantId, subjectId,
    ResourceTypeCode.USER, String.valueOf(userId), OperationCodeConstants.MANAGE);

// 获取被拒绝的业务编码集合（批量非抛出；引擎纯查询，异常由调用方显式抛出）
Set<String> denied = engine.getDeniedResourceCodes(tenantId, subjectId,
    ResourceTypeCode.DOMAIN, domainCodes, OperationCodeConstants.VIEW);
if (!denied.isEmpty()) {
    throw new SecurityException("Permission denied: ...");
}

// —— entityId 轨（仅引擎内部或已完成解析的调用方：资源树、API 映射、资源依赖、权限树等）——

boolean ok = engine.hasPermissionByEntityId(tenantId, subjectId,
    ResourceTypeCode.RESOURCE, resourceEntityId, OperationCodeConstants.MANAGE);

Set<Long> deniedEntityIds = engine.getDeniedEntityIds(tenantId, subjectId,
    ResourceTypeCode.RESOURCE, resourceEntityIds, OperationCodeConstants.DELETE);
```

> **T-PERM-042 终态**：旧 `hasPermission(Object)` / `validateBatch` / `getDeniedIds` / `toLongId` 已从引擎删除。
> 引擎纯查询不抛 `SecurityException`——admin 域经 `AdminPermissionValidator` 门面（`checkTypeLevel` / `checkInstanceLevel` / `checkBatchInstanceLevel`）抛出；permission 域 AppService 显式 `if-throw`。

## Domain 层 API（复杂查询场景）

复杂查询使用 `PermQuery`，授权传递校验使用 `PermissionGrantDomainService`。

> **主体契约**：Domain 层 API（forAuthCheck/forInterfaceCheck/forResourceQuery/forScopeQuery/forValidate/forUserView）与
> canGrant 委托链的 `userId`/`subjectId` 均指权限域投影主体（`abstract_user.id`），禁止直接传 `sys_user.id`。

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

// queryResources — 资源筛选（实现走 forUserView 取全量权限事实；forResourceQuery 无生产调用方，勿用于新代码）
PermQuery q = PermQuery.forUserView(tenantId, userId);
return PermResultUtils.toQueryResourcesResp(engine.query(q), cacheTtl);

// validate — 管理操作校验
PermQuery q = PermQuery.forValidate(tenantId, subjectId, resourceTypeCode, resourceCode, operationCode);
PermResultUtils.validateOrThrow(engine.query(q));

// scopeQuery — 范围查询
PermQuery q = PermQuery.forScopeQuery(tenantId, userId, resourceTypeCodes, operationCodes);
PermResult r = engine.query(q);

// grant check — 授权传递检查（canGrant 校验）
boolean canGrant = permissionGrantDomainService.canGrantPermission(
  tenantId, subjectId, resourceTypeCode, resourceCode, operationCode, scopeAll, domainCode
);

Map<String, PermissionGrantDomainService.GrantCheckResult> results =
  permissionGrantDomainService.checkCanGrant(tenantId, subjectId, permissions, domainCode);
```

## 工厂方法预设

| 工厂方法 | type级 | instance级 | scopeAll短路 | 评估条件 | 评估冲突 | 附属信息 |
|---------|--------|-----------|-------------|---------|---------|---------|
| forAuthCheck | ✅ | ✅ | ✅ | ✅ | ✅ | 无 |
| forInterfaceCheck | ✅ | ✅ | ✅ | ✅ | ✅ | 全部 |
| forResourceQuery | ❌ | ✅ | - | ❌ | ❌ | resource+op |
| forResourceCheck | ✅ | ✅ | ❌ | ✅ | ✅ | resource+op |
| forValidate | ✅ | ✅ | ✅ | ❌ | ❌ | 无 |
| forValidateByEntityId | ✅ | ✅ | ✅ | ❌ | ❌ | 无（entityId 轨，仅引擎内部/已完成解析的调用方） |
| forScopeQuery | ✅ | ✅ | ❌ | ❌ | ❌ | resource+op+role |
| forUserView | ✅ | ✅ | ❌ | ✅ | ✅ | resource+op+role（用户全量视图，快照读缓存） |

> 使用政策（2026-08-27 核实）：`forResourceQuery` / `forResourceCheck` 当前**零生产调用**（`query-resources` 实际走 `forUserView`），勿用于新代码；`forValidateByEntityId` 仅限引擎内部或已完成解析的调用方（资源树、API 映射、资源依赖、权限树），禁止用于 USER/ROLE 等业务对象门禁。

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