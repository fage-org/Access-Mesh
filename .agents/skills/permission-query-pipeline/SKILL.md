---
name: permission-query-pipeline
description: >-
  统一权限查询引擎使用规范。
  TRIGGER when: 涉及 QueryGate、QueryExecutionEngine、PermQueryEngine、PermQuery、PermResult、
  权限查询、权限校验、OperationCode、ResourceTypeCode、批量权限检查、validate、hasPermission、getDeniedIds。
origin: project
metadata:
  project: AccessMesh
  version: "6.0.0"
---

# 统一权限查询引擎规范

> **T-PERM-089（2026-09-27）判定面切换**：管理面门禁与批量拒绝集合的唯一入口已是
> `QueryGate`（`engine.query` 包，内部走新 `QueryExecutionEngine.execute`）——
> check/batchCheck 经 `PermissionCheckAppServiceImpl` 直构 `QueryRequest`。
> **T-PERM-090（2026-09-27）范围与 LEGACY_API 迁移**：queryResources/queryScopes/
> checkInterface/interfaceSnapshot（含 SnapshotAssembler）已全部迁新 execute。
> 旧 `PermQueryEngine` 仅剩迁移期消费者（视图 PermissionViewAppServiceImpl/PermViewAssembler、
> 转授 PermissionGrantDomainServiceImpl，T-PERM-091 迁移、092 删除）；新代码禁止再引用旧引擎。

## 核心组件

| 组件 | 职责 | 使用场景 |
|------|------|---------|
| `QueryGate` | 判定面薄门面：`hasPermissionByCode` / `hasPermissionByEntityId` / `getDeniedResourceCodes` / `getDeniedEntityIds`（T-PERM-089） | 管理面单点门禁与批量拒绝集合的唯一入口 |
| `QueryExecutionEngine` | 新统一执行主体 `execute(QueryRequest)`（T-PERM-082~088：规范化→共享装载→分集合评估） | 唯一权限查询执行器（check/batchCheck 在 AppService 适配层直构请求） |
| `PermQueryEngine` | 旧执行体（迁移期存量：query 供 091 目标〔视图/转授〕使用，092 删除） | 仅存量消费者；新代码禁止引用 |
| `PermQuery` | 旧入参 DTO（迁移期存量） | 仅旧引擎消费者 |
| `PermResult` | 旧返回对象（迁移期存量） | 仅旧引擎消费者 |
| `TargetMode` | 旧目标模式三态枚举：TYPE_LEVEL/INSTANCE/LIST（迁移期存量） | 旧引擎 targetMode 三态判别 |
| `PermBatchQuery` / `PermBatchResult` | 旧批量入参/结果（迁移期存量，092 删除） | 无生产消费者（batchCheck 已迁新 execute） |
| `PermEvalContext` | 条件评估多层上下文（clientIp 用户环境 + evaluatedAt 服务器环境 + attributes 调用方上下文，T-PERM-057；新引擎 CallerContext 承担同职责） | 条件评估入参 |
| `OperationCode` | 操作码常量（CREATE/MANAGE/DELETE等） | 业务层权限校验参数 |
| `ResourceTypeCode` | 资源类型常量（ROLE/USER/SERVICE等） | 业务层权限校验参数 |

## 业务层 API（Service Impl 使用）

> **门禁主体契约（T-ORG-001 统一后）**：操作者 ID 即主体 ID
> （`operatorId = abstract_user.id = sys_user.id`），直接传给 queryGate 门禁，无任何运行时 ID 空间转换层
> （原 `OperatorSubjectResolver` 已删除）。

