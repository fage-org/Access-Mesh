---
name: permission-query-pipeline
description: >-
  统一权限查询引擎使用规范。
  TRIGGER when: 涉及 QueryGate、QueryExecutionEngine、权限查询、权限校验、OperationCode、
  ResourceTypeCode、批量权限检查、validate、hasPermission、getDeniedIds。
origin: project
metadata:
  project: AccessMesh
  version: "7.0.0"
---

# 统一权限查询引擎规范

> **执行主体唯一（T-PERM-092 终态）**：旧 `PermQueryEngine` 与四旧 DTO
> （`PermQuery`/`PermResult`/`PermBatchQuery`/`PermBatchResult`）、`TargetMode`、
> `ResolveContext` 已删除——判定面/管理门禁/getDenied（T-PERM-089）、范围与 LEGACY_API
> 四面（T-PERM-090）、视图/转授（T-PERM-091）全部经新 `QueryExecutionEngine.execute`
> 后整删；X04 退役锁=`QueryBoundaryArchitectureTest`（主源码再现即红）。
> check 族 `inheritMode` 线格式解析收编于 `PermissionCheckAppServiceImpl.inheritClosureOf`。

## 核心组件

| 组件 | 职责 | 使用场景 |
|------|------|---------|
| `QueryGate` | 判定面薄门面：`hasPermissionByCode` / `hasPermissionByEntityId` / `getDeniedResourceCodes` / `getDeniedEntityIds`（T-PERM-089） | 管理面单点门禁与批量拒绝集合的唯一入口 |
| `QueryExecutionEngine` | 唯一执行主体 `execute(QueryRequest)`（T-PERM-082~088：规范化→共享装载→分集合评估→事实与展示投影） | 全部权限查询（check/batchCheck 在 AppService 适配层直构请求） |
| `PermEvalContext` | 条件评估多层上下文（clientIp 用户环境 + evaluatedAt 服务器环境 + attributes 调用方上下文，T-PERM-057；`RunState` 据此构建评估输入，`CallerContext` 承载调用方层） | 条件评估入参 |
| `OperationCode` | 操作码常量（CREATE/MANAGE/DELETE等） | 业务层权限校验参数 |
| `ResourceTypeCode` | 资源类型常量（ROLE/USER/SERVICE等） | 业务层权限校验参数 |

## 业务层 API（Service Impl 使用）

> **门禁主体契约（T-ORG-001 统一后）**：操作者 ID 即主体 ID
> （`operatorId = abstract_user.id = sys_user.id`），直接传给 queryGate 门禁，无任何运行时 ID 空间转换层。

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

> **门面评估口径（T-PERM-089）**：EVALUATE+ENFORCE+DECISION、判定面继承开
> （SELF_AND_ANCESTORS——授父覆盖子）、类型级回退放行（TypeFallback.ALLOW，
> scopeAll 先行）；clientIp 自动装配（无请求上下文时 IP 类条件 fail-closed）。
> 门面纯查询不抛 `SecurityException`——管理轨 AppService（auth/menu/user/org/platform 管理入口与 role 包 `UserRoleQueryAppService` 读聚合）经引擎门面 `AdminPermissionValidator`（`checkTypeLevel` / `checkInstanceLevel` / `checkBatchInstanceLevel`）抛出；权限轨 AppService（角色/主体/授权/资源/类型/条件等管理入口）显式 `if-throw`——按调用入口的轨道分，不按能力包分（role 包内两轨并存）。

## check/batchCheck（服务面适配层直构 QueryRequest）

外部 check 族在 `PermissionCheckAppServiceImpl` 适配层直构 `QueryRequest`（外层职责：
主体业务键解析/USER_NOT_FOUND、原序/重复项〔item key=输入下标〕、请求级父上下文、
context.clientIp 提取为受信 IP）：

```java
// check：无编码目标（含空白串归一 TYPE_LEVEL）→ TypeLevel；有编码 → 单 clause TargetSet
// （inheritMode PARENT/BOTH → Inheritance.SELF_AND_ANCESTORS，否则 SELF——
//  线格式解析=适配层私有 inheritClosureOf，T-PERM-092 收编）
QueryItem item = QueryItem.decision("check", selection(req), checkOutput()); // matchedIds+KEPT
QueryResult result = queryEngine.execute(new QueryRequest(tenantId, new User(userId),
    callerContext(req.context()), ReadOptions.defaults(), List.of(item)));
return PermResultUtils.toAuthCheckResp((DecisionResult) result.orderedResults().get(0));

// batchCheck：多个独立 DECISION item 一次 execute 批量表达（禁循环 N 次公开 execute）；
// 拒绝原因四词表（NO_ROLE/NO_PERMISSION/CONDITION_NOT_MET_OR_CONFLICT/DEPENDENT_NOT_IN_PARENT_CONTEXT）
// 与 DecisionResult.Reason 枚举 1:1（.name() 输出）
```

