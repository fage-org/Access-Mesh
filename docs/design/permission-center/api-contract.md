---
doc_type: design
title: Permission Center 外部 API 契约
status: adopted
domain: permission-center
last_reviewed: 2026-09-10   # 2026-09-10 T-PERM-058 收口：§6.1 check/batch-check 增主资源上下文四可选字段（parentResourceTypeCode/parentResourceCode/parentCodeType/parentOperationCodes，与 §6.7 同名对齐；不传=fail-closed 排除子权限行，拒绝原因 DEPENDENT_NOT_IN_PARENT_CONTEXT）+ 引擎便捷入口/类型级门禁同口径注记 + §6.6 注记子权限行不进清单面（Gateway 快照同口径）；此前 2026-09-10 T-API-003 收口：§5.7 SDK 入口清单注记改分族口径 + §6.1 check/§6.2 check-interface 响应示例与注记改写为结果记录全量回传（matchedRoleIds/matchedPermissionIds/matchedResources[].resourceId 恢复回线格式，推翻 T-API-002 check 族裁剪；Query\* 响应族六字段裁剪维持 §6.6/§6.7 终态）；此前 2026-09-09 T-PERM-057 §6.1 补 inheritMode 接通语义注记（PARENT/BOTH=判定面闭包开）；2026-09-09 T-PERM-056 收口：§5.1 type-definition remove 主体类型删除保护成文（user_type/role_type 下存在有效 abstract_user/abstract_role 行时整批拒绝 20056——主体数据不级联、对齐 resource_type 行数守卫先例，用户定案；role_type 面与角色写入口共持 ABSTRACT_ROLE 树写锁闭合并发交错、user_type 面 best-effort + typeValue 软删不复用兜底；20056 message 措辞扩为「有效引用行——资源行/用户行/角色行」；存量孤儿检测语句见 rebuild-runbook）；此前 2026-09-09 T-PERM-050 收口：§5.1 type-definition remove 级联语义成文（被删 resource_type 的操作定义行——含预置 CRUD 四操作位——与该类型下有效授权行（正常流仅剩 scope_all 类型级）同事务级联软删 + markRoles/OPERATION_PERMISSIONS_BY_TYPE per-type 失效；级联面限定 type_key=resource_type；user_type/role_type 面拆分 T-PERM-056、存量订正语句见 rebuild-runbook）；此前 2026-09-09 T-PERM-046 业务域三项加固收口：§5.1 biz-domain create 加 global 可选入口（默认 false；20057 全局域已存在，预查+uk_biz_domain_global DIVE 兜底；创建后不可变）+ 全局域范围=CLASSIFY 声明或动态补集（用户定案）；§5.6 domain-config 并发语义收口（uk_domain_config 部分唯一索引 + 20058 并发冲突提示重试 + remove/save 域行锁双向闭合孤儿配置窗口）+ 全局域 CLASSIFY 声明生效条目；此前 2026-09-07 T-PERM-051 收口：§5.1 TYPE_DEFINITION 实例投影落地（复合业务键 {typeKey}:{typeCode}、四处门禁消费方迁移、事实链路族六类型、删除级联与 bootstrap 自愈口径）；同日早前 T-PERM-019 D2/D3：§5.1 typeCode 生成码措辞订正（<TYPEKEY大写>_<typeValue>，例 RESOURCE_TYPE_12）；此前 2026-09-06 T-ADMIN-025 增补：事实链路类型族增 ADMIN_FILE（bootstrap 预置+上传惰性登记两条产出链，§5.1/§6.2.2 五类型口径）；2026-09-06 T-API-002 收口：SDK 四件套补齐与内部 id 字段族全线裁剪（§5.7 SDK 入口清单注记 + Query* DTO 迁 perm-common + query-resources/query-scopes 入 PermissionFeignClient；§6.1/§6.2 check 族响应裁 matchedRoleIds/matchedPermissionIds/resourceId（用户决策扩大面）+ §6.6/§6.7 六字段裁剪与去重/DEPENDENT 规则措辞对齐 + 排查页暂停注记 T-FE-043）；2026-09-05 T-PERM-053 收口：§6.3 ApiItem.operationCode 字段退役（删除；严格 mapper 旧载荷 400；前后端同批锁步）；同日 T-PERM-052 codex 二轮复评收口：§5.1 保存校验与运行时入口同规则（来源须注册+未软删+status=1、保留内部来源拒绝、已知键显式 null 拒绝/未知键开放）+ 系统预置类型所有权声明钉死（20056）；同日 T-PERM-052 类型级所有权两批落地与评审批次收口：§5.1 类型所有权声明（managedMode/syncSourceService 两态+内部来源 is_system 豁免+API 类型禁 SYNC+remove 行数守卫）、§5.3 SYNC 类型管理面只读（20055，remove 级联全集守卫）、§6.2.2/§6.2.2.1 同步入口类型门禁（RESOURCE_TYPE_OWNERSHIP_DENIED，含来源服务注册+启用校验）、§6.3/§6.3.1 syncTypes 资源维度退役（三维+退役字段保存拒绝）、§10-14；同日内部来源统一：事实链路四类型种子声明 SYNC+access-service，原保留清单/行级投影防线收编删除，resource_entity.sync_key 列删除；2026-09-04 T-PERM-044 四树环加固收口：§5.2 move 并发语义成文（树级分布式锁串行化，Redisson、事务提交/回滚后释放）；同日双轨评审补：§6.2.2 资源同步 parent 判环条款与版本语义对齐（拒绝/依赖缺失不推进同步版本）；2026-09-03 T-FE-018 评审复核：§5.1 type-definition/list 门禁放宽为类型级或任一实例级 VIEW（与登录权限串投影口径对齐，用户决策；TYPE_DEFINITION 无实例投影经查库核实，实例投影登记 T-PERM-051）；同日外部评审处置：§5.1 list 门禁全拒比较跨 type_key 重码 fail-open 修复（去重码集比较+回归锁）、无自动产出/人工可构造口径精化、资源实体管理入口保留清单精化（create/batch-create 查清单，update 走本地投影所有权保护）；2026-09-02 T-FE-018 需求对齐会：§5.3 登记 resource-entity/tree 未知类型 fail-open 与兄弟接口 fail-closed 口径不一致（待办收口，暂不实施）；同日 T-FE-018 联调评审：§6.5.2 登记 SUB_PERM 顶层通配经真实 jsonb 链路不可达已知缺陷（ALLOW_ALL 以嵌套通配替代，修复待办）、§5.8 轮次标记清理；此前 2026-09-01 T-FE-016 角色管理联调收口：§5.2 角色管理契约要点补 update extraClear 显式清空标志（对齐 §5.3 resource-entity 同款口径）；2026-08-31 T-PERM-040 单资源类型后端支持收口：§6.4 resourceTypeCode 类型过滤标已落地（主权限专用 Mapper 查询 + 子权限按 depend_on 批量挂父，双重约束 SQL 层满足）+ 未知类型空列表 fail-closed 口径成文、§6.5.1 operationCode 适用性校验标已落地（20008/20005 区分成文）、§5.3 类型查询参数补未知类型空列表与空白串视同缺省（不过滤）口径 + domainCode 死参数删除登记（从未实现过滤、零调用方）；2026-08-30 T-PERM-041 主权限条件不变量收口：§6.5.1 共用不变量块与 20042 条件启用状态标已落地（20041 预检最终态判定与主权限 DDL CHECK 先行存在，本任务补 20042 启用校验）+ 20042"未变更"口径成文（update 同 id 重写=存量保留，按解析后条件 id 比对豁免；清除仅精确空串、空白串落 20006、缺省不触发）+ updates conditionCode 三态定义补明空白串口径；同日全局操作概念整体退役（T-PERM-049，外部复审触发的设计定案）：§5.3 includeGlobalFallback 合并参数与「专属优先、全局回退」合并语义退役、resourceTypeCode 可空=全局操作键轨退役（detail/update/remove 必填）、§6.5.1 operationCode 适用性校验收窄为类型专属定义、§6.6 投影轨全局位分歧登记随概念失效；同日 T-PERM-034 外部复评收口：§6.8 apply-grant-plan 写侧快照补录级联删除子权限（depend_on 命中 removes 同记 REMOVE、与显式删除去重）；同日 T-PERM-034 收口：§5.5 sub-perm-allowed-types 落地标注、§6.5.2 落地状态注记、§6.8 增补 apply-grant-plan 写侧聚合形状落地（20043 预检/diff_snapshot §6.8/GoldenFixturePgIT 引擎级比对/引擎全局操作位掩码修复；此前同日 §5.6 resource-dependency 契约要点（T-PERM-031 收口：读三端点 list/graph/check 补类型级 DEPENDENCY:VIEW + 写三档类型级收窄（update 原「编码轨传内部 id」/remove 原实例级 entityId 轨均为 ID 空间错位废弃）、update PUT 全量覆盖 + 资源对业务键补全（Q3=B 前端契约收口）、等价重复业务预查 20054 新增、操作码 fail-closed 20005（原静默丢弃）、自依赖 20044、Resp 补静态字段 + 操作位字符串线格式、maintainSource 四值白名单、bootstrap 补 DEPENDENCY 五档）+ §6.9 同步；同日 §5.6 conflict-rule 契约要点（T-PERM-030 收口：读三端点 list/detail/detect 补类型级 CONFLICT_RULE:VIEW + 写三档类型级收窄（原「编码轨传内部 id」实例级声称系 ID 空间错位废弃，同 CONDITION 口径/实例投影登记 T-PERM-048）、detail 查不到 20020、list 全量不分页、定位键维持内部 id 评估定案（无业务键）、Resp 补 updatedAt、description ≤512、bootstrap 补 CONFLICT_RULE 四档 + CONDITION 写三档（029 遗漏死锁缺口）；同日更早 §5.6 permission-condition 契约要点（T-PERM-029 收口：写门禁口径收窄为类型级（原「实例级」声称系 ID 空间错位废弃，实例投影登记 T-PERM-048）、detail/update/remove 切业务键 code、detail 查不到 20006、list 全量不分页设计定案（🔧3 分页登记反转）、ConditionResp 补 updatedAt、读取无门禁+写三档独立门禁口径、gatewayEvaluable 联合校验/remove 幂等语义成文）；2026-08-29 §5.3 资源与操作业务键定稿（T-PERM-028 收口：detail/update/move/remove 切业务键混合形态、extraClear 显式清空、move 20053 跨类型/防环、list/detail VIEW 门禁补齐、bigint 十进制字符串线格式落地、resource_type 创建联动预置、§5.4 资源选择器登记收口）+ §5.4 service-config/resource-api-mapping 契约要点（T-PERM-027 收口：list 维持全量设计定案、remove 级联清理、syncMode FULL-only 校验、ApiMappingResp 资源业务字段、mapping list 门禁+裁剪、updatedAt、bootstrap SERVICE 三授权）+ §6.3/§6.10.4 同步；§5.1 biz-domain 契约要点 + §5.6 domain-config 契约要点（T-PERM-026 收口：detail/update 切业务键 code、list 服务端过滤分页、Resp global、删除保护 20051/创建查重 20052、extra JSON 校验、JSONB 映射确认）；§6.8 explain 契约扩展 + §6.7/§6.8 门禁设计定案（T-PERM-033：explain/recent-changes 门禁=被查目标实例 USER:VIEW/ROLE:VIEW、无独立排查码；explain 增 context/评估上下文来源/条件评估明细（脱敏）/互斥丢弃明细；recentChanges 按权限键过滤；§6.7 登记 query-scopes 管理端排查复用无门禁）；此前：§5.8 permission-change-log 契约要点 + §6.8 增补 ROLE_BATCH_DELETE（T-PERM-032 收口）；2026-08-28 §5.2 角色管理契约要点 + §6.10.3 tree 全量返回与 enabledOnly（T-PERM-022 收口：detail 业务键/move 类型一致+环路 20050/list+detail VIEW 门禁/sync 最终图判环+版本不推进/remove 根有权整棵子树可删）、§5.1 type-definition 契约要点（T-PERM-023 收口）、§5.8 system-config/operation-log 契约要点（T-PERM-024/025 收口：upsert/isSystem 修复/list 分页/JSONB 语义/OPERATION_LOG:VIEW 审计分离/action-options 字典）；更早：2026-08-27 §5.5 五旧端点删除、§6.6 treeMode 移除、§6.9 autoGrant 20048
---

# Permission Center 外部 API 契约

> 本文档定义权限中心对外稳定接口契约。目标是让权限中心既能服务 AccessMesh 内部 Gateway/SDK，又能作为通用权限管理服务暴露给外部业务系统。**归并后定位（T-ACCESS-012）**：原独立服务 `permission-center` 已归并为 access-service 的 permission 域，本契约经 Gateway `/api/perm/**` 由 access-service 承载；文中「permission-center」即指该域，「admin-service / admin」指同服务管理域。

> **全局注记（2026-06-20 审计 S-001 + T-PERM-018 收尾）**：`permissionVersion` 字段已随 T-PERM-018（缓存下沉）从所有响应体移除——令牌「唯一真正作用是 INTERFACE_SNAPSHOT 缓存 key」已核实，permission-center 侧该 L2 缓存已删，令牌随之失效，连带 304/notModified 死代码一并清除。本文档历史段落保留的字段描述仅作演进记录，**以代码为准**（`InterfaceSnapshotResp`/`InterfaceSnapshotReq`/`QueryResourcesResp`/`QueryScopesResp`/`PermissionTreeResp` 均不再含 `permissionVersion`）。

> **scopeMode 协议定义（2026-06-27 T-PERM-011，T-PERM-013 落地完成）**：对外协议字段统一使用 `scopeMode`，不再暴露旧 boolean 范围字段。`auth/query-scopes.scopeGroups[]` 使用四态 `DENIED / INSTANCE / ALL / EMPTY`：无权限、具体实例、全量范围、有权限但过滤后为空；授权请求、授权配置响应、接口快照项、`query-resources` 和 `effective-permissions` 等权限事实列表项只使用 `INSTANCE / ALL`。授权请求侧 `INSTANCE` 表示具体实例范围且必须传 `resourceCode/codeType`，`ALL` 表示资源类型 + 操作下全量范围且不传 `resourceCode/codeType`。数据库内部仍保留 `role_resource_permission.scope_all` 作为存储字段，由服务端完成协议层映射（`ScopeModeSupport`）。所有对外协议 DTO（含 `InterfaceSnapshotResp.ApiPermissionEntry` 和 `QueryResourcesResp.ResourceEntry`）已完成迁移，旧 `scopeAll` boolean 字段不再出现在任何外部响应中。

## 1. 设计目标

- **统一命名空间**：所有稳定对外接口统一使用 `/api/perm/{resource}/{action}`。
- **保持通用性**：运行时和外部接入接口使用稳定业务键，避免外部系统必须感知权限中心内部主键。
- **保持强约束**：所有接口 `POST + application/json`，禁止 URL Path 参数和 Query 参数。
- **保留扩展空间**：响应 `data` 必须是对象，列表也用 `{ "items": [...] }` 包装。
- **安全多租户**：租户、操作者、调用来源优先来自 Header/Token/SecurityContext，不信任请求体里的同名字段。
- **SDK 友好**：DTO 进入独立 `permission-center-api` 或 `perm-common` 契约模块，不复用服务端内部 `Req/Resp`。
- **多形态接入**：对外交付目标分为 Spring Boot starter、普通 Java client SDK 和其他语言 HTTP 接入文档三层，稳定 API 契约必须同时服务这三类调用方。

## 2. 接口分层

| 分层                    | 路径前缀                     | 调用方                              | 特点                                 |
| ----------------------- | ---------------------------- | ----------------------------------- | ------------------------------------ |
| 管理配置 API            | `/api/perm/*`                | 管理端、access-service 管理域、接入系统后台 | 资源、角色、授权、条件、域配置、日志 |
| 运行时鉴权/权限查询 API | `/api/perm/auth/*`           | Gateway、业务服务 SDK               | 高 QPS、可缓存、强稳定               |
| 服务接入 API            | `/api/perm/service-config/*` | 接入服务、SDK Starter、管理端       | 服务注册、接口同步、接口资源树       |

> 不再定义 `/internal/perm/*` 主契约；本项目未上线，后续实现直接以 `/api/perm/*` 为准。

## 3. 通用协议

### 3.1 Header

| Header           | 必填          | 说明                                                              |
| ---------------- | ------------- | ----------------------------------------------------------------- |
| `Authorization`  | 管理 API 必填 | `Bearer <token>`                                                  |
| `X-Tenant-Id`    | 必填          | 当前租户 ID，由 Gateway 或可信服务注入；请求体不再保留 `tenantId` |
| `X-Request-Id`   | 可选          | 未传时由 Gateway 生成                                             |
| `X-Service-Code` | 内部/SDK 必填 | 调用方服务编码，用于内部来源校验                                  |
| `X-Api-Version`  | 可选          | 契约版本，默认 `2026-04-26`                                       |

可信边界：

- 外部客户端传入的 `X-Tenant-Id/X-User-Id/X-Service-Code` 必须由 Gateway 清洗，不允许原样透传。
- Gateway 从 Token claim 解析租户和主体后重新注入标准 Header。
- 服务间调用由调用方凭证绑定可信服务身份（`SyncAuthVerifier` 从上下文比对）；access-service 需校验服务身份与 Header 一致性。

### 3.2 统一响应

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

### 3.3 分页结构

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

### 3.4 标准标识字段

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
| 操作      | `operationCode`                                                 | 在 `resourceTypeCode` 范围内解析（操作定义按类型隔离，全局操作已退役）      |
| 条件      | `conditionCode`                                                 | 可空                                                                     |
| 明细记录  | `id` 或 `ids`                                                   | 仅用于更新/删除权限关系、日志详情等权限中心已返回的记录                  |

原则：

