---
doc_type: plan
title: R2 权限查询引擎统一与操作准入（方案 A）
status: active
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md
tasks:
  - T-PERM-080
  - T-PERM-081
  - T-PERM-082
  - T-PERM-083
  - T-PERM-084
  - T-PERM-085
  - T-PERM-086
  - T-PERM-087
  - T-PERM-088
  - T-PERM-089
  - T-PERM-090
  - T-PERM-091
  - T-PERM-092
  - T-PERM-093
  - T-PERM-094
  - T-PERM-095
  - T-ACCESS-056
  - T-ACCESS-057
  - T-ACCESS-058
  - T-ACCESS-059
  - T-ACCESS-060
  - T-ACCESS-061
  - T-ACCESS-062
  - T-PERM-054
acceptance: "两个完成条件各自闭合：①T-PERM-092（旧执行体与四旧 DTO 退出）可在仍有 LEGACY_API 服务时完成——legacy 语义经新 execute 表达；②T-ACCESS-062（全服务迁完、API 独立授权与 legacy 协议退役）。设计 §11 最终完成定义逐条有对应项目测试与运行证据"
last_updated: 2026-09-25
---

# R2 权限查询引擎统一与操作准入（方案 A）

> 设计依据：[r2-unified-query-and-admission.md](../design/r2-unified-query-and-admission.md)（v3.1，adopted，2026-09-25 定稿——三项拍板〔时区不处理 / 角色互斥 S/H/D / configGeneration 限定语义〕见 decision-registry 同日行）。
> 立项说明：统一设计稿的报告临时编号（R2-T01~15 / ADM-T01~07）按看板计数器转为正式任务 ID，映射见下表；T-PERM-054 解除暂缓归入本计划。

## 目标

- 唯一权限查询执行主体（一个 execute：规范化 → 共享装载 → 分集合评估），真实消费者全部迁移，旧执行体与四个旧引擎 DTO 退出（设计 §1.1）。
- T-PERM-054 方案 A：接口检查操作准入（OPERATION_ADMISSION），业务服务检查具体实例；API 不再独立授权（设计 §7/§8）。
- 顺带修复已代码级核实的缺陷：PQ-01（getDenied* 整批互斥跨 item 过拒）、PQ-02/04（多类型逐类型装载、辅助输出开关前解析——修复面 T-PERM-084）、PQ-03（逐 item 扫描整批实例行——修复面 T-PERM-085/093）、PQ-05（空规则仍装载操作、审计按冲突端点反推规则）、PQ-06（角色互斥顺序依赖 → S/H/D 定案）。

## 非目标

- 不重建 role_resource_permission、不改变 scopeAll/操作位/MANUAL/AUTO_DEP 真值、不改 depend_on 语义、查询时不写授权、不建通用策略编排平台（设计 §1.3）。
- 普通外部 HTTP/SDK 契约（auth/check、batch-check、query-scopes 等）默认不变；准入与同步协议独立版本化。
- IMP 写侧解耦与继承触发依赖不在本计划（设计稿前言：不因本版重开或自动实施）。

## 准入条件

- 设计已定稿（registry 2026-09-25 行）；T-PERM-081 语义基线先行（红跑取证）；最小正确性修复基线（T-PERM-083+095，设计 §9.3）先于新核心实现（T-PERM-085）落地。
- 核心任务收口跑全量回归（含 E2E/heavy，测试运行纪律见 AGENTS.md）。

## 任务清单

### R2 系列（引擎统一，T-PERM-080~094）