退化输入定案（2026-09-27 用户拍板）：resourceCode 空白串归一 TYPE_LEVEL；
context 顶层 evaluatedAt/timestamp 保留键由 CallerContext 结构拒绝（400 VALIDATION_FAILED），clientIp 提取不受影响。

## Domain 层 API（范围/LEGACY_API/视图/转授全部直构 QueryRequest）

授权传递校验使用 `PermissionGrantDomainService`。

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

// grant check — 授权传递检查（canGrant 校验；T-PERM-091：GRANT_LIST＋PRESERVE+SKIP＋FACTS＋
// 读来源 DATABASE〔写校验面新鲜度〕，操作定义装载留领域侧〔2026-09-27 拍板〕）
boolean canGrant = permissionGrantDomainService.canGrantPermission(
  tenantId, subjectId, resourceTypeCode, resourceCode, codeType, operationCode, scopeAll, domainCode
);

Map<String, PermissionGrantDomainService.GrantCheckResult> results =
  permissionGrantDomainService.checkCanGrant(tenantId, subjectId, permissions, domainCode);
```

## 引擎执行管线（现行唯一管线）

```
execute(QueryRequest)
  主体解析（User→互斥过滤后角色集 / Roles 直供）
  → TYPE_GRANT / INSTANCE / GRANT_LIST 分阶段评估（scopeAll 先行、候选按 clause 精确切分、
    条件评估、互斥 ENFORCE 与证据受控提交〔ConflictEvidence：execution＋item＋stage＋ruleRef 聚合〕）
  → 事实（GrantFact 保留/raw）与展示投影（描述块/操作覆盖/父子展开）
（完整设计：docs/design/r2-unified-query-and-admission.md §4；实现：docs/design/engine/implementation.md）
```

**判定/展示两语义拆分**：判定面继承（`Inheritance.SELF_AND_ANCESTORS`，查询前目标∪同类型祖先链，
改变 allowed/denied）≠ 展示面展开（`PresentationExpansion`，查询后克隆 `grantSource=INHERITED`，
不改变判定）。**角色互斥经 User 主体内部等价解析进全部判定入口**（T-PERM-075 共同判定语义；
判定面外部消费角色集时经 `PermissionConflictDomainService.resolveJudgementRoleIds` 同源口径；
写守卫看原始持有窗口经 `SubjectDomainService.batchResolveRawHoldings`，不经此入口）。

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

- ❌ 禁止使用 `ResourcePermissionValidator`（已删除）— 使用 `QueryGate`
- ❌ 禁止使用 `OperationType` 枚举（已删除）— 使用 `OperationCode`
- ❌ 禁止使用 `ResourcePermissionStrategy`（已删除）— ID转换由 Engine 内部处理
- ❌ 禁止引用/恢复 `PermQueryEngine`、`PermQuery`、`PermResult`、`PermBatchQuery`、`PermBatchResult`、`TargetMode`、`ResolveContext`（T-PERM-092 删除，X04 退役锁）
- ❌ 禁止直接调 `rolePermMapper.selectListByQuery()` 做权限判定 — 通过 Engine
- ❌ 禁止在 service impl 中写权限查询逻辑 — 通过 Engine
- ❌ 禁止 new `RolePermEntry(...)` — 使用 `RolePermEntryMapper`
- ❌ 禁止私有 `loadResources/loadOperations/loadRoles` — 使用对应 Mapper 批量查询（`selectValidByIds(tenantId, ids)` 等；`EntityBatchLoadDomainService` 已删除勿引用）

## 相关文件

| 文件 | 说明 |
|------|------|
| `engine/query/QueryGate.java` | 判定面薄门面（四方法，T-PERM-089） |
| `engine/query/QueryExecutionEngine.java` | 唯一执行主体 execute |
| `engine/query/QueryEngineConfiguration.java` | 引擎 Bean 装配（T-PERM-089 起） |
| `engine/util/PermResultUtils.java` | 新结果→外部响应纯转换 |
| `engine/constant/OperationCode.java` | 操作码常量 |
| `type/enums/ResourceTypeCode.java` | 资源类型常量 |
| `engine/util/OperationPermissionUtils.java` | 位运算 |
| `engine/util/ConditionEvalUtils.java` | 条件评估 |
| `engine/util/RolePermEntryMapper.java` | 实体→VO（ROLE_PERM_SNAPSHOT 缓存载荷例外保留对象） |
