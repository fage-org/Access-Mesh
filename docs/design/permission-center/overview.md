---
doc_type: design
title: Permission Center 概念模型
status: adopted
domain: permission-center
last_reviewed: 2026-08-22   # 2026-08-22 归并收口回写（schema 权威改指 access-service.sql、服务名口径收敛）
---

# Permission Center 概念模型

本文档只描述权限中心的核心模型和关键规则。API 路径、请求体、响应体以 [api-contract.md](api-contract.md) 为准；表字段、索引、约束以 [../schema/access-service.sql](../schema/access-service.sql) 为准（唯一权威 DDL）；端到端调用链路见 [core-flows.md](core-flows.md)；实现细节和类清单见 [implementation.md](implementation.md)。

> **术语（T-ACCESS-012，2026-08-22）**：原独立服务 `permission-center` 已归并为 access-service 的 permission 域。本文及权限中心系列文档中「permission-center / 权限中心」指该 permission 域（同进程同库，经 Gateway 以 `/api/perm/**` 对外），「admin-service / admin」指同服务的管理域；不再存在跨服务同步链路。

## 设计原则

- 权限中心是通用权限事实与鉴权引擎，不读取业务服务私有表。
- 所有数据按 `tenant_id` 隔离，运行时租户来自 `X-Tenant-Id` 或安全上下文，请求体不承载 `tenantId`。
- 所有关联使用逻辑 ID，不使用数据库外键；一致性由应用服务保证。
- 所有表使用软删除，`delete_flag=0` 表示有效数据。
- API 统一使用 `POST + JSON Body`，路径统一在 `/api/perm/*` 命名空间下。
- 运行时接口使用稳定业务键，不要求调用方传权限中心内部主键。

## 核心对象（8 聚合设计）

| 聚合                               | 涉及实体                                                        | 说明                                                                                                                                                                                                                                                      |
| ---------------------------------- | --------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Subject（主体）**                | `abstract_user`, `abstract_role`, `user_role`                   | 抽象用户（人员/服务）和抽象角色（ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE），主体与角色的多对多关系。用户通过 `user_type + external_id` 对外定位。                                                                                                     |
| **Resource（资源）**               | `resource_entity`, `operation_permission`                       | 权限资源（菜单、按钮、接口、报表、数据范围等）建模为 `resource_entity`；操作（VIEW/MANAGE/DATA_READ/DATA_EDIT）建模为 `operation_permission`。                                                                                                            |
| **Grant（授权）**                  | `role_resource_permission`                                      | 角色对资源操作的授权事实（granted 位掩码），支持条件绑定（conditionId）、子权限挂载（dependOn）、全量范围标记（scopeAll）、授权来源追踪（grantSource）。                                                                                                  |
| **ServiceIntegration（服务集成）** | `service_config`, `resource_api_mapping`, `resource_dependency` | 接入服务的注册信息和接口清单；接口与 Gateway 路径的映射储存在 `resource_api_mapping`；资源依赖规则表 `resource_dependency` 表达"授权源资源时自动补全目标资源权限"。API 类型资源由同步自动创建，标记 `ownerServiceCode` 和 `maintainSource=SERVICE_SYNC`。 |
| **DomainConfig（域配置）**         | `biz_domain`, `domain_config`, `type_definition`                | 业务域是管理分区而非子租户；仅用于角色、资源、操作和子权限的分类与后台管理视角隔离；域配置约束域内允许的角色、资源、操作和子权限；类型定义（type_definition）完成 code-to-value 的稳定映射。                                                              |
| **PermissionRule（权限规则）**     | `permission_condition`, `permission_conflict_rule`              | 可复用权限条件（时间范围/IP 白名单/黑名单）和冲突规则（角色互斥/权限互斥），在 PermQueryEngine 查询管线中统一评估。                                                                                                                                       |
| **Audit（审计）**                  | `permission_change_log`, `operation_log`  | 权限变更日志（diff 快照）和操作日志（入口写操作记录）。入口日志由 `@OperationLog` AOP 自动记录；内部动态日志（diff 快照、冲突通知）由 `AuditDomainService` 显式调用。> **2026-06-20 审计 S-001**：`permission_version` 表与机制已决策完全删除（design-review §A'-3 + v3.5 §9.2），缓存失效改由 Redis pub/sub 主动广播 + TTL 兜底，详见 core-flows.md「缓存与一致性」。
| **SystemConfig（系统配置）**       | `system_config`                                                 | 租户级配置（角色唯一性、默认策略等）。                                                                                                                                                                                                                    |

