---
doc_type: design
title: AccessMesh 扩展指南（接入与二次开发全景）
status: 待确认（评审稿，T-FE-023 产出）
domain: common
design_refs:
  - docs/design/architecture.md
  - docs/design/permission-center/api-contract.md
last_reviewed: 2026-09-12
---

# AccessMesh 扩展指南（接入与二次开发全景）

> 本文是面向**接入方与二次开发者**的场景驱动导引：业务服务如何接入鉴权、如何声明自有权限维度、用户/角色体系如何适配、管理台如何加页面。契约细节不在本文重复——每节指向唯一权威文档。
>
> 2026-09-12 T-FE-023 立项时用户定案：指南定位为**跨端全景**（本文件，落 design 根），取代原 design_refs 的 `frontend/extension-guide.md`（纯前端口径，已放弃）。

## 1. 扩展面总览

| # | 扩展面 | 回答的场景提问 | 权威契约 | 验证资产 |
|---|---|---|---|---|
| 1 | 业务服务接入 | 「我的服务怎么接入 AccessMesh 鉴权？」 | api-contract §6.3；`services/example-service.md` | `ExampleProtectedApiE2EIT` |
| 2 | 自有资源类型 | 「我要按门店/项目/单据控制权限，怎么建模？」 | api-contract §5.1/§5.3/§6.2.2/§6.3；类型所有权声明见 api-contract §5.1 | `CustomResourceTypeSlicePgIT`（本指南配套） |
| 3 | 主体/角色体系 | 「我的用户/角色体系与标准设计不一致」 | api-contract §5.2 + §6.3.1（syncTypes 白名单） | AbstractUserSyncAppServiceTest 等单测组 |
| 4 | 条件与范围 | 「时间/IP 限制、数据范围怎么配？边界在哪？」 | api-contract §5.6/§6.7；core-flows | 条件双轨制用例组（T-PERM-048） |
| 5 | 前端页面 | 「我想在管理台加自己的页面」 | `frontend/README.md` 及各页面设计 | 前端 view hook 测试组 |

**架构原则**：AccessMesh 的扩展模型是**数据声明式**，不是代码插件式。除前端页面外，所有扩展通过「声明类型 + 同步数据 + 配置授权」完成，不要求接入方编写平台内代码。无权限判定 SPI 插槽——判定语义由统一引擎（`PermQueryEngine`）唯一承载（见 §6 能力边界）。

## 2. 场景一：业务服务接入接口鉴权（example 模式）

完整活例见 `docs/design/services/example-service.md` 与 E2E 测试 `ExampleProtectedApiE2EIT`。

### 2.1 接入步骤

1. **注册服务**：管理台「服务+接口映射」页（`POST /api/perm/service-config/save`）登记 `serviceCode`/`name`/`status=1`。
2. **声明接口**：`POST /api/perm/service-config/sync`（FULL 模式）上报接口清单——一步创建 **API 资源**与 **Gateway 路由映射**（`pathPattern = basePath + path`，行归属标记 `maintainSource=SERVICE_SYNC`）。API 类型恒为 MANAGED，**不要**走 `resource-entity/sync` 通道（会被 `RESOURCE_TYPE_OWNERSHIP_DENIED` 拒绝）。
3. **请求链路**：业务前端持平台会话令牌（`Authorization: Bearer <token>`，sa-token）经 **Gateway (8080)** 访问业务接口；Gateway 按映射做接口级判定（`check-interface`）并对可下发条件做本地重评。
4. **服务侧防直调**：业务服务部署 Gateway 签名校验过滤器（example 的 `GatewaySignatureFilter` 模式）——拒绝未带有效网关签名的请求，防止绕过 Gateway 直调后端。

### 2.2 服务身份调用（auth/check 等平台 API）

业务服务调用权限查询类 API（`/api/perm/auth/check`、`batch-check`、`query-resources`、`query-scopes`）时的身份：

| 请求头 | 说明 |
|---|---|
| `X-Internal-Secret` | 内部凭证，值 = 部署配置 `PERM_INTERNAL_SECRET` |
| `X-Service-Code` | 本服务编码；凭证验证通过后绑定为可信服务身份 |
| `X-Tenant-Id` | 目标租户（服务调用缺失即 400） |

同步类通道（resource-entity/sync 等）的 payload `sourceService` 必须与凭证身份一致（`SyncAuthVerifier`），防止冒充他服务。

### 2.3 SDK 现状