```java
// 注入 QueryGate（T-PERM-089）
private final QueryGate queryGate;

// 统一主体 ID：operatorId 直接用于门禁/自查/委托链

// —— 业务编码轨（对外；USER/ROLE 等业务对象门禁与跨服务 SDK 统一使用）——
// resource_entity(USER).code = subjectId、resource_entity(ROLE).code = roleId（architecture §12.3）

// 非抛出检查（返回 boolean；code 传 null = 类型级校验）
boolean allowed = queryGate.hasPermissionByCode(tenantId, subjectId,
    ResourceTypeCode.ROLE, String.valueOf(roleId), OperationCode.MANAGE);

// 获取被拒绝的业务编码集合（批量非抛出；门面纯查询，异常由调用方显式抛出）
Set<String> denied = queryGate.getDeniedResourceCodes(tenantId, subjectId,
    ResourceTypeCode.DOMAIN, domainCodes, OperationCode.VIEW);
if (!denied.isEmpty()) {
    throw new SecurityException("Permission denied: ...");
}

// —— entityId 轨（仅已完成解析的调用方：资源树、API 映射等 resource_entity 管理链路）——

boolean ok = queryGate.hasPermissionByEntityId(tenantId, subjectId,
    ResourceTypeCode.RESOURCE, resourceEntityId, OperationCode.MANAGE);

Set<Long> deniedEntityIds = queryGate.getDeniedEntityIds(tenantId, subjectId,
    ResourceTypeCode.RESOURCE, resourceEntityIds, OperationCode.DELETE);
```

> **评估口径（T-PERM-089，沿旧 forValidate 拉平语义）**：EVALUATE+ENFORCE+DECISION、
> 判定面继承开（SELF_AND_ANCESTORS——授父覆盖子）、类型级回退放行（TypeFallback.ALLOW，
> scopeAll 先行）；clientIp 自动装配（无请求上下文时 IP 类条件 fail-closed）。
> 旧 `hasPermission(Object)` / `validateBatch` / `getDeniedIds` / `toLongId` 早已删除（T-PERM-042 终态）。
> 门面纯查询不抛 `SecurityException`——管理轨 AppService（auth/menu/user/org/platform 管理入口与 role 包 `UserRoleQueryAppService` 读聚合）经引擎门面 `AdminPermissionValidator`（`checkTypeLevel` / `checkInstanceLevel` / `checkBatchInstanceLevel`）抛出；权限轨 AppService（角色/主体/授权/资源/类型/条件等管理入口）显式 `if-throw`——按调用入口的轨道分，不按能力包分（role 包内两轨并存）。

## check/batchCheck（T-PERM-089 已迁新 execute）

外部 check 族在 `PermissionCheckAppServiceImpl` 适配层直构 `QueryRequest`（外层职责保留：
主体业务键解析/USER_NOT_FOUND、原序/重复项〔item key=输入下标〕、请求级父上下文、
context.clientIp 提取为受信 IP）：

```java
// check：无编码目标（含空白串归一 TYPE_LEVEL）→ TypeLevel；有编码 → 单 clause TargetSet
// （inheritMode PARENT/BOTH → Inheritance.SELF_AND_ANCESTORS，否则 SELF）
QueryItem item = QueryItem.decision("check", selection(req), checkOutput()); // matchedIds+KEPT
QueryResult result = queryEngine.execute(new QueryRequest(tenantId, new User(userId),
    callerContext(req.context()), ReadOptions.defaults(), List.of(item)));
return PermResultUtils.toAuthCheckResp((DecisionResult) result.orderedResults().get(0));

// batchCheck：多个独立 DECISION item 一次 execute 批量表达（禁循环 N 次公开 execute）；
// 拒绝原因四词表（NO_ROLE/NO_PERMISSION/CONDITION_NOT_MET_OR_CONFLICT/DEPENDENT_NOT_IN_PARENT_CONTEXT）
// 与 DecisionResult.Reason 枚举 1:1（.name() 输出）
```

退化输入定案（2026-09-27 用户拍板）：resourceCode 空白串归一 TYPE_LEVEL；
context 顶层 evaluatedAt/timestamp 保留键由 CallerContext 结构拒绝（400 VALIDATION_FAILED，2026-09-27 外评处置修订），clientIp 提取不受影响。

## Domain 层 API（T-PERM-090 已迁新 execute；forUserView 仅剩 091 目标）