- 运行时鉴权接口不要求调用方传内部 `id`。
- 管理端列表、创建、详情响应可以返回内部 `id`，用于后续 `update/remove`。
- 同一接口不同时接受 `id/code/externalId` 多套定位方式，避免歧义。
- 所有请求体禁止出现 `tenantId`；服务端统一从 `X-Tenant-Id` 和安全上下文读取租户。
- 对外 API 使用稳定字符串 `typeCode`；数据库实体继续保存 `type_value INT`，由服务端通过缓存解析，避免外部系统依赖内部数字枚举。
- **实例授权业务编码语义（T-ACCESS-016 定稿）**：`resource_entity(USER).code = subjectId`（主体 ID 字符串化）、`resource_entity(ROLE).code = roleId`（`abstract_role.id` 字符串化）；业务对象门禁与跨服务 SDK 统一使用业务 `resourceCode`，不得使用 `resource_entity.id`——权限域内部及直接管理资源实体的后台接口（资源树、API 映射、资源依赖、权限树等）允许继续使用，现有 `ApiMappingResp`/`ResourceDependencyResp`/`ResourcePermissionTreeResp` 等契约不因此重构。引擎内部 Java API 契约见 implementation §3.1（`hasPermissionByCode`/`getDeniedResourceCodes` 对外，`getDeniedEntityIds`/`hasPermissionByEntityId` 仅引擎内部或已完成解析的调用方）。
- `type_value` 在同一 `tenant_id + type_key` 内全局唯一，不随 `domainCode/biz_domain_id` 重复；`type_code` 仍可按业务域和全局分别定义。
- `domainCode` 用于**管理查询的域过滤与同步命名空间**：管理查询经 `DomainClassifyService.matchesTypeCode/getClassifiedTypeCodes` 按 **ALL / GLOBAL_PLUS / DOMAIN_ONLY** 三种模式过滤（`domain_config` 表 `CLASSIFY` 配置按 `resourceTypeCode` 关联）；**查询管线不做按域的对象过滤，仅按域分类过滤资源类型**（`queryResources`/`queryScopes` 经 `DomainClassifyService(GLOBAL_PLUS)` 分类过滤，非按 domainCode 定位对象）；角色/资源实体不内嵌域列，`domainCode` 不参与角色/资源定位（仅域存在性校验，见 §6.4/§6.10）。**不存在"传域查域+全局，不传只查全局"的旧命名空间语义**——如有接口确需旧语义，须逐项列出并标注迁移（ P2-2 修正）。
- Gateway 必须清洗外部伪造的 `X-Tenant-Id/X-User-Id/X-Service-Code`，再基于 Token 或可信服务身份重新注入；permission-center 不信任客户端原始 Header。

## 4. 动词规范

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

## 5. API 清单

### 5.1 类型与域

| 接口                                    | 说明                 |
| --------------------------------------- | -------------------- |
| `POST /api/perm/type-definition/list`   | 查询类型定义         |
| `POST /api/perm/type-definition/detail` | 查询类型详情         |
| `POST /api/perm/type-definition/create` | 创建类型             |
| `POST /api/perm/type-definition/update` | 更新类型             |
| `POST /api/perm/type-definition/remove` | 删除类型，支持批量   |
| `POST /api/perm/biz-domain/list`        | 查询业务域           |
| `POST /api/perm/biz-domain/detail`      | 查询业务域详情       |
| `POST /api/perm/biz-domain/create`      | 创建业务域           |
| `POST /api/perm/biz-domain/update`      | 更新业务域           |
| `POST /api/perm/biz-domain/remove`      | 删除业务域，支持批量 |

**type-definition 契约要点（T-PERM-023 收口，2026-08-28）**：

- `create`：`{typeKey, typeCode?, name, description?, sortOrder?, extra?}`——`typeValue` 由服务端在 tenant+typeKey 内自动分配（全量行含软删行 max+1，软删不复用）；`typeCode` 留空按 `<TYPEKEY大写>_<typeValue>` 生成（如 `RESOURCE_TYPE_12`），显式提供时查重（重复 20049；DB 唯一索引对并发窗口与生成码被显式码抢占的场景兜底，同映射 20049）；`isSystem` 不可由 API 创建（固定 false，系统预置仅走租户初始化种子）。
- `list`：`{typeKey?, keyword?, pageNum?, pageSize?}` → 分页结构（§3.3）；`keyword` 匹配 name/typeCode（LIKE，大小写敏感），排序 `sort_order, id`；分页参数均不传 = 字典全量（上限 200，先例 `/role/list`），供下拉数据源消费。

**resource_type 类型所有权声明（T-PERM-052，2026-09-05 定案）**：`create`/`update` 的 `extra` 可携带类型级所有权声明，约定键 `managedMode`（`MANAGED`=缺省，管理面维护 / `SYNC`=外部同步维护）与 `syncSourceService`（`mode=SYNC` 时必填，须为已注册、未软删且 `status=1` 启用的服务——与运行时同步入口同规则（codex 二轮复评 P2 对齐，仅查注册非空会保存出无人可写的锁死类型）；保留内部来源（如 `admin-service`）拒绝声明（运行时拒绝其冒充）；`type_key` 非 resource_type 携带此二键保存拒绝 20044）。规则：SYNC 类型归声明来源服务独占同步（§6.2.2 门禁），管理面 create/update/move/remove 只读（§5.3，20055）；声明有效值变更（含删键隐式切回 MANAGED——extra 为整串替换语义）：**系统预置类型（is_system=true）所有权声明钉死不可变更**（**20056** `TYPE_OWNERSHIP_CHANGE_CONFLICT`，2026-09-05 codex 二轮复评定案——事实链路类型空行翻转后事实写入方照旧写即双 writer，无需并发）；自定义类型在类型下存在有效资源行时拒绝（无有效行才可改，2026-09-05 用户定案）；读取侧 extra 损坏按 MANAGED 处理（fail-closed）。外部服务接入流程：`service-config/save` 注册服务 → `type-definition/create` 建自有类型并声明 `managedMode=SYNC` + 来源 → 调 `resource-entity/sync|full-sync`。内部来源：`syncSourceService=access-service` 仅 is_system 预置类型可声明（豁免服务注册校验）——USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION 六类事实链路类型由种子声明（前四类行经用户/组织/菜单/角色管理自动维护；ADMIN_FILE 文件夹实例（code=sys_file.bucket_name）由 bootstrap 预置四文件夹+上传惰性登记产出，T-ADMIN-025；TYPE_DEFINITION 类型定义实例（code=`{typeKey}:{typeCode}` 复合业务键）由 type-definition 写路径同事务投影 + bootstrap 自愈补种产出，T-PERM-051；外部同步与管理面资源 CRUD 均拒绝，20055 message 指向事实链路管理入口）；来源编码禁止首尾空白（校验按 trim、运行时按原值精确匹配，空白会造出无人可同步的锁死类型，保存拒绝 20044）；已知键显式 null 保存拒绝 20044（与「清除声明=删除键」语义歧义，fail-closed）；未知键开放不视为声明（extra 是通用扩展位，拼错键=无声明按缺省 MANAGED——codex 二轮复评定案）；`API` 类型禁止声明 SYNC（service-config 接口声明通道是其事实 writer，评审批次定案）。`remove`：类型下存在有效资源行时不可删除（20056，与声明变更守卫同款——软删类型会让其行成为外部源与管理面都无法触达的永久孤儿；评审批次补齐）。**T-PERM-050（2026-09-09 定案级联）**：`remove` 同事务级联软删被删 resource_type 的全部有效操作定义行（含创建联动预置的 CRUD 四操作位，对称于创建语义——根治「删类型留 4 条永久孤儿操作行、列表过滤后不可见且无清理入口」）与该类型下有效授权行（正常流仅剩 scope_all 类型级行：资源行已被行数守卫拒绝、实例级授权随资源删除级联；防御性含引用已软删资源行的残留实例行），markRoles 提交后失效受影响角色快照、OPERATION_PERMISSIONS_BY_TYPE 按被删类型集合 per-type 失效（T-PERM-047 终态复用）；级联面限定 `type_key=resource_type`（type_value 仅 tenant+type_key 内唯一，user_type/role_type 同值删除不得误伤 resource_type 空间）。存量孤儿订正语句登记 rebuild-runbook。**T-PERM-056（2026-09-09 用户定案删除保护）**：`remove` 对主体类型补引用面守卫——user_type/role_type 下存在有效 `abstract_user`/`abstract_role` 行时整批拒绝（20056，message 列冲突 typeCode；管理员须先删/迁走该类型用户/角色再删类型——用户/角色是业务主体数据不级联，对齐 resource_entity 面=守卫先例而非操作位/投影=级联先例）。并发语义：role_type 面与角色写入口共持 ABSTRACT_ROLE 树写锁并锁内重读（管理面 updateRole/moveRole 与角色 sync/full-sync 均持同锁；自定义 role_type 角色行的唯一**创建**入口是角色 sync/full-sync——管理面 createRole 经 RoleType 枚举校验只接受种子类型），闭合「守卫查零行→并发建该类型角色→删除落库」交错；user_type 面用户写入口无锁可复用，为 best-effort 守卫（对齐 T-PERM-050 级联并发先例），极窄交错残留由 typeValue 软删不复用兜底（孤儿 user_type 值永不撞新类型，后果同存量孤儿=列表反解缺项，无越权通道），存量孤儿检测语句登记 rebuild-runbook。门禁（2026-09-03 放宽定案，用户决策）：类型级 TYPE_DEFINITION:VIEW **或任一实例级 VIEW** 均可查询——与登录权限串投影口径对齐（权限串全集含实例级授权；此前仅认类型级，出现「前端 hasPerms 探查通过、后端拒绝」的口径不一致，T-FE-018 评审发现）；`detail` 维持按目标实例校验 VIEW、写操作维持类型级/实例级 MANAGE 不变。**T-PERM-051（2026-09-07 收口）**：实例业务键统一为复合键 `{typeKey}:{typeCode}`（typeCode 仅 tenant+type_key 内唯一，种子 user_type 与 resource_type 均有 USER/SERVICE 同名行，裸 code 跨族撞 uk_resource_entity，2026-09-05 定案）——四处门禁消费方全部迁移：`list`/`count` 实例判定、`detail`/`update` 编码轨（原 type_definition.id 字符串系 ID 空间错位）、`remove` 批量编码轨（原 id 直传实体轨，同款错位）；投影行由写路径同事务维护（create 联动 / name 变更同步 / 软删级联投影行与投影行下授权行，deleteResources 同款级联语义）+ bootstrap 自愈补种存量行（幂等，2026-09-07 用户定案启动自愈对齐 T-ADMIN-025 先例；runbook 登记订正语句兜底）；授权页 TYPE_DEFINITION 实例按资源选择器既有模式呈现（类型已声明 SYNC+access-service，资源管理面只读 20055）。全拒判定按去重复合键集比较 fail-closed 拒绝（跨 type_key 重码曾致 fail-open，2026-09-03 修复；复合键行级唯一后去重语义不变，2026-09-07 随 T-PERM-051 迁移）。

**biz-domain 契约要点（T-PERM-026 收口，2026-08-29）**：

- `detail`/`update` 切业务键 `code` 定位（`uk_biz_domain(tenant_id, code)`，软删行不占用；detail/update 的定位键由内部主键退役——`remove` 仍收 `{ids}` 批量软删、`BizDomainResp` 保留 `id`，type-definition 先例）：`detail` 请求 `{domainCode}`，未知编码返回 `data=null` 不抛错（role detail 先例）；`update` 请求 `{domainCode, name?, description?}`——`code` 不可改（改 code 等于新建新域），`name/description` 为 null 表示不更新、`description` 传空串表示显式清空；未命中 **20017** `DOMAIN_NOT_FOUND`。
- `list`：`{keyword?, pageNum?, pageSize?}` → 分页结构（§3.3）；`keyword` 匹配 code/name/description（LIKE，大小写敏感），排序 `code, id`；分页参数均不传 = 字典全量（上限 200，先例 `/role/list`、`/system-config/list`）。门禁 DOMAIN:VIEW 类型级（与 detail 同级，先于查询避免存在性泄露）。
- `create`：`{code, name, description?, global?}`——编码重复拒绝 **20052** `DOMAIN_CODE_DUPLICATE`（预查 + `uk_biz_domain` 唯一索引 DIVE 兜底同映射，TypeDefinition 先例）；`global` 可选默认 false（null 同 false），**T-PERM-046（2026-09-09）**：true=创建全局域，每租户仅一个——已存在时预查拒绝 **20057** `DOMAIN_GLOBAL_EXISTS`，并发窗口由 `uk_biz_domain_global` 唯一索引兜底同映射（约束名带引号精确匹配，防 `uk_biz_domain` 前缀误吞）；`global` 创建后不可变（update 请求体不含此字段，换轨=新建域）；全局域不可删保护既有（20051）；全局域范围=CLASSIFY 声明或动态补集（有声明按声明、无声明退「未被其他域认领」补集，用户定案 2026-09-09，DomainClassifyService 消费）。前端 create 表单加「全局域」开关（已存在全局域时禁用+提示，预判失败由 20057 兜底）。
- `remove` 删除保护（**20051** `DOMAIN_DELETE_CONFLICT`，message 区分原因）：全局域（`global=true`，每租户唯一）不可删；域下仍存在有效 `domain_config` 行时引用检查拒删（schema「删除前检查引用」落地，需先删除该域下配置）；整批校验失败则整批不变更。
- `BizDomainResp` 含 `global` 字段（是否全局域，前端预判禁删）；`create`/`update` 请求体字段长度与格式校验对齐 schema 列宽（code 64 大写字母开头+大写/数字/下划线、name 128、description 512）。
- 权限门禁：读（list/detail）`DOMAIN:VIEW`；写（create/update/remove）`SYSTEM_CONFIG:MANAGE`。DOMAIN:VIEW 已补入空库 bootstrap 固定图（无授予起点死锁防护，OPERATION_LOG:VIEW 先例）。

### 5.2 主体与角色

| 接口                                              | 说明                     |
| ------------------------------------------------- | ------------------------ |
| `POST /api/perm/abstract-user/list`               | 查询主体列表             |
| `POST /api/perm/abstract-user/detail`             | 查询主体详情             |
| `POST /api/perm/abstract-user/create`             | 创建主体，适合管理端     |
| `POST /api/perm/abstract-user/sync`               | 幂等同步外部主体         |
| `POST /api/perm/abstract-user/full-sync`          | 按 scope 全量校准外部主体 |
| `POST /api/perm/abstract-user/update`             | 更新主体                 |
| `POST /api/perm/abstract-user/remove`             | 删除主体，支持批量       |
| `POST /api/perm/abstract-role/list`               | 查询角色列表             |
| `POST /api/perm/abstract-role/tree`               | 查询角色树               |
| `POST /api/perm/abstract-role/detail`             | 查询角色详情             |
| `POST /api/perm/abstract-role/create`             | 创建角色                 |
| `POST /api/perm/abstract-role/sync`               | 幂等同步外部角色         |
| `POST /api/perm/abstract-role/full-sync`          | 按 scope 全量校准外部角色 |
| `POST /api/perm/abstract-role/update`             | 更新角色                 |
| `POST /api/perm/abstract-role/move`               | 移动角色树节点           |
| `POST /api/perm/abstract-role/remove`             | 删除角色，支持批量       |

> T-PERM-043 退役：`/api/perm/abstract-role/extra-roles/list|add|remove` 三接口已删除（分组角色额外基本角色专用入口）。写入口 `add` 自实现起写 `user_role.abstract_user_id=null` 违反 NOT NULL 从未成功，`list` 无数据生产者恒空；前端/SDK 无生产调用方，不做兼容层。角色包含关系待未来按 `role_inclusion(group_role_id, included_role_id)` 单事实源另行立项。`create`/`update` 显式拒绝 `GROUP_ROLE`（复用 `ROLE_TYPE_MISMATCH(20022)`，见 §6.10.3）。

#### `POST /api/perm/abstract-role/list`

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
- `move`：目标父须与移动角色**同角色类型**（同类型内嵌套合法，跨类型嵌套拒绝 **20022** `ROLE_TYPE_MISMATCH`）；目标父为移动角色**自身或其子孙**拒绝 **20050** `ROLE_PARENT_INVALID`（新增错误码，perm 段顺延——parent 链成环后祖先/子孙递归 CTE 不收敛、环节点从树构建中静默消失；对齐 admin 域 `ORG_PARENT_CYCLE`/`MENU_PARENT_INVALID` 先例）。`parentId=null`（移到顶层）不受两者限制。存量 GROUP_ROLE 组树为冻结读模型，跨类型装配自本任务起无 API 通道。并发语义（T-PERM-044 收口）：校验前先取 (abstract_role, 租户) 树级分布式锁（Redisson，事务提交/回滚后经 afterCompletion 释放），同树 parent 写路径（含 sync/full-sync）串行化，交叉移动的后进锁者校验时可见先提交的 parent——环无法落库；环脏数据下递归 CTE/祖先链遍历亦有防环止损（architecture §17）。
- `list`/`detail` 读门禁：类型级 `ROLE:VIEW`（评审收口补齐，与 `tree` 同款——`list` 信息量不低于 `tree`，不设门禁会使 tree 门禁事实可绕；无权抛 SecurityException）。
- `update` 补 `extraClear` 显式清空标志（T-FE-016 联调收口，对齐 §5.3 resource-entity 同款口径）：boolean 可选，`true`=清空 `extra` 为 null、优先于 `extra`（JSON null 无法区分「未传」与「清空」）；其余字段 null=不更新维持。前端编辑表单按「原 extra 非空且表单清空」判定传 `true`（resource-operation 同构公式）。
- `sync`/`full-sync` 的 parent 同款环路判定（评审收口补齐）：parent 为目标角色自身或其子孙时拒绝（单条 nonRetryable、批量该项 failed + `ROLE_PARENT_INVALID`）；只读版本预判先行——旧版本无条件按 STALE 钝化（§6.2.2.5 成功 no-op，含携带非法父边的旧事件），新版本才进入父解析/判环，判环拒绝不推进版本（上游修正后同版本重试不被判 STALE；预判与写入间的并发交错由 applyVersion 原子判定兜底）。full-sync 为**写入前逐项判定**：当前生效图 = 库内既有关系 + 本事务已应用项的边，内存图严格镜像写入语义（未携带父字段的更新不清图内旧边）——STALE 项不落边、天然保持旧边（含同批次多边共同成环、批内/库内混合、STALE+APPLIED 混合的组合均覆盖），仅拒绝真正闭合环的项、指向环的前缀安全项放行。同批重复 `businessKey` 的后续项写入前拒绝（`DUPLICATE_BUSINESS_KEY`，对齐 user-role/full-sync 先例）。item 省略 `parentRoleTypeCode` 时缺省 = `scope.roleTypeCode`（§6.2.2.4 既有规则，父解析/判环/写入均按缺省类型）。
- `remove` 级联覆盖 BASIC_ROLE 子孙（容器类型 GROUP_ROLE/ORG 之外）；级联根有权即整棵子树可删、不对子孙做独立权限过滤（项目规则「父级有权限子级即有权限」，2026-08-28 设计定案——统一引擎判定面继承落地登记 T-PERM-057，终态设计 query-engine-unification.md §6/§10；原 T-PERM-045 已取消并入 057）。

