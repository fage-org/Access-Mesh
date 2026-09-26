---
doc_type: design
title: R2 权限查询引擎统一与操作准入（方案 A）设计
status: adopted
domain: access-service
last_reviewed: 2026-09-26
---

# Access-Mesh：R2 权限查询引擎与 T-PERM-054 统一设计

**v3.1｜2026-09-25｜设计定稿（用户确认入库；评审三项拍板已并入正文）**

**主线：R2-A 统一内部查询契约与执行生命周期；T-PERM-054 采用方案 A——接口检查操作准入，业务服务检查具体实例。**

| 项目 | 本版依据与边界 |
|---|---|
| 核心设计原稿 | 《Access-Mesh_R2_权限查询引擎详细设计_v2.0.md》（2026-09-23，完整稿，1288 行），不是 v2.1 精简版 |
| 方案 A 依据 | 《Access-Mesh_R2与T-PERM-054_方案A统一实施报告_2026-09-25.md》，以及本轮前的权限改造汇总、报表实例授权讨论 |
| 代码基线 | 原稿依据 `9778ffc9`；方案 A 依据 `9ba64cf2c0a373554a174d59b83148b5f0b7f07f`。本轮重读 `feat-permission-center`，仍为后者 [B00] |
| 复核范围 | 分支、`computeInstanceDenied`、`AccessCacheCatalog`、T-PERM-054 任务卡；其他源码事实沿对应固定提交报告引用，不声称重新审完全仓 |
| 决策状态 | **定稿（2026-09-25 用户确认）**：方案 A 方向与 R2-A 结构约束实现；当日三项拍板已并入正文（§2.2 时区不处理、§5.1 S/H/D、§8.4 configGeneration 限定语义）。实施载体=计划 r2-query-engine-and-admission（T-PERM-080~094 + T-ACCESS-056~062）；T-PERM-054 已解除暂缓归入该计划。计划完结时内容按现行规范回写对应设计文档族（engine/implementation.md、契约总册、services/gateway.md），本稿转 superseded 随计划归档（2026-09-25 拍板，沿 permission-query-unification 先例） |
| 验证状态 | v3.0 评审（2026-09-25）对 HEAD 9ba64cf2c 完成 25+ 项代码级事实复核（PQ-01/02/03/05/06、缓存目录边界、任务卡、映射实体与 FULL 清理、快照装配/网关 fail-closed/菜单类型页/条件工具/旧 DTO/checkInterface），勘误已并入本版；未修改仓库代码，未执行编译、集成测试、运行库盘点或性能基准。文中代码为设计示意，不是已实现类 |

本版可独立阅读：必要的契约、算法、反例、调用方迁移和验收均放在正文；附录 A 对照原 v2.0 的保留位置。旧稿的“接口和快照只认 API:ACCESS”仅保留为 **LEGACY_API 迁移期规则**，不再是终态。其他 IMP 写侧解耦和继承触发依赖仍独立，不因本版重开或自动实施。

> **v3.1 修订（2026-09-25 定稿）**：①时区拍板——本项目不做时区处理（§2.2/N26 收敛）；②角色互斥采用 S/H/D 全命中确定化（§5.1，终结 [历史定案原文](../archive/2026-09-26/decision-registry-before.md) 2026-09-22 留观②）；③configGeneration 保留、语义限定为构建期自一致校验（§8.4）；④评审勘误并入——§2.2 Roles 视角不过滤 ROLE_MUTEX、§7.2 准入候选来源措辞（任意实例原授权，无祖先闭包）、§8.3 旧协议 operationCode 已删（T-PERM-053）、§6.6 ACCESS 覆盖位精确口径、§9.1 字段名对齐现物。

## 1. 目标、现状与总体结构

### 1.1 最终只保留一份权限查询执行主体

```java
QueryResult execute(QueryRequest request);
```

```text
HTTP／SDK DTO、管理门禁、范围／转授／视图、接口准入
                         ↓
        可信应用入口：校验业务输入，构造内部请求
                         ↓
       唯一 execute：规范化 → 共享装载 → 分集合评估
                         ↓
      DecisionResult / GrantSetResult / AdmissionResult
                         ↓
        响应转换、范围投影、准入快照或领域最终判断
```

单条就是一个 QueryItem，独立批量就是多个 QueryItem。不同选择可以触发不同的固定阶段，但不复制主体解析、条件求值、规则计算、时钟和审计。便利方法只能构造请求或提取结果，不能另查授权 SQL、另判互斥，或调用旧完整引擎。

统一指标不是“只有一个 Java 类”或“所有请求只有一条 SQL”，而是：**共享原始数据，明确判定集合；真实消费者全部迁移；旧完整执行体与四个旧引擎 DTO 退出。**

### 1.2 当前问题及处理位置

| 编号／事项 | 已有依据 | 本版处理 |
|---|---|---|
| PQ-01 | `getDenied*` 对批量实例先整体互斥，再映射目标；`queryBatch` 按 item 收窄。本轮复核时前一实现仍在 [B01][E01] | 所有独立目标按 item＋阶段评估；物理合批不合并判定集合，见第 4 节。最小修复已先行落地（T-PERM-095，2026-09-26：`computeInstanceDenied` 按目标闭包切分各自 PERM_MUTEX——沿 queryBatch 逐 item 形态，经请求级批量评估器共享装载） |
| PQ-06 | 角色互斥依赖不断缩小的结果集，存在规则顺序依赖 [C11] | 对原始有效角色集计算全部命中，再一次删除端点；先修后供全部入口复用 |
| PQ-02／04 | 多类型操作装载逐类型调用；辅助输出在开关判断前重复解析 [E01][E11] | 多类型批量读；判定数据与展示数据分开；最小输出不做展示装载 |
| PQ-03 | 每个 item 扫描整批实例行 [E01] | 同一个 CandidateSelector 内比较扫描与类型／实体索引，不按入口维护两套算法 |
| PQ-05／日志归因 | 空规则仍装载操作；单／批纯计算重复；用冲突端点反推规则 [E08] | 空规则短路；计算直接返回真实 triggeredRuleIds；根执行统一提交证据 |
| T-PERM-054 | 手工映射可绑非 API 实例，却缺业务操作引用；在线接口固定 API:ACCESS，快照亦是旧语义 [C01]～[C05] | 新模式“已注册接口 → 业务 type-operation → 操作准入”，不再额外授 API:ACCESS |
| 报表 A/B | 已有同类型不同资源实例的独立授权及 check／batch 能力 [C02] | 继续 REPORT_A／REPORT_B 共用 VIEW；网关准入不替代业务实例判断 |

表中问题是源码及历史固定基线的分析，不是线上发生次数或性能测量结果。与实现 HEAD 有变化的调用点，实施前仍需按第 9 节清点。

### 1.3 改与不改

R2 可重做内部 Java 接口、DTO、运行态、编排及输出适配；普通 `auth/check`、`batch-check`、范围等 HTTP／SDK 契约默认不变。T-PERM-054 允许独立增加版本化的映射／同步／准入协议。

不重建 `role_resource_permission`，不改变 scopeAll、操作位、MANUAL／AUTO_DEP 等真值，不把 `depend_on` 解释为资源父子关系，不在查询时写自动授权，不建立通用策略编排平台。**注册 API 可以保留，API 独立业务授权退出；这两件事不矛盾。**

## 2. 请求契约：把主体、目标和评估责任表达完整

### 2.1 顶层模型

以下名称用于设计沟通，正式实现按项目包结构和编译级别落位。

```java
record QueryRequest(
    long tenantId,
    Subject subject,
    CallerContext context,
    ReadOptions reads,
    List<QueryItem> items
) {}

record QueryItem(
    String key,
    Selection selection,
    Evaluation evaluation,
    ResultForm resultForm,
    OutputSpec output
) {}
```

| 字段 | 明确约束与原因 |
|---|---|
| tenantId | 来自可信租户上下文，正数；所有 SQL、缓存、类型与目标解析带同一租户 |
| subject | `User(userId)` 或 `Roles(roleIds)`，不得同时填后再猜优先级 |
| context | 可信 clientIp 与受限 JSON 属性；服务端固定评估时刻，不接受普通客户端覆盖时钟 |
| reads | 只开放已有明确需要的 LIST 授权来源；其他来源由固定读取矩阵决定，不提供含糊的 allFresh |
| items | 保留原顺序，key 唯一；相同目标可出现多项。空 items 合法，零权限 I/O 返回空结果 |
| selection | 封闭的四种选择；每种都有明确候选范围，不用大量 nullable 字段模拟模式 |
| evaluation | 内部的条件／权限互斥策略，受合法组合表和可信工厂约束；不是外部安全开关 |
| resultForm | DECISION、FACTS、ADMISSION 三种完成目标，不可相互冒充 |
| output | 仅控制事实保留、描述、投影与 TRACE；不能关闭判定必需计算 |

**第一版混批约束：**同次 execute 只有一个租户、主体、调用环境；普通 TYPE_LEVEL／TARGET_SET 可混批；存在父要求时至多一个不同的父要求。GRANT_LIST 单项独占请求。新增 OPERATION_ADMISSION 可多项同批，但首版不与普通目标或 GRANT_LIST 混批，且同批使用同一结果形式。没有当前需求的任意多父、多环境、多清单调度暂不建设；这不是保留多个执行器。

### 2.2 主体、可信入口和不可变输入

`User` 使用有效角色＋ROLE_MUTEX 的共同入口；普通运行时调用不能关闭角色互斥。`Roles` 表示可信内部指定角色视角，不证明用户真实持有，不补加角色；`Roles(empty)` 返回 NO_ROLE，**不回退登录用户**。**Roles 视角不做 ROLE_MUTEX 过滤**（沿现行显式 roleIds 先例，[历史定案原文](../archive/2026-09-26/decision-registry-before.md) 2026-09-22 T-PERM-075「显式 roleIds 分支不过滤」）；ROLE_MUTEX 过滤仅发生在 User 主体解析——诊断模拟 Roles({R1,R2}) 且 R1-R2 互斥时事实完整返回，不因互斥清空。查看他人权限、角色配置、TRACE 的管理门禁仍在应用层。

内部 QueryRequest 不直接作为 Controller 的 JSON DTO。普通外部调用不能指定 Roles、PRESERVE、SKIP、任意读取来源或内部原始实体 ID；合法外部业务键由适配层转换。新准入端点只允许经过认证的网关／服务调用，不让前端自行选择“准入后当最终允许”。

请求集合及嵌套 context 防御性复制；只接受 JSON 值，拒绝循环和任意可变 Java 对象。保留键 clientIp／evaluatedAt／timestamp 不得通过 attributes 覆盖。运行态使用注入 Clock 固定一个服务端时刻；父项共用它。**时区拍板（2026-09-25）**：本项目不做时区处理——日期／时间条件按评估进程本地时钟语义评估，不引入 ZoneId 抽象；现行实现由 common `UtcTimezoneEnvironmentPostProcessor` 启动即强制 JVM 默认时区 UTC（代码级，T-ACCESS-024；含 @SpringBootTest 与 E2E 子进程），部署侧无需任何时区约定。R2 重构不得改变现行评估时钟语义。

`CallerContext` 过滤顶层 null 键及各层 null 值，嵌套 Map 的非 String 键拒绝。clientIp/evaluatedAt/timestamp 保留键限制只作用于顶层，未来属性展平仍须保持该边界。它与旧 `PermEvalContext` 浅过滤的接受面差异属于迁移期设计内差异，替换旧消费方时核对，不在骨架期机械统一。[来源](../archive/2026-09-26/decision-registry-before.md)（原第 202 行）。

### 2.3 类型、操作和资源必须保持配对

```java
record TypeOperation(String resourceTypeCode, String operationCode) {}

sealed interface ResourceRef permits ByCode, ByEntityId {}
record ByCode(String code, String codeType, String domainCode) implements ResourceRef {}
record ByEntityId(long entityId) implements ResourceRef {}

record TargetClause(TypeOperation operation, ResourceRef resource) {}
```

`ByEntityId` 只表示 `resource_entity.id`，不能把 sys_user.id、roleId 或 type_definition.id 混进来；也不能把 Long 转字符串塞进 ByCode。ByCode 沿既有 codeType 缺省、大小写及业务键解析，不额外 trim 或大写化改变身份。[E05][E13][E18]

例如本项要求 `X/VIEW + Y/EDIT`，不能先拆成 `{X,Y} × {VIEW,EDIT}`。物理 SQL 可以装载这个超集，但候选选择必须还原原配对；X/EDIT 只有在它真实覆盖 X 所请求 VIEW 时才可进入候选，不能因 Y 需要 EDIT 而串入。

### 2.4 四种 Selection

```java
sealed interface Selection permits TypeLevel, TargetSet, GrantList, OperationAdmission {}
record TypeLevel(List<TypeOperation> requirements) implements Selection {}
record TargetSet(List<TargetClause> clauses, Inheritance inheritance,
                 TypeFallback typeFallback, ParentRequirement parent) implements Selection {}
record GrantList(ParentRequirement requiredParent) implements Selection {}
record OperationAdmission(TypeOperation requirement) implements Selection {}
// Inheritance: SELF / SELF_AND_ANCESTORS
// TypeFallback: ALLOW / DISALLOW
```

| 选择 | 候选和完成含义 | 不代表什么 |
|---|---|---|
| TYPE_LEVEL | 仅适用的类型级主授权；一项内所有 requirements 构成一个候选集合，经评估后有保留事实即成立 | 不是“持有任一实例权限就通过”，也不是所有 requirements 必须分别通过 |
| TARGET_SET | 每个 clause 的目标／允许祖先授权联合成**本项**候选；类型级回退独立配置 | 不是逐 clause 先求 boolean 再 OR／AND |
| GRANT_LIST | 指定主体的一份完整授权事实；保留原清单评估与父上下文语义 | 不是具体业务请求的最终许可；展示筛选不天然等于源头过滤 |
| OPERATION_ADMISSION | 一个 type-operation 的候选资格，可来自 ALL 或实例，在线评条件但不做实例 PERM_MUTEX／运行时父绑定 | 不定位本次资源，不证明有任一实例已经完整鉴权成功 |

**必须保留的反例：**X 只有 VIEW，Y 只有覆盖 VIEW 的 UPDATE，VIEW 与 UPDATE 互斥。X、Y 是两个独立 item 时可以均允许；一个 TARGET_SET 同时包含 X、Y 时，互斥可以将集合清空。它们不能因“最终都是 ANY”而合并。[E21]