四个查询面已直构 `QueryRequest`；授权传递校验使用 `PermissionGrantDomainService`。

> **主体契约**：各面 `userId`/`subjectId` 均指权限面投影主体（`abstract_user.id`）——T-ORG-001 统一后
> `sys_user.id` 与之同值，操作者 ID 直接传入即可（与业务层 API 节同口径，无运行时 ID 空间转换层）。

```java
// checkInterface — LEGACY_API 共同集合（T-PERM-090）：全部匹配 API 组成一个 TARGET_SET 单 item
// （注册门禁在先、不拆项 OR、SELF、TypeFallback.ALLOW；全部映射无实体引用退 TYPE_LEVEL）
TypeOperation access = new TypeOperation(ResourceTypeCode.API, OperationCode.ACCESS);
Selection selection = entityIds.isEmpty()
    ? new TypeLevel(List.of(access))
    : new TargetSet(entityIds.stream().map(id -> new TargetClause(access, new ByEntityId(id))).toList(),
        Inheritance.SELF, TypeFallback.ALLOW, null);
QueryResult r = queryEngine.execute(new QueryRequest(tenantId, new User(userId),
    CallerContext.fromCallerMap(context), ReadOptions.defaults(),
    List.of(QueryItem.decision("checkInterface", selection, interfaceOutput()))));
return PermResultUtils.toCheckInterfaceResp((DecisionResult) r.orderedResults().get(0), 30);

// queryResources — GRANT_LIST＋EVALUATE/ENFORCE＋FACTS（树扩展=OutputSpec 展示展开，判定与展示分离）
PresentationExpansion expansion = /* includeChildren/includeInherited → CHILDREN/PARENTS/BOTH/NONE */;
QueryItem item = QueryItem.grantListFacts("resources", null, Evaluation.full(),
    new OutputSpec(FactDetail.KEPT, false, true, false, expansion, Set.of(), false));

// scopeQuery — GRANT_LIST＋父要求＋EVALUATE/ENFORCE＋RAW_AND_KEPT；四态组装=ScopeCoverageProjector 纯投影
// （父对象存在性预检查在外层：resolveResourceId 落空 → OBJECT_KEY_NOT_FOUND；matchedParentOperations
//  取 result.details().parentCheck().matchedOperationCodes，不重跑父判断；NO_ROLE/PARENT_DENIED → NO_PERMISSION）
OutputSpec output = new OutputSpec(FactDetail.RAW_AND_KEPT, true, true, false,
    PresentationExpansion.NONE, Set.copyOf(requirements), false); // requirements=类型×操作全组合
QueryItem item = QueryItem.grantListFacts("scopes", parent, Evaluation.full(), output);
List<ScopeGroup> groups = ScopeCoverageProjector.project((GrantSetResult) result, requirements);

// interfaceSnapshot — LEGACY_API 旧快照（T-PERM-090）：GRANT_LIST＋PRESERVE/ENFORCE＋FACTS
// （条件身份保留、互斥仍清；角色解析含互斥双删由 User 主体内部完成；SnapshotAssembler 消费 List<GrantFact>）
QueryItem item = QueryItem.grantListFacts("snapshot", null, Evaluation.preserveEnforce(),
    new OutputSpec(FactDetail.KEPT, false, false, false, PresentationExpansion.NONE, Set.of(), false));

// grant check — 授权传递检查（canGrant 校验；T-PERM-091 迁移，仍走 forUserView 旧管线）
boolean canGrant = permissionGrantDomainService.canGrantPermission(
  tenantId, subjectId, resourceTypeCode, resourceCode, codeType, operationCode, scopeAll, domainCode
);

Map<String, PermissionGrantDomainService.GrantCheckResult> results =
  permissionGrantDomainService.checkCanGrant(tenantId, subjectId, permissions, domainCode);
```

## 工厂方法预设（迁移期存量：仅 forUserView 剩 091 目标〔视图/转授〕；forAuthCheck/forValidate/forValidateByEntityId/forInterfaceCheck/forScopeQuery 已无生产消费者）