### 5.3 资源与操作

| 接口                                          | 说明               |
| --------------------------------------------- | ------------------ |
| `POST /api/perm/operation-permission/list`    | 查询操作权限       |
| `POST /api/perm/operation-permission/detail`  | 查询操作详情       |
| `POST /api/perm/operation-permission/create`  | 创建操作           |
| `POST /api/perm/operation-permission/update`  | 更新操作           |
| `POST /api/perm/operation-permission/remove`  | 删除操作，支持批量 |
| `POST /api/perm/resource-entity/tree`         | 查询资源树         |
| `POST /api/perm/resource-entity/list`         | 查询资源列表       |
| `POST /api/perm/resource-entity/detail`       | 查询资源详情       |
| `POST /api/perm/resource-entity/create`       | 创建资源           |
| `POST /api/perm/resource-entity/batch-create` | 批量创建资源       |
| `POST /api/perm/resource-entity/update`       | 更新资源           |
| `POST /api/perm/resource-entity/move`         | 移动资源树节点     |
| `POST /api/perm/resource-entity/remove`       | 删除资源，支持批量 |
| `POST /api/perm/resource-entity/sync`         | 资源实体专用幂等同步 |
| `POST /api/perm/resource-entity/full-sync`    | 按 scope 全量校准资源 |

**业务键定位（T-PERM-028 定稿）**：`resource-entity` 的 `detail/update/move/remove` 与 `operation-permission` 的 `detail/update/remove` 以业务键定位，不再接受内部 id（schema `uk_resource_entity` / `uk_operation_permission_typed` + `ck_operation_permission_resource_type_required` 非空 CHECK 共同保证唯一（原 `uk_operation_permission_global` 已随全局操作退役删除，T-PERM-049）；混合形态——detail/update 键字段平铺、move 嵌套、remove items 数组）：

```json
// resource-entity/detail（ResourceKeyReq 平铺）
{ "resourceTypeCode": "MENU", "code": "sys-mgmt", "codeType": "default" }
// resource-entity/update（ResourceUpdateReq：键平铺 + 可编辑字段；code 为业务键不可更新）
{ "resourceTypeCode": "MENU", "code": "sys-mgmt", "codeType": "default",
  "name": "系统管理", "path": null, "status": 1, "sortOrder": 10,
  "extra": "{\"k\":1}", "extraClear": false }
// resource-entity/move（ResourceMoveReq 嵌套；parent null=移动到顶层）
{ "resource": { "resourceTypeCode": "MENU", "code": "sys-mgmt", "codeType": "default" },
  "parent": null }
// resource-entity/remove（ResourceKeysReq）
{ "items": [ { "resourceTypeCode": "MENU", "code": "sys-mgmt", "codeType": "default" } ] }
// operation-permission/detail|update（OperationKeyReq / OperationUpdateReq：resourceTypeCode 必填）
{ "resourceTypeCode": "USER", "code": "VIEW" }
// operation-permission/remove（OperationKeysReq）
{ "items": [ { "resourceTypeCode": "USER", "code": "VIEW" }, { "resourceTypeCode": "USER", "code": "MANAGE" } ] }
```

| 规则 | 口径 |
|---|---|
| codeType | 可选，null/缺省归一为 `default`（DDL 默认值） |
| 业务键查不到 | detail 抛 20004/20005；update/move 同（原 `data:null` 宽松形态已删除） |
| extraClear | boolean 可选；true=清空 extra 为 null，优先于 extra（JSON null 无法区分「未传」与「清空」） |
| move 校验 | 跨资源类型 / 目标父为自身或子孙 → 20053 RESOURCE_PARENT_INVALID（一类码两因，message 区分） |
| remove 未命中键 | 静默跳过（对齐原 ids 批删语义），级联软删子孙 |
| SYNC 类型只读（T-PERM-052） | 目标类型声明 `extra.managedMode=SYNC` 时 create/batch-create/update/move/remove 一律拒绝 **20055** `RESOURCE_EXTERNALLY_MAINTAINED`（资源由外部来源维护，请到来源系统操作）；**remove 的级联守卫覆盖删除全集（含展开的后代）**——sync 通道允许跨类型父子边，MANAGED 根的子树可能含 SYNC 类型后代，命中即整批拒绝不软删；读路径（tree/list/detail）不受限 |

**读门禁（T-PERM-028 补齐，类型级）**：`resource-entity/list`、`resource-entity/detail` 补 `RESOURCE:VIEW`，`operation-permission/detail` 补 `OPERATION:VIEW`（与既有 tree/list 门禁同口径；bootstrap §14.4 最小集已持有，不阻断首管理员）。

**resource_type 创建联动预置（T-PERM-028 实现定案）**：`type-definition/create` 在 `typeKey=resource_type` 时同事务预置 CRUD 四操作位 `CREATE(1,0)/VIEW(2,0)/UPDATE(4,2)/DELETE(8,2)`（DDL CROSS JOIN 预置组模板同款；新类型位段空闲无 uk 冲突）；非 resource_type 类型不预置。

**请求（类型查询参数）**：

```json
{
  "resourceTypeCode": "ORG"
}
```

| 参数 | 类型 | 口径 |
|---|---|---|
| resourceTypeCode | string\|null | 可选；null/缺省 = 不过滤，返回全量操作定义；空白串视同缺省（不过滤）；指定类型 = 仅该类型操作定义；**指定但类型不存在 = 空列表**（fail-closed 不回退全量，T-PERM-040 收口 2026-08-31，与 role-resource-permission/list 同口径）。原 domainCode 参数已删除（死参数：从未实现过滤、契约未登记语义、零调用方，2026-08-31 设计定案） |

> **已知不一致（2026-09-02 T-FE-018 需求对齐会登记，待办收口、暂不实施）**：`resource-entity/tree` 传 `resourceTypeCode` 但类型不存在时，类型解析失败视同未传 → 返回全类型全量资源树（**fail-open**），与本节 operation-permission/list 及 §6.4 role-resource-permission/list 的「未知类型 = 空列表 fail-closed」（2026-08-31 定案）口径不一致。授权页类型候选恒来自 type-definition 有效值，无页面触达路径（T-FE-018 联调不修，后端零改动纪律）；后续收口时对齐为空列表 fail-closed。注：tree 的 `domainCode` 是活参数（DomainClassifyService GLOBAL_PLUS 分类过滤），与 operation-permission/list 已删除的 domainCode 死参数无关。

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

前端来源链计算（permission-grant.md §3.5）依赖 binaryBit/inheritMask 做 BigInt 位运算。~~联调门禁~~ **T-PERM-028 已落地**：binaryBit/inheritMask 十进制字符串线格式（含 2^62 位值）已由 `OperationPermissionWireFormatTest` + `ResourceOperationKeyPgIT` 锁定，T-FE-018 可切真实接口；请求侧 Long 组件由 Jackson 宽容接受十进制字符串。

### 5.4 服务与接口映射

| 接口                                         | 说明                              |
| -------------------------------------------- | --------------------------------- |
| `POST /api/perm/service-config/list`         | 查询接入服务                      |
| `POST /api/perm/service-config/detail`       | 查询服务详情                      |
| `POST /api/perm/service-config/save`         | 幂等保存服务                      |
| `POST /api/perm/service-config/remove`       | 删除服务，支持批量                |
| `POST /api/perm/service-config/sync`         | 全量同步服务接口，权限中心做 diff |
| `POST /api/perm/service-config/apis`         | 查询服务接口映射列表（扁平）      |
| `POST /api/perm/resource-api-mapping/list`   | 查询接口映射                      |
| `POST /api/perm/resource-api-mapping/create` | 创建接口映射                      |
| `POST /api/perm/resource-api-mapping/update` | 更新接口映射                      |
| `POST /api/perm/resource-api-mapping/remove` | 删除接口映射，支持批量            |

**service-config / resource-api-mapping 契约要点（T-PERM-027 收口，2026-08-29）**：

- `list` 维持 `{}` 全量返回（设计定案：服务登记数量有界——租户内微服务个数，页面左栏目录面板本地过滤，无分页参数与分页响应）；门禁 SERVICE:VIEW 类型级。
- `save` 幂等（`{serviceCode, name, basePath?, description?, status?, extra?}`，按 `uk_service_config(tenant_id, service_code)` 定位，null 字段不更新）；`extra.syncTypes` 结构校验见 §6.3.1。`ServiceConfigResp` 含 `updatedAt`（保存与 FULL 同步回写 basePath 时刷新；`lastSyncedAt` 不设——无现成列且聚合推导语义模糊，登记不做）。
- `remove` 级联清理（设计定案）：同事务软删该服务**全部** API 映射（含 MANUAL 维护来源——服务已删则其路由不再存在，映射即死路径）+ 该服务 SERVICE_SYNC 自动维护的孤立 API 资源（FULL diff 同清理边界，§6.3；被其他服务跨服务手工映射引用的资源保留），事务提交后广播 Gateway 本地快照失效（受影响 serviceCodes）；整批失败整批不变更。
- `sync` 仅接受 `syncMode=FULL`（§6.3）：DTO 校验层 `@Pattern("FULL")` 拒绝其他值（`MethodArgumentNotValidException` → HTTP 400，body `code=90001` 参数校验失败），增量策略已删除（全仓零生产调用）。门禁 SERVICE:SYNC_INTERFACE 实例级（serviceCode）。
- `apis` 与 `resource-api-mapping/list` 返回的 `ApiMappingResp` 含关联资源业务字段 `resourceCode/resourceName/resourceTypeCode/maintainSource`（批量补全；资源已软删时为 null，前端回退展示内部 `resourceEntityId`）——`apis` 实现委托 `list`（同层复用，门禁与补全单点）。`list` 门禁（补齐）：请求带 `serviceCode` 按该服务实例 VIEW 校验；不带（管理全量列表）类型级 VIEW + 结果按服务维裁剪（拒绝服务的映射不出现在结果中）。
- `resource-api-mapping/create`/`update` 仍以内部 `resourceId` 绑定资源（§6.10.4 单条响应，§5.4 定案不随业务键切换）；前端资源选择器已随 **T-PERM-028** 落地（类型下拉 + 资源树选择，选中取节点内部 id 提交，数据源 `resource-entity/tree`），裸数字输入形态已删除。
- 权限门禁：读 SERVICE:VIEW（list/detail/apis、mapping list）；写 save/remove = SERVICE:MANAGE、sync = SERVICE:SYNC_INTERFACE、映射 create/update/remove = SERVICE:MANAGE_API_MAPPING（批量 remove 按映射行 serviceCode 批量校验）。SERVICE:VIEW/MANAGE/SYNC_INTERFACE 已补入空库 bootstrap 固定图（死锁防护=持有解锁首管理员页面读写，MANAGE_API_MAPPING 与 DOMAIN:VIEW 先例；三条均类型级 scopeAll 且**不可转授**——业务门禁统一口径，转授链仅 API:ACCESS，首管理员不能把 SERVICE 权限授予其他角色）。

### 5.5 授权关系

| 接口                                                   | 说明                                           |
| ------------------------------------------------------ | ---------------------------------------------- |
| `POST /api/perm/user-role/list`                        | 查询用户角色关系                               |
| `POST /api/perm/user-role/sync`                        | 幂等同步组织/岗位用户角色关系                   |
| `POST /api/perm/user-role/full-sync`                   | 按 scope 全量校准组织/岗位用户角色关系           |
| `POST /api/perm/user-role/assign`                      | 批量分配角色或分组                             |
| `POST /api/perm/user-role/revoke`                      | 批量回收角色关系                               |
| `POST /api/perm/user-role/batch-assign`                | 按角色视角批量分配多个用户                     |
| `POST /api/perm/role-resource-permission/list`         | 查询角色权限配置（§6.4）                                       |
| `POST /api/perm/role-resource-permission/apply-grant-plan` | **授权页面唯一写入口**（§6.5.1）：记录级 creates/updates/removes + 单事务原子 + 受影响行数断言（无 CAS/无幂等表，收窄） |
| `POST /api/perm/role-resource-permission/sub-perm-allowed-types` | **授权页只读契约（v3.1，已随 T-PERM-034 落地 2026-08-30）**：按父资源类型返回 SUB_PERM 允许的子资源类型（§6.5.2） |
| `POST /api/perm/role-resource-permission/save` ~~已删除~~ | 旧批量授予（已随 T-PERM-034 删除，2026-08-27 端点退役收口，无映射 404；管理域存量调用经核实为零） |
| `POST /api/perm/role-resource-permission/revoke` ~~已删除~~ | 旧批量撤销（同上删除；删除语义由 apply-grant-plan.removes 覆盖） |
| `POST /api/perm/role-resource-permission/children` ~~已删除~~ | 旧子权限查询（同上删除；查询由 list includeChildren 覆盖） |
| `POST /api/perm/role-resource-permission/add-child` ~~已删除~~ | 旧子权限新增（同上删除；新增由 creates + parentPermissionId 覆盖） |
| `POST /api/perm/role-resource-permission/update-child` ~~已移除~~ | 编辑子权限（同上移除；updates 覆盖）                           |
| `POST /api/perm/role-resource-permission/children-save` ~~已移除~~ | 批量子权限提交（同上移除；plan 三段覆盖）                      |
| `POST /api/perm/role-resource-permission/rebuild` ~~已移除~~ | 主权限原子重建（同上移除；removes+creates 同事务覆盖）         |
| `POST /api/perm/role-resource-permission/remove-child` ~~已删除~~ | 旧子权限删除（已随 T-PERM-034 删除，无映射 404；删除由 removes 覆盖） |

### 5.6 高级能力

| 接口                                            | 说明                   |
| ----------------------------------------------- | ---------------------- |
| `POST /api/perm/permission-condition/list`      | 查询权限条件（**无读取门禁，2026-08-08 产品确认：条件规则全租户开放、非敏感**） |
| `POST /api/perm/permission-condition/detail`    | 查询条件详情（**同上：无读取门禁**；业务键 code 定位，查不到 20006） |
| `POST /api/perm/permission-condition/create`    | 创建条件               |
| `POST /api/perm/permission-condition/update`    | 更新条件（业务键 code 定位） |
| `POST /api/perm/permission-condition/remove`    | 按条件编码批量删除     |
| `POST /api/perm/domain-config/list`             | 查询域配置             |
| `POST /api/perm/domain-config/detail`           | 查询单条域配置         |
| `POST /api/perm/domain-config/save`             | 幂等保存域配置         |
| `POST /api/perm/domain-config/remove`           | 删除域配置             |
| `POST /api/perm/resource-dependency/list`       | 查询资源依赖           |
| `POST /api/perm/resource-dependency/create`     | 创建资源依赖           |
| `POST /api/perm/resource-dependency/update`     | 更新资源依赖           |
| `POST /api/perm/resource-dependency/remove`     | 删除资源依赖，支持批量 |
| `POST /api/perm/resource-dependency/batch-sync` | 按资源全量同步依赖     |
| `POST /api/perm/resource-dependency/graph`      | 查询依赖图             |
| `POST /api/perm/resource-dependency/check`      | 检查依赖是否成环       |
| `POST /api/perm/conflict-rule/list`             | 查询冲突规则           |
| `POST /api/perm/conflict-rule/detail`           | 查询冲突规则详情       |
| `POST /api/perm/conflict-rule/create`           | 创建冲突规则           |
| `POST /api/perm/conflict-rule/update`           | 更新冲突规则           |
| `POST /api/perm/conflict-rule/remove`           | 删除冲突规则，支持批量 |
| `POST /api/perm/conflict-rule/detect`           | 冲突检测               |

**permission-condition 契约要点（T-PERM-029 收口，2026-08-30）**：

- **业务键**：管理端点 `detail`/`update`/`remove` 一律以 `code` 定位（`uk_permission_condition(tenant_id, code) WHERE delete_flag=0`，从内部主键 id/conditionId 切换；管理端点请求体不再使用内部 id——id 仅见于 Resp、授权链路 `role_resource_permission.condition_id` 引用及 explain 排查明细 `conditionEvaluations[].conditionId`）。`detail` 请求 `{conditionCode}`（ConditionDetailReq）；`update` 请求 `{code, name?, conditionRules?, enabled?, gatewayEvaluable?, description?}`（code 为定位键不可改，null 字段不更新，name≤128/description≤512 列宽校验）；`remove` 请求 `{codes:[...]}`（ConditionRemoveReq，元素 1-64 字符非空白，批量软删）。
- **detail 收紧**：查不到抛 **20006** `CONDITION_NOT_FOUND`（原 `data:null` 宽松语义删除，对齐 resource-entity/detail 收紧定案与授权链路 apply-grant-plan 未知 conditionCode 同码）。
- **list 全量不分页**（设计定案）：`{}` 返回全量 `ItemsResp<ConditionResp>`——条件模板数量有界（租户内几十个量级，非流水表），与 domain-config/service-config「量小不分页」同款；keyword/enabled 过滤由前端本地完成（前端设计文档 §8 🔧3 登记的 ConditionListReq 分页方案据此反转）。
- **ConditionResp**：`{id, tenantId, code, name, conditionRules, enabled, gatewayEvaluable, description, createdAt, updatedAt}`——`updatedAt` 为本次补齐（entity 有列但 Resp 此前不返回）；`conditionRules` 结构 `{logic, items[]}`（4 预置类型 DATE_RANGE/TIME_RANGE/IP_WHITELIST/IP_BLACKLIST），语义等价可直接再提交——**注意来源差异**：list/detail 为 JSONB 回读的 DB 规范化文本，create/update 直接返回本次最终接受的规则文本（未做写后反查，调用方提交的空白/键序原样保留）。
- **权限门禁**：读（list/detail）无门禁（2026-08-08 产品确认：条件规则全租户开放、非敏感）；写 create/update/remove = CONDITION:CREATE/UPDATE/DELETE **类型级**（scope_all）——CONDITION 为权限定义元数据（同 OPERATION/SYSTEM_CONFIG），无 resource_entity 实例投影、`role_resource_permission.resource_entity_id` 引用 resource_entity.id 空间，实例级授权无从配置；原「实例级门禁」声称系 ID 空间错位，2026-08-30 口径收窄废弃（实例投影与条件双轨制登记 T-PERM-048）。remove 为类型级全有或全无（无 DELETE 类型级授权整批拒绝）。CONDITION 的 CREATE/UPDATE/DELETE 三档独立，非 MANAGE 聚合（与 RESOURCE/OPERATION 的 CREATE+MANAGE 两档不同）。
- **gatewayEvaluable 联合校验**（T-PERM-017 C2.5）：create/update 取「最终状态」校验——只切 flag 不改 rules 用 DB 老 rules、同改用新 rules、已 true 改 rules 用新 rules；`gatewayEvaluable=true` 要求 `logic ∈ {AND, OR}`（缺省 AND）且 `items[].type` 全在 `ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES` 白名单，不通过 20031 `CONDITION_RULES_INVALID`。update/delete 后经 `@PermissionChange` 反查受影响 serviceCodes 广播 Gateway 接口快照失效。
- **remove 幂等语义**：请求中不存在或已删除的 code 静默跳过、重复 code 去重（幽灵键幂等语义与 resource-entity/remove 一致；拒绝语义为类型级全有或全无——无 CONDITION:DELETE 类型级授权整批拒绝，resource-entity 为实例级删有权部分）；remove 响应 Void 无行数，调用方以事后查询核对。
- **create 重复 code**：无预查友好码，由 uk 兜底拒绝（系统错误通道），与 resource/type-def create 同款。