## 分层架构

```
Controller ──► AppService（调度层） ──► DomainService（领域层） ──► Mapper（数据访问）
                                         ├── PermQueryEngine（统一鉴权引擎）
                                         └── AOP（@OperationLog 自动记录入口日志）
```

- **Controller**（22 个）：接收请求、解析 Header 中的 tenant/operator、将业务键（code）转换为内部 ID。
- **AppService**（23 个实现）：调度/编排层，组合多个 DomainService 完成业务流程。每个 Service 按单一职责拆分（如 PermissionCheck/PermissionGrant/PermissionView 等）。
- **DomainService**（11 个接口 + `ResolveContext` + `PermQueryEngine`）：领域逻辑层，封装可复用的业务规则（角色解析、条件评估、冲突过滤、类型解析、域分类、同步元数据、授权传递等）。`PermQueryEngine` 是统一权限查询引擎的唯一入口。
- **Mapper**（18 个）：MyBatis-Flex 数据访问，使用 `Tables` 类引用 TableDef（禁止静态导入 APT 生成的 `*TableDef` 类）。`RolePermEntryMapper` 是工具类（位于 `util` 包），负责 `RoleResourcePermission→RolePermEntry` 的转换。
- **AOP**：`@OperationLog` 注解 + `OperationLogAspect` 切面自动拦截 AppService 写方法并记录入口级操作日志。`OperationLogRuntimeContext` 允许方法体内通过 `markSkip()`/`setSummary()`/`setTargetType()`/`setTargetId()` 覆盖注解值。

## 角色模型

- `ORG`：组织角色，支持树形结构，可参与授权。
- `POSITION`：职位角色，分配给用户时可通过 `user_role.relation_id` 绑定所属组织。
- `PERSONAL`：个人角色，每个用户最多一个，用于用户级特殊授权。
- `GROUP_ROLE`：分组角色，用于组织角色集合，不直接配置权限。首期通过 `extra.basicRoleIds` 简化关联，缓存构建阶段展开。
- `BASIC_ROLE`：基础角色，承载可复用权限配置。

在 AccessMesh 管理端语义中，组织既是业务树节点，也是角色容器。admin 域主维护组织树和 `user-org` 关系；permission 域保存由组织与岗位规则映射出的 ORG/POSITION 角色及最终 `user_role` 权限事实。

默认组织树是 admin 域的用户目录/身份池。permission 域不判断某个组织树是否是默认树，也不直接管理用户生命周期；它只保存 `access.application` 在管理事实写入同一事务内维护的本地投影主体、资源、角色和授权事实（不再有跨服务同步链路；外部业务服务经 `/api/perm/**/sync` 写入自有类型事实）。permission 域的所有接口接受业务键（subjectTypeCode + subjectExternalId / resourceTypeCode + resourceCode / roleTypeCode + roleExternalId），内部通过 TypeResolutionService 解析为内部 ID。外部调用方不应存储或使用 permission 域的内部主键。

用户有效角色由 `SubjectDomainService.resolveEffectiveRoles()` 统一解析（L1 CacheService → L2 Redis → DB），禁止在 Service 中直接查询 `user_role` 表或自己写角色解析逻辑。角色层级用于管理和分组，不默认表示权限继承。

## 资源与操作