| ID | 标题（报告编号） | 状态 |
|---|---|---|
| [T-PERM-080](../tasks/T-PERM-080.md) | 全仓调用与语义清点（R2-T01） | ✅ |
| [T-PERM-081](../tasks/T-PERM-081.md) | PQ-01/06 反例与正常语义基线（R2-T02） | ⚙️ |
| [T-PERM-082](../tasks/T-PERM-082.md) | 新请求/结果模型与合法组合（R2-T03） | ⚙️ |
| [T-PERM-083](../tasks/T-PERM-083.md) | 角色互斥 S/H/D 确定化与纯互斥计算（R2-T04） | ⚙️ |
| [T-PERM-084](../tasks/T-PERM-084.md) | QueryReadSupport 与读来源分桶（R2-T05） | ⚙️ |
| [T-PERM-085](../tasks/T-PERM-085.md) | TYPE_GRANT/INSTANCE 单一阶段主体（R2-T06） | ⚙️ |
| [T-PERM-086](../tasks/T-PERM-086.md) | 父受控子项与 GRANT_LIST 完整事实（R2-T07） | ⚙️ |
| [T-PERM-087](../tasks/T-PERM-087.md) | 投影、展示与范围四态（R2-T08） | ⚙️ |
| [T-PERM-088](../tasks/T-PERM-088.md) | 根审计、TRACE 与故障证据（R2-T09） | ⚙️ |
| [T-PERM-089](../tasks/T-PERM-089.md) | 迁移 check/batch/管理门禁/getDenied（R2-T10） | ⚙️ |
| [T-PERM-090](../tasks/T-PERM-090.md) | 迁移范围与 LEGACY_API 接口集合（R2-T11） | ⚙️ |
| [T-PERM-091](../tasks/T-PERM-091.md) | 迁移旧快照、转授、视图与配置（R2-T12） | ⚙️ |
| [T-PERM-092](../tasks/T-PERM-092.md) | 删除旧执行体与四旧 DTO（R2-T13） | ⚙️ |
| [T-PERM-093](../tasks/T-PERM-093.md) | 候选/规则索引与性能测量（R2-T14） | ⚙️ |
| [T-PERM-094](../tasks/T-PERM-094.md) | 灰度、故障、缓存与发布演练（R2-T15） | ⚙️ |
| [T-PERM-095](../tasks/T-PERM-095.md) | （基线补卡，无报告编号）getDenied* 跨 item 互斥最小修复——与 T-PERM-083 构成回退基线 | ⚙️ |

### ADM 系列（操作准入方案 A，T-ACCESS-056~062）

| ID | 标题（报告编号） | 状态 |
|---|---|---|
| [T-ACCESS-056](../tasks/T-ACCESS-056.md) | 准入定案回写与协议落账（ADM-T01） | ⚙️ |
| [T-ACCESS-057](../tasks/T-ACCESS-057.md) | OPERATION_ADMISSION 阶段与新结果（ADM-T02） | ⚙️ |
| [T-ACCESS-058](../tasks/T-ACCESS-058.md) | 映射模型、服务模式与同步/管理面（ADM-T03） | ⚙️ |
| [T-ACCESS-059](../tasks/T-ACCESS-059.md) | 新端点、快照与 SDK/网关链路（ADM-T04） | ⚙️ |
| [T-ACCESS-060](../tasks/T-ACCESS-060.md) | 失效、TTL 边界与在途代次（ADM-T05） | ⚙️ |
| [T-ACCESS-061](../tasks/T-ACCESS-061.md) | 逐服务业务最终检查与模式切换（ADM-T06） | ⚙️ |
| [T-ACCESS-062](../tasks/T-ACCESS-062.md) | API 独立授权与 legacy 协议退役（ADM-T07） | ⚙️ |

### 归入卡

| ID | 标题 | 状态 |
|---|---|---|
| [T-PERM-054](../tasks/T-PERM-054.md) | 手工 API 映射绑定非 API 资源处置——方案 A 落地收口（2026-09-25 解除暂缓归入） | ⚙️ |

## 归档条件

全部任务 done/cancelled；稳定结论按现行文档规范回写对应设计文档族（2026-09-25 用户拍板）：R2 部分并入 `engine/implementation.md`（overview/core-flows 涉及面随卡回写），准入部分沉淀契约总册新章与 `services/gateway.md`；回写完成后 `r2-unified-query-and-admission.md` 转 superseded 随本计划归档（沿 permission-query-unification 先例），不长期占权威来源表行。

## 当前进度

