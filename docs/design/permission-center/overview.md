# Permission Center 概念模型

本文档只描述权限中心的核心模型和关键规则。API 路径、请求体、响应体以 [api-contract.md](api-contract.md) 为准；表字段、索引、约束以 [../schema/permission-center.sql](../schema/permission-center.sql) 为准；端到端调用链路见 [core-flows.md](core-flows.md)；实现细节和类清单见 [implementation.md](implementation.md)。

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
| **Audit（审计）**                  | `permission_version`, `permission_change_log`, `operation_log`  | 权限版本号（缓存失效驱动）、权限变更日志（diff 快照）和操作日志（入口写操作记录）。入口日志由 `@OperationLog` AOP 自动记录；内部动态日志（diff 快照、冲突通知）由 `AuditDomainService` 显式调用。                                                         |
| **SystemConfig（系统配置）**       | `system_config`                                                 | 租户级配置（角色唯一性、默认策略等）。                                                                                                                                                                                                                    |

## 分层架构

```
Controller ──► AppService（调度层） ──► DomainService（领域层） ──► Mapper（数据访问）
                                         ├── PermQueryEngine（统一鉴权引擎）
                                         └── AOP（@OperationLog 自动记录入口日志）
```

- **Controller**（19 个）：接收请求、解析 Header 中的 tenant/operator、将业务键（code）转换为内部 ID。
- **AppService**（20 个）：调度/编排层，组合多个 DomainService 完成业务流程。每个 Service 按单一职责拆分（如 PermissionCheck/PermissionGrant/PermissionView 等）。
- **DomainService**（12 个）：领域逻辑层，封装可复用的业务规则（角色解析、条件评估、冲突过滤、类型解析、域分类、权限版本等）。`PermQueryEngine` 是统一权限查询引擎的唯一入口。
- **Mapper**（18 个）：MyBatis-Flex 数据访问，使用 `Tables` 类引用 TableDef（禁止静态导入 APT 生成的 `*TableDef` 类）。`RolePermEntryMapper` 是工具类（位于 `util` 包），负责 `RoleResourcePermission→RolePermEntry` 的转换。
- **AOP**：`@OperationLog` 注解 + `OperationLogAspect` 切面自动拦截 AppService 写方法并记录入口级操作日志。`OperationLogRuntimeContext` 允许方法体内通过 `markSkip()`/`setSummary()`/`setTargetType()`/`setTargetId()` 覆盖注解值。

## 角色模型

- `ORG`：组织角色，支持树形结构，可参与授权。
- `POSITION`：职位角色，分配给用户时可通过 `user_role.relation_id` 绑定所属组织。
- `PERSONAL`：个人角色，每个用户最多一个，用于用户级特殊授权。
- `GROUP_ROLE`：分组角色，用于组织角色集合，不直接配置权限。首期通过 `extra.basicRoleIds` 简化关联，缓存构建阶段展开。
- `BASIC_ROLE`：基础角色，承载可复用权限配置。

在 AccessMesh 管理端语义中，组织既是业务树节点，也是角色容器。admin-service 主维护组织树和 `user-org` 关系；permission-center 保存由组织与岗位规则映射出的 ORG/POSITION 角色及最终 `user_role` 权限事实。

用户有效角色由 `SubjectDomainService.resolveEffectiveRoles()` 统一解析（L1 CacheService → L2 Redis → DB），禁止在 Service 中直接查询 `user_role` 表或自己写角色解析逻辑。角色层级用于管理和分组，不默认表示权限继承。

## 资源与操作

- 对外 API 使用 `subjectTypeCode/resourceTypeCode/roleTypeCode` 等稳定字符串编码；内部存储和计算使用 `type_definition.type_value`。
- `type_value` 在同一租户和同一 `type_key` 内全局唯一，不随业务域重复；业务域只影响 `type_code` 解析范围和管理分区。
- `domainCode` 是管理分区和命名空间，不是子租户。传入时查询该域和全局对象，不传时只查询全局对象。
- 业务域不承担数据权限载体、运行时鉴权主链或资源归属重构职责；其主要作用是降低后台管理复杂度，让不同业务管理员聚焦各自负责的角色和权限集合。
- 资源通过 `resourceTypeCode + resourceCode + codeType + domainCode` 定位。
- 操作通过 `operationCode` 定位，并必须与资源类型兼容。
- 接口权限也是资源权限，Gateway 使用 `resource_api_mapping` 将请求路径映射到资源操作；同一路径可映射多个资源，接口级鉴权采用任一资源权限通过即允许的 OR 语义。
- 业务服务如果需要查询“用户能管理哪些组织/角色/菜单”，应先把这些对象建模为 `resource_entity`。

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
- `scope_all=true`：作为一等权限维度存在，不展开为 N 个具体资源条目。例如 `DATA_EDIT + DEPT + scope_all=true` 表示可编辑全部部门范围。
  - `SnapshotAssembler`：不展开 scopeAll 条目，直接作为 `ApiPermissionEntry(scopeAll=true)` 返回。
  - `PermViewAssembler`：按 `resourceType` 分组输出 scopeAll 视图项。
  - 空范围结果不代表全量，必须通过 `scope_all=true` 显式表达。

推荐在 example-service 中使用 `report:sales + DATA_READ -> dept + DATA_READ`、`report:sales + DATA_EDIT -> dept + DATA_EDIT` 的同名业务数据动作映射。该映射是推荐范例，不是所有接入系统的强制标准。

## 鉴权与查询入口

所有权限查询统一通过 `PermQueryEngine.query(PermQuery)` 执行。引擎提供两种粒度的 API：

**AppService 层 API（单目标/批量校验）：**

```java
// 单目标鉴权
engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);
// 批量校验（拒绝时抛 SecurityException）
engine.validateBatch(tenantId, operatorId, ResourceTypeCode.ROLE, roleIds, OperationCodeConstants.DELETE);
// 批量获取拒绝 ID 集合
Set<Long> denied = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.USER, userIds, OperationCodeConstants.MANAGE);
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
| `PermQuery.forUserView`       | 用户视图 | 全量角色权限记录，不按位过滤，完整数据输出      |

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
- 角色关系、角色权限、资源、接口映射、条件、冲突规则变更后必须递增相关 `permission_version`。
- Gateway 可做本地 L1 缓存（Caffeine, TTL 30s），L2 缓存由权限中心内部维护（Redis，通过 `CacheService` + `PermCacheCatalog` 统一管理）。
- 缓存失效在事务提交后（`TransactionSynchronization.afterCommit`）执行，版本递增由调用方在 `afterCommit` 中显式调用 `PermissionVersionDomainService.increment()`。
- 接口级权限检查（`check-interface`）匹配 API 映射后直接走 `API.ACCESS` 引擎判定，无额外 VIEW 门禁。

## 关联文档

| 文档                                                                                                 | 说明                                     |
| ---------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| [api-contract.md](api-contract.md)                                                                   | 外部 API 路径、请求体、响应体、错误码    |
| [core-flows.md](core-flows.md)                                                                       | 核心场景端到端调用链路                   |
| [implementation.md](implementation.md)                                                               | 分层架构、类清单、关键机制实现细节       |
| [../../archive/2026-05-30/service-layer-review.md](../../archive/2026-05-30/service-layer-review.md) | Service 层重构分析（Phase 1-5 完成总结） |
| [../schema/permission-center.sql](../schema/permission-center.sql)                                   | 表结构 DDL                               |

## 非权威内容

旧版完整设计和产品功能长文档已移动到 [../archive/2026-04-28/](../archive/2026-04-28/)。归档内容仅用于追溯，不作为实现依据。