- 对外 API 使用 `subjectTypeCode/resourceTypeCode/roleTypeCode` 等稳定字符串编码；内部存储和计算使用 `type_definition.type_value`。
- `type_value` 在同一租户和同一 `type_key` 内全局唯一，不随业务域重复；业务域只影响 `type_code` 解析范围和管理分区。
- `domainCode` 是管理分区的命名空间标识：管理查询经 `DomainClassifyService` 按 ALL / GLOBAL_PLUS / DOMAIN_ONLY 三种模式分类过滤；查询管线不做按域的对象过滤（仅分类过滤资源类型）；角色/资源实体不内嵌域列，`domainCode` 不参与对象定位。（P2-2 同步，旧"传域查域+全局"语义废弃）
- 业务域不承担数据权限载体、运行时鉴权主链或资源归属重构职责；其主要作用是降低后台管理复杂度，让不同业务管理员聚焦各自负责的角色和权限集合。
- 资源通过 `resourceTypeCode + resourceCode + codeType` 定位（`domainCode` 不参与资源定位，仅管理查询域过滤，P2-2 同步）。
- 操作通过 `operationCode` 定位，并必须与资源类型兼容。
- 接口权限也是资源权限，Gateway 使用 `resource_api_mapping` 将请求路径映射到资源操作；同一路径可映射多个资源，接口级鉴权采用任一资源权限通过即允许的 OR 语义。
- 业务服务如果需要查询“用户能管理哪些组织/角色/菜单”，应先把这些对象建模为 `resource_entity`。

AccessMesh 管理端的用户与组织需要使用以下资源建模：

| 管理对象 | 资源建模 | 说明 |
|----------|----------|------|
| 被管理用户 | `resource_entity(resourceTypeCode=USER, resourceCode=sys_user.id)` | 支撑 `USER:{userId}` 的更新、删除、启停、重置密码等实例级校验（T-ACCESS-018 收敛：原 ADMIN_USER 并入 USER）。 |
| 被管理组织/岗位 | `resource_entity(resourceTypeCode=ORG, resourceCode=sys_org.id)` | 支撑 `ORG:{orgId}` 的组织 CRUD、成员管理和可管理组织查询（原 ADMIN_ORG 并入）。 |

注意：`abstract_user` 只表示访问主体，不能替代 `USER` 被管理资源；`abstract_role(ORG/POSITION)` 只表示组织/岗位角色容器，不能替代 `ORG` 被管理资源。所有资源通过 `resourceTypeCode + resourceCode` 或 `roleTypeCode + roleExternalId` 定位，调用方无需感知 permission 域内部主键。

## 资源依赖

资源依赖用于表达“授权一个源资源时，自动补齐它依赖的目标资源权限”。

- `resource_dependency.resource_entity_id` 是源资源，即被授权后触发补全的资源。
- `resource_dependency.depends_on_resource_entity_id` 是被源资源依赖的目标资源，即需要自动补全的资源。
- `source_operation_bits` 限定源资源哪些操作会触发补全；为空表示源资源任意操作都触发。
- `required_operation_bits` 表示目标资源需要补全的操作。
- 批量同步依赖规则时，只能清理同一维护方和维护来源范围内的规则，避免 SDK/清单同步覆盖管理端手工配置。

## 范围权限

范围权限用于表达”用户进入某个主资源后，能操作哪些范围资源”。典型例子是查看销售报表时能读取哪些部门数据，或编辑报表数据时能编辑哪些部门范围。

有效范围权限由两类授权取并集：

```text
effectiveScopes = DIRECT 直接范围权限 ∪ DEPENDENT 子权限范围权限
```

- `DIRECT`：`depend_on IS NULL` 的独立范围资源授权，例如 A 部门主管拥有 `dept:A + DATA_READ`。
- `DEPENDENT`：`depend_on` 指向当前主权限的子权限，只在该主资源上下文内生效，例如用户只在销售报表下额外拥有 `dept:B + DATA_READ`。
- `scope_all=true`：内部存储的一等权限维度，不展开为 N 个具体资源条目；对外协议统一映射为 `scopeMode=ALL`。例如 `DATA_EDIT + DEPT + scopeMode=ALL` 表示可编辑全部部门范围。
  - `SnapshotAssembler`：内部 `scope_all` 条目不展开，对外快照项返回 `scopeMode=ALL`；实例级条目返回 `scopeMode=INSTANCE`。
  - `PermViewAssembler`：按 `resourceType` 分组输出全量范围视图项，对外使用 `scopeMode=ALL`。
  - 空范围结果不代表全量，必须通过 `scopeMode=ALL` 显式表达。

