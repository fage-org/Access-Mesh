---
name: permission-query-pipeline
description: >-
  统一权限查询引擎使用规范。
  TRIGGER when: 涉及 PermQueryEngine、PermQuery、PermResult、权限查询、权限校验、
  OperationCodeConstants、ResourceTypeCode、批量权限检查、validate、hasPermission、getDeniedIds。
origin: project
metadata:
  project: AccessMesh
  version: "5.1.0"
---

# 统一权限查询引擎规范

## 核心组件

| 组件 | 职责 | 使用场景 |
|------|------|---------|
| `PermQueryEngine` | 统一查询入口 `query(PermQuery)` + 业务层API | 所有权限查询的唯一入口 |
| `PermQuery` | 统一入参 DTO，6个预设工厂（参数预设封装） | 调用方构造查询参数 |
| `PermResult` | 统一返回对象（双轨 + 主资源上下文回传） | 调用方获取结果 |
| `TargetMode` | 目标模式三态枚举：TYPE_LEVEL/INSTANCE/LIST（T-PERM-057） | targetMode 三态判别 |
| `PermEvalContext` | 条件评估多层上下文（clientIp 用户环境 + evaluatedAt 服务器环境 + attributes 调用方上下文，T-PERM-057） | 条件评估入参 |
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

> **主体契约**：Domain 层 API（forAuthCheck/forInterfaceCheck/forValidate/forValidateByEntityId/forScopeQuery/forUserView）与
> canGrant 委托链的 `userId`/`subjectId` 均指权限域投影主体（`abstract_user.id`），禁止直接传 `sys_user.id`。

```java
// check — 权限判定
PermQuery q = PermQuery.forAuthCheck(tenantId, userId, resourceTypeCode, resourceCode, operationCode);
PermResult r = engine.query(q);
return PermResultUtils.toAuthCheckResp(r);

// batchCheck — 批量判定（组装在 AppService：PermissionCheckAppServiceImpl.batchCheck 逐 item 走 engine.query 后 new BatchAuthCheckResp）
for (var item : items) {
    PermQuery q = PermQuery.forAuthCheck(tenantId, userId, item.resourceTypeCode(), item.resourceCode(), item.operationCode());
    results.add(engine.query(q));
}
return new BatchAuthCheckResp(List.copyOf(results));

// checkInterface — 接口权限
Set<Long> entityIds = matchApiPaths(tenantId, path, method);
PermQuery q = PermQuery.forInterfaceCheck(tenantId, userId, Set.of("API"), entityIds, "ACCESS");
return PermResultUtils.toCheckInterfaceResp(engine.query(q), cacheTtl);

// queryResources — 资源筛选（实现走 forUserView 取全量权限事实；响应组装在 AppService：
// PermissionQueryAppServiceImpl.buildQueryResourcesResponse，toQueryResourcesResp 已删除勿引用）
PermQuery q = PermQuery.forUserView(tenantId, userId);
return buildQueryResourcesResponse(engine.query(q), req, tenantId);

// validate — 管理操作校验（引擎纯查询，拒绝由调用方显式抛出；admin 域经 AdminPermissionValidator 门面）
PermQuery q = PermQuery.forValidate(tenantId, subjectId, resourceTypeCode, resourceCode, operationCode);
PermResult r = engine.query(q);
if (!r.allowed()) {
    throw new SecurityException("Permission denied: ...");
}

// scopeQuery — 范围查询（LIST；主资源上下文经引擎执行 depend_on 过滤，T-PERM-057 收编）
PermQuery q = PermQuery.forScopeQuery(tenantId, userId, resourceTypeCodes, operationCodes);
q.setParentResource(parentResourceTypeCode, parentResourceCode, parentCodeType, parentOperationCodes);
q.setEvalContext(PermEvalContext.fromCallerMap(callerContextMap));
PermResult r = engine.query(q);

// grant check — 授权传递检查（canGrant 校验；codeType 为第 5 参）
boolean canGrant = permissionGrantDomainService.canGrantPermission(
  tenantId, subjectId, resourceTypeCode, resourceCode, codeType, operationCode, scopeAll, domainCode
);

Map<String, PermissionGrantDomainService.GrantCheckResult> results =
  permissionGrantDomainService.checkCanGrant(tenantId, subjectId, permissions, domainCode);
```

## 工厂方法预设（T-PERM-057 统一引擎：targetMode 三态 + 评估口径）