需要两个权限都通过时，构造两个独立 DECISION 项，再由业务明确 AND。同资源两个不同操作也不能未经语义确认合为一个 ANY 集合。

### 2.5 评估选项与合法组合

```text
ConditionMode = EVALUATE | PRESERVE
MutexMode     = ENFORCE | SKIP
ResultForm    = DECISION | FACTS | ADMISSION
```

| Selection／结果 | 条件／PERM_MUTEX | 合法性与完成目标 |
|---|---|---|
| TYPE_LEVEL／TARGET_SET ＋ DECISION | EVALUATE＋ENFORCE | 合法；普通最终鉴权 |
| TYPE_LEVEL／TARGET_SET ＋ DECISION | 任一 PRESERVE 或 SKIP | 非法，零授权 I/O 报结构错误 |
| TYPE_LEVEL／TARGET_SET ＋ FACTS | 内部允许各组合 | 合法；结果写明实际覆盖，不能转换为普通 allowed |
| GRANT_LIST ＋ FACTS | 内部允许各组合 | 合法；如视图 FULL、转授 PRESERVE＋SKIP |
| GRANT_LIST ＋ DECISION／ADMISSION | 任意 | 非法 |
| OPERATION_ADMISSION ＋ ADMISSION | 固定 EVALUATE＋SKIP | 合法；仅受控准入工厂，恒要求业务最终检查 |
| OPERATION_ADMISSION ＋ FACTS | 固定 PRESERVE＋SKIP | 合法；准入快照收全候选和条件身份，不返回准入布尔值 |
| OPERATION_ADMISSION ＋ DECISION，或其他选择＋ADMISSION | 任意 | 非法，不能借新用途绕过普通鉴权 |

ROLE_MUTEX 是主体阶段职责，不受 MutexMode 影响。PRESERVE 不等于条件通过，只是不按当前 IP／时间过滤；规则是否可下发由投影层处理。普通权限互斥使用**原授予操作**，不先展开 effective 操作再人为制造冲突。

### 2.6 校验、未知对象与空输入

空 TYPE_LEVEL requirements、空 TARGET_SET clauses、空类型／操作、非法实体 ID、重复 key、非法组合／混批属于结构错误，执行前整体拒绝。外部 DTO 原有更严格校验继续保留。

格式正确但不存在的 type／operation／code 是普通查询未命中，按 item 返回拒绝或无事实；不能让一个未知对象变成全批技术故障。共同集合有部分未知时仍按已定义候选语义处理；“全部对象必须存在”由业务预检查表达。**空实例集合或解析失败永不自动升级为 TYPE_LEVEL**。

保持类型级先行短路：允许 typeFallback 时，scopeAll 已足以放行可不检查实例存在性。接口注册门禁必须在进入引擎之前执行，不能借 scopeAll 绕过未注册路由。[E13]

## 3. 结果、事实、输出与错误边界

### 3.1 三种结果不能只剩一个 allowed

```text
QueryResult(executionId, evaluatedAt, orderedResults)
  DecisionResult(key, ALLOW|DENY, reason, evaluationCoverage, details)
  GrantSetResult(key, collectionStatus, evaluationCoverage, details)
  AdmissionResult(key, MAY_ENTER|DENY, reason, evaluationCoverage,
                  finalCheckRequired=true, details)
```

orderedResults 与输入等长、顺序相同；相同目标不同 key 仍各自返回。GrantSetResult 不提供 allowed()；AdmissionResult 不实现最终授权的布尔接口。准入审计 ID 是候选证据，不称为“已访问目标的命中授权”。

FACTS 状态按顺序聚合：NO_ROLE → PARENT_DENIED → 任一阶段有保留事实为 PRESENT → 有 raw 但全部清空为 FILTERED_EMPTY → 其余 NO_MATCH。PRESENT 不表明所有阶段通过，更不表明 PRESERVE 的条件已满足。

### 3.2 真值、阶段事实和展示分开

```text
GrantFact
  permissionId, roleId, resourceType, resourceEntityId
  grantedBits, scopeAll, canGrant, conditionId, hasCondition, dependOn, grantSource
StageFacts
  stage, rawAfterContext, retainedAfterEvaluation, stageStatus
PresentationEntry
  sourcePermissionId, sourceRoleId, displayedEntityId,
  derivation = ORIGINAL | PARENT | CHILD | OPERATION_COVERAGE
```

rawAfterContext 是**上下文绑定处理之后、条件／权限互斥之前**，不是数据库全部原始行。不同 permissionId、角色、条件身份、父授权与来源不能揉成一个位图；同一物理授权被多个 clause 命中可按 permissionId 去掉重复读取，但不能消掉不同授权来源。

展示继承、操作覆盖展开不改 GrantFact.resourceEntityId，不将展示 INHERITED 写成真实 grantSource。hasCondition 与 conditionId 异常不一致时要诊断，不得把条件引用损坏变成无条件授权。

### 3.3 OutputSpec 与执行覆盖

OutputSpec 包括事实档 `NONE / KEPT / RAW_AND_KEPT`、matchedIds、描述块、有效操作展开、展示父／子展开、extraOperationKeys、TRACE。FACTS 至少 KEPT；范围必须 RAW_AND_KEPT；最小 DECISION／ADMISSION 可为 NONE。extraOperationKeys 只供描述／投影，不能扩大 Selection。

展示方向采用 `PresentationExpansion.NONE/PARENTS/CHILDREN/BOTH` 四态，替换不能表达单方向的布尔值；方向只影响评估后的展示投影，保留旧 inheritParents/inheritChildren 的全部组合，不改变判定面 Inheritance。T-PERM-087 验收 A05 的执行覆盖与不补查部分，TRACE 输出和敏感信息门禁整体由 T-PERM-088 实现；087 期间 trace=true 继续明确拒绝，不返回伪 TRACE。（来源：2026-09-26 T-PERM-087 实施时用户确认。）

`extraOperationKeys` 使用 `Set<TypeOperation>`，与 Selection 的类型—操作配对复用同一内部模型；不再以 `"REPORT:EXPORT"` 字符串编码。配对在执行前校验，两字段均须非空白，不改变外部 HTTP DTO（T-PERM-084，2026-09-26 用户确认）。

Details 用 loadedSections 或显式可选块区分“没有请求”与“请求后为空”，不返回 RunState、ORM 可变实体或缓存对象。内部父 matchedPermissionIds 即使不对外展示也必须计算；拒绝项公开命中集保持空，TRACE 仅受权诊断可用。[E14][E17]

| EvaluationCoverage 字段 | 必须能表达 |
|---|---|
| subjectResolution | USER_EFFECTIVE_WITH_MUTEX 或 EXPLICIT_ROLES |
| conditions | EVALUATED／PRESERVED／NO_CANDIDATE |
| permissionMutex | EVALUATED／SKIPPED／NO_CANDIDATE；准入 SKIPPED 并标明延后业务，不写通过 |
| parentCheck | NOT_REQUIRED／NOT_TRIGGERED／PASSED／FAILED；准入另标 RUNTIME_DEFERRED |
| completedStages／skippedStages | 实际完成与短路理由，不为美化解释补查未执行阶段 |
| requestedSelectionComplete | FACTS 成功必须为 true；充分 DECISION 或准入短路不冒称全事实已收集 |
| authorizationStage | FINAL_DECISION／FACT_COLLECTION／OPERATION_ADMISSION；准入恒 finalCheckRequired |

**DECISION 与 FACTS 的重要差别：**TARGET_SET 允许类型级回退时，DECISION 在 TYPE_GRANT 通过即可停；FACTS 必须继续收集所选实例阶段，两个阶段仍独立评估。精确实例配置用 DISALLOW 避免读类型级事实。

TRACE 解释真实执行，不是全面配置扫描；scopeAll 短路的 INSTANCE 显示 SKIPPED。确需目标描述时可在结论后为输出补读，但不得因此改变已完成判断；判定 I/O 与投影 I/O 分开统计。

**事实与投影实施边界**：T-PERM-085 已接入 TYPE_GRANT/INSTANCE 的不可变 StageFacts、raw/kept 和命中 ID；T-PERM-087 接入描述、有效操作和四态展示方向。结果的描述使用不可变资源/角色记录及 OperationDefinition，不暴露 ORM 或缓存对象；loadedSections 区分未请求与已装载为空。TRACE 由 T-PERM-088 承接，未实现时明确抛未实现异常，不忽略请求或返回伪完整结果。

### 3.4 原因和异常不混淆

| 情况 | 处理 |
|---|---|
| 无有效角色 | DECISION 为 NO_ROLE；FACTS 为 NO_ROLE；ADMISSION 为准入 NO_ROLE |
| 普通目标任一适用阶段评估通过 | ALLOW |
| 曾有可评估候选，最终条件／互斥清空 | CONDITION_NOT_MET_OR_CONFLICT，优先于仅父上下文排除 |
| 没有上述清空，只因父绑定排除 | DEPENDENT_NOT_IN_PARENT_CONTEXT |
| 其余未命中／合法未知目标 | NO_PERMISSION；事实按 NO_MATCH 等映射 |
| 准入没有候选／条件均不通过 | 分别 NO_CANDIDATE／CONDITION_NOT_MET；准入不能报告已计算实例冲突 |
| 非法结构 | QueryValidationException；保留普通外部参数错误映射 |
| DB、缓存无法可信回源、规则装载、预算／deadline 故障 | QueryExecutionException 或明确配置／技术错误；不得当普通 DENY、空清单或半批成功 |

TARGET_SET 实例未解析时，仍保留之前 TYPE_GRANT 曾被条件／冲突清空的原因，不一律覆盖为 NO_PERMISSION。损坏条件规则按既有四态失败关闭；数据库读取失败则是技术故障，两者分别记录。新内部原因不未经版本化直接扩散到普通 SDK。

**类型级子授权的原因口径（2026-09-26 用户确认）**：TYPE_LEVEL 没有父资源上下文，depend_on 子行不属于其有效候选；仅有此类行时返回 `NO_PERMISSION`，沿用旧单条/批量类型级门禁口径。有主授权候选但被条件/互斥清空时仍返回 `CONDITION_NOT_MET_OR_CONFLICT`。`DEPENDENT_NOT_IN_PARENT_CONTEXT` 适用于 TARGET_SET 的父上下文排除，不因类型级选择排除子行而产生。

## 4. 唯一执行器：阶段算法与父上下文

### 4.1 职责划分和一次执行的生命周期

| 部件 | 唯一职责 | 禁止越界 |
|---|---|---|
| PermQueryEngine（终态就地改造为唯一 execute 主体；**迁移期注（T-PERM-082，2026-09-25 拍板）**：execute 骨架先以 `engine.query` 包独立类 `QueryExecutionEngine`〔暂名〕落位——暂不注册 Spring bean、零生产消费者、旧 PermQueryEngine 零改动，终名随 T-PERM-092 删旧类时定） | 结构校验、根时钟、固定阶段、结果完成与证据提交 | 按旧方法名分发回旧 query／queryBatch |
| RunState | 此次时刻、角色、已读／缺失记忆、规则、条件结果、父结果、原始行、审计 | 单例字段、ThreadLocal、整个 HTTP 请求跨写复用 |
| QueryReadSupport | 有租户／空集守卫的批量 DB／缓存访问与同源复用 | 缓存已被某 item 过滤过的候选结果 |
| CandidateSelector | 按规范化选择＋阶段切出精确候选 | 自行查库、把展示过滤提前 |
| CandidateEvaluator | 使用共享绑定、条件、纯互斥能力执行封闭策略 | 发第二次审计，复制一套业务专用条件解释器 |
| QueryProjector | 组装事实、覆盖操作、描述和展示派生 | 重新鉴权、反向修改候选或 ALLOW |
| QueryAuditCollector | 收集真实规则／角色对和项关系；根级一次提交 | 用端点猜规则、把准入记为业务成功 |

**迁移期适用边界**：T-PERM-085/086 已接入 User 有效角色＋纯 ROLE_MUTEX、TYPE_GRANT/INSTANCE、父要求与 GRANT_LIST，T-PERM-087 接入完整输出投影；C06 同序等长判定、R03 显式角色完整清单由执行链测试覆盖。新类仍不注册 Spring bean、无生产消费者，旧执行器保持现役。ADMISSION_CANDIDATES 由 T-ACCESS-057 承接；非空角色触发尚未实现的选择时抛 `UnsupportedOperationException`，不伪装普通 DENY 或空事实。NO_ROLE 终态继续适用于全部结果形式，并可按输出要求装载额外操作定义；不因此装载授权。TRACE 由 T-PERM-088 承接，在主体读取前明确拒绝该未实现输出。[骨架边界来源](../archive/2026-09-26/decision-registry-before.md)（原第 201 行）；输出范围见 §3.3，父与清单实现见 §4.5/§4.6 及 [T-PERM-086](../tasks/T-PERM-086.md)。

不要求每行新增独立 Spring Bean，包内 helper 也可；依赖方向是应用→引擎→既有读／领域能力→Mapper。转授服务可调用引擎，引擎不能反注入授权计划／物化服务。

```java
QueryResult execute(QueryRequest request) {
    validateStructureAndAllowedCombinations(request);
    if (request.items().isEmpty()) return emptyResult();
    RunState run = startRun(request, clock);       // 不改写请求
    try {
        resolveSubjectOnce(run);
        if (run.roles().isEmpty()) return noRoleResults(run);
        prepareRequiredTypeOperations(run);
        processTypeGrantStage(run);
        processInstanceStage(run);
        processGrantListStage(run);
        processAdmissionStage(run);              // 仅准入选择触发
        projectRequestedOutputs(run);
        return completeOrderedResults(run);
    } catch (RuntimeException error) {
        run.recordExecutionFailure(error);
        throw preserveTechnicalFailure(error);
    } finally {
        submitConfirmedEvidenceOnce(run);        // 不覆盖主异常
        releaseRunReferences(run);
    }
}
```

这是阶段说明，不是可直接编译的实现。父项在必要时复用阶段函数，不递归调用公开 execute；新核心也不调用旧完整核心。查询期间不写授权事实。

### 4.2 装载批与判定集合必须分开

四个单位分别是：请求（共享环境）、item（独立判定／事实集合）、stage（TYPE_GRANT／INSTANCE／GRANT_LIST／ADMISSION_CANDIDATES）、LoadBatch（物理 SQL／缓存读取）。**SQL 合批可以变大，item＋stage 的候选不得随之变大。**