- 2026-09-25 立项并转 active（准入条件①设计定稿已达成、②为执行纪律）。全部任务 proposed 未开工。依赖概览（唯一权威=各卡 frontmatter depends_on）：T-PERM-080 → 081/082 → 083/084/095（083+095=最小正确性修复基线，§9.3）→ 085 → 086（088 依赖 083+086，可与 087 并行）→ 087/089 → 090 → 091 → 092/093 → 094；ADM 支线：T-ACCESS-056 → 057（跨线依赖 R2 主线 083~086+088）/058 → 059 → 060 → 061 → 062（另依赖 092——两个完成条件在此会合）；T-PERM-054 收口于 T-ACCESS-058/061 之后。
- 2026-09-25 T-PERM-080 完成：全仓清点册见附录 A（清点口径、生产调用点逐点迁移目标、测试/文档/容量盘点与设计 §6.5 增补结论）；设计 §6.5 已增补指针与四消费面勘正。

## 附录 A：全仓旧执行体清点册（T-PERM-080 产出）

> 清点时点：2026-09-25，HEAD `320d16a87`，工作树 clean。清点方法：rg 程序化生成全量命中（符号见 A.0），逐行人工核实调用形态（区分真实调用 / Javadoc `{@link}` / 行注释），非手抄。行号为该时点快照，迁移卡执行时以符号重扫为准、本册为语义底册；**各节不写总量计数**（2026-09-25 去计数化拍板）——完整性校验方式=按 A.0 符号集重扫后与本册逐行比对，不比对汇总数字。

### A.0 清点口径与零命中声明

- 符号集：`PermQuery` / `PermBatchQuery` / `PermResult` / `PermBatchResult`（四旧 DTO）；`query(` / `queryBatch(`（直接执行入口）；`hasPermissionByCode` / `hasPermissionByEntityId`（单点门禁便捷入口）；`getDeniedResourceCodes` / `getDeniedEntityIds`（批量拒绝集合）；`computeInstanceDenied` / `passesScopeAll`（引擎私有阶段方法）。范围：全仓，排除 `docs/archive/**` 与 `**/target/**`；维度六面（设计 §9.2 R2-T01）：调用 / 语义 / 输出形态 / 事务边界 / 缓存序列化 / 协议。
- **零命中声明**（以下面均经 rg 程序化核实）：
  - gateway / example-service / e2e / common / perm-sdk 无任何直接引用（perm-common `BusinessKeyUtil:262/274` 仅 Javadoc 提及；外部消费全部经 HTTP 协议面——auth/check 族与 interface-snapshot 快照）；
  - 引擎类**反射调用零**（`getDeclaredMethods` 全仓命中 5 个测试文件——Feign 契约、审计切面、操作日志覆盖、HTTP 路径快照、perm-common DTO 契约——均为对自身接口/DTO 的方法枚举，无一反射引擎类）；**方法引用零**（无 `PermQueryEngine::` / `::query` 形态）；
  - `computeInstanceDenied` / `passesScopeAll` 为 `PermQueryEngine` 私有方法，生产代码外部直接调用零（仅 `ConditionAppServiceImpl:187` 行注释提及语义）；它们作为「旧执行体内部实现」随 T-PERM-092 整体删除，无独立迁移目标。
- 非消费点引用（注释/Javadoc 提及，属 T-PERM-092 删除时的文档清扫面，不设迁移目标）：`RequestContextInterceptor:337`、`OAuth2ResourcePathProperties:21`（两处「不接入 PermQueryEngine」决策注记）、`ResourceEntityMapper:73`、`UserMenuQueryAppService:11`（菜单判定经 PermissionViewAppService 间接消费）、`AccessCacheCatalog:169`、前端 `frontend/src/views/perm/grant/utils/source-chain.ts:5/16/241`（展示口径注释，运行时以引擎为准）。

### A.1 直接执行入口消费点（`query`/`queryBatch`）

全部为**旧执行体消费点**（迁新 execute）；其中 2 点同时属 LEGACY_API 业务模式（见 A.1 注）。