| 工厂方法 | targetMode | 评估条件 | 条目互斥 | 判定面继承 | 附属信息 |
|---------|-----------|---------|---------|-----------|---------|
| forAuthCheck | code=null→TYPE_LEVEL / 有 code→INSTANCE | ✅ | ✅ | 关 + `setInheritMode("PARENT"/"BOTH")` 显式开 | 无；depend_on 子行按 parentResource 上下文过滤（T-PERM-058：不传=fail-closed 排除，拒绝原因 DEPENDENT_NOT_IN_PARENT_CONTEXT） |
| forInterfaceCheck | INSTANCE | ✅ | ✅ | 关（API 扁平） | 全部 |
| forValidate | code=null→TYPE_LEVEL / 有 code→INSTANCE | ✅（拉平，入口自动装配 clientIp） | ✅ | **开**（管理面写门禁矩阵） | 无 |
| forValidateByEntityId | id=null→TYPE_LEVEL / 有 id→INSTANCE | ✅（同上） | ✅ | **开** | 无（entityId 轨，仅引擎内部/已完成解析的调用方） |
| forScopeQuery | LIST | ✅ | ✅ | 不适用 | resource+op+role；主资源上下文 `setParentResource` 由引擎执行 depend_on 过滤 |
| forUserView | LIST | ✅（标记态 `setMarkConditionsOnly`，快照构建） | ✅ | 不适用 | resource+op+role（用户全量视图，快照读缓存）；树扩展 `setInheritChildren/setInheritParents`（展示面展开） |

**三态互不串义**：TYPE_LEVEL 只消费 scopeAll（零实例查询，实例授权不得放行类型级门禁）；INSTANCE 目标下推+判定面闭包；LIST 按角色全量。**两语义拆分**：判定面继承（`inheritClosure`，查询前目标∪同类型祖先链，改变 allowed/denied）≠ 展示面展开（`inheritParents/inheritChildren`，查询后克隆 `grantSource=INHERITED`，不改变判定）。**角色互斥不归引擎**（2026-09-09 定案）：快照/权限树的 `filterRoleMutex` 调用方自理，授权时校验另行立项。

> 使用政策：`forValidateByEntityId` 仅限引擎内部或已完成解析的调用方（资源树、API 映射、资源依赖、权限树），禁止用于 USER/ROLE 等业务对象门禁。`forResourceQuery` / `forResourceCheck` 已删除（2026-08-28，零生产调用；资源类查询语义由 `forUserView` / `forValidateByEntityId` 覆盖，勿重新引入）。

## Engine 内部流程（T-PERM-057 统一管线）

```
query(PermQuery)
  ├─ 0. resolveRoleIds (EFFECTIVE_ROLES 缓存；四便捷入口自动装配 PermEvalContext)
  ├─ TYPE_LEVEL：resolveContext → resolveBitMasks(位覆盖常开) → queryScopeAll (1 SQL)
  │     → depend_on 行读侧排除（T-PERM-058：只认主授权）→ evaluateIfNeeded → allowed（零实例查询）
  ├─ INSTANCE：queryScopeAll (1 SQL) → filterDependentEntries (depend_on 上下文
  │       过滤，惰性父判定) → 评估通过提前返回
  │     → resolveEntityIds → [inheritClosure] selectSelfAndAncestorClosureBatch
  │       (判定面闭包 CTE：{目标}∪同类型祖先链，止步同类型/软删截断/防环)
  │     → queryInstance (1 SQL，目标下推含闭包集) → filterDependentEntries (两阶段共享一次父判定)
  │     → evaluateIfNeeded → [展示面展开] expandByPresentMode (查询后克隆) → loadAncillary
  └─ LIST：loadRolePermEntriesWithCache (ROLE_PERM_SNAPSHOT 读缓存全量)
        → [parentResource] 主资源 INSTANCE 判定 + depend_on 过滤
        → 记录 rawEntries → evaluateIfNeeded → [展示面展开] → loadAncillaryForView
  └─ build PermResult (双轨 + rawEntries/parentMatched 回传)
```

## 工具类

| 工具类 | 方法 | 用途 |
|--------|------|------|
| `PermResultUtils` | `toAuthCheckResp()` | PermResult→AuthCheckResp |
| `PermResultUtils` | `toCheckInterfaceResp()` | PermResult→CheckInterfaceResp |
| `OperationPermissionUtils` | `effectiveBits()` | 有效位计算 |
| `OperationPermissionUtils` | `covers(granted,target)` | 操作覆盖检查 |
| `OperationPermissionUtils` | `coveredOperations()` | 按位掩码取覆盖操作集 |
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
OperationCodeConstants.SYNC        // 同步
OperationCodeConstants.MANAGE_API_MAPPING // API映射管理
OperationCodeConstants.SYNC_INTERFACE     // 接口同步
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
- ❌ 禁止私有 `loadResources/loadOperations/loadRoles` — 使用对应 Mapper 批量查询（`selectValidByIds(tenantId, ids)` 等；`EntityBatchLoadDomainService` 已删除勿引用）

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
| `UserRoleMapper.java` 等批量查询 | 批量加载（`selectValidByIds`） |