规范化时保留原下标、key、类型—操作—目标配对、继承、父要求。相同 type／operation／code 的已读成功和 NOT_FOUND 都记忆；Map.get()==null 不能同时代表未读和不存在。ByCode 按精确解析键合并，ByEntityId 不做编码反查。

物理授权可以按类型 OR 掩码、按目标／祖先并集装载超集。规范化项及共享原始行均不可变；某项条件不通过，只生成该项 retained，不从共享原始行删除。

### 4.3 TYPE_GRANT：先证明类型级是否足够

适用项为 TYPE_LEVEL，以及 typeFallback=ALLOW 的 TARGET_SET；DISALLOW 项不参与。

1. 汇总各项类型覆盖掩码，用现有批量 scopeAll SQL 装载。
2. 按每项自己的类型—操作要求切回候选。TYPE_LEVEL 排除 depend_on；TARGET_SET 按其父上下文规则处理。
3. 只为绑定处理后真正需要评估的候选批量预载条件；条件后计算本阶段 PERM_MUTEX。
4. DECISION 有保留候选就完成此项。TYPE_LEVEL 清空则拒绝；TARGET_SET 清空则记住原因，继续实例。
5. FACTS 保留本阶段。TARGET_SET 即使本阶段有事实，也继续收集所选实例范围。

阶段结果去重键必须包含真实候选、type-mask 配对、绑定／父结果与评估策略，不得只按资源类型去重。完整候选的互斥没有做完之前，不能见到第一条授权就返回 ALLOW。

T-PERM-085/086 以 `CandidateSelector` 切回配对、`CandidateEvaluator` 复用领域条件与纯互斥计算。阶段评估记忆键包含完整 Selection、stage、解析后的 type-mask/closure、上下文后 raw、评估策略及实际父命中权限 ID 集，输出开关不参与判定。条件在每阶段上下文过滤后按需要求值的候选并集预载，PRESERVE 不加载规则；父项固定 FULL，不继承根项弱策略。真实互斥 ruleId 与角色对暂存 RunState，根审计提交和故障证据由 T-PERM-088 实现。

### 4.4 INSTANCE：按原配对切回候选

仅处理尚需实例决策或完整实例事实的 TARGET_SET。

1. 批量解析这些项的业务码；类型级已短路且不需要描述的项不解析实例。
2. 只对 SELF_AND_ANCESTORS 项查询同类型祖先闭包，保存 target→closure；SELF 恒为自身。合法目标闭包缺省仍含自身。
3. 按闭包并集及类型掩码并集查询实例授权。空角色、实体或掩码集直接短路，不能调用会省略过滤条件的无界 SQL。
4. 扫描或建立一次原始行索引。每个 clause 分别执行同类型＋本 clause 掩码＋本 clause 闭包，再在本 item 内合并、按 permissionId 去物理重复。
5. 上下文绑定→条件→PERM_MUTEX，组装此项结果及真实命中证据。

```text
C(item) = 对 item 中各 clause c 求联合：
  g.scopeAll = false
  且 g.resourceType = type(c)
  且 g.resourceEntityId ∈ closure(c)
  且 (g.grantedBits & coveringMask(c)) ≠ 0
```

SELF 不能消费同批其他项加载的祖先；不同类型相同 bit 不相互覆盖；不同 item 的候选不能共同互斥。一个 item 跨多个 SQL 块时，候选必须合齐再计算互斥。

T-PERM-085 先使用同一 Selector 内顺序扫描作为正确性基线；继承目标合批查询既有同类型闭包 CTE，并在 RunState 记忆目标→闭包，SELF 固定自身集合。读取分块全部完成后才进入逐项候选选择和评估。`QueryProjector` 投影实际完成阶段的不可变事实与命中 ID；可在判定后按输出要求读取描述及展示树关系，不能补跑授权阶段或改变结论。DECISION 类型级充分短路用 `INSTANCE=SUFFICIENT_DECISION` 明示未执行，FACTS 则继续收集实例事实。

**充分决策允许阶段短路，不允许集合内不完整互斥。**这是准入“找到充分候选可以停”和普通实例鉴权的关键区别，见第 7 节。

### 4.5 父上下文：绑定的是父授权记录，不是父资源

```java
record ParentRequirement(String resourceTypeCode, ResourceRef resource,
                         Set<String> operationCodes) {}
```

父要求只支持一层，不能再嵌套父。父内部项固定 SELF、允许类型级回退、EVALUATE＋ENFORCE、只消费主授权；不继承子项 PRESERVE／SKIP。空父操作集不代表不限操作；普通 HTTP 已要求非空则继续拒绝非法输入。

| 场景 | 处理 |
|---|---|
| TYPE_LEVEL | 只认主授权，无父要求字段 |
| TARGET_SET 无父 | 排除 depend_on 子行；主行照常 |
| TARGET_SET 有父但没有子候选 | 不判父，不增加无用查询 |
| TARGET_SET 有子候选 | 惰性计算父，保留主行及 dependOn 命中父 matchedPermissionIds 的子行；父失败不删除独立主行 |
| GRANT_LIST 无父 | 保留存储子行参与原清单流程；哪些消费方之后排除由装配契约决定 |
| GRANT_LIST 有父且源非空 | 父是整清单门禁，父失败→PARENT_DENIED；通过后过滤不匹配子行，再定义 rawAfterContext |
| GRANT_LIST 源为空 | 返回无事实，不为填结果强制判父 |
| OPERATION_ADMISSION | 不执行上述运行时父要求；仅核查子行结构并标待业务验证，见第 7 节 |

父结果在同一 RunState 内按完整规范化要求记忆。共享角色、时刻、定义和条件，可为父做必要的额外授权读取，不能错误复用根项范围更窄的事实。父 scopeAll 已命中时，其真实权限 ID 就是绑定集，不能再扩读父实例来扩大可用子授权。

T-PERM-086 实现中，沿 `ParentRequirement` 已有构造前归一约定按完整字段值判等；父项独立于根项执行表，调用方 key 不会覆盖内部父项。父阶段事实、命中 ID、真实互斥规则与受影响根项 key 的关联保存在一次 RunState 中，结束即释放；父与根共享评估时刻和条件读取记忆，父不递归公开 execute、不独立提交审计。[实施证据](../tasks/T-PERM-086.md)

`queryScopes` 的“父对象是否存在”预检查留在外层，OBJECT_KEY_NOT_FOUND 行为保持；存在不等于父权限允许。普通无父 GRANT_LIST 的子行可能先参与原 LIST 条件／互斥、后在装配中隐藏，不能以统一为由提前全部删掉。[E14][E15]

### 4.6 GRANT_LIST：事实完成目标不能被短路或分页破坏

按明确的 DB／原始角色快照来源装载清单→必要父整集合门禁→保存 rawAfterContext→按 Evaluation 做条件／权限互斥→保存 retained→输出后置筛选、范围分桶、展示和分页。

本版不把原 queryResources 的 type/op/domain/codeType 或权限码白名单提前到授权 SQL。反例：A、B 冲突而页面只显示 A；先互斥再筛 A 是空，先筛 A 再互斥会让 A 复活。只有证明某个过滤不改变该用途集合语义后，才能单独下推。

操作描述至少覆盖 raw 所涉类型，以及 output.extraOperationKeys 的目标类型。retained 为空也不能缺操作定义；无授权不等于目标操作不存在。

T-PERM-086 已接通完整清单评估与最小 StageFacts 输出，保留绑定后 raw 供 T-PERM-087 的描述/范围投影消费；不提前下推展示过滤。父门禁拒绝时返回 `PARENT_DENIED`，不伪装成成功的空清单。按 §3.3 的实际执行覆盖约束，此时 `parentCheck=FAILED`、`GRANT_LIST` 以 `PARENT_DENIED` 标为跳过、`requestedSelectionComplete=false`，不输出未执行阶段的事实。源为空则不判父，完成空清单并返回 `NO_MATCH`。这些是内部执行说明，外部响应仍由对应迁移卡保持原契约。[实施证据](../tasks/T-PERM-086.md)

## 5. 共享规则、读取来源、缓存与资源控制

### 5.1 条件和互斥只有一份计算能力

**条件**继续用现有批量评估器及 OK／NOT_FOUND／DISABLED／INVALID 四态，非 OK 不通过。每阶段绑定处理后批量 preload；同条件、同次环境结果记忆，PRESERVE 不向布尔结果缓存写 true。缓存 miss 批量回源，只按已有安全规则回填；规则缺失／禁用／损坏不变成无条件。[E07]

**PERM_MUTEX**先读取规则；成功读到空规则时，不再装载互斥专用操作目录。规则存在时，从候选的原授予位按 type＋bit 取得操作 ID，对原候选集合判断每条规则两端，收集全部命中端点再过滤；直接返回真实 triggeredRuleIds。不得边删除边判下一规则，也不通过冲突端点 OR 反推规则。[E08][E09]

**ROLE_MUTEX**以原始有效角色集 S 为基础：

```text
H = { (a,b) | 规则(a,b)存在，且 a∈S 且 b∈S }
D = H 全部端点的并集
effectiveRoles = S - D
```

持 A/B/C/D，规则 A-B 与 B-C：结果只剩 D，与规则顺序无关。仅持 A/C/D、没有 B：两规则均不触发，不能按图连通性传递删除。此修复是异常持有状态的确定化，不声称正常写入口原本允许任意制造双持。

**（2026-09-25 用户拍板采纳为定案算法——终结 [历史定案原文](../archive/2026-09-26/decision-registry-before.md) 2026-09-22 留观②「同角色多互斥对双删顺序不确定」。删多为预期收紧：持 {A,B,C} 且规则 A-B、B-C 时，现行顺序依赖结果至少保留一个链端角色，S/H/D 三角色全删；灰度差异按 §10.5「预期修复」登记。）**

现有角色规则缓存可能仅存角色对；证据用 RolePairRef，有真实 ruleId 才附带。引擎调用不立即通知的角色判定能力，避免旧 resolveJudgementRoleIds 先通知、根执行再发一遍。仍在迁移的旧入口也复用同一纯计算，不保留另一种冲突算法。

> **就地实施注（2026-09-26，T-PERM-083）**：S/H-D 定案算法、`computeRoleMutex`/`computePermMutex` 纯计算（不通知）与 `RolePairRef` 配对证据（uk_conflict_rule_role 唯一约束下对↔规则一一对应，缓存载荷不扩 ruleId）已就地落地于旧执行体——旧入口 `filterRoleMutex`/`filterPermMutex` 保持签名、内部复用同一纯计算叠加去重通知（引擎调用点零改动，2026-09-26 用户拍板「域服务内解耦」）；PERM_MUTEX 空规则短路（I01）与真实 triggeredRuleIds 直返同批落地，与 T-PERM-095（PQ-01）共同构成 §9.3 最小正确性修复基线。

### 5.2 明确读取矩阵，不使用一个 FRESH 标签掩盖差异

| 数据／用途 | 默认来源 | 请求内复用与限制 |
|---|---|---|
| 用户有效角色、ROLE_MUTEX | 既有 L2_ONLY、10 秒目录 | 根执行一次；新准入与普通最终鉴权都执行角色过滤 |
| TYPE_GRANT／INSTANCE 授权 | 目标下推的数据库读取 | 不被 LIST 快照替代；同源原始事实可共享 |
| GRANT_LIST 授权 | 显式 DATABASE 或 ROLE_SNAPSHOT | DB 模式不读、不回填角色授权快照；快照只保存原始行 |
| 类型码／值 | 既有解析／缓存策略 | 记忆已读空值与缺失，不重复解析 |
| 普通查询操作覆盖掩码 | 既有 OPERATION_PERMISSIONS_BY_TYPE：L1 60 分钟／L2 120 分钟 | 保留既有语义与失效；不称为新鲜数据库事实 |
| 互斥／输出所需完整操作定义 | 数据库多类型读取 | 与缓存掩码目录分桶，同来源可复用 |
| **新 OPERATION_ADMISSION 的操作要求和覆盖定义** | **本版建议固定用新鲜数据库类型目录** | 在线准入和准入快照使用同一算法及此来源；不消费旧长 TTL 掩码目录 |
| 新准入的授权候选 | 首版固定按角色＋type-mask 数据库批量读 | 独立显式查询，无目标展开；不是调用旧 GRANT_LIST 评估结果 |
| 条件规则 | 既有 CONDITION_RULES、L2_ONLY 10 秒及四态回源 | 固定本次环境；PRESERVE 与实际求值状态分开 |
| 资源／角色描述、准入父结构 | 按实际需要批量数据库读取 | 描述只为输出；子候选父结构为准入计算需求，不受输出开关关闭 |

上述现有 TTL 和 RolePermEntry 缓存值类型本轮已核对 [B02]。**保留的是各来源的已有边界，不是承诺普通鉴权、转授或网关全链均无缓存。**新准入固定读来源由可信用途决定，不给普通 HTTP 暴露任意选择。

每个记忆表区分 UNLOADED／LOADED_EMPTY／LOADED_VALUE。只按 ID 读过几个操作，不能标记整个类型目录已完整读取；缓存掩码目录不得覆盖 freshDefinitionIndex。转授没有目标类型授权时，仍须加载额外目标操作定义，区分 INVALID_OPERATION 与 NO_PERMISSION。

**读取部件迁移边界（T-PERM-084，2026-09-26 用户确认）**：本卡仅实现新 QueryReadSupport，I02～I06 在部件级验收；旧 PermQueryEngine 不改动，现役路径的附属读取问题随消费者迁移退出。整体 execute 的判定、投影接线分别由 T-PERM-085/087 验证，部件验收不代表新执行器已承接生产流量。

读取记忆归单次 RunState，键缺失表示未读，Optional.empty/空集合表示已读空；完整类型目录与按 ID 索引分别记忆。`OperationDefinition` 隔离可变操作实体，保留完整字段以维持普通操作缓存载荷；缓存命中只进入掩码桶，不写新鲜定义索引。数据库首次读到的定义在本次执行复用，部分 ID 读取仍需补读完整类型目录。资源键读取复用已解析类型，经现有多类型 Mapper 装载超集后按完整业务键取回；null/空白 codeType 沿既有缺省语义，不增加 trim/大小写归一。RunState 释放时丢弃读取记忆，后续执行重新读取。

**首次未命中边界（2026-09-26 用户确认）**：按 ID 读取的 NOT_FOUND 在同次 execute 保留，即使后续整类型查询返回该 ID，也不推翻该请求的负记忆；下一次 execute 才重新读取。该取舍只约束请求内记忆，不能把旧值或缺失记忆回填到共享长 TTL 目录。

