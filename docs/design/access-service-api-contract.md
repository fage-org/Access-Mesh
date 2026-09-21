---
doc_type: design
title: access-service API 契约总册
status: adopted
domain: cross-service
supersedes:
  - docs/design/permission-center/api-contract.md
  - docs/design/services/admin-service-api-contract.md
last_reviewed: 2026-09-21   # T-PERM-071：新增 §19.10 独立依赖 manifest 章与 §19.2.1 发布代次（M2M 端点 integration/permission-manifest/full-sync+RESOLVED/REJECTED 逐项诊断）、§12.3 管理写路由退役口径、§2.5 大写锁覆盖清单补 manifest 端点；此前 2026-09-20 T-PERM-070：新增 §24 服务凭证与 M2M 服务认证章（/api/access/service-credential/* 四端点+仲裁状态表+M2M 白名单单源+错误码 20065~20068+SDK 配置键）；此前 2026-09-19 T-GW-009（registry 定案⑤）：§7.7 补 Gateway 白名单放行注记（/api/access/user/reset-password 纳入会话入口族，服务层门禁零改动）+ §6.4 会话入口白名单族口径补记；此前 2026-09-18 T-PERM-068 外评处置（claude P2×1 + grok P2×1 + 共同 P3×1，全采纳）：§19.1 父字段组改为仅 UPSERT 生效（DISABLE/DELETE 忽略父字段，用户拍板）+ 解挂显式清 parent 列落库（grok P2：flex update(entity) 忽略 null 列，applied=true 旧父残留 fail-open——UpdateEntity 先例修复）+ 新增 DELETE 有子拒绝 CHILDREN_EXIST 句（用户拍板，DEPENDENCY_MISSING 可重试先于版本写入）；此前 2026-09-17（Q-007 三定案）：§19.1 父资源定位与同类型门禁句改写（父字段组激活条件=parentResourceCode 非空、typeCode 缺省回填自身类型、显式异类型 NON_RETRYABLE/PARENT_TYPE_MISMATCH 先于父解析与版本写入）、§19.2 full-sync 父默认值句改写（缺省回填 scope 类型自本批为真实实现语义——原「默认同类型」与实现半传静默解挂漂移已修）、§12.1 规则表补 create/batch-create 父校验行（20053/20004 对齐 move + 裸 parentId 补校验）+ SYNC 只读行「sync 通道允许跨类型」改「入口已收紧、DB 直写脏数据防线」；此前 T-PERM-066/067 外评处置（claude P3×1 + grok P2×1，全采纳修复）：§2.5 typeCode 注记修正（可选——null 与空串放行走留空生成分支，`^$` 显式放行；正则同步改 `^$|^[A-Z][A-Z0-9_]*$`）、§7.7 force_reset_pwd 置位口径（自身自助改密置 false、非自身置 true——归档设计「修改个人密码成功后置 false」回归）；此前 2026-09-14 T-PERM-067 USER 写入口自身豁免收窄（Q-002 转出）：§4 表 /user/update 行、§7.4 门禁、§7.7 门禁/force_reset_pwd/验收要点（自身=自助改密通道定位，证伪「走另外的修改密码接口」旧句）、§7.8 abstract-user update/remove 行（perm 轨死分支语义统一）、§21.2 验收 2、§22.2 决策 13——档案字段自我豁免保留、启停/删除不豁免；此前 2026-09-14 T-PERM-066 operationCodeKey 族入参大写：§2.4 操作行注记 + §2.5 新增集中注记（@Pattern 覆盖面/边界/守卫/SDK 生效），授权域归一退役两域统一 raw；此前 2026-09-14 T-ACCESS-041 域叙事改管理面/权限面口径：归并定位/术语映射更新、「管理域家族」→「管理面家族（裸路径族）」全局换词（9 处）、§2/§4/§5/§10/§21/§22 及附录域前缀表述清扫；契约语义零变化；此前 2026-09-13 T-ACCESS-034 操作码合一与 USER 轨细粒度化：§4 操作码常量行改挂唯一常量源 OperationCode（原两册常量类删除）、§7.8 补管理端点字段分档门禁表（update 分档/空 patch 90001/首管理员放行/USER:MANAGE 退役）、§12.1 remove 示例与 §16.4 参照系措辞对清；此前 2026-09-13 T-ACCESS-040 契约深合一：原《Permission Center 外部 API 契约》与《Admin Service 对前端 API 契约》两册并为一份总册——按能力分章、两个 URL 家族同册分列，契约内容语义零变化（仅章节重组、交叉引用重锚、旧包名事实性修正）；两合并源已转 superseded 留原位可解析
---

# access-service API 契约总册

> 本总册由 T-ACCESS-040（2026-09-13）将两份契约深合一而来：**管理面家族（裸路径族）**（原《Admin Service 对前端 API 契约》，T-ACCESS-040 原称「管理域家族」，控制器裸路径 `/user`、`/org` 等，前端经网关 `/admin/api/**` 访问）与 **perm 家族**（原《Permission Center 外部 API 契约》，`/api/access/**` 命名空间）同册分列，按能力分章（第 6~18 章 = auth/user/org/menu/role/grant/resource/type/domain/rule/audit/platform + engine 引擎子系统，第 19 章 sync 同步通道）。两 URL 风格并存曾登记 docs/pending-problems.md Q-001——已于 T-ACCESS-042（2026-09-15）统一为单命名空间 `/api/access/**`（本行前半保留合并来源的历史描述，裸路径/`/admin`、`/perm` 网关前缀均为退役形态）。章节锚点映射与合并源对照见文末「附录 C 合并源对照」。
>
> **归并定位（T-ACCESS-012，2026-08-22；T-ACCESS-041 更新，2026-09-14）**：原独立服务 `permission-center` 与 `admin-service` 已归并为 access-service，两服务的「permission 域 / admin 域」称谓又已随能力包融合（T-ACCESS-033）退役——文中「admin / admin 域」按**管理面**理解（管理能力包，T-ACCESS-042 起随单命名空间以 `/api/access/**` 对外——原裸路径族经 Gateway `/admin/api/**` 的形态已退役）、「permission-center / permission 域」按**权限面**理解（引擎子系统 + 权限事实能力包，`/api/access/**`）；不再存在跨服务同步链路。access-service 内部包结构已随 T-ACCESS-033 重构为能力包（本文提及的旧包名按能力包口径理解，`access.application` 写编排已解散入 user/org/menu 能力）。
>
> **全局注记（2026-06-20 审计 S-001 + T-PERM-018 收尾）**：`permissionVersion` 字段已随 T-PERM-018（缓存下沉）从所有响应体移除——令牌「唯一真正作用是 INTERFACE_SNAPSHOT 缓存 key」已核实，permission-center 侧该 L2 缓存已删，令牌随之失效，连带 304/notModified 死代码一并清除。历史段落保留的字段描述仅作演进记录，以代码为准。
>
> **scopeMode 协议定义（2026-06-27 T-PERM-011，T-PERM-013 落地完成）**：对外协议字段统一使用 `scopeMode`，不再暴露旧 boolean 范围字段。`auth/query-scopes.scopeGroups[]` 使用四态 `DENIED / INSTANCE / ALL / EMPTY`；授权请求、授权配置响应、接口快照项和 `query-resources` 等权限事实列表项只使用 `INSTANCE / ALL`。授权请求侧 `INSTANCE` 表示具体实例范围且必须传 `resourceCode/codeType`，`ALL` 表示资源类型 + 操作下全量范围且不传 `resourceCode/codeType`。授权写入口（apply-grant-plan 的 `key.scopeMode`）DTO 类型与查询侧共享四态枚举，但写入口仅接受 `INSTANCE/ALL`——`DENIED/EMPTY` 提交按参数校验拒绝（20027 `VALIDATION_FAILED`，`ScopeModeSupport.validateGrantScopeMode` 值域校验）。数据库内部仍保留 `role_resource_permission.scope_all` 作为存储字段，由服务端完成协议层映射（`ScopeModeSupport`）。旧 `scopeAll` boolean 字段不再出现在任何外部响应中。
>
> **关联文档（迁移自原 admin 册头部）**：`project-rules.md`（强约束：报文/接口/异常/错误码段）、`engine/` 三档与本文档（原 permission-center 设计内档，T-ACCESS-040 迁位）、`default-org-tree-user-lifecycle.md`（默认组织树身份目录边界）、`org-user-permission-contract.md` v1.2（页面门禁与岗位=特殊组织决策）、`schema/access-service.sql`（字段事实，唯一权威 DDL）、`../archive/2026-08-22/admin-service.md`（原 admin-service 服务设计，已 superseded）。
>
> **覆盖面说明（T-ACCESS-040 登记）**：本册承载原两册的全部成册契约。access-service 另有五组管理面端点族未在原两册成册（`/api/access/auth` 登录族、`/api/access/dict/*`、`/api/access/notice/*`、`/api/access/job/*`、`/api/access/login-log/page`），维持现状以代码与 `HttpApiPathSnapshotTest` 快照为准——登记见 §6.4 与 §17.3，不在本册补写（不新增契约内容）。第六组 `/config/*` 已随 T-ACCESS-037 退役（2026-09-13，僵尸端点删除，见 §17.3 注记）。另有四个零散端点未单独成册（`/api/access/user/detail`、`/api/access/user/user-menus`、`/api/access/org/detail`、`/api/access/role/my-info`——仅存在于门禁表或快照，T-ACCESS-040 评审登记）

> **自动授权交付边界**：资源 sync/full-sync 与可选 manifest 独立提供（§19）。管理依赖写入口、autoGrant 字段及 20048 已退役；071 的编译与资源发布组件已实现，其余生命周期/SDK 及 072～073 的物化、解释以任务卡为准，不能把已编译依赖等同于已交付自动授权。设计见[简化方案](dependency-auto-grant.md)。

## 1. 设计目标与接口分层
### 1.1 设计目标（perm 家族）

- **统一命名空间**：所有稳定对外接口统一使用 `/api/access/{resource}/{action}`。
- **保持通用性**：运行时和外部接入接口使用稳定业务键，避免外部系统必须感知权限中心内部主键。
- **保持强约束**：所有接口 `POST + application/json`，禁止 URL Path 参数和 Query 参数。
- **保留扩展空间**：响应 `data` 必须是对象，列表也用 `{ "items": [...] }` 包装。
- **安全多租户**：租户、操作者、调用来源优先来自 Header/Token/SecurityContext，不信任请求体里的同名字段。
- **SDK 友好**：DTO 进入独立 `permission-center-api` 或 `perm-common` 契约模块，不复用服务端内部 `Req/Resp`。
- **多形态接入**：对外交付目标分为 Spring Boot starter、普通 Java client SDK 和其他语言 HTTP 接入文档三层，稳定 API 契约必须同时服务这三类调用方。

### 1.2 接口分层（perm 家族）

| 分层                    | 路径前缀                     | 调用方                              | 特点                                 |
| ----------------------- | ---------------------------- | ----------------------------------- | ------------------------------------ |
| 管理配置 API            | `/api/access/*`                | 管理端、access-service 管理面、接入系统后台 | 资源、角色、授权、条件、域配置、日志 |
| 运行时鉴权/权限查询 API | `/api/access/auth/*`           | Gateway、业务服务 SDK               | 高 QPS、可缓存、强稳定               |
| 服务接入 API            | `/api/access/service-config/*` | 接入服务、SDK Starter、管理端       | 服务注册、接口同步、接口资源树       |

> 不再定义 `/internal/perm/*` 主契约；本项目未上线，后续实现直接以 `/api/access/*` 为准。


> **管理面家族路径分层（T-ACCESS-042 单命名空间更新）**：管理面家族控制器统一挂载 `/api/access/<资源>`（如 `/api/access/user`、`/api/access/org`、`/api/access/auth`、`/api/access/oauth2/client`；原裸资源根映射退役），外部路径=服务路径——Gateway 以 `Path=/api/access/**` 单路由转发、无 StripPrefix；本册各能力章内两家族端点分节列出（管理面/权限面为文档组织口径，非 URL 前缀差异）。

## 2. 通用协议
### 2.1 Header

| Header           | 必填          | 说明                                                              |
| ---------------- | ------------- | ----------------------------------------------------------------- |
| `Authorization`  | 管理 API 必填 | `Bearer <token>`                                                  |
| `X-Tenant-Id`    | 必填          | 当前租户 ID，由 Gateway 或可信服务注入；请求体不再保留 `tenantId` |
| `X-Request-Id`   | 可选          | 未传时由 Gateway 生成（access-service 直连由拦截器兜底生成 UUID）；全链路单 ID，兼作链路追踪 ID——响应 `traceId`/日志 MDC `traceId` 为同一值（T-PERM-021 F1.d 定案） |
| `X-Service-Code` | 内部/SDK 必填 | 调用方服务编码，用于内部来源校验                                  |
| `X-Api-Version`  | 可选          | 契约版本，默认 `2026-04-26`                                       |

可信边界：

- 外部客户端传入的 `X-Tenant-Id/X-User-Id/X-Service-Code` 必须由 Gateway 清洗，不允许原样透传。
- Gateway 从 Token claim 解析租户和主体后重新注入标准 Header。
- 服务间调用由调用方凭证绑定可信服务身份（`SyncAuthVerifier` 从上下文比对）；access-service 需校验服务身份与 Header 一致性。

### 2.2 统一响应

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "requestId": "uuid",
  "traceId": "trace-id"
}
```

失败时 `data=null`，错误码使用 permission-center 段 `20001-29999`。

### 2.3 分页结构

请求：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "sort": "createdAt,desc"
}
```

响应：

```json
{
  "items": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 20,
  "hasNext": false
}
```

### 2.4 标准标识字段

对象引用要解决的问题是：同一个权限对象在不同系统里可能有多套标识。例如用户在业务系统里是 `externalId`，资源在权限中心里有内部 `id`，操作又常用 `code` 表示。若每个字段都包装成 `{id, code, externalId, type}`，灵活性很高，但请求体复杂，且容易出现多字段指向不同对象的问题。

本契约取消通用 `Ref` 对象，改为按对象类型使用固定扁平字段。每个接口只使用一套明确定位方式。

| 对象      | 标准入参字段                                                    | 说明                                                                     |
| --------- | --------------------------------------------------------------- | ------------------------------------------------------------------------ |
| 租户      | `X-Tenant-Id`                                                   | 只放 Header，不放 Body                                                   |
| 用户/主体 | `subjectTypeCode` + `subjectExternalId`                         | `subjectTypeCode` 对应 `type_definition(type_key='user_type').type_code` |
| 角色      | `domainCode` + `roleTypeCode` + `roleExternalId`                | 对外接口使用外部角色标识；可被外部调用分配/授权的角色必须有 `externalId` |
| 业务域    | `domainCode`                                                    | 可空；为空表示全局域                                                     |

> **跨字段校验（assign/revoke，2026-06-15 M2 落地）**：`domainCode` 可空仅对**功能角色**（BASIC_ROLE / GROUP_ROLE / PERSONAL）成立——为空表示全局域。对 `roleTypeCode ∈ {ORG, POSITION}` 的组织/岗位角色，`domainCode` **必填**（标识所属业务域）。服务端 `UserManageAppServiceImpl.assignRole/assignRolesBatch/revokeRolesBatch` 通过 Feature flag `permission.assign.strict-domain-check`（默认 `true`）强制：`strict-domain-check=true` 时违反上述约束抛 `BizException`；`false` 时仅兜底为空串放行（上线灰度用）。

| 资源      | `domainCode` + `resourceTypeCode` + `resourceCode` + `codeType` | `codeType` 默认 `default`                                                |
| 操作      | `operationCode`                                                 | 在 `resourceTypeCode` 范围内解析（操作定义按类型隔离，全局操作已退役）；入参大写（@Pattern，见下方 T-PERM-066 注记） |
| 条件      | `conditionCode`                                                 | 可空                                                                     |
| 明细记录  | `id` 或 `ids`                                                   | 仅用于更新/删除权限关系、日志详情等权限中心已返回的记录                  |

原则：

- 运行时鉴权接口不要求调用方传内部 `id`。
- 管理端列表、创建、详情响应可以返回内部 `id`，用于后续 `update/remove`。
- 同一接口不同时接受 `id/code/externalId` 多套定位方式，避免歧义。
- 所有请求体禁止出现 `tenantId`；服务端统一从 `X-Tenant-Id` 和安全上下文读取租户。
- 对外 API 使用稳定字符串 `typeCode`；数据库实体继续保存 `type_value INT`，由服务端通过缓存解析，避免外部系统依赖内部数字枚举。
- **实例授权业务编码语义（T-ACCESS-016 定稿）**：`resource_entity(USER).code = subjectId`（主体 ID 字符串化）、`resource_entity(ROLE).code = roleId`（`abstract_role.id` 字符串化）；业务对象门禁与跨服务 SDK 统一使用业务 `resourceCode`，不得使用 `resource_entity.id`——权限面内部及直接管理资源实体的后台接口（资源树、API 映射、资源依赖、权限树等）允许继续使用，现有 `ApiMappingResp`/`ResourceDependencyResp` 等契约不因此重构（原 `ResourcePermissionTreeResp` 已随 T-PERM-059 删除，2026-09-10）。引擎内部 Java API 契约见 engine/implementation.md §3.1（`hasPermissionByCode`/`getDeniedResourceCodes` 对外，`getDeniedEntityIds`/`hasPermissionByEntityId` 仅引擎内部或已完成解析的调用方）。
- `type_value` 在同一 `tenant_id + type_key` 内全局唯一，不随 `domainCode/biz_domain_id` 重复；`type_code` 仍可按业务域和全局分别定义。
- `domainCode` 用于**管理查询的域过滤与同步命名空间**：管理查询经 `DomainClassifyService.matchesTypeCode/getClassifiedTypeCodes` 按 **ALL / GLOBAL_PLUS / DOMAIN_ONLY** 三种模式过滤（`domain_config` 表 `CLASSIFY` 配置按 `resourceTypeCode` 关联）；批量上下文（逐条目循环/列表过滤）必须走 `preloadCoveredTypeCodes` 一次预载模式覆盖集、循环内 `Set.contains` 复用（T-PERM-055，与 `matchesTypeCode` 同语义的批量形态，三模式判定结果不变）；**查询管线不做按域的对象过滤，仅按域分类过滤资源类型**（`queryResources`/`queryScopes` 经 `DomainClassifyService(GLOBAL_PLUS)` 分类过滤，非按 domainCode 定位对象）；角色/资源实体不内嵌域列，`domainCode` 不参与角色/资源定位（仅域存在性校验，见 §11.2/§10.4）。**不存在"传域查域+全局，不传只查全局"的旧命名空间语义**——如有接口确需旧语义，须逐项列出并标注迁移（ P2-2 修正）。
- Gateway 必须清洗外部伪造的 `X-Tenant-Id/X-User-Id/X-Service-Code`，再基于 Token 或可信服务身份重新注入；permission-center 不信任客户端原始 Header。

### 2.5 入参约束集中注记（批量上限与 Req 单源 T-PERM-065 / 码值大写 T-PERM-066）

**operationCode/resourceTypeCode 族入参大写（T-PERM-066，2026-09-14 定案 raw 严格化）**：operationCode 族字段（`operationCode`/`operationCodes`/`parentOperationCodes`/`sourceOperationCodes`/`requiredOperationCodes`/`scopeOperationCodes` 等）与同链路的 `resourceTypeCode` 族字段（含 `resourceTypeCodes`/`scopeResourceTypeCodes` 复数形）统一 `@Pattern("^[A-Z][A-Z0-9_]*$")`——小写/混合大小写/含首尾空格一律 **400（90001，Bean Validation 层）**，不进解析链路。覆盖：`auth/check`、`auth/batch-check`（含 item 与请求级 parent 链）、`auth/query-scopes`（parent/scope 四列表）、`auth/query-resources`、`user-effective-permission-codes`、`role-resource-permission/apply-grant-plan`（`recordKey` 主键与 `children` 嵌套级联）、`integration/permission-manifest/full-sync`（source/target 类型码及源/目标操作码列表）、`operation-permission` create/update/detail/remove（`code`）、`type-definition` create（`typeCode`，可选——null 与空串放行走留空生成分支（`^$` 显式放行，grok 外评 P2 处置），非空值须大写）。**定义侧同款锁死**：小写操作码/类型码定义自本批不可再建（未部署零存量迁移；服务端自动生成的 typeCode 恒大写）。授权域旧 `toUpperCase/trim` 归一已随本批退役——两域统一 raw 裸拼，小写不再存在「授权面（apply-grant-plan 经归一）静默成功、查询面 20005 fail-closed 拒绝」的双语义。守卫：`OperationCodeCaseValidationTest` 行为锁（旧实现无注解必红）+ `PermCommonReqContractTest` 注解签名快照。边界（不锁面）：`roleTypeCode`/`subjectTypeCode`/`domainCode`/`typeKey` 不在本锁范围（两域一致 raw，值域口径另议）；**仅含 `resourceTypeCode` 的纯查询/资源实体 CRUD 面不加锁**（`operation-permission/list`、`resource-entity` list/tree/detail/create/update/key 族、`resource-dependency/check` 等——两域一致 raw，非授权/查询不对称面）。`/api/access/org/tree` 的 `operationCode`（VIEW/CREATE 白名单 10008）与 check-interface 固定 `ACCESS` 维持既有严格口径。SDK 消费方（perm-common 单源）随注解同批生效——客户端启用 bean validation 时构造期即拒绝。

**批量上限 1000（SDK 公开契约，2026-09-12 登记）**：进入权限引擎批量入口的列表型字段一律 `@Size(max = 1000)`（超限 400，Bean Validation 层拒绝，不进引擎）。覆盖：`user-role/assign` 的 `items`、`user-role/revoke` 的 `items`、`resource-entity/batch-create` 的 `items`（**仅封规模**——嵌套项不级联校验，畸形项走服务端宽容收集逐条跳过、部分成功语义为有意设计，2026-09-12 用户拍板维持）、perm 家族 `IdsReq.ids` 批量端点族（abstract-role/remove、abstract-user/remove、biz-domain/remove、conflict-rule/remove、domain-config/remove、resource-api-mapping/remove、service-config/remove、type-definition/remove）、`auth/batch-check` 的 `items`、`AuthCheckReq/BatchAuthCheckReq` 的 `parentOperationCodes`。SDK 消费方（perm-common）与服务端同限值（`BatchEntrySizeValidationTest` 行为锁 + `PermCommonReqContractTest` 注解签名快照双守卫）。管理面家族批删端点（`/api/access/dict/type/delete` 等）不进引擎闭包，**不设此限**（2026-09-12 用户拍板不换绑）。`integration/permission-manifest/full-sync` 的 `dependencies`/`requires` 清单同**不设此限**（2026-09-21 拍板豁免登记：发布通道天然大批量、非引擎批量入口——清单完整性是协议语义〔含完整空清单〕，规模由源侧快照完整性与部署侧报文限制约束，4,370 目标项清单为已验证设计预期；与资源 full-sync `items` 同族）。

**Req 单源（DTO 双轨收敛）**：原 access-service `permission/dto/req` 与 perm-common `dto/req` 双轨维护的 17 对同名 Req 已收敛——服务端 Controller/AppService 与 SDK 消费方统一消费 perm-common 类型（`AuthCheckReq`、`BatchAuthCheckReq`、`CheckInterfaceReq`、`IdReq`、`IdsReq`、`OperationListReq`、`ResourceBatchCreateReq`、`ResourceCreateReq`、`ResourceKeyReq`、`ResourceKeysReq`、`ResourceUpdateReq`、`RoleCreateReq`、`RoleDetailReq`、`RoleListReq`、`UserAssignRoleReq`、`UserRoleBatchRevokeReq`、`UserRoleListReq`），access-service 侧副本删除。SDK 侧 T-PERM-028 同批漏改三处同步修复为服务端业务键定稿形态：`ResourceUpdateReq`（update，原 id 定位）、`PermissionFeignClient.getRole`（原 `IdReq`→`RoleDetailReq`）、`deleteResources`（原 `IdsReq`→`ResourceKeysReq`）——原形态发至服务端均必 400；`UserAssignRoleReq.items`/`UserRoleBatchRevokeReq.items`/`IdsReq.ids`/`ResourceBatchCreateReq.items` 补齐 `@Size(max=1000)`（原 SDK 契约缺失，外部服务按 SDK 构造超限请求会被服务端拒——现在 SDK 侧同限值，构造期即感知）。`UserAssignRoleReq`/`UserRoleBatchRevokeReq`/`ResourceBatchCreateReq` 的 `items` 另带元素级 `@NotNull`（null 元素 400，不级联嵌套字段——不触碰 batch-create 宽容收集拍板语义）。管理面家族同名 `IdsReq`/`UserRoleListReq`（后者与 perm-common 同名异义：`userId` 内部 ID 查询）保持独立，不参与单源。check 族响应 DTO 仍双副本（`CheckFamilyWireShapeTest` 同形守卫），Resp 面收敛未盘点、另定。

### 2.6 管理面家族通用约束

引用 `project-rules.md`:

- **§1.1 统一响应壳**: 所有接口返回 `R<T> { code, message, data, requestId, traceId }`. `code=200` 为成功, 失败时 `data=null`. `requestId/traceId` 由 `RResponseAdvice` 在序列化前回填（`X-Request-Id` 由网关生成/透传；`traceId` 与 `requestId` 同值——单 ID 收敛，T-PERM-021 F1.d 删除原 `X-Trace-Id` 头偏好路径）, 业务侧不写入.
- **§2.3 分页**: 入参 `{ pageNum, pageSize, sort? }`, `pageNum>=1`, `1<=pageSize<=100`, `sort` 形如 `"createdAt,desc"`. 出参分页对象统一为 `PageResp<T> { items: T[], total, pageNum, pageSize, hasNext }`（承载类 perm-common `perm.common.dto.resp.PageResp`，两家族与 SDK 单一来源）. 非分页列表也必须用 `{ items: [...] }` 包装, 禁止顶层数组.
- **§2.1 HTTP 方法**: 所有接口 `POST + application/json + @RequestBody DTO`. 禁止 `@GetMapping/@PutMapping/@DeleteMapping/@PatchMapping`, 禁止 `@RequestParam` (除文件上传/下载), 禁止路径参数. 业务 ID 必须放 JSON Body.
- **§2.2 路径**: T-ACCESS-042 起 URL 单命名空间——全部端点（管理面与权限面）统一挂载 `/api/access/<资源>/<动作>`, 外部路径=服务路径（Gateway `Path=/api/access/**` 单路由, 无 StripPrefix）. 本契约文档中所有路径即请求方实际使用的路径.
- **请求体禁止 `tenantId`**: 服务端统一从 `X-Tenant-Id` Header 与 SecurityContext 读取. 前端经网关后无需感知.
- **§1.2 业务错误码**: access-service 管理面（裸路径族）业务错误使用 `10001-19999` 段; 系统公共错误 (参数校验/系统异常) 使用 `90001-99999` 段, 由 `common` 模块统一定义.
- **§3.2 异常**: 业务拒绝 (资源不存在/状态冲突/默认树边界违规等) 抛 `BizException`; 安全拒绝 (操作者身份缺失/权限不足/越权) 抛 `SecurityException`; 技术故障 (DB/RPC/序列化) 抛 `SystemException`. **禁止**用 `SecurityException` 表达"资源不存在"或"参数非法".

## 3. 动词规范
### 3.1 动词表
| Action   | 语义                                             |
| -------- | ------------------------------------------------ |
| `list`   | 分页或非分页列表，响应必须包装 `{items,...}`     |
| `tree`   | 树结构查询                                       |
| `detail` | 单条详情                                         |
| `create` | 创建                                             |
| `update` | 局部更新                                         |
| `save`   | 幂等创建或更新                                   |
| `remove` | 批量软删除，请求体统一 `{ "ids": [...] }`        |
| `assign` | 分配用户角色关系                                 |
| `revoke` | 回收用户角色或权限关系                           |
| `grant`  | 权限授权，偏业务语义                             |
| `sync`   | 外部系统幂等同步；全量语义必须在具体资源契约中限定范围 |
| `check`  | 判定                                             |
| `query`  | 运行时权限事实查询，返回可访问资源或范围权限集合 |
| `detect` | 检测但不落库                                     |

### 3.2 `remove` 与 `{ "ids": [...] }`

- 动词 `remove` 的请求体统一为 `{ "ids": [ ... ] }`，元素为**权限中心表主键**（`BIGINT`），用于删除已在 `list` / `detail` 响应中暴露过的配置行。
- **适用范围**：`domain-config/remove`、`service-config/remove`、`resource-api-mapping/remove` 以及其它已声明支持批量的 `remove` 接口。
- **与业务键的关系**：`ids` 中的主键**不**用于「首次定位外部主体/角色」；主体与角色在其它接口中仍使用 `subjectTypeCode + subjectExternalId`、`domainCode + roleTypeCode + roleExternalId` 等稳定键。调用方应先通过列表或详情拿到待删行的 `id`，再调用 `remove`。

## 4. 管理面门禁规范

admin 门禁通过 `AdminPermissionValidator` 本地调用 `PermQueryEngine` 完成，不再 Feign 自调用。接口形态:

```java
void checkTypeLevel(String resourceTypeCode, String operationCode);
void checkInstanceLevel(String resourceTypeCode, String resourceCode, String operationCode);
void checkBatchInstanceLevel(String resourceTypeCode, List<String> resourceCodes, String operationCode);
boolean hasTypeLevel(String resourceTypeCode, String operationCode);
```

> **终态口径（T-ACCESS-016 定稿，2026-08-23；T-ADMIN-021 增补 `hasTypeLevel`）**：前三个抛出式门禁是 `SecurityException` 的出口（引擎纯查询，见 engine/implementation.md §3.1）——`checkInstanceLevel` 内部走 `engine.hasPermissionByCode`、`checkBatchInstanceLevel` 内部走 `engine.getDeniedResourceCodes`（实施 T-PERM-042）；`hasTypeLevel` 为**非抛出判定**（T-ADMIN-021，供「部分裁剪」类调用方）：仅引擎成功响应且明确拒绝返回 false，技术故障/主体缺失抛 `SystemException(99999)`（非 SecurityException 的第二出口，fail-closed 不静默降级），见 §8.1 岗位裁剪段。业务对象门禁统一**业务编码语义**：`resourceCode` 为业务 ID 字符串（`/api/access/user/**` 的 userId、`/api/access/org/**` 的 orgId；统一主体 ID 后 `resource_entity(USER).code = sys_user.id = abstract_user.id`，数值与语义一致），不得使用 `resource_entity.id`。下表及各章节资源类型串已按收敛映射切换为 `USER/ORG/ROLE`（access-service-architecture §13 资源类型注册表；常量类已合一为 `ResourceTypeCode`，原 `AdminResourceType` 随 T-ACCESS-018 删除）。

资源类型常量（`ResourceTypeCode`，T-ACCESS-018 合一后单一常量源，原 AdminResourceType 已删除）:

| 常量 | 值 | 说明 |
|------|------|------|
| `ResourceTypeCode.USER` | `USER` | 被管理的用户实例 (resource_entity, code=sys_user.id；原 ADMIN_USER 并入，T-ACCESS-018) |
| `ResourceTypeCode.ORG` | `ORG` | 被管理的组织/岗位实例 (resource_entity, code=sys_org.id；原 ADMIN_ORG 并入) |
| `ResourceTypeCode.ROLE` | `ROLE` | (本契约只读: 仅 /role/list 用；原 ADMIN_ROLE 并入) |

操作码常量（`OperationCode`，`engine.constant` 单一常量源，T-ACCESS-034 合一——原 `AdminOperationCode` 与 `OperationCodeConstants` 两册已删除；按资源类型分节，共享码按值命名）：`CREATE / VIEW / UPDATE / DELETE / MANAGE / SYNC / ENABLE`（跨类型共享；ENABLE 为 USER 启停与 ADMIN_JOB 任务启停共用值面）+ `RESET_PASSWORD`（USER）+ `ASSIGN / REVOKE`（ROLE）+ `ACCESS`（API）+ `MANAGE_API_MAPPING / SYNC_INTERFACE`（SERVICE）+ ORG 六码（`MANAGE_MEMBER / CREATE_POSITION / VIEW_POSITION / UPDATE_POSITION / DELETE_POSITION / ASSIGN_POSITION_USER`）+ 平台三码（`PUBLISH / TRIGGER / TOGGLE`）。统一常量面 = 注册表镜像：DDL 种子在册即收录（含零代码引用的 ROLE:ASSIGN/REVOKE，取代 T-PERM-019 D3 只镜像代码引用面口径）。历史注：DISABLE 已并入 ENABLE（v1.4）、ADMIN_ROLE:GRANT/REVOKE 已删除（T-ACCESS-018）。

本契约接口的门禁映射表:

| 接口 | 资源类型 | 资源粒度 | 操作码 | 备注 |
|------|----------|----------|--------|------|
| `/api/access/user/page` | `USER` | 类型级 | `VIEW` | 默认树身份目录范围内列表 |
| `/api/access/user/member-candidates` | `ORG` | 实例级 (目标 orgId) | `UPDATE` | 仅校验"能管理目标组织的成员"; 候选用户范围由默认树可见性二次裁剪 |
| `/api/access/user/create` | `USER` | 类型级 | `CREATE` | 若入参带 orgId, 同时需 `ORG:UPDATE@orgId` |
| `/api/access/user/update` | `USER` | 实例级 (userId) | `UPDATE` | 档案字段自我豁免保留（T-PERM-067 Q-002 收窄）；`status` 变更不豁免（自身 status=0 硬拒 `CANNOT_DISABLE_SELF`、status=1 须 `USER:ENABLE`） |
| `/api/access/user/delete` | `USER` | 实例级批量 (ids) | `DELETE` | 默认树身份目录边界 |
| `/api/access/user/enable` | `USER` | 实例级批量 (ids) | `ENABLE` | 启停共用一码（toggle），按入参 `status` 设置实体字段 |
| `/api/access/user/reset-password` | `USER` | 实例级 (userId) | `RESET_PASSWORD` | 默认树身份目录边界 |
| `/api/access/user/detail` | `USER` | 实例级 (userId) | `VIEW` | 类型级 VIEW 门禁 + 默认树可见范围裁剪（P1-2：复用 `validateUsersInDefaultTreeScope`，与 `/api/access/user/page` 同等约束，防止知道 ID 即可读列表不可见用户；无组织关系用户拒绝）|
| `/api/access/org/tree` | `ORG` | 类型级 | `VIEW` 或 `CREATE` | 入参 `operationCode` 决定语义: `VIEW`=可视范围; `CREATE`=新增用户时可选挂载点 (限默认树) |
| `/api/access/org/page` | `ORG` | 类型级 | `VIEW` | |
| `/api/access/org/users` | `ORG` | 实例级 (orgId) | `VIEW` | |
| `/api/access/org/create` | `ORG` | 实例级 (parentOrgId, 顶级时类型级) | `CREATE` | |
| `/api/access/org/update` | `ORG` | 实例级 (orgId) | `UPDATE` | 改 `parentOrgId` 等价于"移动", 同时需新父级 `UPDATE` |
| `/api/access/org/delete` | `ORG` | 实例级 (orgId) | `DELETE` | |
| `/api/access/user-org/list` | `USER` | 实例级 (userId) | `VIEW` | 读用户成员关系视图 |
| `/api/access/user-org/assign` | `ORG` | 实例级批量 (orgIds) | `UPDATE` | 关系级追加; 默认树关系受身份目录边界二次校验 |
| `/api/access/user-org/remove` | `ORG` | 实例级 (orgId) | `UPDATE` | 非默认树仅删关系并回收对应 user_role; 默认树移除按身份目录高危处理 |
| `/api/access/user-org/set-primary` | `ORG` | 实例级 (orgId) | `UPDATE` | 首期仅允许默认组织树主归属 |
| `/api/access/user-role/view` | `USER` | 实例级 (userId) | `VIEW` | admin 代理直查（T-ACCESS-042 由 list 改名 view——与权限轨持有角色 `/list` 统一命名空间后区分双轨）; 不再额外要求 `ROLE:MANAGE` |
| `/api/access/role/list` | `ROLE` | 类型级 | `VIEW` | 仅功能角色 |

> **默认树身份目录边界二次校验**: `/api/access/user/create`、`/api/access/user/delete`、`/api/access/user/enable`、`/api/access/user/reset-password`、`/api/access/user-org/set-primary` 在通过 `AdminPermissionValidator` 后, AppService 内部还要二次确认目标用户的默认树关系存在 (通过 `sys_user_org` 推导), 且操作者在默认树该子树下具备可见性. 不满足时抛 `BizException(ErrorCode.NOT_IN_DEFAULT_TREE_SCOPE)`. 这一层不能用 `SecurityException` 表达.

## 5. 管理面写入口的本地投影

> **目标架构（T-ACCESS-005，2026-08-15，迁移自原 admin 册头部）**：管理域各写接口的投影动作已改为同事务本地权限投影。HTTP 路径、DTO 与错误码继续有效。access 内部不再写 `sys_sync_task`、不再 Feign 自调用。

用户、组织、菜单及成员关系写入由 user/org/menu 能力写编排层（原 access.application，T-ACCESS-033 解散入能力包）编排，在同一 PostgreSQL 事务内维护管理事实、本地权限投影和 `permission_change_log`。不再写 `sys_sync_task`，不再 Feign 自调用。见 [`access-service-architecture.md`](access-service-architecture.md) §4。

投影定位（稳定外部键，独立主键；`owner_service_code=access-service`；不写 `sync_metadata`）：

| 管理事实 | 投影 | 外部键 |
|----------|------|--------|
| `sys_user` | `abstract_user(LOCAL_USER)` + `resource_entity(USER)` | `external_id` / `code` = `sys_user.id.toString()` |
| `sys_org` | `abstract_role(ORG\|POSITION)` + `resource_entity(ORG)` | `external_id` / `code` = `sys_org.id.toString()` |
| `sys_menu`（DIR/MENU/EXTERNAL/IFRAME/HIDDEN 五值全量投影，T-ACCESS-015） | `resource_entity(MENU)` | `code` = `sys_menu.id.toString()` |
| `sys_user_org` | `user_role` | 主体 `LOCAL_USER` + 角色 `ORG/POSITION` |

> **终态口径（T-ACCESS-016 定稿，2026-08-23）**：① 主体 ID 统一后 `sys_user.id = abstract_user.id`（唯一 ID 源，access-service-architecture §12），`external_id`/`code` 的数值与语义不变（同一 Long 的字符串化）；② 类型码随 §13 注册表收敛：`abstract_user(ADMIN_USER)`→`abstract_user(LOCAL_USER)`（user_type 更名）、`resource_entity(ADMIN_USER/ADMIN_ORG/ADMIN_MENU)`→`resource_entity(USER/ORG/MENU)`；③ 保留业务键终态（§8.7 终态注记）：subject 侧 `ADMIN_USER`→`LOCAL_USER`（无兼容别名）；resource 侧取消类型级保留，本地投影行改按所有权保护（外部 sync UPSERT/DISABLE/DELETE 任一 mutation 分支前置 `owner=access-service` 即 20045、新建撞 code 由唯一索引兜底；USER/MENU 保持公共类型可被外部同步自身资源）——**该 resource 侧口径已被 T-PERM-052 类型级所有权取代（2026-09-05；T-ADMIN-025 增 ADMIN_FILE）：事实链路类型种子声明 SYNC+access-service，外部同步一律拒绝、管理面 20055 只读，行级 owner 防线删除，见下方「保护」段**。类型串替换已随 T-ACCESS-018 落地（本契约全量切换）；USER/ROLE 投影全写路径补齐已随 T-ACCESS-019 落地（2026-08-23，permission 域 abstract-user/abstract-role 管理入口同事务投影，外部 sync 入口遗留登记）。

保护：权限管理入口与外部 `/api/access/**/sync|full-sync` 拒绝改写本地投影——保留业务键 `LOCAL_USER` / `ORG|POSITION` / `SYS_USER_ORG`（subject 侧原 `ADMIN_USER` 已更名）与内部 `sourceService` 拒绝为 `BizException(20045)`；resource 侧为类型级所有权（T-PERM-052，2026-09-05；T-ADMIN-025 增 ADMIN_FILE、T-PERM-051 增 TYPE_DEFINITION：事实链路七类型 USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION/CONDITION （T-PERM-048 增 CONDITION——管理页条件投影，仅 source=MANAGED）种子声明 `managedMode=SYNC + syncSourceService=access-service`，外部同步入口拒绝、管理面资源 CRUD 20055 只读，原保留清单与行级 owner 防线已收编删除；T-PERM-069 增 API 同款种子声明——唯一事实入口=service-config/sync 接口声明通道+bootstrap 固定图，管理面资源 CRUD 20055；SERVICE 维持 MANAGED，详见 §13/§12/§19.1/§19.8）。

本契约接口的投影动作：

| 接口 | 主事务 | 投影 |
|------|--------|------|
| `/api/access/user/create` | INSERT `sys_user` (+ 可选 `sys_user_org`) | `upsertAdminUser`；若带 orgId：`bindUserOrg` |
| `/api/access/user/update` | UPDATE `sys_user` | `upsertAdminUser` |
| `/api/access/user/delete` | 软删 `sys_user`，级联清理 `sys_user_org` | 每条关系 `unbindUserOrg`；`deleteAdminUser` |
| `/api/access/user/enable` | UPDATE `sys_user.status` | 启用 `upsertAdminUser`；停用 `disableAdminUser` |
| `/api/access/user/reset-password` | UPDATE `sys_user.password` | 无（密码不进入权限投影） |
| `/api/access/org/create` | INSERT `sys_org` | `upsertAdminOrg` |
| `/api/access/org/update` | UPDATE `sys_org` | `upsertAdminOrg` |
| `/api/access/org/delete` | 软删 `sys_org`，级联清理 `sys_user_org` | 每条关系 `unbindUserOrg`；`deleteAdminOrg` |
| `/api/access/user-org/assign` | INSERT/UPDATE `sys_user_org` | 每个新增关系 `bindUserOrg` |
| `/api/access/user-org/remove` | DELETE `sys_user_org` | `unbindUserOrg` |
| `/api/access/user-org/set-primary` | UPDATE `sys_user_org.is_primary` | 无（`is_primary` 不映射 `user_role` 拓扑） |

> 功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）的分配/回收走 `/api/access/user-role/*`（perm 家族），管理面 `/api/access/user-role/*` 仅保留 `/api/access/user-role/view` 读聚合（T-ACCESS-042 由 list 改名，与权限轨持有角色 `/list` 区分双轨）。组织/岗位角色只能由组织与成员关系写入投影产生，`createRoleForOrg` 与针对保留角色类型的菜单授权一律拒绝。

## 6. auth 能力（登录会话与 OAuth2）

> OAuth2 端点此前仅存在于归档设计（`docs/archive/2026-04-28/admin-service-design.full.md` §1.5-1.6），本章按当前实现登记为活跃契约（T-ACCESS-013 补记）。授权链路语义（JWT 载荷、audience、开放路径门禁）以 `access-service-architecture.md` §6 为权威。

### 6.1 授权端点（/auth/oauth2/*）

所有端点 POST + JSON Body；统一响应壳 `R<T>`。

#### 6.1.1 `POST /api/access/auth/oauth2/authorize`（需平台会话）

平台用户为客户端发起授权，生成一次性授权码（Redis `oauth2:code:<uuid>`，TTL 300s，Lua GET+DEL 原子消费）。

请求（`AuthorizeReq`）：`clientId`* / `responseType`*（固定 `code`）/ `redirectUri`* / `state` / `scope`（空格分隔，⊆ 客户端注册 scopes，否则 `OAUTH2_SCOPE_INVALID`；**客户端注册 scopes 为空时拒绝非空 scope 请求**——空注册不解释为无限制；空 scope 请求放行，签发的无 scope 令牌因业务路径 requiredScopes 强制非空而仅可访问 userinfo 豁免端点）/ `codeChallenge` / `codeChallengeMethod`（S256|plain）。

响应（`AuthorizeResp`）：`code` / `state`。

#### 6.1.2 `POST /api/access/auth/oauth2/token`（匿名）

授权码兑换访问令牌。仅支持 `grant_type=authorization_code`。

请求（`TokenReq`）：`grantType`* / `clientId`* / `clientSecret`* / `code`* / `redirectUri`* / `codeVerifier` / `refreshToken`。

响应（`TokenResp`）：`accessToken`（JWT）/ `tokenType`（`Bearer`）/ `expiresIn`（=客户端 `accessTokenTtl`）/ `refreshToken` / `scope`。

**JWT 载荷**（`SaJwtUtil` HS256，loginType=`oauth2`，密钥 `sa-token.jwt-secret-key`）：`loginId`（userId）/ `client_id` / `tenant_id`（字符串，无租户 `"0"`）/ `scope`（空格分隔委托范围）/ `jti` / `aud`（**T-ACCESS-013**：客户端注册 `audiences` 非空时写入 List，未配置不写）/ `eff` / `device=oauth2`。

#### 6.1.3 `POST /api/access/auth/oauth2/refresh`（匿名）

刷新令牌轮换（Lua 原子取删旧 refresh token，一次性使用；签发新 access + refresh token，scope/clientId 透传）。

请求（`RefreshTokenReq`）：`clientId`* / `refreshToken`*。响应同 `TokenResp`。

#### 6.1.4 `POST /api/access/auth/oauth2/revoke`（匿名）

撤销访问令牌：先验签（非法令牌不写 Redis，防黑名单键 DoS），`jti` 写入 `oauth2:blacklist:<jti>`，TTL=令牌剩余有效期。黑名单对全部开放路径生效。

请求（`RevokeTokenReq`）：`accessToken`*。响应：`R<Void>`。

#### 6.1.5 `POST /api/access/auth/oauth2/userinfo`（需 OAuth2 JWT，默认开放路径）

资源服务器端点（默认开放路径，audience 豁免）。请求体空；`Authorization: Bearer <OAuth2 JWT>`。

响应（`OAuth2UserInfoResp`）：`sub`（userId 字符串）/ `username` / `name` / `phone` / `email`。

### 6.2 资源服务器开放路径门禁（T-ACCESS-013）

OAuth2 委托令牌访问业务 API 由显式配置的路径白名单 + 三重门禁控制（**默认拒绝**）：

- 配置：`access.oauth2.resource-paths`（application.yml；默认仅 `/api/access/auth/oauth2/userinfo`；Ant 通配允许；显式配置为全量替换；启动防护禁止覆盖 `/api/access/auth/**` 会话端点与 `/api/access/**` 内部凭证空间（静态前缀保守判定，`/api/**/sync` 等绕过形态均拦截），且**业务开放路径必须声明 requiredScopes 与 audience**——缺失启动失败，防配置遗漏静默放行）。
- 每条规则：`path` + `requiredScopes`（令牌 scope 子集校验，独立映射模型——不接入 PermQueryEngine）+ `audience`（业务路径强制；userinfo 豁免）+ `clientIds`（可选客户端限定）。
- 恒定校验（无需配置）：验签 + 必填 claim（loginId/jti/client_id）+ 撤销黑名单 + 客户端启用动态校验（禁用立即失效 → 401）。门禁不满足 → 403。
- 委托调用绑定 `USER + delegatedClientId` 上下文（审计可区分第三方委托）。
- Gateway 侧配套 `gateway.oauth2.passthrough-paths`（外部路径口径，默认空；**仅对 Bearer 三段式 JWT 启用透传**，平台 uuid 会话仍走 Gateway 正常鉴权）透传 Authorization；双侧路径口径差异与部署约束见架构文档 §6。

### 6.3 OAuth2 客户端管理（/oauth2/client/*）

门禁：`ResourceTypeCode.ADMIN_OAUTH2_CLIENT`（类型级 CREATE / 实例级 UPDATE/DELETE）；操作日志 `@OperationLog`（sys_oauth2_client）。

| 端点 | 请求 | 响应 | 备注 |
|------|------|------|------|
| `POST /api/access/oauth2/client/create` | `Oauth2ClientCreateReq` | `R<Long>`（新客户端 id） | clientId 唯一（重复 `CLIENT_ID_EXISTS`）；secret BCrypt 存储 |
| `POST /api/access/oauth2/client/update` | `Oauth2ClientUpdateReq` | `R<Void>` | 仅更新非 null 字段；secret 更新重新 BCrypt |
| `POST /api/access/oauth2/client/delete` | `IdsReq` | `R<Void>` | 批量软删除 |
| `POST /api/access/oauth2/client/detail` | `IdReq` | `R<Oauth2ClientResp>` | 不返回 clientSecret |
| `POST /api/access/oauth2/client/page` | `Oauth2ClientPageReq` | `R<PageResp<Oauth2ClientResp>>` | 按名称/状态过滤 |

`Oauth2ClientCreateReq`：`clientId`* / `clientSecret`* / `clientName`* / `grantTypes`（逗号分隔）/ `redirectUris`（逗号分隔）/ `scopes`（逗号分隔）/ **`audiences`**（逗号分隔资源服务器标识，T-ACCESS-013；配置后签发写入 aud claim）/ `accessTokenTtl`（60-86400）/ `refreshTokenTtl`（60-604800）/ `status`。

`Oauth2ClientResp`：上表字段 + `id` / `tenantId` / `createdAt` / `updatedAt`（不含 clientSecret）。

种子数据（access-service.sql）：admin-web / example-web / internal-service，`scopes='all'`、`audiences='access-service'`。

### 6.4 登录端点族（未成册登记）

> `/api/access/auth/captcha`、`/api/access/auth/login`、`/api/access/auth/login/sms`、`/api/access/auth/logout`、`/api/access/auth/userinfo`、`/api/access/auth/user-menu` 六个登录会话端点未在原两册成册（原 admin 册范围限定「组织与用户域」）；契约以代码与 `HttpApiPathSnapshotTest` 快照为准，前端侧行为见 `frontend/login.md`。成册补写待后续批次（T-ACCESS-040 登记，不在本任务新增契约内容）。会话入口白名单族（Gateway 匿名白名单）2026-09-19 起另含 `/api/access/user/reset-password`（T-GW-009 定案⑤，注记见 §7.7「Gateway 放行」）。

## 7. user 能力（用户管理）
### 7.1 `POST /api/access/user/page` 🔧

**目的**: 默认组织树身份目录视角下的用户分页查询. 用于「组织与用户」页左侧选中组织或岗位后的用户列表.

**请求 DTO**: `UserPageReq` (`record`)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pageNum` | `Integer` | 否 | 默认 1, 最小 1 |
| `pageSize` | `Integer` | 否 | 默认 20, 范围 1-100 |
| `sort` | `String` | 否 | 形如 `"createdAt,desc"` |
| `username` | `String` | 否 | 模糊匹配 (默认树范围内) |
| `name` | `String` | 否 | 模糊匹配 |
| `phone` | `String` | 否 | 模糊匹配 |
| `email` | `String` | 否 | 模糊匹配 |
| `status` | `Integer` | 否 | 1=启用, 0=停用（与 DDL `sys_user.status` 一致） |
| `orgId` | `Long` | 否 | 选中组织/岗位 ID; 不传时返回操作者在默认树内可见的全部用户; 传时仅返回直接挂在该组织 (含子树, 视实现决策) 的成员 |

**响应 DTO**: `PageResp<UserPageItemResp>`, `items[]` 字段:

`UserPageItemResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | sys_user.id |
| `username` | `String` | 登录账号 |
| `name` | `String` | 显示名 |
| `phone` | `String` | |
| `email` | `String` | |
| `status` | `Integer` | 1=启用, 0=停用（与 DDL `sys_user.status` 一致） |
| `orgs` | `List<OrgBrief>` | 用户所属组织简表 |
| `createdAt` | `LocalDateTime` | |

`UserPageItemResp.OrgBrief`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `orgId` | `Long` | sys_org.id |
| `orgName` | `String` | |
| `orgType` | `String` | 字典值 (Phase 2 决策: 后端固定为 String 字典编码; 前端 `OrgBrief.orgType` 可选) |
| `isPrimary` | `boolean` | 是否主组织 |

**门禁**: `USER:VIEW` 类型级 + 默认树可见范围裁剪 (操作者只能看到默认树中其有 `ORG:VIEW` 的子树成员).

**同步动作**: 无 (只读)

**错误码段**: 10001-10099 (用户查询)

**当前差距 (来自 api-gap-analysis，已归档 `docs/archive/2026-06-21/`)**: 需要明确语义为"默认组织树身份目录查询"; 区别于添加组织成员时的候选用户查询 (后者改用 §7.2 `/api/access/user/member-candidates`). 该差距已由 admin-service 实现收口。

**验收要点**:
- 操作者无 `USER:VIEW` 时返回空列表 + `code=200` (不抛 SecurityException).
- 操作者在默认树中无任一可见组织时返回空列表.
- `orgId` 落在非默认树时返回 `BizException(ErrorCode.ORG_NOT_IN_DEFAULT_TREE)`; 该接口语义只服务身份目录视图.

### 7.2 `POST /api/access/user/member-candidates` 🔧

**目的**: 给非默认组织/岗位添加成员时, 查询候选用户. 候选范围 = 默认组织树中操作者可见 ∩ 排除目标组织已有成员.

**请求 DTO**: `MemberCandidatesReq` (`record`, 新增)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `targetOrgId` | `Long` | 是 | 目标组织/岗位 ID (用户即将被加入的组织, 通常属于非默认树) |
| `pageNum` | `Integer` | 否 | 默认 1 |
| `pageSize` | `Integer` | 否 | 默认 20, 范围 1-100 |
| `keyword` | `String` | 否 | 关键字 (按 username/name/phone/email 模糊匹配) |

**响应 DTO**: `PageResp<MemberCandidateItemResp>`

`MemberCandidateItemResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | sys_user.id |
| `username` | `String` | |
| `name` | `String` | |
| `avatar` | `String` | 可选 |
| `primaryOrgName` | `String` | 默认树主归属组织名, 便于识别 |
| `alreadyAssigned` | `Boolean` | 固定 false (服务端已过滤; 字段保留用于一致性) |

**门禁**: `ORG:UPDATE@targetOrgId` (实例级, 校验"能管理目标组织成员").

**同步动作**: 无 (只读)

**错误码段**: 10100-10119

**当前差距**: 无——后端独立接口已实现（`POST /api/access/user/member-candidates`，语义为默认树身份目录候选查询）；前端 api 函数已新增并接入岗位挂人选择器（T-FE-015 收口 2026-08-31，`getMemberCandidates`——`alreadyAssigned` 后端恒 false，占用过滤由调用方本地完成）.

**验收要点**:
- 候选集**严格**来自默认树中操作者具备 `USER:VIEW` (或 `ORG:VIEW`) 的范围; 不暴露全租户用户.
- 必须排除目标组织已通过 `sys_user_org` 直接关联的用户.
- `targetOrgId` 不存在或已删除时抛 `BizException`.

### 7.3 `POST /api/access/user/create` 🔧

**目的**: 在默认组织树身份目录中新建用户; 可选一步完成组织挂载. 系统生成随机初始密码, 仅本次响应返回.

**请求 DTO**: `UserCreateReq` (现有)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | `String` | 是 | 登录账号, 租户内唯一 |
| `name` | `String` | 是 | 显示名 |
| `phone` | `String` | 否 | |
| `email` | `String` | 否 | |
| `status` | `Integer` | 否 | 默认 1 (启用)；1=启用, 0=停用（T-ACCESS-021 修正：原文「0=正常,1=禁用」与 DDL/启停接口语义矛盾）；仅接纳 0/1，其它值抛 `BizException(INVALID_PARAM)`（T-ADMIN-022 收口：create 为 status 写入口，与 update/enable 同口径） |
| `orgId` | `Long` | 否 | 创建时一步完成挂载; **必须**属于默认组织树, 否则抛 `BizException(ORG_NOT_IN_DEFAULT_TREE)` |
| `primaryOrg` | `Boolean` | 否 | 仅当 `orgId` 非空时生效, 默认 true |

**响应 DTO**: `UserCreateResp`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 新用户 ID |
| `initialPassword` | `String` | 系统生成的随机初始密码明文, **仅本次返回** |

**门禁**: `USER:CREATE` 类型级 (+ 若带 `orgId` 还需 `ORG:UPDATE@orgId`).

**投影动作**:
1. 主事务: INSERT `sys_user` (+ 可选 INSERT `sys_user_org`)
2. `LocalProjectionDomainService.upsertAdminUser`
3. 若带 `orgId`: `bindUserOrg`
4. 同事务写 `permission_change_log`；缓存失效仅在提交后发生

**错误码段**: 10120-10149

**当前差距**: 默认树校验与初始密码返回已由实现覆盖；内部 ID 不再回填前端。

**验收要点**:
- `username` 在租户内重复时抛 `BizException(USERNAME_DUPLICATED)`.
- `orgId` 非默认树时抛 `BizException(ORG_NOT_IN_DEFAULT_TREE)`.
- `initialPassword` 必须非空且强度满足策略 (8-32 位, 可配置).
- 管理事实、投影与 `permission_change_log` 必须同事务；任一失败整体回滚.

### 7.4 `POST /api/access/user/update` ✅

**目的**: 更新用户基本资料. 状态字段可写但建议改用 `/api/access/user/enable` 启停一体.

**请求 DTO**: `UserUpdateReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `Long` | 是 | sys_user.id |
| `name` | `String` | 否 | |
| `phone` | `String` | 否 | |
| `email` | `String` | 否 | |
| `status` | `Integer` | 否 | 1=启用, 0=停用；仅接纳 0/1，其它值抛 `BizException(INVALID_PARAM)`（T-ADMIN-022 语义收口，与 DDL `sys_user.status` 单一口径） |

**响应**: `R<Void>`

**门禁**: `USER:UPDATE@id` (实例级，仅非自身——档案字段自我豁免保留，T-PERM-067 Q-002 收窄)；`status` 变更无论自身与否均查 `USER:ENABLE@id`（启停分权——T-ACCESS-034 外评处置补齐，对齐 perm 轨 §7.8 字段分档与 `/api/access/user/enable` 门禁，防仅持 UPDATE 旁路启停；T-PERM-067 起自身 `status=0` 对齐 `/api/access/user/enable` 硬拒 `CANNOT_DISABLE_SELF`——此前自身全免旁路该硬禁，自身 `status=1` 须持 `USER:ENABLE`).

**投影动作**: 同事务 `upsertAdminUser`（名称/状态变化一并投影）。

**错误码段**: 10150-10169

**验收要点**: 投影与管理事实同事务提交；回滚不发布缓存失效。

### 7.5 `POST /api/access/user/delete` 🔧

**目的**: 批量软删除用户. 高危身份目录操作, 仅默认组织树管理员可执行.

**请求 DTO**: `IdsReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ids` | `List<Long>` | 是 | sys_user.id 列表 |

**响应**: `R<Void>`

**门禁**: `USER:DELETE` 实例级批量 (`checkBatchInstanceLevel(USER, ids, DELETE)`) + 默认树边界二次校验 (操作者必须在每个目标用户的默认树主归属子树下具备 `ORG:UPDATE`).

**投影动作** (每个 id):
1. 主事务: 软删 `sys_user`, 级联软删 `sys_user_org`
2. 每条 user-org 关系 → `unbindUserOrg`
3. `deleteAdminUser`

**错误码段**: 10170-10189

**当前差距**: 默认树边界校验由 `UserWriteAppService` 执行。

**验收要点**:
- 批量中任一用户不在操作者默认树可管范围抛 `BizException(NOT_IN_DEFAULT_TREE_SCOPE)`, 整批回滚.
- 软删后 `username` 在租户内可被新用户复用 (业务策略, 与现状一致).
- 不允许删除当前操作者本人 → `BizException(CANNOT_DELETE_SELF)`.

### 7.6 `POST /api/access/user/enable` 🔧

**目的**: 批量启用/停用用户 (启停一体, 管理员手工启停). `status=1` 启用, `status=0` 停用. 高危生命周期操作.

> 设计决策: 启停**不**拆为 `/api/access/user/enable` + `/api/access/user/disable` 双接口, 沿用现有 `UserUpdateStatusReq(ids, status)` 形态; 在 AppService 内部按 `status` 动态选择门禁操作码 (`ENABLE` vs `DISABLE`).
>
> **status 语义单一口径（T-ADMIN-022）**: `sys_user.status` 仅 0(停用)/1(启用)；登录失败临时锁定不落库（Redis 失败计数键剩余 TTL 即锁定时长，键过期自动恢复），历史 `status=2` 已删除。

**请求 DTO**: `UserUpdateStatusReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ids` | `List<Long>` | 是 | 用户 ID 列表 |
| `status` | `Integer` | 是 | 1=启用, 0=停用 |

**响应**: `R<Void>`

**门禁**:
- `status=1`: `USER:ENABLE` 实例级批量
- `status=0`: `USER:ENABLE（toggle）` 实例级批量
- 加默认树边界二次校验 (与 §7.5 同).

**投影动作** (每个 id):
1. 主事务: UPDATE `sys_user.status`
2. 启用 → `upsertAdminUser`；停用 → `disableAdminUser`

**错误码段**: 10190-10209

**当前差距**: 门禁码按 status 派发与默认树二次校验由 `UserWriteAppService` 执行。

**验收要点**:
- 不允许停用操作者本人 → `BizException(CANNOT_DISABLE_SELF)`.
- 非默认树成员管理员调用此接口必须被门禁拦截 (因其无 `USER:ENABLE`（启停共用一码，v1.4 DISABLE 已并入）).

### 7.7 `POST /api/access/user/reset-password` 🔧

**目的**: 管理员重置用户密码. 不传 `newPassword` 时由系统生成随机密码, 明文仅本次返回. 高危生命周期操作.

**请求 DTO**: `ResetPasswordReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `newPassword` | `String` | 否 | 长度 8-32; 不传则系统生成 |

**响应 DTO**: `ResetPasswordResp`

| 字段 | 类型 | 说明 |
|------|------|------|
| `newPassword` | `String` | 生效的密码明文, **仅本次返回** |

**门禁**: `USER:RESET_PASSWORD@userId` 实例级 + 默认树边界二次校验（均仅非自身；自身路径为自助改密通道免门禁——T-PERM-067 Q-002 定位保留，旧密码验证另立任务）.

**Gateway 放行（T-GW-009，2026-09-19 定案⑤）**: 本端点在 Gateway 匿名白名单会话入口族内（精确清单见 `services/gateway.md` 匿名白名单行）——Gateway 不做接口级鉴权。背景：Gateway 快照条目仅由 API 类型 ACCESS 位派生、实例授权只种子给管理用功能角色（绑首管理员），forceResetPwd 阻断人群（普通用户）不经白名单放行经 Gateway 必 403。服务层门禁不变：Sa-Token 登录校验（`StpUtil.getLoginIdAsLong`，未登录拒绝）+ 自身路径豁免 / 非自身 `USER:RESET_PASSWORD` 实例级（T-PERM-067 语义零改动）。前端强制改密闭环（T-FE-046）的前置.

**同步动作**: 无 (密码不进入 permission-center). 重置成功后 `sys_user.force_reset_pwd` 置位口径（T-PERM-067 外评 claude P3 处置，2026-09-14）：非自身重置（密码经管理员之手）置 `true`——DDL 语义「管理员重置后须改密」（T-ADMIN-022，前端强制改密阻断据此闭环——T-FE-046 起登录后由路由守卫阻断至改密页，登录页 warning 口径已退役）；自身自助改密置 `false`——用户亲手完成改密（密码经响应当即已知），与归档设计「修改个人密码成功后置 false」口径回归（此前一律置 true 且全仓无清除通道，自助改密后「初始密码」提示永久失真）.

**错误码段**: 10210-10229

**当前差距**: 无——`newPassword` 可选 + `ResetPasswordResp` 返回 + 非自我修改时默认树边界二次校验（`validateUsersInDefaultTreeScope`）均已实现.

**验收要点**:
- 操作者本人重置自己密码**复用本接口**（自助改密通道，T-PERM-067 Q-002 定位：自身路径免门禁；旧句「走另外的『修改密码』接口」所指接口从未实现，已证伪删除）.
- 自定义 `newPassword` 不满足策略时抛 `BizException(PASSWORD_TOO_WEAK)`.

### 7.8 perm 家族主体端点（/api/access/abstract-user/*）

| 接口                                              | 说明                     |
| ------------------------------------------------- | ------------------------ |
| `POST /api/access/abstract-user/list`               | 查询主体列表             |
| `POST /api/access/abstract-user/detail`             | 查询主体详情             |
| `POST /api/access/abstract-user/create`             | 创建主体，适合管理端     |
| `POST /api/access/abstract-user/sync`               | 幂等同步外部主体         |
| `POST /api/access/abstract-user/full-sync`          | 按 scope 全量校准外部主体 |
| `POST /api/access/abstract-user/update`             | 更新主体                 |
| `POST /api/access/abstract-user/remove`             | 删除主体，支持批量       |

> 主体 `sync`/`full-sync` 为外部事实源同步通道端点，契约详见 §19.4（主体、角色、用户角色同步接口）；`detail`/`list`/`create`/`update`/`remove` 为管理端点（DTO 名 `AbstractUser*`，T-ACCESS-033 裁决 1 改名消歧，JSON 契约零变化）。

**管理端点门禁（T-ACCESS-034 USER 轨细粒度化，唯一权限语义变更）**：

| 接口 | 门禁 | 口径 |
|---|---|---|
| `abstract-user/create` | `USER:CREATE` 类型级 | 保留主体类型守卫（LOCAL_USER 等保留键拒绝） |
| `abstract-user/update` | **字段分档实例级**（`resource_entity(USER).code = subjectId`）：name/extra 变更查 `USER:UPDATE`、`enabled != null` 查 `USER:ENABLE` | 组合字段须同时通过全部涉及的操作码（防 UPDATE 绕过启停分权）；自身档案字段豁免保留、`enabled` 变更不豁免（自身也须持 `USER:ENABLE`——T-PERM-067 Q-002 收窄；生产面操作者==目标必被 LOCAL_USER 投影守卫先拒，该分支为死分支语义统一）；空 patch（仅 userId 无任何业务字段）经 Bean Validation 拒绝 **90001**（HTTP 400，`@AssertTrue` 至少一个业务字段——堵无门禁落点的无条件写副作用） |
| `abstract-user/remove` | `USER:DELETE` 实例级批量（getDeniedResourceCodes，任一拒绝整批拒绝） | 全量目标走门禁（含操作者自身——T-PERM-067 删除 nonSelfUserIds 静默剔除特例，对齐 admin 轨 CANNOT_DELETE_SELF 硬禁口径）；LOCAL_USER 目标拒绝（投影守卫，生产面批量含自身必被先拒） |

> USER:MANAGE 操作位已退役（DDL 种子删除、位 16 空闲不复用）。bootstrap 固定图 USER 段本无 MANAGE 位——旧实现下空库首管理员经 update/remove 恒拒（死锁），换绑后**由拒转放行是预期行为变化**（FirstAdminUserTrackPgIT 正向锁）。粗细统一仅 USER 一处；ROLE/RESOURCE 等类型的 MANAGE 惯例维持。

## 8. org 能力（组织与用户-组织关系）
### 8.1 `POST /api/access/org/tree` ✅

**目的**: 返回组织层级树（按树配置子树裁剪，顶层为配置根单根）. 入参 `operationCode` 控制语义: `VIEW`=可视范围, `CREATE`=作为新增用户挂载点 (限默认树). `includePositions=true` 返回组织+岗位一体树（T-ADMIN-021，2026-09-03 全字段落地）.

**请求 DTO**: `OrgQuery`（T-ADMIN-021 起全字段落地，含原债务① `operationCode`/`treeConfigId`）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `operationCode` | `String` | 否 | `VIEW` 或 `CREATE`; 不传时按 `VIEW` 处理；非法值 → `INVALID_PARAM`(10008)。门禁按 `OrgOperationCodeMapper.resolve(orgType, operationCode)` 组合分发（orgType=1+CREATE → `ORG:CREATE`；orgType=2+VIEW → `ORG:VIEW_POSITION`）。**`includePositions=true` 时仅支持 `VIEW`（P2-1：CREATE + 混合树 → 10008——岗位裁剪固定检查 VIEW_POSITION，CREATE 混合树会形成 CREATE+VIEW_POSITION 混合门禁；CREATE 场景保持 `orgType=1` 单类型树，不需要岗位节点）；`CREATE` 与 `treeConfigId` 同传 → 10008（CREATE 限默认树，T-ADMIN-021 用户决策）** |
| `treeConfigId` | `Long` | 否 | 组织树配置 ID；不传 = **默认树（is_default）子树**（契约字面，T-ADMIN-021 用户决策——多树租户下其他树必须显式传 id 才可见；无默认配置 → `ORG_TREE_CONFIG_NOT_FOUND`(11001) fail-closed，对齐 resolver 禁止静默 fallback 口径）；显式传 → 该配置 `root_org_id` 为根的子树（不存在 → 11001；根组织已删 → `ORG_TREE_ROOT_NOT_RESOLVED`(11002)）；`CREATE` 时禁止传 |
| `orgName` | `String` | 否 | 模糊匹配（树剪枝语义：保留自身或后代命中的节点及其祖先链，与前端树过滤一致；T-ADMIN-021 修正原死参数——实现先前忽略本字段） |
| `orgType` | `Integer` | 条件必填 | 1=组织, 2=岗位（**值域白名单：非 1/2 → `INVALID_PARAM`(10008)**）；**`includePositions != true` 时必填**（缺失 → `ORG_TYPE_REQUIRED`，保留现有业务码；后端 `treeOrgs` 按 orgType 分发 `VIEW/VIEW_POSITION` 门禁，放开空值会在单一门禁下返回全部类型，P1-2）；**`includePositions=true` 时忽略本字段（一体树语义，岗位裁剪由 hasTypeLevel 独立门控）**。注：orgType 过滤为**节点级内存过滤**（根不豁免）——orgType=2 无 parentOrgId 时**恒为空树**（根组织被类型过滤剔除），岗位树形态统一走 `includePositions` 混合树（已知边界，非缺陷） |
| `includePositions` | `Boolean` | 否 | **T-ADMIN-021 已实现（2026-08-01 评审 P1-6）**；默认 `false` 行为与现状完全一致；`true` 时返回组织+岗位一体树：岗位（orgType=2）作为所属组织（orgType=1）的**子节点**挂入同一树（岗位自身无下级），**忽略 `orgType` 单类型过滤** |
| `status` | `Integer` | 否 | 节点级内存过滤（根不豁免）：根不匹配 status 时顶层即空，配合 `parentOrgId` 透视可取匹配节点 |
| `parentOrgId` | `Long` | 否 | 用于在配置子树内再取该节点子树（不在子树范围内 → 空结果，过滤语义不报错；T-ADMIN-021 修正原死参数）; 一般不与 `treeConfigId` 同时使用 |

**响应（P1-3 定稿，T-ADMIN-021 已落地）**: `R<ItemsResp<OrgResp>>`，`data.items[]`（复用 perm-common 泛型 `ItemsResp{ items: List<OrgResp> }`，与 `/api/access/role/list`、`/api/access/user-role/list` 同款——T-ADMIN-021 用户决策，原定稿命名的 `OrgItemsResp` 类不再新建，线格式不变）；**唯一形状，不再返回裸数组**（历史直返 List 已废弃）

`OrgResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | |
| `orgType` | `Integer` | 1=组织, 2=岗位 (字典值) |
| `orgName` | `String` | |
| `parentOrgId` | `Long` | null=顶级 |
| `code` | `String` | |
| `status` | `Integer` | 0/1 |
| `sort` | `Integer` | |
| `createdAt` | `LocalDateTime` | |
| `updatedAt` | `LocalDateTime` | |
| `children` | `List<OrgResp>` | 树形 |

（phone/email 行删除：sys_org 无联系方式字段，OrgResp 早已不含——T-ADMIN-021 核对修正）

**门禁**: 类型级，`OrgOperationCodeMapper.resolve(orgType, operationCode)` 组合分发（单一事实源）；`includePositions=true` 时组织轨固定 `ORG:VIEW`，岗位轨独立门控（见下）.

**岗位节点裁剪（T-ADMIN-021 已实现）**: `includePositions=true` 时，岗位节点（orgType=2）按调用者岗位权限**后端裁剪**——调用者仅具备 `ORG:VIEW`（无 `ORG:VIEW_POSITION`）时响应不包含任何岗位节点；裁剪判定用非抛出入口 `AdminPermissionValidator.hasTypeLevel(ORG, VIEW_POSITION)`（**仅引擎成功响应且明确拒绝返回 false；本地权限引擎技术故障或操作者主体缺失抛 `SystemException`(99999) 向上，统一响应业务码标识故障，不得静默降级为裁剪后的树**，P2-1；不复用 SecurityException——全局映射 403 与故障语义矛盾）。前端隐藏不作为安全边界。

**同步动作**: 无.

**当前差距**: 无——T-ADMIN-021（2026-09-03）销账：① `OrgQuery` 补齐 `operationCode`/`treeConfigId`（含 CREATE 冲突拒绝与非法值 fail-closed）+ orgType 值域白名单；② 响应 `{items:[...]}` 包装落地（`ItemsResp<OrgResp>`）；③ `orgName`/`parentOrgId` 原死参数修正为真实过滤；④ OrgResp 表格 phone/email 陈旧行删除；⑤ 根存在性守卫（11002）基于未过滤全量——仅根真缺失/软删时触发，orgType/status 为节点级内存过滤（设计定案：过滤即过滤，根不豁免）。回归锁定以 `OrgTreeIncludePositionsPgIT`（否定性裁剪/故障 99999/兼容/多树/参数拒绝/过滤语义守卫/裁剪旁路锁）与 `AdminPermissionValidatorImplHasTypeLevelTest` 断言为准，另有 `HttpApiPathSnapshotTest` 快照同步。

### 8.2 `POST /api/access/org/page` ✅

**目的**: 平铺分页查询组织/岗位列表. 岗位 Tab 通过 `orgType=2` + `orgId=选中组织` 实现子树筛选.

**请求 DTO**: `OrgPageReq` (现有)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pageNum` | `Integer` | 否 | 默认 1 |
| `pageSize` | `Integer` | 否 | 默认 20 |
| `sort` | `String` | 否 | |
| `orgName` | `String` | 否 | 模糊匹配 |
| `orgType` | `Integer` | 是 | 1=组织, 2=岗位; 缺失报 `ORG_TYPE_REQUIRED` |
| `status` | `Integer` | 否 | |
| `parentOrgId` | `Long` | 否 | 直接父级 |
| `orgId` | `Long` | 否 | 子树根; 传入时返回该组织及其全部子孙 (岗位 Tab 用) |

**响应**: `PageResp<OrgResp>` (children 字段为空数组, 平铺语义)

**门禁**: 按 `orgType` 分发——`ORG:VIEW`(组织) / `ORG:VIEW_POSITION`(岗位).

### 8.3 `POST /api/access/org/users` ✅

**目的**: 查询组织/岗位下通过 `sys_user_org` 关联的用户列表. 岗位卡片展开用.

**请求 DTO**: `IdReq` (公共 record)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `Long` | 是 | 组织/岗位 ID |

> 前端 mock 实际传 `{ orgId }`, 与后端 `IdReq.id` 不一致. **决策**: 以 `IdReq.id` 为准, 前端在 Phase 2 调整 mock 字段名为 `id`. 列入 Phase 2 前端调整项.

**响应**: `R<ItemsResp<OrgUserItemResp>>`（`{ items: [...] }` 包装已收编，T-ADMIN-027）

`OrgUserItemResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | `Long` | |
| `username` | `String` | |
| `name` | `String` | |
| `avatar` | `String` | 可选 |
| `isPrimary` | `Boolean` | 是否主组织 |

**门禁**: `ORG:VIEW@id`.

### 8.4 `POST /api/access/org/create` 🔧

**目的**: 创建组织或岗位 (`orgType=1` 组织, `orgType=2` 岗位).

**请求 DTO**: `OrgCreateReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `orgType` | `Integer` | 是 | 1=组织, 2=岗位 |
| `orgName` | `String` | 是 | |
| `parentOrgId` | `Long` | 否 | null=顶级; 顶级仅允许在默认树根 (业务策略) |
| `code` | `String` | 否 | 租户内唯一 (有值时) |
| `status` | `Integer` | 否 | 默认 1 (1=启用, 0=停用, 对齐 DDL) |
| `sort` | `Integer` | 否 | |

> 评审 P2（2026-08-15）：组织 `phone`/`email` 字段已从契约/请求 DTO/响应模型删除——`sys_org` 实体与表不含联系方式字段（声明必须生效）。

**响应**: `R<Long>` (新组织 ID)

**门禁**:
- 顶级 (`parentOrgId=null`): `ORG:CREATE` 类型级
- 子级: `ORG:UPDATE@parentOrgId` 实例级 (在父级下添加子节点等价于"修改父级结构")

**投影动作**:
1. 主事务: INSERT `sys_org`
2. `upsertAdminOrg`（`roleTypeCode=ORG` 或 `POSITION` 视 `orgType`）

**错误码段**: 10300-10329

**当前差距**: 组织写入已由 `OrgWriteAppService` 同事务投影。

**验收要点**:
- `code` 重复抛 `BizException(ORG_CODE_DUPLICATED)`.
- `parentOrgId` 不存在或已删除抛 `BizException(PARENT_ORG_NOT_FOUND)`.
- 跨树 `parentOrgId` 抛 `BizException(CROSS_TREE_PARENT_FORBIDDEN)`.

### 8.5 `POST /api/access/org/update` 🔧

**目的**: 更新组织属性. 改 `parentOrgId` 等价于"移动子树".

**请求 DTO**: `OrgUpdateReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `Long` | 是 | |
| `orgName` | `String` | 否 | |
| `parentOrgId` | `Long` | 否 | 改动等价于移动 |
| `code` | `String` | 否 | |
| `status` | `Integer` | 否 | |
| `sort` | `Integer` | 否 | |

**响应**: `R<Void>`

**门禁**: `ORG:UPDATE@id` 实例级. 若 `parentOrgId` 变化, 还需 `ORG:UPDATE@新parentOrgId`.

**投影动作**:
1. 主事务: UPDATE `sys_org`
2. `upsertAdminOrg`
3. 若 `parentOrgId` 变化：由组织投影更新父子关系；成员 `user_role` 的 relationKey 随本地投影维护，不再拆外部 envelope。

**错误码段**: 10330-10359

**验收要点**:
- 不允许把组织移到自己的子树下 (`BizException(CIRCULAR_PARENT)`).
- 不允许跨树移动 (`BizException(CROSS_TREE_MOVE_FORBIDDEN)`).

### 8.6 `POST /api/access/org/delete` 🔧

**目的**: 软删除组织 (单条). 子组织非空时拒绝.

**请求 DTO**: `IdReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `Long` | 是 | sys_org.id |

**响应**: `R<Void>`

**门禁**: `ORG:DELETE@id` 实例级.

**投影动作**:
1. 主事务: 软删 `sys_org` (delete_flag=1), 级联软删 `sys_user_org`
2. 每条被清理的 user-org → `unbindUserOrg`
3. `deleteAdminOrg`

**错误码段**: 10360-10389

**验收要点**:
- 存在未删除子组织 → `BizException(ORG_HAS_CHILDREN)`.
- 默认树根节点不允许删除 → `BizException(CANNOT_DELETE_DEFAULT_ROOT)`.

### 8.7 `POST /api/access/user-org/list` ✅

**目的**: 查询用户所属组织列表 (含主组织标记).

**请求 DTO**: `IdReq` (`{ id: userId }`)

**响应**: `R<ItemsResp<UserPageItemResp.OrgBrief>>`（`{ items: [...] }` 包装已收编，T-ADMIN-027）

| 字段 | 类型 | 说明 |
|------|------|------|
| `orgId` | `Long` | |
| `orgName` | `String` | |
| `orgType` | `String` | 字典值 |
| `isPrimary` | `boolean` | |

**门禁**: `USER:VIEW@userId`.

### 8.8 `POST /api/access/user-org/assign` 🔧

**目的**: 给用户**追加**组织关系 (关系级精确变更, 禁止跨树 wipe). 可选同时设置主组织.

**请求 DTO**: `UserOrgAssignReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `orgIds` | `List<Long>` | 是 | 待追加的组织 ID 列表 (允许已存在的关系幂等忽略) |
| `primaryOrgId` | `Long` | 否 | 主组织; 必须落在 `orgIds` 内或用户已有关系内; 默认树边界校验 |

**响应**: `R<Void>`

**门禁**: 对 `orgIds` 中每个组织实例分别 `ORG:UPDATE@orgId` (`checkBatchInstanceLevel`).

**写入语义** (关键决策):
- **追加**已存在的关系幂等忽略, 不删除用户在其他组织树的关系.
- **禁止**"先 wipe 再 batch insert"模式; 必须按 `(userId, orgId)` 对增量比较.
- 如果 `primaryOrgId` 非空: 仅在 `primaryOrgId` 所属组织树内将其设为主, **不**清除用户在其他树的 `is_primary` 标记 (首期仅默认树主归属生效, 见 §8.10).

**投影动作**:
- 每个**新增**关系 → `bindUserOrg`
- 已存在关系 → 不重复投影

**错误码段**: 10400-10429

**当前差距**: 关系级追加由 `UserOrgWriteAppService` 执行；不再拆外部 sync envelope。

**验收要点**:
- `userId` 在默认树有归属时方可追加非默认树关系; 未在默认树时抛 `BizException(USER_NOT_IN_DEFAULT_TREE)`.
- 候选 `orgIds` 中含已删除组织 → `BizException(ORG_NOT_FOUND)`.
- 任一 `orgId` 操作者无 `ORG:UPDATE` → `SecurityException`, 整批回滚.

### 8.9 `POST /api/access/user-org/remove` 🔧

**目的**: 移除单条 user-org 关系. 默认树关系按身份目录高危处理.

**请求 DTO**: `UserOrgRemoveReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `orgId` | `Long` | 是 | |

**响应**: `R<Void>`

**门禁**:
- 非默认树关系: `ORG:UPDATE@orgId`
- 默认树关系: `USER:UPDATE@userId` (按身份目录边界, 等同"移动用户默认归属")

**投影动作**:
- DELETE `sys_user_org` (单条) → `unbindUserOrg`

**错误码段**: 10430-10459

**当前差距**: 无——默认树/非默认树门禁分支已实现（非默认树关系 `ORG:UPDATE@orgId`、默认树关系 `USER:UPDATE@userId`，默认树关系归 0 时拒绝）.

**验收要点**:
- 移除后用户默认树关系归 0 时抛 `BizException(USER_LOSE_DEFAULT_TREE_HOME)` — 默认树主归属不可被普通组织成员管理员意外清除.
- 被移除关系若是该树内 `is_primary`, 必须同时把同树另一关系提升为主 (业务策略: 选 sort 最小的; 若该树仅此一条则按上一条规则拒绝).

### 8.10 `POST /api/access/user-org/set-primary` 🔧

**目的**: 设置用户主组织. 首期**仅允许**默认组织树主归属.

**请求 DTO**: `UserOrgSetPrimaryReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `orgId` | `Long` | 是 | 目标主组织 ID, **必须**位于默认组织树 |

**响应**: `R<Void>`

**门禁**: `ORG:UPDATE@orgId` + 默认树边界校验.

**写入语义**:
- 仅在默认树内将 `(userId, orgId)` 的 `is_primary=true`, 同时把该用户在默认树的其他关系置 `is_primary=false`.
- **禁止**清除其他组织树的 `is_primary` 标记 (即便是历史脏数据, 也由专门 `is_primary` 修复迁移负责, 不通过本接口).

**投影动作**: 无（`is_primary` 是 admin 内自管字段，不映射 `user_role` 拓扑）。

**错误码段**: 10460-10479

**当前差距**: 无——已实现仅默认组织树内主归属（默认树配置解析 + 树内切换主标记，不影响其他组织树的 `is_primary`）.

**验收要点**:
- `orgId` 不在默认树 → `BizException(PRIMARY_MUST_BE_IN_DEFAULT_TREE)`.
- 用户与 `orgId` 不存在关联 → `BizException(USER_ORG_RELATION_NOT_FOUND)`.


> `/api/access/org-tree-config/*` 六端点（page/create/update/detail/delete/set-default）为组织树配置管理面，原两册未展开登记契约（登记项见附录 A 注记），以代码与 `HttpApiPathSnapshotTest` 快照为准。

## 9. menu 能力（菜单管理）
### 9.1 总述与 bootstrap 菜单种子
> 菜单表仅承载 UI 路由元数据与关联资源 link，不承载权限语义（`sys_menu` 权威 DDL 见 `schema/access-service.sql`；设计语义见 `permission-center-v3.5-design.md` §2.1/§4.1）。按钮级权限由 OperationPermission（L1）承担，不再挂菜单。前端登录菜单聚合走 `/api/access/auth/user-menu`（v3.5 §5 单 RPC 契约），与本节管理接口分离。前端菜单管理页尚未开发（views/system 无 menu 页面），本节契约为先行定稿，无现存消费方破坏面。
>
> **bootstrap 菜单种子（T-FE-015，2026-08-31 设计定案）**：空库 bootstrap 固定图幂等种子 14 行菜单——welcome 首页（纯展示）+「系统管理」DIR + 12 业务页 MENU（按页面主资源类型挂接、类型级 `resource_code=null` 走 scopeAll 派生；权限条件页读取全租户开放故挂纯展示；原权限排查页已随 T-PERM-059 删除，2026-09-10）。path 与前端静态路由一一对齐（侧栏点击按 path 跳静态路由）；检测断言以 path 集合为期望键（结构键严格，displayName/icon/sortOrder 容忍菜单管理页改动）。同批种子默认组织树（根组织稳定业务键 `root` + 默认树配置 + 首管理员挂根组织）——`/api/access/user/page` 与 `/api/access/user/member-candidates` 为默认树身份目录视图，无默认树配置则恒空。固定图定义升级后既有库须重建（runbook 常见问题表）。

### 9.2 `POST /api/access/menu/create` 🔧

**请求 DTO**: `MenuCreateReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `menuType` | `String` | 是 | 枚举 `DIR/MENU/EXTERNAL/IFRAME/HIDDEN`（v3.5 五值，BUTTON 已移除） |
| `displayName` | `String` | 是 | 最长 128 |
| `parentId` | `Long` | 否 | null/0 表示顶级 |
| `path` | `String` | 否 | 最长 256；EXTERNAL/IFRAME 为外链 URL；租户内唯一（10205） |
| `icon` | `String` | 否 | 最长 64 |
| `sortOrder` | `Integer` | 否 | 默认 0，升序 |
| `status` | `Integer` | 否 | `1=ENABLED`（默认）/`0=DISABLED`，对齐 DDL |
| `resourceType` | `String` | 否 | 与 `resourceCode` 成对（同填或同空，校验失败 90001） |
| `resourceCode` | `String` | 否 | 关联资源实例；租户内同一资源仅可挂一个菜单（10206） |
| `sourceService` | `String` | 否 | 业务服务标识，缺省 `access-service`（管理端创建） |

**响应**: `R<Long>`（新菜单 ID）。

**错误**: `10201` 菜单不存在（含正数 `parentId` 指向的父菜单不存在）/ `10203` 深度超限 / `10205` 路径已存在 / `10206` 资源关联已被占用 / `10207` 父菜单为自身或后代 / `90001` 成对校验失败。

**门禁**: `MENU:CREATE`（类型级）。

### 9.3 `POST /api/access/menu/update` 🔧

**请求 DTO**: `MenuUpdateReq`：同 `MenuCreateReq` 全部字段均可选（null 跳过保留原值）+ 必填 `id`；`sourceService` 不可更新（创建期追溯标识）。

**响应**: `R<Void>`。

**错误**: `10201` 菜单不存在（含目标 `parentId` 不存在）/ `10203` 深度超限（换父按整棵子树）/ `10205` / `10206`（排除自身的冲突预查 + 唯一索引兜底）/ `10207` 父菜单为自身或后代（防环）。

**门禁**: `MENU:UPDATE`（实例级，按 sys_menu.id）。

### 9.4 `POST /api/access/menu/delete` 🔧

**请求 DTO**: `IdReq`（`id`）。

**响应**: `R<Void>`。软删（`delete_flag=id`）+ 同事务清理 MENU 投影；软删后部分唯一索引释放（path/资源可复用）。

**错误**: `10201` 不存在 / `10204` 存在子菜单。

**门禁**: `MENU:DELETE`（实例级）。

### 9.5 `POST /api/access/menu/detail` 🔧

**请求 DTO**: `IdReq`。**响应**: `R<MenuResp>`。**错误**: `10201`。

### 9.6 `POST /api/access/menu/tree` 与写链路语义 🔧

**请求**: 无参。**响应**: `R<ItemsResp<MenuResp>>`（全量菜单树，管理界面用；用户可见性过滤走 `/api/access/auth/user-menu`；T-ADMIN-027 收编 `{ items }` 包装）。

`MenuResp` 字段：`id / menuType(String) / displayName / parentId / path / icon / sortOrder / status(1=ENABLED) / resourceType / resourceCode / sourceService / createdAt / updatedAt / children`。

**写链路语义**（create/update/delete 同一事务）：

- 可选字符串字段（`path/icon/resourceType/resourceCode/sourceService`）收到空白字符串时**服务端规范化为 null**（空串写库会命中部分唯一索引并被读链路误判为业务菜单；update 时空白等同未提供，跳过保留原值）——设计定案 2026-08-22
- `status` 仅允许 `0/1`（DTO `@Min(0) @Max(1)` 校验，违规 90001）
- MENU 投影对 DIR/MENU/EXTERNAL/IFRAME/HIDDEN **全量维护**（无 BUTTON 短路；实例级门禁依赖投影行授权到具体菜单实例）
- 菜单可见性由 v3.5 §4.1 派生公式在 `/api/access/auth/user-menu` 读链路决定（业务菜单 = `resource_type` 非空走资源访问事实，纯展示 `resource_type` 为空全员可见），不消费投影
- 菜单层级最多 5 级（根=第 1 层）：`calculateDepth` 返回父节点自身深度，新节点深度 = 父深度 + 1；**换父按整棵子树校验**（新根深度 + 子树高度 - 1 ≤ 5，即最深节点不超上限；顶级目标父深度按 0 计，防止把不存在的父层多算一层）
- 父菜单校验：正数 `parentId` 必须为同租户有效菜单（否则 `10201`，无外键兜底防孤儿节点）；换父时目标父不能是被移动菜单自身或其后代（否则 `10207`，防 parent 链成环——环会导致祖先链遍历与递归 CTE 不收敛）
- `MENU_PERM_CODE_EXISTS(10202)` 已退役（perm_code 列移除），由 `MENU_PATH_EXISTS(10205)` / `MENU_RESOURCE_EXISTS(10206)` 承接

## 10. role 能力（角色管理）
### 10.1 管理面用户-角色读聚合（/user-role/view）
> **核心决策（T-ACCESS-006 修订，T-ADMIN-024 删除收口）**: 合并后角色管理由权限面直接提供（`/api/access/user-role/*`），管理面不再维护角色代理。`/api/access/user-role/view` 保留为读接口（T-ACCESS-042 子路径由 list 改名 view——统一命名空间后与权限轨持有角色 `/list`（§10.4，SDK 消费、业务键入参）撞路径，管理轨改名对应 USER:VIEW 门禁语义；经 `role.service` 的 `UserRoleQueryAppService`（T-ACCESS-033 迁移改名） 聚合，POSITION 补所属组织名）；原 `/api/access/user-role/assign`、`/api/access/user-role/revoke` 写代理已删除（无存量调用方，不留兼容层，无映射 404），角色分配/回收走 `/api/access/user-role/assign|revoke`（门禁 `ROLE:MANAGE` 由权限面 enforce）。注：已删端点在 access-service 无任何 Handler 映射（注册表证据见 HttpApiPathSnapshotTest 负向断言与 RetiredRoleApiContractTest）；运行时观察值按入口区分——匿名直连管理面路径族先被 RequestContextInterceptor 拒为 401（不泄露路径存在性），通过拦截后无映射即 404，经 Gateway 的未注册路径先被接口快照按 `unregistered-policy=DENY` 拦为 403（fail-closed）。
> 接口使用业务键 `(roleTypeCode, roleExternalId)` 标识角色。仅服务功能角色 (BASIC_ROLE/GROUP_ROLE/PERSONAL); 排除 ORG/POSITION (后者走 /user-org/*)。

#### 10.1.1 `POST /api/access/user-role/view` 🔧

**目的**: 查询用户已分配的角色列表 (含组织/岗位/功能角色全集, 由 admin 代理拼接).

**请求 DTO**: `UserRoleListReq` (新增)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | sys_user.id |

**响应**: `R<{ items: UserRoleItemResp[] }>`

`UserRoleItemResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `roleTypeCode` | `String` | `ORG` / `POSITION` / `PERSONAL` / `GROUP_ROLE` / `BASIC_ROLE` |
| `roleExternalId` | `String` | 角色业务键（`/api/access/user-role/*` 消费） |
| `roleName` | `String` | 角色名 |
| `roleTypeLabel` | `String` | 显示名 (代理层映射) |
| `targetType` | `String` | (前端展示用, 同 `roleTypeCode`) |
| `relationId` | `Long` | POSITION 角色对应的所属组织 abstract_role.id（permission-center 内部主键）; 其他类型为 null. 内部参考字段, 前端不直接消费 |
| `relationExternalId` | `String` | POSITION 角色对应的所属组织业务键（= sys_org.id 字符串，permission-center 返回）; admin 据此解析组织名（P2-1：替代用 relationId 错查 sys_org）|
| `relationOrgName` | `String` | POSITION 角色对应的所属组织名 (代理层用 relationExternalId 查 sys_org 补) |
| `validFrom` | `LocalDateTime` | |
| `validTo` | `LocalDateTime` | |

**门禁**: `USER:VIEW@userId` (admin-service 层); permission-center 层不再额外要求 (本接口为读).

**代理动作（T-ACCESS-006 修订）**: `UserRoleQueryAppService`（`role.service`）经专用 QueryMapper 读取 `user_role ⨝ abstract_role`（有效期窗口过滤），再批量查 `sys_org` 补 `relationOrgName`。返回业务键 `(roleTypeCode, roleExternalId)` 替代 roleId。

**错误码段**: 10500-10519

**当前差距**: 接口已由本地代理实现。

**验收要点**:
- `userId` 不存在 → `BizException(USER_NOT_FOUND)`.
- 前端通过 `(roleTypeCode, roleExternalId)` 业务键回传 assign/revoke，不再使用 roleId.

### 10.2 角色候选查询（/role/list）✅

**目的**: 查询功能角色候选列表. 默认仅返回 `BASIC_ROLE / GROUP_ROLE / PERSONAL`, 排除 `ORG / POSITION`.

**请求 DTO**: `RoleController.RoleListQueryReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `roleTypeCodes` | `List<String>` | 否 | 不传时默认 `[BASIC_ROLE, GROUP_ROLE, PERSONAL]` |

**响应**: `R<{ items: RoleListItemResp[] }>`

`RoleListItemResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `roleTypeCode` | `String` | 角色类型编码（BASIC_ROLE / GROUP_ROLE / PERSONAL） |
| `roleExternalId` | `String` | 角色业务键（`/api/access/user-role/*` 消费） |
| `roleName` | `String` | 角色名称 |
| `roleTypeLabel` | `String` | 角色类型显示名 |

**门禁**: `ROLE:VIEW`.

**数据来源**: 本地经 `role.service`（`UserRoleQueryAppService` 直读 `user_role ⨝ abstract_role` 跨域只读）按 `roleTypeCodes` 过滤, 无跨服务调用.

### 10.3 perm 家族角色端点（/api/access/abstract-role/*）

| 接口                                              | 说明                     |
| ------------------------------------------------- | ------------------------ |
| `POST /api/access/abstract-role/list`               | 查询角色列表             |
| `POST /api/access/abstract-role/tree`               | 查询角色树               |
| `POST /api/access/abstract-role/detail`             | 查询角色详情             |
| `POST /api/access/abstract-role/create`             | 创建角色                 |
| `POST /api/access/abstract-role/sync`               | 幂等同步外部角色         |
| `POST /api/access/abstract-role/full-sync`          | 按 scope 全量校准外部角色 |
| `POST /api/access/abstract-role/update`             | 更新角色                 |
| `POST /api/access/abstract-role/move`               | 移动角色树节点           |
| `POST /api/access/abstract-role/remove`             | 删除角色，支持批量       |

> T-PERM-043 退役：`/api/access/abstract-role/extra-roles/list|add|remove` 三接口已删除（分组角色额外基本角色专用入口）。写入口 `add` 自实现起写 `user_role.abstract_user_id=null` 违反 NOT NULL 从未成功，`list` 无数据生产者恒空；前端/SDK 无生产调用方，不做兼容层。角色包含关系待未来按 `role_inclusion(group_role_id, included_role_id)` 单事实源另行立项。`create`/`update` 显式拒绝 `GROUP_ROLE`（复用 `ROLE_TYPE_MISMATCH(20022)`，见 §10.5）。

#### `POST /api/access/abstract-role/list`

请求体：

```json
{
  "domainCode": null,
  "roleTypeCode": "BASIC_ROLE",
  "roleTypeCodes": ["BASIC_ROLE", "GROUP_ROLE", "PERSONAL"],
  "keyword": null,
  "pageNum": 1,
  "pageSize": 200,
  "sort": null
}
```

规则：

- `roleTypeCode` 保留单类型过滤兼容；`roleTypeCodes` 用于多类型过滤。
- 两者同时传入时按并集去重后过滤；任一显式类型编码无法解析时返回空分页。
- 不传 `roleTypeCode/roleTypeCodes` 时不按角色类型过滤。

**角色管理契约要点**（T-PERM-022 收口，2026-08-28）：

- `detail`：业务键二元组定位 `{roleTypeCode, roleExternalId}`（tenantId 走上下文，不含 domainCode；原 `IdReq{id}` 内部主键废弃）。依据唯一索引 `uk_abstract_role_external (tenant_id, role_type, external_id)`（`external_id` 非空部分索引——`externalId` 为空的角色不可经本接口定位，管理端新建表单已强制必填）；未命中返回 `data=null`（未知 roleTypeCode 与 list 空分页同口径，不抛错）。
- `move`：目标父须与移动角色**同角色类型**（同类型内嵌套合法，跨类型嵌套拒绝 **20022** `ROLE_TYPE_MISMATCH`）；目标父为移动角色**自身或其子孙**拒绝 **20050** `ROLE_PARENT_INVALID`（新增错误码，perm 段顺延——parent 链成环后祖先/子孙递归 CTE 不收敛、环节点从树构建中静默消失；对齐管理面 `ORG_PARENT_CYCLE`/`MENU_PARENT_INVALID` 先例）。`parentId=null`（移到顶层）不受两者限制。存量 GROUP_ROLE 组树为冻结读模型，跨类型装配自本任务起无 API 通道。并发语义（T-PERM-044 收口）：校验前先取 (abstract_role, 租户) 树级分布式锁（Redisson，事务提交/回滚后经 afterCompletion 释放），同树 parent 写路径（含 sync/full-sync）串行化，交叉移动的后进锁者校验时可见先提交的 parent——环无法落库；环脏数据下递归 CTE/祖先链遍历亦有防环止损（architecture §17）。
- `list`/`detail` 读门禁：类型级 `ROLE:VIEW`（评审收口补齐，与 `tree` 同款——`list` 信息量不低于 `tree`，不设门禁会使 tree 门禁事实可绕；无权抛 SecurityException）。
- `update` 补 `extraClear` 显式清空标志（T-FE-016 联调收口，对齐 §12 resource-entity 同款口径）：boolean 可选，`true`=清空 `extra` 为 null、优先于 `extra`（JSON null 无法区分「未传」与「清空」）；其余字段 null=不更新维持。前端编辑表单按「原 extra 非空且表单清空」判定传 `true`（resource-operation 同构公式）。
- `sync`/`full-sync` 的 parent 同款环路判定（评审收口补齐）：parent 为目标角色自身或其子孙时拒绝（单条 nonRetryable、批量该项 failed + `ROLE_PARENT_INVALID`）；只读版本预判先行——旧版本无条件按 STALE 钝化（§19.5 成功 no-op，含携带非法父边的旧事件），新版本才进入父解析/判环，判环拒绝不推进版本（上游修正后同版本重试不被判 STALE；预判与写入间的并发交错由 applyVersion 原子判定兜底）。full-sync 为**写入前逐项判定**：当前生效图 = 库内既有关系 + 本事务已应用项的边，内存图严格镜像写入语义（未携带父字段的更新不清图内旧边）——STALE 项不落边、天然保持旧边（含同批次多边共同成环、批内/库内混合、STALE+APPLIED 混合的组合均覆盖），仅拒绝真正闭合环的项、指向环的前缀安全项放行。同批重复 `businessKey` 的后续项写入前拒绝（`DUPLICATE_BUSINESS_KEY`，对齐 user-role/full-sync 先例）。item 省略 `parentRoleTypeCode` 时缺省 = `scope.roleTypeCode`（§19.7 既有规则，父解析/判环/写入均按缺省类型）。
- `remove` 级联覆盖 BASIC_ROLE 子孙（容器类型 GROUP_ROLE/ORG 之外）；级联根有权即整棵子树可删、不对子孙做独立权限过滤（项目规则「父级有权限子级即有权限」，2026-08-28 设计定案——统一引擎判定面继承落地登记 T-PERM-057，终态设计已并入 engine/implementation.md §3；原 T-PERM-045 已取消并入 057）。

### 10.4 user-role 管理与同步端点（/api/access/user-role/*）

| 接口                                                   | 说明                                           |
| ------------------------------------------------------ | ---------------------------------------------- |
| `POST /api/access/user-role/list`                        | 查询用户角色关系                               |
| `POST /api/access/user-role/sync`                        | 幂等同步组织/岗位用户角色关系                   |
| `POST /api/access/user-role/full-sync`                   | 按 scope 全量校准组织/岗位用户角色关系           |
| `POST /api/access/user-role/assign`                      | 批量分配角色或分组；**角色互斥守卫（T-PERM-063）**：事务内校验「授予后有效角色集（现有效 ∪ 本批新增，仅计启用且有效期覆盖当前时刻的角色）」，命中 ROLE_MUTEX 对整批原子拒绝 **20062**（message 列出冲突用户与角色对），规则 DB 直查即时生效 |
| `POST /api/access/user-role/revoke`                      | 批量回收角色关系（回收不产生互斥，无守卫）                               |
| `POST /api/access/user-role/batch-assign`                | 按角色视角批量分配多个用户；同款互斥守卫 **20062**（批内任一用户命中即整批拒绝） |

#### `user-role/list` 与 `user-role/revoke` 契约
**`POST /api/access/user-role/list`** — 按主体业务键查询该用户的角色关系：

```json
{
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001"
}
```

可选扩展筛选字段（如 `domainCode`）由实现与前端约定；请求体**不得**使用权限中心内部 `abstract_user.id`。

**`POST /api/access/user-role/revoke`** — 批量回收，请求体示例：

```json
{
  "items": [
    {
      "subjectTypeCode": "USER",
      "subjectExternalId": "u-10001",
      "domainCode": "admin",
      "roleTypeCode": "BASIC_ROLE",
      "roleExternalId": "role_admin",
      "relationId": null
    }
  ]
}
```

- 每条 `item` **必须**包含 `domainCode`，用于在「租户 + 域 + 全局」命名空间内唯一定位角色，避免跨业务域同名角色歧义。
- `relationId` 与表 `user_role.relation_id` 一致（如 POSITION 等类型需要时填写，否则 `null`）。`relationId` 是关联组织角色的 `abstract_role.id`（permission-center 内部主键），**外部不应据此反查业务实体**。
- `UserRolesResp.RoleSummary` 另含 `relationExternalId`（关联组织角色业务键 = sys_org.id 字符串，P2-1 增），供 admin 层解析组织名，避免用内部主键 `relationId` 错查 `sys_org`。permission-center `getUserRoles` 批量解析关联组织角色 externalId 填充。

### 10.5 abstract-role/tree 与 GROUP_ROLE 写入口收口

**`POST /api/access/abstract-role/tree`**：

```json
{
  "domainCode": "admin",
  "enabledOnly": false
}
```

- `domainCode` 可省略或显式 `null`：返回**全部**角色树（P1-1 修正：`abstract_role` 已移除 `biz_domain_id`，不按域过滤；原"仅返回全局域角色树（biz_domain_id 为空）"为旧模型残留文字）。
- `domainCode` 有值：仅校验域存在性（域不存在→空树），并按域分类规则判断当前域是否覆盖角色管理资源类型（`RoleManageAppServiceImpl.getRoleTree` 域覆盖短路分支，`DomainQueryMode.GLOBAL_PLUS`）；**不按域过滤角色**（角色树本身返回全量）。
- `enabledOnly`（可选，默认 false，T-PERM-022）：false/null 返回**全部有效角色**（`delete_flag=0`，含禁用——status 仅作展示字段，禁用角色在树中可见、可再启用）；true 仅返回启用角色（SQL 过滤，禁用节点整棵不返回）。消费方口径：角色管理页不传（需见禁用角色），授权页主体树传 true（T-PERM-022 设计定案：前端入参后端过滤）。

**T-PERM-043：GROUP_ROLE 写入口收口**：

- `extra-roles/list|add|remove` 三接口删除（见 §10 退役说明）。
- `create`：`roleTypeCode=GROUP_ROLE` 抛 `ROLE_TYPE_MISMATCH(20022)`（首期功能角色仅 BASIC_ROLE）。
- `update`：目标角色现行类型为 GROUP_ROLE 时抛 `ROLE_TYPE_MISMATCH(20022)`（请求体无 `roleTypeCode`，按目标类型判定）。
- `sync`/`full-sync`：`roleTypeCode=GROUP_ROLE` 抛 `ROLE_TYPE_MISMATCH(20022)`（外部同步通道与通用入口同口径拒绝，GROUP_ROLE 生命周期冻结）。
- `move`/`remove` 不拒绝 GROUP_ROLE：保留为存量行的清理通道（move 的同类型校验不排斥组树内部同类型移动与解挂，见 §10 角色管理契约要点）。
- `user-role/assign|revoke` 对存量 GROUP_ROLE 行仍可用（运行时读模型冻结：直绑展开、有效角色解析不受本任务影响）。
- GROUP_ROLE 枚举、role_type 种子与读模型（tree/list 过滤值、有效角色树展开）保留且冻结。

## 11. grant 能力（授权关系）
### 11.1 授权关系端点清单（/api/access/role-resource-permission/*）

| 接口                                                   | 说明                                           |
| ------------------------------------------------------ | ---------------------------------------------- |
| `POST /api/access/role-resource-permission/list`         | 查询角色权限配置（§11.2）                                       |
| `POST /api/access/role-resource-permission/apply-grant-plan` | **授权页面唯一写入口**（§11.4）：记录级 creates/updates/removes + 单事务原子 + 受影响行数断言（无 CAS/无幂等表，收窄） |
| `POST /api/access/role-resource-permission/sub-perm-allowed-types` | **授权页只读契约（v3.1，已随 T-PERM-034 落地 2026-08-30）**：按父资源类型返回 SUB_PERM 允许的子资源类型（§11.5） |
| `POST /api/access/role-resource-permission/save` ~~已删除~~ | 旧批量授予（已随 T-PERM-034 删除，2026-08-27 端点退役收口，无映射 404；管理面存量调用经核实为零） |
| `POST /api/access/role-resource-permission/revoke` ~~已删除~~ | 旧批量撤销（同上删除；删除语义由 apply-grant-plan.removes 覆盖） |
| `POST /api/access/role-resource-permission/children` ~~已删除~~ | 旧子权限查询（同上删除；查询由 list includeChildren 覆盖） |
| `POST /api/access/role-resource-permission/add-child` ~~已删除~~ | 旧子权限新增（同上删除；新增由 creates + parentPermissionId 覆盖） |
| `POST /api/access/role-resource-permission/update-child` ~~已移除~~ | 编辑子权限（同上移除；updates 覆盖）                           |
| `POST /api/access/role-resource-permission/children-save` ~~已移除~~ | 批量子权限提交（同上移除；plan 三段覆盖）                      |
| `POST /api/access/role-resource-permission/rebuild` ~~已移除~~ | 主权限原子重建（同上移除；removes+creates 同事务覆盖）         |
| `POST /api/access/role-resource-permission/remove-child` ~~已删除~~ | 旧子权限删除（已随 T-PERM-034 删除，无映射 404；删除由 removes 覆盖） |

### 11.2 角色权限配置查询（list）
> **写入入口（2026-08-27 端点退役收口）**：授权页面的全部写操作统一走 §11.4 `apply-grant-plan`（记录级 creates/updates/removes + 单事务原子 + 受影响行数断言；**砍 expectedRevision CAS / grant_revision 列 / 幂等表 / 20037/20039 / clientRequestId / @Idempotent**）。旧写入口 `save`/`revoke`/`children`/`add-child`/`remove-child` **已从 Controller 删除（无映射 404，无存量调用方，不留兼容层）**；`update-child`/`children-save`/`rebuild` 从未实现。以下 §11.2/§11.3 规则已并入 §11.4 统一预检。

#### 查询角色权限配置

`POST /api/access/role-resource-permission/list`

请求：

```json
{
  "domainCode": "admin",
  "roleTypeCode": "BASIC_ROLE",
  "roleExternalId": "role_admin",
  "resourceTypeCode": "ORG",
  "includeChildren": false
}
```

- `domainCode` 可选：非空时仅校验域存在性（域不存在 → 解析失败返回空列表）；**不按域过滤**——`abstract_role` 无域列（`biz_domain_id` 已移除），角色按 `roleTypeCode + roleExternalId` 唯一解析（`uk_abstract_role_external`）。授权页可恒传 null。（修正 2026-08-01  review P1-1：原"为空时只定位全局角色"为旧模型残留文字）
- **`resourceTypeCode`（可选；已落地 T-PERM-040，单类型矩阵上下文定稿）**：按资源类型过滤主权限（`depend_on IS NULL` 且 `resource_type` 匹配当前类型）——过滤下沉专用 Mapper 查询（`selectValidMainByRoleIdAndResourceType`，避免 N+1），子权限按 `depend_on` 批量挂父取回（`selectValidByRoleIdAndDependIds`，双重约束在 SQL 层满足）。**授权矩阵调用时必填**（矩阵一次只呈现一个类型，见 frontend/permission-grant.md §2.2）；null/缺省/空白串 = 不过滤（空白串视同缺省；兼容既有调用方，如管理面菜单授权按角色取全量）；**指定但类型不存在 = 空列表**（fail-closed 不回退全量，2026-08-31 定案）。
- `includeChildren`（可选，默认 `true` 兼容现行为）：
  - `false`：只返回该类型主权限（`depend_on IS NULL` + 上述类型过滤），子权限不进列表（辅助查询用）；
  - `true`：返回**该类型主权限及其全部子权限**——子权限按 `depend_on` 挂在其父主权限下返回，**子权限自身可能属于其他资源类型，不能按子记录自身 `resource_type` 过滤**（如父为 `ORG` 权限、子为 `BUTTON`/`DATA` 权限是合法配置）；**子权限集合双重约束：`depend_on` ∈ 主权限集合 且 `abstract_role_id` = 目标角色**（跨类型返回不引入其他角色或其他主权限下的记录）；`resourceTypeCode=null` 时按现状返回全量主权限 + 全部子权限。

响应：`data.items[]`，每项为 `RolePermissionItemResp`（14 字段；`grantSource`/`grantedBits`/`createdAt`/`childCount` 与 `includeChildren` 已实现）：

| 字段 | 类型 | 口径 |
|---|---|---|
| id | number | 记录 id（持久化行键，含 grant_source 区分） |
| resourceTypeCode | string | 资源类型码 |
| resourceCode | string\|null | 资源实例码（scopeMode=ALL 时为 null） |
| codeType | string\|null | 资源码类型（scopeMode=ALL 时为 null） |
| resourceName | string\|null | 资源名（展示用；scopeMode=ALL 时为 null，对齐 §18.5 示例） |
| operationCode | string | 操作码；MANUAL 授权一行只对应一个操作定义，不返回组合位记录 |
| canGrant | boolean | 是否可再授予 |
| conditionCode | string\|null | 条件码；无条件为 null |
| scopeMode | string | INSTANCE / ALL |
| dependOn | number\|null | 父权限 id；主权限为 null |
| grantSource | string | MANUAL（手动授权）/ AUTO_DEP（依赖自动补全产生）；INHERITED 为查询时克隆、不落库不返回 |
| grantedBits | string | 操作位，**十进制字符串**；MANUAL 记录等于单个操作定义的 `binaryBit`（2 的幂），避免 JSON number 精度丢失，前端用 BigInt 解析 |
| createdAt | string | 创建时间，ISO-8601 无时区（如 `2026-04-20T10:30:00`，对齐 §16 diff_snapshot 规范示例） |
| childCount | number | 直接子权限数（depend_on = 本 id，不含孙代）；list 时按 depend_on 分组 COUNT 一次返回 |

- 门禁：目标抽象角色 ROLE:VIEW（`PermissionGrantAppServiceImpl.listPermissions` 入口校验，失败返回空列表）。


### 11.3 子权限/范围权限（语义，单入口收敛）

子权限通过 `role_resource_permission.depend_on` 表达。`depend_on` 指向一条主权限记录的 `id`，表示当前授权依赖该主权限存在。典型场景：角色拥有"销售报表 DATA_READ"主权限，主权限下挂"上海数据 DATA_READ"和"杭州数据 DATA_READ"作为范围权限。

**单入口收敛（2026-08-02，2026-08-27 端点退役收口）**：授权页面不再调用子权限独立接口，创建/编辑/删除全部通过 §11.4 `apply-grant-plan` 的记录级 plan 表达。`add-child`/`remove-child` 等旧端点**已删除（无映射 404）**；`update-child`/`children-save` 从未实现：

- 新增子权限 = `creates[]` 项带 `parentPermissionId`（**属性系统不变量：`conditionCode` 必须为 null、`canGrant` 必须为 false，违反 -> 20043，2026-08-08 复审产品确认**）
- 编辑子权限 = **不支持**（子权限不承载条件/再授予，`updates[]` 目标为子权限 -> 20043；见 §11.4 校验规则）
- 删除子权限 = `removes[]`（子权限 id）
- 删除主权限 = `removes[]`（主权限 id，**级联软删其全部子权限——预期行为**）；跨键替换（范围/资源/操作变化）= removes 旧 + creates 新（同事务原子），**子权限不迁移**（产品语义），新主权限的子权限在 creates 中显式配置

语义规则：

- 子权限继承父权限的 `abstract_role_id`，调用方不需要再次传角色。
- 子权限的 `depend_on = parentPermissionId`，只支持一层，不允许子权限继续挂子权限。
- 子权限资源类型必须符合 `domain_config(config_type='SUB_PERM')` 中对当前业务域的配置。**fail-closed**：配置不存在 / `extra` 为空 / `extra` 格式错误 → 统一拒绝（20011，错误信息区分"配置缺失/配置为空/配置格式错误"）；`extra="*"` = **显式**允许任意子资源类型（通配必须显式声明，不得靠"未配置"隐式放行；顶层 `"*"` 经真实 jsonb 链路暂不可达——已知缺陷登记见 §11.5 判定步骤 1，ALLOW_ALL 以嵌套通配配置）。父域直接按父权限记录自身 `resource_type` 批量反查类型码与所属域，INSTANCE/ALL 统一，不依赖 `resource_entity_id`。该规则由 `PermissionGrantPlanDomainService.prevalidate` 强制；旧 `add-child` 已删除（2026-08-27 端点退役，无映射 404），不作为授权页面入口亦无从调用。**授权页面的类型选择过滤走只读契约 `sub-perm-allowed-types`（§11.5，判定口径与本条一致）**。
- `scopeMode=ALL` 表示该授权覆盖 `resourceTypeCode` 下全部资源；此时请求不传 `resourceCode/codeType`，运行时响应也通过 `scopeMode=ALL` 明确表达全量范围。
- 删除主权限时，系统必须级联软删 `depend_on` 指向该主权限的所有子权限。
- 子权限写入、删除都必须记录 `permission_change_log`，并通过 Redis pub/sub 广播 `PermInvalidateEvent` 失效父角色缓存（afterCommit）。
- 运行时不要通过 `auth/check` 承载范围集合：`auth/check` 只做主权限布尔判定；业务需要范围权限集合时调用 `POST /api/access/auth/query-scopes`。

### 11.4 聚合授权提交 apply-grant-plan（T-PERM-034 已落地 2026-08-30，收敛为唯一写入口，收窄）

> **自动授权预览已采纳、待实施（T-PERM-073，2026-09-20）**：按[自动授权设计 §12](dependency-auto-grant.md#admin-ui)，预览仅供参考，保存以事务内最新事实重新校验与计算；自动授撤影响与预览不同不构成拒绝保存或再次确认的条件，不新增强制预览凭证或版本匹配。保存成功后刷新实际权限与来源，预览/刷新失败不能冒充零影响或提交结果。预览正式接口与读取门禁仍待 078 收敛，现役提交校验与回滚规则保持。

> **自动授权物化已采纳、待实施（T-PERM-072，2026-09-20）**：按[自动授权设计 §6.3](dependency-auto-grant.md#materializer)，AUTO_DEP 保留推导出的各操作与条件事实，仅按同角色的资源/操作/条件身份精确去重，不按操作覆盖、条件支配或 MANUAL/类型级覆盖省略自动行。VIEW/UPDATE 即使存在覆盖关系也分别保留，运行时依各入口既有互斥评估集合处理，不以删行规避冲突。此为待实施自动结果规则，MANUAL 唯一性和现役提交契约不变。

**（2026-08-02）收窄**：砍 expectedRevision CAS + grant_revision 列 + 幂等表 grant_plan_idempotency + 20037/20039 + hash canonical + replayed/currentRevision（Stripe 式重幂等对低频内部管理页错配）；单事务原子 + 受影响行数断言；clientRequestId/@Idempotent/幂等表全删（幂等中间件实现取消（未登记看板））；schema 文件删除，本节为唯一权威契约（补结构约束）。

`POST /api/access/role-resource-permission/apply-grant-plan`

**请求**（统一响应壳见 §2.2）：

```json
{
  "domainCode": null,
  "roleTypeCode": "BASIC_ROLE",
  "roleExternalId": "role_admin",
  "plan": {
    "creates": [
      {
        "key": { "resourceTypeCode": "DATA", "resourceCode": "data:report:sales", "codeType": "default", "operationCode": "DATA_READ", "scopeMode": "INSTANCE", "conditionCode": null, "canGrant": false },
        "children": [
          { "resourceTypeCode": "DATA", "resourceCode": "data:city:shanghai", "codeType": "default", "operationCode": "DATA_READ", "scopeMode": "INSTANCE", "canGrant": false, "conditionCode": null }
        ]
      },
      { "key": { "resourceTypeCode": "DATA", "resourceCode": "data:city:beijing", "codeType": "default", "operationCode": "DATA_READ", "scopeMode": "INSTANCE", "canGrant": false, "conditionCode": null }, "parentPermissionId": 200 }
    ],
    "updates": [
      { "id": 100, "canGrant": false, "conditionCode": "office-hours" }
    ],
    "removes": [201, 202]
  }
}
```

**结构约束（补，原 schema 固化项回归本节）**：

- 请求体封闭对象（固定字段集，无额外字段）；`domainCode` 可选（非空仅校验域存在性，不按域过滤）；`roleTypeCode`/`roleExternalId`/`plan` 必填。
- `plan.creates[]`：`key`（recordKey）+ 可选 `parentPermissionId`（挂父，仅引用提交前已存在父）+ 可选 `children`（仅主权限可用，一次性建树）。
- `recordKey` 跨字段约束（**仅结构约束，复审拆分：属性不变量见 §11.4 校验规则**）：`operationCode` 必填且只能定位一个有效操作定义；`scopeMode=INSTANCE` -> `resourceCode`/`codeType` 必填（minLength 1）；`scopeMode=ALL` -> `resourceCode`/`codeType` 为 null。**条件绑定二选一（T-PERM-048）**：`conditionCode`（引用轨，值域=MANAGED）与 `inlineCondition`（内联轨）同记录同时出现拒绝（VALIDATION_FAILED）。**属性约束按主/子记录分类（见 §11.4）**：主权限 `conditionCode != null 或 inlineCondition != null` -> `canGrant=false`（🔧 T-PERM-041，20041）且条件必须启用（20042；内联恒启用天然满足）；子权限 `conditionCode`/`inlineCondition` 必须 null、`canGrant` 必须 false（20043），子权限 update 一律 20043。
- `plan.updates[]`：`id` 必填 + `canGrant`（三态：null=不改/true/false）+ `conditionCode`（三态：缺省或 null=不改/`""`=清除/非空=覆盖；**仅精确空串表示清除，全空白串按非空处理——条件码不存在则 20006，不静默清除**；值域=MANAGED 管理页条件，引用 INLINE 行 20060）+ `inlineCondition`（**T-PERM-048 内联轨**：`{name, conditionRules, gatewayEvaluable?}`，非空=该记录最终条件为该内联定义——现绑定为 INLINE 就地编辑规则（同条件 id）、现绑定为 null/MANAGED 新建内联行换绑；与 `conditionCode` 非空互斥（`""` 精确空串 + inlineCondition 组合=换绑为内联，允许）；内联条件 enabled 恒 true 不受 20042 约束）；与 `removes` 互斥（同 id 不得同时出现在两段）。
- **内联条件生命周期（T-PERM-048 定案①，2026-09-11）**：`creates[].key.inlineCondition` / `updates[].inlineCondition` 携带的内联定义随计划**同事务创建/编辑**（source=INLINE、code 自动生成 `inline-` 前缀、1:1 属于该授权记录不可共享、不建 resource_entity 投影行）；`removes` 删除授权行 / updates 换绑清除使内联条件引用归零时**同事务回收**（软删条件行）；计划失败整体回滚——取消/失败零残留。门禁随授权入口 ROLE:MANAGE 携带（定案②：不单独要求 CONDITION 写权限——内联条件是授权记录属性，操作者本可选任意管理页条件挂授权，自造一条边际风险≈0）。
- `plan.removes[]`：integer 数组（记录 id）。
- `permissionItem`（响应 items）：`grantedBits` 十进制字符串（63 位位图，前端 BigInt 解析）；`createdAt` local-date-time 无时区（如 `2026-04-20T10:30:00`，ISO-8601 无时区，非 RFC 3339）。

**语义**：请求携带角色 MANUAL 权限的**全部写意图**--`creates`（新建记录：主权限可带 children 一次性建树；子权限用 `parentPermissionId` 挂父）+ `updates`（直接修改现有记录的 canGrant/conditionCode，不涉及资源/操作/范围）+ `removes`（删除记录：主权限级联删子、子权限单条删），后端**单事务执行 -> 任一失败整体回滚**。同一角色 + 资源/范围 + 操作 + 父权限最多一条 MANUAL 直接授权，`conditionCode/canGrant` 是该记录的可变属性，不构成新分支。单事务原子执行，前端不再编排跨请求顺序。

**响应**：统一壳 `{ code: 200, message: "success", data: { "items": [...] }, requestId, traceId }`（完整持久化结果，结构同 list 响应 data；**不含 revision/currentRevision/replayed**，砍）。

**校验规则**（经 `prevalidateGrantPlan` 唯一预检入口执行，AppService 禁止自行拼门禁）：

- 目标抽象角色 `ROLE:MANAGE`（hasPermission 返回 boolean，必须显式判断 false 抛 SecurityException，先于一切分支）。
- **creates/updates 共用不变量（🔧 T-PERM-041，2026-08-06 评审移置；复审修订：仅主权限；已落地 2026-08-30）**：
  - **条件不可转授（仅主权限）**：**主权限**（`creates[].key` 与 updates 目标为主权限）`conditionCode != null` 时 `canGrant` 必须为 `false`——覆盖 `creates[].key` 及 updates 应用三态变更后的**最终状态**（判定与 update 是否同时携带 conditionCode/canGrant **无关**：只改 conditionCode 覆盖到当前 canGrant=true 的记录、或只改 canGrant=true 使已有条件的记录变为可转授，均按最终状态判定）；**子权限不适用本不变量**（子权限 create 非 null/false -> 20043，见下）；违反 -> **20041** `CONDITIONAL_PERMISSION_CANNOT_DELEGATE`（新错误码）。
  - **条件启用状态（🔧 2026-08-08 产品确认，新增 20042；已落地 2026-08-30）**：主权限 `conditionCode` **新写入或变更**时必须 `enabled=true`——creates 主权限与 updates 中 conditionCode 变化均适用（与 20041 同样按最终状态判定）；停用条件不得新建绑定或改绑（违反 -> **20042** `CONDITION_DISABLED`）；存量绑定（update 未变更 conditionCode）允许保留并回显标注——**"未变更"按解析后条件 id 比对（2026-08-30 设计定案）：update 重写与当前绑定同一条件（同 id）视同存量保留豁免 20042，清除（`""`）与缺省（`null`）不触发**；与前端选择器/复制同规则（新选限启用，源条件停用时复制入口禁用）；**子权限不承载条件，不受 20042 约束**（子权限带条件 -> 20043）。
**错误优先级（复审明确，消除 20041/20043 重叠）**：**先按主/子记录分类**——① 子权限 create 的 `conditionCode`/`canGrant` 非 null/false 一律 **20043**（不再评估 20041/20042）；② 子权限 update 一律 **20043**；③ 20041/20042 仅评估主权限；④ 主权限同时违反多个不变量时按 **20041 → 20042 → 20033 → 其他** 顺序返回首个命中。
- **creates**：
  - **单直接授权唯一性**：主权限及子权限均按 `(role, resource/范围, operation, parentPermission)` 唯一；同键已存在 MANUAL 记录 -> **20033** `DIRECT_PERMISSION_CONFLICT`（conditionCode/canGrant 不参与身份；查重基于本请求 removes 软删生效后状态，合法"先删后同键重加"不误判；AUTO_DEP 并列允许）。主权限（`parentPermissionId` 缺省）可带 children 一次性建树；`canGrant` 缺省 false。
  - 子权限（`parentPermissionId` 非空）：父不存在 -> **20009**；父非主权限 -> **20010**；不得再带 children；**属性系统不变量（复审产品确认）**：`conditionCode` 必须为 null/空、`canGrant` 必须为 false（与主权限无关，子权限不承载条件/再授予），违反 -> **20043** `SUB_PERMISSION_ATTRIBUTE_NOT_ALLOWED`；历史异常记录（已存在带条件/可再授予的子权限）只兼容读取与删除，不允许继续属性编辑。
  - **operationCode 适用性校验（已落地 T-PERM-040；全局操作退役后收窄）**：逐项校验必填的 `operationCode` 是否适用于 `recordKey.resourceTypeCode`——判定基于**有效（未停用）操作定义**：该类型存在同码专属定义时校验通过（全局操作概念已退役，无回退轨）；不匹配 -> **20008** `RESOURCE_TYPE_OPERATION_MISMATCH`（码存在于其他类型但本类型无专属定义；错误码已存在，复用）。码任何类型都不存在 -> **20005** OPERATION_NOT_FOUND（与 20008 区分锁定）。MANUAL 新授权不接受 `operationCode=null` 或组合位。**覆盖全部新记录形态**：`creates[].key`（主权限）、`creates[].children[]` 嵌套子权限、`parentPermissionId` 挂已有父记录的 create——不允许通过子权限形态绕过。
  - **条件不可转授 -> 20041**：见上方 **creates/updates 共用不变量**（2026-08-06 评审移置，此处不再重复）。
  - 逐项 `checkCanGrant`（资源/范围/操作结构键；操作者可转授记录按 T-PERM-041 必为无条件，因此 conditionCode 不参与授权传递身份）；不满足 -> **20040** `GRANT_CANNOT_DELEGATE`；SUB_PERM 约束（fail-closed，父域 resource_type 直查，§11.3）；`scopeMode`/资源兼容。
- **updates**：目标 id 必须存在且属于目标角色 -> 否则 **20036**；AUTO_DEP -> **20034**；AUTHORITY_ROOT -> **20061**（T-PERM-062：授权根种子行只读，随类型生命周期维护）；**目标为子权限（depend_on 非空）-> 20043**（子权限属性为系统不变量，不承载条件/再授予，仅可删除）；与 removes 互斥；**条件不可转授按最终状态判定（creates/updates 共用不变量，违反 -> 20041，见上）**；`canGrant` 或 `conditionCode` 有变更 -> `canGrantPermission`；conditionCode/canGrant 直接更新原记录，不创建新记录；**实际影响行数 ≠ 预期 -> 20036 整体回滚**；update 至少改 canGrant/conditionCode，拒绝重复 ID 与 update/remove 交叉 ID。
- **removes**：主权限 id -> 级联删子（预期行为）；子权限 id -> 单条删；id 不存在/已软删/非目标角色 -> **20036**；AUTO_DEP -> **20034**；AUTHORITY_ROOT -> **20061**；**实际影响行数 ≠ 预期（并发删除/修改）-> 20036 整体回滚**。
- **无幂等中间件（定案）**：**砍 clientRequestId / @Idempotent / 幂等表**（幂等中间件实现取消（未登记看板））；前端 saving 期间按钮 disabled 防重复点击，超时提示刷新确认；后端靠单事务原子 + uk 约束 + 受影响行数断言保证不重复/不部分成功。执行顺序：① 认证 + ROLE:MANAGE 门禁（hasPermission 显式判断 false 抛 SecurityException）-> ② prevalidateGrantPlan -> ③ 单事务执行 + 受影响行数断言。
- **砍**：`expectedRevision` CAS / `grant_revision` 列 / 20037 `VERSION_CONFLICT` / 20039 `IDEMPOTENCY_OPERATOR_MISMATCH` / 幂等表 `grant_plan_idempotency` / hash canonical / replayed/currentRevision / 20037 重试 machinery / clientRequestId / @Idempotent 中间件（幂等中间件实现取消（未登记看板））。
- 写入 `permission_change_log` + 一次 `PermInvalidateEvent`（afterCommit）。

**错误码枚举（apply-grant-plan 链路，精简）**：20001 ROLE_NOT_FOUND / 20003 ROLE_DISABLED / 20004 RESOURCE_NOT_FOUND / 20005 OPERATION_NOT_FOUND / 20006 CONDITION_NOT_FOUND / 20007 RESOURCE_TYPE_NOT_FOUND / 20008 RESOURCE_TYPE_OPERATION_MISMATCH / 20009 PARENT_PERMISSION_NOT_FOUND / 20010 PARENT_PERMISSION_NOT_TOP_LEVEL / 20011 SUB_PERMISSION_RESOURCE_TYPE_NOT_ALLOWED / 20012 RESOURCE_CODE_REQUIRED（INSTANCE 缺 resourceCode 服务端兜底）/ **20033 DIRECT_PERMISSION_CONFLICT（同角色+资源/范围+操作+父权限的 MANUAL 直接授权已存在）** / 20034 AUTO_DEP_READONLY / 20036 PERMISSION_NOT_FOUND / 20040 GRANT_CANNOT_DELEGATE / **20041 CONDITIONAL_PERMISSION_CANNOT_DELEGATE（条件权限不可转授）** / **20042 CONDITION_DISABLED（🔧 2026-08-08 产品确认：新写入/变更的主权限 conditionCode 必须为启用状态）** / **20043 SUB_PERMISSION_ATTRIBUTE_NOT_ALLOWED（🔧 2026-08-08 复审产品确认：子权限不承载条件/再授予——create 非 null/false 或 update 目标为子权限均拒绝，系统不变量）** / **20061 AUTHORITY_ROOT_READONLY（T-PERM-062：授权根种子行只读——updates/removes/向其挂子权限拒绝，随类型生命周期维护，见 §13）**；**砍 20037/20039**；20013/20014/20035 随旧子权限接口移除；20038 随同键重建语义废弃。

> **20040 reason 细分（T-PERM-062，2026-09-12）**：拒绝 message 形如 `Cannot delegate <key>; reason=<REASON>`；委托失败（NO_PERMISSION/NO_GRANT_RIGHT）且目标为自定义 resource_type（is_system=false）在租户内零条可转授覆盖行时，reason 为 **TYPE_GRANT_ORIGIN_MISSING**（类型未初始化/种子被直改库清除——前端据此引导到类型定义页确认所有者角色；种子行经产品链路不可销毁，该 reason 正常运维不应出现）。内置类型零可转授行是转授链收窄的设计状态，reason 维持 NO_PERMISSION/NO_GRANT_RIGHT。

#### 11.4.1 已采纳待实施：授撤影响预览（073）

`POST /api/access/role-resource-permission/preview-grant-plan`，管理入口，服务凭证不开放；固定图按现有角色授权族注册。请求为 `{request: ApplyGrantPlanReq, maxItems?: int}`，request 复用现役 domainCode、roleTypeCode、roleExternalId、plan；外层可选 maxItems（1..2000，缺省 500）。ROLE:MANAGE、角色启用与授权委托/形状校验和保存同源；预览无数据库写入，不创建 INLINE 条件。

响应 `R<GrantPlanPreviewResp>`：`advisory=true`、`viewedAt`、`added`、`removed`、`retained`、`totalCount`、`truncated`、`driftDetected`。每个事实包含资源业务键、operationCode、conditionRef 与来源种子引用；retained 指受本计划影响但仍有其他显式来源支持的自动事实。conditionRef 为 NONE / EXISTING（conditionId/可见描述）/ PREVIEW_INLINE（requestItemRef），不将新条件预先落库。totalCount 为完整计算的影响事实数量，maxItems 只截断展示，truncated=true 时不能宣称明细完整。

事实元素统一为 `{fact: FactKey, seeds: SeedRef[]}`，嵌套形状如下（字段必须返回，nullable 字段明确为 null）：

- `ResourceKey = {resourceTypeCode: string, resourceCode: string, codeType: string}`，codeType 已归一为非空默认值。
- `ConditionRef = {kind: "NONE"|"EXISTING"|"PREVIEW_INLINE", conditionId: long|null, requestItemRef: string|null}`。NONE 后两项均 null；EXISTING 仅 conditionId 非 null；PREVIEW_INLINE 仅 requestItemRef 非 null，如 `creates[0]`、`updates[1]`，同表达式不同条目不得合并。现有 INLINE 就地编辑保持 EXISTING 身份。
- `FactKey = {resource: ResourceKey, operationCode: string, conditionRef: ConditionRef}`。
- `SeedRef = {permissionId: long|null, requestItemRef: string|null}`，现有显式来源返回 permissionId，新建预览来源返回请求条目位置，严格二选一。seeds 按身份排序，不返回完整路径组合。

三组均按 ResourceKey 各字段、operationCode、ConditionRef(kind/id/itemRef) 的元组顺序排列（字符串按 Unicode 码点、ID 按数值）。展示预算全局按 removed、added、retained 顺序取前 maxItems 项，再放入对应数组；totalCount 为三组完整数量之和。输出数小于 totalCount 时 truncated=true，空数组不能独立解释为无影响。现有漂移通过 driftDetected 表示，不加入“计划导致”的 added/removed 计数。

基于当前种子得到 beforeDesired、计划假想状态得到 afterDesired，并与实际 AUTO_DEP 标识已有漂移；不把 actual 缺行当“原来没权限”，也不伪称预览已修复漂移。预览失败按正常异常信封返回，前端显示“无法预览”；不返回伪造零影响。保存继续独立接受原 ApplyGrantPlanReq，无预览 token/hash/版本匹配字段。

### 11.5 子权限类型只读查询（sub-perm-allowed-types，v3.1，已随 T-PERM-034 落地 2026-08-30，收口修订）

`POST /api/access/role-resource-permission/sub-perm-allowed-types`

授权弹窗子权限配置器专用**只读**契约：按**父资源类型**返回该父类型允许挂载的子资源类型，判定口径与 §11.3 SUB_PERM fail-closed 校验一致（父域按父资源类型反查类型码与所属域，不依赖 `resource_entity_id`）。本接口只做配置查询，不触发权限缓存失效与变更日志。

请求：

```json
{
  "domainCode": null,
  "roleTypeCode": "BASIC_ROLE",
  "roleExternalId": "role_admin",
  "parentResourceTypeCode": "MENU"
}
```

- `domainCode`（可选，授权页恒传 null，同 §11.2 list 的 domainCode 口径）、`roleTypeCode`、`roleExternalId`：**目标角色业务键，门禁定位用**（`resolveRoleId` 解析失败 -> 20001，见下方门禁）。
- `parentResourceTypeCode` 必填；资源类型不存在 -> **20007** `RESOURCE_TYPE_NOT_FOUND`。

响应 `data`：

| 字段 | 类型 | 口径 |
|---|---|---|
| parentResourceTypeCode | string | 回显请求父资源类型 |
| mode | string | `ALLOW_ALL` / `ALLOW_LIST` / `ALLOW_NONE` |
| reason | string\|null | `ALLOW_NONE` 时细分原因：`CONFIG_MISSING`（配置缺失）/ `CONFIG_EMPTY`（extra 为空）/ `CONFIG_INVALID`（extra 格式错误）/ `PARENT_NOT_CONFIGURED`（无匹配 parent_type 项）/ **`CHILD_TYPES_EMPTY`（匹配到 parent_type 但 child_types 为空，2026-08-08 复审新增）**；`ALLOW_ALL` / `ALLOW_LIST` 时为 null |
| allowedChildResourceTypeCodes | string[] | `ALLOW_LIST` 时返回允许的子资源类型集合（匹配项并集去重）；`ALLOW_ALL` / `ALLOW_NONE` 时为空数组 |

`mode` 判定（与 `PermissionGrantPlanDomainServiceImpl.assertSubPermissionAllowed` 写校验**同一策略解析函数**，前端据 `mode` 过滤选择器，**不得前端硬编码允许集**）。**判定优先级顺序固定如下（2026-08-08 复审写死，消除多匹配项歧义）**：

0. **配置存在性**：`domain_config(config_type='SUB_PERM')` 不存在 -> **`CONFIG_MISSING`**；`extra` 为空/空白 -> **`CONFIG_EMPTY`**。
1. **顶层通配识别（必须先于 JSON 解析——`"*"` 不是合法 JSON）**：`extra.trim() == "*"` -> **`ALLOW_ALL`**，直接结束。
   **已知缺陷（2026-09-02 T-FE-018 联调实证，登记待办）——顶层通配经真实链路暂不可达**：`domain_config.extra` 为 jsonb 列，正规写入口（domain-config save）仅校验 JSON 语法，裸 `*` 不是合法 JSON、无法落库；即便以 JSON 字符串 `"*"` 形态入库，读回为带引号文本，本步骤裸串比对不命中、步骤 2 解析得到标量字符串而非 `allowed[]` 结构 -> `CONFIG_INVALID`（单测 fixture 为裸字符串形态、未覆盖真实 jsonb 读回）。**ALLOW_ALL 请以嵌套通配 `{"allowed":[{"parent_type":"<父类型>","child_types":["*"]}]}` 配置（实测可用）**；修复（解析层兼容带引号形态 + 写入口口径）另立任务收口，本判定语义不变。
2. **全量解析与校验（复审修正：非匹配项不再容错）**：其余值解析为 JSON 失败 -> **`CONFIG_INVALID`**；解析成功后，**完整验证 `allowed[]` 的每一项**——`parent_type` 必须为非空字符串、`child_types` 必须为字符串数组，**任一项结构非法 -> `CONFIG_INVALID`**（与写实现一致：`assertSubPermissionAllowed` L452-453 在比较父类型前遍历全部项校验结构，缺 `parent_type` 的项即拒绝——它无法证明属于其他父类型，属全局结构错误；且不得因"先前匹配项已放行"跳过后续项校验——**禁止匹配即 return**）。
3. 无任何匹配 `parent_type` 的项 -> **`PARENT_NOT_CONFIGURED`**。
4. **任一匹配项的 `child_types` 内含 `"*"`**（嵌套通配，写校验 L460 接受）-> **`ALLOW_ALL`**。
5. 所有匹配项 `child_types` **并集（去重）非空** -> **`ALLOW_LIST`**（如 `[{"parent_type":"MENU","child_types":["BUTTON"]},{"parent_type":"MENU","child_types":["DATA"]}]` -> `["BUTTON","DATA"]`；`[{MENU,[]},{MENU,["BUTTON"]}]` -> `ALLOW_LIST["BUTTON"]`，空项不覆盖非空项）。
6. 所有匹配项并集为空（如全部 `child_types=[]`）-> **`CHILD_TYPES_EMPTY`**。
- **大小写口径**：`parent_type` 与 `child_types` 的比较均**大小写不敏感**（对齐写校验 `equalsIgnoreCase`）；响应返回**配置原文**（不做规范码转换，避免配置含 `type_definition` 外码时丢失），前端按大小写不敏感匹配过滤。
- **实现约束**：读写链路必须复用同一个策略解析函数（抽取 `assertSubPermissionAllowed` 的配置解析逻辑为共享方法），保证"后端允许集 == 前端过滤集"。

- 门禁：`resolveRoleId(domainCode, roleTypeCode, roleExternalId)` 解析**失败 -> 20001** `ROLE_NOT_FOUND`（明确抛出，不返回空结果——本接口无"空列表即自然结果"语义，避免前端把角色不存在误判为 `ALLOW_NONE`）；`hasPermission(ROLE, roleId, VIEW)` 为 false 时**抛 `SecurityException` 走统一访问拒绝**（与 §11.4 写入口同模式；**区别于 §11.2 list 的"失败返回空列表"**——本接口不采用空结果掩盖鉴权失败，以免把"无权查看"误判为"SUB_PERM 未配置"）。
- 错误码：不新增；`parentResourceTypeCode` 无效 -> 20007，角色定位失败 -> 20001，无 ROLE:VIEW -> 统一访问拒绝（全局异常处理）。

**实现绑定（收口补充）**：SUB_PERM 解析的唯一公开入口为 `PermissionGrantPlanDomainService.resolveSubPermissionPolicy(tenantId, parentResourceTypeCode)`，返回不可变策略对象 `SubPermissionPolicy { mode, reason, allowedTypeCodes, allows(childTypeCode) }`（`assertSubPermissionAllowed` 抽取，见 engine/implementation.md §4）；调用链：Controller → `PermissionGrantAppService`（请求/响应 DTO 映射）→ `planDomainService.resolveSubPermissionPolicy`（读接口直接序列化策略结果）；`prevalidate` 内部复用同一解析器（`policy.allows`）。**禁止在 AppService/Controller 另行编写 SUB_PERM 判断**，读写必须同源。

> **落地状态（T-PERM-034 收口，2026-08-30）**：端点/策略对象/判定优先级已按上文逐条实现（`resolveSubPermissionPolicy` 唯一公开入口，读接口经 ROLE:VIEW 实例门禁直接序列化，写链路复用 `allows()`）；单测覆盖判定表全分支（CONFIG_MISSING/EMPTY/INVALID×2/PARENT_NOT_CONFIGURED/嵌套通配/并集去重/CHILD_TYPES_EMPTY/20007）与门禁（20001/SecurityException）。

## 12. resource 能力（资源实体、操作、服务接入与接口映射、资源依赖）
### 12.1 资源与操作（resource-entity + operation-permission）
| 接口                                          | 说明               |
| --------------------------------------------- | ------------------ |
| `POST /api/access/operation-permission/list`    | 查询操作权限       |
| `POST /api/access/operation-permission/detail`  | 查询操作详情       |
| `POST /api/access/operation-permission/create`  | 创建操作           |
| `POST /api/access/operation-permission/update`  | 更新操作           |
| `POST /api/access/operation-permission/remove`  | 删除操作，支持批量 |
| `POST /api/access/resource-entity/tree`         | 查询资源树         |
| `POST /api/access/resource-entity/list`         | 查询资源列表       |
| `POST /api/access/resource-entity/detail`       | 查询资源详情       |
| `POST /api/access/resource-entity/create`       | 创建资源           |
| `POST /api/access/resource-entity/batch-create` | 批量创建资源       |
| `POST /api/access/resource-entity/update`       | 更新资源           |
| `POST /api/access/resource-entity/move`         | 移动资源树节点     |
| `POST /api/access/resource-entity/remove`       | 删除资源，支持批量 |
| `POST /api/access/resource-entity/sync`         | 资源实体专用幂等同步 |
| `POST /api/access/resource-entity/full-sync`    | 按 scope 全量校准资源 |

**业务键定位（T-PERM-028 定稿）**：`resource-entity` 的 `detail/update/move/remove` 与 `operation-permission` 的 `detail/update/remove` 以业务键定位，不再接受内部 id（schema `uk_resource_entity` / `uk_operation_permission_typed` + `ck_operation_permission_resource_type_required` 非空 CHECK 共同保证唯一（原 `uk_operation_permission_global` 已随全局操作退役删除，T-PERM-049）；混合形态——detail/update 键字段平铺、move 嵌套、remove items 数组）：

```json
// resource-entity/detail（ResourceKeyReq 平铺）
{ "resourceTypeCode": "MENU", "code": "sys-mgmt", "codeType": "default" }
// resource-entity/update（ResourceUpdateReq：键平铺 + 可编辑字段；code 为业务键不可更新）
{ "resourceTypeCode": "MENU", "code": "sys-mgmt", "codeType": "default",
  "name": "系统管理", "path": null, "status": 1,
  "extra": "{\"k\":1}", "extraClear": false }
// resource-entity/move（ResourceMoveReq 嵌套；parent null=移动到顶层）
{ "resource": { "resourceTypeCode": "MENU", "code": "sys-mgmt", "codeType": "default" },
  "parent": null }
// resource-entity/remove（ResourceKeysReq）
{ "items": [ { "resourceTypeCode": "MENU", "code": "sys-mgmt", "codeType": "default" } ] }
// operation-permission/detail|update（OperationKeyReq / OperationUpdateReq：resourceTypeCode 必填）
{ "resourceTypeCode": "USER", "code": "VIEW" }
// operation-permission/remove（OperationKeysReq）
{ "items": [ { "resourceTypeCode": "USER", "code": "VIEW" }, { "resourceTypeCode": "ROLE", "code": "MANAGE" } ] }
```

| 规则 | 口径 |
|---|---|
| codeType | 可选，null/缺省归一为 `default`（DDL 默认值） |
| 业务键查不到 | detail 抛 20004/20005；update/move 同（原 `data:null` 宽松形态已删除） |
| extraClear | boolean 可选；true=清空 extra 为 null，优先于 extra（JSON null 无法区分「未传」与「清空」） |
| move 校验 | 跨资源类型 / 目标父为自身或子孙 → 20053 RESOURCE_PARENT_INVALID（一类码两因，message 区分） |
| create/batch-create 父校验（T-PERM-068，2026-09-17 Q-007 定案②） | 与 move 同口径：业务键父 `parentResourceTypeCode` ≠ 自身类型 → 20053（拒绝/宽容收集跳过）；单条 create 裸 `parentId` 补父存在性（20004）与类型校验（20053），batch-create 裸 `parentId` 存在性既有、补类型比对；父业务键半传（只传 code 无 typeCode）仍按「无父造根」处理（与 sync 缺省回填语义有意不同——管理面交互式 API，半传视为前端缺陷更安全） |
| remove 未命中键 | 静默跳过（对齐原 ids 批删语义），级联软删子孙 |
| SYNC 类型只读（T-PERM-052；API 入列 T-PERM-069） | 目标类型声明 `extra.managedMode=SYNC` 时 create/batch-create/update/move/remove 一律拒绝 **20055** `RESOURCE_EXTERNALLY_MAINTAINED`——message 按声明来源分两支：**内部来源**（`syncSourceService=access-service`，事实链路七类型 USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION/CONDITION + API，T-PERM-069 起）为「资源由系统事实链路维护（用户/组织/菜单/角色/类型定义/条件管理、文件上传/预置、服务接口同步 service-config/sync——末项仅 API 适用），资源管理面只读: resourceTypeCode=X」；**外部来源**为「资源由外部来源维护，请到来源系统操作: resourceTypeCode=X, syncSourceService=Y」；**remove 的级联守卫覆盖删除全集（含展开的后代）**——入口虽已收紧同类型父边（T-PERM-068），DB 直写脏数据仍可能构成 MANAGED 根下含 SYNC 类型后代的跨类型子树，命中即整批拒绝不软删；读路径（tree/list/detail）不受限 |

> **resource 面 sortOrder 字段退役（T-ACCESS-036，2026-09-13）**：`resource_entity.sort_order` 全仓零读取方（列表分页 `ORDER BY id`、资源树不按该列排序），列与实体字段、SDK 双册 `ResourceCreateReq/ResourceUpdateReq/ResourceResp`、服务端 `ResourceResp/ResourceTreeResp`、`ResourceEntitySyncReq` 与 full-sync item、`ResourceTreeResp.ResourceTreeNode` 及全部写入点（管理面 create/batch-create/update、资源同步通道、service-config/sync 资源落库）一次性退役；前端提交与表单链路同批清理。仍携带 `sortOrder` 的旧载荷在反序列化层被拒（全局 ObjectMapper 严格模式，未知字段 → 400/90001 信封，与 T-PERM-053 operationCode 退役同机制）；`ResourceSortOrderRetiredTest` 锁定四 DTO 面行为。role/menu/org/type_definition 的 `sortOrder` 不在退役范围（各自在用）。

**读门禁（T-PERM-028 补齐，类型级）**：`resource-entity/list`、`resource-entity/detail` 补 `RESOURCE:VIEW`，`operation-permission/detail` 补 `OPERATION:VIEW`（与既有 tree/list 门禁同口径；bootstrap §14.4 最小集已持有，不阻断首管理员）。

**resource_type 创建联动预置（T-PERM-028 实现定案）**：`type-definition/create` 在 `typeKey=resource_type` 时同事务预置 CRUD 四操作位 `CREATE(1,0)/VIEW(2,0)/UPDATE(4,2)/DELETE(8,2)`（DDL CROSS JOIN 预置组模板同款；新类型位段空闲无 uk 冲突）；非 resource_type 类型不预置。

**操作生命周期与授权根联动（T-PERM-062，2026-09-12 grok 外评存量升级用户定案「补联动」）**：`operation-permission/update` 的 `binaryBit` 有效变更（自定义 resource_type 目标）同事务迁移授权根种子——软删旧操作位种子行、向所有者补种新操作位（不迁则旧位成指向无定义位的永久死行、新位零种子令该操作回到无人能首授的死锁）；`operation-permission/remove` 对被删自定义类型操作同事务级联清理该操作位种子行（生命周期通道回收，与 apply-grant-plan 20061 只读边界不冲突——同 T-PERM-050 类型删除级联先例；is_system 类型无种子不联动）。两入口与类型生命周期写路径共持 RESOURCE_ENTITY 树写锁并锁内重读（update 锁内重读操作行 + 锁内重绑 typeValue——「删类型→同码重建」交错下锁前解析值指向已级联清理的死号）。

**类型授权根生命周期（T-PERM-062，2026-09-12 定案——新类型首笔授权自举）**：全新自定义类型在租户内初始可转授行为 0，apply-grant-plan 委托校验（checkCanGrant 严格无旁路，§14.1 红线维持）下无人能完成首笔授权——固定图内 `API:ACCESS` 类型级 canGrant（T-API-001「鸡生蛋」解法）的模式推广到类型生命周期：

- **创建即建基座**：`type-definition/create`（typeKey=resource_type）同事务向所有者角色写 CRUD 四操作位 `AUTHORITY_ROOT` 首授行（`grant_source=AUTHORITY_ROOT`；scopeAll + canGrant + 无条件 + 无实例 + 单操作位，形状由 DDL CHECK `ck_role_resource_permission_authority_root` 焊死）；所有者指针持久化于 `type_definition.extra.grantOriginRole`（`{"roleTypeCode":..,"roleExternalId":..}`，roleTypeCode 值域仅 BASIC_ROLE）——**服务端管理键**：create 请求 extra 自带该键拒绝 **20044**（合法输入通道是 `ownerRoleTypeCode/ownerRoleExternalId` 请求字段）。
- **追加操作补种**：`operation-permission/create` 目标为自定义 resource_type 时同事务向同一所有者补种该操作位（不钩则死锁转移到第五个操作）；`is_system` 类型不钩（内置类型转授链收窄是既有产品选择，T-PERM-027 口径维持）。
- **所有者变更迁移（用户定案：变更=转移而非叠加）**：`type-definition/update` 的 extra 携带不同 `grantOriginRole` → 新所有者先解析（不存在 20001/停用 20003 整单回滚），类型行落库后同事务「先清后种」重整化——软删该类型全部 AUTHORITY_ROOT 行（含已删角色/误配旧 owner 残留，杜绝误配 owner 的一次性永久扩权——AUTHORITY_ROOT 经授权页不可改删，只补不迁则旧 owner 无产品内移除通道），再向新所有者补齐该类型全部有效操作位；markRoles 覆盖旧 owners 与新 owner。未携带该键 = 保留现值（指针无「清除」语义，所有权无空态）；同值重提交 = 幂等无迁移；指针仅自定义 resource_type 可携带（其他 typeKey / is_system 类型 20044）；指针显式 null / 结构非法 20044（fail-closed）。
- **种子行只读**：AUTHORITY_ROOT 行经 apply-grant-plan updates/removes（含向其挂子权限）一律拒绝 **20061** `AUTHORITY_ROOT_READONLY`（对齐 AUTO_DEP 只读 20034 先例）；类型删除级联清理是唯一回收路径（T-PERM-050 级联覆盖全部 grant_source）。
- **写入通道**：bootstrap 直写通道下沉为 `PermissionGrantPlanDomainService.seedGrants`（跳过 prevalidate/verifyDelegation、保留 validateSingleManualGrants + validateGrantAttributes 两条领域校验、幂等 insert-if-absent），bootstrap 固定图与类型授权根共用——「在委托不变量之外建立引导」而非开洞；checkCanGrant 与通用授权链零改动。
- **20040 reason 细分**：委托失败（NO_PERMISSION/NO_GRANT_RIGHT）且目标为**自定义 resource_type**（is_system=false）在租户内零条可转授覆盖行（canGrant=true + 无条件 + 范围匹配 + 位覆盖，任意角色）时，message reason 改判 `TYPE_GRANT_ORIGIN_MISSING`（区分「类型未初始化」与「操作者持有面不够」；内置类型零可转授行是转授链收窄的设计状态，reason 维持原值——用户定案 2026-09-12）。
- **恢复路径**：所有者角色被删除致授权根失能时，经 `type-definition/update` 重指所有者即恢复（迁移语义自动清理已删角色残留行并补齐新 owner）——产品内闭环，无需 reseed API（2026-09-12 实施期定案，取代立项时「不采 reseed」的纯结构防护口径：恢复面收敛进 update 单入口）。

**请求（类型查询参数）**：

```json
{
  "resourceTypeCode": "ORG"
}
```

| 参数 | 类型 | 口径 |
|---|---|---|
| resourceTypeCode | string\|null | 可选；null/缺省 = 不过滤，返回全量操作定义；空白串视同缺省（不过滤）；指定类型 = 仅该类型操作定义；**指定但类型不存在 = 空列表**（fail-closed 不回退全量，T-PERM-040 收口 2026-08-31，与 role-resource-permission/list 同口径）。原 domainCode 参数已删除（死参数：从未实现过滤、契约未登记语义、零调用方，2026-08-31 设计定案） |

> **已知不一致（2026-09-02 T-FE-018 需求对齐会登记，待办收口、暂不实施）**：`resource-entity/tree` 传 `resourceTypeCode` 但类型不存在时，类型解析失败视同未传 → 返回全类型全量资源树（**fail-open**），与本节 operation-permission/list 及 §11.2 role-resource-permission/list 的「未知类型 = 空列表 fail-closed」（2026-08-31 定案）口径不一致。授权页类型候选恒来自 type-definition 有效值，无页面触达路径（T-FE-018 联调不修，后端零改动纪律）；后续收口时对齐为空列表 fail-closed。注：tree 的 `domainCode` 是活参数（DomainClassifyService GLOBAL_PLUS 分类过滤），与 operation-permission/list 已删除的 domainCode 死参数无关。

> **全局操作概念退役（2026-08-30 设计定案，T-PERM-049）**：`operation_permission.resource_type` 由 DDL CHECK 强制非空——每个资源类型的操作定义完全独立，位空间按类型隔离（`uk_operation_permission_typed_bit` 保证同类型同位不异码）。原 `includeGlobalFallback` 合并参数、`resourceTypeCode 可空=全局操作` 键轨与「专属优先、全局回退」合并语义随概念一并退役（存量种子无全局行，无迁移成本；退役动机：授权行只存 `resource_type + granted_bits` 不存操作 ID，全局位与专属位同值时授权身份不可区分——同位异码互相越权）。

> **调用方门禁**：接口鉴权维持现状（`OPERATION:VIEW` 等既有接线），矩阵页消费方仍以既有页面门禁控制可见性。

- **操作继承语义**：调用方使用各类型自身定义的 `binaryBit`/`inheritMask` 做覆盖计算，禁止跨类型混用其他类型同码位定义；响应每项均含明确 `resourceTypeCode` 与十进制字符串 `binaryBit/inheritMask`。

`operation-permission/list` 响应 `data.items[]`，每项为 `OperationPermissionResp`（**字段精确对齐 DTO**，P1-4 修正；binaryBit/inheritMask 线格式已落地 **T-PERM-028**，T-FE-036 前置验收点）：

| 字段 | 类型 | 口径 |
|---|---|---|
| id | number | 操作 id |
| tenantId | number | 租户 id |
| resourceTypeCode | string | 操作定义所属资源类型（全局操作概念已退役，恒非空） |
| resourceTypeName | string\|null | 资源类型名称 |
| code | string | 操作编码（**注意：字段名是 `code` 而非 `operationCode`**，T-FE-036 mock/前端类型按 `code` 建模） |
| name | string | 操作名称（**不是 `operationName`**） |
| binaryBit | string | 操作位，**十进制字符串**（如 `"8"`）；63 位 bigint 列，Jackson 序列化为 string 防 >2^53 丢精度（T-PERM-028 已落地：DTO `@JsonSerialize(ToStringSerializer)`，全项目 bigint 序列化策略首例）；前端 BigInt 解析 |
| inheritMask | string | 继承掩码，**十进制字符串**（同 binaryBit 线格式）；covers 判定 `(effectiveBits & target.binaryBit) != 0` |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

前端来源链计算（frontend/permission-grant.md §3.5）依赖 binaryBit/inheritMask 做 BigInt 位运算。~~联调门禁~~ **T-PERM-028 已落地**：binaryBit/inheritMask 十进制字符串线格式（含 2^62 位值）已由 `OperationPermissionWireFormatTest` + `ResourceOperationKeyPgIT` 锁定，T-FE-018 可切真实接口；请求侧 Long 组件由 Jackson 宽容接受十进制字符串。


> operation_permission 全链归 type 能力包（capability-structure §8.0 裁决 2），但其对外契约与 resource-entity 业务键体系强耦合、在原册即同节成文，本节按原册保持完整（语义零变化优先，T-ACCESS-040 迁移裁决）；type-definition 契约见 §13。

### 12.2 服务与接口映射（service-config / resource-api-mapping）

| 接口                                         | 说明                              |
| -------------------------------------------- | --------------------------------- |
| `POST /api/access/service-config/list`         | 查询接入服务                      |
| `POST /api/access/service-config/detail`       | 查询服务详情                      |
| `POST /api/access/service-config/save`         | 幂等保存服务（serviceCode 形状 `^[A-Za-z0-9][A-Za-z0-9._-]*$` ≤128，与凭证签发侧同宽——T-PERM-070 外评闭合） |
| `POST /api/access/service-config/remove`       | 删除服务，支持批量                |
| `POST /api/access/service-config/sync`         | 全量同步服务接口，权限中心做 diff |
| `POST /api/access/service-config/apis`         | 查询服务接口映射列表（扁平）      |
| `POST /api/access/resource-api-mapping/list`   | 查询接口映射                      |
| `POST /api/access/resource-api-mapping/create` | 创建接口映射                      |
| `POST /api/access/resource-api-mapping/update` | 更新接口映射                      |
| `POST /api/access/resource-api-mapping/remove` | 删除接口映射，支持批量            |

**service-config / resource-api-mapping 契约要点（T-PERM-027 收口，2026-08-29）**：

- `list` 维持 `{}` 全量返回（设计定案：服务登记数量有界——租户内微服务个数，页面左栏目录面板本地过滤，无分页参数与分页响应）；门禁 SERVICE:VIEW 类型级。
- `save` 幂等（`{serviceCode, name, basePath?, description?, status?, extra?}`，按 `uk_service_config(tenant_id, service_code)` 定位，null 字段不更新）；`extra.syncTypes` 结构校验见 §19.9。`ServiceConfigResp` 含 `updatedAt`（保存与 FULL 同步回写 basePath 时刷新；`lastSyncedAt` 不设——无现成列且聚合推导语义模糊，登记不做）。
- `remove` 级联清理（设计定案）：同事务软删该服务**全部** API 映射（含 MANUAL 维护来源——服务已删则其路由不再存在，映射即死路径）+ 该服务 SERVICE_SYNC 自动维护的孤立 API 资源（FULL diff 同清理边界，§19.8；被其他服务跨服务手工映射引用的资源保留），事务提交后广播 Gateway 本地快照失效（受影响 serviceCodes）；整批失败整批不变更。
- `sync` 仅接受 `syncMode=FULL`（§19.8）：DTO 校验层 `@Pattern("FULL")` 拒绝其他值（`MethodArgumentNotValidException` → HTTP 400，body `code=90001` 参数校验失败），增量策略已删除（全仓零生产调用）。门禁 SERVICE:SYNC_INTERFACE 实例级（serviceCode）。
- `apis` 与 `resource-api-mapping/list` 返回的 `ApiMappingResp` 含关联资源业务字段 `resourceCode/resourceName/resourceTypeCode/maintainSource`（批量补全；资源已软删时为 null，前端回退展示内部 `resourceEntityId`）——`apis` 实现委托 `list`（同层复用，门禁与补全单点）。`list` 门禁（补齐）：请求带 `serviceCode` 按该服务实例 VIEW 校验；不带（管理全量列表）类型级 VIEW + 结果按服务维裁剪（拒绝服务的映射不出现在结果中）。
- `resource-api-mapping/create`/`update` 仍以内部 `resourceId` 绑定资源（§12.5 单条响应，§12 定案不随业务键切换）；前端资源选择器已随 **T-PERM-028** 落地（类型下拉 + 资源树选择，选中取节点内部 id 提交，数据源 `resource-entity/tree`），裸数字输入形态已删除。
- 权限门禁：读 SERVICE:VIEW（list/detail/apis、mapping list）；写 save/remove = SERVICE:MANAGE、sync = SERVICE:SYNC_INTERFACE、映射 create/update/remove = SERVICE:MANAGE_API_MAPPING（批量 remove 按映射行 serviceCode 批量校验）。SERVICE:VIEW/MANAGE/SYNC_INTERFACE 已补入空库 bootstrap 固定图（死锁防护=持有解锁首管理员页面读写，MANAGE_API_MAPPING 与 DOMAIN:VIEW 先例；三条均类型级 scopeAll 且**不可转授**——业务门禁统一口径，转授链仅 API:ACCESS，首管理员不能把 SERVICE 权限授予其他角色）。

### 12.3 资源依赖只读查询（/api/access/resource-dependency/*）

管理写路由 create/update/remove/batch-sync 已移除，管理台只读；所属服务使用 §19.10 manifest 发布声明。关闭路由不等于已迁移旧数据，071 的保全迁移与其余交付以任务卡为准。

| 接口 | 请求 | 返回 |
|---|---|---|
| `POST /api/access/resource-dependency/list` | `{resourceEntityId?: long}` | `ItemsResp<ResourceDependencyResp>` |
| `POST /api/access/resource-dependency/graph` | 同 list | 同 list，扁平边列表，由前端建图 |
| `POST /api/access/resource-dependency/check` | 源/目标资源业务键（typeCode、code、可选 codeType） | `DependencyCycleCheckResp` |

所有读入口执行 DEPENDENCY:VIEW 类型级门禁。list 保持全量不分页，关键词过滤由前端完成；graph 不含角色来源解释，角色解释目标契约见下一节。check 缺失资源返回既有 RESOURCE_NOT_FOUND，自依赖返回 hasCycle=true。

ResourceDependencyResp 提供 id、tenantId、源/目标实体 ID 与业务编码/类型/名称、sourceOperationBits、requiredOperationBits、description、ownerServiceCode、maintainSource、createdAt/updatedAt。操作位沿字符串线格式保持 63 位精度，null 触发位为任意操作；前端按资源类型的操作定义拆解显示。聚合边 description 取诊断声明的描述，不代表全部来源。autoGrant 已从后端实体、响应、新 schema 与前端移除；历史开关仅随原表保全，不再决定新编译图是否生效。旧错误码 20048 退役且不复用。

#### 12.3.1 已采纳待实施：角色自动授权来源解释（073）

`POST /api/access/resource-dependency/explain`，管理入口，服务凭证不开放；固定图登记，服务端校验 DEPENDENCY:VIEW 类型级权限。输入 roleTypeCode、roleExternalId、可选 domainCode；可选 target（resourceTypeCode、resourceCode、codeType、operationCode、conditionId，其中无条件须明确 conditionId=null）；maxDepth 1..50 缺省 20，maxNodes 1..2000 缺省 500，maxEdges 1..10000 缺省 1000。

响应 `R<AutoGrantExplainResp>`：viewedAt、nodes、edges、totalNodeCount、totalEdgeCount、truncated、driftDetected。节点以完整逻辑事实键标识，包含资源/操作/条件、explicitSeed、desired、actualPermissionIds；边包含 fromNodeKey、toNodeKey、实际触发操作与声明引用。AUTO_DEP 缺失时 actualPermissionIds 为空而 desired=true；孤立存量 AUTO_DEP 以 desired=false 标识，不伪造来源。逻辑节点不能依赖 AUTO_DEP 主键。

`nodes[]` 元素为 `{nodeKey: string, fact: FactKey, explicitSeed: boolean, seedRefs: SeedRef[], desired: boolean, actualPermissionIds: long[]}`；FactKey/SeedRef 沿 §11.4.1，explain 不出现 PREVIEW_INLINE。nodeKey 是本次完整结果按 FactKey 元组排序分配的展示 ID（如 n1），仅在本响应内引用；节点身份始终来自 fact，不跨请求按 n1 合并。`edges[]` 为 `{fromNodeKey: string, toNodeKey: string, triggerOperationCode: string, declarationRefs: [{declarationId: long, declarationKey: string, sourceService: string}]}`，所有字段必填，引用数组排序去重。

有 target 时从目标反向遍历直接来源；无 target 时遍历全集为推导图与未被 desired 支持的 actual AUTO_DEP 节点的并集，显式根及孤立 actual 节点共同作为展示起点，按逻辑键排序后广度优先输出至 maxDepth/maxNodes。孤立 actual 节点也计入总数与预算，desired=false 且没有来源边，不伪造支持关系；只返回两个端点都已输出的边，再按起点/终点逻辑键排序取前 maxEdges 条，超限省略边也必须置 truncated。totalNodeCount/totalEdgeCount 是选定目标子图（无 target 为推导图与孤立 actual 节点的并集）的完整数量，不是已输出数量。actualPermissionIds 仅列对应 AUTO_DEP 行；显式 MANUAL 来源另由 seedRefs 表示。空数组表示无实际自动行，不能用 null 表示读取失败。

单次只读一致视图生成共享 DAG，源事实权限不表示用户当前必然 allowed。达到输出限额时显式截断，不能影响完整推导；界面选择节点按直接边逐段展开来源，不持久化或枚举所有完整路径。不存在跨请求冻结承诺；读取失败、角色不存在与权限不足沿正常错误信封处理。

### 12.4 旧依赖批量同步退役

`POST /api/access/resource-dependency/batch-sync` 不再注册 Controller 路由；替代通道为 §19.10 独立 MANIFEST FULL。旧维护来源和请求 DTO 不作为新发布输入，旧依赖仅按设计 §10.1 保全，不自动代服务发布。

### 12.5 resource-api-mapping 单条响应约定

- `create`、`update` 成功后响应 `data` 为**单条**映射对象（与列表项结构一致），至少包含映射主键 `id` 及 `serviceCode`、`httpMethod`、`pathPattern` 等关键字段，便于调用方无需再发 `list` 即可确认结果。
- T-PERM-027：`ApiMappingResp`（`list`/`service-config/apis`/`create`/`update` 共用）另含关联资源业务字段 `resourceCode/resourceName/resourceTypeCode/maintainSource`（资源已软删时为 null）。

## 13. type 能力（类型定义与操作权限）
### 13.1 type-definition（/api/access/type-definition/*）

| 接口                                    | 说明                 |
| --------------------------------------- | -------------------- |
| `POST /api/access/type-definition/list`   | 查询类型定义         |
| `POST /api/access/type-definition/detail` | 查询类型详情         |
| `POST /api/access/type-definition/create` | 创建类型             |
| `POST /api/access/type-definition/update` | 更新类型             |
| `POST /api/access/type-definition/remove` | 删除类型，支持批量   |

**type-definition 契约要点（T-PERM-023 收口，2026-08-28）**：

- `create`：`{typeKey, typeCode?, name, description?, sortOrder?, extra?, ownerRoleTypeCode?, ownerRoleExternalId?}`——`typeValue` 由服务端在 tenant+typeKey 内自动分配（全量行含软删行 max+1，软删不复用）；`typeCode` 留空按 `<TYPEKEY大写>_<typeValue>` 生成（如 `RESOURCE_TYPE_12`），显式提供时查重（重复 20049；DB 唯一索引对并发窗口与生成码被显式码抢占的场景兜底，同映射 20049）；`isSystem` 不可由 API 创建（固定 false，系统预置仅走租户初始化种子）。`ownerRole*` 仅 `typeKey=resource_type` 消费（T-PERM-062，2026-09-12 定案）：类型所有者角色业务键（与授权页同一套键），必须成对提供、缺省 `BASIC_ROLE/bootstrap-admin`；`roleTypeCode` 值域仅 `BASIC_ROLE` 功能角色——容器角色（ORG/POSITION/GROUP_ROLE）携带拒绝 **20044**（授权根是「该类型全部实例可转授」的类型级行，挂容器角色等于给全体容器成员发转授权，且种子行 20061 只读无逐行移除通道；2026-09-12 claude 外评定案）；所有者角色不存在（**20001**）/停用（**20003**）整单回滚，不存在「已建类型但无所有者」中间态。
- `list`：`{typeKey?, keyword?, pageNum?, pageSize?}` → 分页结构（§2.3）；`keyword` 匹配 name/typeCode（LIKE，大小写敏感），排序 `sort_order, id`；分页参数均不传 = 字典全量（上限 200，先例 `/api/access/role/list`），供下拉数据源消费。

**resource_type 类型所有权声明（T-PERM-052，2026-09-05 定案）**：`create`/`update` 的 `extra` 可携带类型级所有权声明，约定键 `managedMode`（`MANAGED`=缺省，管理面维护 / `SYNC`=外部同步维护）与 `syncSourceService`（`mode=SYNC` 时必填，须为已注册、未软删且 `status=1` 启用的服务——与运行时同步入口同规则（codex 外评 P2 对齐，仅查注册非空会保存出无人可写的锁死类型）；保留内部来源（如 `admin-service`）拒绝声明（运行时拒绝其冒充）；`type_key` 非 resource_type 携带此二键保存拒绝 20044）。规则：SYNC 类型归声明来源服务独占同步（§19.1 门禁），管理面 create/update/move/remove 只读（§12，20055）；声明有效值变更（含删键隐式切回 MANAGED——extra 为整串替换语义）：**系统预置类型（is_system=true）所有权声明钉死不可变更**（**20056** `TYPE_OWNERSHIP_CHANGE_CONFLICT`，2026-09-05 codex 外评定案——事实链路类型空行翻转后事实写入方照旧写即双 writer，无需并发）；自定义类型在类型下存在有效资源行时拒绝（无有效行才可改，2026-09-05 用户定案）；读取侧 extra 损坏按 MANAGED 处理（fail-closed）。外部服务接入流程：`service-config/save` 注册服务 → `type-definition/create` 建自有类型并声明 `managedMode=SYNC` + 来源 → 调 `resource-entity/sync|full-sync`。内部来源：`syncSourceService=access-service` 仅 is_system 预置类型可声明（豁免服务注册校验）——USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION 七类事实链路类型由种子声明（前四类行经用户/组织/菜单/角色管理自动维护；ADMIN_FILE 文件夹实例（code=sys_file.bucket_name）由 bootstrap 预置四文件夹+上传惰性登记产出，T-ADMIN-025；TYPE_DEFINITION 类型定义实例（code=`{typeKey}:{typeCode}` 复合业务键）由 type-definition 写路径同事务投影 + bootstrap 自愈补种产出，T-PERM-051；CONDITION 管理页条件实例（code=条件 code，租户内唯一）由条件写路径同事务投影 + bootstrap 自愈补种产出（仅 source=MANAGED——INLINE 内联条件不投影，无资源身份消费者，T-PERM-048 定案⑤）；外部同步与管理面资源 CRUD 均拒绝，20055 message 指向事实链路管理入口）；来源编码禁止首尾空白（校验按 trim、运行时按原值精确匹配，空白会造出无人可同步的锁死类型，保存拒绝 20044）；已知键显式 null 保存拒绝 20044（与「清除声明=删除键」语义歧义，fail-closed）；未知键开放不视为声明（extra 是通用扩展位，拼错键=无声明按缺省 MANAGED——codex 外评定案）；`API` 类型禁止经本接口声明 SYNC（T-PERM-069，2026-09-18 Q-008「仅 API 收紧」定案：所有权由种子钉死 SYNC+access-service——唯一事实入口=service-config 接口声明通道（§19.8）+bootstrap 固定图，管理面资源 CRUD 20055、外部 resource-entity/sync 一律拒绝；SERVICE 维持 MANAGED——新 SERVICE 行唯一通道=管理面手工建行，做按服务实例级授权目标行，收紧即零 writer）。`remove`：类型下存在有效资源行时不可删除（20056，与声明变更守卫同款——软删类型会让其行成为外部源与管理面都无法触达的永久孤儿；评审批次补齐）。**T-PERM-050（2026-09-09 定案级联）**：`remove` 同事务级联软删被删 resource_type 的全部有效操作定义行（含创建联动预置的 CRUD 四操作位，对称于创建语义——根治「删类型留 4 条永久孤儿操作行、列表过滤后不可见且无清理入口」）与该类型下有效授权行（正常流仅剩 scope_all 类型级行：资源行已被行数守卫拒绝、实例级授权随资源删除级联；防御性含引用已软删资源行的残留实例行），markRoles 提交后失效受影响角色快照、OPERATION_PERMISSIONS_BY_TYPE 按被删类型集合 per-type 失效（T-PERM-047 终态复用）；级联面限定 `type_key=resource_type`（type_value 仅 tenant+type_key 内唯一，user_type/role_type 同值删除不得误伤 resource_type 空间）。存量孤儿订正语句登记 rebuild-runbook。**T-PERM-056（2026-09-09 用户定案删除保护）**：`remove` 对主体类型补引用面守卫——user_type/role_type 下存在有效 `abstract_user`/`abstract_role` 行时整批拒绝（20056，message 列冲突 typeCode；管理员须先删/迁走该类型用户/角色再删类型——用户/角色是业务主体数据不级联，对齐 resource_entity 面=守卫先例而非操作位/投影=级联先例）。并发语义：role_type 面与角色写入口共持 ABSTRACT_ROLE 树写锁并锁内重读（管理面 updateRole/moveRole 与角色 sync/full-sync 均持同锁；自定义 role_type 角色行的唯一**创建**入口是角色 sync/full-sync——管理面 createRole 经 RoleType 枚举校验只接受种子类型），闭合「守卫查零行→并发建该类型角色→删除落库」交错；user_type 面用户写入口无锁可复用，为 best-effort 守卫（对齐 T-PERM-050 级联并发先例），极窄交错残留由 typeValue 软删不复用兜底（孤儿 user_type 值永不撞新类型，后果同存量孤儿=列表反解缺项，无越权通道），存量孤儿检测语句登记 rebuild-runbook。门禁（2026-09-03 放宽定案，用户决策）：类型级 TYPE_DEFINITION:VIEW **或任一实例级 VIEW** 均可查询——与登录权限串投影口径对齐（权限串全集含实例级授权；此前仅认类型级，出现「前端 hasPerms 探查通过、后端拒绝」的口径不一致，T-FE-018 评审发现）；`detail` 维持按目标实例校验 VIEW、写操作维持类型级/实例级 MANAGE 不变。**T-PERM-051（2026-09-07 收口）**：实例业务键统一为复合键 `{typeKey}:{typeCode}`（typeCode 仅 tenant+type_key 内唯一，种子 user_type 与 resource_type 均有 USER/SERVICE 同名行，裸 code 跨族撞 uk_resource_entity，2026-09-05 定案）——四处门禁消费方全部迁移：`list`/`count` 实例判定、`detail`/`update` 编码轨（原 type_definition.id 字符串系 ID 空间错位）、`remove` 批量编码轨（原 id 直传实体轨，同款错位）；投影行由写路径同事务维护（create 联动 / name 变更同步 / 软删级联投影行与投影行下授权行，deleteResources 同款级联语义）+ bootstrap 自愈补种存量行（幂等，2026-09-07 用户定案启动自愈对齐 T-ADMIN-025 先例；runbook 登记订正语句兜底）；授权页 TYPE_DEFINITION 实例按资源选择器既有模式呈现（类型已声明 SYNC+access-service，资源管理面只读 20055）。全拒判定按去重复合键集比较 fail-closed 拒绝（跨 type_key 重码曾致 fail-open，2026-09-03 修复；复合键行级唯一后去重语义不变，2026-09-07 随 T-PERM-051 迁移）。

### 13.2 operation-permission（/api/access/operation-permission/*）

| 接口                                          | 说明               |
| --------------------------------------------- | ------------------ |
| `POST /api/access/operation-permission/list`    | 查询操作权限       |
| `POST /api/access/operation-permission/detail`  | 查询操作详情       |
| `POST /api/access/operation-permission/create`  | 创建操作           |
| `POST /api/access/operation-permission/update`  | 更新操作           |
| `POST /api/access/operation-permission/remove`  | 删除操作，支持批量 |

> 操作权限端点的业务键定位、类型查询参数、响应字段与授权根联动契约与 resource-entity 同节成文，见 §12.1（原册 §12 整节迁移，语义零变化）。

## 14. domain 能力（业务域与域配置）
### 14.1 biz-domain（/api/access/biz-domain/*）

| 接口                                    | 说明                 |
| --------------------------------------- | -------------------- |
| `POST /api/access/biz-domain/list`        | 查询业务域           |
| `POST /api/access/biz-domain/detail`      | 查询业务域详情       |
| `POST /api/access/biz-domain/create`      | 创建业务域           |
| `POST /api/access/biz-domain/update`      | 更新业务域           |
| `POST /api/access/biz-domain/remove`      | 删除业务域，支持批量 |

**biz-domain 契约要点（T-PERM-026 收口，2026-08-29）**：

- `detail`/`update` 切业务键 `code` 定位（`uk_biz_domain(tenant_id, code)`，软删行不占用；detail/update 的定位键由内部主键退役——`remove` 仍收 `{ids}` 批量软删、`BizDomainResp` 保留 `id`，type-definition 先例）：`detail` 请求 `{domainCode}`，未知编码返回 `data=null` 不抛错（role detail 先例）；`update` 请求 `{domainCode, name?, description?}`——`code` 不可改（改 code 等于新建新域），`name/description` 为 null 表示不更新、`description` 传空串表示显式清空；未命中 **20017** `DOMAIN_NOT_FOUND`。
- `list`：`{keyword?, pageNum?, pageSize?}` → 分页结构（§2.3）；`keyword` 匹配 code/name/description（LIKE，大小写敏感），排序 `code, id`；分页参数均不传 = 字典全量（上限 200，先例 `/api/access/role/list`、`/system-config/list`）。门禁 DOMAIN:VIEW 类型级（与 detail 同级，先于查询避免存在性泄露）。
- `create`：`{code, name, description?, global?}`——编码重复拒绝 **20052** `DOMAIN_CODE_DUPLICATE`（预查 + `uk_biz_domain` 唯一索引 DIVE 兜底同映射，TypeDefinition 先例）；`global` 可选默认 false（null 同 false），**T-PERM-046（2026-09-09）**：true=创建全局域，每租户仅一个——已存在时预查拒绝 **20057** `DOMAIN_GLOBAL_EXISTS`，并发窗口由 `uk_biz_domain_global` 唯一索引兜底同映射（约束名带引号精确匹配，防 `uk_biz_domain` 前缀误吞）；`global` 创建后不可变（update 请求体不含此字段，换轨=新建域）；全局域不可删保护既有（20051）；全局域范围=CLASSIFY 声明或动态补集（有声明按声明、无声明退「未被其他域认领」补集，用户定案 2026-09-09，DomainClassifyService 消费）。前端 create 表单加「全局域」开关（已存在全局域时禁用+提示，预判失败由 20057 兜底）。
- `remove` 删除保护（**20051** `DOMAIN_DELETE_CONFLICT`，message 区分原因）：全局域（`global=true`，每租户唯一）不可删；域下仍存在有效 `domain_config` 行时引用检查拒删（schema「删除前检查引用」落地，需先删除该域下配置）；整批校验失败则整批不变更。
- `BizDomainResp` 含 `global` 字段（是否全局域，前端预判禁删）；`create`/`update` 请求体字段长度与格式校验对齐 schema 列宽（code 64 大写字母开头+大写/数字/下划线、name 128、description 512）。
- 权限门禁：读（list/detail）`DOMAIN:VIEW`；写（create/update/remove）`SYSTEM_CONFIG:MANAGE`。DOMAIN:VIEW 已补入空库 bootstrap 固定图（无授予起点死锁防护，OPERATION_LOG:VIEW 先例）。

### 14.2 domain-config（/api/access/domain-config/*）

| 接口                                            | 说明                   |
| ----------------------------------------------- | ---------------------- |
| `POST /api/access/domain-config/list`             | 查询域配置             |
| `POST /api/access/domain-config/detail`           | 查询单条域配置         |
| `POST /api/access/domain-config/save`             | 幂等保存域配置         |
| `POST /api/access/domain-config/remove`           | 删除域配置             |

**domain-config 契约要点（T-PERM-026 收口，2026-08-29）**：

- `save`（upsert）：`{domainCode, configType, extra}`——按 `domainCode+configType` 查存在则 update `extra`、不存在则 insert（新建/编辑统一走 save）；`configType` 白名单仅接受 `SUB_PERM/CLASSIFY`（历史设想类型 SCOPE/RELATION/BINDING 未实现，写入校验拒绝）；`extra` 为 JSON 字符串，写入前经 `JsonValidationUtils` 语法校验（非法 JSON fail-closed，统一异常通道返回 `code=400` 参数错误，system-config `configValue` 同款）；未知 domainCode 拒绝 **20017** `DOMAIN_NOT_FOUND`。
- `detail`：`{domainCode, configType}` 业务键二元组，未命中 `data=null` 不抛错。
- `list`：`{domainCode?}` 过滤（不传全量），量小不分页（每域至多 SUB_PERM/CLASSIFY 两条）。
- `extra` JSONB↔String 映射已确认（`JsonbStringTypeHandler`，BizDomainConfigPgIT 真库锁定）：读出为 DB 规范化 JSON 文本，语义等价、可直接再提交。
- `extra` 字段名口径（save 仅校验 JSON 语法不校验 schema，字段名以消费方为准，PgIT 跨层锁锁定）：CLASSIFY 用 `{"resourceTypeCodes":["ORG","USER"]}`（`DomainClassifyService` 消费）；SUB_PERM 用 `{"allowed":[{"parent_type":"USER","child_types":["POSITION"]}]}`（授权链路 `assertSubPermissionAllowed` 消费，`*` 通配）。
- 权限门禁：读（list/detail）`SYSTEM_CONFIG:VIEW`；写（save/remove）`SYSTEM_CONFIG:MANAGE`。
- 并发语义（T-PERM-046 收口，2026-09-09）：①save 的 check-then-insert 并发双插窗口由部分唯一索引 `uk_domain_config(tenant_id, biz_domain_id, config_type) WHERE delete_flag=0` 兜底，违例映射 **20058** `DOMAIN_CONFIG_CONCURRENT_CONFLICT`（提示重试——后到者重试时另一事务已提交，转 update 分支）；②biz-domain remove 的引用检查（FOR UPDATE 域行锁）与 save 的域解析（同款域行锁）双向互斥：remove 提交后 save 解析不到软删域（20017），save 持锁插入的配置会被 remove 引用检查看到（20051 拒删）——孤儿配置窗口闭合。
- 全局域 CLASSIFY 声明生效（T-PERM-046 用户定案 2026-09-09）：save 目标域为全局域时 CLASSIFY 配置有真实语义（全局域实际范围=声明集，声明即收窄覆盖动态补集；GLOBAL_PLUS 隐式段同源对齐）；SUB_PERM 挂全局域继续有效（未被认领类型经反查兜底读全局域策略）。

## 15. rule 能力（权限条件与冲突规则）
### 15.1 permission-condition（/api/access/permission-condition/*）

| 接口                                            | 说明                   |
| ----------------------------------------------- | ---------------------- |
| `POST /api/access/permission-condition/list`      | 查询权限条件（**无读取门禁，2026-08-08 产品确认：条件规则全租户开放、非敏感**） |
| `POST /api/access/permission-condition/detail`    | 查询条件详情（**同上：无读取门禁**；业务键 code 定位，查不到 20006） |
| `POST /api/access/permission-condition/create`    | 创建条件               |
| `POST /api/access/permission-condition/update`    | 更新条件（业务键 code 定位） |
| `POST /api/access/permission-condition/remove`    | 按条件编码批量删除     |

**permission-condition 契约要点（T-PERM-029 收口，2026-08-30）**：

- **业务键**：管理端点 `detail`/`update`/`remove` 一律以 `code` 定位（`uk_permission_condition(tenant_id, code) WHERE delete_flag=0`，从内部主键 id/conditionId 切换；管理端点请求体不再使用内部 id——id 仅见于 Resp、授权链路 `role_resource_permission.condition_id` 引用及原 explain 排查明细——该端点已随 T-PERM-059 删除，2026-09-10）。`detail` 请求 `{conditionCode}`（ConditionDetailReq）；`update` 请求 `{code, name?, conditionRules?, enabled?, gatewayEvaluable?, description?}`（code 为定位键不可改，null 字段不更新，name≤128/description≤512 列宽校验）；`remove` 请求 `{codes:[...]}`（ConditionRemoveReq，元素 1-64 字符非空白，批量软删）。
- **detail 收紧**：查不到抛 **20006** `CONDITION_NOT_FOUND`（原 `data:null` 宽松语义删除，对齐 resource-entity/detail 收紧定案与授权链路 apply-grant-plan 未知 conditionCode 同码）。
- **list 全量不分页**（设计定案）：返回全量 `ItemsResp<ConditionResp>`——条件模板数量有界（租户内几十个量级，非流水表），与 domain-config/service-config「量小不分页」同款；keyword/enabled 过滤由前端本地完成（前端设计文档 §8 🔧3 登记的 ConditionListReq 分页方案据此反转）。**双轨制（T-PERM-048 收口 2026-09-11）**：list 请求体 `{includeInline?}`（ConditionListReq）——缺省/false 只返回 source=MANAGED 管理页条件（权限条件页口径：内联条件在管理页查不到也不能管理）；`includeInline=true` 含 INLINE（授权页回显内联条件名称/规则摘要用）。
- **ConditionResp**：`{id, tenantId, code, name, conditionRules, enabled, gatewayEvaluable, source, description, createdAt, updatedAt}`——`updatedAt` 为 T-PERM-029 补齐、`source` 为 T-PERM-048 补齐（`MANAGED`/`INLINE`，值域 schema CHECK 焊死）；`conditionRules` 结构 `{logic, items[]}`（4 预置类型 DATE_RANGE/TIME_RANGE/IP_WHITELIST/IP_BLACKLIST），语义等价可直接再提交——**注意来源差异**：list/detail 为 JSONB 回读的 DB 规范化文本，create/update 直接返回本次最终接受的规则文本（未做写后反查，调用方提交的空白/键序原样保留）。
- **条件双轨制（T-PERM-048 收口，2026-09-11 五项定案见 decision-registry 同日行）**：条件分两类、管理边界互斥——①**MANAGED 管理页条件**：仅权限条件页 CRUD（create 固定 source=MANAGED，不接受请求指定来源），授权页只能 conditionCode 引用；有 resource_entity(CONDITION) 实例投影（code=条件 code，status 镜像 enabled），可被实例级授权；②**INLINE 内联条件**：仅授权页随 apply-grant-plan 产生（§11.4 inlineCondition），1:1 属于创建它的授权记录不可共享（code 自动生成 `inline-` 前缀、enabled 恒 true、**不建投影行**——无资源身份消费者），授权行删除/换绑时引用归零同事务回收。管理面防线（20060 `CONDITION_INLINE_NOT_MANAGEABLE`）：update/remove 遇 INLINE 行整批拒绝、detail 拒绝、apply-grant-plan 的 conditionCode 引用轨遇 INLINE 行拒绝（1:1 的 API 焊点——引用轨值域恒 MANAGED）。
- **权限门禁（T-PERM-048 定案④升级实例级）**：读（list/detail）无门禁（2026-08-08 产品确认：条件规则全租户开放、非敏感）；写 create = CONDITION:CREATE **类型级**（scope_all）维持；update/remove 升**实例级** CONDITION:UPDATE@{code} / DELETE@{code}（经 resource_entity(CONDITION).code=条件 code 投影解析，业务编码轨同款——原参照系 USER:MANAGE 已随 T-ACCESS-034 退役，门禁细码化为 UPDATE/DELETE；投影行缺失 fail-closed）——bootstrap 固定图与存量授权全为 scope_all 三档，passesScopeAll 天然覆盖全部实例，升级**零破坏**（实例级授权此前配不进，不存在会被降权的存量行）。remove 实例级全有或全无（getDeniedResourceCodes 批量，任一 code 拒绝整批不变更）。CONDITION 的 CREATE/UPDATE/DELETE 三档独立，非 MANAGE 聚合（与 RESOURCE/OPERATION 的 CREATE+MANAGE 两档不同）。
- **删除引用守卫（T-PERM-048 定案③，20059 `CONDITION_REFERENCED_BY_GRANTS`）**：remove 时两类引用任一命中**整批拒绝**（message 携带冲突 code 清单，T-PERM-056 删除保护先例）——① `role_resource_permission.condition_id` 挂靠引用（挂该条件的授权行评估 fail-close，静默删除会让授权「静默失效」）；② 投影行下实例授权引用（CONDITION:UPDATE/DELETE@code 等实例级授权行悬空防护）。零引用才放行；放行删除时条件行+投影行同事务软删。弃 TYPE_DEFINITION 级联软删方案（授权资产被删条件连带消失比要求显式解绑更危险）。
- **gatewayEvaluable 联合校验**（T-PERM-017 C2.5）：create/update 取「最终状态」校验——只切 flag 不改 rules 用 DB 老 rules、同改用新 rules、已 true 改 rules 用新 rules；`gatewayEvaluable=true` 要求 `logic ∈ {AND, OR}`（缺省 AND）且 `items[].type` 全在 `ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES` 白名单，不通过 20031 `CONDITION_RULES_INVALID`。update/delete 后经 `@PermissionChange` 反查受影响 serviceCodes 广播 Gateway 接口快照失效。
- **remove 幂等语义**：请求中不存在或已删除的 code 静默跳过、重复 code 去重（幽灵键幂等语义与 resource-entity/remove 一致；拒绝语义为实例级全有或全无——无 CONDITION:DELETE 授权整批拒绝（T-PERM-048 升实例级），被引用时守卫 20059 整批拒绝，resource-entity 为实例级删有权部分）；remove 响应 Void 无行数，调用方以事后查询核对。
- **create 重复 code**：无预查友好码，由 uk 兜底拒绝（系统错误通道），与 resource/type-def create 同款。

### 15.2 conflict-rule（/api/access/conflict-rule/*）

| 接口                                            | 说明                   |
| ----------------------------------------------- | ---------------------- |
| `POST /api/access/conflict-rule/list`             | 查询冲突规则           |
| `POST /api/access/conflict-rule/detail`           | 查询冲突规则详情       |
| `POST /api/access/conflict-rule/create`           | 创建冲突规则           |
| `POST /api/access/conflict-rule/update`           | 更新冲突规则           |
| `POST /api/access/conflict-rule/remove`           | 删除冲突规则，支持批量 |
| `POST /api/access/conflict-rule/detect`           | 冲突检测               |

**conflict-rule 契约要点（T-PERM-030 收口，2026-08-30）**：

- **定位键维持内部主键 id**（评估定案）：冲突规则无业务键——`type + 对象对 + resourceTypeValue` 为复合语义身份，无单列 code 可切（与 resource/operation/condition 不同）；`detail` 请求 `{id}`（IdReq）、`update` 请求 `{id, conflictType, ...}`、`remove` 请求 `{ids:[...]}`（IdsReq）。Resp 含 id/tenantId 透出（前端名称映射按 id 关联 role/operation 列表建立）。
- **detail 收紧**：查不到抛 **20020** `CONFLICT_RULE_NOT_FOUND`（原 `data:null` 宽松语义删除，对齐 resource-entity/condition 两先例；update 侧既有同码，先解析后门禁——未知 id 优先 20020 且零副作用）。
- **list 全量不分页**：`{}` 返回全量 `ItemsResp<ConflictRuleResp>`（量小非流水表，对齐 condition/domain-config 定案）；类型/关键词过滤由前端本地完成。
- **门禁四档类型级**（2026-08-30 口径，经决策）：读 list/detail/detect = CONFLICT_RULE:VIEW、写 create/update/remove = CONFLICT_RULE:CREATE/UPDATE/DELETE，全部类型级（scope_all）——CONFLICT_RULE 无 resource_entity 实例投影，实例级授权无从配置，原「编码轨传内部 id」的实例级声称系 ID 空间错位已废弃（原与 CONDITION 同口径收窄；CONDITION 已随 T-PERM-048 落地实例投影与实例级写门禁，CONFLICT_RULE 维持类型级——投影如需另立任务）。remove 类型级全有或全无，幽灵 id 静默跳过（幂等，对齐 resource-entity/condition remove）。
- **update 全量覆盖语义**（PUT）：`conflictType` 必填，按类型字段集全量覆盖、对侧字段强制 null（UpdateEntity 显式写列）；PERM_MUTEX 下 `resourceTypeValue` 显式传（null=清空"全部"）；对象对写库前规范化 first&lt;second（对齐 uk_conflict_rule_perm/role 唯一索引）；审计 updatedBy/updatedAt 随写。等价规则（同类型+同对象对双向+同 rtv）拒绝 **20032** `CONFLICT_RULE_DUPLICATE`（业务层预查 + DB 唯一约束兜底，NULLS NOT DISTINCT 覆盖 NULL 全局规则）。
- **detect**（T-PERM-063 扩展双形态二选一）：**操作权限对**（`firstOperationPermissionId` + `secondOperationPermissionId`，PERM_MUTEX 场景——双向匹配（规则 (A,B) 对请求 (A,B)/(B,A) 均命中）；`resourceTypeValue` 可空过滤，`resource_type_value IS NULL` 的全局规则始终参与（schema「NULL=所有」语义，SQL 层保证））；**角色对**（`firstAbstractRoleId` + `secondAbstractRoleId`，ROLE_MUTEX 场景——立规前预检：回传当前有效角色集同时含两角色的用户清单，两端相同拒绝 VALIDATION_FAILED）。形态完整性 fail-closed：任一对字段出现即要求配对字段同现（半传拒绝），且两对恰现其一——违反拒绝 VALIDATION_FAILED。响应 `{conflictDetected, matchedRules[], conflictedUserIds[]}`——角色对形态 `conflictDetected` 以 `conflictedUserIds` 非空判定（非空 = create/update 该规则将被 20063 存量守卫拒绝），操作权限对形态 `conflictedUserIds` 恒空列表、`conflictDetected` 以 matchedRules 非空判定；纯查询无副作用。
- **角色互斥三面守卫（T-PERM-063 落地，2026-09-12 三项用户拍板见 registry 同日行）**：①**授予守卫**——`user-role/assign`、`batch-assign` 事务内校验「授予后有效角色集」（现有效 ∪ 本批新增，仅计启用且有效期覆盖当前时刻的角色——与运行时 `filterRoleMutex` 判定集合同源；同批双端由集合语义覆盖），命中互斥对整批原子拒绝 **20062** `ROLE_MUTEX_ASSIGN_CONFLICT`；规则 DB 直查（不经 ROLE_MUTEX_RULE 缓存），新建规则即刻生效；并发双开两笔授予的窄竞态窗口接受（运行时双删兜底 fail-closed）。①′**sync 通道面守卫（T-PERM-064 补全）**——`user-role/sync`、`full-sync` 的 BIND 分支同款校验，冲突 item 按通道语义返回 `NON_RETRYABLE` + `reason=ROLE_MUTEX_CONFLICT`（见 §19.4）；规则面并拒绝 ORG/POSITION 角色对（VALIDATION_FAILED——结构角色由本地投影通道维护，规则面拒绝后投影通道结构性造不出违规；角色行缺失维持惰性规则语义）。②**存量守卫**——create/update 的 ROLE_MUTEX 分支写入前检查存量双持（经有效角色解析收敛：有效期窗口/启用态/组角色展开同源，候选经 `findUserIdsByEffectiveRoles` 三路反查含组角色间接持有），非空拒绝 **20063** `ROLE_MUTEX_EXISTING_HOLDERS`（message 含用户 id 清单截断上限 20；同对重写幂等——系统干净时自然放行）；PERM_MUTEX 分支与 remove 不适用。③**运行时可观测**——快照构建 `filterRoleMutex` 双删命中记 CONFLICT_DETECTED 操作日志（对齐 PERM_MUTEX 先例；每「租户×用户×规则对」每 JVM 1 小时至多一条，Caffeine 有界去重，多实例独立记账；仅提交期异常回滚去重标记，异步落库失败由 AsyncUncaughtExceptionHandler 兜底）。
- **ConflictRuleResp**：`{id, tenantId, conflictType, firstOperationPermissionId, secondOperationPermissionId, resourceTypeValue, firstAbstractRoleId, secondAbstractRoleId, description, createdAt, updatedAt}`——`updatedAt` 为本次补齐（entity 列本就存在）；description ≤512 列宽校验（create/update）。
- **bootstrap 固定图**：CONFLICT_RULE:VIEW/CREATE/UPDATE/DELETE 四条类型级不可转授随本批补入（空库无授予起点死锁防护，同 DOMAIN/SERVICE 先例）；顺带补 CONDITION:CREATE/UPDATE/DELETE 三条（T-PERM-029 遗漏的同款缺口，读取无门禁故无 VIEW 条目）。

## 16. audit 能力（审计日志）
### 16.1 端点清单（/api/access/log/*）

| 接口                                                   | 说明                                                   |
| ------------------------------------------------------ | ------------------------------------------------------ |
| `POST /api/access/log/operation/list`              | 操作日志（路径以 LogQueryController 实现为准；历史误写已随 T-ACCESS-007 修正） |
| `POST /api/access/log/operation/action-options`    | 操作日志 action 字典（T-PERM-025 新增） |
| `POST /api/access/log/change/list`                  | 权限变更日志                                           |

> **permission-view 七端点已删除（T-PERM-059，2026-09-10 删除重设计定案）**：`effective-permissions` / `resource-users` / `role-permissions` / `effective-roles` / `resource-tree` / `explain` / `recent-changes` 整体退役（前端排查页同批删除，新形态待重做另立任务）；`/api/access/auth/query-permission-tree`（零外部消费）同批退役。登录权限串 `effective-permission-codes` 不在删除面，仍由 `PermissionViewController` 提供。

### 16.2 operation-log 契约要点（T-PERM-025 收口，2026-08-28）
**operation-log 契约要点（T-PERM-025 收口，2026-08-28）**：

- `list`：`{module?, action?, operatorId?, since?, until?, targetType?, pageNum, pageSize}` → 分页结构（§2.3，排序 `created_at DESC`）；module/action/targetType **精确匹配**（等值索引友好）；`since`/`until` 为创建时间闭区间（ISO 无偏移墙钟；后端 LocalDateTime 语义为 UTC 墙钟——全链路 UTC（project-rules §7.4），前端提交数字与表格原样展示对齐）。无 detail 接口——`OperationLogResp` 已含全部字段，详情由前端抽屉展示。
- `action-options`：`{module?}` → `ItemsResp<String>`——返回 operation_log 当前实际存在的 action 去重集合（字典序），供筛选下拉动态拉取；返回实际存在值而非维护端枚举（action 由 `@OperationLog` 注解开放增长，避免双轨漂移）。
- 权限门禁：list 与 action-options 需独立 `OPERATION_LOG:VIEW`（**审计分离**，2026-08-28 设计定案——不再复用 `SYSTEM_CONFIG:VIEW`；资源类型 OPERATION_LOG=30 权威 DDL 种子，bootstrap 固定图已授予管理角色）。排查视图端点族（permission-view 七端点）已删除（T-PERM-059，2026-09-10）。

### 16.3 permission-change-log 契约要点（T-PERM-032 收口，2026-08-29）
**permission-change-log 契约要点（T-PERM-032 收口，2026-08-29）**：

- 端点为 `POST /api/access/log/change/list`（原 §16 表格误写 `/api/access/permission-change-log/list`，已随 T-ACCESS-007 评审修正，此处补记）；无独立 detail——`ChangeLogResp` 含全字段（含 diffSnapshot），前端抽屉展示。
- 筛选全集（维度对齐 schema 索引，2026-08-29 设计定案）：`entityType/entityId`（实体索引）、`eventType`（diff_snapshot.eventType 表达式索引，单选）、`affectedUserId/affectedRoleId`（affected_*_ids GIN 包含匹配）、`since/until`（created_at 闭区间，时间索引；ISO 无偏移墙钟字符串，同操作日志数字对齐口径）、`changeSource`（MANUAL/SERVICE_SYNC 精确匹配，低基数无索引）。服务端分页（条件组设计源自已删的 recent-changes 端点，T-PERM-059 后为变更日志独立条件面）。
- `ChangeLogResp` 暴露 `createdBy`（表 created_by，抽象用户 ID；名称解析归前端展示层）。
- 权限门禁：独立 `PERMISSION_CHANGE_LOG:VIEW`（**审计分离**，2026-08-29 设计定案，对齐 OPERATION_LOG 先例；资源类型 PERMISSION_CHANGE_LOG=31 权威 DDL 种子，bootstrap 固定图已授予管理角色）。
- `diff_snapshot.eventType` 增补第 7 枚举 `ROLE_BATCH_DELETE`（批量删除角色的聚合事件：entityId=0 + operation=BATCH_DELETE，items[] 逐角色列出；本节 diff_snapshot 规范同步），`operation` 列含 `BATCH_DELETE/BATCH_REMOVE`（批量聚合行专用，schema 注释已修正）。

### 16.4 diff_snapshot 轻量规范

`permission_change_log.diff_snapshot` 用于保存可展示、可检索的结构化变更摘要。它只描述本次写操作直接改变了什么，不负责计算用户最终有效权限是否发生变化。

角色权限变更：

```json
{
  "eventType": "ROLE_PERMISSION_CHANGE",
  "items": [
    {
      "changeType": "REMOVE",
      "permission": {
        "domainCode": "example",
        "resourceTypeCode": "REPORT",
        "resourceCode": "report:sales",
        "codeType": "default",
        "operationCode": "DATA_EDIT",
        "scopeMode": "INSTANCE"
      },
      "role": {
        "roleTypeCode": "BASIC_ROLE",
        "roleExternalId": "role_report_editor",
        "roleName": "报表编辑员"
      }
    }
  ]
}
```

用户角色变更：

```json
{
  "eventType": "USER_ROLE_CHANGE",
  "items": [
    {
      "changeType": "REMOVE",
      "role": {
        "roleTypeCode": "BASIC_ROLE",
        "roleExternalId": "role_report_editor",
        "roleName": "报表编辑员"
      }
    }
  ]
}
```

资源、角色或条件状态变更：

```json
{
  "eventType": "RESOURCE_STATUS_CHANGE",
  "items": [
    {
      "changeType": "UPDATE",
      "resource": {
        "domainCode": "example",
        "resourceTypeCode": "REPORT",
        "resourceCode": "report:sales",
        "codeType": "default"
      },
      "before": {
        "status": 1
      },
      "after": {
        "status": 0
      }
    }
  ]
}
```

`diff_snapshot` 字段约束：

- 顶层必须包含 `eventType` 和 `items[]`。
- `eventType` 固定枚举：`USER_ROLE_CHANGE`、`ROLE_PERMISSION_CHANGE`、`ROLE_STATUS_CHANGE`、`RESOURCE_STATUS_CHANGE`、`CONDITION_CHANGE`、`RESOURCE_DEPENDENCY_CHANGE`、`ROLE_BATCH_DELETE`（批量删除角色的聚合事件，entityId=0 + operation=BATCH_DELETE，T-PERM-032 增补——原 6 枚举无一语义覆盖批量删除聚合；`GROUP_ROLE_CHANGE` 随 T-PERM-043 extra-roles 写入口删除移除——该事件类型自登记起无任何生产方）。
- `items[].changeType` 固定枚举：`ADD`、`REMOVE`、`UPDATE`。
- （历史）原 `recent-changes` 响应的 `impactLevel` 枚举（DIRECT/POSSIBLE）随端点删除退役（T-PERM-059，2026-09-10）；diff_snapshot 本身不含 impactLevel。
- 权限项使用稳定业务键：`domainCode + resourceTypeCode + resourceCode + codeType + operationCode + scopeMode`。
- 用户或角色来源使用稳定业务键，不要求在 `diff_snapshot` 中暴露内部 ID；内部 ID 可保留在 `old_snapshot/new_snapshot/entity_id` 中用于审计追溯。
- `old_snapshot/new_snapshot` 继续保存原始变更前后快照；`diff_snapshot` 只保存排查展示需要的摘要。
- **写侧落地（T-PERM-034 收口，2026-08-30——T-PERM-033 登记的读侧依赖解锁）**：`apply-grant-plan` 按本节聚合形状写一条 `ROLE_PERMISSION_CHANGE` 日志——`items[]{changeType, permission(6 字段业务键), role 摘要}` 由 prevalidate 期业务键快照装配（creates 取请求键、updates/removes 反查资源实体+类型+按位操作，removes 行软删后不可回查故预检期快照；removes 的悬挂引用降级为 null 键字段，删除不被死引用阻塞；随主权限级联软删的子权限（`depend_on` 命中 removes）同记 REMOVE 业务键快照、与显式删除项去重——实际被删除的行均可按业务键检索本次变更），端到端场景（成功恰好一条聚合日志+缓存失效；失败全回滚不触发）由容器特征测试锁定。

### 16.5 批量删除与审计日志

- 单次 `remove` 接口无论软删除多少行，**写入一条** `operation_log`（摘要中可含删除数量或 id 列表截断说明）。
- 若该写操作需记 `permission_change_log`，同一事务内**写入一条**记录；`diff_snapshot` 符合 §16 diff_snapshot 规范：`eventType` + `items[]`，可在 `items` 中列出多条 `REMOVE`/`UPDATE` 摘要，**禁止**为每个被删 id 各插入一条 `permission_change_log` 父记录。

## 17. platform 能力（文件与系统配置）
### 17.1 文件管理（/file）🔧（T-ADMIN-023 安全加固补记, 2026-08-25 契约从实现反向登记）

> 本模块先于契约存在（代码即事实），T-ADMIN-023 安全加固时反向登记契约；前端暂无消费方。
> **资源类型**: `ADMIN_FILE`（type_definition `resource_type=25`）。**存储**: 本地磁盘单实例
> （默认 `${user.home}/accessmesh-files`，多实例部署下本地盘不可共享为已知限制，见
> `access-service-architecture.md` §15；不建对象存储抽象层）。

#### 17.1.1 `POST /api/access/file/upload` 🔧

**请求**: `multipart/form-data`（`@RequestParam` 例外之一）：`file`（文件）+ `bizType`（业务类型，可选，默认 `default`）。
**响应**: `R<Long>`（文件记录 ID）。

**安全语义**:

- **门禁**: `ADMIN_FILE:CREATE` 目标文件夹实例级（T-ADMIN-025；resourceCode = 归一化 bizType）。引擎 scopeAll 短路：类型级 CREATE（scopeAll）授权放行任意文件夹——含无投影新文件夹首传；无类型级授权时按文件夹实例判定，无投影文件夹 fail-closed 拒绝（403）。
- **惰性登记**: 门禁放行后同事务 insert-if-absent `resource_entity(ADMIN_FILE, code=bizType)`——首次出现的 bizType 即成为可授权实例（bootstrap 预置 default/avatar/document/image 四文件夹；拒绝路径不登记）。
- **bizType 格式白名单**: `^[A-Za-z0-9_-]{1,32}$`（null/空白归一 `default`）；bizType 是存储路径第一段，违规拒绝 `10506`（不排斥未来新增业务类型，仅消除路径注入面）。
- **文件校验**: 大小上限（默认 10MB，`10503`）；危险扩展名黑名单 + 按 bizType 的扩展名白名单（`10504`）；原始文件名 sanitize；落盘文件名 = UUID + 扩展名；存储相对路径 `{bizType}/yyyy/MM/dd/{uuid}{ext}`。
- **路径安全**: 目录与目标文件构造均经统一路径安全函数（规范化后必须位于存储根内，违规 `10506`）。

#### 17.1.2 `POST /api/access/file/detail` 🔧

**请求 DTO**: `IdReq`。**响应**: `R<FileResp>`（字段序：`id/fileName/originalName/fileSuffix/fileUrl/fileSize/fileType/storagePath(=file_path)/createdAt`；既有实现将 MIME 同时填入 `fileSuffix` 与 `fileType` 两位置，消费方按 `fileType` 取 MIME）。
**错误**: `10501`。**门禁**: `ADMIN_FILE:VIEW` 文件所属文件夹实例级（T-ADMIN-025；resourceCode = 元数据 `bucket_name`，须先取行——文件不存在先报 `10501`，再做文件夹判定）。无投影文件夹（含白名单前落库的历史脏桶）fail-closed 拒绝（403）。

#### 17.1.3 `POST /api/access/file/page` 🔧

**请求 DTO**: `FilePageReq { pageNum, pageSize, sort?, bizType? }`（`pageNum/pageSize` 可选，默认 1/20，`@Min(1)/@Max(100)` 校验——T-ADMIN-026 对齐 PageReq 先例，原「必填、缺省将失败」行为废止；`sort/bizType` 可选）。**响应**: `R<PageResp<FileResp>>`（按创建时间倒序 `created_at DESC, id DESC`，可按 bizType 过滤）。
**门禁**: 过滤语义非门禁（T-ADMIN-025，不 403）：可见文件夹全集 = 租户内有效文件覆盖的 bucket_name 去重（bizType 请求参数先收窄全集），经引擎批量判定（scopeAll 命中返回全量；无投影文件夹 fail-closed 落入不可见侧），按可见文件夹集合 SQL `bucket_name IN` 过滤；无可见文件夹返回空页，bizType 指向无权文件夹静默返回空页。

#### 17.1.4 `POST /api/access/file/download` 🔧

**请求 DTO**: `IdReq`。**响应**: 文件字节流直接写 HTTP 响应（`Content-Disposition: attachment`；全仓唯一绕过 `R` 包装的文件流白名单端点，T-ACCESS-011 登记）。
**错误**: `10501`（元数据或物理文件不存在）/ `10506`（路径非法）/ `10507`（读取 IO 失败；原裸 `10504/10505` 硬编码已归位，T-ADMIN-023）。
**门禁**: `ADMIN_FILE:VIEW` 文件所属文件夹实例级（T-ADMIN-025，同 detail 口径：先取行、resourceCode=`bucket_name`、无投影 fail-closed）。

#### 17.1.5 `POST /api/access/file/delete` 🔧

**请求 DTO**: `IdsReq { ids: List<Long> }`（批量）。**响应**: `R<Void>`。
**门禁**: `ADMIN_FILE:DELETE` 批量文件夹实例级（T-ADMIN-025：resourceCode 从文件 ID 迁移为有效文件的 `bucket_name` 去重集合，先取元数据后判定；无投影历史脏桶 fail-closed 拒绝，scopeAll 放行全量）。

**删除顺序（T-ADMIN-023 反转后的终态语义）**:

1. 同一事务内提交元数据软删除（`delete_flag=id`）；
2. 事务提交成功后经事务同步（afterCommit）物理清理文件；
3. 物理清理失败仅记 WARN **保留孤儿文件**（孤儿文件优于丢失有效文件，可人工清理），不回滚已提交的软删、不使接口失败；
4. 无事务上下文时软删后立即清理。

**遗留**: 孤儿文件自动回收调度不做（仅记录）；多实例共享存储/对象存储不做（另立任务）。

#### 17.1.6 安全语义总表

| 端点 | 门禁（操作/档位） | 路径安全 | 错误码 |
|------|------------------|----------|--------|
| `/api/access/file/upload` | `ADMIN_FILE:CREATE` 目标文件夹实例级（scopeAll 放行任意文件夹含新夹首传）+ 惰性登记投影 | 目录 + 目标文件经统一路径安全函数；bizType 格式白名单 | `10502/10503/10504/10506` |
| `/api/access/file/detail` | `ADMIN_FILE:VIEW` 文件所属文件夹实例级 | 不触盘 | `10501` |
| `/api/access/file/page` | 过滤语义（按可见文件夹裁剪，无 403） | 不触盘 | — |
| `/api/access/file/download` | `ADMIN_FILE:VIEW` 文件所属文件夹实例级 | DB filePath 经统一路径安全函数（纵深防御） | `10501/10506/10507` |
| `/api/access/file/delete` | `ADMIN_FILE:DELETE` 批量文件夹实例级（bucket 去重） | 清理阶段经统一路径安全函数（非法路径容忍为孤儿，不回滚软删） | —（软删总是提交） |

> **文件夹级授权（T-ADMIN-025 落地，2026-09-06）**: bizType 即文件夹实例（`sys_file.bucket_name` =
> `resource_entity(ADMIN_FILE).code`，单事实源为投影表）；实例由 bootstrap 预置四文件夹 +
> 上传惰性登记两条事实链产出，无管理界面；ADMIN_FILE 种子声明 SYNC+access-service（人工经
> resource-entity 管理入口构造一律 20055）。原「类型级 VIEW 过渡」口径（2026-08-25）随本任务废止。
> 新文件夹需先有一次上传才产生实例、才可配置实例级授权（预登记机制为后续优化点，未设计）。

### 17.2 system-config（/api/access/system-config/*）

| 接口                                                   | 说明                                                   |
| ------------------------------------------------------ | ------------------------------------------------------ |
| `POST /api/access/system-config/list`                    | 查询系统配置                                           |
| `POST /api/access/system-config/detail`                  | 查询系统配置详情                                       |
| `POST /api/access/system-config/save`                    | 保存系统配置                                           |

> **system-config 错误码**：`system-config/save`（upsert）在权限校验后、触达数据前 fail-closed 校验配置键命名空间前缀（`admin.`/`permission.`/`access.`），非法键返回 **20047 `CONFIG_KEY_NAMESPACE_INVALID`**（配置键只能使用 admin./permission./access. 命名空间前缀，T-ACCESS-007）。update 分支命中已有系统内置行（`is_system=true` 种子行）返回 **20064 `CONFIG_KEY_SYSTEM_IMMUTABLE`**——「系统内置仅走种子」的运行时强制（T-ACCESS-037 外评存量观察修正，2026-09-13；原 admin `/config` 侧 10702 校验随僵尸端点退役，本码不复用其码值）。

> **单入口口径（T-ACCESS-037，2026-09-13）**：本族为 system_config 唯一管理入口——原 admin `/config` 双入口（同表 ConfigController，UPDATE/DELETE 实例级门禁）已整链退役（见 §17.3 注记），无迁移端点、无兼容层。

**system-config 契约要点（T-PERM-024 收口，2026-08-28）**：

- `save`（upsert）：`{configKey, configValue, description?}`——按 `configKey` 查存在则 update、不存在则 insert（新建固定 `isSystem=false` 租户自定义，系统内置仅走种子）；`configValue` 为 JSON 字符串（`JsonValidationUtils` 校验合法性）；`configKey` 命名空间前缀校验 20047（见上）。无 create/update/remove——`save` 幂等覆盖新建/编辑，配置项不可删除（键稳定，防误删回退默认）。
- `detail`：`{configKey}`（按业务键 configKey 查询，非 id）。
- `list`：`{keyword?, pageNum?, pageSize?}` → 分页结构（§2.3）；`keyword` 匹配 configKey/description（LIKE，大小写敏感），排序 `config_key, id`；分页参数均不传 = 字典全量（上限 200，先例 `/api/access/role/list`）。
- **isSystem 行标识（T-ACCESS-037 后续修正，2026-09-13）**：detail/save 响应与 list 行均携带 `isSystem`（Boolean，映射 `is_system` 列）——`true` 为系统内置（仅走种子，save 拒改 20064，见上）；前端据此以「系统预置/自定义」标签展示并对内置行隐藏编辑入口（type-def 同款形态）。
- **configValue JSONB 语义（SystemConfigJsonbPgIT 实证）**：读出为 DB 规范化后的 JSON 文本——与提交值**语义等价**（解析树相等，含中文/嵌套/数组），但非字节回显（JSONB 规范化空白与键序）；展示值可直接再提交（规范化幂等），无截断/转义问题。
- 权限门禁：`list`/`detail` 需 `SYSTEM_CONFIG:VIEW`，`save` 需 `SYSTEM_CONFIG:MANAGE`（操作位种子已由权威 DDL CRUD 预置组覆盖，租户 1）。

### 17.3 未成册端点族登记（dict / notice / job / login-log）

> `/api/access/dict/*`（字典）、`/api/access/notice/*`（公告）、`/api/access/job/*`（任务调度）、`/api/access/login-log/page`（登录日志）四组管理面端点族未在原两册成册，契约以代码与 `HttpApiPathSnapshotTest` 快照为准（T-ACCESS-040 登记，不在本任务新增契约内容）。
>
> **`/config/*` 退役（T-ACCESS-037，2026-09-13）**：原 admin `/config`（page/detail/update/delete，ConfigController + ConfigAppService + ConfigUpdateReq/ConfigResp + SystemConfigMapper 五个 admin 侧方法与 XML 语句 + 错误码 10701/10702）整链删除——消费面核实（2026-09-13）前端/e2e/gateway/example-service 主代码零引用，僵尸端点。system_config 管理**单入口**收敛到 §17.2 `/api/access/system-config`（前端唯一消费方）。负向锁 = `HttpApiPathSnapshotTest` 快照（`/config/*` 四路径出快照，控制器扫描双向比对）+ `RETIRED_PATHS` 登记四条（`retiredPaths_haveNoControllerMappings` 断言零映射，capability-structure §8.4 指令兑现）；错误码 10701/10702 入 `ErrorCodeContractTest` RETIRED_ADMIN_NAMES（码值不复用）。SYSTEM_CONFIG 操作码不删（VIEW/MANAGE 为 perm 入口门禁与 bootstrap 固定图在用；UPDATE/DELETE 为 DDL CRUD 预置种子 CROSS JOIN 产物，随端点消亡转为未消费预置位，034 的 USER:MANAGE 类清理不涉此类预置码）。

## 18. engine 运行时鉴权与权限查询（/api/access/auth/*）
### 18.1 端点清单与 SDK 入口

| 接口                                      | 说明                                                   |
| ----------------------------------------- | ------------------------------------------------------ |
| `POST /api/access/auth/check`               | 单次资源权限判定                                       |
| `POST /api/access/auth/batch-check`         | 批量资源权限判定                                       |
| `POST /api/access/auth/query-resources`     | 查询主体在指定资源类型和操作下可访问或可管理的资源集合 |
| `POST /api/access/auth/query-scopes`        | 查询主体在某个主资源上下文内可用的范围资源权限集合     |
| `POST /api/access/auth/check-interface`     | Gateway 接口级判定                                     |
| `POST /api/access/auth/interface-snapshot`  | Gateway 接口权限快照，可选优化接口                     |

> **SDK 入口清单（T-API-002 收口 2026-09-06；T-API-003 check 族改写 2026-09-10）**：`check` / `batch-check` / `query-resources` / `query-scopes` 四件套已全部进入 perm-sdk `PermissionFeignClient`（Query* DTO 迁入 perm-common，接入方不再手写 HTTP + 自造 DTO 副本）；`check-interface` / `interface-snapshot` 为 Gateway 专用（经 starter/PermissionClient 消费），不开放业务服务 Feign 入口。**内部数据库 id 字段族口径分族**：check 族三端点（check/batch-check/check-interface）结果记录全量回传（`matchedRoleIds`/`matchedPermissionIds`/`matchedResources[].resourceId`，T-API-003 推翻 T-API-002 的 check 族裁剪——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场，详见 §18.2/§18.3 注记）；Query\* 响应族（query-resources/query-scopes）六字段裁剪维持 T-API-002 终态（详见 §18.5/§18.6 各节注记）。

### 18.2 单次鉴权 check

`POST /api/access/auth/check`

```json
{
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001",
  "resourceTypeCode": "MENU",
  "resourceCode": "sys:user",
  "operationCode": "VIEW",
  "domainCode": "admin",
  "codeType": "default",
  "inheritMode": "NONE",
  "parentResourceTypeCode": "REPORT",
  "parentResourceCode": "report:sales",
  "parentCodeType": "default",
  "parentOperationCodes": ["VIEW"],
  "context": {
    "clientIp": "127.0.0.1",
    "timestamp": "2026-04-26T18:00:00"
  }
}
```

响应：

```json
{
  "allowed": true,
  "reason": null,
  "matchedRoleIds": [20, 31],
  "matchedPermissionIds": [401],
  "conditionEvaluated": false
}
```

> **`inheritMode` 参数语义接通（T-PERM-057，2026-09-09 落地）**：`NONE`/缺省 = 判定面继承关（目标精确判定）；`PARENT`/`BOTH` = 判定面继承开——判定目标扩为 {目标}∪同类型祖先链（授父资源该操作时子目标判定通过，闭包止步同类型、软删祖先截断）。该参数从「对单点判定结论无效」接通为目标闭包真实语义（判定面继承默认值矩阵见 engine/implementation.md §3.4）。
> **结果记录全量回传（T-API-003，2026-09-09 定案推翻 T-API-002 的 check 族裁剪，2026-09-10 落地）**：`matchedRoleIds` / `matchedPermissionIds`（role / role_resource_permission 内部行 id）恢复回传——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场。拒绝时为空列表（与允许时非空记录区分）。人类可读的来源解释通道（原册 §6.8 `permission-view/explain`）已随排查端点族删除（T-PERM-059，2026-09-10），重做设计另立任务。Query\* 响应族（§18.5/§18.6）的字段裁剪不在推翻范围，维持 T-API-002 终态。
> **主资源上下文与 depend_on 单点闭合（T-PERM-058，2026-09-10 落地）**：查询目标为子权限（depend_on）实例时，可选传入 `parentResourceTypeCode` / `parentResourceCode` / `parentCodeType` / `parentOperationCodes`（与 §18.6 query-scopes 父入参同名对齐；type 与 code 必须成对提供，半传 400；**给出父上下文时 `parentOperationCodes` 一并必填且非空**——缺省/空集按 400 拒绝，勿依赖静默回退：引擎对空操作集不发父判定查询、父判定必不命中）——引擎对父资源做 INSTANCE 判定（含条件/互斥评估，惰性执行：仅当目标命中集确含子行才触发），子行要求其 `depend_on` ∈ 父命中权限集才计入。**不传时子权限行一律不参与判定（fail-closed）**，拒绝原因为 `DEPENDENT_NOT_IN_PARENT_CONTEXT`（与「无任何授权」的 `NO_PERMISSION` 区分；**类型级门禁面（resourceCode=null）为 `NO_PERMISSION`**——该面无 DEPENDENT 分支，SDK 按 reason 分支时勿漏判）——子权限的授权语义是「只在父权限命中的主资源上下文内生效」（§18.6 DEPENDENT 公式），无上下文即无法证明父命中。`batch-check` 的同名四字段为**请求级**（与 query-scopes 对齐：批量项共享同一主资源上下文，如报表A上下文内的广东/杭州/上海多目标）。引擎内部便捷入口（`hasPermissionByCode` / `hasPermissionByEntityId` / `getDenied*`，管理面写门禁矩阵）无主资源上下文概念，同口径 fail-closed；类型级门禁（`resourceCode=null`）只认主授权——scopeAll 子权限行（写侧可造形态）不放行类型级门禁（读侧排除，DB 直写脏数据同受防护）。
> **批量评估口径（T-PERM-061 A+ 形态实施，2026-09-11 落地）**：`batch-check` 编排已收敛到引擎 `queryBatch`（分组 + 请求级共享装载，语义与单条逐 item 等价——等价差分回归锁钉死；实现设计见 engine/implementation.md §3.10），**wire 契约零变化**（请求/响应 JSON、1000 上限、reason 词表、matched 字段族不变）。**评估时刻（a2 定案）**：整批共用请求级单一评估时刻——服务端在批量入口统一钉住一个 `evaluatedAt`（全部 item 评估、父判定递归、条件评估共用），时间类条件（DATE_RANGE/TIME_RANGE）的观测粒度为**批**而非 item：跨时间边界的大批量不再出现「前一半放行、后一半拒绝」的批内漂移。READ COMMITTED 下批量化同时把「逐 item 各语句各看各的」变为「批内一次装载同源」——与 a2 同向的行为变化，随定案接受。互斥审计通知按 (组, ruleId) 去重聚合（每命中组一条、携 hitItemCount），不再逐 item 重复通知。

### 18.3 Gateway 接口级鉴权 check-interface

`POST /api/access/auth/check-interface`

```json
{
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001",
  "serviceCode": "access-service",
  "httpMethod": "POST",
  "path": "/api/example/order/list",
  "context": {
    "clientIp": "127.0.0.1",
    "headers": {
      "User-Agent": "Mozilla/5.0"
    }
  }
}
```

响应：

```json
{
  "allowed": true,
  "reason": null,
  "matchedResources": [
    {
      "resourceId": 101,
      "resourceTypeCode": null,
      "resourceCode": "admin:user:list",
      "operationCode": "ACCESS",
      "allowed": true,
      "matchedRoleIds": [20],
      "matchedPermissionIds": [401]
    }
  ],
  "cacheTtlSeconds": 30
}
```

规则：

- 查询 `resource_api_mapping` 必须带 `tenant_id + service_code + http_method + enabled + delete_flag=0`。
- `path` 使用请求原始路径——T-ACCESS-042 起外部路径=服务路径，二者天然同形（原「不用 StripPrefix 后路径」的区分已随 StripPrefix 退役消失）。
- 当同一路径匹配多个资源映射时，接口级鉴权采用 OR 语义：任一映射资源权限通过即允许。
- 响应使用 `matchedResources[]` 返回所有命中的映射资源及各自鉴权结果；只要其中任一项 `allowed=true`，顶层 `allowed=true`。
- 未注册接口默认拒绝，返回 `API_NOT_REGISTERED`。
- **结果记录全量回传（T-API-003，2026-09-09 定案推翻 T-API-002 的 check 族裁剪，2026-09-10 落地）**：`matchedResources[]` 条目的 `resourceId`（resource_entity 内部行 id）与 `matchedRoleIds` / `matchedPermissionIds` 恢复回传——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场；业务键 `resourceTypeCode + resourceCode` 继续并行表达资源身份。现消费方 Gateway 只读 `allowed` / `reason`，不受回传字段影响。`matchedResources[].resourceTypeCode` 实现恒 null（示例同步改 null，registry T-API-003 行挂靠 T-PERM-059 的示例漂移收口，2026-09-10 用户拍板）——资源身份以 `resourceId + resourceCode` 表达。

### 18.4 Gateway 接口权限快照 interface-snapshot

`POST /api/access/auth/interface-snapshot`

用于 Gateway 按服务拉取当前主体可访问的 API 快照。permission-center 每次实时调 engine 构建全量快照返回（T-PERM-018 缓存下沉，移除 `permissionVersion`/`notModified` 与条件请求），Gateway 本地 Caffeine 缓存 + Redis 广播（`perm:invalidate`，事件载荷含 `serviceCodes`，T-PERM-006 订阅侧已落地）+ TTL 兜底保证一致性。

> **令牌移除（T-PERM-018，2026-06-20）**：`permissionVersion` / `notModified` 字段已移除——令牌「唯一真正作用是 INTERFACE_SNAPSHOT 缓存 key」已核实，permission-center 侧该 L2 缓存已删，令牌随之失效，连带 304/notModified 死代码一并清除。permission-center 正确性改由 engine `ROLE_PERM_SNAPSHOT` 读缓存（per-role 精确失效）保证；Gateway 本地陈旧由广播 + TTL 兜底。

请求：

```json
{
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001",
  "serviceCode": "access-service"
}
```

响应：

```json
{
  "allowedApis": [
    {
      "serviceCode": "access-service",
      "httpMethod": "POST",
      "pathPattern": "/api/user/list",
      "hasCondition": false,
      "conditionId": null,
      "scopeMode": "INSTANCE"
    },
    {
      "serviceCode": "access-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeMode": "ALL"
    }
  ]
}
```

规则：

- permission-center 每次实时构建全量快照返回，不再有令牌比较 / 304 短路路径；无有效角色时返回 `allowedApis=[]`。
- `scopeMode=ALL` 的条目表示角色对该服务全部 API 拥有权限，`httpMethod` 和 `pathPattern` 为 null。调用方自行根据 `hasCondition`/`conditionId` 决定是否放行——服务端不展开全量权限为逐条 API。实例级条目（`scopeMode=INSTANCE`）仍按 `httpMethod + pathPattern` 精确匹配。
- Gateway 本地缓存 key 为 `(tenantId,subjectTypeCode,userId,serviceCode)`；API mapping / 资源 / syncInterfaces 变更触发 `PermInvalidateEvent`（含 `serviceCodes`），Gateway 订阅后按 tenant+serviceCodes evict 本地快照（T-PERM-006）。

### 18.5 通用资源权限查询 query-resources

> **原登记失效（2026-08-30 全局操作概念退役，T-PERM-049）**：曾登记的「判定轨合并全局操作位 vs 展示投影轨仅取类型专属」分歧随概念退役自动消解——两侧现均为类型专属操作，无投影轨缺口。

`POST /api/access/auth/query-resources`

用于业务服务查询某个主体在指定资源类型和操作下的有效权限集合。典型场景包括管理面查询用户能管理哪些组织、哪些角色、哪些菜单。前提是这些业务对象已经作为 `resource_entity` 同步或创建到权限中心。

请求：

```json
{
  "subjectTypeCode": "LOCAL_USER",
  "subjectExternalId": "10001",
  "domainCode": "admin",
  "resourceTypeCodes": ["ORG"],
  "operationCodes": ["UPDATE"],
  "codeType": "default",
  "includeInherited": true,
  "includeChildren": false,
  "context": {
    "clientIp": "127.0.0.1",
    "timestamp": "2026-04-26T18:00:00"
  }
}
```

响应：

```json
{
  "items": [
    {
      "resourceTypeCode": "ORG",
      "resourceCode": "100",
      "resourceName": "研发中心",
      "codeType": "default",
      "operations": ["UPDATE"],
      "scopeMode": "INSTANCE",
      "canGrant": true,
      "grantSources": ["MANUAL"]
    },
    {
      "resourceTypeCode": "ORG",
      "resourceCode": null,
      "resourceName": null,
      "codeType": null,
      "operations": ["UPDATE"],
      "scopeMode": "ALL",
      "canGrant": false,
      "grantSources": ["MANUAL"]
    }
  ],
  "cacheTtlSeconds": 60
}
```

> **DTO 公共化与内部 id 裁剪（T-API-002 收口，2026-09-06）**：`QueryResourcesReq/Resp` 已迁入 perm-common（SDK `PermissionFeignClient.queryResources` 直连）；条目的 `matchedRoleIds` / `matchedPermissionIds`（role / role_resource_permission 内部行 id）已裁剪（engine/core-flows.md §15 口径），线格式字段快照由 SDK 契约测试钉死。
> **子权限行不进清单面（T-PERM-058，2026-09-10 落地）**：depend_on 子权限行不出现在 `items[]`——其授权只在 §18.6 query-scopes 主资源上下文内生效/可见，独立 INSTANCE 条目呈现会误导调用方（子行实例 ≠ 独立可访问）。Gateway 接口快照（§18.4）同口径：API 类型子权限行不下发（接口鉴权无主资源上下文概念）。

管理面查询示例：

| 查询目标   | 建模方式                                                                           | 查询参数                                                                  |
| ---------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| 可管理组织 | 组织同步为管理资源，例如 `resourceTypeCode=ORG`、`resourceCode={sys_org.id}` | `resourceTypeCodes=["ORG"]`、`operationCodes=["UPDATE"]` 或其他管理操作 |
| 可管理用户 | 用户同步为管理资源，例如 `resourceTypeCode=USER`、`resourceCode={sys_user.id}` | `resourceTypeCodes=["USER"]`、`operationCodes=["UPDATE","DELETE","ENABLE","RESET_PASSWORD"]` |
| 可管理角色 | 角色同步为资源，例如 `resourceTypeCode=ROLE`、`resourceCode=role:{roleExternalId}` | `resourceTypeCodes=["ROLE"]`、`operationCodes=["MANAGE"]` 或 `["ASSIGN"]` |
| 可见菜单   | 菜单同步为资源，例如 `resourceTypeCode=MENU`、`resourceCode=menu:{menuCode}`       | `resourceTypeCodes=["MENU"]`、`operationCodes=["VIEW"]`（树由调用方基于平面列表自建） |

规则：

- 查询接口只返回权限事实和资源业务键，不查询管理面的组织、角色、菜单业务表。
- 调用方拿到 `resourceCode` 后，由业务服务映射成本服务内的组织树、角色列表或菜单树。
- AccessMesh 管理端中，`USER`/`ORG` 的 `resourceCode` 固定使用管理面本地主键字符串（T-ACCESS-018 收敛后类型码），避免与组织编码、用户名等可变业务字段混用。
- AccessMesh 管理端中，USER/ORG 的 resourceCode 固定使用管理面本地主键字符串。所有接口均支持业务键参数，权限面内部通过 TypeResolutionService 解析为内部 ID。调用方不应存储权限面的内部主键 ID。
- `scopeMode=ALL` 的条目表示该 `resourceTypeCode` 下全量资源权限，此时 `resourceCode`、`resourceName`、`codeType` 均为 null；不展开全量范围为逐条资源实例。实例级条目（`scopeMode=INSTANCE`）按 `resourceCode + codeType` 精确表示。
- 多个角色命中同一资源时，按 `resourceTypeCode + resourceCode + codeType + scopeMode` 去重，并合并 `operations`、`grantSources`。
- 条件、冲突规则、停用状态、角色继承、资源继承必须与 `auth/check` 使用同一套计算逻辑。
- `treeMode` 树模式响应已从契约移除（2026-08-27 决策，无真实消费方）：接口固定返回平面列表，树形展示由调用方基于平面列表自建；资源父子关系可经 `includeChildren` 展开获取。如未来需要服务端树响应，登记于 v3.5.1-evolution 演进方向重新评估。
- 该接口面向运行时 SDK 查询（原「解释授权来源用 permission-view/*」的指引已随排查端点族删除失效，T-PERM-059，2026-09-10）。

### 18.6 范围权限运行时查询 query-scopes

`POST /api/access/auth/query-scopes`

用于业务服务查询某个主资源上下文内的有效范围权限。典型场景是 example-service 查询用户能查看或编辑销售报表中的哪些部门、城市、门店、数据集。范围权限由两类权限取并集：直接范围权限 `DIRECT` 和依赖当前主权限的子权限 `DEPENDENT`。

请求：

```json
{
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001",
  "domainCode": "example",
  "parentResourceTypeCode": "REPORT",
  "parentResourceCode": "report:sales",
  "parentCodeType": "default",
  "parentOperationCodes": ["DATA_READ", "DATA_EDIT"],
  "scopeResourceTypeCodes": ["DATA"],
  "scopeOperationCodes": ["DATA_READ", "DATA_EDIT"],
  "scopeCodeType": "default",
  "context": {
    "clientIp": "127.0.0.1",
    "timestamp": "2026-04-26T18:00:00"
  }
}
```

响应（T-PERM-009 分类模型，按 `(resourceTypeCode, operationCode)` 分组）：

```json
{
  "reason": null,
  "matchedParentOperations": ["DATA_READ", "DATA_EDIT"],
  "scopeGroups": [
    {
      "resourceTypeCode": "DATA",
      "operationCode": "DATA_READ",
      "scopeMode": "ALL",
      "items": []
    },
    {
      "resourceTypeCode": "DATA",
      "operationCode": "DATA_EDIT",
      "scopeMode": "INSTANCE",
      "items": [
        { "resourceCode": "data:dept:A", "codeType": "default", "resourceName": "A部门数据" },
        { "resourceCode": "data:dept:B", "codeType": "default", "resourceName": "B部门数据" }
      ]
    },
    {
      "resourceTypeCode": "DATA",
      "operationCode": "DATA_EXPORT",
      "scopeMode": "DENIED",
      "items": []
    }
  ],
  "cacheTtlSeconds": 60
}
```

`scopeMode` 四态语义（枚举定义于 perm-common `cn.ac.fage.accessmesh.perm.common.enums.ScopeMode`）：

| scopeMode | 含义 | 业务方行为 |
|---|---|---|
| `DENIED` | 无操作权限（合并原 `allowed=false`） | 拒绝/403，不发 SQL |
| `INSTANCE` | 有权限 + 具体实例授权 | `items[]` 非空，按 `resourceCode` 加 IN 过滤 |
| `ALL` | 有权限 + 全量授权 | `items[]` 为空，不加范围过滤 |
| `EMPTY` | 有权限但条件/互斥过滤后无数据 | 返回空结果，不发 SQL |

规则：

- 权限中心先按 `parentResourceTypeCode + parentResourceCode + parentCodeType + parentOperationCodes[]` 执行主权限判定。
- 主权限全部不通过时，`reason="NO_PERMISSION"`，所有 `scopeGroups` 格置 `scopeMode=DENIED`，不返回范围实例。
- 主体/主资源未解析时 `reason` 填 `USER_NOT_FOUND` / `OBJECT_KEY_NOT_FOUND`，`scopeGroups` 为空。
- `DIRECT` 范围权限来自当前主体有效角色下 `depend_on IS NULL` 的范围资源授权。
- `DEPENDENT` 范围权限来自依赖已通过主权限的子权限授权（服务端内部按 `depend_on ∈ 已通过主权限行集合` 过滤），只在当前主资源上下文内生效；该集合不进线格式（T-API-002 裁剪，见下）。
- 有效范围权限计算公式为 `effectiveScopes = DIRECT ∪ DEPENDENT`。
- **分类键为 `(resourceTypeCode, operationCode)`**：每个请求的 `scopeResourceTypeCodes × scopeOperationCodes` 笛卡尔积对应一个 `scopeGroup`，各格独立判定 `scopeMode`。
- 单格判定优先级：无覆盖该操作的权限 → `DENIED`；有权限但条件/互斥过滤后为空 → `EMPTY`；过滤后含全量范围条目 → `ALL`（`items` 为空，ALL 优先于 INSTANCE）；仅具体实例 → `INSTANCE`（`items` 去重列出有效实例）。
- 范围操作必须被至少一个已通过的主操作激活。推荐在 example-service 中使用同名业务数据动作，例如 `report:sales + DATA_READ -> dept + DATA_READ`、`report:sales + DATA_EDIT -> dept + DATA_EDIT`；这只是推荐范例，不作为所有接入系统的强制标准。
- 如果主操作和范围操作不是同名关系，应通过域配置声明映射规则；未配置映射时，默认只做同名操作匹配。
- `scopeMode=ALL` 表示该格 `resourceTypeCode + operationCode` 下全量范围权限，实现不应展开返回全部实例明细。
- 权限中心只返回范围权限事实，不生成 SQL、不解释业务字段；业务服务自行按 `scopeMode` 决定是否发 SQL 及如何把 `items[].resourceCode` 映射为查询条件。
- **管理端排查复用（T-PERM-033 设计定案，2026-08-29）**：权限排查页 Tab2 复用本接口，但本接口**维持运行时语义、不加排查门禁**（业务服务按主体查询不要求调用者持排查码；未来业务方合法的非自查查询不应被拒）。排查页仅靠页面级 UI 门（`USER:VIEW` 或 `ROLE:VIEW` 任一）控制入口；API 层为租户内只读暴露面，按演进需要再评估收紧。**排查页复用注记（历史）**：原权限排查页 Tab2 复用本接口的形态已随页面整体删除（T-PERM-059，2026-09-10；此前 2026-09-06 曾暂停待重做）；本接口维持运行时语义不变。
- **DTO 公共化与内部 id 裁剪（T-API-002 收口，2026-09-06）**：`QueryScopesReq/Resp` 已迁入 perm-common（SDK `PermissionFeignClient.queryScopes` 直连）；`parentPermissionIds` 与 `scopeGroups[]` 的 `matchedRoleIds` / `matchedPermissionIds` / `dependOnPermissionIds`（均为 role / role_resource_permission 内部行 id）已裁剪（engine/core-flows.md §15 口径），线格式字段快照由 SDK 契约测试钉死。
- 已删除字段：`allowed`（合并进 `scopeMode`）、`mergeMode`（分类模型下每格独立，不再需要 UNION 标记）、顶层 `items[]`/`ScopeEntry`（改为 `scopeGroups[].items[]`）、`parentPermissionIds`、`scopeGroups[]` 的 `matchedRoleIds`/`matchedPermissionIds`/`dependOnPermissionIds`（T-API-002）。`permissionVersion` 字段已于 T-PERM-018（缓存下沉）移除。

> **编号注记**：原册 §6.8「权限排查视图与近期变更」与 原册 §6.10.5 已随 T-PERM-059 删除（2026-09-10）——diff_snapshot 轻量规范迁至 §16，编号空洞保留防历史锚点断链。

## 19. sync 同步通道（/api/access/**/sync 与 full-sync）
### 19.1 资源实体专用同步 resource-entity/sync

> **资源发布顺序（T-PERM-071）**：混用资源 FULL 的 scope 中，增量与 FULL 共同携带源侧分配的 `publicationGeneration`，平台提供双向旧请求防护；尚未切换的纯增量 scope 可省略该字段。字段、响应与一次性切换规则见 §19.2.1。

> **自动授权生命周期已采纳、待实施（T-PERM-071/072，2026-09-20）**：仅资源 DISABLE 或 UPSERT/FULL 的 status 停用、恢复不改变自动授权传播；源、目标、中间资源均不因停用退出推导，既有自动授权保留，恢复不触发重建。显式撤权、资源 DELETE/FULL 缺失删除、依赖声明变更仍按各自规则重算。此决定不改变资源自身的运行时鉴权或来源服务启停门禁，详见[自动授权设计 §7](dependency-auto-grant.md#triggers)。

`POST /api/access/resource-entity/sync`

用于外部事实源把业务对象幂等同步为 permission-center 的 `resource_entity`。该接口只处理资源实体，不是跨实体万能 replay 入口；不接受 `entityType + operationType + payload` 形式。

> 服务间认证：sync/full-sync 接口必须由已验证服务身份调用——凭证通过后绑定 `X-Service-Code`，`SyncAuthVerifier` 从上下文比对（见 `access-service-architecture.md` §18.3 安全策略矩阵）。`sourceService` 必须等于已验证服务身份，不匹配返回 `SECURITY_DENIED`；仅信任请求体 `sourceService` 而不校验服务身份的行为已被禁止。

请求：

```json
{
  "operation": "UPSERT",
  "resourceTypeCode": "HR_ORG",
  "resourceCode": "2001",
  "codeType": "default",
  "name": "研发部",
  "parentResourceTypeCode": "HR_ORG",
  "parentResourceCode": "1000",
  "path": null,
  "status": 1,
  "extra": {
    "region": "CN"
  },
  "sourceService": "hr-service",
  "sourceEntityType": "hr_org",
  "sourceEntityId": "2001",
  "publicationGeneration": "42",
  "syncVersion": {
    "occurredAt": "2026-06-12T10:00:00.123",
    "sequenceNo": 1024
  }
}
```

规则：

- `operation` 首期固定为 `UPSERT`、`DISABLE` 或 `DELETE`；`UPSERT` 表示不存在则创建、存在则更新，`DISABLE` 表示幂等停用，`DELETE` 表示幂等软删除，不存在也视为成功。
- 幂等业务键为 `businessKey=resourceTypeCode={resourceTypeCode}&resourceCode={resourceCode}&codeType={codeType}`，其中 `codeType` 默认 `default`；`tenantId/sourceService/entityKind` 由独立字段承载。
- `syncVersion` 使用事件时间 + 序号；同一幂等键下旧版本请求必须返回成功但不覆盖新状态。permission-center 必须通过 `sync_metadata.last_sync_occurred_at + last_sync_sequence_no` 做原子比较更新，禁止只在内存中判断版本。
- 父资源定位与同类型门禁（T-PERM-068，2026-09-17 Q-007 定案①③ + 2026-09-18 外评处置两项拍板）：父字段组**仅 UPSERT 生效**（DISABLE/DELETE 忽略父字段——删/停不被父资源存否绑架，对齐角色域 OP_UPSERT 守卫先例）；UPSERT 下以 `parentResourceCode` 非空为激活条件——为空则解挂（已存在行显式清 parent 列落库，UpdateEntity 先例）；`parentResourceTypeCode` 缺省回填 item 自身 `resourceTypeCode`（只传 code 不传 typeCode 也按同类型解析挂父，对齐角色域 full-sync 先例）；显式传入的 `parentResourceTypeCode` 必须等于自身类型，跨类型拒绝 `retryClass=NON_RETRYABLE`、reason=`PARENT_TYPE_MISMATCH`（码比对足够：code↔value 双射），先于父解析与版本写入。父资源不存在时返回 `retryClass=DEPENDENCY_MISSING`，调用方可按短退避重发（T-PERM-044 评审对齐角色先例：父解析与判环先于版本写入——依赖缺失与环路拒绝均不推进同步版本，短退避同版本重发不会被 STALE 挡）。
- DELETE 有子拒绝（2026-09-18 用户拍板）：单条 DELETE 目标存在有效后代（含脏数据跨类型子，保守阻塞防误删）→ `retryClass=DEPENDENCY_MISSING`、reason=`CHILDREN_EXIST`，先于版本写入不推进——先删子资源后同版本重发父即自愈（乱序删除窗口短退避自愈，子永不悬挂）；后代全删/目标不存在照常幂等软删。full-sync 差异校准维持 scope 全量口径逐行软删、不查子（同载荷整树同删，无乱序问题）。
- 父资源环路防护（T-PERM-044 评审补齐，与 moveResource 同款）：目标父为资源自身或其子孙时拒绝 `retryClass=NON_RETRYABLE`、reason=`RESOURCE_PARENT_INVALID`；full-sync 按内存图逐项判定（当前生效图 = 库内关系 + 本事务已应用项的边），仅拒绝真正闭合环的项，指向环的前缀安全项放行。
- 调用方必须通过可信 Header 提供服务身份；permission-center 必须校验认证服务身份与 `sourceService`（不匹配返回 `SECURITY_DENIED`/`SOURCE_SERVICE_MISMATCH`），禁止任意服务同步任意资源类型。
- **类型级所有权门禁（T-PERM-052，2026-09-05 定案，取代原 syncTypes.resourceTypeCodes 白名单维度）**：目标 `resourceTypeCode` 必须在类型定义上声明 `extra.managedMode=SYNC` 且 `extra.syncSourceService` 等于调用服务身份，且调用服务在 service_config 注册、未软删、`status=1` 启用（评审批次补强：服务停用/注销即四个同步通道一起断，与主体/角色/user_role 白名单语义一致）——任一不满足返回 `SECURITY_DENIED`/`RESOURCE_TYPE_OWNERSHIP_DENIED`（类型不存在一并 fail-closed 拒绝，真实原因仅记服务端日志）。每个资源类型单一所有权：MANAGED（缺省，管理面维护）/ SYNC（声明来源独占同步，管理面只读）；事实链路七类型 USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION/CONDITION 由种子声明 SYNC+access-service（内部来源，见 §13；ADMIN_FILE 为 T-ADMIN-025 增补——文件夹实例由 bootstrap 预置+上传惰性登记产出；TYPE_DEFINITION 为 T-PERM-051 增补——类型定义实例投影由写路径同事务维护+bootstrap 自愈补种产出；CONDITION 为 T-PERM-048 增补——管理页条件投影由条件写路径同事务维护+bootstrap 自愈补种产出，仅 MANAGED 来源；API 为 T-PERM-069 增补同款声明——唯一事实入口=service-config/sync 接口声明通道+bootstrap 固定图，本接口对其来源不匹配拒绝不变、管理面 20055，SERVICE 维持 MANAGED）。外部服务接入先经 `type-definition/create` 建自有类型（如 `HR_ORG`、`BI_MENU`）并声明来源，再调用本接口同步。
- 外部业务服务同步自有资源类型时调用本接口（`resourceTypeCode` 须经类型级所有权门禁，见上条；T-ACCESS-018 的「公共类型外部同步合法」口径已随 T-PERM-052 类型级所有权定案收紧——外部服务同步自身用户/菜单须建自有类型，公共 `USER`/`MENU` 类型不再对同步开放）；事实链路七类型 `USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION/CONDITION` 由种子声明 `SYNC + syncSourceService=access-service`（内部来源，2026-09-05 内部来源统一；ADMIN_FILE 为 T-ADMIN-025 增补、TYPE_DEFINITION 为 T-PERM-051 增补、CONDITION 为 T-PERM-048 增补——管理页条件投影）——本接口对该七类型一律入口拒绝（来源不匹配，较旧口径「不撞投影行即放行」更严），其资源行由 user/org/menu 能力写编排（原 access.application）与角色/主体管理入口（T-ACCESS-019）经 LocalProjectionDomainService 同事务独占维护（ADMIN_FILE 文件夹投影走 ensureAdminFileFolder 两条事实链：bootstrap 预置 + 上传惰性登记；TYPE_DEFINITION 实例投影走写路径同事务维护 + bootstrap 自愈补种两条事实链，T-PERM-051）；原类型保留清单与行级投影防线（rejectIfLocalResource 20045）已收编删除，管理面 `create|batch-create|update|move|remove` 对 SYNC 类型（含七类型；API 同为种子声明 SYNC+access-service——T-PERM-069，事实入口 service-config 接口声明通道+bootstrap，管理面 20055 只读）统一 20055 只读（见 §12）。
- 外部业务服务的全量校准同步走 `resource-entity/full-sync`，不是逐条调用本接口。
- `sortOrder` 已随 resource 面字段退役删除（T-ACCESS-036）：本接口与 full-sync item 的旧载荷仍携带该字段 → 400（严格 mapper 未知字段拒绝，见 §12.1 退役注记）。

### 19.2 资源实体分领域全量校准 resource-entity/full-sync

> **完整空清单**：明确 `items=[]` 表示完整空 scope，通过身份、类型所有权与发布代次校验后，仅清理该同步范围的事实。缺失/null items 返回 HTTP 400；读取失败或分页未完成不得作为空快照提交。依赖贡献已随资源删除同事务收缩；自动授权回收由 072 交付，当前资源同步组件不表示物化回收已完成。

> **范围发布顺序**：`publicationGeneration` 必填，平台在事实写入与缺失删除前按 scope 原子拒旧；重试沿用原代次与完整快照。此代次独立于逐项 `syncVersion`，由源侧绑定一致快照，不能由 SDK 临时取当前时间生成。详见 §19.2.1 与[设计 §4.4](dependency-auto-grant.md#integration)。

`POST /api/access/resource-entity/full-sync`

请求体必须携带强制 scope，permission-center 只在该 scope 对应的同步来源范围内做差异校准，禁止默认按租户全量清理。

```json
{
  "scope": {
    "sourceService": "hr-service",
    "resourceTypeCode": "HR_ORG"
  },
  "publicationGeneration": "43",
  "items": [
    {
      "resourceCode": "2001",
      "codeType": "default",
      "name": "研发部",
      "parentResourceTypeCode": "HR_ORG",
      "parentResourceCode": "1000",
      "parentCodeType": "default",
      "status": 1,
      "extra": {
        "region": "CN"
      },
      "sourceEntityType": "hr_org",
      "sourceEntityId": "2001",
      "syncVersion": {
        "occurredAt": "2026-06-12T10:00:00.123",
        "sequenceNo": 1024
      }
    }
  ]
}
```

规则：

- 单请求表示 `scope.sourceService` 字段与 `scopeKey=resourceTypeCode={resourceTypeCode}` 共同限定范围内的完整事实；`sourceService` 独立承载，不拼入 `scopeKey`。
- full-sync item 的父资源定位与单条 sync 同口径（T-PERM-068）：父字段组以 `parentResourceCode` 非空为激活条件；`parentResourceTypeCode` 缺省回填 scope 中的 `resourceTypeCode`、`parentCodeType` 缺省 `default`（本句「默认同类型」自 T-PERM-068 起为真实实现语义——此前实现半传静默解挂与原文不符，已修）；显式 `parentResourceTypeCode` 异于 scope 类型 → 该 item `NON_RETRYABLE`/`PARENT_TYPE_MISMATCH`、不推进同步版本，同批其余项正常应用。实现解析父节点使用 `生效类型 + parentResourceCode + parentCodeType`。
- permission-center 以 `sync_metadata(entityKind=RESOURCE_ENTITY, sourceService, scopeKey)` 作为 full-sync ownership 范围；请求中存在则 upsert 并更新 metadata，请求中缺失的 metadata 对应事实按删除语义软删除。`resource_entity.owner_service_code/maintain_source` 不作为本接口的清理依据（`sync_key` 列已删除，2026-09-05）。
- `resource-entity/full-sync` 与既有 `service-config/sync` 是两条独立 ownership 通道。本接口只清理命中 `sync_metadata` scope 的同步事实，绝不按 `resourceTypeCode` 扫描删除资源，也不删除 `service-config/sync`、`MANUAL` 或其他维护来源创建的事实。
- 全量接口仍必须执行 source 身份校验、类型级所有权门禁（scope.resourceTypeCode 同 §19.1 单条口径）和旧版本 no-op 规则。
- 组织资源等有树依赖的数据应按足够小的 scope 调用，避免单请求过大。

#### 19.2.1 资源发布顺序字段与响应

资源 sync/full-sync 已实现本节协议；完整处理顺序见[自动授权设计 §4.4.1](dependency-auto-grant.md#integration)。071 的 SDK、生命周期与迁移交付状态以任务卡为准。

| 位置 | 字段 | 约束 |
|---|---|---|
| resource-entity/sync 顶层 | publicationGeneration | 可选十进制字符串，取值 1..9223372036854775807，无前导零/符号/小数；该 scope 切入顺序协议后必填 |
| resource-entity/full-sync 顶层 | publicationGeneration | 必填，同上；由源侧绑定完整快照，不可由 SDK 当前时间生成 |
| full-sync 顶层 | items | 必填非 null 数组，允许完整空数组；同一业务键重复项整请求拒绝 |
| 每个资源 item / sync 顶层 | syncVersion | 维持 occurredAt + sequenceNo；与发布代次共同验证，不能代替范围顺序 |

正常返回 SyncResultResp，沿用既有 retryClass，不新增数字业务错误码。旧发布代次使用 STALE_VERSION（accepted=true、applied=false、stale=true；reason=PUBLICATION_GENERATION_STALE）；缺代次、范围非法、同代次内容冲突分别 NON_RETRYABLE + PUBLICATION_GENERATION_REQUIRED / PUBLICATION_GENERATION_INVALID / PUBLICATION_GENERATION_CONFLICT。FULL item 逐键版本与快照矛盾使用 NON_RETRYABLE + SYNC_VERSION_CONFLICT，进入既有部分失败统计。请求级反序列化/校验异常仍走统一错误信封。

资源单条同业务键、同代次、同完整指纹已成功时返回 `STALE_VERSION/PUBLICATION_UNCHANGED`（accepted=true、applied=false、stale=true），明确表示已确认的原请求重试；真正旧请求仍为 PUBLICATION_GENERATION_STALE，两者不能混作资源前置成功。

FULL 缺失/null/空白 `publicationGeneration`、缺失/null `items` 在 HTTP DTO 校验阶段返回 400；已切换 scope 的增量省略代次则返回上述 `PUBLICATION_GENERATION_REQUIRED`。规范化后重复业务键整请求返回 `NON_RETRYABLE/DUPLICATE_BUSINESS_KEY`。旧 FULL 的明细全部计入 staleCount，不计 failedCount，且 deactivatedCount=0。

纯增量旧协议仅适用于未切换 scope。切换后不得无代次降级；同一来源按租户、资源类型独立切换。FULL 重试须保持原代次与完整请求，后来已成功的更新使旧 FULL 重试失效时，应由源侧重新采集发布，不能仅改数字重放旧清单。

### 19.3 同步接口通用响应与错误分类

**事务与版本消费（T-PERM-074）**：依赖缺失、成员关系归属冲突、角色互斥及资源 DISABLE 目标缺失的失败项不消费 `syncVersion`，补齐依赖或修正阻碍后可重发原版本；已成功应用的相同/旧版本按 STALE 返回。版本比较、事实修改与账本状态回填在同一入口事务内完成，技术异常一并回滚。full-sync 已进入逐项阶段且以 ItemResult 返回的业务拒绝逐项记录，成功项与 scope 缺失清理共同提交；入口预检拒绝不进入处理，抛出的异常（含本地投影不可变等业务异常与技术异常）整个请求回滚，不提交部分成功。该逐项版本规则不提供 FULL 快照顺序保护，发布代次仍由 071 承接。

历史上失败但已消费的版本不因本次修复自动恢复；只能按运维手册核实具体业务键、失败版本、最后成功事实后定点修复，禁止全表清账或凭空提高版本。

所有 sync/full-sync 接口成功时仍使用统一响应壳。同步语义结果放在 `data` 中：

```json
{
  "accepted": true,
  "applied": true,
  "stale": false,
  "retryClass": null,
  "reason": null
}
```

正常返回 `SyncResultResp` 的 sync/full-sync 结果由 Controller 以 `code=200` 信封包裹（含返回结果形式的 `SECURITY_DENIED` 与条目级失败），须进一步读取 `data.accepted`、`data.stale`、`retryClass` 和 FULL 明细。请求级异常不属于该结果协议：参数校验失败、保留键/本地投影不可变（20045）及技术异常走统一异常信封，先检查 HTTP 状态与信封 code，不得无条件读取 data 或视为成功。已进入事务后抛异常会整体回滚。以下为正常返回的同步业务拒绝：

```json
{
  "accepted": false,
  "applied": false,
  "stale": false,
  "retryClass": "DEPENDENCY_MISSING",
  "reason": "PARENT_RESOURCE_NOT_FOUND"
}
```

`retryClass` 固定枚举：`RETRYABLE`、`DEPENDENCY_MISSING`、`NON_RETRYABLE`、`SECURITY_DENIED`、`STALE_VERSION`。`STALE_VERSION` 是唯一 `accepted=true` 的失败分类（`accepted=true, applied=false, stale=true`，reason=`SYNC_VERSION_STALE`）——表示请求已接受但未覆盖旧版本。调用方判定失败以 `accepted=false || stale=true` 为准，勿依赖信封 code。

旧版本 no-op 必须返回成功响应壳，调度器据 `stale=true` 直接把任务置为 `SUCCESS`，不得重试：

```json
{
  "accepted": true,
  "applied": false,
  "stale": true,
  "retryClass": "STALE_VERSION",
  "reason": "SYNC_VERSION_STALE"
}
```

> full-sync 接口顶层字段同 sync；批量明细可选放入 `data.detail`，结构详见 §19.6。**禁止**为 full-sync 引入独立顶层响应类型（如已删除的 `FullSyncResultResp`）。

### 19.4 主体、角色、用户角色同步接口

外部业务服务的主体/角色/成员关系同步使用专用 sync/full-sync 接口，不通过 `resource-entity/sync`，也不复用角色授权管理接口表达同步语义。请求中的 `subjectTypeCode`/`roleTypeCode`/`sourceType` 均为调用方自有类型，禁止使用 AccessMesh 内部保留键（`LOCAL_USER`/`ORG|POSITION`/`SYS_USER_ORG`，20045 拒绝；subject 侧原 `ADMIN_USER` 已随 T-ACCESS-016 更名 `LOCAL_USER`，旧名经类型解析失败直接拒绝）——内部管理事实由 user/org/menu 能力写编排同事务维护本地投影，不经过 sync 链路。

| 接口 | 用途 | scopeKey / businessKey |
|------|------|------------|
| `POST /api/access/abstract-user/sync` | 主体 UPSERT/DISABLE/DELETE | businessKey：`subjectTypeCode={subjectTypeCode}&subjectExternalId={subjectExternalId}` |
| `POST /api/access/abstract-user/full-sync` | 全量主体校准 | scopeKey：`subjectTypeCode={subjectTypeCode}` |
| `POST /api/access/abstract-role/sync` | 角色 UPSERT/DISABLE/DELETE | businessKey：`roleTypeCode={roleTypeCode}&roleExternalId={roleExternalId}` |
| `POST /api/access/abstract-role/full-sync` | 全量角色校准 | scopeKey：`roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |
| `POST /api/access/user-role/sync` | 成员关系 BIND/UNBIND | businessKey：`subjectTypeCode={subjectTypeCode}&subjectExternalId={subjectExternalId}&roleTypeCode={roleTypeCode}&roleExternalId={roleExternalId}&relationKey={relationKey}` |
| `POST /api/access/user-role/full-sync` | 全量成员关系校准 | scopeKey：`sourceType={sourceType}&roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |

约束：

- 成员关系 sync/full-sync 仅接受调用方自有 `sourceType` 与 `roleTypeCode` 组合（服务身份 + 类型白名单校验）；`SYS_USER_ORG`/`ORG`/`POSITION` 为 AccessMesh 内部保留键，20045 拒绝。功能角色分配走正式用户角色管理接口和 `ROLE:MANAGE` 门禁。
- `relationKey` 为成员关系的关联角色业务键。写入 `businessKey` 时必须按 §19.7 编码。permission-center 按调用方声明的角色类型 + 外部 ID 解析关联 `abstract_role.id`，写入 `user_role.relation_id`。`relation_id` 表示关联角色 ID，不对外暴露为 API 入参。
- 单次 sync 接口的 `operation` 使用混合严格语义：禁用为 `DISABLE`，删除为 `DELETE`，成员移除为 `UNBIND`。
- full-sync 接口均为单请求全量校准接口，必须携带强制 scope，只在 scope 内补齐缺失并清理多余同步事实。
- 所有 sync/full-sync 接口的调度分类以 §19.3 为准；错误响应返回 `RETRYABLE`、`DEPENDENCY_MISSING`、`NON_RETRYABLE`、`SECURITY_DENIED`，旧版本 no-op 使用成功响应并返回 `STALE_VERSION`。

主体同步请求：

```json
{
  "operation": "UPSERT",
  "subjectTypeCode": "EMP",
  "subjectExternalId": "10001",
  "name": "张三",
  "enabled": true,
  "extra": {
    "employeeNo": "zhangsan"
  },
  "sourceService": "hr-service",
  "sourceEntityType": "hr_employee",
  "sourceEntityId": "10001",
  "syncVersion": {
    "occurredAt": "2026-06-12T10:00:00.123",
    "sequenceNo": 1024
  }
}
```

主体全量校准请求：

```json
{
  "scope": {
    "sourceService": "hr-service",
    "subjectTypeCode": "EMP"
  },
  "items": [
    {
      "subjectExternalId": "10001",
      "name": "张三",
      "enabled": true,
      "extra": {
        "employeeNo": "zhangsan"
      },
      "sourceEntityType": "hr_employee",
      "sourceEntityId": "10001",
      "syncVersion": {
        "occurredAt": "2026-06-12T10:00:00.123",
        "sequenceNo": 1024
      }
    }
  ]
}
```

角色同步请求：

```json
{
  "operation": "UPSERT",
  "roleTypeCode": "TEAM_ROLE",
  "roleExternalId": "2001",
  "name": "研发部",
  "parentRoleTypeCode": "TEAM_ROLE",
  "parentRoleExternalId": "1000",
  "treeRootExternalId": "1",
  "status": 1,
  "sortOrder": 10,
  "extra": {
    "teamCode": "RND"
  },
  "sourceService": "hr-service",
  "sourceEntityType": "hr_team",
  "sourceEntityId": "2001",
  "syncVersion": {
    "occurredAt": "2026-06-12T10:00:00.123",
    "sequenceNo": 1024
  }
}
```

角色全量校准请求：

```json
{
  "scope": {
    "sourceService": "hr-service",
    "roleTypeCode": "TEAM_ROLE",
    "treeRootExternalId": "1"
  },
  "items": [
    {
      "roleExternalId": "2001",
      "name": "研发部",
      "parentRoleExternalId": "1000",
      "status": 1,
      "sortOrder": 10,
      "extra": {
        "teamCode": "RND"
      },
      "sourceEntityType": "hr_team",
      "sourceEntityId": "2001",
      "syncVersion": {
        "occurredAt": "2026-06-12T10:00:00.123",
        "sequenceNo": 1024
      }
    }
  ]
}
```

用户角色同步请求：

```json
{
  "operation": "BIND",
  "sourceType": "HR_MEMBER",
  "subjectTypeCode": "EMP",
  "subjectExternalId": "10001",
  "roleTypeCode": "TEAM_ROLE",
  "roleExternalId": "3001",
  "relationKey": "TEAM_ROLE:2001",
  "validFrom": null,
  "validTo": null,
  "sourceService": "hr-service",
  "sourceEntityType": "hr_member",
  "sourceEntityId": "10001:3001",
  "syncVersion": {
    "occurredAt": "2026-06-12T10:00:00.123",
    "sequenceNo": 1024
  }
}
```

用户角色全量校准请求：

```json
{
  "scope": {
    "sourceService": "hr-service",
    "sourceType": "HR_MEMBER",
    "roleTypeCode": "TEAM_ROLE",
    "treeRootExternalId": "1"
  },
  "items": [
    {
      "subjectTypeCode": "EMP",
      "subjectExternalId": "10001",
      "roleTypeCode": "TEAM_ROLE",
      "roleExternalId": "3001",
      "relationKey": "TEAM_ROLE:2001",
      "validFrom": null,
      "validTo": null,
      "sourceEntityType": "hr_member",
      "sourceEntityId": "10001:3001",
      "syncVersion": {
        "occurredAt": "2026-06-12T10:00:00.123",
        "sequenceNo": 1024
      }
    }
  ]
}
```

> `items[].roleTypeCode` 为必填字段，且必须与 `scope.roleTypeCode` 严格相等；不一致时该 item 返回 `NON_RETRYABLE`（`reason=ROLE_TYPE_CODE_MISMATCH_WITH_SCOPE`），不进入 `markStatus` 路径。调用方必须按 binding 对应的角色类型拆分为多个 envelope，每个 envelope 内 item.roleTypeCode 与 scope.roleTypeCode 对齐。

> **角色互斥守卫（T-PERM-064，sync/full-sync 共用）**：BIND 将新增「启用且有效期覆盖当前时刻」的持有时（新建行或非当前有效行重激活），授予后有效角色集命中 ROLE_MUTEX 对 → 该 item 返回 `NON_RETRYABLE`（`reason=ROLE_MUTEX_CONFLICT`）零写库——走通道逐条错误信封而非管理面 20062 整批语义；已当前有效行的幂等改期与 future 生效不触发检查。**full-sync 同批同用户多 BIND 经请求级批内累积判定**（postState=现有效∪本批已 apply 的新增有效持有∪本目标，grok 外评 P1 修复）——同批互斥两端第二条即拒，不依赖缓存可见性。UNBIND 不适用。

非资源实体 full-sync 规则：

- `abstract-user/full-sync` 对比 `sync_metadata(entityKind=ABSTRACT_USER, sourceService, scopeKey)`。
- `abstract-role/full-sync` 对比 `sync_metadata(entityKind=ABSTRACT_ROLE, sourceService, scopeKey)`；item 的父角色默认与 scope 中的 `roleTypeCode` 同类型，如需跨类型必须显式传 `parentRoleTypeCode`。
- `user-role/full-sync` 对比 `sync_metadata(entityKind=USER_ROLE, sourceService, scopeKey)`；请求缺失的旧关系按 `UNBOUND` 处理，不删除正式功能角色分配。
- 所有 full-sync 接口只清理命中 `sync_metadata` 的同步事实，不扫描删除人工维护或正式管理 API 创建的事实。

### 19.5 服务间认证（sync/full-sync 专用）

外部业务服务通过服务身份凭证建立 SERVICE 上下文后调用 sync/full-sync 接口（无前端会话与 Gateway 鉴权）。访问控制语义见 `access-service-architecture.md` §18.3 安全策略矩阵：

| 入口 | 调用方要求 | 关键约束 | 实现 |
|---|---|---|---|
| `/api/access/**/sync`、`/full-sync` | 已验证服务身份 | `sourceService` 必须等于已验证服务身份（凭证通过后绑定的 X-Service-Code，`SyncAuthVerifier` 从上下文比对） | SERVICE 上下文；不匹配 → SECURITY_DENIED |

- 服务身份由 `access-service` 统一校验（内部凭证验证通过后绑定 `X-Service-Code` 为凭证持有者声明的服务身份，防无凭证外部伪造），调用方**不得**自行声明或伪造服务身份。
- 内部同步子系统（`sys_sync_task` 调度、`SyncTaskFeignClient`/`FeignInternalSyncInterceptor`、`X-Internal-Secret` 调度链路）已随 T-ACCESS-005 删除；管理事实由 user/org/menu 能力写编排同事务维护本地投影，不再经 sync 接口进入。
- 仅当配置了服务凭证时服务身份校验才生效；未配置的部署不会启动失败，但对应接口按矩阵 fail-closed。

### 19.6 FullSyncDetail 结构

full-sync 接口在顶层成功响应壳的基础上，额外在 `data.detail` 中返回批量明细。结构如下：

```json
{
  "accepted": true,
  "applied": true,
  "stale": false,
  "retryClass": null,
  "reason": null,
  "detail": {
    "appliedCount": 3,
    "staleCount": 0,
    "failedCount": 0,
    "deactivatedCount": 1,
    "itemResults": [
      { "businessKey": "subjectTypeCode=USER&subjectExternalId=u1", "applied": true,  "stale": false, "retryClass": null, "reason": null },
      { "businessKey": "subjectTypeCode=USER&subjectExternalId=u2", "applied": false, "stale": false, "retryClass": "DEPENDENCY_MISSING", "reason": "PARENT_NOT_FOUND" }
    ]
  }
}
```

字段：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `appliedCount` | int | 成功 apply 的 item 数量 |
| `staleCount` | int | 因 syncVersion 较旧而被钝化的 item 数量 |
| `failedCount` | int | 因依赖/参数/权限失败的 item 数量 |
| `deactivatedCount` | int | scope 内未出现而被自动 DELETE/UNBIND 的业务键数量 |
| `itemResults[]` | array | 每个 item 的明细结果 |

`itemResults[]` 元素：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `businessKey` | string | 业务键（按 §19.7 编码） |
| `applied` | boolean | 是否成功落库 |
| `stale` | boolean | 是否因版本较旧被钝化 |
| `retryClass` | string\|null | 失败/钝化分类（与 §19.3 同枚举） |
| `reason` | string\|null | 可读原因 |

顶层字段联动规则：

- 全部成功：`accepted=true, applied=true, retryClass=null`，`detail.failedCount=0`。
- 部分失败：`accepted=true, applied=false, retryClass=RETRYABLE, reason=FULL_SYNC_PARTIAL_FAILURE`，明细在 `detail.itemResults` 中按 item 给出原因。
- 全局拒绝（身份/scope 不合法）：`accepted=false, applied=false, retryClass∈{SECURITY_DENIED,NON_RETRYABLE}`，并在 `detail.failedCount` 中记拒绝条数。

> sync 接口固定 `data.detail = null`，调度器据顶层 retryClass/applied/stale 判定 outcome；full-sync 不引入额外顶层 DTO。

### 19.7 业务键与 scopeKey 规范

所有同步任务、`sync_metadata` 和 full-sync 差异校准必须使用同一套规范化 key，禁止各模块自行拼接 `:`、`;` 等自由格式。

通用规则：

- `businessKey` 和 `scopeKey` 均使用 `key=value&key=value` 的有序参数串。
- 参数名使用 camelCase，顺序由本节样例固定；缺省字段不得省略，除非样例未包含该字段。
- 参数值使用 URL percent-encoding；因此 `relationKey=ORG:2001` 必须写为 `relationKey=ORG%3A2001`。
- key 字符串不包含 `tenantId`、`sourceService`、`entityKind`，这些维度由表字段或请求 scope 单独承载。
- `sync_metadata.sync_key` 使用 `sourceService|entityKind|businessKey`，仅用于来源内稳定定位，不参与对外 API 契约。
- 关系库中必须同时保存 key 原文和 SHA-256 lowercase hex。原文用于排查，唯一约束与高频查询使用 hash 字段，避免长外部 ID 导致索引超长。

`businessKey` 固定格式：

| entityKind | businessKey |
| ---------- | ----------- |
| `ABSTRACT_USER` | `subjectTypeCode={subjectTypeCode}&subjectExternalId={subjectExternalId}` |
| `ABSTRACT_ROLE` | `roleTypeCode={roleTypeCode}&roleExternalId={roleExternalId}` |
| `RESOURCE_ENTITY` | `resourceTypeCode={resourceTypeCode}&resourceCode={resourceCode}&codeType={codeType}` |
| `USER_ROLE` | `subjectTypeCode={subjectTypeCode}&subjectExternalId={subjectExternalId}&roleTypeCode={roleTypeCode}&roleExternalId={roleExternalId}&relationKey={relationKey}` |

`scopeKey` 固定格式：

| full-sync 接口 | scopeKey |
| -------------- | -------- |
| `abstract-user/full-sync` | `subjectTypeCode={subjectTypeCode}` |
| `abstract-role/full-sync` | `roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |
| `resource-entity/full-sync` | `resourceTypeCode={resourceTypeCode}` |
| `user-role/full-sync` | `sourceType={sourceType}&roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |

`targetStatus` 固定映射：

| entityKind | operation | targetStatus |
| ---------- | --------- | ------------ |
| `ABSTRACT_USER` / `ABSTRACT_ROLE` / `RESOURCE_ENTITY` | `UPSERT` | `ACTIVE` |
| `ABSTRACT_USER` / `ABSTRACT_ROLE` / `RESOURCE_ENTITY` | `DISABLE` | `DISABLED` |
| `ABSTRACT_USER` / `ABSTRACT_ROLE` / `RESOURCE_ENTITY` | `DELETE` | `DELETED` |
| `USER_ROLE` | `BIND` | `ACTIVE` |
| `USER_ROLE` | `UNBIND` | `UNBOUND` |

### 19.8 服务接口全量同步 service-config/sync

`POST /api/access/service-config/sync`

```json
{
  "serviceCode": "access-service",
  "basePath": "/admin",
  "syncMode": "FULL",
  "groups": [
    {
      "groupCode": "user",
      "groupName": "用户管理",
      "apis": [
        {
          "name": "查询用户列表",
          "httpMethod": "POST",
          "path": "/api/user/list",
          "resourceCode": "admin:user:list",
          "description": "用户列表查询"
        }
      ]
    }
  ]
}
```

规则：

- 首期只支持 `syncMode=FULL`。FULL 模式下，以本次上报内容作为该 `serviceCode` 的完整事实来源。（T-PERM-027 收口：`ServiceConfigSyncReq.syncMode` 校验层 `@Pattern("FULL")` 拒绝其他值，增量策略已删除。）
- `ApiItem.operationCode` 字段已退役（T-PERM-053，2026-09-05 删除）：接口权限模型为「API 资源实例 × ACCESS 单操作」，无操作粒度——敏感度区分靠把敏感路由拆分为独立 API 资源实例、仅向目标角色授 ACCESS。仍携带该字段的旧请求体经全局严格 ObjectMapper（未知字段拒绝，common `cacheObjectMapper` 顶替 Boot 宽容默认）反序列化失败返回 HTTP 400；前后端同批锁步发布（唯一活跃调用方=管理前端服务接口配置页），不做 mapper 放宽。
- 权限中心自动拼接 `basePath + path` 得到 Gateway 原始路径。
- 新接口自动创建 API 类型 `resource_entity` 和 `resource_api_mapping`。
- `service-config/sync` 自动创建的 API 资源必须写入 `resource_entity.ownerServiceCode=serviceCode`、`maintainSource=SERVICE_SYNC`（`resource_entity.sync_key` 列已删除：写-only 死列全仓零读取方，2026-09-05 T-PERM-052 清理；资源依赖表的同名列不受影响）。
- FULL diff 只能软删除同一 `ownerServiceCode + maintainSource=SERVICE_SYNC` 范围内本次缺失的 API 映射和自动创建资源。
- 已不存在接口软删除映射和自动创建的 API 资源，不删除 `maintainSource=MANUAL` 或其他维护来源的资源。
- API 类型已由 DDL 种子声明 `SYNC + syncSourceService=access-service`（T-PERM-069，2026-09-18 Q-008「仅 API 收紧」定案）：本通道与 bootstrap 固定图即 API 资源的唯一事实入口（领域直写，不经资源管理面门禁）；资源管理面 `create|batch-create|update|move|remove` 对 API 类型一律 20055，外部 `resource-entity/sync|full-sync` 对其来源不匹配拒绝。SERVICE 类型维持 MANAGED（新 SERVICE 行唯一通道=管理面手工建行——按服务实例级授权的目标行）。

### 19.9 同步类型白名单配置（service-config/save 的 extra.syncTypes）

外部 sync/full-sync 只能写入本服务声明的类型命名空间（服务-类型白名单，fail-closed）。白名单存放于 `service-config/save` 的 `extra` 字段：

```json
{
  "syncTypes": {
    "subjectTypeCodes": ["EMP"],
    "roleTypeCodes": ["TEAM_ROLE"],
    "sourceTypes": ["HR_MEMBER"]
  }
}
```

语义（与 `SyncTypeGuard` 实现一致）：

- **三个分类均可选**；某分类缺失或为空数组 = 该分类无任何权限（对应链路同步全部 `SECURITY_DENIED`）。
- **资源维度已退役（T-PERM-052，2026-09-05 定案）**：原 `resourceTypeCodes` 字段删除——resource-entity 同步通道改由**类型级所有权声明**判定（`type-definition` 的 `extra.managedMode=SYNC` + `extra.syncSourceService`，见 §19.1），服务侧不再声明资源类型；保存含 `resourceTypeCodes` 字段直接拒绝（防旧结构配置误导）。
- **三条链路的最小映射**：主体同步校验 `subjectTypeCode`；角色同步校验 `roleTypeCode`；用户角色同步校验写入事实使用的 `subjectTypeCode` + `roleTypeCode` + `sourceType`（`relationKey` 角色类型为引用，不要求声明）；资源同步走类型级所有权门禁（不经本白名单）。
- **GROUP_ROLE 例外（T-PERM-043）**：角色同步在白名单之外恒拒 `GROUP_ROLE`（`ROLE_TYPE_MISMATCH(20022)`，先于白名单判定）——即使服务声明了 `roleTypeCodes: ["GROUP_ROLE"]` 也不生效。
- **服务状态**：`status != 1`（禁用）/未注册/已软删时该服务全部 sync/full-sync 拒绝——subject/role/user_role 三通道由本白名单校验，resource-entity 通道由类型所有权门禁校验（来源服务注册+启用，§19.1；评审批次补强后两轨语义一致）。
- **校验顺序**：使用经过认证的服务身份（凭证通过后绑定的 `X-Service-Code`）查询配置，不信任请求体；未通过统一返回 `SECURITY_DENIED`（`SERVICE_TYPE_NOT_ALLOWED`），内部日志记录真实原因，不向调用方返回白名单明细。
- **保留键纵深**：即使白名单错误声明 `LOCAL_USER`/`ORG`/`POSITION`/`SYS_USER_ORG` 等 AccessMesh 保留键，入口仍以 20045 拒绝。
- **结构校验**：`service-config/save` 保存时校验 `syncTypes` 必须为对象、内部仅允许 subjectTypeCodes/roleTypeCodes/sourceTypes 三个字段（未知字段拒绝，含已退役的 `resourceTypeCodes`）、各分类（如存在）必须为非空白字符串数组；结构非法保存失败（20044）。缺失配置在运行时按无权限处理（fail-closed），不视为允许全部。
- **上线准备（fail-closed 发布顺序）**：先为各同步服务通过 `service-config/save` 补齐 `syncTypes` 声明（并确认 `status=1`），再部署严格校验代码；未声明类型的存量服务在严格校验上线后同步全部拒绝，属预期行为。

### 19.10 独立依赖 manifest（071 已交付）

> HTTP 入口、声明编译与发布状态事务已实现并经真实凭证验证；资源/定义变更 dirty 联动与保全迁移已实现；可选 SDK 已提供独立发布与显式资源前置，角色物化由 072 完成；071 已于 2026-09-21 收口交付。

`POST /api/access/integration/permission-manifest/full-sync`，仅服务身份入口，按 070 精确 M2M 白名单；tenant 与 service 从已认证上下文取得，不接受 body 中的 serviceCode。来源服务须注册、启用；资源/操作存在性及类型所有权由服务端校验。

```json
{
  "schemaVersion": 1,
  "publicationGeneration": "42",
  "revision": "release-42",
  "dependencies": [
    {
      "declarationKey": "monthly-template",
      "source": {"resourceTypeCode": "REPORT", "resourceCode": "sales-monthly", "codeType": "default"},
      "sourceOperationCodes": ["VIEW"],
      "requires": [
        {
          "target": {"resourceTypeCode": "REPORT", "resourceCode": "sales-template", "codeType": "default"},
          "operationCodes": ["VIEW"]
        }
      ],
      "description": "月报查看依赖模板查看"
    }
  ]
}
```

schemaVersion 必须为整数 1；publicationGeneration 为 §19.2.1 同款正整数字符串，与资源发布代次独立。revision 必填非空、最长 128 字符，仅标识来源版本，不能替代排序。dependencies 必填非 null，空数组表示该服务完整空声明；declarationKey 必填、最长 128 字符，在请求中唯一且跨发布稳定。source/target 复用 ResourceKey；类型/操作码按现役严格大写规则，codeType 缺省 default、宽度与资源契约一致。sourceOperationCodes 缺失/null/空数组统一为任意操作；非空必须恰有一个非空操作码。requires 必填非空，同一声明内 target 业务键唯一；operationCodes 必填非空、排序去重。description 可空、最长 512 字符。

形状错误、重复 declarationKey/target、非法 schemaVersion 或代次在声明写入前整请求拒绝。有效请求按 declarationKey + target 业务键展开为编译项，同一组可有 RESOLVED 与 REJECTED 目标，拒绝项保留诊断且不产生边；source 不合法时该组全部目标拒绝。声明存储的逻辑唯一键是 tenant + 服务身份 + declarationKey + target 业务键；新 FULL 删除本服务缺失项，不能通过清空其他来源的编译贡献达成替换。

返回现有 `R<SyncResultResp>` 与 FullSyncDetail。itemResults 的 businessKey 采用 SyncKeyCodecUtil 规范：`declarationKey=...&targetResourceTypeCode=...&targetResourceCode=...&targetCodeType=...`，各值 percent-encoded；统计按展开后的目标项计，deactivatedCount 为缺失而删除的声明目标项数量。item applied 表示该声明成功 RESOLVED，REJECTED 返回依赖缺失/非重试分类和具体原因（RESOURCE_MISSING / TYPE_MISSING / OPERATION_INVALID / CROSS_OWNER / SELF_DEPENDENCY / CYCLE）；同语义 hash 重发的 unchanged 项返回 `applied=false / stale=true / reason=MANIFEST_UNCHANGED`（该批图已确认的幂等语义，与资源侧 PUBLICATION_UNCHANGED 同族，可继续不重试）。上层部分失败仍为 FULL_SYNC_PARTIAL_FAILURE，不能只看 HTTP 200。旧代次与同代次请求冲突沿 §19.2.1。

同代次比较完整规范化请求 hash（含 revision、description，排除代次）；语义 hash 仅基于 declarationKey、source/触发操作与目标/操作集合，不含 description/revision。语义 hash 可用于避免重复编译，完整 hash 用于不可变重试，二者不可互相代替。较新代次同图仍保存 revision/description；原代次原请求仅在该代次仍是当前已接受代次（未被更新发布覆盖）时可重判 PARTIAL/dirty——已被更晚成功代次覆盖后旧代次重放按 §19.2.1 拒旧，不再触发重判。环路检查以本次替换后保留的图为基础，先保留未变化的已解析贡献，再按声明完整规范键顺序尝试变化项，后到成环项 REJECTED。

#### 19.10.1 可选 registration SDK

`perm-registration-spring-boot-starter` 默认不装配，配置 `perm.registration.enabled=true` 后提供 PermissionRegistrationPublisher。复用 `perm.credential-id/credential-secret` 与显式 true/false 的 `perm.allow-insecure`，不另建密钥；默认单租户静态发布另用 `perm.tenant-id`、`perm.service-code`。多租户逐调用传 RegistrationTarget，自带完整凭证，既有拦截器不覆盖它。

- `capture(generation, revision, PermissionManifestProvider)` 只读取一次依赖并固定输入；generation 来自来源提交顺序。`publish(target, snapshot)` 独立发送 manifest；重试传原 snapshot，不重读 Provider。
- `prepareAndPublish(target, snapshot, ResourcePreparation)` 复用调用方显式资源输入/同步程序。前置任一业务失败、部分失败或 PUBLICATION_GENERATION_STALE 停止清单发布并保留诊断；同键同代次同内容的 PUBLICATION_UNCHANGED 可继续。资源成功、依赖失败不回滚资源；两步不是分布式事务。
- 本地校验形状、重复键、重复目标、自依赖与清单内环；资源/操作存在、所有权与联合图仍由平台终验。HTTP/信封错误抛错；正常 SyncResultResp 返回完整业务结果，不能只看 HTTP 200。
- 静态启动发布仅在设置 `perm.registration.manifest-location` 时执行；JSON 与请求 DTO 同构且自身绑定代次。全流严格解析，缺文件、坏 JSON/尾随内容、缺字段或发布未完整成功均失败，不将异常当空清单。无文件位置时不在启动时发送。
- TenantRegistrationProvider 返回逐租户的目标与完整清单，先 capture 再逐项发布；SDK 不持共享可变租户状态。无后台重试、队列或平台生成代次。

[example-service 示例](../../example-service/examples/permission-manifest.md)提供静态文件与动态调用说明；可选 profile 为 permission-manifest，默认不发布且不改变 Gateway 鉴权。

## 20. 错误码与错误原因
### 20.1 管理面家族错误码段（10001-10599 / 90001-99999）
| 子段 | 含义 |
|------|------|
| 10001-10099 | 用户查询 (page/detail/member-candidates 业务错误) |
| 10100-10119 | 候选用户查询 |
| 10120-10169 | 用户创建/更新 |
| 10170-10209 | 用户删除/启停 |
| 10210-10229 | 用户密码重置 |
| 10300-10399 | 组织 CRUD |
| 10400-10499 | 用户-组织关系 |
| 10500 | 用户-角色代理段（历史分配，无活跃错误码；`10111` 已随端点删除退役、码值不复用；`10501-10599` 文件模块：`10501` 不存在 / `10502` 上传失败 / `10503` 超限 / `10504` 类型不允许 / `10505` 删除失败（删除顺序反转后仅保留枚举，正常链路不再抛出）/ `10506` 路径非法（T-ADMIN-023）/ `10507` 读取失败（T-ADMIN-023）） |
| 10600-19999 | 保留给管理面家族后续模块 (字典/通知/任务/审计等) |

具体码值由单册 `AccessErrorCode` 落地（T-ACCESS-038 合一，原两域枚举已删）; 90001-99999 段 (参数校验/系统异常) 由 `common` 模块统一定义.

### 20.2 perm 家族错误原因建议（reason 词表）
| reason                  | 说明                                     |
| ----------------------- | ---------------------------------------- |
| `USER_NOT_FOUND`        | 主体不存在                               |
| `USER_DISABLED`         | 主体停用                                 |
| `ROLE_DISABLED`         | 命中角色停用                             |
| `RESOURCE_DISABLED`     | 资源停用                                 |
| `SERVICE_DISABLED`      | 服务停用                                 |
| `NO_ROLE`               | 无有效角色                               |
| `NO_PERMISSION`         | 无授权                                   |
| `CONDITION_NOT_MET`     | 条件不满足                               |
| `CONFLICT_DETECTED`     | 权限互斥导致失效                         |
| `API_NOT_REGISTERED`    | 接口未注册                               |
| `OBJECT_KEY_NOT_FOUND`  | 标准业务键无法定位对象                   |
| `OBJECT_KEY_DUPLICATED` | 标准业务键命中多个对象，需修正数据唯一性 |

## 21. 验收标准
### 21.1 perm 家族验收标准
- 当 Gateway 使用默认配置回调权限中心时，系统应调用 `POST /api/access/auth/check-interface` 并得到稳定响应。
- 当外部系统只知道用户 `subjectTypeCode + subjectExternalId`、资源 `resourceTypeCode + resourceCode`、操作 `operationCode` 时，系统应能完成鉴权判定。
- 当管理端查询任何列表接口时，响应 `data` 应始终是对象，且列表数据位于 `data.items`。
- 当调用批量删除接口时，系统应接受 `{ "ids": [...] }` 并执行软删除，不暴露 RESTful Path 参数。
- 当同一路径映射存在于多个租户时，接口级鉴权应只在 `X-Tenant-Id` 对应租户内匹配。
- 当 SDK 调用权限中心时，Feign 返回类型应与服务端 Controller 响应 DTO 完全一致。
- 当调用任意接口时，请求体不得包含 `tenantId`；租户统一从 `X-Tenant-Id` 读取。
- 当管理面查询用户能管理哪些组织、角色、菜单时，应通过 `POST /api/access/auth/query-resources` 返回资源业务键集合。
- 当 example-service 查询报表范围权限时，应通过 `POST /api/access/auth/query-scopes` 返回同一主资源下的直接范围权限和子权限并集。

### 21.2 管理面家族验收标准
后端实现本契约接口（原历史计数「19 个」不维护, 现状以 `HttpApiPathSnapshotTest` 快照为准; Phase 2 已于 2026-08-31 完成）后, 必须满足:

1. **字段对齐**: 前端 `frontend/src/api/user-manage.ts` 中所有类型与本契约 record 字段名/类型一一对齐, 不允许不一致.
2. **门禁**: 所有写操作经 `AdminPermissionValidator` 本地调用 `PermQueryEngine`; 实现不短路判断 (除档案字段自我豁免与自助改密通道——T-PERM-067 Q-002 收窄口径: 启停/删除不豁免).
3. **本地投影**: 所有写操作 (除 /user/reset-password, /user-org/set-primary) 在主事务内维护对应权限投影与 `permission_change_log`。
4. **错误码段**: 管理面家族业务错误使用 10001-19999 段, 系统错误使用 90001-99999 段; 单册 `AccessErrorCode` 不重复定义系统段.
5. **响应壳统一**: 所有接口返回 `R<T>`, 列表不直接返回数组 (由 `RResponseAdvice` 强制); ~~现有违反此规则的接口 (例如 `/api/access/org/tree` 直接返回 `List<OrgResp>`)~~ 已清零（`/api/access/org/tree` 已由 T-ADMIN-021 切 `R<ItemsResp<OrgResp>>`；台账见附录 B）.
6. **异常映射**: 业务拒绝抛 `BizException`; 安全拒绝抛 `SecurityException`; 技术故障抛 `SystemException`. 不允许用 `SecurityException` 表达"资源不存在".
7. **默认树身份目录边界**: `/api/access/user/create` (带 orgId), `/api/access/user/delete`, `/api/access/user/enable`, `/api/access/user/reset-password`, `/api/access/user-org/set-primary` 必须在 AppService 内做默认树边界二次校验, 失败抛 `BizException`.
8. **投影所有权**: subject/role/user_role 侧保留业务键（`LOCAL_USER`/`ORG|POSITION`/`SYS_USER_ORG`）不得被外部 sync/full-sync 写入，失败抛 `BizException(20045)`；resource 侧为类型级所有权（T-PERM-052，2026-09-05；T-ADMIN-025 增 ADMIN_FILE、T-PERM-051 增 TYPE_DEFINITION）：事实链路七类型 USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION/CONDITION （T-PERM-048 增 CONDITION——管理页条件投影，仅 source=MANAGED）种子声明 SYNC+access-service——外部同步被类型所有权门禁以 `RESOURCE_TYPE_OWNERSHIP_DENIED`（同步拒绝响应）拒绝、管理面资源 CRUD 拒绝 20055、所有权声明变更/类型删除冲突拒绝 20056，原行级 `owner=access-service` 检查已收编删除；API 为 T-PERM-069 增补同款种子声明（唯一事实入口=service-config/sync 接口声明通道+bootstrap 固定图，管理面 20055；SERVICE 维持 MANAGED）。外部增量/全量同步仍使用 `sync_metadata` 做版本乱序保护。

## 22. 已确认决策（设计沉淀）
### 22.1 perm 家族已确认决策
1. **租户来源**：只使用 `X-Tenant-Id` 和安全上下文，请求体不保留 `tenantId`。
2. **对象定位**：取消通用 `Ref` 对象，使用固定扁平字段和标准业务键。
3. **授权入口**：授权页面写链路收敛为**唯一写入口 `POST /api/access/role-resource-permission/apply-grant-plan`**（2026-08-02 单入口收敛，收窄：单事务原子 + 受影响行数断言，无 CAS/幂等表/clientRequestId）。`save/revoke/children/add-child/remove-child` **已删除（2026-08-27 端点退役，Controller 无映射 404，不留兼容层；删除/新增/查询语义分别由 apply-grant-plan 的 removes/creates 与 list includeChildren 覆盖）**；`update-child/children-save/rebuild` 从未实现。
4. **接口同步**：首期仅支持 FULL 全量同步。
5. **兼容策略**：项目未上线，不考虑旧接口兼容，直接按新契约实现。
6. **运行时查询**：SDK 除布尔鉴权外，需要提供通用资源查询和范围权限查询；查询结果返回权限事实，不返回业务服务私有数据。
7. **范围权限**：`query-scopes = DIRECT 直接范围权限 ∪ DEPENDENT 子权限范围权限`，并支持 `parentOperationCodes[]` 与 `scopeOperationCodes[]` 多操作查询。
8. **全量范围**：`role_resource_permission` 保留内部 `scope_all` 字段；对外协议使用 `scopeMode=ALL` 显式表示某资源类型下的全量范围权限；空 `items=[]` 不表示全量。
9. **类型模型**：对外 API 使用 `subjectTypeCode/resourceTypeCode/roleTypeCode`，内部存储继续使用 `type_value INT`，通过 `type_definition` 缓存解析；`type_value` 在同一 `tenant_id + type_key` 内全局唯一。
10. **业务域模型**：角色、资源等实体**不内嵌 `bizDomainId` 列**，域分类通过 `domain_config` 表的 `CLASSIFY` 配置实现（按 `resourceTypeCode` 关联，管理查询经 `DomainClassifyService.matchesTypeCode/getClassifiedTypeCodes` 按 ALL / GLOBAL_PLUS / DOMAIN_ONLY 三种模式过滤，批量上下文走 `preloadCoveredTypeCodes` 预载，见 §2.4）；**查询管线不做按域的对象过滤，仅按域分类过滤资源类型**（`queryResources`/`queryScopes` 经 `DomainClassifyService(GLOBAL_PLUS)` 分类过滤）；对外管理接口的 `domainCode` 参数仅做域存在性校验与同步命名空间，不参与角色/资源定位（见 §11.2/§10.4，abstract_role/resource_entity 均无域列）。（ P2-3 修正：原"传域查域+全局，不传只查全局"为旧命名空间模型残留； P2-2 同步"仅分类过滤"措辞）
11. **接口映射**：同一路径允许映射多个接口资源，Gateway 接口鉴权采用 OR 语义，任一映射资源权限通过即允许。
12. **委托授权**：`canGrant=true` 表示可把同一条权限授权给他人，但不得扩大资源、操作或范围；被授权对象候选范围由业务服务控制。
13. **资源依赖方向**：`resource_dependency.resource_entity_id` 是源资源/被授权资源，`depends_on_resource_entity_id` 是被源资源依赖、需要自动补全的目标资源。
14. **同步所有权**：服务接口同步通过 `ownerServiceCode + maintainSource` 限定 FULL diff 删除范围；资源依赖同步通过 `ownerServiceCode + maintainSource + syncKey`（`resource_dependency.sync_key`，非 `resource_entity.sync_key`——后者已删除）。
15. **变更摘要枚举**：`diff_snapshot.eventType` 与 `items[].changeType` 使用固定枚举（原 `recent-changes.impactLevel` 枚举随端点删除，2026-09-10 T-PERM-059），不使用开放字符串。
16. **子权限类型只读契约（v3.1，D5，2026-08-08 收口修订）**：授权页面子权限配置器通过 `POST /api/access/role-resource-permission/sub-perm-allowed-types`（§11.5）按父资源类型获取 SUB_PERM 允许的子资源类型并过滤选择器；请求携带目标角色业务键（domainCode/roleTypeCode/roleExternalId），门禁使用与 §11.2 list **相同的 ROLE:VIEW 权限资源与操作码，但失败响应不同**（list 失败返回空列表，本接口角色定位失败 20001、无 VIEW 抛 SecurityException 走统一访问拒绝）；`mode` 判定（ALLOW_ALL / ALLOW_LIST / ALLOW_NONE + reason 细分）与写校验 `assertSubPermissionAllowed` 完全同口径——覆盖顶层与嵌套 `"*"` 通配、多匹配项并集去重、大小写不敏感，**读写复用同一策略解析函数**；前端不硬编码允许集；本契约不改变任何写语义，不新增错误码（复用 20007/20001）。
17. **子权限属性系统不变量（2026-08-08 复审产品确认）**：子权限不承载条件与再授予是**系统不变量而非 UI 限制**——两种子权限 create 形态（`creates[].children[]` 与 `parentPermissionId` 挂父）的 `conditionCode` 必须为 null、`canGrant` 必须为 false（违反 -> **20043**）；`updates[]` 目标为子权限一律拒绝（20043，仅可删除）；历史异常记录只兼容读取与删除，不允许继续属性编辑；20042 不再描述 child create（子权限带条件 -> 20043 而非 20042）。
18. **引擎显式资源 API 与实例门禁业务编码（T-ACCESS-016 定稿，2026-08-23）**：引擎便捷 API 拆分为 `hasPermissionByCode(...)`/`getDeniedResourceCodes(...)`（对外，业务编码语义）与 `getDeniedEntityIds(...)`/`hasPermissionByEntityId(...)`（仅引擎内部或已完成解析的调用方）；泛型 `<ID>`、`Object resourceId`、`toLongId()` 运行时猜测全部删除，抛异常便捷方法从引擎移除（引擎纯查询不抛 `SecurityException`，异常由调用方显式抛出：管理面能力 AppService 经 `AdminPermissionValidator` 门面、权限面 AppService if-throw）。`resource_entity(USER).code = subjectId`、`resource_entity(ROLE).code = roleId` 业务编码定稿（§2.4）；资源类型收敛映射、`type_value` 终值与扩展操作 bit 终值以 access-service-architecture §13 资源类型注册表为准（实施 T-PERM-042/T-ACCESS-018）。

### 22.2 管理面家族已确认决策
| # | 决策 | 理由 |
|---|------|------|
| 1 | `/api/access/user-role/view` 读接口保留管理面聚合（跨面只读；T-ACCESS-042 由 list 改名，权限轨 `/list` 为 SDK 持有角色查询）；`/api/access/user-role/assign`/`revoke` 已删除（T-ADMIN-024，无映射 404），角色管理由权限面 `/api/access/abstract-role`、`/api/access/user-role/*` 直接提供 | 原代理方案避免业务键暴露（api-gap-analysis §4 A 方案，已归档）；T-ACCESS-006 起单服务内不再需要写代理，读聚合保留供前端组合查询 |
| 2 | `/api/access/user/create` 一次性返回 `initialPassword` (明文) | 仅本次返回, 由前端弹窗展示给操作者; 后续无法再获取 |
| 3 | `/api/access/user/enable` 启停一体 (`status=0/1`), 不拆 `/api/access/user/disable` | 前端 mock 已采用此形态; AppService 内部按 status 派发 ENABLE/DISABLE 门禁码 |
| 4 | `/api/access/user-org/assign` 关系级追加, 禁止 wipe 模式 | 防止跨树意外清除 (default-org-tree §3.2); 已存在关系幂等忽略 |
| 5 | `/api/access/user-org/set-primary` 首期只允许默认树主归属 | 不能全局清除其他组织树主标记 (api-gap-analysis §3，已归档) |
| 6 | 岗位 = 特殊组织 (`orgType=2`), 走 `/api/access/org/*` + `/api/access/user-org/*` | org-user-permission-contract.md v1.2 决策; `/api/access/user-role/*` 仅服务功能角色 |
| 7 | 候选用户来自默认树可见范围, 新增 `/api/access/user/member-candidates` 接口与 `/api/access/user/page` 解耦 | api-gap-analysis §2（已归档）; 默认树 = 用户目录/身份池, 不暴露全租户用户 |
| 8 | 写操作必须在同一事务内维护管理事实、本地权限投影和 permission_change_log | access-service-architecture §4；任一步失败整体回滚；缓存失效仅提交后发生 |
| 9 | 功能角色分配/回收由 `/api/access/user-role/assign|revoke` 提供，仅处理功能角色 | ORG/POSITION 由组织与成员关系投影产生；外部 sync 的 SYS_USER_ORG 来源一律拒绝（原 admin 代理端点已删除） |
| 10 | 管理面不存储权限面内部 ID | 跨面统一用业务键; 业务键格式严格按 本册 §19.7 |
| 11 | `IdReq` 入参字段名为 `id` 而非 `orgId/userId` | 复用公共 record; 前端在 Phase 2 调整 mock 字段 (例如 `/api/access/org/users` 入参 `{ id }`) |
| 12 | 列表响应统一用 `{ items: [...] }` 包装, 即便是非分页列表 | project-rules.md §1.3 强约束; 现有违反此规则的接口列入 Phase 2 修正项 (`/api/access/role/list` 已收编; `/api/access/user-org/list`、`/api/access/org/users` 已由 T-ADMIN-027 收编; **`/api/access/org/tree` 已由 T-ADMIN-021 消化, P1-3**) |
| 13 | `/api/access/user/update` 自我修改业务豁免 | 在 AppService 调用门禁前判断 `operatorId == id`; T-PERM-067 Q-002 收窄后豁免仅限档案字段（name/phone/email），`status` 变更不豁免（自禁硬拒/自启用须持码）; 不放在门禁层 |
| 14 | 错误码段 admin-service 子分配 | 用户域 10001-10299 / 组织域 10300-10499 / 关系域 10400-10499 / 10500 用户-角色代理段（历史分配，无活跃错误码；10111 已退役且码值不复用）+ 10501-10599 文件模块（附录 B，T-ADMIN-023 起） / 其他保留 10600-19999 |

## 23. 实施建议
1. 直接以 `/api/access/*` 重新实现 permission-center Controller，不保留旧路径兼容。
2. Gateway 默认路径改为 `/api/access/auth/check-interface`。
3. SDK Feign 改为依赖稳定契约 DTO，返回类型与服务端保持一致。
4. 所有 Request DTO 移除 `tenantId` 字段，服务端从 Header/SecurityContext 获取租户和操作者。
5. 首期 `service-config/sync` 仅实现 FULL 全量同步。
6. `auth/query-resources` 和 `auth/query-scopes` 必须复用 `auth/check` 的角色解析、条件评估、冲突处理、租户过滤和缓存失效逻辑。

## 24. 服务凭证与 M2M 服务认证（T-PERM-070）

> 设计权威：[service-authentication.md](service-authentication.md)（adopted 2026-09-19）；本章为契约登记面。定位：per-service M2M 身份认证是多通道共用的平台能力（资源同步通道、manifest 依赖声明通道〔T-PERM-071〕），替代「全局共享密钥 + 自报头」的现行信任模型（旧密钥路径过渡期维持，退役判据见 §3.5）。

### 24.1 认证协议

**凭证头**：`X-Credential-Id` + `X-Credential-Secret`（TLS 传输；同设计稿「不做签名制」定案——内网 TLS 下重放幂等 full-sync 无害）。

**仲裁状态表**（access-service order=1 `ServiceAuthArbiter`，替换原 InternalApiSecretInterceptor 单策略位）：

| 形态 | 判定 | 结果 |
|------|------|------|
| 完整凭证头（自报头/密钥头并存时**一律不采信**） | 验证成功 + 命中 M2M 白名单 | 绑定 SERVICE 上下文（tenantId/serviceCode **由凭证行派生**，忽略 X-Service-Code/X-Tenant-Id/X-User-Id） |
| 完整凭证头 | 验证失败（20065/20066/20067/20068）或白名单外 | **403，禁止降级回落旧密钥** |
| 半头（恰一个凭证头） | 形态即拒 | 403（20065，不落库不比对） |
| 无凭证头 + X-Internal-Secret 有效 | 旧密钥路径 | 行为与迁移前零变化（attribute → 自报头绑定） |
| 无凭证头 + 密钥无效/缺失 | — | 403（既有行为） |

**验证顺序**（泄露面最小化）：行定位 → BCrypt secret 比对（失败一律 20065）→ 状态/过期细分（20067/20066）→ 服务注册+启用（20068）——**三态细分仅对持有正确 secret 的请求者暴露**（credential_id 高熵不可枚举）。

**服务端凭证端点白名单**（单源 `common` 模块 `M2mCredentialEndpoints`，Gateway M2M 放行与服务端强制消费同一份）：

| method + 精确路径 | 说明 |
|---|---|
| `POST /api/access/resource-entity/sync` | 资源实体幂等同步（§19.1） |
| `POST /api/access/resource-entity/full-sync` | 资源实体全量校准（§19.2） |
| `POST /api/access/integration/permission-manifest/full-sync` | 依赖声明 FULL 同步（T-PERM-071 端点，随本卡预留登记） |

白名单外凭证请求一律 403（不依赖 Gateway，SDK 直连同款受限——防凭证能力半径扩大到管理/查询端点）。两处限定：①仲裁器豁免的会话入口族（§7.7 清单）上凭证头不参与仲裁（无 SERVICE 绑定，回落用户链 401/匿名/会话语义）；②经 Gateway 的半头/白名单外请求不置 skipAuth、回落 AuthTokenFilter **401**（服务端 403 仅发生在仲裁器已注册且完整凭证头的路径上）。阶段二逐端点扩展至全部 sync 族（主体/角色/成员/接口声明同步），退役判据=仍依赖旧密钥的端点清零。

### 24.2 管理端点（`/api/access/service-credential/*`）

门禁挂 service-config 管理面同族：写操作 `SERVICE:MANAGE`、list `SERVICE:VIEW`（bootstrap 固定图已有授权，零新增；四端点已入固定图 API 清单）。

| 端点 | 请求 | 响应 / 语义 |
|------|------|------------|
| `POST /create` | `{serviceCode, expiresAt?}`——serviceCode 须为已注册且启用（status=1）的服务（未注册/停用拒绝 20044「未注册或已停用」防死凭证）；expiresAt 须未来时间 | `{id, credentialId, secret, serviceCode, status, expiresAt}`——**明文 secret 仅本响应回显一次**（服务端只存 BCrypt 哈希，任何通道不可回查）；credentialId=`sc-`+22 字符 base64url（16 字节熵）、secret=`sk-`+43 字符 base64url（32 字节熵）；同服务多凭证并存（轮换窗口），**不设数量上限**（2026-09-20 拍板） |
| `POST /update` | `{id, status?, expiresAt?}`——三态语义 null=不修改，至少一项（空 patch 90001）；status 值域 0/1（停用即轮换收尾/吊销，立即生效并记 rotated_at）；expiresAt 仅改期，「清除过期时间」不提供（永不过期=签发时不设） | 更新后行视图（无 secret 面） |
| `POST /remove` | `{id}` | 软删立即失效 |
| `POST /list` | `{serviceCode?}`（可选过滤，含停用/过期行——轮换状态可见；凭证量低频管理面不分页） | `{items: [...]}`（无 secretHash） |

**错误码**（perm 段顺延，2026-09-20 拍板三码细分 + 服务停用码）：

| 码 | 枚举 | 说明 |
|----|------|------|
| 20065 | `SERVICE_CREDENTIAL_INVALID` | 凭证定位失败 / secret 错误 / 半头（认证层 403 body 携带） |
| 20066 | `SERVICE_CREDENTIAL_EXPIRED` | 已过期（处置=签发新凭证轮换） |
| 20067 | `SERVICE_CREDENTIAL_DISABLED` | 已停用（轮换收尾或管理员吊销） |
| 20068 | `SERVICE_CREDENTIAL_SERVICE_INACTIVE` | 凭证绑定的服务未注册或已停用（对齐 20055 门禁的服务注册段——服务停用即同步通道一起断） |

### 24.3 两类接入形态与 SDK 配置

- **经 Gateway**：`M2mCredentialFilter`（-75，白名单后用户认证前）完整凭证头+M2M 路径 → skipAuth 语义（仅透传、服务端仲裁器终验）；`InternalSecretFilter` 收窄为「无凭证头才兜底注入」；半头/缺头回落 AuthTokenFilter 401。**禁止把 /api/access/** 整体加入白名单**。
- **SDK 直连**：`perm-common` 的 `FeignCredentialInterceptor`（client 与 registration〔071〕两 starter 共用），配置键：
  - `perm.credential-id` + `perm.credential-secret`：成对必填（半配 fail-fast）；已显式声明凭证头的请求不覆盖；
  - `perm.allow-insecure`：**启动声明式 TLS 信任域护栏**（2026-09-20 拍板；外评处置收紧为二值白名单）——配置凭证必须显式声明且值域仅 `true`/`false`（大小写不敏感，非法值如拼写错拒启并提示合法值；true=单信任域明文 hop 可接受；false=跨边界期望 TLS；缺省拒启）。护栏为纯声明（服务发现形态下静态地址校验无落点，true/false 无运行时行为差异——值校验仅防声明拼写错静默通过），Gateway→access-service 内网 hop 不校验（同部署单元信任域）。
- **上线序**（服务端先行向后兼容）：①先发布 access-service 仲裁器（无凭证头存量调用方行为零变化）；②后发布 Gateway 改动与新版 SDK——「凭证头+注入密钥并存」由仲裁器凭证优先规则消解，无同批发布要求。

## 附录 A. 接口与前端 API 一一对照表（管理面家族）
| 后端接口 | 前端 `user-manage.ts` 函数 | 状态 |
|----------|-----------------------------|------|
| `POST /api/access/user/page` | `getUserPage` | 🔧 |
| `POST /api/access/user/member-candidates` | `getMemberCandidates`（T-FE-015 已接入） | 🔧 |
| `POST /api/access/user/create` | `createUser` | 🔧 |
| `POST /api/access/user/update` | `updateUser` | ✅ |
| `POST /api/access/user/delete` | `deleteUser` | 🔧 |
| `POST /api/access/user/enable` | `enableUsers` | 🔧 |
| `POST /api/access/user/reset-password` | `resetUserPassword` | 🔧 |
| `POST /api/access/org/tree` | `getOrgTree` | ✅ |
| `POST /api/access/org/page` | `getOrgPage` | ✅ |
| `POST /api/access/org/users` | `getOrgUsers` | ✅ |
| `POST /api/access/org/create` | `createOrg` | 🔧 |
| `POST /api/access/org/update` | `updateOrg` | 🔧 |
| `POST /api/access/org/delete` | `deleteOrg` | 🔧 |
| `POST /api/access/user-org/list` | `getUserOrgs` | ✅ |
| `POST /api/access/user-org/assign` | `assignUserOrgs` | 🔧 |
| `POST /api/access/user-org/remove` | `removeUserOrg` | 🔧 |
| `POST /api/access/user-org/set-primary` | `setPrimaryOrg` | 🔧 |
| `POST /api/access/user-role/view` | `getUserRoles` | 🔧 |
| `POST /api/access/role/list` | `getRoleList` | ✅ |

合计: 19 项接口 (13 🔧 + 6 ✅)。原 `/api/access/user-role/assign`、`/api/access/user-role/revoke` 两行已随 T-ADMIN-024 端点删除移除（前端 `assignRole`/`revokeRole` 已随 T-FE-015 联调 2026-08-31 切换至 `/api/access/user-role/assign|revoke` items[] 契约）；api-gap-analysis.md "已核对接口汇总" 2026-06-21 归档至 `docs/archive/2026-06-21/`（其时点 16 个 🔧 已由 admin-service 实现，当前实数 13）. 另：后端 `OrgTreeConfigController` 存在 `/api/access/org-tree-config/{page,create,update,detail,delete,set-default}` 六端点（前端仅消费 `page`，已随 T-FE-015 注册 Gateway），本契约未展开登记——待树配置管理 UI 立项时补 §8.x 契约段（登记项，T-FE-015 联调发现）.

## 附录 B. 已对齐接口汇总（管理面家族，✅ 6 项）
| # | 接口 | 来源 record | 备注 |
|---|------|-------------|------|
| 1 | `POST /api/access/org/tree` | `OrgQuery` | **已对齐（T-ADMIN-021 全字段落地：operationCode + treeConfigId + includePositions + 响应 `{ items }` 包装）** |
| 2 | `POST /api/access/org/page` | `OrgPageReq` | 含 `orgId` 子树筛选; 已对齐 |
| 3 | `POST /api/access/org/users` | `IdReq` | 前端 mock 入参字段名为 `orgId`, 待 Phase 2 调整为 `id`; 响应已收编 `{ items }` 包装 (T-ADMIN-027) |
| 4 | `POST /api/access/user/update` | `UserUpdateReq` | 已对齐 |
| 5 | `POST /api/access/user-org/list` | `IdReq` | 已对齐; `{ items }` 包装已收编 (T-ADMIN-027) |
| 6 | `POST /api/access/role/list` | `RoleListQueryReq` | 已对齐 |

## 附录 C. 合并源对照（T-ACCESS-040）

| 总册章节 | 原《Permission Center 外部 API 契约》 | 原《Admin Service 对前端 API 契约》 |
| --- | --- | --- |
| §1 设计目标与接口分层 | §1 / §2 | 头部定位注记 |
| §2 通用协议 | §3（3.1~3.5） | §1（→2.6） |
| §3 动词规范 | §4 + §6.10.1 | — |
| §4 门禁规范 / §5 本地投影 | — | §2 / §3 |
| §6 auth | — | §8 |
| §7 user | §5.2（abstract-user 行） | §4.1 |
| §8 org | — | §4.2 + §4.3 |
| §9 menu | — | §4.6 |
| §10 role | §5.2（abstract-role）+ §5.5（user-role 行）+ §6.10.2 + §6.10.3 | §4.4 + §4.5 |
| §11 grant | §5.5 + §6.4 + §6.5 + §6.5.1 + §6.5.2 | — |
| §12 resource | §5.3 + §5.4 + §5.6（dependency）+ §6.9 + §6.10.4 | — |
| §13 type | §5.1（type-definition）+ §5.3（operation 行） | — |
| §14 domain | §5.1（biz-domain）+ §5.6（domain-config） | — |
| §15 rule | §5.6（condition + conflict） | — |
| §16 audit | §5.8 + §6.10.6 | — |
| §17 platform | §5.8（system-config） | §4.7 |
| §18 engine | §5.7 + §6.1 + §6.2 + §6.2.1 + §6.6 + §6.7 | — |
| §19 sync | §6.2.2~§6.2.2.6 + §6.3 + §6.3.1 | — |
| §20~§23 + 附录 A/B | §7~§10 / §9 | 附录 B / §5~§7 / 附录 A |

> 正文中的「原册 §X.Y」字样指旧两册的历史章节锚点（合并源文件保留原位可解析，frontmatter 已标 superseded）。