**conflict-rule 契约要点（T-PERM-030 收口，2026-08-30）**：

- **定位键维持内部主键 id**（评估定案）：冲突规则无业务键——`type + 对象对 + resourceTypeValue` 为复合语义身份，无单列 code 可切（与 resource/operation/condition 不同）；`detail` 请求 `{id}`（IdReq）、`update` 请求 `{id, conflictType, ...}`、`remove` 请求 `{ids:[...]}`（IdsReq）。Resp 含 id/tenantId 透出（前端名称映射按 id 关联 role/operation 列表建立）。
- **detail 收紧**：查不到抛 **20020** `CONFLICT_RULE_NOT_FOUND`（原 `data:null` 宽松语义删除，对齐 resource-entity/condition 两先例；update 侧既有同码，先解析后门禁——未知 id 优先 20020 且零副作用）。
- **list 全量不分页**：`{}` 返回全量 `ItemsResp<ConflictRuleResp>`（量小非流水表，对齐 condition/domain-config 定案）；类型/关键词过滤由前端本地完成。
- **门禁四档类型级**（2026-08-30 口径，经决策）：读 list/detail/detect = CONFLICT_RULE:VIEW、写 create/update/remove = CONFLICT_RULE:CREATE/UPDATE/DELETE，全部类型级（scope_all）——CONFLICT_RULE 无 resource_entity 实例投影，实例级授权无从配置，原「编码轨传内部 id」的实例级声称系 ID 空间错位已废弃（与 CONDITION 同口径，实例投影登记 T-PERM-048）。remove 类型级全有或全无，幽灵 id 静默跳过（幂等，对齐 resource-entity/condition remove）。
- **update 全量覆盖语义**（PUT）：`conflictType` 必填，按类型字段集全量覆盖、对侧字段强制 null（UpdateEntity 显式写列）；PERM_MUTEX 下 `resourceTypeValue` 显式传（null=清空"全部"）；对象对写库前规范化 first&lt;second（对齐 uk_conflict_rule_perm/role 唯一索引）；审计 updatedBy/updatedAt 随写。等价规则（同类型+同对象对双向+同 rtv）拒绝 **20032** `CONFLICT_RULE_DUPLICATE`（业务层预查 + DB 唯一约束兜底，NULLS NOT DISTINCT 覆盖 NULL 全局规则）。
- **detect**：仅操作权限对（PERM_MUTEX 场景），双向匹配（规则 (A,B) 对请求 (A,B)/(B,A) 均命中）；`resourceTypeValue` 可空过滤，`resource_type_value IS NULL` 的全局规则始终参与（schema「NULL=所有」语义，SQL 层保证）；响应 `{conflictDetected, matchedRules[]}`，纯查询无副作用。
- **ConflictRuleResp**：`{id, tenantId, conflictType, firstOperationPermissionId, secondOperationPermissionId, resourceTypeValue, firstAbstractRoleId, secondAbstractRoleId, description, createdAt, updatedAt}`——`updatedAt` 为本次补齐（entity 列本就存在）；description ≤512 列宽校验（create/update）。
- **bootstrap 固定图**：CONFLICT_RULE:VIEW/CREATE/UPDATE/DELETE 四条类型级不可转授随本批补入（空库无授予起点死锁防护，同 DOMAIN/SERVICE 先例）；顺带补 CONDITION:CREATE/UPDATE/DELETE 三条（T-PERM-029 遗漏的同款缺口，读取无门禁故无 VIEW 条目）。

**resource-dependency 契约要点（T-PERM-031 收口，2026-08-30）**：

- **门禁五档类型级**（DEPENDENCY 无 resource_entity 实例投影，实例级授权无从配置，同 CONDITION/CONFLICT_RULE 口径）：读 list/graph/check = DEPENDENCY:VIEW（原三读端点无门禁，本批补齐——graph/check 透出全租户依赖图数据）；写 create/update/remove = DEPENDENCY:CREATE/UPDATE/DELETE；batch-sync = DEPENDENCY:SYNC。remove 类型级全有或全无，幽灵 id 解析阶段静默跳过（幂等，响应 Void 无行数）；update 先解析后门禁（未知 id 优先 **20019** DEPENDENCY_NOT_FOUND 且零副作用，T-PERM-029 模式）。
- **update PUT 全量覆盖语义**（Q3=B 前端契约收口）：UpdateReq 补齐资源对业务键四字段（source/targetResourceTypeCode+Code 必填 + codeType 可选）——原 DTO 缺资源对字段导致前端「改资源对」提交被静默丢弃；全部字段按提交值覆盖（UpdateEntity 显式写列），`sourceOperationCodes` null/空=清空为「任意操作触发」、`description` null=清空；`maintainSource`/`ownerServiceCode` 来源归属不随管理端编辑改写（仅批量同步侧变更）。
- **等价重复预查**：同源资源+同目标资源+同 `COALESCE(source_operation_bits,0)`（uk_resource_dependency 唯一语义，NULL 与 0 同档）命中拒绝 **20054** DEPENDENCY_DUPLICATE（本批新增错误码，业务层预查 + DB uk 兜底转同码，对齐 conflict-rule 20032 先例）。
- **操作码 fail-closed**：create/update/batch-sync 收到解析不到的 `operationCodes` 拒绝 **20005** OPERATION_NOT_FOUND（原实现静默丢弃——部分丢弃合并已知位、全 miss 落 0，会写出语义错误规则且无报错）；空白元素忽略、全空白码列表拒绝 20044（三入口统一口径）。
- **自依赖拒绝**：source==target（即成环）写入口拒绝 20044 INVALID_PARAM（前端表单已校验，后端兜底；check 端点同判定返回 hasCycle=true）。
- **list 全量不分页**（设计定案，对齐 condition/conflict-rule：量小非流水表）；`resourceEntityId` 内部主键可选过滤保留，关键词过滤由前端本地完成。**graph 维持扁平列表**（非 nodes/edges 图结构，设计定案，前端建图）。
- **ResourceDependencyResp**：`{id, tenantId, resourceEntityId, sourceResourceCode, sourceResourceTypeCode, sourceResourceName, dependsOnResourceEntityId, depResourceCode, targetResourceTypeCode, targetResourceName, sourceOperationBits, requiredOperationBits, autoGrant, description, ownerServiceCode, maintainSource, createdAt, updatedAt}`——本批补静态字段（类型编码经 type_definition 反查、名称取资源实体、updatedAt/来源归属字段）；`sourceOperationBits`/`requiredOperationBits` 为 63 位 bigint 位值列，改**字符串线格式**下发（T-PERM-028 binaryBit 同款，JS Number ≥2^53 丢精度）；操作码数组不反解（前端经操作列表建 bit 映射拆解，操作软删后残留位反解有歧义）。description ≤512（create/update/sync item）。
- **maintainSource 四值白名单**：ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC（schema 口径收口，DTO @Pattern 拒绝其他值——原注释 SERVICE/MANUAL 系漂移）；管理端创建行显式落 `ADMIN_UI`+`ownerServiceCode=null`。
- **batch-sync**（§6.9）：FULL diff 匹配键为「源+目标+COALESCE(source_bits,0)」三元组（原仅按资源对匹配——同资源对不同触发操作是不同规则，漏删且 upsert 错行，本批修正）；资源 ID 与操作位在循环外按类型批量预解析（消解逐条目解析的 N+1）；**清单级预检**（autoGrant + 必填操作码）先于资源解析与全部写操作（畸形清单零副作用）；`items` 级联校验（`@Valid + @NotNull`）与两段操作解析 fail-closed 见 §6.9。
- **bootstrap 固定图**：DEPENDENCY:VIEW/CREATE/UPDATE/DELETE/SYNC 五条类型级不可转授随本批补入（空库无授予起点死锁防护，同 DOMAIN/CONFLICT_RULE 先例；SYNC 随四档同补——端点存在且门禁为 SYNC，前端 P0 未接入不改变端点事实）。

**domain-config 契约要点（T-PERM-026 收口，2026-08-29）**：

- `save`（upsert）：`{domainCode, configType, extra}`——按 `domainCode+configType` 查存在则 update `extra`、不存在则 insert（新建/编辑统一走 save）；`configType` 白名单仅接受 `SUB_PERM/CLASSIFY`（历史设想类型 SCOPE/RELATION/BINDING 未实现，写入校验拒绝）；`extra` 为 JSON 字符串，写入前经 `JsonValidationUtils` 语法校验（非法 JSON fail-closed，统一异常通道返回 `code=400` 参数错误，system-config `configValue` 同款）；未知 domainCode 拒绝 **20017** `DOMAIN_NOT_FOUND`。
- `detail`：`{domainCode, configType}` 业务键二元组，未命中 `data=null` 不抛错。
- `list`：`{domainCode?}` 过滤（不传全量），量小不分页（每域至多 SUB_PERM/CLASSIFY 两条）。
- `extra` JSONB↔String 映射已确认（`JsonbStringTypeHandler`，BizDomainConfigPgIT 真库锁定）：读出为 DB 规范化 JSON 文本，语义等价、可直接再提交。
- `extra` 字段名口径（save 仅校验 JSON 语法不校验 schema，字段名以消费方为准，PgIT 跨层锁锁定）：CLASSIFY 用 `{"resourceTypeCodes":["ORG","USER"]}`（`DomainClassifyService` 消费）；SUB_PERM 用 `{"allowed":[{"parent_type":"USER","child_types":["POSITION"]}]}`（授权链路 `assertSubPermissionAllowed` 消费，`*` 通配）。
- 权限门禁：读（list/detail）`SYSTEM_CONFIG:VIEW`；写（save/remove）`SYSTEM_CONFIG:MANAGE`。
- 并发语义（T-PERM-046 收口，2026-09-09）：①save 的 check-then-insert 并发双插窗口由部分唯一索引 `uk_domain_config(tenant_id, biz_domain_id, config_type) WHERE delete_flag=0` 兜底，违例映射 **20058** `DOMAIN_CONFIG_CONCURRENT_CONFLICT`（提示重试——后到者重试时另一事务已提交，转 update 分支）；②biz-domain remove 的引用检查（FOR UPDATE 域行锁）与 save 的域解析（同款域行锁）双向互斥：remove 提交后 save 解析不到软删域（20017），save 持锁插入的配置会被 remove 引用检查看到（20051 拒删）——孤儿配置窗口闭合。
- 全局域 CLASSIFY 声明生效（T-PERM-046 用户定案 2026-09-09）：save 目标域为全局域时 CLASSIFY 配置有真实语义（全局域实际范围=声明集，声明即收窄覆盖动态补集；GLOBAL_PLUS 隐式段同源对齐）；SUB_PERM 挂全局域继续有效（未被认领类型经反查兜底读全局域策略）。

### 5.7 运行时鉴权与权限查询

| 接口                                      | 说明                                                   |
| ----------------------------------------- | ------------------------------------------------------ |
| `POST /api/perm/auth/check`               | 单次资源权限判定                                       |
| `POST /api/perm/auth/batch-check`         | 批量资源权限判定                                       |
| `POST /api/perm/auth/query-resources`     | 查询主体在指定资源类型和操作下可访问或可管理的资源集合 |
| `POST /api/perm/auth/query-scopes`        | 查询主体在某个主资源上下文内可用的范围资源权限集合     |
| `POST /api/perm/auth/check-interface`     | Gateway 接口级判定                                     |
| `POST /api/perm/auth/interface-snapshot`  | Gateway 接口权限快照，可选优化接口                     |

> **SDK 入口清单（T-API-002 收口 2026-09-06；T-API-003 check 族改写 2026-09-10）**：`check` / `batch-check` / `query-resources` / `query-scopes` 四件套已全部进入 perm-sdk `PermissionFeignClient`（Query* DTO 迁入 perm-common，接入方不再手写 HTTP + 自造 DTO 副本）；`check-interface` / `interface-snapshot` 为 Gateway 专用（经 starter/PermissionClient 消费），不开放业务服务 Feign 入口。**内部数据库 id 字段族口径分族**：check 族三端点（check/batch-check/check-interface）结果记录全量回传（`matchedRoleIds`/`matchedPermissionIds`/`matchedResources[].resourceId`，T-API-003 推翻 T-API-002 的 check 族裁剪——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场，详见 §6.1/§6.2 注记）；Query\* 响应族（query-resources/query-scopes）六字段裁剪维持 T-API-002 终态（详见 §6.6/§6.7 各节注记）。

### 5.8 视图与审计

| 接口                                                   | 说明                                                   |
| ------------------------------------------------------ | ------------------------------------------------------ |
| `POST /api/perm/permission-view/effective-roles`       | 查询用户有效角色                                       |
| `POST /api/perm/permission-view/effective-permissions` | 分页筛选查询用户或角色当前有效权限                     |
| `POST /api/perm/permission-view/resource-tree`         | 查询用户资源树                                         |
| `POST /api/perm/permission-view/resource-users`        | 查询拥有资源权限的用户                                 |
| `POST /api/perm/permission-view/role-permissions`      | 查询角色权限视图                                       |
| `POST /api/perm/permission-view/explain`               | 解释单个用户或角色对某资源操作的当前权限和近期影响事件 |
| `POST /api/perm/permission-view/recent-changes`        | 查询近期可能影响用户或角色权限的变更事件               |
| `POST /api/perm/log/operation/list`              | 操作日志（路径以 LogQueryController 实现为准；历史误写已随 T-ACCESS-007 修正） |
| `POST /api/perm/log/operation/action-options`    | 操作日志 action 字典（T-PERM-025 新增） |
| `POST /api/perm/log/change/list`                  | 权限变更日志                                           |
| `POST /api/perm/system-config/list`                    | 查询系统配置                                           |
| `POST /api/perm/system-config/detail`                  | 查询系统配置详情                                       |
| `POST /api/perm/system-config/save`                    | 保存系统配置                                           |

> **system-config 错误码**：`system-config/save`（upsert）在权限校验后、触达数据前 fail-closed 校验配置键命名空间前缀（`admin.`/`permission.`/`access.`），非法键返回 **20047 `CONFIG_KEY_NAMESPACE_INVALID`**（配置键只能使用 admin./permission./access. 命名空间前缀，T-ACCESS-007）。

**system-config 契约要点（T-PERM-024 收口，2026-08-28）**：

- `save`（upsert）：`{configKey, configValue, description?}`——按 `configKey` 查存在则 update、不存在则 insert（新建固定 `isSystem=false` 租户自定义，系统内置仅走种子）；`configValue` 为 JSON 字符串（`JsonValidationUtils` 校验合法性）；`configKey` 命名空间前缀校验 20047（见上）。无 create/update/remove——`save` 幂等覆盖新建/编辑，配置项不可删除（键稳定，防误删回退默认）。
- `detail`：`{configKey}`（按业务键 configKey 查询，非 id）。
- `list`：`{keyword?, pageNum?, pageSize?}` → 分页结构（§3.3）；`keyword` 匹配 configKey/description（LIKE，大小写敏感），排序 `config_key, id`；分页参数均不传 = 字典全量（上限 200，先例 `/role/list`）。
- **configValue JSONB 语义（SystemConfigJsonbPgIT 实证）**：读出为 DB 规范化后的 JSON 文本——与提交值**语义等价**（解析树相等，含中文/嵌套/数组），但非字节回显（JSONB 规范化空白与键序）；展示值可直接再提交（规范化幂等），无截断/转义问题。
- 权限门禁：`list`/`detail` 需 `SYSTEM_CONFIG:VIEW`，`save` 需 `SYSTEM_CONFIG:MANAGE`（操作位种子已由权威 DDL CRUD 预置组覆盖，租户 1）。

**operation-log 契约要点（T-PERM-025 收口，2026-08-28）**：

- `list`：`{module?, action?, operatorId?, since?, until?, targetType?, pageNum, pageSize}` → 分页结构（§3.3，排序 `created_at DESC`）；module/action/targetType **精确匹配**（等值索引友好）；`since`/`until` 为创建时间闭区间（ISO 无偏移墙钟；后端 LocalDateTime 语义为 UTC 墙钟——全链路 UTC §7.4，前端提交数字与表格原样展示对齐）。无 detail 接口——`OperationLogResp` 已含全部字段，详情由前端抽屉展示。
- `action-options`：`{module?}` → `ItemsResp<String>`——返回 operation_log 当前实际存在的 action 去重集合（字典序），供筛选下拉动态拉取；返回实际存在值而非维护端枚举（action 由 `@OperationLog` 注解开放增长，避免双轨漂移）。
- 权限门禁：list 与 action-options 需独立 `OPERATION_LOG:VIEW`（**审计分离**，2026-08-28 设计定案——不再复用 `SYSTEM_CONFIG:VIEW`；资源类型 OPERATION_LOG=30 权威 DDL 种子，bootstrap 固定图已授予管理角色）。权限排查视图（`permission-view/explain`、`permission-view/recent-changes`）已随 T-PERM-033 切被查目标实例 `USER:VIEW`/`ROLE:VIEW`（无独立排查码）。

**permission-change-log 契约要点（T-PERM-032 收口，2026-08-29）**：