普通掩码目录 miss 必须独立读取本次数据库完整类型行再回填，不复用 RunState 的完整目录、按 ID 正值或负记忆；其模式与 TTL 不变，也不宣称消除既有数据库读取与失效之间的并发窗口。资源超集取回使用 `(resourceType, code, codeType)` 字段元组，避免允许冒号的 code/codeType 在拼接串中塌缩；该元组仅用于内部匹配，不改变 BusinessKeyUtil 的跨层格式。

### 5.3 新准入不能照搬旧快照的缓存安全结论

现有目录明确把长 TTL 操作缓存定义为“不进接口快照内容”，并以受限的安全目录、回源截止和网关 L1 TTL 组成原快照边界。**新准入把业务操作覆盖转换为快照候选，依赖关系已经改变。**若直接使用 L1 60 分钟／L2 120 分钟的旧操作缓存构建新快照，不能仍沿用原有约 30 秒的配置安全边界。[B02]

本版默认：准入在线与构建快照均从新鲜数据库目录解析 required operation 和 coveringMask；事实中的授权／角色／条件仍按各自来源标注。新增缓存目录、路由来源、父结构以及本地 TTL 必须纳入边界校验，回填保留读前令牌／剩余 TTL，不在投影完成后重新起算寿命。

备选是业务操作专用的短 TTL／版本化安全缓存，可降低构建成本，但要同批改写覆盖变化、删除、读前令牌、跨节点失效和启动校验。**它不是本版默认，也不能只换一个 TTL 数字就宣称安全已证明。**即便采用新鲜操作定义，广播＋TTL 仍不是零延迟强一致；旧普通目标查询的操作缓存边界也不会被此次改造自动消除。

### 5.4 事务、时间与缓存载荷迁移

引擎跟随外层事务，不自建 REQUIRES_NEW，不并发使用 Mapper，不承担检查到后续写入的全部竞态保护。固定 evaluatedAt 只固定条件时刻，不固定数据库版本；同源首次读取后复用会改变部分并发可见时机，需作为明确设计记录，而非宣称所有交错与旧流程等价。

一次 execute 完成后释放 RunState。同一事务先查询、写入、再查询，也要创建新的运行态。更强一致性需分别设计 DB 隔离／缓存绕过、完整事实版本检测、写侧锁，不能用一个“强一致”布尔值代替。

**RolePermEntry 与四个旧 DTO 区别处理：**默认保留 `ROLE_PERM_SNAPSHOT` 的现有序列化载荷，在读边界转为 GrantFact；新应用和执行器不再消费旧 PermResult。这样不强迫 R2 同时改已有缓存格式。备选版本化载荷需明确双命名空间、旧写者和双失效；新准入网关快照则因语义不同必须版本隔离，不能借“缓存兼容”复用旧 API 放行结构。[B02]

T-PERM-084 的读取部件已建立该转换边界：TYPE_GRANT/INSTANCE 固定数据库读取，LIST 的 DATABASE 不读写角色快照，ROLE_SNAPSHOT 只缓存原始 RolePermEntry。缓存 miss 在首次数据库查询前获取令牌，同运行态后续 miss、分块和重试沿用该令牌，剩余 TTL 耗尽时只返回读取事实、不回填。T-PERM-085/086 已接入普通阶段与 GRANT_LIST 编排：领域批量条件评估器在本次执行跨阶段及父子判定复用读取记忆；互斥复用读取部件的新鲜目录，不另建或混入长 TTL 掩码桶。I05 的整体 execute 验证覆盖条件缓存冷/热与增量 miss、角色快照冷/热/混合 miss、跨 SQL 分块沿用首次令牌以及预算耗尽不回填；真实 PG/Redis 验证筛空结果不污染原始快照、热清单下父判定仍使用数据库事实，以及 DATABASE 读本事务写入不改角色快照。[实施证据](../tasks/T-PERM-086.md)

### 5.5 SQL、候选算法与预算

现有 scopeAll／instance／list SQL 尽量复用，查询专用入口有 tenant、roles、masks、entityIds 守卫；不能借空集合省略条件变成无界查询。准入新增明确的 type-mask 候选读取，不重载旧 instance SQL 的空 entityIds 含义。[C10]

PQ-02 使用已有多类型 IN 查询：过滤 null，预建请求类型空桶，查询后分组；普通规模一批，大集合按已验证参数预算分块。服务方法调用一次不证明底层一条 SQL，Mapper、实际 SQL、网络往返分开计数。

| 候选算法 | 实现与收益 | 代价／采用方式 |
|---|---|---|
| S：顺序扫描 | 每项按精确谓词扫描本阶段行，易校验、小批分配少 | 约 O(NR)，先作正确性与性能基线 |
| I：类型＋实体索引 | 一次建立 type→entity→rowRefs，各项只访问自身／祖先桶 | 约 O(R＋Σ闭包访问＋Σ候选)，另有保序和哈希成本；密集授权未必获益 |

二者只在同一个 Selector 内切换，不做新策略框架，也不在不同接口各写“优化版”。索引拼桶保留原始行序或明确采用经契约验证的稳定顺序；不能改变展示“第一条”的来源。规则索引同样仅在测量支持时加入。

EngineLimits 是服务端配置，覆盖授权行、闭包规模、内存、上下文深度／大小、deadline、审计与快照载荷；数值需观察和批准，不把参考代码的深度 64 当项目已定限制。外部 batch 上限与内部 getDenied 容量分别盘点。分块不改变候选集合，超限整体技术失败，不能返回半份 FACTS 或尚未完成集合评估的 ALLOW。

## 6. 审计、范围及各消费方的完整落地

### 6.1 审计与解释的提交责任

```text
ConflictEvidence
  executionId, ruleRef(PermRuleId|RolePairRef), evaluationItemId,
  affectedRootItemKeys, stage, actualConflictingOperationIds,
  completion(COMPLETE|EXECUTION_ERROR_AFTER_CONFIRMED_STAGE)
```

按 execution＋实际内部 item＋stage＋ruleRef 聚合。重复用户输入的 key 不同，应分别计影响；共享父被多项引用时是一条父证据关联多项，不虚构多次父冲突。物理读取去重、计算复用、审计输入计数不能混为一件事。

根 execute 统一受控提交，纯规则计算和父项不发日志。保持既有非阻塞提交策略，不宣称持久必达或跨请求 exactly-once；提交失败记录技术日志／指标，不覆盖主查询异常。执行中途失败只提交已确认阶段，标明执行未完成。

> **就地实施注（2026-09-26，T-PERM-083）**：纯计算与通知已按上述责任边界在旧执行体预解耦——域服务暴露不通知的纯互斥计算（§5.1 实施注）；旧入口通知形态：角色双删按「租户×用户×命中对」1h 去重、批量按 (组,ruleId) 聚合、单条 PERM_MUTEX 逐次触发（存量形态，随新核心受控提交收编）。新核心落地（T-PERM-085/088）直接消费纯计算并自管受控提交，不经旧通知入口，无双发窗口。准入事件独立标注 OPERATION_ADMISSION，不能写“业务实例互斥已通过”或“业务操作已成功”。

**旧单条通知的接受边界**：旧单条 PERM_MUTEX 的 summary 拼入规则明细后可能被 512 长度截断，现阶段维持该行为；批量路径每行单规则不在本项接受范围。T-PERM-088 按规则生成 ConflictEvidence 并受控提交实际落地后复核，出现范围外新影响也须复核。T-PERM-095 已把 getDenied* 改为批量 (组,ruleId) 聚合，不能再按旧单条形态扩大本例外。[来源](../archive/2026-09-26/decision-registry-before.md)（原第 206、208 行）。

TRACE 不用另一个时刻重新评条件，不为显示完整过程补跑短路阶段；敏感角色／授权 ID、IP 规则只向经门禁的诊断开放。指标按选择／阶段／结果聚合，不使用任意 resourceCode、permissionId、itemKey 等高基数标签。

纯互斥计算在 T-PERM-083 阶段允许零生产消费者，由 T-PERM-085/088 接入；当前交付证据证明能力可供消费且无通知副作用，不宣称引擎已切换。返回角色集合保持不可变及确定迭代顺序，避免响应数组与分页随 JVM 重启漂移。全量验证让渡已由 T-PERM-095 完成，不再保留为待兑现例外。[来源](../archive/2026-09-26/decision-registry-before.md)（原第 204、205、208 行）。

### 6.2 范围四态：保留 raw 才能正确区分

`queryScopes` 使用一个 GRANT_LIST＋必要父要求、EVALUATE＋ENFORCE、RAW_AND_KEPT。ScopeCoverageProjector 只消费结果和已装载定义，不再查授权／条件／规则；额外请求每个范围 type-operation 的定义。

T-PERM-087 已提供纯 ScopeCoverageProjector：调用方请求 descriptions 并把范围要求加入 extraOperationKeys；投影消费完整 raw/kept 与描述快照，拒绝缺少输出块、未收全或保留条件/跳过互斥的事实结果。NO_ROLE/PARENT_DENIED 仍映射全 DENIED；外部整体原因和父对象预检查由 T-PERM-090 适配层保留。实例以 (codeType, code) 字段元组去重，避免合法字段中的分隔符碰撞；资源有效性沿既有 selectValidByIds 的租户与软删过滤，不额外改变停用资源的现役口径。

父检查摘要由 T-PERM-087 同批提供（来源：2026-09-26 用户确认本次补齐）：ResultDetails.parentCheck.matchedOperationCodes 从已执行父项的 retained 事实和已装载的新鲜操作定义按 covers 投影，只返回父要求中实际覆盖的操作码，排序且不可变；不重评条件、不补跑短路的 INSTANCE、不增加 I/O，也不暴露父权限 ID。摘要独立于事实/描述/matchedIds 输出开关，共享父项只组装一次；实际执行过父判断才有 PARENT_CHECK 块，未触发/无角色时结合 coverage.parentCheck 区分，已拒绝父项的摘要为空。T-PERM-090 将该摘要封装到既有 QueryScopesResp.matchedParentOperations，保留 HTTP/SDK 字段和语义。

```text
类型／目标操作未知，或 rawAfterContext 无覆盖 → DENIED
raw 有覆盖，retained 无覆盖                  → EMPTY
retained 有类型级覆盖                         → ALL
其余按有效实例的 codeType＋code 去重          → INSTANCE
原有实例全部失效                             → EMPTY（不是空 INSTANCE）
```

父对象不存在继续由外层返回 OBJECT_KEY_NOT_FOUND；父整集合门禁失败和 NO_ROLE 映射既有整体原因与分组形状。不能仅靠 retained.isEmpty 判断 DENIED；不能把 raw 改成绑定前原始行。[E14]

### 6.3 转授不是普通 allowed，也不是新引擎分支

`checkCanGrant` 读取 User 主体的 GRANT_LIST＋FACTS、DATABASE、PRESERVE＋SKIP，无展示展开，额外装载待授类型—操作定义。用户有效角色仍走其既有过滤；只有条目条件／PERM_MUTEX 不做运行时筛选。

转授领域继续判断：**同一条真实授权**同时满足 canGrant、无条件、操作覆盖和范围；不得用一行的操作、另一行的 canGrant 拼接资格。目标 ALL 只认类型级资格；实例按现有实例／类型级转授规则，不因运行时有祖先继承就扩大转授。保留 INVALID_RESOURCE_TYPE、INVALID_OPERATION、NO_PERMISSION、NO_GRANT_RIGHT 与授权根诊断的区别；refineGrantOriginMissing 留在领域层，读引擎不反调写侧。[E16]

### 6.4 查询、视图、菜单与配置

`queryResources`、有效权限码和可见资源投影继续先做原 GRANT_LIST 评估，再做白名单、排除 API、domain/codeType、展示与分页。权限码全量聚合，不因分页漏掉有效操作。物理子孙展开在授权集合评估之后，不把展示后代提前加入互斥候选。[E14][E17]

T-PERM-087 的展示展开按保留事实源批量读取既有祖先/后代 CTE，scopeAll 不展开业务实例；PresentationEntry 以真实授权/角色 ID 关联 GrantFact，保留 ORIGINAL/PARENT/CHILD 派生来源。有效操作输出沿 OperationPermissionUtils 覆盖算法，不将不同授权来源、条件或父绑定揉成一行。PRESERVE 保留存储条件字段且对不一致引用记诊断，EVALUATE 对同类损坏保持失败关闭；展示不修正原事实或将损坏条件降为无条件。

有效操作条目的 `derivation` 采用方向优先：父/子展示行保留 PARENT/CHILD；只有源资源上的操作覆盖行标 OPERATION_COVERAGE，源资源上的原授操作仍标 ORIGINAL。是否为操作覆盖通过比较 `grantedOperationCode` 与 `operationCode` 的字符串内容是否不同判断，消费方不得只筛 OPERATION_COVERAGE。例如资源 100 授予 UPDATE 且覆盖 VIEW，展开到子资源 110 后，110 的 VIEW 行保持 CHILD，两个操作码分别为 UPDATE/VIEW；父资源展开同理。方向与覆盖分别由现有字段表达，不新增枚举组合或字段；此口径不改变鉴权结论。（来源：2026-09-26 用户确认保留方向优先；视图迁移验收见 [T-PERM-091](../tasks/T-PERM-091.md)。）

当前新基线的菜单还支持 `resourceCode` 为空的类型页因该类型存在实例授权而显示；具体 A/B 菜单仍分别绑定 REPORT_A／REPORT_B。这个入口仍是“任意有效操作”语义，不等于 REPORT:VIEW 的操作准入，不能复用成接口安全算法。[C15][C16]

角色配置用 Roles＋FACTS；精确实例用 SELF＋DISALLOW，防 scopeAll 混进配置清单。诊断用户真实访问用 User＋DECISION＋TRACE。查看者的管理门禁与被查看角色是否能实际使用权限是两个判定，不因配置展示删掉其带条件授权。

### 6.5 消费者迁移矩阵