- `perm-client-spring-boot-starter`：**Feign 远程查询 SDK**（`PermissionFeignClient`：auth/check 族调用入口 + 内部同步拦截器）。适合需要在服务内主动查询权限/范围的场景。
- `perm-gateway-spring-boot-starter`：网关侧装配（本项目 Gateway 自用）。
- example-service **有意不消费** starter——接口级鉴权完全由 Gateway 承担，服务内零权限代码。这是当前推荐的轻接入形态。
- 纯 HTTP 对接（非 Java 技术栈）：直接按 api-contract §6 契约调用，不要求 SDK。

## 3. 场景二：自有资源类型（自定义数据权限维度）

「按门店/按项目/按单据控制权限」的答案：**声明自有资源类型 + 同步资源实例 + 授权**。配套回归锁：`access-service` 容器测试 `CustomResourceTypeSlicePgIT`（声明→操作定义→服务身份同步→授权→引擎判定的完整链路）。

### 3.1 第一步：选择所有权模式

每个 resource_type 单一所有权，声明于 `type_definition.extra`：

| 模式 | 语义 | 适用 |
|---|---|---|
| `MANAGED`（缺省） | 管理台手工 CRUD 维护资源 | 资源量小、人工维护（如自定义目录） |
| `SYNC` | 声明来源服务（`syncSourceService`）独占同步，管理面只读（写操作 20055） | 资源事实在业务系统里（订单、门店、项目） |

声明约束（api-contract §5.1 类型所有权声明段 + §6.2.2 同步入口门禁）：SYNC 来源必须为已注册、未软删、`status=1` 的服务；类型下存在有效资源行时声明不可变更（20056），**系统预置类型（is_system=true）所有权声明一律钉死不可变更**（20056）；API 类型禁止声明 SYNC。

### 3.2 完整链路（六步）

```text
① 注册服务（service-config/save，status=1）
② 声明类型（type-definition/create：typeKey=resource_type + typeCode=自有码
   + extra={"managedMode":"SYNC","syncSourceService":"<服务码>"}）
   ——创建即自动预置 CRUD 四操作（CREATE/VIEW/UPDATE/DELETE，位 1/2/4/8）
③ 按需追加自定义操作（operation-permission/create：resourceTypeCode=自有码 + code
   + binaryBit——操作位空间按类型隔离，binaryBit 类型内唯一，避开预置位）
④ 同步资源（resource-entity/sync 单条 UPSERT/DISABLE/DELETE 或 full-sync 全量 diff；
   服务身份请求头见 §2.2；DISABLE 为幂等停用，非删除）
⑤ 授权（管理台授权页 apply-grant-plan，或 API：roleTypeCode+roleExternalId
   + key{resourceTypeCode, resourceCode, codeType, operationCode, scopeMode}）
⑥ 判定（业务侧 auth/check：subjectTypeCode + subjectExternalId + resourceTypeCode=自有码
   + resourceCode + operationCode → allowed/reason；主体类型须匹配——本地登录用户是
   LOCAL_USER（user_type=3），USER（=1）是族内另一类型，外部同步主体用自有 subject_type）
```

### 3.3 门禁与错误码速查

| 错误 | 触发面 | 含义 |
|---|---|---|
| `RESOURCE_TYPE_OWNERSHIP_DENIED` | sync/full-sync 入口 | 类型非 SYNC / 来源不匹配 / 来源服务未注册或停用 |
| `20055 RESOURCE_EXTERNALLY_MAINTAINED` | 管理面 create/update/move/remove | SYNC 类型管理面只读 |
| `20056 TYPE_OWNERSHIP_CHANGE_CONFLICT` | 声明变更/类型删除 | 类型下有有效资源行；系统预置类型（is_system）声明一律钉死 |
| `20040 GRANT_CANNOT_DELEGATE` | apply-grant-plan | 授予者未持有覆盖目标键的可转授权限（新类型首笔授权见 §3.5） |
| `20048 AUTO_GRANT_NOT_SUPPORTED` | create/update/batch-sync | 自动授权暂缓（T-PERM-035），`autoGrant=true` 一律拒绝 |
| `20005 / 20044` | 操作码解析/清单校验 | 未知操作码 fail-closed / 畸形清单零副作用 |

### 3.4 资源树与父子关系

资源可声明跨类型父子边（`parentResourceTypeCode` 可与 item 类型不同）；SYNC 类型资源出现在管理面资源树（读路径不受限），授权页按类型出矩阵。依赖补全（depend_on 触发自动授权）**当前不生效**——`autoGrant` 全入口拒绝（见 §6）。