- 端点为 `POST /api/perm/log/change/list`（原 §5.8 表格误写 `/api/perm/permission-change-log/list`，已随 T-ACCESS-007 评审修正，此处补记）；无独立 detail——`ChangeLogResp` 含全字段（含 diffSnapshot），前端抽屉展示。
- 筛选全集（维度对齐 schema 索引，2026-08-29 设计定案）：`entityType/entityId`（实体索引）、`eventType`（diff_snapshot.eventType 表达式索引，单选）、`affectedUserId/affectedRoleId`（affected_*_ids GIN 包含匹配）、`since/until`（created_at 闭区间，时间索引；ISO 无偏移墙钟字符串，同操作日志数字对齐口径）、`changeSource`（MANUAL/SERVICE_SYNC 精确匹配，低基数无索引）。服务端分页；页面与 recent-changes 统一条件组。
- `ChangeLogResp` 暴露 `createdBy`（表 created_by，抽象用户 ID；名称解析归前端展示层）。
- 权限门禁：独立 `PERMISSION_CHANGE_LOG:VIEW`（**审计分离**，2026-08-29 设计定案，对齐 OPERATION_LOG 先例；资源类型 PERMISSION_CHANGE_LOG=31 权威 DDL 种子，bootstrap 固定图已授予管理角色）。
- `diff_snapshot.eventType` 增补第 7 枚举 `ROLE_BATCH_DELETE`（批量删除角色的聚合事件：entityId=0 + operation=BATCH_DELETE，items[] 逐角色列出；§6.8 同步），`operation` 列含 `BATCH_DELETE/BATCH_REMOVE`（批量聚合行专用，schema 注释已修正）。

## 6. 核心请求契约

### 6.1 单次鉴权

`POST /api/perm/auth/check`

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

> **`inheritMode` 参数语义接通（T-PERM-057，2026-09-09 落地）**：`NONE`/缺省 = 判定面继承关（目标精确判定）；`PARENT`/`BOTH` = 判定面继承开——判定目标扩为 {目标}∪同类型祖先链（授父资源该操作时子目标判定通过，闭包止步同类型、软删祖先截断）。该参数从「对单点判定结论无效」接通为目标闭包真实语义（判定面继承默认值矩阵见 implementation §3.4）。
> **结果记录全量回传（T-API-003，2026-09-09 定案推翻 T-API-002 的 check 族裁剪，2026-09-10 落地）**：`matchedRoleIds` / `matchedPermissionIds`（role / role_resource_permission 内部行 id）恢复回传——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场。拒绝时为空列表（与允许时非空记录区分）。需要人类可读的来源解释时仍使用 §6.8 `permission-view/explain`（管理端排查端点，`sourceRoles` 业务键 + 门禁）。Query\* 响应族（§6.6/§6.7）的字段裁剪不在推翻范围，维持 T-API-002 终态。
> **主资源上下文与 depend_on 单点闭合（T-PERM-058，2026-09-10 落地）**：查询目标为子权限（depend_on）实例时，可选传入 `parentResourceTypeCode` / `parentResourceCode` / `parentCodeType` / `parentOperationCodes`（与 §6.7 query-scopes 父入参同名对齐；type 与 code 必须成对提供，半传 400；**给出父上下文时 `parentOperationCodes` 一并必填且非空**——缺省/空集按 400 拒绝，勿依赖静默回退：引擎对空操作集不发父判定查询、父判定必不命中）——引擎对父资源做 INSTANCE 判定（含条件/互斥评估，惰性执行：仅当目标命中集确含子行才触发），子行要求其 `depend_on` ∈ 父命中权限集才计入。**不传时子权限行一律不参与判定（fail-closed）**，拒绝原因为 `DEPENDENT_NOT_IN_PARENT_CONTEXT`（与「无任何授权」的 `NO_PERMISSION` 区分；**类型级门禁面（resourceCode=null）为 `NO_PERMISSION`**——该面无 DEPENDENT 分支，SDK 按 reason 分支时勿漏判）——子权限的授权语义是「只在父权限命中的主资源上下文内生效」（§6.7 DEPENDENT 公式），无上下文即无法证明父命中。`batch-check` 的同名四字段为**请求级**（与 query-scopes 对齐：批量项共享同一主资源上下文，如报表A上下文内的广东/杭州/上海多目标）。引擎内部便捷入口（`hasPermissionByCode` / `hasPermissionByEntityId` / `getDenied*`，管理面写门禁矩阵）无主资源上下文概念，同口径 fail-closed；类型级门禁（`resourceCode=null`）只认主授权——scopeAll 子权限行（写侧可造形态）不放行类型级门禁（读侧排除，DB 直写脏数据同受防护）。

### 6.2 Gateway 接口级鉴权

`POST /api/perm/auth/check-interface`