| 当前消费者 | 新请求／结果 | 必须保留的外层职责 |
|---|---|---|
| PermissionCheckAppServiceImpl.check | 单项 TYPE_LEVEL 或 TARGET_SET＋DECISION | 外部主体业务键解析、字段校验、SELF 等缺省策略和原响应 |
| batchCheck | 多个独立 DECISION 项 | 原序、重复项结果、请求级父上下文；不得循环 N 次公开 execute |
| getDeniedResourceCodes／getDeniedEntityIds | 每个目标独立 item，纯结果投影 | 输入原键回映射、原祖先策略、空输入与业务异常；语义变化四消费面（资源树、API 映射、资源依赖、权限树 ID 轨）逐面确认「跨 item 冲突从全拒变各自判」可接受（**勘误见本节末 T-PERM-080 增补——四消费面与实际清点不符，清单以增补为准**） |
| AdminPermissionValidatorImpl | 普通最终 DECISION，显式管理继承 | 当前操作者、SecurityException 与技术错误分界；不能改成准入 |
| ResourceManage／TypeDefinition 等直接门禁 | CODE／ENTITY_ID 分型、单或批 DECISION | 对象类型核实、业务事务和现有写守卫 |
| queryResources／queryScopes | GRANT_LIST＋FACTS；范围保留 raw／kept | 原筛选、分页、父存在性、四态与外部线格式 |
| PermissionViewAppServiceImpl | GRANT_LIST FULL＋有效操作投影 | 查看他人门禁、权限码全量、可见后代与新类型页语义 |
| PermissionGrantDomainServiceImpl | DB 原始 FACTS，PRESERVE＋SKIP | 同行转授资格、授权根诊断与写入保护 |
| 配置、解释、PermViewAssembler | FACTS 或 DECISION＋TRACE | 按真实用途选择，删除旧结果依赖，不机械套 GRANT_LIST |
| LEGACY_API checkInterface | 一个 API:ACCESS TARGET_SET 集合 | 原注册门禁和共同集合互斥；迁移期不拆项 OR |
| LEGACY_API interfaceSnapshot／SnapshotAssembler | GRANT_LIST＋PRESERVE／ENFORCE FACTS | 原 API:ACCESS 覆盖、条件分支、注册映射展开；旧模式结束后退役 |
| 新在线 interface-admission | OPERATION_ADMISSION＋ADMISSION | 服务模式、完整路由匹配、唯一 required operation 和新响应 |
| 新准入快照 | 多个 OPERATION_ADMISSION＋FACTS | 完整路由表、条件分支、版本、失效与本地准入协议 |

迁移前清点全仓直接调用、方法引用、反射、缓存序列化、测试夹具与文档，不以这张主要消费者表代替完整引用清单。

> **T-PERM-080 清点增补（2026-09-25）**：全仓清点册已成册——`r2-query-engine-and-admission-plan` 附录 A（生产调用点逐点迁移目标、门面链、结果加工面、缓存序列化面、测试夹具全量分组、活文档清单、容量盘点；行号为 HEAD `320d16a87` 快照）。勘正一处：本节 getDenied 行「语义变化四消费面（资源树、API 映射、资源依赖、权限树 ID 轨）」与实际不符——四面中「资源依赖」（DependencyAppServiceImpl 无 getDenied 调用）与「权限树 ID 轨」（端点已随 T-PERM-059 删除）两面不存在，实际全量消费面清单见附录 A.3；逐面确认以附录 A.3 为准。四旧 DTO（含 PermResult）均不进缓存载荷（唯一缓存接触面=ROLE_PERM_SNAPSHOT 的 List<RolePermEntry>）。

### 6.6 LEGACY_API 兼容边界不能误写成终态

迁移期旧接口仍先检查注册，再对全部匹配 API 资源组成一个共同 TARGET_SET，SELF、ALLOW、FULL；不拆项 OR。旧快照仍从 GRANT_LIST PRESERVE＋ENFORCE 结果投影，仅**有效位覆盖 ACCESS 的操作位集合**（ACCESS 位 ∪ inheritMask 覆盖 ACCESS 的自定义位，与现行 SnapshotAssembler 口径一致）才放行；dependent 条目按旧装配顺序排除；API 的 ALL 展开为**目标服务已注册且启用的路由**，不产生任意路径通配；无条件与各 conditionId 分支保留。

原 v2.0 明确指出旧 LIST 快照与在线共同目标／逐目标可能有不同集合语义，本版不假装它们因重构自动等价。**新准入不从这份旧结果继续加工**，它有自己的明确候选选择，因而不继承旧 LIST 的跨实例 PERM_MUTEX 删除。

<a id="operation-admission"></a>

## 7. T-PERM-054 方案 A：操作准入的精确定义和 R2 实现

### 7.1 两层判定，不生成第二份 API 授权

```text
用户只有 REPORT_A / VIEW
   请求 GET /reports/REPORT_B
     网关：已注册路由要求 REPORT:VIEW
       → 存在覆盖 VIEW 的候选授权，MAY_ENTER
     业务：本次实际目标 REPORT_B / VIEW
       → 完整实例鉴权 DENY，不读取／返回 B 数据
```

B 请求到达业务服务是方案 A 的预期，并不表示 B 获得权限。角色只授业务资源；API 保留注册目录及接口元数据，新模式不要求再授 API:ACCESS，不把业务权限物化为一批 API AUTO_DEP。

`REPORT:VIEW` 只能表示类型和操作，不能表示 REPORT_A／REPORT_B。菜单、平面 permissions 字符串、API 操作准入和实例最终鉴权分别使用各自定义，不能相互替代。

### 7.2 操作准入是“存在候选资格”，不是“存在已完整允许的实例”

给定有效角色集合 S 和要求 o=(类型,操作)，候选授权满足：同租户、属于 S、资源类型一致、授予位覆盖 o、授权及引用结构按既有生命周期规则有效。候选可来自 ALL、任意实例上的原授权（准入无目标定位，不做闭包展开），或结构有效的上下文子行；不展开所有后代，不枚举每个实例做最终检查。

```text
在线准入 MAY_ENTER ⇔ 存在一个结构有效的覆盖候选，
                     其本行条件在当前可信环境下通过。

这不是：存在某资源已经通过父上下文＋实例范围＋PERM_MUTEX 的完整判定。
```

| 检查 | 操作准入 | 业务最终鉴权 |
|---|---|---|
| 租户、用户、有效角色、ROLE_MUTEX | 执行 | 执行 |
| 类型与操作覆盖 | 执行，使用明确 type-operation | 执行 |
| ALL／实例／祖先范围 | 都可提供候选；不判本次目标，不展开树 | 按实际目标、类型回退和合法祖先选择 |
| 本行条件 | 在线评估；本地评可下发分支，否则回源 | 完整评估 |
| PERM_MUTEX | 固定延后，不做跨实例或同实例的最终冲突判定 | 对本 item＋stage 的完整候选执行 |
| depend_on | 只核结构，父运行时校验待业务 | 无父排除；有父验证父结果及 permissionId 绑定 |
| 响应 | MAY_ENTER／DENY，finalCheckRequired=true | ALLOW／DENY |

若为了“更严格”在准入把 A/VIEW 与 B/UPDATE 混成清单互斥，两个原本可分别访问的对象可能都被提前挡住。即使是同实例真互斥，准入也可以 MAY_ENTER、由业务拒绝；审计不得将“未检查”写成“通过”。普通 DECISION 的 ENFORCE 不因此变为可选。

### 7.3 来源、子授权与条件分支

沿现有使用权限语义处理 MANUAL、AUTO_DEP、AUTHORITY_ROOT 等来源；不能为了简化仅查 MANUAL，也不给 AUTHORITY_ROOT 额外准入特权。不同条件／角色／来源的授权保持独立，存在一条无条件来源不能被另一条失败条件覆盖。

上下文子行作为候选时，至少批量确认 depend_on 对应父行同租户、同角色、存在、未按现有规则失效、为合法主行且无嵌套非法结构。**只证明结构，不评父条件、不猜父操作、不伪造父 matchedPermissionIds。**候选标 `CONTEXT_DEFERRED`，业务必须传真实父上下文。若首版选择全排除子行，必须作为缩小范围的备选定案，未迁入的上下文接口继续受原路径保护；不能一边排除一边宣称方案 A 无误拒支持完整。

当前条件类型包括日期、时间和 IP 白／黑名单 [C12]。在线准入、最终鉴权与本地可下发分支使用共同规则算法和明确的评估时刻语义（时区拍板：不做时区处理，§2.2）／可信 IP。gateway_evaluable=false 表示需要远端评估，不表示通过；缺失、禁用、非法条件是不可用分支，不能变成无条件。未来增加对象属性条件时需补准入处理设计；不得默认未知属性为空即放行。

在**相同事实版本、操作定义、时刻和可信环境**下，应证明最终同操作的合法访问不会仅因“没有类型级授权、不同实例冲突、网关没有父运行时上下文”被准入提前拒绝。这不是跨进程数据库／缓存一致性承诺，时间边界和并发授撤要另测。

### 7.4 ADMISSION_CANDIDATES 阶段

本阶段仍由第 4 节唯一 execute 调度，复用原始事实结构、角色与条件能力。

1. 从已匹配路由要求取得 type-operation；读取本次需要的**新鲜完整操作定义**，计算精确覆盖掩码。要求未知或损坏由接口层报告配置错误，不回退任意操作。
2. 为同批要求合并 type-mask，一次或按预算分块读取有效角色的类型／位候选；既包括 ALL，也包括实例。使用明确的 `selectAdmissionCandidatesByTypeMasks` 类方法，不把空 entityIds 当作无限实例。
3. 切回每个要求的候选；对存在的子候选集中读取父结构，非法结构排除并记录诊断；不执行资源树闭包或依赖图。
4. 在线 ADMISSION：批量预载本行条件，求候选存在性；某项已有充分候选可结束，**仅因本用途没有 PERM_MUTEX，才允许这种存在性短路**。拒绝则必须穷尽该项候选。
5. FACTS：不按当前环境删条件分支，完整收集范围、条件身份和候选类别，供准入快照装配；已知坏条件保留不可用诊断或显式排除，不能作为普通无条件分支。
6. 生成独立 AdmissionResult，或声明准入来源与条件未评估的 GrantSetResult；不从旧 LIST／旧网关快照推导。

首版可先完整批量读候选再做逻辑短路，减少 SQL 分页与早停复杂性。后续流式 EXISTS 优化必须保留类型覆盖、子行结构、条件回源、稳定读取／计数和审计含义；不能仅 SQL LIMIT 1 后由一条条件失败判无资格。

多个路由要求相同 type-operation 可以共享候选和计算，但输入项关系仍保留。准入与业务实例检查是两次执行，不共享跨 HTTP 的 RunState、固定时刻或最终布尔缓存。

## 8. 映射、同步、网关快照和业务接入

### 8.1 映射模型：API 是登记对象，业务操作是准入要求

当前映射只有资源引用与服务／方法／路径等信息，没有正式的业务操作引用；手工可绑非 API 实例的死配置不能通过简单删除 API 过滤解决。[C01][C03][C04]

本版默认保留 API 登记实体，减少对同步／目录的冲击；增加明确业务操作引用，不改角色授权真值。

| 对象 | 建议模型与约束 |
|---|---|
| service_config | 新增 api_auth_mode=LEGACY_API／OPERATION_ADMISSION；由可信服务配置控制，迁移期按服务选择，不接受客户端模式头 |
| resource_api_mapping | 保留 resource_entity_id 引用注册 API；新增 required_operation_id；业务类型从操作定义取得，避免两份类型真值 |
| 对外 DTO | requiredPermission={resourceTypeCode, operationCode}，不要求 REPORT_A/B；内部解析为操作 ID |
| 维护来源 | 明确 MANUAL／SERVICE_SYNC／BOOTSTRAP；owner 绑定服务所有权；不要通过 extra 或被绑业务资源推断覆盖权 |
| 授权／依赖表 | 不新增角色—API 授权，不编译为 resource_dependency，不借 depend_on 表达 API 关联 |

操作选择是“接口→业务资源类型 REPORT→VIEW”，不是“菜单 A→该菜单任意权限”。MENU 的配置管理操作与菜单关联 REPORT 的业务操作不能混用。新模式拒绝将 API:ACCESS 重新作为 requiredPermission。

保存时校验登记 API、服务维护权、租户、业务操作有效性，操作必须属于选定类型。操作引用被使用时删除须有明确守卫；先改绑／删除映射再删操作。软删、同步缺失、类型删除等间接路径也要经过同一引用处置，避免仅手工删除有守卫。异常死引用阻断，不能回退 VIEW 或任意操作。操作覆盖改变即使 ID 不变，也必须重算准入投影并失效。

### 8.2 路由匹配：完整配置优先，不能按用户权限挑较弱规则

新模式首版每个登记路由声明一个业务 type-operation。匹配 service＋method＋规范化路径时，必须从该服务完整的已启用路由集取全部命中：

| 匹配情况 | 建议行为 |
|---|---|
| 无注册匹配 | 拒绝，不能因用户有 ALL 而放行未注册路径 |
| 多匹配但业务要求相同 | 去重为一个要求，记录实际路由来源 |
| 多匹配要求不同 | AMBIGUOUS_REQUIREMENT 配置故障并阻断，不隐式 OR／AND，也不把 matchOrder 偷改为安全优先级 |
| 引用损坏／未知新协议 | 配置故障阻断，建议技术错误 503；不伪装普通用户无权限 |
| 要求明确但无准入资格 | 正常准入拒绝，建议沿网关 403 协议 |

例如 `/reports/** → VIEW` 与 `/reports/export → EXPORT` 同时匹配，不能因为用户只有 VIEW 就从快照中漏掉 EXPORT 规则，从而只看到较弱要求。已确定的公共／认证白名单维持独立；没有 requiredPermission 绝不自动等于公共路由。复合多权限最终 AND／OR 留在业务实现，本轮不建设通用接口策略语言。

这与 LEGACY_API 的旧共同候选语义不同，是方案 A 的新规则，必须定案、盘点和迁移，不能伪装为 R2 等价重构。

### 8.3 手工、服务同步、FULL 清理一并改造

建议新增版本化同步 DTO，ApiItem 真正落 requiredPermission 并由读侧消费；旧协议不能自动升级；现行同步 DTO 已无操作引用字段（operationCode 已于 T-PERM-053，2026-09-05 删除——「仅校验无运行时效果」为删除前历史形态），新版本化 DTO 才引入 requiredPermission 并由读侧真正消费。[C08]

手工／同步／bootstrap 共用校验保存职责。保留服务认证、所属服务与原 FULL 事务边界；任一必要操作引用解析失败整批回滚，不静默跳过。FULL 只收敛**该服务 SERVICE_SYNC 自有映射**，不能覆盖 MANUAL／BOOTSTRAP；显式接管单独授权和审计，同一路由跨 owner 争写拒绝。