| 工厂方法 | targetMode | 评估条件 | 条目互斥 | 判定面继承 | 附属信息 |
|---------|-----------|---------|---------|-----------|---------|
| forAuthCheck | ~~code=null→TYPE_LEVEL / 有 code→INSTANCE~~ | ✅ | ✅ | 关 + `setInheritMode("PARENT"/"BOTH")` 显式开 | ~~已迁新 execute（T-PERM-089）~~ |
| forInterfaceCheck | INSTANCE | ✅ | ✅ | 关（API 扁平） | ~~已迁新 execute（T-PERM-090，TARGET_SET 共同集合）~~ |
| forValidate / forValidateByEntityId | ~~两档~~ | ✅（拉平，入口自动装配 clientIp） | ✅ | **开**（管理面写门禁矩阵） | ~~已迁 QueryGate（T-PERM-089）~~ |
| forScopeQuery | LIST | ✅ | ✅ | 不适用 | ~~已迁新 execute（T-PERM-090，GRANT_LIST＋RAW_AND_KEPT）~~ |
| forUserView | LIST | ✅（标记态 `setMarkConditionsOnly`，快照构建） | ✅ | 不适用 | resource+op+role（用户全量视图，快照读缓存）；树扩展 `setInheritChildren/setInheritParents`（展示面展开）（视图/转授消费，T-PERM-091 迁移） |

**三态互不串义**：TYPE_LEVEL 只消费 scopeAll（零实例查询，实例授权不得放行类型级门禁）；INSTANCE 目标下推+判定面闭包；LIST 按角色全量。**两语义拆分**：判定面继承（`inheritClosure`，查询前目标∪同类型祖先链，改变 allowed/denied）≠ 展示面展开（`inheritParents/inheritChildren`，查询后克隆 `grantSource=INHERITED`，不改变判定）。**角色互斥经 resolveJudgementRoleIds 进全部判定入口**（T-PERM-075，2026-09-22——取代 2026-09-09「不归引擎」定案）：引擎解析分支/getDenied* 便捷入口/菜单权限串/接口快照消费互斥过滤后角色集（写守卫看原始持有窗口不经此入口）；授予时校验沿 T-PERM-063 落地（[历史定案原文](../../../docs/archive/2026-09-26/decision-registry-before.md) 2026-09-22 行）。

> 使用政策：`forValidateByEntityId` 仅限已完成解析的调用方（资源树、API 映射），禁止用于 USER/ROLE 等业务对象门禁（entityId 轨改走 `queryGate.hasPermissionByEntityId`）。`forResourceQuery` / `forResourceCheck` 已删除（2026-08-28，零生产调用；资源类查询语义由 `forUserView` / `forValidateByEntityId` 覆盖，勿重新引入）。

## Engine 内部流程（迁移期存量管线，仅剩 091 目标〔视图/转授〕；092 删除）

> check/batchCheck/管理门禁/getDenied（T-PERM-089）与范围/LEGACY_API 接口集合（T-PERM-090）
> 已不走本管线（新 execute：
> 主体解析 → TYPE_GRANT/INSTANCE/GRANT_LIST 分阶段 → 事实与展示投影，设计
> `r2-unified-query-and-admission.md` §4；下述仅剩视图/转授消费者）。

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