| # | 调用点 | 入口语义（协议面） | 构造 | 输出消费 | 事务 | 迁移目标（Selection＋ConditionMode/MutexMode＋ResultForm） |
|---|---|---|---|---|---|---|
| 1 | `engine/service/impl/PermissionCheckAppServiceImpl:96` check | 外部 `auth/check` 单项最终鉴权 | `forAuthCheck` | `PermResultUtils.toAuthCheckResp` | readOnly | 单项 TYPE_LEVEL（resourceCode=null）或 TARGET_SET＋EVALUATE/ENFORCE＋DECISION；外层保留主体解析/缺省策略/原响应 |
| 2 | `PermissionCheckAppServiceImpl:141` batchCheck | 外部 `auth/batch-check` | `PermBatchQuery.forAuthCheckBatch`（T-PERM-061 A+ 分组+请求级共享装载；evaluatedAt 请求级钉住） | `outcomes()` 按输入序回填 AuthCheckItemResult | readOnly | 多个独立 DECISION item **一次 execute 批量表达**；外层保留原序/重复项/请求级父上下文；禁循环 N 次公开 execute |
| 3 | `PermissionCheckAppServiceImpl:185` checkInterface | **LEGACY_API** 在线接口检查 | `forInterfaceCheck(API, entityIds, ACCESS)` | `toCheckInterfaceResp(q, 30)`（**cacheTtlSeconds=30 秒缓存有效期**，matched 结果记录全量回传——T-API-003 口径） | readOnly | 一个 API:ACCESS TARGET_SET **共同集合**（迁移期不拆项 OR；原注册门禁外层保留）；T-ACCESS-062 退役 |
| 4 | `engine/service/impl/PermissionQueryAppServiceImpl:119` queryResources | 外部 `query-resources` | `forUserView`（＋`inheritChildren`/`inheritParents` 展示面树扩展开关） | `allEntries`→白名单/排除/投影分页 | readOnly | GRANT_LIST＋FACTS；物理子孙展开在授权集合评估之后 |
| 5 | `PermissionQueryAppServiceImpl:285` queryScopes | 外部 `query-scopes` | `forScopeQuery` | `rawEntries`/`instanceEntries` 双轨→四态投影 | readOnly | GRANT_LIST＋FACTS，OutputSpec RAW_AND_KEPT（范围四态保留 raw） |
| 6 | `PermissionQueryAppServiceImpl:443` interfaceSnapshot | **LEGACY_API** 快照下发（gateway 消费） | 引擎 LIST 口径 | `SnapshotAssembler` | readOnly | GRANT_LIST＋PRESERVE/ENFORCE FACTS；有效位覆盖口径/dependent 排除/ALL 展开语义原样（设计 §6.6）；T-ACCESS-062 退役 |
| 7 | `engine/service/impl/PermissionViewAppServiceImpl:177`（`getEffectiveResourceAccess`） | 内部：权限视图＋**菜单有效资源链**（UserMenuQueryAppServiceImpl 间接消费） | `forUserView`（LIST 全量） | `PermViewAssembler:62` allEntries | readOnly | GRANT_LIST FULL＋有效操作投影；查看他人门禁（:71）是独立 USER VIEW 实例 DECISION |
| 8 | `grant/service/domain/impl/PermissionGrantDomainServiceImpl:168` checkCanGrant | 内部：转授资格（写校验面） | `forUserView`＋`evaluateConditions/Conflicts/MatchesBit(false)`＋`includeOperations(true)`＋**`bypassPermSnapshot(true)`** | `instanceEntries`→同行转授资格 | 写事务内 | GRANT_LIST＋PRESERVE＋SKIP＋FACTS，读来源 `ListGrantRead.DATABASE`（§9.1 bypassPermSnapshot 映射的现存实证）；同行资格判定留领域层 |

### A.2 单点门禁便捷入口（`hasPermissionByCode` / `hasPermissionByEntityId`）

全部为**旧执行体消费点**；语义两档由 `resourceCode/entityId` 是否为 null 决定（null=类型级），两个便捷入口自身直接返回 boolean（内部 `forValidate`/`forValidateByEntityId` + `query().allowed()`），调用形态几乎一律 `if (!engine.hasPermission…) throw SecurityException`（门面 ：61 为 return 透传、个别为复合条件快捷放行分支）。事务边界：全部在管理面 AppService 写/读事务内。迁移目标按两档收敛：**类型级 → TypeLevel＋EVALUATE/ENFORCE＋DECISION（单 requirement）；实例级 → TargetSet 单 clause＋EVALUATE/ENFORCE＋DECISION**（判定面继承=管理面默认开，Inheritance 按现行口径；ENTITY_ID 引用形态仅 ResourceManage 三处）。