推荐在 example-service 中使用 `report:sales + DATA_READ -> dept + DATA_READ`、`report:sales + DATA_EDIT -> dept + DATA_EDIT` 的同名业务数据动作映射。该映射是推荐范例，不是所有接入系统的强制标准。

## 鉴权与查询入口

所有权限查询统一通过 `PermQueryEngine.query(PermQuery)` 执行。引擎提供两种粒度的 API：

**AppService 层 API（单目标/批量校验）：**

```java
// —— 业务编码轨（对外；USER/ROLE 等业务对象门禁统一使用，T-PERM-042 终态）——
// resource_entity(USER).code = subjectId、resource_entity(ROLE).code = roleId（architecture §12.3）

// 单目标鉴权（code 传 null = 类型级）
boolean allowed = engine.hasPermissionByCode(tenantId, subjectId,
    ResourceTypeCode.ROLE, String.valueOf(roleId), OperationCodeConstants.MANAGE);
// 批量获取拒绝的业务编码集合（引擎纯查询不抛异常，拒绝时调用方显式 throw）
Set<String> denied = engine.getDeniedResourceCodes(tenantId, subjectId,
    ResourceTypeCode.USER, userCodes, OperationCodeConstants.MANAGE);

// —— entityId 轨（仅引擎内部或已完成解析的调用方：资源树、API 映射、资源依赖、权限树等）——
boolean ok = engine.hasPermissionByEntityId(tenantId, subjectId,
    ResourceTypeCode.RESOURCE, resourceEntityId, OperationCodeConstants.MANAGE);
Set<Long> deniedEntityIds = engine.getDeniedEntityIds(tenantId, subjectId,
    ResourceTypeCode.RESOURCE, resourceEntityIds, OperationCodeConstants.DELETE);
```

**复杂查询 API（`PermQuery` 工厂方法 + `engine.query()`）：**

| 工厂方法                      | 模式     | 说明                                            |
| ----------------------------- | -------- | ----------------------------------------------- |
| `PermQuery.forAuthCheck`      | 鉴权校验 | 类型+实例查询，scopeAll 匹配时提前返回          |
| `PermQuery.forInterfaceCheck` | 接口鉴权 | 类型优先 + 实例回退，完整评估，返回所有辅助信息 |
| `PermQuery.forResourceQuery`  | 资源过滤 | 仅实例级查询，不评估条件/冲突                   |
| `PermQuery.forResourceCheck`  | 资源检查 | 全范围+实例，完整评估条件/冲突                  |
| `PermQuery.forValidate`       | 管理校验 | 类型+实例，无评估，最小输出                     |
| `PermQuery.forScopeQuery`     | 范围查询 | 无提前返回，不评估，返回所有辅助信息            |
| `PermQuery.forUserView`       | 用户视图 | 全量角色权限记录，不按位过滤；同时按 `effectiveBits` 生成最终可用操作投影 |

**对外接口：**

- `auth/check`：判断单个资源操作是否允许。
- `auth/batch-check`：批量判断多个资源操作。
- `auth/check-interface`：Gateway 接口级鉴权。
- `auth/query-resources`：查询用户能操作哪些独立资源。
- `auth/query-scopes`：查询用户在某个主资源上下文内能操作哪些范围资源。
- `permission-view/*`：用于管理端解释和审计，不作为业务服务高频运行时依赖。

## 权限排查与变更日志

权限排查能力采用”当前权限事实 + 最近影响事件”的轻量模型，用于解释用户或管理员常见问题，例如”为什么突然缺失某权限”或”为什么突然新增某权限”。