### 3.5 首笔授权引导（创建即建授权根，T-PERM-062）

授权委托校验（`checkCanGrant`）**严格无旁路**：授予者必须已持有覆盖目标键且 `canGrant=true`、无条件的授权行。bootstrap 固定图只覆盖**种子类型**——若无生命周期钩子，一个全新自定义类型在创建后**没有任何人能经 apply-grant-plan 完成首笔授权**（一律 20040 `GRANT_CANNOT_DELEGATE`）。

**T-PERM-062（2026-09-12 定案）后该缺口在产品内自举闭环**，接入方按 §3.2 走完「创建类型 → 追加操作 → 授权页」即可首授，**无需部署方种子或手工 SQL**：

- **创建即建基座**：`type-definition/create` 同事务向「类型所有者角色」写 CRUD 四操作位首授行（`grant_source=AUTHORITY_ROOT`，类型级 scopeAll + 可转授）；所有者缺省引导角色 `bootstrap-admin`，可经请求字段 `ownerRoleTypeCode/ownerRoleExternalId` 指定（roleTypeCode 仅接受 BASIC_ROLE 功能角色；类型定义页「所有者角色」选择器同入口），指针持久化于 `type_definition.extra.grantOriginRole`。
- **追加操作自动补种**：后续经 `operation-permission/create` 追加的操作（如 `EXPORT` 位 16）同事务向同一所有者补种——不会出现「CRUD 能授、EXPORT 仍 20040」。
- **所有者可迁移**：`type-definition/update` 变更 `extra.grantOriginRole` = 同事务「先清后种」迁移（旧所有者种子清理、新所有者补齐全部操作位）；所有者角色被误删时经重指所有者即可恢复授权能力。种子行在授权页只读（20061），类型删除时级联清理。
- **可发现性**：所有者的成员在授权页可见这些种子行（标注「授权根」），并可正常收窄为实例级授权；非所有者成员对无授权根类型发起授权仍 20040——message 中 `reason=TYPE_GRANT_ORIGIN_MISSING` 表示「类型未初始化」（去类型定义页确认所有者），`reason=NO_PERMISSION/NO_GRANT_RIGHT` 表示「你的持有面不够」（找所有者角色成员操作）。

回归锁：`CustomResourceTypeSlicePgIT` 全链路固化——创建即落 4 条种子、追加操作补种第 5 条、管理员直接首授成功（原「部署方种子后放行」步骤已随修复退役）、非所有者仍 20040、种子行改删 20061、所有者迁移清理+补齐、零授权根时 reason=TYPE_GRANT_ORIGIN_MISSING。

## 4. 场景三：主体/角色体系适配

「接入方用户体系与标准设计不一致」的两条路：

1. **本地主体**：平台自管用户（管理台组织与用户页创建，`LOCAL_USER`）；适合接入方把账号体系交给 AccessMesh。
2. **自有主体类型 + 用户同步通道**：接入方在 `service_config.extra.syncTypes.subjectTypeCodes` 白名单声明自有 subject_type，经 `POST /api/perm/abstract-user/sync` 同步主体（同 §2.2 服务身份）。角色同理：自有 role_type + `POST /api/perm/abstract-role/sync`（`roleTypeCodes` 白名单）。

边界：内置事实链路类型（USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION/CONDITION 等）已声明为 access-service 内部 SYNC——**外部同步一律拒绝**，需要差异化建模时请声明自有类型（如 `BI_MENU`），不要试图写公共类型。

## 5. 场景四：条件与范围权限

### 5.1 条件（时间/IP 类环境断言）

- **封闭操作符集**：`DATE_RANGE`（日期区间）、`TIME_RANGE`（时段）、IP 白名单、IP 黑名单，共 4 类。**不可自定义条件操作符**（求值器 `ConditionEvalUtils` 为 perm-common 静态实现，Gateway 与 access-service 共享同语义；无扩展插槽）。
- **双轨制**（T-PERM-048）：管理页条件（source=MANAGED，独立 CRUD、可复用）vs 授权 INLINE 内联条件（随授权记录声明，仅 source=INLINE）。
- 时钟语义：`evaluatedAt` 由引擎统一注入（跨进程一致性靠 NTP，业务粒度按天/小时）。

### 5.2 范围权限（scopeMode 四态）