| 文件 | 行:操作 | 类型级/实例级 |
|---|---|---|
| `audit/.../LogQueryAppServiceImpl` | 86/106 VIEW PERMISSION_CHANGE_LOG；141/171/196 VIEW OPERATION_LOG | 类型级×5 |
| `domain/.../BizDomainAppServiceImpl` | 89/226/261 MANAGE SYSTEM_CONFIG；151/174/197 VIEW DOMAIN | 类型级×6 |
| `domain/.../DomainConfigAppServiceImpl` | 89/219 MANAGE、156/184 VIEW（SYSTEM_CONFIG） | 类型级×4 |
| `platform/.../SystemConfigAppServiceImpl` | 75 MANAGE；130/152/174 VIEW | 类型级×4 |
| `infrastructure/credential/.../ServiceCredentialAppServiceImpl` | 55/76/110 MANAGE；123 VIEW（SERVICE） | 类型级×4 |
| `resource/.../DependencyAppServiceImpl` | 90/112/137/196/264 VIEW DEPENDENCY | 类型级×5 |
| `rule/.../ConflictRuleAppServiceImpl` | 259 CREATE；327/354/488 VIEW；391 UPDATE；576 DELETE（CONFLICT_RULE） | 类型级×6 |
| `rule/.../ConditionAppServiceImpl` | 103 CREATE 类型级；188 UPDATE 实例级（CONDITION） | 混合×2 |
| `type/.../OperationAppServiceImpl` | 125 CREATE；202/245 VIEW；300/372 MANAGE（OPERATION） | 类型级×5 |
| `type/.../TypeDefinitionAppServiceImpl` | 147 CREATE 类型级；356/481 VIEW/MANAGE 实例级（两态：`type!=null?instanceKey:null`）；421 VIEW 类型级快捷放行；644 MANAGE 类型级 | 混合×5 |
| `resource/.../ResourceManageAppServiceImpl`（ByCode） | 193/241 CREATE 类型级；429 VIEW 类型级快捷放行；874/1026 MANAGE_API_MAPPING 实例级（SERVICE）；976/979 VIEW 实例级+类型级两态（SERVICE） | 混合×7 |
| `resource/.../ResourceManageAppServiceImpl`（**ByEntityId**） | 415 VIEW；521/567 MANAGE（RESOURCE） | 实例级(ENTITY_ID)×3 |
| `resource/.../ServiceConfigAppServiceImpl` | 106/272 MANAGE 类型级；206 VIEW 实例级；233 VIEW 类型级快捷放行（SERVICE） | 混合×4 |
| `resource/.../ServiceSyncAppServiceImpl` | 134 SYNC_INTERFACE 实例级（SERVICE） | 实例级×1 |
| `role/.../RoleManageAppServiceImpl` | 139 CREATE 类型级；586 VIEW 类型级快捷放行；190 VIEW / 222/280 MANAGE 实例级（ROLE，code=roleId 串） | 混合×5 |
| `user/.../UserManageAppServiceImpl` | 216 CREATE 类型级；272 ENABLE / 278 UPDATE 实例级（USER） | 混合×3 |
| `grant/.../PermissionGrantAppServiceImpl` | 125/177 MANAGE 实例级；215/409 VIEW 实例级（ROLE 查看门禁） | 实例级×4 |
| `engine/service/impl/PermissionViewAppServiceImpl` | 71 VIEW 实例级（USER 查看他人门禁） | 实例级×1 |
| `engine/AdminPermissionValidatorImpl` | 61 类型级（hasTypeLevel）；131 实例级 | 混合×2（门面实现，透传至 A.4 消费链） |

### A.3 批量拒绝集合（`getDeniedResourceCodes` / `getDeniedEntityIds`）

全部为**旧执行体消费点**；输出形态一律「被拒键集合 → 回映射输入键过滤或整批拒绝」。**全部调用点同时是 T-PERM-095（PQ-01 跨 item 互斥最小修复）的语义影响面**——修复后「整批互斥全拒」变「各自判」，逐面确认职责随 T-PERM-095/081 落实。迁移目标统一：**每个目标独立 item 的 TargetSet/TypeLevel＋EVALUATE/ENFORCE＋DECISION，纯结果投影**（外层保留原键回映射、原祖先策略、空输入与业务异常）。