```json
{
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001",
  "serviceCode": "access-service",
  "httpMethod": "POST",
  "path": "/admin/api/user/list",
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
      "resourceTypeCode": "API",
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
- `path` 使用 Gateway 收到的原始路径，不使用 StripPrefix 后的服务内部路径。
- 当同一路径匹配多个资源映射时，接口级鉴权采用 OR 语义：任一映射资源权限通过即允许。
- 响应使用 `matchedResources[]` 返回所有命中的映射资源及各自鉴权结果；只要其中任一项 `allowed=true`，顶层 `allowed=true`。
- 未注册接口默认拒绝，返回 `API_NOT_REGISTERED`。
- **结果记录全量回传（T-API-003，2026-09-09 定案推翻 T-API-002 的 check 族裁剪，2026-09-10 落地）**：`matchedResources[]` 条目的 `resourceId`（resource_entity 内部行 id）与 `matchedRoleIds` / `matchedPermissionIds` 恢复回传——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场；业务键 `resourceTypeCode + resourceCode` 继续并行表达资源身份。现消费方 Gateway 只读 `allowed` / `reason`，不受回传字段影响。

### 6.2.1 Gateway 接口权限快照

`POST /api/perm/auth/interface-snapshot`

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

### 6.2.2 资源实体专用同步

`POST /api/perm/resource-entity/sync`

用于外部事实源把业务对象幂等同步为 permission-center 的 `resource_entity`。该接口只处理资源实体，不是跨实体万能 replay 入口；不接受 `entityType + operationType + payload` 形式。

> 服务间认证：sync/full-sync 接口必须由已验证服务身份调用——凭证通过后绑定 `X-Service-Code`，`SyncAuthVerifier` 从上下文比对（见 `access-service-architecture.md` §6.2 安全策略矩阵）。`sourceService` 必须等于已验证服务身份，不匹配返回 `SECURITY_DENIED`；仅信任请求体 `sourceService` 而不校验服务身份的行为已被禁止。

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
  "sortOrder": 10,
  "extra": {
    "region": "CN"
  },
  "sourceService": "hr-service",
  "sourceEntityType": "hr_org",
  "sourceEntityId": "2001",
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
- 父资源使用 `parentResourceTypeCode + parentResourceCode` 业务键定位，permission-center 内部解析为 `parentId`；父资源不存在时返回 `retryClass=DEPENDENCY_MISSING`，调用方可按短退避重发（T-PERM-044 评审对齐角色先例：父解析与判环先于版本写入——依赖缺失与环路拒绝均不推进同步版本，短退避同版本重发不会被 STALE 挡）。
- 父资源环路防护（T-PERM-044 评审补齐，与 moveResource 同款）：目标父为资源自身或其子孙时拒绝 `retryClass=NON_RETRYABLE`、reason=`RESOURCE_PARENT_INVALID`；full-sync 按内存图逐项判定（当前生效图 = 库内关系 + 本事务已应用项的边），仅拒绝真正闭合环的项，指向环的前缀安全项放行。
- 调用方必须通过可信 Header 提供服务身份；permission-center 必须校验认证服务身份与 `sourceService`（不匹配返回 `SECURITY_DENIED`/`SOURCE_SERVICE_MISMATCH`），禁止任意服务同步任意资源类型。
- **类型级所有权门禁（T-PERM-052，2026-09-05 定案，取代原 syncTypes.resourceTypeCodes 白名单维度）**：目标 `resourceTypeCode` 必须在类型定义上声明 `extra.managedMode=SYNC` 且 `extra.syncSourceService` 等于调用服务身份，且调用服务在 service_config 注册、未软删、`status=1` 启用（评审批次补强：服务停用/注销即四个同步通道一起断，与主体/角色/user_role 白名单语义一致）——任一不满足返回 `SECURITY_DENIED`/`RESOURCE_TYPE_OWNERSHIP_DENIED`（类型不存在一并 fail-closed 拒绝，真实原因仅记服务端日志）。每个资源类型单一所有权：MANAGED（缺省，管理面维护）/ SYNC（声明来源独占同步，管理面只读）；事实链路六类型 USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION 由种子声明 SYNC+access-service（内部来源，见 §5.1；ADMIN_FILE 为 T-ADMIN-025 增补——文件夹实例由 bootstrap 预置+上传惰性登记产出；TYPE_DEFINITION 为 T-PERM-051 增补——类型定义实例投影由写路径同事务维护+bootstrap 自愈补种产出）。外部服务接入先经 `type-definition/create` 建自有类型（如 `HR_ORG`、`BI_MENU`）并声明来源，再调用本接口同步。
- 外部业务服务同步自有资源类型时调用本接口（`resourceTypeCode` 须经类型级所有权门禁，见上条；T-ACCESS-018 的「公共类型外部同步合法」口径已随 T-PERM-052 类型级所有权定案收紧——外部服务同步自身用户/菜单须建自有类型，公共 `USER`/`MENU` 类型不再对同步开放）；事实链路六类型 `USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION` 由种子声明 `SYNC + syncSourceService=access-service`（内部来源，2026-09-05 内部来源统一；ADMIN_FILE 为 T-ADMIN-025 增补、TYPE_DEFINITION 为 T-PERM-051 增补）——本接口对该六类型一律入口拒绝（来源不匹配，较旧口径「不撞投影行即放行」更严），其资源行由 access.application（用户/组织/菜单编排）与 permission 域管理入口（角色/主体编排，T-ACCESS-019）经 LocalProjectionDomainService 同事务独占维护（ADMIN_FILE 文件夹投影走 ensureAdminFileFolder 两条事实链：bootstrap 预置 + 上传惰性登记；TYPE_DEFINITION 实例投影走写路径同事务维护 + bootstrap 自愈补种两条事实链，T-PERM-051）；原类型保留清单与行级投影防线（rejectIfLocalResource 20045）已收编删除，管理面 `create|batch-create|update|move|remove` 对 SYNC 类型（含六类型）统一 20055 只读（见 §5.3）。
- 外部业务服务的全量校准同步走 `resource-entity/full-sync`，不是逐条调用本接口。

#### 6.2.2.1 资源实体分领域全量校准

`POST /api/perm/resource-entity/full-sync`

请求体必须携带强制 scope，permission-center 只在该 scope 对应的同步来源范围内做差异校准，禁止默认按租户全量清理。

```json
{
  "scope": {
    "sourceService": "hr-service",
    "resourceTypeCode": "HR_ORG"
  },
  "items": [
    {
      "resourceCode": "2001",
      "codeType": "default",
      "name": "研发部",
      "parentResourceTypeCode": "HR_ORG",
      "parentResourceCode": "1000",
      "parentCodeType": "default",
      "status": 1,
      "sortOrder": 10,
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
- full-sync item 的父资源默认与 scope 中的 `resourceTypeCode` 同类型、`codeType=default`；若不是默认值，必须显式传入 `parentResourceTypeCode` 和 `parentCodeType`。实现解析父节点时使用 `parentResourceTypeCode + parentResourceCode + parentCodeType`。
- permission-center 以 `sync_metadata(entityKind=RESOURCE_ENTITY, sourceService, scopeKey)` 作为 full-sync ownership 范围；请求中存在则 upsert 并更新 metadata，请求中缺失的 metadata 对应事实按删除语义软删除。`resource_entity.owner_service_code/maintain_source` 不作为本接口的清理依据（`sync_key` 列已删除，2026-09-05）。
- `resource-entity/full-sync` 与既有 `service-config/sync` 是两条独立 ownership 通道。本接口只清理命中 `sync_metadata` scope 的同步事实，绝不按 `resourceTypeCode` 扫描删除资源，也不删除 `service-config/sync`、`MANUAL` 或其他维护来源创建的事实。
- 全量接口仍必须执行 source 身份校验、类型级所有权门禁（scope.resourceTypeCode 同 §6.2.2 单条口径）和旧版本 no-op 规则。
- 组织资源等有树依赖的数据应按足够小的 scope 调用，避免单请求过大。

#### 6.2.2.2 同步接口通用响应与错误分类

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

失败时 `code != 200`，并在 `data.retryClass` 中显式返回调度分类：

```json
{
  "accepted": false,
  "applied": false,
  "stale": false,
  "retryClass": "DEPENDENCY_MISSING",
  "reason": "PARENT_RESOURCE_NOT_FOUND"
}
```

`retryClass` 固定枚举：`RETRYABLE`、`DEPENDENCY_MISSING`、`NON_RETRYABLE`、`SECURITY_DENIED`、`STALE_VERSION`。其中 `STALE_VERSION` 必须使用成功响应，表示请求已接受但未覆盖更新版本。

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

> full-sync 接口顶层字段同 sync；批量明细可选放入 `data.detail`，结构详见 §6.2.2.6。**禁止**为 full-sync 引入独立顶层响应类型（如已删除的 `FullSyncResultResp`）。

#### 6.2.2.3 主体、角色、用户角色同步接口

外部业务服务的主体/角色/成员关系同步使用专用 sync/full-sync 接口，不通过 `resource-entity/sync`，也不复用角色授权管理接口表达同步语义。请求中的 `subjectTypeCode`/`roleTypeCode`/`sourceType` 均为调用方自有类型，禁止使用 AccessMesh 内部保留键（`LOCAL_USER`/`ORG|POSITION`/`SYS_USER_ORG`，20045 拒绝；subject 侧原 `ADMIN_USER` 已随 T-ACCESS-016 更名 `LOCAL_USER`，旧名经类型解析失败直接拒绝）——内部管理事实由 `access.application` 同事务维护本地投影，不经过 sync 链路。

| 接口 | 用途 | scopeKey / businessKey |
|------|------|------------|
| `POST /api/perm/abstract-user/sync` | 主体 UPSERT/DISABLE/DELETE | businessKey：`subjectTypeCode={subjectTypeCode}&subjectExternalId={subjectExternalId}` |
| `POST /api/perm/abstract-user/full-sync` | 全量主体校准 | scopeKey：`subjectTypeCode={subjectTypeCode}` |
| `POST /api/perm/abstract-role/sync` | 角色 UPSERT/DISABLE/DELETE | businessKey：`roleTypeCode={roleTypeCode}&roleExternalId={roleExternalId}` |
| `POST /api/perm/abstract-role/full-sync` | 全量角色校准 | scopeKey：`roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |
| `POST /api/perm/user-role/sync` | 成员关系 BIND/UNBIND | businessKey：`subjectTypeCode={subjectTypeCode}&subjectExternalId={subjectExternalId}&roleTypeCode={roleTypeCode}&roleExternalId={roleExternalId}&relationKey={relationKey}` |
| `POST /api/perm/user-role/full-sync` | 全量成员关系校准 | scopeKey：`sourceType={sourceType}&roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |

约束：

- 成员关系 sync/full-sync 仅接受调用方自有 `sourceType` 与 `roleTypeCode` 组合（服务身份 + 类型白名单校验）；`SYS_USER_ORG`/`ORG`/`POSITION` 为 AccessMesh 内部保留键，20045 拒绝。功能角色分配走正式用户角色管理接口和 `ROLE:MANAGE` 门禁。
- `relationKey` 为成员关系的关联角色业务键。写入 `businessKey` 时必须按 §6.2.2.4 编码。permission-center 按调用方声明的角色类型 + 外部 ID 解析关联 `abstract_role.id`，写入 `user_role.relation_id`。`relation_id` 表示关联角色 ID，不对外暴露为 API 入参。
- 单次 sync 接口的 `operation` 使用混合严格语义：禁用为 `DISABLE`，删除为 `DELETE`，成员移除为 `UNBIND`。
- full-sync 接口均为单请求全量校准接口，必须携带强制 scope，只在 scope 内补齐缺失并清理多余同步事实。
- 所有 sync/full-sync 接口的调度分类以 §6.2.2.2 为准；错误响应返回 `RETRYABLE`、`DEPENDENCY_MISSING`、`NON_RETRYABLE`、`SECURITY_DENIED`，旧版本 no-op 使用成功响应并返回 `STALE_VERSION`。

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

非资源实体 full-sync 规则：

- `abstract-user/full-sync` 对比 `sync_metadata(entityKind=ABSTRACT_USER, sourceService, scopeKey)`。
- `abstract-role/full-sync` 对比 `sync_metadata(entityKind=ABSTRACT_ROLE, sourceService, scopeKey)`；item 的父角色默认与 scope 中的 `roleTypeCode` 同类型，如需跨类型必须显式传 `parentRoleTypeCode`。
- `user-role/full-sync` 对比 `sync_metadata(entityKind=USER_ROLE, sourceService, scopeKey)`；请求缺失的旧关系按 `UNBOUND` 处理，不删除正式功能角色分配。
- 所有 full-sync 接口只清理命中 `sync_metadata` 的同步事实，不扫描删除人工维护或正式管理 API 创建的事实。

#### 6.2.2.5 服务间认证（sync/full-sync 专用）

外部业务服务通过服务身份凭证建立 SERVICE 上下文后调用 sync/full-sync 接口（无前端会话与 Gateway 鉴权）。访问控制语义见 `access-service-architecture.md` §6.2 安全策略矩阵：

| 入口 | 调用方要求 | 关键约束 | 实现 |
|---|---|---|---|
| `/api/perm/**/sync`、`/full-sync` | 已验证服务身份 | `sourceService` 必须等于已验证服务身份（凭证通过后绑定的 X-Service-Code，`SyncAuthVerifier` 从上下文比对） | SERVICE 上下文；不匹配 → SECURITY_DENIED |

- 服务身份由 `access-service` 统一校验（内部凭证验证通过后绑定 `X-Service-Code` 为凭证持有者声明的服务身份，防无凭证外部伪造），调用方**不得**自行声明或伪造服务身份。
- 内部同步子系统（`sys_sync_task` 调度、`SyncTaskFeignClient`/`FeignInternalSyncInterceptor`、`X-Internal-Secret` 调度链路）已随 T-ACCESS-005 删除；管理事实由 `access.application` 同事务维护本地投影，不再经 sync 接口进入。
- 仅当配置了服务凭证时服务身份校验才生效；未配置的部署不会启动失败，但对应接口按矩阵 fail-closed。

#### 6.2.2.6 FullSyncDetail 结构

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
| `businessKey` | string | 业务键（按 §6.2.2.4 编码） |
| `applied` | boolean | 是否成功落库 |
| `stale` | boolean | 是否因版本较旧被钝化 |
| `retryClass` | string\|null | 失败/钝化分类（与 §6.2.2.2 同枚举） |
| `reason` | string\|null | 可读原因 |

顶层字段联动规则：

- 全部成功：`accepted=true, applied=true, retryClass=null`，`detail.failedCount=0`。
- 部分失败：`accepted=true, applied=false, retryClass=RETRYABLE, reason=FULL_SYNC_PARTIAL_FAILURE`，明细在 `detail.itemResults` 中按 item 给出原因。
- 全局拒绝（身份/scope 不合法）：`accepted=false, applied=false, retryClass∈{SECURITY_DENIED,NON_RETRYABLE}`，并在 `detail.failedCount` 中记拒绝条数。

> sync 接口固定 `data.detail = null`，调度器据顶层 retryClass/applied/stale 判定 outcome；full-sync 不引入额外顶层 DTO。

#### 6.2.2.4 业务键与 scopeKey 规范

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

### 6.3 服务接口全量同步

`POST /api/perm/service-config/sync`

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

#### 6.3.1 同步类型白名单配置（service-config/save 的 extra.syncTypes）

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
- **资源维度已退役（T-PERM-052，2026-09-05 定案）**：原 `resourceTypeCodes` 字段删除——resource-entity 同步通道改由**类型级所有权声明**判定（`type-definition` 的 `extra.managedMode=SYNC` + `extra.syncSourceService`，见 §6.2.2），服务侧不再声明资源类型；保存含 `resourceTypeCodes` 字段直接拒绝（防旧结构配置误导）。
- **三条链路的最小映射**：主体同步校验 `subjectTypeCode`；角色同步校验 `roleTypeCode`；用户角色同步校验写入事实使用的 `subjectTypeCode` + `roleTypeCode` + `sourceType`（`relationKey` 角色类型为引用，不要求声明）；资源同步走类型级所有权门禁（不经本白名单）。
- **GROUP_ROLE 例外（T-PERM-043）**：角色同步在白名单之外恒拒 `GROUP_ROLE`（`ROLE_TYPE_MISMATCH(20022)`，先于白名单判定）——即使服务声明了 `roleTypeCodes: ["GROUP_ROLE"]` 也不生效。
- **服务状态**：`status != 1`（禁用）/未注册/已软删时该服务全部 sync/full-sync 拒绝——subject/role/user_role 三通道由本白名单校验，resource-entity 通道由类型所有权门禁校验（来源服务注册+启用，§6.2.2；评审批次补强后两轨语义一致）。
- **校验顺序**：使用经过认证的服务身份（凭证通过后绑定的 `X-Service-Code`）查询配置，不信任请求体；未通过统一返回 `SECURITY_DENIED`（`SERVICE_TYPE_NOT_ALLOWED`），内部日志记录真实原因，不向调用方返回白名单明细。
- **保留键纵深**：即使白名单错误声明 `LOCAL_USER`/`ORG`/`POSITION`/`SYS_USER_ORG` 等 AccessMesh 保留键，入口仍以 20045 拒绝。
- **结构校验**：`service-config/save` 保存时校验 `syncTypes` 必须为对象、内部仅允许 subjectTypeCodes/roleTypeCodes/sourceTypes 三个字段（未知字段拒绝，含已退役的 `resourceTypeCodes`）、各分类（如存在）必须为非空白字符串数组；结构非法保存失败（20044）。缺失配置在运行时按无权限处理（fail-closed），不视为允许全部。
- **上线准备（fail-closed 发布顺序）**：先为各同步服务通过 `service-config/save` 补齐 `syncTypes` 声明（并确认 `status=1`），再部署严格校验代码；未声明类型的存量服务在严格校验上线后同步全部拒绝，属预期行为。

### 6.4 角色权限配置查询（list）

> **写入入口（2026-08-27 端点退役收口）**：授权页面的全部写操作统一走 §6.5.1 `apply-grant-plan`（记录级 creates/updates/removes + 单事务原子 + 受影响行数断言；**砍 expectedRevision CAS / grant_revision 列 / 幂等表 / 20037/20039 / clientRequestId / @Idempotent**）。旧写入口 `save`/`revoke`/`children`/`add-child`/`remove-child` **已从 Controller 删除（无映射 404，无存量调用方，不留兼容层）**；`update-child`/`children-save`/`rebuild` 从未实现。以下 §6.4/§6.5 规则已并入 §6.5.1 统一预检。

#### 查询角色权限配置

`POST /api/perm/role-resource-permission/list`

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
- **`resourceTypeCode`（可选；已落地 T-PERM-040，单类型矩阵上下文定稿）**：按资源类型过滤主权限（`depend_on IS NULL` 且 `resource_type` 匹配当前类型）——过滤下沉专用 Mapper 查询（`selectValidMainByRoleIdAndResourceType`，避免 N+1），子权限按 `depend_on` 批量挂父取回（`selectValidByRoleIdAndDependIds`，双重约束在 SQL 层满足）。**授权矩阵调用时必填**（矩阵一次只呈现一个类型，见 permission-grant.md §2.2）；null/缺省/空白串 = 不过滤（空白串视同缺省；兼容既有调用方，如管理域菜单授权按角色取全量）；**指定但类型不存在 = 空列表**（fail-closed 不回退全量，2026-08-31 定案）。
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
| resourceName | string\|null | 资源名（展示用；scopeMode=ALL 时为 null，对齐 §6.6 示例） |
| operationCode | string | 操作码；MANUAL 授权一行只对应一个操作定义，不返回组合位记录 |
| canGrant | boolean | 是否可再授予 |
| conditionCode | string\|null | 条件码；无条件为 null |
| scopeMode | string | INSTANCE / ALL |
| dependOn | number\|null | 父权限 id；主权限为 null |
| grantSource | string | MANUAL（手动授权）/ AUTO_DEP（依赖自动补全产生）；INHERITED 为查询时克隆、不落库不返回 |
| grantedBits | string | 操作位，**十进制字符串**；MANUAL 记录等于单个操作定义的 `binaryBit`（2 的幂），避免 JSON number 精度丢失，前端用 BigInt 解析 |
| createdAt | string | 创建时间，ISO-8601 无时区（如 `2026-04-20T10:30:00`，对齐 §6.8 示例） |
| childCount | number | 直接子权限数（depend_on = 本 id，不含孙代）；list 时按 depend_on 分组 COUNT 一次返回 |

- 门禁：目标抽象角色 ROLE:VIEW（`PermissionGrantAppServiceImpl.listPermissions` 入口校验，失败返回空列表）。




### 6.5 子权限/范围权限（语义，单入口收敛）

子权限通过 `role_resource_permission.depend_on` 表达。`depend_on` 指向一条主权限记录的 `id`，表示当前授权依赖该主权限存在。典型场景：角色拥有"销售报表 DATA_READ"主权限，主权限下挂"上海数据 DATA_READ"和"杭州数据 DATA_READ"作为范围权限。

**单入口收敛（2026-08-02，2026-08-27 端点退役收口）**：授权页面不再调用子权限独立接口，创建/编辑/删除全部通过 §6.5.1 `apply-grant-plan` 的记录级 plan 表达。`add-child`/`remove-child` 等旧端点**已删除（无映射 404）**；`update-child`/`children-save` 从未实现：

- 新增子权限 = `creates[]` 项带 `parentPermissionId`（**属性系统不变量：`conditionCode` 必须为 null、`canGrant` 必须为 false，违反 -> 20043，2026-08-08 复审产品确认**）
- 编辑子权限 = **不支持**（子权限不承载条件/再授予，`updates[]` 目标为子权限 -> 20043；见 §6.5.1 校验规则）
- 删除子权限 = `removes[]`（子权限 id）
- 删除主权限 = `removes[]`（主权限 id，**级联软删其全部子权限——预期行为**）；跨键替换（范围/资源/操作变化）= removes 旧 + creates 新（同事务原子），**子权限不迁移**（产品语义），新主权限的子权限在 creates 中显式配置

语义规则：

- 子权限继承父权限的 `abstract_role_id`，调用方不需要再次传角色。
- 子权限的 `depend_on = parentPermissionId`，只支持一层，不允许子权限继续挂子权限。
- 子权限资源类型必须符合 `domain_config(config_type='SUB_PERM')` 中对当前业务域的配置。**fail-closed**：配置不存在 / `extra` 为空 / `extra` 格式错误 → 统一拒绝（20011，错误信息区分"配置缺失/配置为空/配置格式错误"）；`extra="*"` = **显式**允许任意子资源类型（通配必须显式声明，不得靠"未配置"隐式放行；顶层 `"*"` 经真实 jsonb 链路暂不可达——已知缺陷登记见 §6.5.2 判定步骤 1，ALLOW_ALL 以嵌套通配配置）。父域直接按父权限记录自身 `resource_type` 批量反查类型码与所属域，INSTANCE/ALL 统一，不依赖 `resource_entity_id`。该规则由 `PermissionGrantPlanDomainService.prevalidate` 强制；旧 `add-child` 已删除（2026-08-27 端点退役，无映射 404），不作为授权页面入口亦无从调用。**授权页面的类型选择过滤走只读契约 `sub-perm-allowed-types`（§6.5.2，判定口径与本条一致）**。
- `scopeMode=ALL` 表示该授权覆盖 `resourceTypeCode` 下全部资源；此时请求不传 `resourceCode/codeType`，运行时响应也通过 `scopeMode=ALL` 明确表达全量范围。
- 删除主权限时，系统必须级联软删 `depend_on` 指向该主权限的所有子权限。
- 子权限写入、删除都必须记录 `permission_change_log`，并通过 Redis pub/sub 广播 `PermInvalidateEvent` 失效父角色缓存（afterCommit）。
- 运行时不要通过 `auth/check` 承载范围集合：`auth/check` 只做主权限布尔判定；业务需要范围权限集合时调用 `POST /api/perm/auth/query-scopes`。

### 6.5.1 聚合授权提交 apply-grant-plan（T-PERM-034 已落地 2026-08-30，收敛为唯一写入口，收窄）

**（2026-08-02）收窄**：砍 expectedRevision CAS + grant_revision 列 + 幂等表 grant_plan_idempotency + 20037/20039 + hash canonical + replayed/currentRevision（Stripe 式重幂等对低频内部管理页错配）；单事务原子 + 受影响行数断言；clientRequestId/@Idempotent/幂等表全删（幂等中间件实现取消（未登记看板））；schema 文件删除，本节为唯一权威契约（补结构约束）。

`POST /api/perm/role-resource-permission/apply-grant-plan`

**请求**（统一响应壳见 §0）：

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
- `recordKey` 跨字段约束（**仅结构约束，复审拆分：属性不变量见 §6.5.1 校验规则**）：`operationCode` 必填且只能定位一个有效操作定义；`scopeMode=INSTANCE` -> `resourceCode`/`codeType` 必填（minLength 1）；`scopeMode=ALL` -> `resourceCode`/`codeType` 为 null。**属性约束按主/子记录分类（见 §6.5.1）**：主权限 `conditionCode != null` -> `canGrant=false`（🔧 T-PERM-041，20041）且条件必须启用（20042）；子权限 `conditionCode` 必须 null、`canGrant` 必须 false（20043），子权限 update 一律 20043。
- `plan.updates[]`：`id` 必填 + `canGrant`（三态：null=不改/true/false）+ `conditionCode`（三态：缺省或 null=不改/`""`=清除/非空=覆盖；**仅精确空串表示清除，全空白串按非空处理——条件码不存在则 20006，不静默清除**）；与 `removes` 互斥（同 id 不得同时出现在两段）。
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
  - 逐项 `checkCanGrant`（资源/范围/操作结构键；操作者可转授记录按 T-PERM-041 必为无条件，因此 conditionCode 不参与授权传递身份）；不满足 -> **20040** `GRANT_CANNOT_DELEGATE`；SUB_PERM 约束（fail-closed，父域 resource_type 直查，§6.5）；`scopeMode`/资源兼容。
- **updates**：目标 id 必须存在且属于目标角色 -> 否则 **20036**；AUTO_DEP -> **20034**；**目标为子权限（depend_on 非空）-> 20043**（子权限属性为系统不变量，不承载条件/再授予，仅可删除）；与 removes 互斥；**条件不可转授按最终状态判定（creates/updates 共用不变量，违反 -> 20041，见上）**；`canGrant` 或 `conditionCode` 有变更 -> `canGrantPermission`；conditionCode/canGrant 直接更新原记录，不创建新记录；**实际影响行数 ≠ 预期 -> 20036 整体回滚**；update 至少改 canGrant/conditionCode，拒绝重复 ID 与 update/remove 交叉 ID。
- **removes**：主权限 id -> 级联删子（预期行为）；子权限 id -> 单条删；id 不存在/已软删/非目标角色 -> **20036**；AUTO_DEP -> **20034**；**实际影响行数 ≠ 预期（并发删除/修改）-> 20036 整体回滚**。
- **无幂等中间件（定案）**：**砍 clientRequestId / @Idempotent / 幂等表**（幂等中间件实现取消（未登记看板））；前端 saving 期间按钮 disabled 防重复点击，超时提示刷新确认；后端靠单事务原子 + uk 约束 + 受影响行数断言保证不重复/不部分成功。执行顺序：① 认证 + ROLE:MANAGE 门禁（hasPermission 显式判断 false 抛 SecurityException）-> ② prevalidateGrantPlan -> ③ 单事务执行 + 受影响行数断言。
- **砍**：`expectedRevision` CAS / `grant_revision` 列 / 20037 `VERSION_CONFLICT` / 20039 `IDEMPOTENCY_OPERATOR_MISMATCH` / 幂等表 `grant_plan_idempotency` / hash canonical / replayed/currentRevision / 20037 重试 machinery / clientRequestId / @Idempotent 中间件（幂等中间件实现取消（未登记看板））。
- 写入 `permission_change_log` + 一次 `PermInvalidateEvent`（afterCommit）。

**错误码枚举（apply-grant-plan 链路，精简）**：20001 ROLE_NOT_FOUND / 20003 ROLE_DISABLED / 20004 RESOURCE_NOT_FOUND / 20005 OPERATION_NOT_FOUND / 20006 CONDITION_NOT_FOUND / 20007 RESOURCE_TYPE_NOT_FOUND / 20008 RESOURCE_TYPE_OPERATION_MISMATCH / 20009 PARENT_PERMISSION_NOT_FOUND / 20010 PARENT_PERMISSION_NOT_TOP_LEVEL / 20011 SUB_PERMISSION_RESOURCE_TYPE_NOT_ALLOWED / 20012 RESOURCE_CODE_REQUIRED（INSTANCE 缺 resourceCode 服务端兜底）/ **20033 DIRECT_PERMISSION_CONFLICT（同角色+资源/范围+操作+父权限的 MANUAL 直接授权已存在）** / 20034 AUTO_DEP_READONLY / 20036 PERMISSION_NOT_FOUND / 20040 GRANT_CANNOT_DELEGATE / **20041 CONDITIONAL_PERMISSION_CANNOT_DELEGATE（条件权限不可转授）** / **20042 CONDITION_DISABLED（🔧 2026-08-08 产品确认：新写入/变更的主权限 conditionCode 必须为启用状态）** / **20043 SUB_PERMISSION_ATTRIBUTE_NOT_ALLOWED（🔧 2026-08-08 复审产品确认：子权限不承载条件/再授予——create 非 null/false 或 update 目标为子权限均拒绝，系统不变量）**；**砍 20037/20039**；20013/20014/20035 随旧子权限接口移除；20038 随同键重建语义废弃。

### 6.5.2 子权限类型只读查询（sub-perm-allowed-types，v3.1，已随 T-PERM-034 落地 2026-08-30，评审复审修订）

`POST /api/perm/role-resource-permission/sub-perm-allowed-types`

授权弹窗子权限配置器专用**只读**契约：按**父资源类型**返回该父类型允许挂载的子资源类型，判定口径与 §6.5 SUB_PERM fail-closed 校验一致（父域按父资源类型反查类型码与所属域，不依赖 `resource_entity_id`）。本接口只做配置查询，不触发权限缓存失效与变更日志。

请求：

```json
{
  "domainCode": null,
  "roleTypeCode": "BASIC_ROLE",
  "roleExternalId": "role_admin",
  "parentResourceTypeCode": "MENU"
}
```

- `domainCode`（可选，授权页恒传 null，同 §6.4 list 的 domainCode 口径）、`roleTypeCode`、`roleExternalId`：**目标角色业务键，门禁定位用**（`resolveRoleId` 解析失败 -> 20001，见下方门禁）。
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

- 门禁：`resolveRoleId(domainCode, roleTypeCode, roleExternalId)` 解析**失败 -> 20001** `ROLE_NOT_FOUND`（明确抛出，不返回空结果——本接口无"空列表即自然结果"语义，避免前端把角色不存在误判为 `ALLOW_NONE`）；`hasPermission(ROLE, roleId, VIEW)` 为 false 时**抛 `SecurityException` 走统一访问拒绝**（与 §6.5.1 写入口同模式；**区别于 §6.4 list 的"失败返回空列表"**——本接口不采用空结果掩盖鉴权失败，以免把"无权查看"误判为"SUB_PERM 未配置"）。
- 错误码：不新增；`parentResourceTypeCode` 无效 -> 20007，角色定位失败 -> 20001，无 ROLE:VIEW -> 统一访问拒绝（全局异常处理）。

**实现绑定（复审补充）**：SUB_PERM 解析的唯一公开入口为 `PermissionGrantPlanDomainService.resolveSubPermissionPolicy(tenantId, parentResourceTypeCode)`，返回不可变策略对象 `SubPermissionPolicy { mode, reason, allowedTypeCodes, allows(childTypeCode) }`（`assertSubPermissionAllowed` 抽取，见 implementation §4）；调用链：Controller → `PermissionGrantAppService`（请求/响应 DTO 映射）→ `planDomainService.resolveSubPermissionPolicy`（读接口直接序列化策略结果）；`prevalidate` 内部复用同一解析器（`policy.allows`）。**禁止在 AppService/Controller 另行编写 SUB_PERM 判断**，读写必须同源。

> **落地状态（T-PERM-034 收口，2026-08-30）**：端点/策略对象/判定优先级已按上文逐条实现（`resolveSubPermissionPolicy` 唯一公开入口，读接口经 ROLE:VIEW 实例门禁直接序列化，写链路复用 `allows()`）；单测覆盖判定表全分支（CONFIG_MISSING/EMPTY/INVALID×2/PARENT_NOT_CONFIGURED/嵌套通配/并集去重/CHILD_TYPES_EMPTY/20007）与门禁（20001/SecurityException）。

### 6.6 通用资源权限查询

> **原登记失效（2026-08-30 全局操作概念退役，T-PERM-049）**：曾登记的「判定轨合并全局操作位 vs 展示投影轨仅取类型专属」分歧随概念退役自动消解——两侧现均为类型专属操作，无投影轨缺口。

`POST /api/perm/auth/query-resources`

用于业务服务查询某个主体在指定资源类型和操作下的有效权限集合。典型场景包括管理域查询用户能管理哪些组织、哪些角色、哪些菜单。前提是这些业务对象已经作为 `resource_entity` 同步或创建到权限中心。

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

> **DTO 公共化与内部 id 裁剪（T-API-002 收口，2026-09-06）**：`QueryResourcesReq/Resp` 已迁入 perm-common（SDK `PermissionFeignClient.queryResources` 直连）；条目的 `matchedRoleIds` / `matchedPermissionIds`（role / role_resource_permission 内部行 id）已裁剪（core-flows §15 口径），线格式字段快照由 SDK 契约测试钉死。
> **子权限行不进清单面（T-PERM-058，2026-09-10 落地）**：depend_on 子权限行不出现在 `items[]`——其授权只在 §6.7 query-scopes 主资源上下文内生效/可见，独立 INSTANCE 条目呈现会误导调用方（子行实例 ≠ 独立可访问）。Gateway 接口快照（§6.2.1）同口径：API 类型子权限行不下发（接口鉴权无主资源上下文概念）。

管理域查询示例：

| 查询目标   | 建模方式                                                                           | 查询参数                                                                  |
| ---------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| 可管理组织 | 组织同步为管理资源，例如 `resourceTypeCode=ORG`、`resourceCode={sys_org.id}` | `resourceTypeCodes=["ORG"]`、`operationCodes=["UPDATE"]` 或其他管理操作 |
| 可管理用户 | 用户同步为管理资源，例如 `resourceTypeCode=USER`、`resourceCode={sys_user.id}` | `resourceTypeCodes=["USER"]`、`operationCodes=["UPDATE","DELETE","ENABLE","RESET_PASSWORD"]` |
| 可管理角色 | 角色同步为资源，例如 `resourceTypeCode=ROLE`、`resourceCode=role:{roleExternalId}` | `resourceTypeCodes=["ROLE"]`、`operationCodes=["MANAGE"]` 或 `["ASSIGN"]` |
| 可见菜单   | 菜单同步为资源，例如 `resourceTypeCode=MENU`、`resourceCode=menu:{menuCode}`       | `resourceTypeCodes=["MENU"]`、`operationCodes=["VIEW"]`（树由调用方基于平面列表自建） |

规则：

- 查询接口只返回权限事实和资源业务键，不查询 admin 域的组织、角色、菜单业务表。
- 调用方拿到 `resourceCode` 后，由业务服务映射成本服务内的组织树、角色列表或菜单树。
- AccessMesh 管理端中，`USER`/`ORG` 的 `resourceCode` 固定使用 admin 域本地主键字符串（T-ACCESS-018 收敛后类型码），避免与组织编码、用户名等可变业务字段混用。
- AccessMesh 管理端中，USER/ORG 的 resourceCode 固定使用 admin 域本地主键字符串。所有接口均支持业务键参数，permission-center 内部通过 TypeResolutionService 解析为内部 ID。调用方不应存储 permission-center 的内部主键 ID。
- `scopeMode=ALL` 的条目表示该 `resourceTypeCode` 下全量资源权限，此时 `resourceCode`、`resourceName`、`codeType` 均为 null；不展开全量范围为逐条资源实例。实例级条目（`scopeMode=INSTANCE`）按 `resourceCode + codeType` 精确表示。
- 多个角色命中同一资源时，按 `resourceTypeCode + resourceCode + codeType + scopeMode` 去重，并合并 `operations`、`grantSources`。
- 条件、冲突规则、停用状态、角色继承、资源继承必须与 `auth/check` 使用同一套计算逻辑。
- `treeMode` 树模式响应已从契约移除（2026-08-27 决策，无真实消费方）：接口固定返回平面列表，树形展示由调用方基于平面列表自建；资源父子关系可经 `includeChildren` 展开获取。如未来需要服务端树响应，登记于 v3.5.1-evolution 演进方向重新评估。
- 该接口面向运行时 SDK 查询；若要解释授权来源和变更历史，使用 `permission-view/*`。

### 6.7 范围权限运行时查询

`POST /api/perm/auth/query-scopes`

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
- **管理端排查复用（T-PERM-033 设计定案，2026-08-29）**：权限排查页 Tab2 复用本接口，但本接口**维持运行时语义、不加排查门禁**（业务服务按主体查询不要求调用者持排查码；未来业务方合法的非自查查询不应被拒）。排查页仅靠页面级 UI 门（`USER:VIEW` 或 `ROLE:VIEW` 任一）控制入口；API 层为租户内只读暴露面，按演进需要再评估收紧。**排查页暂停注记（2026-09-06 用户决策）**：权限排查页整体暂停待重做（T-FE-043 登记重做事项），T-API-002 仅做编译一致的最小改造（内部 id 字段类型清除、信息条「主权限ID」栏随字段删除），不投入页面改造。
- **DTO 公共化与内部 id 裁剪（T-API-002 收口，2026-09-06）**：`QueryScopesReq/Resp` 已迁入 perm-common（SDK `PermissionFeignClient.queryScopes` 直连）；`parentPermissionIds` 与 `scopeGroups[]` 的 `matchedRoleIds` / `matchedPermissionIds` / `dependOnPermissionIds`（均为 role / role_resource_permission 内部行 id）已裁剪（core-flows §15 口径），线格式字段快照由 SDK 契约测试钉死。
- 已删除字段：`allowed`（合并进 `scopeMode`）、`mergeMode`（分类模型下每格独立，不再需要 UNION 标记）、顶层 `items[]`/`ScopeEntry`（改为 `scopeGroups[].items[]`）、`parentPermissionIds`、`scopeGroups[]` 的 `matchedRoleIds`/`matchedPermissionIds`/`dependOnPermissionIds`（T-API-002）。`permissionVersion` 字段已于 T-PERM-018（缓存下沉）移除。

### 6.8 权限排查视图与近期变更

权限排查视图用于回答“用户或角色为什么当前有/没有某权限，以及最近有哪些变更可能影响了权限”。该能力不追求还原任意历史时刻的精确有效权限快照，首期采用“当前权限事实 + 最近影响事件”的轻量模型。

#### 分页筛选查询当前有效权限

`POST /api/perm/permission-view/effective-permissions`

用户视角请求：

```json
{
  "targetType": "USER",
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001",
  "domainCode": "example",
  "resourceTypeCodes": ["REPORT"],
  "operationCodes": ["DATA_READ", "DATA_EDIT"],
  "resourceKeyword": "销售",
  "sourceRoleExternalId": null,
  "includeScopes": false,
  "includeApiResources": false,
  "includeSourceRoles": true,
  "sourceRoleLimit": 3,
  "pageNum": 1,
  "pageSize": 50
}
```

角色视角请求：

```json
{
  "targetType": "ROLE",
  "domainCode": "example",
  "roleTypeCode": "BASIC_ROLE",
  "roleExternalId": "role_report_viewer",
  "resourceTypeCodes": ["REPORT"],
  "operationCodes": ["DATA_READ"],
  "includeScopes": false,
  "includeApiResources": false,
  "pageNum": 1,
  "pageSize": 50
}
```

响应示例：

```json
{
  "targetType": "USER",
  "items": [
    {
      "resourceTypeCode": "REPORT",
      "resourceCode": "report:sales",
      "resourceName": "销售报表",
      "codeType": "default",
      "operationCodes": ["DATA_READ"],
      "scopeMode": "INSTANCE",
      "sourceRoles": [
        {
          "roleTypeCode": "BASIC_ROLE",
          "roleExternalId": "role_report_viewer",
          "roleName": "报表查看员",
          "via": []
        }
      ],
      "sourceRoleCount": 1,
      "sourceRolesTruncated": false,
      "matchedPermissionIds": [200]
    },
    {
      "resourceTypeCode": "REPORT",
      "resourceCode": null,
      "resourceName": null,
      "codeType": null,
      "operationCodes": ["DATA_READ"],
      "scopeMode": "ALL",
      "sourceRoles": [
        {
          "roleTypeCode": "BASIC_ROLE",
          "roleExternalId": "role_admin",
          "roleName": "管理员",
          "via": []
        }
      ],
      "sourceRoleCount": 1,
      "sourceRolesTruncated": false,
      "matchedPermissionIds": [201]
    }
  ],
  "total": 2,
  "pageNum": 1,
  "pageSize": 50,
  "hasNext": false
}
```

规则：

- `effective-permissions` 是管理端排查视图，不作为业务服务运行时高频接口；业务运行时继续使用 `auth/query-resources` 和 `auth/query-scopes`。
- 该接口必须分页，禁止默认一次性返回用户或角色的全部有效权限；`pageSize` 必须有服务端上限。
- 查询应支持 `domainCode`、`resourceTypeCodes`、`operationCodes`、`resourceKeyword`、`sourceRoleExternalId` 等筛选条件。
- 默认 `includeScopes=false`，不展开数据范围或子权限；排查数据权限时由调用方显式开启。
- 默认 `includeApiResources=false`，不返回 API 类型资源；排查接口权限时由调用方显式传 `resourceTypeCodes=["API"]` 或开启该字段。
- 用户视角默认只返回来源角色摘要；`sourceRoles` 最多返回 `sourceRoleLimit` 条，同时返回 `sourceRoleCount` 和 `sourceRolesTruncated`。
- 需要查看某条权限的完整来源角色时，应使用 `permission-view/explain` 或按权限键二次查询，不要求列表接口展开全部来源。
- `scopeMode=ALL` 的条目表示该 `resourceTypeCode` 下全量范围权限，此时 `resourceCode`、`resourceName`、`codeType` 均为 null。不展开全量范围为逐条资源实例。实例级条目（`scopeMode=INSTANCE`）按 `resourceCode + codeType` 精确表示。

#### 解释单个权限

`POST /api/perm/permission-view/explain`

用于排查“某用户或角色为什么有/没有某个具体权限”。这是单权限问题的推荐入口，避免通过 `effective-permissions` 拉取全量权限再筛选。

请求：

```json
{
  "targetType": "USER",
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001",
  "domainCode": "example",
  "resourceTypeCode": "REPORT",
  "resourceCode": "report:sales",
  "codeType": "default",
  "operationCode": "DATA_EDIT",
  "scopeMode": "INSTANCE",
  "includeSourceRoles": true,
  "includeRecentChanges": true,
  "recentDays": 30,
  "context": {
    "clientIp": "10.20.30.40"
  }
}
```

响应示例：

```json
{
  "targetType": "USER",
  "allowed": false,
  "reason": "NO_PERMISSION",
  "permission": {
    "domainCode": "example",
    "resourceTypeCode": "REPORT",
    "resourceCode": "report:sales",
    "codeType": "default",
    "operationCode": "DATA_EDIT",
    "scopeMode": "INSTANCE"
  },
  "sourceRoles": [],
  "matchedPermissionIds": [],
  "recentChanges": [
    {
      "changeLogId": 9001,
      "eventType": "ROLE_PERMISSION_CHANGE",
      "changeType": "REMOVE",
      "impactLevel": "POSSIBLE",
      "message": "角色 报表编辑员 删除了销售报表 DATA_EDIT 权限，可能影响该用户",
      "createdAt": "2026-04-20T10:30:00"
    }
  ],
  "evaluationContextSource": "ADMIN_INPUT",
  "evaluatedClientIp": "10.20.30.40",
  "conditionEvaluations": [
    {
      "conditionId": 77,
      "permissionId": 501,
      "roleId": 20,
      "status": "OK",
      "logic": "AND",
      "passed": false,
      "items": [
        { "type": "IP_WHITELIST", "maskedParams": "192.168.*.*/24, 10.20.*.*", "matched": false },
        { "type": "TIME_RANGE", "maskedParams": "09:00:00~18:00:00", "matched": true }
      ]
    }
  ],
  "conflictDrops": [
    {
      "permissionId": 502,
      "roleId": 21,
      "ruleId": 9,
      "firstOperationCode": "VIEW",
      "secondOperationCode": "MANAGE"
    }
  ]
}
```

规则：

- `explain` 只解释一个资源和一个操作，不返回权限列表。
- **门禁（T-PERM-033 设计定案，2026-08-29）**：`explain` 门禁为**被查目标实例 `USER:VIEW` / `ROLE:VIEW`**（查谁就要对谁有 VIEW，与 `effective-permissions` 同款；ROLE 目标未解析时类型级 `ROLE:VIEW` 兜底，USER 目标未解析不检查、返回 `USER_NOT_FOUND`）。不引入独立排查权限码（原预案 `PERMISSION_QUERY:VIEW` 否决：权限码结构为「资源:操作」，`PERMISSION_QUERY` 是操作描述而非资源）。
- 请求侧 `scopeMode` 只允许 `INSTANCE` / `ALL`：`INSTANCE` 表示解释具体实例权限，必须传 `resourceCode/codeType`；`ALL` 表示解释 `resourceTypeCode + operationCode` 下的全量范围权限，不传 `resourceCode/codeType`。
- `allowed/reason` 复用 `auth/check` 的主体、角色、资源、操作、条件、冲突计算逻辑（判定查询与运行时同一引擎语义）。
- 用户视角需要返回命中的来源角色；未命中时返回拒绝原因和相关近期影响事件。
- **条件评估上下文（T-PERM-033）**：请求 `context.clientIp` 为管理员输入的模拟客户端 IP；未提供时回退**当前请求环境**（操作者 IP），响应 `evaluationContextSource` 标注实际来源（`ADMIN_INPUT` / `CURRENT_REQUEST`）。日期/时间类条件按服务进程系统时钟评估（与运行时判定一致，不可模拟）；IP 类条件按上述上下文评估。
- **条件评估明细（T-PERM-033）**：`conditionEvaluations` 覆盖候选命中条目（条件/互斥过滤前）中挂条件的条目，逐项给出类型、脱敏参数摘要与是否满足；`status` 区分 `OK/DISABLED/NOT_FOUND/INVALID`，非 `OK` 恒 fail-close（`passed=false`）。**敏感条件值脱敏**：IP 黑白名单掩码主机段（如 `192.168.1.0/24 → 192.168.*.*\/24`，IPv6/非常规整体 `MASKED`，超过三条以 `…` 截断）；日期/时间范围为非敏感值原样回传。
- **互斥丢弃明细（T-PERM-033）**：`conflictDrops` 列出候选命中中被权限互斥规则丢弃的条目及命中规则（规则ID + 两侧操作码），解释「本可命中但被互斥移除」。角色级互斥（ROLE_MUTEX）不在此明细范围。
- `includeRecentChanges=true` 时，`recentChanges` **按完整权限键过滤**：含 `permission` 键的事件按 6 字段匹配（`resourceTypeCode/operationCode/scopeMode` 精确相等；`domainCode/resourceCode/codeType` 请求侧为 null 时通配），返回与目标权限键相关的事件；USER 目标额外保留该用户的 `USER_ROLE_CHANGE`（角色分配/回收，`impactLevel=DIRECT`），含权限键事件对 USER 目标标 `POSSIBLE`、对 ROLE 目标标 `DIRECT`。默认窗口为 30 天，服务端可限制最大窗口。**现状登记（T-PERM-034 收口后更新，2026-08-30）**：`apply-grant-plan` 已按本规范写入 `items[].permission` 聚合形状（ROLE 目标 recentChanges 端到端可用）；`ROLE_BATCH_DELETE`/`USER_ROLE_CHANGE` 的 items 无 permission 键（按事件类型本就无该键，非缺口）。
- 范围权限排查应使用主资源权限 + `auth/query-scopes` 或后续扩展 `explain` 的 scope 参数，不应让本接口隐式展开全部范围。

#### 查询近期影响事件

`POST /api/perm/permission-view/recent-changes`

```json
{
  "targetType": "USER",
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001",
  "domainCode": "example",
  "since": "2026-03-29T00:00:00",
  "until": "2026-04-29T23:59:59",
  "eventTypes": [
    "USER_ROLE_CHANGE",
    "ROLE_PERMISSION_CHANGE",
    "ROLE_STATUS_CHANGE",
    "RESOURCE_STATUS_CHANGE",
    "CONDITION_CHANGE"
  ],
  "pageNum": 1,
  "pageSize": 20
}
```

响应示例：

```json
{
  "items": [
    {
      "changeLogId": 9001,
      "eventType": "ROLE_PERMISSION_CHANGE",
      "changeType": "REMOVE",
      "impactLevel": "POSSIBLE",
      "message": "角色 报表编辑员 删除了销售报表 DATA_EDIT 权限，可能影响该用户",
      "permission": {
        "domainCode": "example",
        "resourceTypeCode": "REPORT",
        "resourceCode": "report:sales",
        "codeType": "default",
        "operationCode": "DATA_EDIT",
        "scopeMode": "INSTANCE"
      },
      "sourceRole": {
        "roleTypeCode": "BASIC_ROLE",
        "roleExternalId": "role_report_editor",
        "roleName": "报表编辑员"
      },
      "operatorId": 100,
      "operatorName": "admin",
      "changeReason": "权限清理",
      "createdAt": "2026-04-20T10:30:00"
    }
  ],
  "total": 2,
  "pageNum": 1,
  "pageSize": 20,
  "hasNext": false
}
```

规则：

- `recent-changes` 返回的是“可能影响目标权限的变更事件”，不是目标有效权限的精确历史 diff。
- **门禁（T-PERM-033 设计定案）**：被查目标实例 `USER:VIEW` / `ROLE:VIEW`（与 `explain` 同款；ROLE 未解析时类型级兜底，USER 未解析返回空）——从 `SYSTEM_CONFIG:VIEW` 切换。
- 查询对象为用户时，事件来源包括用户角色分配/回收、命中角色的权限增删改、角色启停、资源启停、条件变更、分组角色包含关系变化。
- 查询对象为角色时，只返回该角色自身权限、状态、条件、依赖规则等相关变更。
- 如果同一权限来自多个角色，某个角色删除权限不代表用户一定失去该权限；响应应使用 `impactLevel=POSSIBLE` 或解释性文案表达“可能影响”。
- 需要展示“当前是否仍拥有某个具体权限”时，前端或管理端应优先调用 `permission-view/explain`；需要浏览权限清单时再调用 `effective-permissions`。
- 默认查询最近 30 天；调用方可通过 `since/until` 缩小或扩大窗口，服务端可设置最大窗口限制。

#### diff_snapshot 轻量规范

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
- `recent-changes` 响应中的 `impactLevel` 固定枚举：`DIRECT` 表示直接命中查询对象，`POSSIBLE` 表示通过角色、资源、条件、分组等间接关系可能影响查询对象。
- 权限项使用稳定业务键：`domainCode + resourceTypeCode + resourceCode + codeType + operationCode + scopeMode`。
- 用户或角色来源使用稳定业务键，不要求在 `diff_snapshot` 中暴露内部 ID；内部 ID 可保留在 `old_snapshot/new_snapshot/entity_id` 中用于审计追溯。
- `old_snapshot/new_snapshot` 继续保存原始变更前后快照；`diff_snapshot` 只保存排查展示需要的摘要。
- **写侧落地（T-PERM-034 收口，2026-08-30——T-PERM-033 登记的读侧依赖解锁）**：`apply-grant-plan` 按本节聚合形状写一条 `ROLE_PERMISSION_CHANGE` 日志——`items[]{changeType, permission(6 字段业务键), role 摘要}` 由 prevalidate 期业务键快照装配（creates 取请求键、updates/removes 反查资源实体+类型+按位操作，removes 行软删后不可回查故预检期快照；removes 的悬挂引用降级为 null 键字段，删除不被死引用阻塞；随主权限级联软删的子权限（`depend_on` 命中 removes）同记 REMOVE 业务键快照、与显式删除项去重——实际被删除的行均可按业务键检索本次变更），端到端场景（成功恰好一条聚合日志+缓存失效；失败全回滚不触发）由容器特征测试锁定。

### 6.9 资源依赖批量同步

`POST /api/perm/resource-dependency/batch-sync`

```json
{
  "serviceCode": "example-service",
  "maintainSource": "MANIFEST",
  "syncMode": "FULL",
  "items": [
    {
      "sourceResourceTypeCode": "REPORT",
      "sourceResourceCode": "report:sales",
      "sourceCodeType": "default",
      "sourceOperationCodes": ["DATA_READ"],
      "targetResourceTypeCode": "API",
      "targetResourceCode": "api:report:sales:query",
      "targetCodeType": "default",
      "requiredOperationCodes": ["ACCESS"],
      "autoGrant": false,
      "description": "销售报表读取依赖查询接口（自动补全未实现，autoGrant 仅接受 false）"
    }
  ]
}
```

规则：

- `source*` 表示源资源，即被授权后会触发依赖补全的资源，对应 `resource_dependency.resource_entity_id`。
- `target*` 表示被源资源依赖、需要自动补全的目标资源，对应 `resource_dependency.depends_on_resource_entity_id`。
- `maintainSource` 四值白名单：`ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC`（schema 口径，DTO `@Pattern` 拒绝其他值；T-PERM-031 收口，原注释 SERVICE/MANUAL 系漂移）。
- **`autoGrant` 预留未实现（2026-08-27 设计定案）**：自动授权暂缓（T-PERM-035，design-review §11 E4），create / update / batch-sync 全部写入口拒绝 `true`（错误码 **20048** `AUTO_GRANT_NOT_SUPPORTED`），仅接受 `false`/省略；表列默认 `false`。依赖补全当前不生效，规则中的「触发依赖补全」语义为 T-PERM-035 实现后的目标态。
- 授权源资源时，自动补全查询条件必须是 `resource_dependency.resource_entity_id = sourceResourceId`，不能反向使用 `depends_on_resource_entity_id` 查询。
- `sourceOperationCodes` 转为 `source_operation_bits`；为空表示任意源操作触发。`requiredOperationCodes` 转为 `required_operation_bits`，表示目标资源需要自动补全的操作；**条目缺失或全空白该字段拒绝 20044（清单级预检，先于资源解析与 FULL diff——畸形清单零副作用，不因条目资源未解析而绕过）**。任一操作码解析不到拒绝 **20005** OPERATION_NOT_FOUND（fail-closed，不静默丢弃——T-PERM-031 定案；code→id 解析与按 id 二次加载**两段查询均 fail-closed**，间隙并发软删同样 20005，不静默丢位）；空白元素忽略、全空白码列表拒绝 20044（与单条入口统一口径）。`items` 元素级联校验生效（`@Valid + @NotNull`，apply-grant-plan 同款）：条目内 `@NotBlank`/`@Size` 由 400 参数校验通道拒绝，null 元素不进入服务层。
- FULL diff 只清理同一 `ownerServiceCode=serviceCode + maintainSource` 范围内本次缺失的依赖规则，不清理其他服务或其他维护来源的规则；匹配键为「源资源 + 目标资源 + `COALESCE(source_operation_bits, 0)`」三元组（T-PERM-031 修正：同资源对不同触发操作是不同规则，仅按资源对匹配会漏删且 upsert 定位错行）。
- 同一语义依赖仍受 `tenant_id + resource_entity_id + depends_on_resource_entity_id + source_operation_bits` 唯一约束保护，避免不同来源重复创建同一条依赖；业务层等价预查命中返回 **20054** DEPENDENCY_DUPLICATE，并发窗口由该约束兜底转同码。**upsert 命中异源同三元组行（ownerServiceCode/maintainSource 与本次请求不一致）时接管归属**——改写为本次 serviceCode/maintainSource，该行随此后 FULL 清单的 owner 范围清理（uk 为全局三元组唯一，接管避免同语义规则整批 20054 拒绝；管理端 UI 行被接管后即受同步清单管辖，2026-08-30 设计定案）。
- 资源 ID 与操作位在循环外按资源类型批量预解析（消解逐条目解析的 N+1）；资源无法解析的条目静默跳过（同步清单漂移由对账兜底，与单条写入口 20004 fail-closed 不同）——**FULL 模式下跳过条目的同三元组存量行会被连带软删**（清单即终态：资源已不存在的依赖规则为死数据，一并清除；增量模式无删除分支不受影响，2026-08-30 设计定案）。

### 6.10 管理接口补充契约（实现约定）

#### 6.10.1 `remove` 与 `{ "ids": [...] }`

- 动词 `remove` 的请求体统一为 `{ "ids": [ ... ] }`，元素为**权限中心表主键**（`BIGINT`），用于删除已在 `list` / `detail` 响应中暴露过的配置行。
- **适用范围**：`domain-config/remove`、`service-config/remove`、`resource-api-mapping/remove` 以及其它已声明支持批量的 `remove` 接口。
- **与业务键的关系**：`ids` 中的主键**不**用于「首次定位外部主体/角色」；主体与角色在其它接口中仍使用 `subjectTypeCode + subjectExternalId`、`domainCode + roleTypeCode + roleExternalId` 等稳定键。调用方应先通过列表或详情拿到待删行的 `id`，再调用 `remove`。

#### 6.10.2 `user-role/list` 与 `user-role/revoke`

**`POST /api/perm/user-role/list`** — 按主体业务键查询该用户的角色关系：

```json
{
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001"
}
```

可选扩展筛选字段（如 `domainCode`）由实现与前端约定；请求体**不得**使用权限中心内部 `abstract_user.id`。

**`POST /api/perm/user-role/revoke`** — 批量回收，请求体示例：

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

#### 6.10.3 `abstract-role/tree`

**`POST /api/perm/abstract-role/tree`**：

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

- `extra-roles/list|add|remove` 三接口删除（见 §5.2 退役说明）。
- `create`：`roleTypeCode=GROUP_ROLE` 抛 `ROLE_TYPE_MISMATCH(20022)`（首期功能角色仅 BASIC_ROLE）。
- `update`：目标角色现行类型为 GROUP_ROLE 时抛 `ROLE_TYPE_MISMATCH(20022)`（请求体无 `roleTypeCode`，按目标类型判定）。
- `sync`/`full-sync`：`roleTypeCode=GROUP_ROLE` 抛 `ROLE_TYPE_MISMATCH(20022)`（外部同步通道与通用入口同口径拒绝，GROUP_ROLE 生命周期冻结）。
- `move`/`remove` 不拒绝 GROUP_ROLE：保留为存量行的清理通道（move 的同类型校验不排斥组树内部同类型移动与解挂，见 §5.2 角色管理契约要点）。
- `user-role/assign|revoke` 对存量 GROUP_ROLE 行仍可用（运行时读模型冻结：直绑展开、有效角色解析不受本任务影响）。
- GROUP_ROLE 枚举、role_type 种子与读模型（tree/list 过滤值、有效角色树展开）保留且冻结。

#### 6.10.4 `resource-api-mapping/create` 与 `update` 响应

- `create`、`update` 成功后响应 `data` 为**单条**映射对象（与列表项结构一致），至少包含映射主键 `id` 及 `serviceCode`、`httpMethod`、`pathPattern` 等关键字段，便于调用方无需再发 `list` 即可确认结果。
- T-PERM-027：`ApiMappingResp`（`list`/`service-config/apis`/`create`/`update` 共用）另含关联资源业务字段 `resourceCode/resourceName/resourceTypeCode/maintainSource`（资源已软删时为 null）。

#### 6.10.5 `permission-view/explain` 在 `targetType=ROLE` 时的语义

- `targetType=USER`：复用运行时鉴权等价逻辑（与 `auth/check` 一致的主体、角色解析、条件、冲突等）。
- `targetType=ROLE`：**仅**判定该角色在 `role_resource_permission` 上是否**直接**拥有指定 `resourceTypeCode + resourceCode + codeType + operationCode + scopeMode`（含内部 `scope_all`、条件启用、记录停用等角色侧字段）；**不**走用户维度的 `auth/check` 链路，不模拟用户继承的多角色并集。
- 请求体必须显式传 `scopeMode`。`scopeMode=INSTANCE` 时按 `resourceCode + codeType` 精确匹配；`scopeMode=ALL` 时不传 `resourceCode/codeType`，只匹配类型级全量授权。

#### 6.10.6 批量删除与审计日志

- 单次 `remove` 接口无论软删除多少行，**写入一条** `operation_log`（摘要中可含删除数量或 id 列表截断说明）。
- 若该写操作需记 `permission_change_log`，同一事务内**写入一条**记录；`diff_snapshot` 符合 §6.8：`eventType` + `items[]`，可在 `items` 中列出多条 `REMOVE`/`UPDATE` 摘要，**禁止**为每个被删 id 各插入一条 `permission_change_log` 父记录。

## 7. 错误原因建议

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

## 8. 验收标准

- 当 Gateway 使用默认配置回调权限中心时，系统应调用 `POST /api/perm/auth/check-interface` 并得到稳定响应。
- 当外部系统只知道用户 `subjectTypeCode + subjectExternalId`、资源 `resourceTypeCode + resourceCode`、操作 `operationCode` 时，系统应能完成鉴权判定。
- 当管理端查询任何列表接口时，响应 `data` 应始终是对象，且列表数据位于 `data.items`。
- 当调用批量删除接口时，系统应接受 `{ "ids": [...] }` 并执行软删除，不暴露 RESTful Path 参数。
- 当同一路径映射存在于多个租户时，接口级鉴权应只在 `X-Tenant-Id` 对应租户内匹配。
- 当 SDK 调用权限中心时，Feign 返回类型应与服务端 Controller 响应 DTO 完全一致。
- 当调用任意接口时，请求体不得包含 `tenantId`；租户统一从 `X-Tenant-Id` 读取。
- 当管理域查询用户能管理哪些组织、角色、菜单时，应通过 `POST /api/perm/auth/query-resources` 返回资源业务键集合。
- 当 example-service 查询报表范围权限时，应通过 `POST /api/perm/auth/query-scopes` 返回同一主资源下的直接范围权限和子权限并集。

## 9. 实施建议

1. 直接以 `/api/perm/*` 重新实现 permission-center Controller，不保留旧路径兼容。
2. Gateway 默认路径改为 `/api/perm/auth/check-interface`。
3. SDK Feign 改为依赖稳定契约 DTO，返回类型与服务端保持一致。
4. 所有 Request DTO 移除 `tenantId` 字段，服务端从 Header/SecurityContext 获取租户和操作者。
5. 首期 `service-config/sync` 仅实现 FULL 全量同步。
6. `auth/query-resources` 和 `auth/query-scopes` 必须复用 `auth/check` 的角色解析、条件评估、冲突处理、租户过滤和缓存失效逻辑。

## 10. 已确认决策

1. **租户来源**：只使用 `X-Tenant-Id` 和安全上下文，请求体不保留 `tenantId`。
2. **对象定位**：取消通用 `Ref` 对象，使用固定扁平字段和标准业务键。
3. **授权入口**：授权页面写链路收敛为**唯一写入口 `POST /api/perm/role-resource-permission/apply-grant-plan`**（2026-08-02 单入口收敛，收窄：单事务原子 + 受影响行数断言，无 CAS/幂等表/clientRequestId）。`save/revoke/children/add-child/remove-child` **已删除（2026-08-27 端点退役，Controller 无映射 404，不留兼容层；删除/新增/查询语义分别由 apply-grant-plan 的 removes/creates 与 list includeChildren 覆盖）**；`update-child/children-save/rebuild` 从未实现。
4. **接口同步**：首期仅支持 FULL 全量同步。
5. **兼容策略**：项目未上线，不考虑旧接口兼容，直接按新契约实现。
6. **运行时查询**：SDK 除布尔鉴权外，需要提供通用资源查询和范围权限查询；查询结果返回权限事实，不返回业务服务私有数据。
7. **范围权限**：`query-scopes = DIRECT 直接范围权限 ∪ DEPENDENT 子权限范围权限`，并支持 `parentOperationCodes[]` 与 `scopeOperationCodes[]` 多操作查询。
8. **全量范围**：`role_resource_permission` 保留内部 `scope_all` 字段；对外协议使用 `scopeMode=ALL` 显式表示某资源类型下的全量范围权限；空 `items=[]` 不表示全量。
9. **类型模型**：对外 API 使用 `subjectTypeCode/resourceTypeCode/roleTypeCode`，内部存储继续使用 `type_value INT`，通过 `type_definition` 缓存解析；`type_value` 在同一 `tenant_id + type_key` 内全局唯一。
10. **业务域模型**：角色、资源等实体**不内嵌 `bizDomainId` 列**，域分类通过 `domain_config` 表的 `CLASSIFY` 配置实现（按 `resourceTypeCode` 关联，管理查询经 `DomainClassifyService.matchesTypeCode/getClassifiedTypeCodes` 按 ALL / GLOBAL_PLUS / DOMAIN_ONLY 三种模式过滤）；**查询管线不做按域的对象过滤，仅按域分类过滤资源类型**（`queryResources`/`queryScopes` 经 `DomainClassifyService(GLOBAL_PLUS)` 分类过滤）；对外管理接口的 `domainCode` 参数仅做域存在性校验与同步命名空间，不参与角色/资源定位（见 §6.4/§6.10，abstract_role/resource_entity 均无域列）。（ P2-3 修正：原"传域查域+全局，不传只查全局"为旧命名空间模型残留； P2-2 同步"仅分类过滤"措辞）
11. **接口映射**：同一路径允许映射多个接口资源，Gateway 接口鉴权采用 OR 语义，任一映射资源权限通过即允许。
12. **委托授权**：`canGrant=true` 表示可把同一条权限授权给他人，但不得扩大资源、操作或范围；被授权对象候选范围由业务服务控制。
13. **资源依赖方向**：`resource_dependency.resource_entity_id` 是源资源/被授权资源，`depends_on_resource_entity_id` 是被源资源依赖、需要自动补全的目标资源。
14. **同步所有权**：服务接口同步通过 `ownerServiceCode + maintainSource` 限定 FULL diff 删除范围；资源依赖同步通过 `ownerServiceCode + maintainSource + syncKey`（`resource_dependency.sync_key`，非 `resource_entity.sync_key`——后者已删除）。
15. **变更摘要枚举**：`diff_snapshot.eventType`、`items[].changeType` 和 `recent-changes.impactLevel` 使用固定枚举，不使用开放字符串。
16. **子权限类型只读契约（v3.1，D5，2026-08-08 评审复审修订）**：授权页面子权限配置器通过 `POST /api/perm/role-resource-permission/sub-perm-allowed-types`（§6.5.2）按父资源类型获取 SUB_PERM 允许的子资源类型并过滤选择器；请求携带目标角色业务键（domainCode/roleTypeCode/roleExternalId），门禁使用与 §6.4 list **相同的 ROLE:VIEW 权限资源与操作码，但失败响应不同**（list 失败返回空列表，本接口角色定位失败 20001、无 VIEW 抛 SecurityException 走统一访问拒绝）；`mode` 判定（ALLOW_ALL / ALLOW_LIST / ALLOW_NONE + reason 细分）与写校验 `assertSubPermissionAllowed` 完全同口径——覆盖顶层与嵌套 `"*"` 通配、多匹配项并集去重、大小写不敏感，**读写复用同一策略解析函数**；前端不硬编码允许集；本契约不改变任何写语义，不新增错误码（复用 20007/20001）。
17. **子权限属性系统不变量（2026-08-08 复审产品确认）**：子权限不承载条件与再授予是**系统不变量而非 UI 限制**——两种子权限 create 形态（`creates[].children[]` 与 `parentPermissionId` 挂父）的 `conditionCode` 必须为 null、`canGrant` 必须为 false（违反 -> **20043**）；`updates[]` 目标为子权限一律拒绝（20043，仅可删除）；历史异常记录只兼容读取与删除，不允许继续属性编辑；20042 不再描述 child create（子权限带条件 -> 20043 而非 20042）。
18. **引擎显式资源 API 与实例门禁业务编码（T-ACCESS-016 定稿，2026-08-23）**：引擎便捷 API 拆分为 `hasPermissionByCode(...)`/`getDeniedResourceCodes(...)`（对外，业务编码语义）与 `getDeniedEntityIds(...)`/`hasPermissionByEntityId(...)`（仅引擎内部或已完成解析的调用方）；泛型 `<ID>`、`Object resourceId`、`toLongId()` 运行时猜测全部删除，抛异常便捷方法从引擎移除（引擎纯查询不抛 `SecurityException`，异常由调用方显式抛出：admin 域经 `AdminPermissionValidator` 门面、permission 域 AppService if-throw）。`resource_entity(USER).code = subjectId`、`resource_entity(ROLE).code = roleId` 业务编码定稿（§3.4）；资源类型收敛映射、`type_value` 终值与扩展操作 bit 终值以 access-service-architecture §13 资源类型注册表为准（实施 T-PERM-042/T-ACCESS-018）。