当前清理按 API 资源的来源和 owner 推断映射归属 [C09]。增加映射独立来源后，API 实体清理也要检查保留映射引用：即使映射本身没被 FULL 删除，若其登记实体被同时回收仍会失效。选择“仍有其他合法映射则保留登记实体”或“拒绝本次冲突清理”，与资源同步的先后顺序同事务验收；不能只更新映射 DELETE 条件就宣称维护隔离完成。

### 8.4 新准入快照：保留本地性能路径，协议明确隔离

不默认改成每次“网关远程准入＋业务远程实例检查”。建议增加新端点／DTO，如 interface-admission、interface-admission-snapshot（名称为建议），PermissionClient 显式按可信服务模式调用。旧 check-interface 只能服务 LEGACY_API，新模式失败不回落旧 API:ACCESS。

```text
InterfaceAdmissionSnapshot
  schemaVersion, tenantId, subject, serviceCode
  generatedAt, expiresAt, configGeneration
  routes[]              // 完整的启用路由与 required type-operation
  operationCandidates[] // 对上述要求的独立条件分支与候选类别
  authorizationStage = OPERATION_ADMISSION
  finalCheckRequired = true
```

configGeneration 表示本次路由／模式配置代次。**（2026-09-25 拍板：保留字段，语义钉死为构建期自一致校验——构建前后代次比对、变更即废弃重建，及接收侧代次匹配检查；不承诺跨节点授权版本强一致，不实现全节点代次确认协议——那是将来不停流切换立项时的扩展。）**构建期间若配置代次改变，结果废弃重建；接收／发布匹配代次有明确校验，不能把两次不一致读取拼成一份可信快照。

候选从新 OPERATION_ADMISSION＋FACTS 构建，绝不经过旧 GRANT_LIST 的全集合 PERM_MUTEX。原始 GrantFact 不去重合并；最终准入投影可按 type-operation＋conditionId／无条件身份＋候选类别归并。仅上下文子行提供的候选保留 CONTEXT_DEFERRED，不伪装成主授权。

本地判定顺序：**校验模式／版本／时效→完整路由匹配与歧义检测→取得唯一要求→评该要求的条件分支**。存在无条件或条件通过分支就 MAY_ENTER；无通过分支但有需远端求值的候选则回源；其余拒绝。坏条件显式不可用，不能因空 rules 成为无条件。完整 branches 保留不同 conditionId，另一分支失败不能覆盖已成立来源。

本地与在线使用同一类型／操作／条件／候选定义及同事实同环境测试。准入快照不携带用于绕过业务的“实例已授权”证明；客户端传入 finalCheckRequired=false 不能改变服务配置。

### 8.5 失效、时效与模式切换

| 变化 | 必须维护 |
|---|---|
| 授撤、角色归属／有效期、AUTO_DEP 改变 | 原角色／用户失效继续覆盖新准入快照；删除一个来源不清掉其他有效来源 |
| 条件同 ID 改规则、启停、删除 | 条件缓存和相关服务准入投影；不可仅按旧 resourceEntityId 相等联接服务 |
| 操作覆盖／定义变更 | 新鲜定义、准入快照及对应服务失效；引用 ID 没变不代表权限语义没变 |
| 父授权撤销或结构变化 | 子候选的结构有效性及新快照随授权生命周期收敛，不让只缓存子行永不检查父 |
| 映射新增、改绑、停用、删除、FULL 缺失 | 事务内事实改变，提交后更新服务路由与准入快照；不重算角色 AUTO_DEP |
| 服务模式／配置代次／协议切换 | 旧新命名空间、在途加载、负缓存和节点能力一起处理 |

条件关联服务的反查默认采用安全超集：“引用条件的授权类型→该类型所需操作→映射服务”，再按实际覆盖优化；不能直接沿旧授权资源＝API 资源关联 [C13]。收到失效后，旧在途读取不能覆盖新一代缓存；保留加载去重、回源截止、剩余 TTL 和失败关闭。

**首次迁移默认采用可验证的服务级切换：**暂停目标服务业务入口→确认每条路由已具备业务最终检查→切模式／配置→全接流量节点确认新版本并清理旧缓存／加载→恢复。需要不停流时另补带全节点确认的代次切换协议；一次 pub/sub 广播不是完成证据。新快照缺失、远端不可用、未知 schema 不得自动 OR 旧权限或 stale-allow；按现有失败关闭路径返回技术错误。[C06][C07]

新快照短 TTL、上游安全目录寿命与截止时间按第 5 节重新推导并纳入启动校验；不能直接宣称旧 30 秒目标自动覆盖新增的操作／路由依赖。临时强制在线可用于灰度验证，但要有容量预算与退出条件，不能称作性能不变的终态。

### 8.6 业务服务：以实际目标做最后一道权限检查

普通实例接口不变，例如：

```json
{
  "subjectTypeCode": "LOCAL_USER",
  "subjectExternalId": "<已认证用户>",
  "resourceTypeCode": "REPORT",
  "resourceCode": "REPORT_B",
  "operationCode": "VIEW"
}
```

主体／租户取自可信认证链，实际操作对象从业务请求及业务解析得到；不能检查 A，却按另一参数读取 B。继承模式与产品约定显式对齐：菜单后代可见不能推导默认 SELF 的 check 已打开祖先。

| 业务入口 | 完整检查 |
|---|---|
| 查看／预览／下载／导出／签发链接 | 实际资源＋对应操作，允许后才返回数据或可访问地址 |
| 独立批量 | 每个目标一个 DECISION 项；全拒还是返回允许子集由业务明确决定，不能任一允许放行整批 |
| 列表／搜索 | 权限范围或完整批量结果落实到返回数据；过滤／分页／total 同口径 |
| CREATE | 最终 TYPE_LEVEL；实例准入不能授予类型创建权 |
| 上下文子权限 | 提供真实父资源、父操作和环境，业务引擎验证父授权记录绑定；无父／错父拒绝 |
| 异步作业 | 明确提交与实际执行／取数的鉴权时点；不永久复用准入结果绕过撤权 |
| 直连／内部调用 | 同样验证可信身份并执行最终检查；不能依赖只能从网关进入的假设替代业务门禁 |

迁移资格来自“最终检查的代码位置＋反向拒绝测试”，不是一个 businessChecked=true 配置。权限服务自身的认证／内部调用链保持独立，不能因新准入而递归调用自己的准入端点。没有逐路由证明的服务不切新模式。

<a id="r2-migration"></a>

## 9. 实施、旧新字段迁移与回退

### 9.1 字段迁移不能只包一层门面

| 旧模型 | 新位置／处理 |
|---|---|
| userId 与 roleIds 可同时填 | Subject.User／Roles 封闭变体；空 Roles 不回退 |
| targetMode 与 nullable resourceCodes（复数集合） | 明确 Selection 与 CODE／ENTITY_ID 引用 |
| resourceTypeCodes、operationCodes 独立集合 | TypeOperation／TargetClause；真实需要全组合时由适配层显式构造 |
| exactInstanceOnly | TypeFallback.DISALLOW，与 Inheritance 独立 |
| inheritClosure | 判定 Inheritance；不和展示父／子展开混合 |
| inheritParents／inheritChildren | OutputSpec 的展示展开，不参与最终判定 |
| parentResource*（编码类型字段实际为 parentCodeType） | ParentRequirement；按选择固定父语义 |
| evaluateConditions／markConditionsOnly | EVALUATE／PRESERVE；是否下发条件仍由快照投影负责 |
| evaluateConflicts | 内部 MutexMode＋合法组合校验；普通 DECISION 不可 SKIP |
| evaluateMatchesBit | 删除误导开关；覆盖匹配始终执行，覆盖操作列表仅作输出 |
| includeResources／Operations／Roles | 明确描述块；不机械保留没有消费者的 includeDomains／Conditions |
| bypassPermSnapshot | ListGrantRead.DATABASE，仅约束这份 LIST 授权来源 |
| LIST allowed、rawEntries／instanceEntries | GrantSetResult 状态与分阶段 raw／retained |
| 原实体和展示克隆混装 | GrantFact 与 PresentationEntry 分离 |
| 请求对象补写时刻／解析状态 | immutable Request＋一次 RunState |
| 新准入返回旧 AuthCheckResp.allowed | 禁止；单独 AdmissionResult／版本化外部响应 |

PermResultUtils 改为新结果到既有外部响应的纯转换，或删除；不先重建旧 PermResult 再转换。最终旧 `PermQuery / PermBatchQuery / PermResult / PermBatchResult` 与独立编排消失，RolePermEntry 仅可按第 5 节作为缓存边界例外保留。

### 9.2 保留原 R2 任务粒度，补准入工作包

以下 R2-Txx 沿原设计临时编号，ADM-Txx 为本报告临时编号，**都不表示已创建仓库任务卡**。

| 编号 | 实施内容与主要位置 | 依赖／退出条件 |
|---|---|---|
| R2-T01 | 全仓调用、语义、输出、事务、缓存与协议清点 | 每个实际调用都有迁移目标；区分旧执行体与 legacy 业务模式 |
| R2-T02 | PQ-01／06 反例及正常语义基线 | T01；真实规则／数据库可复现，不全用 passthrough mock |
| R2-T03 | 新请求／结果、工厂与合法组合 | T01；三结果、四选择、不可变与混批限制可测 |
| R2-T04 | 纯权限互斥、角色确定化、证据返回 | T02；真实规则、空规则短路、无重复通知 |
| R2-T05 | QueryReadSupport、解析记忆、读来源分桶 | T03；空集守卫、缺失记忆、多类型读、缓存载荷边界 |
| R2-T06 | TYPE_GRANT／INSTANCE 单一阶段主体 | T04／05；短路、clause 精确候选、独立 item、原因与继承正确 |
| R2-T07 | 父受控子项和 GRANT_LIST 完整事实 | T06；父惰性／整集合门禁、raw 定义、FACTS 不漏阶段 |
| R2-T08 | 描述、操作覆盖、展示与范围投影 | T07；不影响判定、不污染真值；缺失目标定义补齐 |
| R2-T09 | 根审计、TRACE、故障证据 | T04／07；无重复、短路不补跑、敏感字段控制 |
| R2-T10 | 迁移 check／batch／管理／getDenied | T06／09；最终鉴权均进入新 execute |
| R2-T11 | 迁移范围及 legacy 接口集合 | T07／08／10；原四态、注册和共同集合语义保持 |
| R2-T12 | 迁移旧快照、转授、视图和配置 | T08／09／11；读取、条件分支、同行资格、菜单更新全部覆盖 |
| R2-T13 | 删除旧执行体、四旧 DTO 及旧结果工具依赖 | T10～12；引用与架构门禁通过，载荷例外明确 |
| R2-T14 | 候选／规则索引和性能测量 | 正确新核心后；索引比扫描，N=1 不以大批收益掩盖 |
| R2-T15 | 灰度、故障、缓存与发布演练 | T13／14；可回退至正确核心，不恢复已知错误 |
| ADM-T01 | 回写方案 A 准入规则、路由规则、版本与任务验收 | R2-T01／03；明确本报告建议的定案状态 |
| ADM-T02 | OPERATION_ADMISSION 阶段与新结果适配 | T04～07/09、ADM-T01；条件共享、父结构、新鲜操作目录、最终检查标记（T09=审计证据结构复用前置） |
| ADM-T03 | 映射列、服务模式、手工／同步／bootstrap／前端、FULL | ADM-T01；操作真正消费、owner 隔离、登记实体清理无悬挂 |
| ADM-T04 | 新端点、快照、PermissionClient／Filter／Matcher | ADM-T02／03；完整路由匹配、本地／在线一致、版本隔离 |
| ADM-T05 | 新链路失效、TTL 边界校验与在途代次控制 | ADM-T03／04；条件／操作／父／路由均可收敛，旧加载不能覆盖新态 |
| ADM-T06 | 逐服务业务最终检查、负向验收与模式切换 | ADM-T04／05；A/B、批量、导出、父上下文及身份链实测 |
| ADM-T07 | 全服务迁完后退出 API 独立授权和 legacy 协议 | ADM-T06、R2-T13；保留 API 登记目录，受控清理系统来源授权和旧缓存；退役 API 授权生产入口（bootstrap 种子与授权写入口拒 API 类型、前端授权页分支）——空库启动零 API 授权行、写入口负向验收 |

**两个完成条件：**R2-T13 可以在仍有 LEGACY_API 服务时完成，因为 legacy 语义也应通过新 R2 表达，不需要旧引擎。ADM-T07 才表示 API 独立授权模式退役。不能用“R2 入口统一了”证明 T-PERM-054 完成，也不能为未迁完服务永久保留旧查询执行体。

### 9.3 存量与发布顺序

先准备保留正常行为的最小正确性修复基线，再实施新内部契约和阶段；先离线固定事实差分，再逐调用方迁移。T-PERM-054 在映射、网关和业务端均具备能力后按服务启用，不把 R2、整个授权表改造与 IMP-15 混成一次不可回退上线。

> 基线已落（2026-09-26）：T-PERM-083（PQ-06 S/H-D）+ T-PERM-095（PQ-01 逐目标互斥）两提交构成最小正确性修复基线，本地 tag `r2-baseline-correctness`（验证证据见 T-PERM-095 任务卡完成记录）。

运行库盘点至少包括：非 API 手工映射、同路由或重叠路径多要求、缺业务操作的登记、各服务独立 API 授权、同步归属、无最终业务门禁的路由。非 API 存量映射要显式找到登记 API 并补准入操作，不能凭旧菜单类型猜 VIEW；不确认的数据不启新模式。涉及 AUTHORITY_ROOT 等受保护行的清理使用受控迁移，不在普通授权页硬删。

新核心不调用旧核心。迁移期在明确的调用方边界选择新／旧一次，不让真实请求完整跑两次有副作用鉴权再比较。离线差分固定 seed、时钟、规则和读入事实；影子验证只比较无副作用部分，审计一次提交。

### 9.4 删除与回退检查

全仓检查四旧 DTO、queryBatch、computeInstanceDenied、passesScopeAll、getDenied*、旧工厂、方法引用、反射／序列化、测试和文档。getDenied 名称可留作薄门面，但架构测试应禁止其注入权限 Mapper、解析角色或调用条件／互斥服务。

索引问题回退到**同一新核心**的扫描；投影问题只回退投影；代码级故障退到最小正确性修复基线（tag `r2-baseline-correctness`，T-PERM-083+095 两提交），而不是恢复 PQ-01／06 的旧 SHA。新准入技术故障可走同语义在线判定；退回 LEGACY_API 必须重新确认旧权限、路由与业务安全，不自动切、不新旧 OR。改缓存格式／模式时同时处理旧写者、旧加载和双命名空间；禁用安全检查不是回退方案。