- `permission-view/effective-permissions` 分页筛选展示当前有效权限；用户视角可展示权限来源角色摘要。
- `permission-view/explain` 是单权限排查主入口，用于解释某个具体资源操作当前是否拥有、来源角色、拒绝原因和近期相关变更。
- `permission-view/recent-changes` 展示最近一段时间可能影响目标用户或角色权限的事件。
- `effective-permissions` 默认不展开数据范围、子权限、API 资源和完整来源角色，避免大权限用户一次返回过多数据。
- 入口操作日志由 `@OperationLog` AOP 自动记录（拦截标注该注解的 AppService 写方法）。方法体内可通过 `OperationLogRuntimeContext.markSkip()` / `setSummary()` / `setTargetType()` / `setTargetId()` 覆盖注解值。内部动态日志（diff 快照、冲突通知）由 `AuditDomainService` 显式调用。
- `permission_change_log.old_snapshot/new_snapshot` 保存原始审计快照。
- `permission_change_log.diff_snapshot` 保存结构化变更摘要，顶层包含 `eventType + items[]`，用于排查展示和筛选。
- `diff_snapshot` 只描述本次写操作直接改变了什么，不计算用户最终有效权限是否新增或删除。
- 同一权限可能来自多个角色；某个角色删除权限时，只能说明”可能影响该用户”，不能直接推断”用户已失去该权限”。

## 缓存与一致性

- 权限运行时计算应复用统一的 `PermQueryEngine` 角色解析、条件评估、冲突处理、租户过滤和缓存逻辑。
- **缓存失效采用 Redis pub/sub 主动广播 + TTL 兜底**（2026-06-27 T-PERM-007 核对）：写操作通过 `@PermissionChange` 绑定 `PermissionChangeContext`，业务侧只调用 `markRoles/markUsers/markConditions/markRoleSnapshots/markServiceCodes` 登记影响范围；事务提交后由 `PermissionChangeAspect` 统一 evict `EFFECTIVE_ROLES` / `ROLE_PERM_SNAPSHOT` / `CONDITION_RULES` 并通过 `StringRedisTemplate.convertAndSend("perm:invalidate", PermInvalidateEvent JSON)` 广播。Gateway 订阅后 evict 本地接口快照；广播丢失由 TTL（30-60s）自然过期兜底。**已删除 `permission_version` 机制**（原“递增 version 驱动失效”的设计已废弃，Gateway 不读 version）。
- Gateway 本地 L1 缓存（Caffeine，TTL ≤15s，catalog `gw:interface-snapshot`；30 秒是串行授权安全总预算 = 授权 L2 ≤10s + 回源全链路截止 ≤5s + 快照 L1 ≤15s，见 architecture §7.2），L2 缓存由权限中心内部维护（Redis，通过 `CacheService` + `PermCacheCatalog` 统一管理）。权限中心侧不再缓存 `INTERFACE_SNAPSHOT(L2)`，接口快照每次实时调 engine 构建，依赖 `ROLE_PERM_SNAPSHOT` 兜住角色权限记录读路径。
- 缓存失效在事务提交后由 `PermissionChangeAspect.afterCommit` 执行；业务侧（AppService / DomainService 方法体）禁止手写 `TransactionSynchronizationManager`。
- 接口级权限检查（`check-interface`）匹配 API 映射后直接走 `API.ACCESS` 引擎判定，无额外 VIEW 门禁。

## 关联文档

| 文档                                                                                                 | 说明                                     |
| ---------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| [api-contract.md](api-contract.md)                                                                   | 外部 API 路径、请求体、响应体、错误码    |
| [core-flows.md](core-flows.md)                                                                       | 核心场景端到端调用链路                   |
| [implementation.md](implementation.md)                                                               | 分层架构、类清单、关键机制实现细节       |
| [../../archive/2026-05-30/service-layer-review.md](../../archive/2026-05-30/service-layer-review.md) | Service 层重构分析（Phase 1-5 完成总结） |
| [../schema/access-service.sql](../schema/access-service.sql)                                         | 表结构 DDL（唯一权威）                   |

## 非权威内容

旧版完整设计和产品功能长文档已移动到 [../../archive/2026-04-28/](../../archive/2026-04-28/)。归档内容仅用于追溯，不作为实现依据。
