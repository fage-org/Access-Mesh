# Permission Center 概念模型

本文档只描述权限中心的核心模型和关键规则。API 路径、请求体、响应体以 [api-contract.md](api-contract.md) 为准；表字段、索引、约束以 [../schema/permission-center.sql](../schema/permission-center.sql) 为准；端到端调用链路见 [core-flows.md](core-flows.md)。

## 设计原则

- 权限中心是通用权限事实与鉴权引擎，不读取业务服务私有表。
- 所有数据按 `tenant_id` 隔离，运行时租户来自 `X-Tenant-Id` 或安全上下文，请求体不承载 `tenantId`。
- 所有关联使用逻辑 ID，不使用数据库外键；一致性由应用服务保证。
- 所有表使用软删除，`delete_flag=0` 表示有效数据。
- API 统一使用 `POST + JSON Body`，路径统一在 `/api/perm/*` 命名空间下。
- 运行时接口使用稳定业务键，不要求调用方传权限中心内部主键。

## 核心对象

| 对象 | 说明 |
|------|------|
| `abstract_user` | 抽象主体，表示人员或服务，通过 `user_type + external_id` 对外定位 |
| `abstract_role` | 抽象角色，支持组织、职位、个人、分组角色、基础角色 |
| `user_role` | 主体与角色关系，职位场景可用 `relation_id` 表达所属组织 |
| `resource_entity` | 权限资源，菜单、按钮、接口、报表、数据范围等都建模为资源 |
| `operation_permission` | 资源操作，如 `VIEW`、`MANAGE`、`DATA_READ`、`DATA_EDIT` |
| `role_resource_permission` | 角色对资源操作的授权事实，支持条件、子权限、全量范围 |
| `resource_api_mapping` | 接口资源与 Gateway 原始路径的映射 |
| `permission_condition` | 可复用权限条件，如时间范围、IP 白名单 |
| `permission_version` | 权限版本，用于缓存失效 |
| `permission_change_log` / `operation_log` | 权限变更和写操作审计 |

## 角色模型

- `ORG`：组织角色，支持树形结构，可参与授权。
- `POSITION`：职位角色，分配给用户时可通过 `user_role.relation_id` 绑定所属组织。
- `PERSONAL`：个人角色，每个用户最多一个，用于用户级特殊授权。
- `GROUP_ROLE`：分组角色，用于组织角色集合，不直接配置权限。首期通过 `extra.basicRoleIds` 简化关联，缓存构建阶段展开。
- `BASIC_ROLE`：基础角色，承载可复用权限配置。

用户有效角色由 `user_role`、角色启停状态、分组角色展开、职位上下文共同决定。角色层级用于管理和分组，不默认表示权限继承。

## 资源与操作

- 对外 API 使用 `subjectTypeCode/resourceTypeCode/roleTypeCode` 等稳定字符串编码；内部存储和计算使用 `type_definition.type_value`。
- `type_value` 在同一租户和同一 `type_key` 内全局唯一，不随业务域重复；业务域只影响 `type_code` 解析范围和管理分区。
- `domainCode` 是管理分区和命名空间，不是子租户。传入时查询该域和全局对象，不传时只查询全局对象。
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

范围权限用于表达“用户进入某个主资源后，能操作哪些范围资源”。典型例子是查看销售报表时能读取哪些部门数据，或编辑报表数据时能编辑哪些部门范围。

有效范围权限由两类授权取并集：

```text
effectiveScopes = DIRECT 直接范围权限 ∪ DEPENDENT 子权限范围权限
```

- `DIRECT`：`depend_on IS NULL` 的独立范围资源授权，例如 A 部门主管拥有 `dept:A + DATA_READ`。
- `DEPENDENT`：`depend_on` 指向当前主权限的子权限，只在该主资源上下文内生效，例如用户只在销售报表下额外拥有 `dept:B + DATA_READ`。
- `scope_all=true`：显式表示某资源类型下全量范围权限，例如 `DATA_EDIT + DEPT + scope_all=true` 表示可编辑全部部门范围。
- 空范围结果不代表全量，必须通过 `scope_all=true` 表达全量。

推荐在 example-service 中使用 `report:sales + DATA_READ -> dept + DATA_READ`、`report:sales + DATA_EDIT -> dept + DATA_EDIT` 的同名业务数据动作映射。该映射是推荐范例，不是所有接入系统的强制标准。

## 鉴权与查询入口

- `auth/check`：判断单个资源操作是否允许。
- `auth/batch-check`：批量判断多个资源操作。
- `auth/check-interface`：Gateway 接口级鉴权。
- `auth/query-resources`：查询用户能操作哪些独立资源。
- `auth/query-scopes`：查询用户在某个主资源上下文内能操作哪些范围资源。
- `permission-view/*`：用于管理端解释和审计，不作为业务服务高频运行时依赖。

## 权限排查与变更日志

权限排查能力采用“当前权限事实 + 最近影响事件”的轻量模型，用于解释用户或管理员常见问题，例如“为什么突然缺失某权限”或“为什么突然新增某权限”。

- `permission-view/effective-permissions` 分页筛选展示当前有效权限；用户视角可展示权限来源角色摘要。
- `permission-view/explain` 是单权限排查主入口，用于解释某个具体资源操作当前是否拥有、来源角色、拒绝原因和近期相关变更。
- `permission-view/recent-changes` 展示最近一段时间可能影响目标用户或角色权限的事件。
- `effective-permissions` 默认不展开数据范围、子权限、API 资源和完整来源角色，避免大权限用户一次返回过多数据。
- `permission_change_log.old_snapshot/new_snapshot` 保存原始审计快照。
- `permission_change_log.diff_snapshot` 保存结构化变更摘要，顶层包含 `eventType + items[]`，用于排查展示和筛选。
- `diff_snapshot` 只描述本次写操作直接改变了什么，不计算用户最终有效权限是否新增或删除。
- 同一权限可能来自多个角色；某个角色删除权限时，只能说明“可能影响该用户”，不能直接推断“用户已失去该权限”。

## 缓存与一致性

- 权限运行时计算应复用统一的角色解析、条件评估、冲突处理、租户过滤和缓存失效逻辑。
- 角色关系、角色权限、资源、接口映射、条件、冲突规则变更后必须递增相关 `permission_version`。
- Gateway 可做本地 L1 缓存，但接口级最终判定以权限中心运行时接口为准。

## 非权威内容

旧版完整设计和产品功能长文档已移动到 [../archive/2026-04-28/](../archive/2026-04-28/)。归档内容仅用于追溯，不作为实现依据。