| 调用点 | 所在方法与语义 | 类型×操作 |
|---|---|---|
| `engine/AdminPermissionValidatorImpl:109` | 门面透传（`checkBatch` 整批非空即拒）→ `getDeniedResourceCodes` 门面方法唯一外部直连消费者 `FileAppServiceImpl:462`（ADMIN_FILE 文件夹可见性 VIEW）；同引擎路径的 `checkBatchInstanceLevel` 门面调用方见 A.4 门面链 | 透传 |
| `resource/.../ResourceManageAppServiceImpl:936` | removeApiMappingsByIds 整批门禁 | SERVICE×MANAGE_API_MAPPING |
| `ResourceManageAppServiceImpl:984/1001` | listApiMappings 服务范围过滤（:984 全量服务码 / :1001 行内映射服务码） | SERVICE×VIEW |
| `ResourceManageAppServiceImpl:436` | resolveVisibleResourceEntityIdsOrNull——**资源树可见性** | RESOURCE×VIEW（**EntityIds**） |
| `ResourceManageAppServiceImpl:631` | deleteResources 批量删除门禁 | RESOURCE×MANAGE（**EntityIds**） |
| `role/.../RoleManageAppServiceImpl:373` | deleteRoles 批量删除门禁（existingRoles 全集） | ROLE×MANAGE |
| `RoleManageAppServiceImpl:594` | resolveVisibleRoleIdsOrNull——角色列表可见性 | ROLE×VIEW |
| `user/.../UserManageAppServiceImpl:357` | deleteUsers 批量删除门禁 | USER×DELETE |
| `UserManageAppServiceImpl:455/575/722` | assignRole / assignRolesBatch / revokeRolesBatch——**角色候选集写守卫**（目标角色须可 MANAGE） | ROLE×MANAGE |
| `type/.../TypeDefinitionAppServiceImpl:430` | requireTypeViewPermission——类型定义列表可见性 | TYPE_DEFINITION×VIEW |
| `TypeDefinitionAppServiceImpl:655` | deleteTypesByIds 批量删除门禁 | TYPE_DEFINITION×MANAGE |
| `rule/.../ConditionAppServiceImpl:295` | deleteConditionsByCodes 批量删除门禁 | CONDITION×DELETE |
| `org/.../OrgVisibilityQueryAppServiceImpl:68` | filterVisibleOrgIds——组织可见性（T-PERM-042，code 解析下沉引擎） | ORG×VIEW |
| `resource/.../ServiceConfigAppServiceImpl:239` | 服务配置列表可见性 | SERVICE×VIEW |

**设计 §6.5 增补勘正**：矩阵 getDenied 行「语义变化四消费面（资源树、API 映射、资源依赖、权限树 ID 轨）」与实际清点不符——四面中「资源依赖」（DependencyAppServiceImpl 无 getDenied 调用，仅 ByCode 单点门禁见 A.2）与「权限树 ID 轨」（`/auth/query-permission-tree` 已随 T-PERM-059 于 2026-09-10 删除，无消费面）两面**不存在**，实存两面（资源树 :436、API 映射 :936/984/1001）；实际全量消费面以上表清单为准（多调用点方法仅 listApiMappings 一处，assignRole/assignRolesBatch/revokeRolesBatch 各 1 点）＋门面透传链。逐面确认清单以上表为准。

### A.4 `AdminPermissionValidator` 门面间接消费链（经门面调引擎，随门面实现一并迁移）

`auth/Oauth2ClientAppServiceImpl`、`user/UserAppServiceImpl`、`user/UserWriteAppServiceImpl`、`menu/MenuWriteAppServiceImpl`、`org/OrgAppServiceImpl`、`org/OrgWriteAppServiceImpl`、`org/UserOrgAppServiceImpl`、`org/UserOrgWriteAppServiceImpl`、`org/OrgTreeConfigAppServiceImpl`、`platform/NoticeAppServiceImpl`、`platform/DictAppServiceImpl`、`platform/JobAppServiceImpl`、`platform/FileAppServiceImpl`、`user/controller/AdminUserController`（唯一 controller 直接消费门面）、`role/UserRoleQueryAppService(+Impl)`。迁移目标＝设计矩阵「AdminPermissionValidatorImpl：普通最终 DECISION，显式管理继承」——门面接口形状可保留（§9.4 getDenied 可留薄门面），实现内部换新 execute；当前操作者/SecurityException 与技术错误分界保留。