queryBatch(PermBatchQuery) — 批量判定 A+ 形态（T-PERM-061，语义与单条逐 item 等价）
  ├─ 唯一非空 PermEvalContext 钉住（a2：请求级单一评估时刻挂全链）
  ├─ 角色 ×1（空=整批 NO_ROLE）→ 六元分组键归组（parentResource 请求级共享不进键）
  ├─ 共享装载：类型/操作解析 ×1 + 逐组掩码 + scopeAll 行 ×1（合并 SQL 切回组子集）
  ├─ scopeAll 段逐组评估（组内一次；TYPE_LEVEL 无条件丢 depend_on 子行/INSTANCE filterDependentEntries）
  │     → 组放行即短路（全部 item allowed，含幽灵 code；ledger 记账后继续）
  ├─ 实例段（未放行组）：entity 预解析 ×1 按键取 → true 档闭包 CTE ×1（false 档恒 {自身}）
  │     → 实例行 ×1 → 逐 item 评估（PERM_MUTEX 集合语义）→ reason 三支
  ├─ 条件快照 = 请求级增量四态（openBatchEvaluator：仅 OK 入正缓存，失败态 fail-close）
  ├─ 互斥 = 静态数据共享 + 计算通知解耦（openBatchMutexEvaluator；(组,ruleId) ledger 聚合通知）
  └─ 空目标集守卫：可解析 entityId 并集为空不调闭包 CTE 与实例 SQL
```

## 工具类

| 工具类 | 方法 | 用途 |
|--------|------|------|
| `PermResultUtils` | `toAuthCheckResp(DecisionResult)` | 新单项判定→AuthCheckResp（纯转换，T-PERM-089） |
| `PermResultUtils` | `toCheckInterfaceResp(DecisionResult, ttl)` | 新单项判定→CheckInterfaceResp（纯转换，T-PERM-090） |
| `OperationPermissionUtils` | `effectiveBits()` | 有效位计算 |
| `OperationPermissionUtils` | `covers(granted,target)` | 操作覆盖检查 |
| `OperationPermissionUtils` | `coveredOperations()` | 按位掩码取覆盖操作集 |
| `ConditionEvalUtils` | `evalDateRange()` | 日期评估 |
| `ConditionEvalUtils` | `evalTimeRange()` | 时间评估 |
| `ConditionEvalUtils` | `ipMatchesCidr()` | IP/CIDR匹配 |

## 常量类

### OperationCode（操作码）

```java
OperationCode.CREATE      // 创建
OperationCode.VIEW        // 查看
OperationCode.MANAGE      // 管理
OperationCode.UPDATE      // 更新
OperationCode.DELETE      // 删除
OperationCode.SYNC        // 同步
OperationCode.MANAGE_API_MAPPING // API映射管理
OperationCode.SYNC_INTERFACE     // 接口同步
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
- ❌ 禁止使用 `OperationType` 枚举（已删除）— 使用 `OperationCode`
- ❌ 禁止使用 `ResourcePermissionStrategy`（已删除）— ID转换由 Engine 内部处理
- ❌ 禁止直接调 `rolePermMapper.selectListByQuery()` 做权限判定 — 通过 Engine
- ❌ 禁止在 service impl 中写权限查询逻辑 — 通过 Engine
- ❌ 禁止 new `RolePermEntry(...)` — 使用 `RolePermEntryMapper`
- ❌ 禁止私有 `loadResources/loadOperations/loadRoles` — 使用对应 Mapper 批量查询（`selectValidByIds(tenantId, ids)` 等；`EntityBatchLoadDomainService` 已删除勿引用）

## 相关文件

| 文件 | 说明 |
|------|------|
| `engine/query/QueryGate.java` | 判定面薄门面（四方法，T-PERM-089） |
| `engine/query/QueryExecutionEngine.java` | 新统一执行主体 execute |
| `engine/query/QueryEngineConfiguration.java` | 引擎 Bean 装配（T-PERM-089 起） |
| `PermQueryEngine.java` | 旧执行体（迁移期存量，092 删除） |
| `PermQuery.java` | 旧入参DTO+工厂（迁移期存量） |
| `PermResult.java` | 旧返回对象（迁移期存量） |
| `PermResultUtils.java` | 转换工具 |
| `OperationCode.java` | 操作码常量 |
| `ResourceTypeCode.java` | 资源类型常量 |
| `OperationPermissionUtils.java` | 位运算 |
| `ConditionEvalUtils.java` | 条件评估 |
| `RolePermEntryMapper.java` | 实体→VO |
| `UserRoleMapper.java` 等批量查询 | 批量加载（`selectValidByIds`） |