## 10. 验收、性能与上线门槛

### 10.1 测试层级和比较维度

契约单测证明模型合法性；纯算法测试证明候选／位／规则；协作测试证明阶段与读取次数；PostgreSQL 集成测试证明真实 SQL、条件和互斥；HTTP／SDK／网关测试证明接入、协议和身份；性能／故障测试证明容量与失败关闭。**任何一层都不能用文档矩阵或独立参考类编译代替。**

不仅比较 boolean，还比较 reason、原序／重复项、matched ID、raw／retained 的权限身份、父绑定集、阶段覆盖、范围四态、条件分支、审计真实规则、读取来源及 Mapper／SQL 次数。已知旧错误用修正预期比较，不能要求新版本复现旧错误。

### 10.2 原 v2.0 必要回归矩阵（保留编号）

下表继承原完整稿的验收要求，不是本轮已执行记录。S01～S04 只验证 LEGACY_API 迁移兼容；新准入使用下一节 N 系列，不能拿旧快照通过代替新模式验收。

| ID | 场景 | 关键预期 |
|---|---|---|
| C01 | 空请求 | 空结果；引擎实现验证主体、Mapper、审计零调用 |
| C02 | DECISION＋PRESERVE/SKIP | 执行前结构错误，不能返回ALLOW |
| C03 | GRANT_LIST＋DECISION／混批 | 结构错误 |
| C04 | Roles(empty) | 不回退User；NO_ROLE |
| C05 | 重复key／空type／空op／无实例clause | 校验错误，不扩大查询 |
| C06 | 两个不同key指向同目标 | 两个结果，原输入顺序不变 |
| C07 | 输入集合／嵌套context执行前后 | 防御性复制，外部修改不改变执行输入 |
| C08 | attributes伪造evaluatedAt/clientIp | 不能覆盖正式环境 |
| D01 | X:VIEW、Y:UPDATE且覆盖VIEW，互斥 | 独立项双允许；getDenied投影为空 |
| D02 | 同样数据放入一个目标集合项 | 两端同场，按共同集合拒绝 |
| D03 | 同目标挂互斥两端 | 必须拒绝，不能第一条授权提前返回 |
| D04 | 条件剔除互斥一端 | 条件后另一端可留，不能产生虚假互斥 |
| D05 | 类型级门禁只有实例授权／子scopeAll | 不放行 |
| D06 | scopeAll评估通过＋无投影编码 | 按既有规则放行；最小输出不解析实例 |
| D07 | DISALLOW类型回退 | scopeAll不能替代实例 |
| D08 | scopeAll评估失败而实例有授权 | 仍可在实例阶段允许 |
| D09 | scopeAll被评估清空＋未知目标 | 保留CONDITION_NOT_MET_OR_CONFLICT优先级 |
| D10 | SELF/SELF_AND_ANCESTORS混批 | SELF不消费他项闭包 |
| D11 | 父与目标授权同场真实冲突 | 按该项闭包候选拒绝 |
| D12 | 跨类型同位值 | 不泄漏操作覆盖 |
| D13 | X:VIEW、Y:EDIT的物理合批超集 | 每个clause的操作和目标关系不串配 |
| D14 | 未知type/op/code，其他独立项正常 | 未知项拒绝，正常项正常；无全批技术失败 |
| P01 | 无父要求但有dependent候选 | TARGET_SET排除子行，原因正确 |
| P02 | 有父但只有主授权 | 不触发父查询 |
| P03 | 父失败且主授权存在 | TARGET_SET主行仍可生效 |
| P04 | GRANT_LIST父失败且源非空 | 整集合PARENT_DENIED |
| P05 | 父scopeAll已命中 | 不扩读父实例，绑定集来自真实命中ID |
| P06 | 相同父被多个项使用 | 一次父计算；证据正确映射全部受影响项 |
| P07 | 父操作空集 | 不解释为不限操作；无循环或额外授权 |
| P08 | 根PRESERVE/SKIP、父为required | 父仍FULL评估，不能被根弱选项放行 |
| G01 | 无父GRANT_LIST中dependent条目 | 按存储事实参与原清单流程；装配后隐藏契约保持 |
| G02 | raw有覆盖、retained空 | 范围EMPTY，不丢操作定义 |
| G03 | raw无覆盖／目标op未知 | 范围DENIED |
| G04 | retained含scopeAll | ALL优先；不展开为全量业务实例 |
| G05 | retained实例全部资源失效 | EMPTY，不产生空INSTANCE |
| G06 | 页面只筛A，但A-B互斥 | 后置过滤不能让A复活 |
| G07 | FACTS目标集合允许类型回退 | 两阶段事实都收集；互斥仍不跨阶段 |
| S01 | LEGACY_API：快照API:VIEW不覆盖ACCESS | 不下发为接口放行依据 |
| S02 | LEGACY_API：API scopeAll＋未注册路径 | 只展开enabled注册映射，不输出任意通配 |
| S03 | LEGACY_API：同API无条件＋多个条件授权 | 各分支都保留，不能单条化 |
| S04 | LEGACY_API：条件不可内联／无效 | 维持有条件与回源/fail-closed，不变无条件 |
| T01 | 转授授权撤销后清单DB读取 | 不读ROLE_PERM_SNAPSHOT |
| T02 | 可覆盖行不可转授，另行canGrant不覆盖 | 不拼接两行资格 |
| T03 | 操作者无目标类型授权，目标操作实际存在 | NO_PERMISSION而非INVALID_OPERATION |
| T04 | 运行时祖先可用但转授未授权该继承 | 转授不自动扩大 |
| R01 | 角色A/B/C/D，规则A-B、B-C | 仅D，规则顺序任意结果相同 |
| R02 | 只持有A/C/D，无B | 全保留，不做传递冲突 |
| R03 | Roles主体角色视角 | 不暗中解析用户或加角色 |
| I01 | 空互斥规则 | 互斥专用操作装载零调用 |
| I02 | 20个辅助类型 | 默认一次多类型Mapper，无逐类型循环 |
| I03 | 最小输出成功路径 | 无装配专用操作解析／描述读取 |
| I04 | 已读空类型／缺失操作 | 请求内不反复回源 |
| I05 | 冷缓存／热缓存／混合miss | 同来源复用，TTL令牌不重置 |
| I06 | 缓存掩码与新鲜定义同时存在 | 两来源不混用，输出不覆盖判定所需目录 |
| I07 | 先写后新execute | 新RunState，不复用上次事实 |
| I08 | SQL分块跨同一item | 候选到齐后互斥，不因块边界改变结果 |
| A01 | A-B触发，A-C未触发 | warning和新证据不列A-C |
| A02 | 重复item及scope/instance同规则 | 各维计数按定义去重，不按entity误去重 |
| A03 | 父、角色、主阶段证据 | 一次受控提交，不重复通知 |
| A04 | 后续装载故障、先前阶段已确认冲突 | 保留技术失败，证据标不完整执行 |
| A05 | TRACE＋scopeAll短路 | 实例为SKIPPED，不额外查询填充解释 |
| X01 | DB／规则读取异常 | 不是普通DENY或空成功清单 |
| X02 | 预算／deadline超限 | 不返回半份事实或未经完整评估的ALLOW |
| X03 | 新旧固定事实差分 | 已知错误用新正确预期，其余保持语义 |
| X04 | 旧DTO/执行体删除 | 生产引用为零，缓存载荷例外明确 |

### 10.3 方案 A 与集成补充矩阵

| ID | 场景 | 关键预期 |
|---|---|---|
| N01 | 只有 A/VIEW，请求 A 和 B | 操作准入均可进入；业务 A 允许、B 拒绝；无其他授权时 B 精确菜单不可见 |
| N02 | 无覆盖候选，或主体无有效角色 | 准入拒绝，不执行业务；只持旧 API:ACCESS 不得兜底 |
| N03 | 实例授权与 TYPE_LEVEL 对照 | 准入可成立；真正类型级门禁／CREATE 不因此通过 |
| N04 | A/VIEW、B/UPDATE 覆盖 VIEW，存在互斥规则 | 准入不跨实例误拒；业务各项独立；共同集合仍按原规则 |
| N05 | 同实例真互斥 | 准入可 MAY_ENTER，业务必须拒绝；准入审计不写互斥通过 |
| N06 | 仅有结构合法 depend_on 子行 | 准入保留候选；正确父可在业务通过；无父、错父、父失败拒绝 |
| N07 | 父缺失、跨租户／跨角色、非法嵌套 | 准入结构检查失败；不能因缓存有子行就保留可用分支 |
| N08 | MANUAL／AUTO_DEP／类型授权根覆盖同操作 | 按真实覆盖参与，不按来源添加特权；撤一来源保留其他来源 |
| N09 | 无条件＋多个条件来源，其中一条失败 | 保留独立 OR 分支；最后来源撤销才失去候选 |
| N10 | 条件缺失／停用／非法 | 不转换无条件；规则坏与规则读取故障分别记录 |
| N11 | 条件不可下发，且没有其他通过分支 | 本地回源；在线按相同规则求值；故障失败关闭 |
| N12 | 新准入 FACTS 与在线 ADMISSION | 同事实／定义／时刻／IP 下本地投影和在线准入一致；普通 DECISION 不能 SKIP |
| N13 | 同次要求重用、重复输入项 | 可共享读／算，输入关联保留；准入与普通项混批按首版限制拒绝 |
| N14 | 共同通配与精确路由要求不同 | 完整路由集检测歧义并阻断；不能只保留用户有权规则 |
| N15 | 多匹配要求相同／无注册路由 | 相同要求去重；无注册仍拒绝；ALL 不放行未注册接口 |
| N16 | required operation 丢失或被间接删除 | 写侧守卫／迁移处置覆盖全部路径；坏引用为配置故障，不猜 VIEW |
| N17 | FULL 缺失与 MANUAL／BOOTSTRAP 并存 | 只清自有映射；保留映射的登记实体不被旁路清理悬空 |
| N18 | 同服务同步与手工争写、接管 | 拒绝冲突或走显式授权接管；失败整批回滚，无静默 owner 覆盖 |
| N19 | 条件同 ID 更新、操作覆盖改变 | 相关服务准入快照失效；不依赖旧 resourceId 等值服务反查 |
| N20 | 长 TTL 普通操作缓存仍残留旧值 | 新准入构建使用新鲜定义／已批准安全目录，不读取残留旧掩码投影 |
| N21 | 快照构建中路由配置代次改变 | 废弃旧构建；旧在途结果不能覆盖新代次 |
| N22 | 新旧 schema／模式混用、版本缺失 | 新服务不回落旧 API:ACCESS；未确认能力节点不接流量 |
| N23 | 服务模式切换／在线验证回切本地 | 全节点、缓存、加载与负缓存有演练证据；不依赖广播发送即成功 |
| N24 | 批量 B 混入 A、下载／导出／异步任务 | 检查实际目标与执行时点；不能任一通过或提交时准入长期放行 |
| N25 | 直连、伪造主体／租户／IP／模式／时钟 | 可信身份链与业务门禁阻断；内部参数不可由客户端绕过 |
| N26 | 父子时钟与评估时刻 | 同次父子一时刻（沿现行 a2 定案）；重构不意外改变本地时钟评估语义（时区拍板：不做时区处理） |
| N27 | 大路由表、大授权量、分块、快照上限 | 不逐实例 check，不展开全租户；不截断 FACTS／routes；超限显式技术失败 |
| N28 | 权限服务自身／公共路由／内部服务认证 | 保持独立认证边界，不形成准入递归；缺映射不自动公共 |
| N29 | R2 完成但仍有 LEGACY_API 服务 | legacy 也进入新 execute；旧完整核心／DTO 已退出，无永久双执行 |
| N30 | ADM-T07 清理系统来源 API 授权 | 使用受控迁移；不误删业务授权或 API 注册目录，回滚数据可追踪；空库启动零 API 授权行、写入口拒 API 类型（负向锁） |

### 10.4 性能基准和结构门槛

R2 至少比较：旧版用于定位开销、**已修正确性且扫描的新基线**、新核心＋索引优化。主要比较后两者。新准入单独比较在线与快照；端到端计入业务最终检查，不能省掉最终检查换取数据好看。

负载覆盖 N=1／10／100／现有外部上限与内部实际规模，单／多类型、稀疏／密集、共享祖先、冷／热缓存、条件数、互斥规则数、scopeAll 短路／实例／完全拒绝、最小输出／完整事实；新增路由规模、条件分支量、回源比例和失效风暴。

记录 Mapper 调用、实际 SQL、行数、候选访问、条件预载、规则／父执行、缓存 get／put／载荷、分配与 GC、P50／P95／P99、审计失败、快照构建与回源错误。具体预算基于实测审批，不预填提速比例，也不以 N=1000 的提升掩盖 N=1 回退。

可硬性验收的是：多类型正常规模不逐类型查询；无规则不装载互斥专用操作；最小输出不做展示专用读取；准入不做逐资源最终鉴权；独立 item 不混集合；FACTS 与 routes 不静默截断；旧完整执行体不存在。新安全目录／普通目录分工与每个失效触发都有反向测试。

### 10.5 上线门槛与观测

上线前要有：定案记录、逐消费者／逐路由覆盖清单、项目级回归证据、SDK／网关契约、缓存与模式切换演练、实际性能预算、不会恢复已知错误的回退目标。仅设计完成或任务卡写 done 不满足这些门槛。

灰度差异分别归类：PQ-01／06 预期修复（06 含 S/H/D 删多收紧：链式多持用户现行保留一端、新算法全删）、FACTS 完整性／新空角色契约、同源首次读取复用、方案 A 新准入语义、意外回归。监控选择类型与阶段延迟、scopeAll／父触发率、定义缺失、预算超限、准入拒绝／配置故障／回源、业务最终拒绝、模式不一致和审计失败。准入成功而业务 B 被拒是预期分层行为，不应自动当系统错误。

## 11. 保留的关键取舍与待定案项

采用方案 A 的方向不重开。下面列的是实施规则的默认建议和真正有价值的备选，不把每个内部方法都变成可插拔接口。