### A.5 `PermResult` 结果加工面

| 加工点 | 消费访问器 | 迁移目标 |
|---|---|---|
| `engine/util/PermResultUtils`（toAuthCheckResp/toCheckInterfaceResp 本体与 :68/:93 `allEntries().hasCondition`；A.1#1/:96、#3/:185 为其调用侧） | allowed/reason/allEntries/matchedRoleIds/matchedPermissionIds/resourceMap/operationMap | §9.1：改为新结果→既有外部响应的**纯转换**或删除，不重建旧 PermResult 再转换 |
| `engine/util/SnapshotAssembler:116` | `allEntries` | LEGACY_API 快照装配（A.1#6 调用），随快照退役（T-ACCESS-062） |
| `engine/util/PermViewAssembler:62-64` | `allEntries`＋effectiveOperationEntries/resourceMap | 视图投影（A.1#7 调用），改消费新 GrantFact |
| `PermissionQueryAppServiceImpl:133/327/328` | allEntries / rawEntries / instanceEntries | queryResources/queryScopes 投影，改消费 GrantSetResult 分阶段 raw/retained |
| `PermissionGrantDomainServiceImpl:170` | instanceEntries | 转授同行资格（A.1#8），改消费新 FACTS |

（引擎内部 `PermQueryEngine:985 parentResult.allEntries()` 为父判定递归内部消费，随旧执行体删除。）

### A.6 缓存序列化面

- **`ROLE_PERM_SNAPSHOT`（AccessCacheCatalog）＝`List<RolePermEntry>`**，L2_ONLY，引擎 `resolveBitMasks` 读写——旧执行体的唯一缓存序列化接触面；`RolePermEntry` 即设计 §5/§9.1 预留的「缓存边界例外保留」对象，T-PERM-084/094 处理读来源分桶与失效时沿用或替换该载荷，T-PERM-092 删除时禁止连带删除该目录条目。
- `PermQuery`/`PermResult`/`PermBatchQuery`/`PermBatchResult` **均不进任何缓存载荷**（全仓核实零序列化点）；四旧 DTO 退出（T-PERM-092）无缓存兼容动作。
- 写路径失效联动（OPERATION_PERMISSIONS_BY_TYPE 等）不直接消费旧执行体符号，失效链路随 T-PERM-094 演练覆盖。

### A.7 测试夹具（7 组）

| 组 | 文件 | 迁移目标 |
|---|---|---|
| 引擎与 DTO 自身 | `engine/core/PermQueryEngineTest`、`engine/dto/PermResultTest` | T-PERM-085~088 新核心测试的对照底册；旧断言随 T-PERM-092 删除 |
| engine 服务单测 | PermissionCheckAppServiceImplTest、PermissionQueryAppServiceImplTest、PermissionViewAppServiceImplTest、AdminPermissionValidatorImplTest、AdminPermissionValidatorImplHasTypeLevelTest | 随 A.1/A.4 被测面在 T-PERM-089~091 改写（外部响应断言尽量原样保留作回归锁） |
| 结果加工器单测 | `engine/util/PermViewAssemblerTest`、`engine/util/SnapshotAssemblerTest` | 随 A.5 加工面改写（PermResultUtils 无独立测试类，经服务单测覆盖） |
| grant 域单测 | PermissionGrantDomainServiceImplTest（转授资格） | 随 A.1#8 被测面在 T-PERM-091 改写 |
| 管理面单测（mock engine） | TypeDefinition/Operation/Condition/ConflictRule/RoleManage/UserManage/BizDomain/DomainConfig/SystemConfig/ServiceConfig/ServiceSync/Dependency/ResourceManage/PermissionGrantApp/ServiceCredential/LogQuery×2/OrgVisibility/FileAppService/OperationLogRuntimeContext/ResourceDeletePermChangeRegistration | 随 A.2/A.3 被测门禁在 T-PERM-089~091 改写 mock 面（mock 目标从 PermQueryEngine 换新 execute/门面） |
| characterization PgIT | PermissionCharacterization、TargetModeClosure、BatchAuthCheck、GoldenFixture、AutoGrantEngineContract、AuthorizationChangeInvalidation（失效链）、OrgTreeIncludePositions、InstanceGateBusinessCode | **R2 语义基线资产**：T-PERM-081 基线在其上补 PQ-01/06 反例；T-PERM-094 差分对照主力，不全量重写 |
| 写路径/级联 PgIT 与契约测试 | UserRoleWriteProjection、TypeDefinitionProjection、OperationPermissionCacheEviction、MixedTypeDeletionLock、ServiceConfigCascade、ResourceOperationKey、ResourceBatchCreateCompositeIdentity、DependencyLifecycle、AutoGrantMaterialization、AutoGrantInsight、RoleMutexGuard、FirstAdminUserTrack；BatchEntrySizeValidationTest（@Size(max=1000) 三类放大面锁） | PgIT 断言走业务端点不经旧执行体符号者零改动、引用符号者随被测面改写；BatchEntrySizeValidationTest 迁移目标=持续锁新入口等价上限（T-PERM-089/090 契约面） |