资源范围判定四态（契约见 api-contract §6.7）：`INSTANCE`（单实例）/ `ALL`（类型全量）/ `DENIED`（无操作权限）/ `EMPTY`（有权限但条件/互斥过滤后无数据——返回空结果不发 SQL）。注意**授权配置侧只用 `INSTANCE`/`ALL` 二态**（apply-grant-plan 的 scopeMode），`DENIED`/`EMPTY` 是查询响应侧的判定结果。

### 5.3 特殊判定逻辑（外部审批等）怎么落地

无代码级判定插槽（§6）。推荐路径：审批流转在接入方系统内完成后，由接入方服务身份或管理员经授权写入口（apply-grant-plan）落授权——权限生效路径与人工授权完全一致，可审计、可回收。

## 6. 能力边界（不可扩展项清单）

| 项 | 状态 | 说明 |
|---|---|---|
| 条件操作符扩展 | **封闭** | 仅 4 类内置操作符，无 SPI |
| 权限源扩展 | **不存在** | 统一引擎唯一事实源（role_perm_entry + 同事务投影）；历史上设想的「权限源策略」已随 T-PERM-057 统一引擎重构收编 |
| 自动授权（依赖补全） | **暂缓** | T-PERM-035 未排期；写入口 `autoGrant=true` 全拒（20048）；`resource_dependency` 数据模型已就绪 |
| 动态数据权限端到端 | **延后** | T-PERM-036 延后至 example-service 演示；scopeMode→SQL 映射契约已定（api-contract §6.7） |
| 内置事实链路类型写入 | **禁止** | USER/ORG/MENU/ROLE 等归 access-service 内部 SYNC，外部同步一律拒（§4） |
| 异常告警通知渠道 | **不做** | 异步异常可观测性由 log.error 承载；钉钉/邮件等告警渠道不在开源 IAM 核心范围（T-PERM-038 定性），接入方经日志采集侧自行对接 |
| resource-dependency batch-sync 前端 UI | **P0 不做** | 后端端点已收口可用；前端不暴露按钮（Q5=B 决策） |

## 7. 场景五：前端页面扩展（管理台二开）

管理台基于 pure-admin-thin（Vue 3 + Element Plus）。新增一个管理页面的标准模式（以现有 13 页为活例）：

1. **权限串声明**：`src/views/system/<page>/utils/perms.ts` 导出 `<PAGE>_PERM_LIST`（操作码常量，对齐后端 `OperationCodeConstants`）。
2. **路由注册**：`src/router/modules/*.ts` 路由项 `meta` 引用 PERM_LIST（按钮级 `auths` / 页面级门禁）。注意侧栏菜单已切后端派生（T-FE-015）：**可见性由菜单数据的 ∃op 派生决定，`meta.showLink` 不再控制侧栏**。
3. **菜单种子**：sys_menu 行（bootstrap 固定图或管理台菜单管理页创建）；菜单可见性 = 该类型**存在任一可授权操作**（∃op 派生）自动可见，无需逐菜单授权。
4. **API 层**：`src/api/<page>.ts`——全部 POST + JSON Request DTO（禁 GET/RESTful，project-rules §API），响应统一信封 `{code, data, message}`。
5. **页面分组**（可选）：业务域页为资源类型配置 `CLASSIFY`（domain_config），管理查询按域过滤（ALL/GLOBAL_PLUS/DOMAIN_ONLY 三模式）。

前端工程约束（pnpm/构建/布局）见 `docs/design/frontend/README.md` 与前端 rules（frontend-coding-standards、frontend-layout-patterns、css-design-system）。

## 8. 验证资产索引

| 资产 | 轨道 | 覆盖 |
|---|---|---|
| `CustomResourceTypeSlicePgIT` | access-service 容器组 | 场景二完整链路（本指南 §3 的回归锁；含 T-PERM-062 授权根锁组：创建即落种子/追加操作补种/非所有者 20040/种子行改删 20061/所有者迁移/零授权根 reason=TYPE_GRANT_ORIGIN_MISSING；负向组：裸用户 NO_ROLE / 未同步资源 fail-closed） |
| `ExampleProtectedApiE2EIT` | e2e 模块 | 场景一完整链路（注册→接口声明→403→授权→30s 内生效） |
| `BasicRoleGrantVerticalSliceE2EIT` | e2e 模块 | 内置类型授权垂直切片（bootstrap→建号→授权→判定） |

> 新增扩展面相关改造时，若改变本指南描述的链路步骤或门禁语义，须同步更新本文与对应验证资产（文档治理：指南为导引层，契约变更仍以 api-contract 为准先行）。