| 决策 | 本版默认 | 备选及代价 |
|---|---|---|
| 内部架构 | R2-A，一个 execute＋有限选择／结果 | R2-B 分判定／事实协调可减少单模型组合，但增加生命周期协调；本版不按此实施 |
| 内部兼容 | 最终直接迁新模型，删除四旧 DTO | 短期桥接要有退出任务；不先做完整 U2 再整体重做 |
| 外部协议 | 普通 check／batch／范围不改；准入和同步独立版本化 | 同时重做全部外部接口扩大接入风险，无必要不绑定 |
| 原集合／回退／FACTS | 独立项与共同集合分开；保留 scopeAll 短路；FACTS 收全所选阶段 | 全改逐目标或先验实例会改变行为；必须另算影响，不默认 |
| 第一版模型限制 | 单主体环境，至多一个父；LIST 独占；准入只与准入同批 | 放开要增加环境、来源、父结果和审计分桶，目前无必要 |
| 读取与事务 | 固定来源矩阵＋同源首次读取复用，非强一致保证 | 完整版本校验、DB-only 快照、写侧锁另评估 |
| RolePermEntry 缓存载荷 | 暂留为边界类型，不保留旧引擎结果 | 新载荷目录更干净，但需滚动发布双失效，不与 R2 强绑 |
| 候选算法 | 正确扫描基线后选索引 | 一律索引可能拖慢小批，按基准决定 |
| 准入子行 | 结构有效即候选，运行时父判定延后 | 全排除更简单但误挡合法上下文接口；须明确不迁支持范围 |
| 新准入操作定义 | 新鲜数据库完整类型目录 | 专用短 TTL／版本化安全缓存可降开销，但必须重做安全边界证明 |
| 映射数据 | 保留登记 API，新增 required_operation_id 与独立来源 | 删除 API 实体／改成任意业务实例冲击同步、目录与 FULL，非方案 A 必需 |
| 多路由要求 | 同要求去重，不同要求歧义阻断 | AND／OR／优先级是新增产品策略，需完整冲突语义；首版不引入 |
| 网关与切换 | 新准入本地快照＋同义在线回源；服务级可暂停切换；configGeneration 仅构建期自一致校验（拍板限定，§8.4） | 暂时全在线适合验证；不停流需全节点代次切换协议，另计成本 |
| 审计 | 真实证据、根级受控非阻塞提交 | 强持久／outbox 是独立写模型，不冒称现有必达 |

准入候选/子行规则、新快照安全读取、映射歧义与来源、服务迁移门槛，以及 R2 的空角色/FACTS 完整性/首次读取复用等新内部契约，形成决定后当轮归本设计及对应契约章节，并补来源与任务安排。按 [文档治理](project-rules.md#decision-governance)更新当前正文，历史快照不追加。其余既有行为按回归保护，不把“建议采用”写成用户已经逐条拍板。

**最终完成定义：一份合法请求进入唯一执行器；授权数据能共享，判定集合不串；事实与准入都不冒充最终许可；普通消费者迁完且旧核心退出；新模式业务逐路由有最终检查，API 不再独立授权；所有结论都有对应项目测试和运行证据。**

## 附录 A：请求构造示例

示例只说明契约搭配，不是已编译的项目代码。`full()` 表示 EVALUATE＋ENFORCE；`minimalWithMatchIds()` 表示无完整事实／描述、保留命中 ID；FACTS 工厂至少请求 KEPT。

### A.1 一个报表实例的普通最终鉴权

```java
var request = new QueryRequest(
    tenantId, new User(userId), trustedCallerContext,
    new ReadOptions(ListGrantRead.DATABASE),
    List.of(new QueryItem(
        "report-A",
        new TargetSet(
            List.of(new TargetClause(
                new TypeOperation("REPORT", "VIEW"),
                new ByCode("REPORT_A", null, null))),
            Inheritance.SELF, TypeFallback.ALLOW, null),
        full(), ResultForm.DECISION, minimalWithMatchIds())));
```

此处 ReadOptions 的 DATABASE 不代表整个链路无缓存；目标授权本来固定 DB。普通外部 check 沿 SELF 缺省，管理继承门禁显式选择 SELF_AND_ANCESTORS。

### A.2 独立批量、共同集合与实体 ID

```text
分别检查 A、B：
  items[0] = TargetSet({REPORT_A/VIEW}, SELF, ALLOW) + DECISION + FULL
  items[1] = TargetSet({REPORT_B/VIEW}, SELF, ALLOW) + DECISION + FULL

明确共同集合：
  items[0] = TargetSet({REPORT_A/VIEW, REPORT_B/VIEW}, SELF, ALLOW)
             + DECISION + FULL
```

前者共享 SQL 但隔离候选；后者共同 PERM_MUTEX，不能互换。批量管理 ID 门禁将真实 resource_entity.id 作为 ByEntityId，按原下标把拒绝项映射回输入 ID；不先查角色或 scopeAll，也不把 ID 转业务码。

### A.3 范围、转授、角色配置

| 场景 | 主体／Selection | Evaluation／读取 | 必需输出与后续 |
|---|---|---|---|
| 报表 R 上下文的数据范围 | User；GrantList(requiredParent=R/VIEW) | FULL；原 ROLE_SNAPSHOT 来源 | RAW_AND_KEPT，补 scope type-operation 定义；外层先验父对象，结果四态投影 |
| 操作者转授资格事实 | User；GrantList(null) | PRESERVE＋SKIP；DATABASE | KEPT＋目标操作定义；转授领域按同一授权行验证资格 |
| 指定角色精确配置 | Roles({roleId})；TargetSet(目标, SELF, DISALLOW) | PRESERVE＋SKIP；目标授权固定 DB | RAW_AND_KEPT；查询前做 ROLE:VIEW 等管理门禁 |
| 用户真实访问解释 | User；实际 TargetSet | FULL；目标授权固定 DB | DECISION＋受权 TRACE；不为补全解释改变短路路径 |

### A.4 新准入与旧接口兼容

```text
新模式在线准入：
  subject = User(userId)
  items[0] = OperationAdmission(REPORT:VIEW)
  evaluation = EVALUATE + SKIP
  resultForm = ADMISSION
  result.finalCheckRequired = true

新准入快照的候选请求：
  每个去重后的 required type-operation 一个 OperationAdmission item
  evaluation = PRESERVE + SKIP
  resultForm = FACTS
  output = KEPT + 所需条件元数据，完整收集，不混入普通目标项
```

路由、服务模式和完整注册集留在外层，不作为普通 QueryRequest 中任意可控策略。LEGACY_API 在线仍构建一个共同 API:ACCESS TargetSet；旧快照仍为 GrantList PRESERVE＋ENFORCE。两种 legacy 适配都可以使用新 execute，**不能因为保留旧授权模式而保留旧引擎**。

## 附录 B：与第一版 R2-v2.0 的内容对照

| 原完整稿章节／关键内容 | 本版位置 | 处理 |
|---|---|---|
| §1～3 目标、问题、执行单位与不变量 | §1～5、§9 | 合并重复宣言，保留 item／stage／load 区分及所有安全边界 |
| §4～6 请求、主体、选择、父要求、合法组合 | §2、§4.5 | 恢复结构与反例；增加 ADMISSION 的受控组合，不放松普通 DECISION |
| §7～8 结果、事实、覆盖、输出与后置过滤 | §3、§4.6、§6.2 | 保留 raw 定义、事实完整性、缺省／空块、范围四态与输出不得改判定 |
| §9～10 阶段算法、父复用、原因与技术故障 | §4、§3.4、§7.4 | 恢复逐阶段流程与候选公式；准入是新增封闭阶段，不调用旧核心 |
| §11 条件／权限互斥／角色互斥 | §5.1、§6.1 | 保留四态、真实规则归因、原集合角色算法、通知去重 |
| §12 读取、新鲜度、事务与缓存载荷 | §5.2～5.4 | 恢复完整矩阵；补新准入不能用旧长 TTL 操作缓存投影的安全约束 |
| §13 SQL、候选方案、分块与容量 | §5.5、§10.4 | 保留扫描／索引两案、复杂度、N=1、顺序与完整性；不预承诺提速 |
| §14 审计与 TRACE | §6.1、§3.3 | 保留实际阶段、父影响映射、失败标记、敏感披露及非必达边界 |
| §15～18 场景、示例、消费者与字段映射 | §6、§9.1、附录 A | 恢复可直接评估的调用构造和逐消费者迁移，不只写“统一入口” |
| 原接口／快照 API:ACCESS 条款 | §6.6、§8、附录 A.4 | 仅 legacy 兼容；新模式由方案 A 替换，不把旧快照规则套给准入 |
| §19、23 多方案与待决策 | §11 | 保留仍需权衡的局部选择；不重开用户已选择的方案 A |
| §20 实施任务、删除和回退 | §9 | 保留 R2-T01～15 编号，新增 ADM-T01～07；区分核心迁移与授权模式退役 |
| §21～22 测试、性能与上线 | §10 | 保留原 65 项场景编号，另加 30 项新准入／集成场景；均非已跑结果 |
| §24、原参考代码验证／附录 | 文首、附录 C | 保留证据限制；不把原参考程序的验证记录冒充本版执行器测试，不随文交付过期参考实现 |

本版相对上一份方案 A 报告补充／澄清的设计包括：新准入的固定读取来源和快照时效；普通条件评估时钟语义维持不变（时区拍板：不做时区处理，§2.2）；FULL 不得经登记实体删除绕过映射 owner 隔离；路由配置代次变化时的快照废弃；R2 与 legacy 退役的两个独立完成条件。这些是本次补齐的实施建议，需随主方案确认，不是既有仓库事实。

## 附录 C：源码与版本依据

引用分三组：**B** 为本轮直接复核；**E** 来自第一版完整 R2-v2.0 的 `9778ffc9` 固定源码依据；**C** 来自上一份方案 A 报告的 `9ba64cf2` 固定源码依据（C01 本轮亦重读）。旧基线引用用于说明原契约和发现，不表示本轮重新读取了所有文件。下面链接均固定提交；方法名与正文一起定位。

| 来源组 | 文件／支持内容 |
|---|---|
| [B00]、[B01]、[B02] | 本轮 HEAD；当前 computeInstanceDenied；当前缓存 TTL、载荷和安全目录边界 |
| [E01]、[E02]、[E03]、[E04]、[E04b] | 原引擎、请求／结果 DTO、阶段与集合语义 |
| [E05]、[E06]、[E10]、[E11] | 类型／目标解析、请求记忆、授权与多类型操作 SQL |
| [E07]、[E08]、[E09]、[E23] | 条件四态、权限／角色冲突、操作覆盖及写侧守卫 |
| [E13]、[E14]、[E15]、[E16] | check／batch／旧接口、范围、旧快照和转授资格 |
| [E17]、[E18]、[E19]、[E20]、[E21] | 视图、管理门禁、旧缓存目录、环境和批量 PgIT |
| [C01]、[C02]、[C03]、[C04]、[C05] | 任务卡、当前普通／接口检查、API 映射与旧快照 |
| [C06]、[C07]、[C08]、[C09] | 网关本地／回源、服务同步契约、映射同步和 FULL 清理 |
| [C10]、[C11]、[C12]、[C13]、[C14] | 准入可复用的 SQL、角色逻辑、条件类型、服务失效反查、批量旧路径 |
| [C15]、[C16] | 新基线菜单派生与 instanceIdsByType 类型页准入 |

[B00]: https://github.com/fage-org/Access-Mesh/commit/9ba64cf2c0a373554a174d59b83148b5f0b7f07f
[B01]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/core/PermQueryEngine.java#L1210-L1340
[B02]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/infrastructure/cache/AccessCacheCatalog.java
[E00]: https://github.com/fage-org/Access-Mesh/commit/9778ffc9bf397a156e649aa8f8646bb7a280c11f
[E01]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/core/PermQueryEngine.java
[E02]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/dto/PermQuery.java
[E03]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/dto/PermBatchQuery.java
[E04]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/dto/PermResult.java
[E04b]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/dto/PermBatchResult.java
[E05]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/core/TypeResolutionServiceImpl.java
[E06]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/core/ResolveContext.java
[E07]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/rule/service/domain/impl/PermissionConditionDomainServiceImpl.java
[E08]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/rule/service/domain/impl/PermissionConflictDomainServiceImpl.java
[E09]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/util/OperationPermissionUtils.java
[E10]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/resources/mapper/grant/RoleResourcePermissionMapper.xml
[E11]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/resources/mapper/type/OperationPermissionMapper.xml
[E13]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/service/impl/PermissionCheckAppServiceImpl.java
[E14]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/service/impl/PermissionQueryAppServiceImpl.java
[E15]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/util/SnapshotAssembler.java
[E16]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/grant/service/domain/impl/PermissionGrantDomainServiceImpl.java
[E17]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/service/impl/PermissionViewAppServiceImpl.java
[E18]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/AdminPermissionValidatorImpl.java
[E19]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/infrastructure/cache/AccessCacheCatalog.java
[E20]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/dto/PermEvalContext.java
[E21]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/test/java/cn/ac/fage/accessmesh/access/characterization/BatchAuthCheckPgIT.java
[E23]: https://github.com/fage-org/Access-Mesh/blob/9778ffc9bf397a156e649aa8f8646bb7a280c11f/access-service/src/main/java/cn/ac/fage/accessmesh/access/rule/service/impl/ConflictRuleAppServiceImpl.java
[C01]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/docs/tasks/T-PERM-054.md
[C02]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/service/impl/PermissionCheckAppServiceImpl.java#L67-L213
[C03]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/entity/ResourceApiMapping.java
[C04]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/impl/ResourceManageAppServiceImpl.java
[C05]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/util/SnapshotAssembler.java#L121-L237
[C06]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/gateway/src/main/java/cn/ac/fage/accessmesh/gateway/service/InterfaceSnapshotMatcher.java
[C07]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/gateway/src/main/java/cn/ac/fage/accessmesh/gateway/filter/PermissionFilter.java#L105-L335
[C08]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/dto/req/ServiceConfigSyncReq.java
[C09]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/domain/impl/MappingSyncHandlerImpl.java#L40-L198
[C10]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/resources/mapper/grant/RoleResourcePermissionMapper.xml#L245-L346
[C11]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/rule/service/domain/impl/PermissionConflictDomainServiceImpl.java#L103-L160
[C12]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/util/ConditionEvalUtils.java
[C13]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/resources/mapper/grant/RoleResourcePermissionMapper.xml
[C14]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/core/PermQueryEngine.java#L1240-L1340
[C15]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/menu/service/impl/UserMenuQueryAppServiceImpl.java#L162-L260
[C16]: https://github.com/fage-org/Access-Mesh/blob/9ba64cf2c0a373554a174d59b83148b5f0b7f07f/access-service/src/main/java/cn/ac/fage/accessmesh/access/engine/service/impl/PermissionViewAppServiceImpl.java#L107-L174