（`PermCommonReqContractTest` 的 `getDeclaredMethods` 为 DTO 反射契约枚举，不消费引擎符号，不入册计数。）

### A.8 活文档引用清单（T-PERM-092 删除前清扫面；superseded 档案不入——留原位待物理归档、不改写，2026-09-25 拍板）

高密度（正文以引擎口径行文，回写主目标）：`engine/implementation.md`（最密，R2 完结后 §3 族整体重写）、`engine/overview.md`、`engine/core-flows.md`。契约/治理面（门禁矩阵与调用口径行）：`access-service-api-contract.md`、`access-service-architecture.md`、`project-rules.md`、`access-service-capability-structure.md`、`org-user-permission-contract.md`、`schema/access-service.sql`（注释）、`permission-center-v3.5-design.md`、`dependency-auto-grant.md`、`extension-guide.md`、`iam-task-closure.md`、`frontend/permission-grant.md`、`frontend/service-interface-mapping.md`（:127 `getDeniedResourceCodes` 带日期划线历史注记——T-PERM-092 触达时按「带日期历史句」口径定去留）。治理索引：AGENTS.md、docs/README.md、docs/design/README.md、decision-registry.md（历史定案行不改写）、CHANGELOG.md。文档清扫统一口径=代码退役后按「现行文档规范回写」执行（本计划归档条件），decision-registry 历史行例外保留。

### A.9 容量与上限盘点

- **外部 batch 上限（协议面）**：引擎批量入口 DTO `@Size(max=1000)` Bean Validation、HTTP 层 `@Valid` 拒 400（`BatchEntrySizeValidationTest` 锁定，perm-common 单源——服务端与 SDK 共用）。三类放大面：① 判定面闭包 CTE 入参（`UserAssignRoleReq`/`UserRoleBatchRevokeReq` 角色集→getDeniedResourceCodes；`ResourceKeysReq`/`IdsReq`→getDenied* 直连）；② 逐项完整引擎管线（`BatchAuthCheckReq.items`）；③ 内存网格笛卡尔组装（`QueryScopesReq` 三列表）。
- **内部 getDenied 容量（引擎面）**：`getDenied*` 与 `TypeResolutionService.batchResolve*` **无独立容量约束/无 IN 分批**（无 partition/chunk 逻辑，全仓核实）——容量完全委托外部 DTO 层上限；`getDeniedResourceCodes` 入参做 LinkedHashSet 去重但无上限。此现状落账给 T-PERM-085/093（候选/预算与性能测量卡）：新 execute 是否引入内部预算以书面结论为准，不由本清点预设。
- 输出面既有口径：checkInterface `toCheckInterfaceResp(q, 30)` 的 30 是 **cacheTtlSeconds（快照缓存有效期 30 秒）**，matched 结果记录全量回传（T-API-003 定案口径，无截断）——LEGACY_API 兼容口径，迁移时原样保留。
