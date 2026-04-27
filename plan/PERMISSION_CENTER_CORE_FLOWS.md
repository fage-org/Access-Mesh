# Permission Center 核心流程链路

> 本文档把权限管理的核心场景串成接口调用链路，用于确认 API 契约、产品目标和实现方向是否一致。接口契约以 `plan/PERMISSION_CENTER_API_CONTRACT.md` 为准。

## 1. 全局约定

所有场景都遵守以下约定：

| 约定 | 说明 |
|------|------|
| 租户来源 | 只从 `X-Tenant-Id` 读取，请求体不包含 `tenantId` |
| 操作者来源 | 管理类写操作从 Token/SecurityContext 读取操作者 |
| 接口方式 | 全部 `POST + application/json` |
| 对外标识 | 运行时接口使用 `subjectType + subjectExternalId`、`resourceType + resourceCode + codeType`、`operationCode` |
| 内部明细 ID | 仅用于已返回记录的更新、删除、子权限挂载，如 `permissionId`、`ids` |
| 响应结构 | `data` 必须是对象，列表使用 `data.items` |
| 事实来源 | `abstract_user`、`abstract_role`、`resource_entity`、`operation_permission`、`role_resource_permission` 等原表是事实来源 |
| 缓存失效 | 权限关系、角色、资源、条件、依赖变更后递增相关角色 `permission_version` 并失效缓存 |

## 2. 总体主链路

```mermaid
flowchart LR
    A["初始化类型和业务域"] --> B["同步用户/服务/资源"]
    B --> C["创建角色与用户-角色关系"]
    C --> D["配置角色-资源-操作权限"]
    D --> E["配置子权限/条件/依赖/冲突规则"]
    E --> F["Gateway 或 SDK 发起鉴权"]
    F --> G["权限中心解析主体、角色、资源、操作和条件"]
    G --> H["返回允许/拒绝、数据范围和命中信息"]
    H --> I["权限视图和审计日志用于排查"]
```

## 3. 场景一：租户初始化与权限模型准备

目标：为一个租户建立基本类型、业务域、操作权限和域规则。

| 步骤 | 接口 | 关键入参 | 结果 |
|------|------|----------|------|
| 1 | `POST /api/perm/type-definition/create` | `typeKey=user_type/role_type/resource_type` | 建立用户、角色、资源类型 |
| 2 | `POST /api/perm/biz-domain/create` | `code=admin` | 建立业务域 |
| 3 | `POST /api/perm/operation-permission/create` | `resourceType + operationCode` | 建立 VIEW/EDIT/ACCESS/DATA_READ 等操作 |
| 4 | `POST /api/perm/domain-config/save` | `configType=SCOPE/RELATION/BINDING/SUB_PERM` | 约束域内允许的角色、资源、操作和子权限 |
| 5 | `POST /api/perm/system-config/save` | 租户级配置 | 保存角色唯一性、默认策略等配置 |

关键逻辑：

- 创建 `resource_type` 时可以自动预置 CRUD 或 ACCESS 操作，具体以实现配置为准。
- `SUB_PERM` 决定哪些资源类型可以作为某类父资源的子权限。
- 这些配置是后续授权校验的基础，不直接给用户产生权限。

## 4. 场景二：接入服务并同步接口资源

目标：让业务服务把自己的接口注册到权限中心，供 Gateway 鉴权使用。

| 步骤 | 接口 | 关键入参 | 结果 |
|------|------|----------|------|
| 1 | `POST /api/perm/service-config/save` | `serviceCode + basePath + name` | 注册或更新服务 |
| 2 | `POST /api/perm/service-config/sync` | `syncMode=FULL + groups[].apis[]` | 全量同步服务接口 |
| 3 | `POST /api/perm/service-config/apis` | `serviceCode` | 查看服务接口资源树 |
| 4 | `POST /api/perm/resource-api-mapping/list` | `serviceCode` 或 `resourceId` | 查看接口映射 |

关键逻辑：

- 首期只支持 FULL 全量同步，本次上报内容就是该 `serviceCode` 的完整事实。
- 权限中心用 `basePath + api.path` 生成 Gateway 原始路径并写入 `resource_api_mapping.path_pattern`。
- 新接口自动创建 API 类型 `resource_entity` 和 `resource_api_mapping`。
- 上报中缺失的旧接口会被软删除映射；若资源是自动创建的 API 资源，也可同步软删除。
- Gateway 鉴权使用客户端原始请求路径匹配，不使用后端 StripPrefix 后路径。

## 5. 场景三：同步主体、创建角色、分配角色

目标：把外部用户同步为权限主体，并为其分配组织、职位、基础角色或分组角色。

| 步骤 | 接口 | 关键入参 | 结果 |
|------|------|----------|------|
| 1 | `POST /api/perm/abstract-user/sync` | `userType + externalId + name + version` | 幂等同步用户 |
| 2 | `POST /api/perm/abstract-role/create` | `roleType + roleExternalId + name + domainCode` | 创建可授权角色 |
| 3 | `POST /api/perm/abstract-role/tree` | `domainCode/roleType` | 查看角色层级 |
| 4 | `POST /api/perm/user-role/assign` | `subjectType + subjectExternalId + assignments[]` | 给用户分配角色 |
| 5 | `POST /api/perm/permission-view/effective-roles` | `subjectType + subjectExternalId` | 验证用户有效角色 |

关键逻辑：

- 用户同步以 `userType + externalId` 幂等定位。
- 对外可调用的角色建议必须有 `roleExternalId`，后续授权和分配可以不用内部角色 ID。
- `GROUP_ROLE` 本身不直接配置权限，通过子角色或额外基本角色产生有效权限。
- `POSITION` 类型分配时可带组织关系字段，用于表达职位在某组织下的上下文。
- 分配或回收用户角色后，失效该用户有效角色缓存。

## 6. 场景四：配置基础角色权限

目标：为角色配置资源和操作权限，这是最核心的权限事实写入链路。

| 步骤 | 接口 | 关键入参 | 结果 |
|------|------|----------|------|
| 1 | `POST /api/perm/resource-entity/create` | `resourceType + resourceCode + codeType + name` | 创建菜单、按钮、API、DATA 等资源 |
| 2 | `POST /api/perm/operation-permission/list` | `resourceType` | 选择适用操作 |
| 3 | `POST /api/perm/permission-condition/create` | `conditionCode + conditionRules` | 可选，创建复用条件 |
| 4 | `POST /api/perm/role-resource-permission/save` | `roleType + roleExternalId + add/update/remove` | 三段式保存授权 |
| 5 | `POST /api/perm/role-resource-permission/list` | `roleType + roleExternalId` | 验证角色权限 |

关键逻辑：

- `save` 在一个事务中处理 `add/update/remove`。
- 授权项用 `resourceType + resourceCode + codeType + operationCode` 定位资源和操作。
- 操作必须与资源类型匹配，或操作是全局操作。
- 条件可选，填写 `conditionCode` 时必须存在且启用。
- 写入后记录 `operation_log` 和 `permission_change_log`，递增该角色 `permission_version`，失效角色权限缓存。

## 7. 场景五：配置子权限和数据范围

目标：在主权限下挂数据范围或其他附属权限，用 `depend_on` 表达主从关系。

| 步骤 | 接口 | 关键入参 | 结果 |
|------|------|----------|------|
| 1 | `POST /api/perm/domain-config/save` | `configType=SUB_PERM` | 定义父资源类型允许挂载的子资源类型 |
| 2 | `POST /api/perm/resource-entity/create` | `resourceType=DATA + resourceCode` | 创建数据范围资源 |
| 3 | `POST /api/perm/role-resource-permission/save` | 主权限授权 | 返回主权限 `id` |
| 4 | `POST /api/perm/role-resource-permission/add-child` | `parentPermissionId + children[]` | 写入子权限，`depend_on=parentPermissionId` |
| 5 | `POST /api/perm/role-resource-permission/children` | `permissionId` | 查询主权限下子权限 |
| 6 | `POST /api/perm/auth/query-scopes` | 主资源业务键、主操作、范围资源类型和范围操作 | 运行时查询范围权限 |

关键逻辑：

- `parentPermissionId` 指向 `role_resource_permission.id`，且该记录必须 `depend_on IS NULL`。
- 子权限继承父权限的角色，不需要再次传 `roleType + roleExternalId`。
- 子权限只支持一层，不允许子权限继续挂子权限。
- 删除主权限时级联软删子权限。
- 权限中心只返回数据范围事实，不生成业务 SQL，不解释业务字段。
- `auth/check` 适合只判断主权限是否允许；业务需要拿范围权限集合时，使用 `auth/query-scopes`。
- `scopeAll=true` 表示该授权覆盖某个资源类型下全部范围资源，不需要创建 `data:all` 这类特殊资源。

## 8. 场景六：Gateway 接口级鉴权

目标：Gateway 在请求进入业务服务前，通过权限中心判断当前用户是否能访问接口。

| 步骤 | 执行方 | 动作 |
|------|--------|------|
| 1 | Gateway | 解析 Token，得到 `subjectType + subjectExternalId` 和 `X-Tenant-Id` |
| 2 | Gateway | 提取 `serviceCode + httpMethod + 原始 path` |
| 3 | Gateway | 查询本地 L1 缓存 |
| 4 | Gateway | 缓存未命中时调用 `POST /api/perm/auth/check-interface` |
| 5 | permission-center | 按租户、服务、方法、路径匹配 `resource_api_mapping` |
| 6 | permission-center | 解析资源、操作、用户有效角色、条件和冲突规则 |
| 7 | permission-center | 返回 `allowed/reason/matchedResourceId/matchedRoleIds/cacheTtlSeconds` |
| 8 | Gateway | 允许则转发业务服务，拒绝则返回 403 |

拒绝原因示例：

| reason | 典型含义 |
|--------|----------|
| `API_NOT_REGISTERED` | 接口没有映射，白名单模式拒绝 |
| `USER_DISABLED` | 主体被停用 |
| `NO_ROLE` | 用户无有效角色 |
| `NO_PERMISSION` | 有角色但无接口资源权限 |
| `CONDITION_NOT_MET` | 条件不满足 |
| `CONFLICT_DETECTED` | 权限互斥导致失效 |

## 9. 场景七：业务服务 SDK 鉴权与权限查询

目标：业务服务既能判断单个动作是否允许，也能查询用户可操作资源集合和数据范围。SDK 运行时接口不依赖权限中心内部数据库 ID，也不复用管理端解释用的 `permission-view/*`。

| 能力 | 接口 | 典型场景 | 结果 |
|------|------|----------|------|
| 布尔鉴权 | `POST /api/perm/auth/check` | 打开报表前判断是否有 `VIEW` 权限 | `allowed/reason` |
| 批量鉴权 | `POST /api/perm/auth/batch-check` | 列表页按钮批量置灰 | 每个检查项的 `allowed/reason` |
| 可操作资源查询 | `POST /api/perm/auth/query-resources` | admin-service 查询可管理组织、角色、菜单 | 资源业务键集合和命中操作 |
| 范围权限查询 | `POST /api/perm/auth/query-scopes` | example-service 查询报表可读、可编辑的城市、部门、门店等范围 | 范围权限集合 |

### 9.1 admin-service 查询可管理对象

admin-service 需要先把可被权限控制的组织、角色、菜单同步或创建为权限中心资源。

| 查询目标 | 资源建模 | 运行时查询 |
|----------|----------|------------|
| 用户能管理哪些组织 | `resourceType=ORG`、`resourceCode=org:{orgId}` | `query-resources` 传 `resourceTypes=[ORG]`、`operationCodes=["MANAGE"]` |
| 用户能管理哪些角色 | `resourceType=ROLE`、`resourceCode=role:{roleExternalId}` | `query-resources` 传 `resourceTypes=[ROLE]`、`operationCodes=["MANAGE"]` 或 `["ASSIGN"]` |
| 用户能看到哪些菜单 | `resourceType=MENU`、`resourceCode=menu:{menuCode}` | `query-resources` 传 `resourceTypes=[MENU]`、`operationCodes=["VIEW"]`、`treeMode=true` |

调用链路：

1. admin-service 从登录态取 `subjectType + subjectExternalId` 和 `X-Tenant-Id`。
2. admin-service 调用 `POST /api/perm/auth/query-resources`，传资源类型、操作码、业务域和上下文。
3. permission-center 解析用户有效角色、角色继承、资源继承、条件、冲突规则。
4. permission-center 返回命中的 `resourceCode`、`operations`、`matchedRoleIds`、`matchedPermissionIds`。
5. admin-service 用 `resourceCode` 回查本服务组织、角色、菜单表，过滤列表或组装树。

关键逻辑：

- 权限中心不直接查询 admin-service 的业务表，只返回权限事实。
- 如果角色本身也是被管理对象，就必须把角色建模成 `resource_entity`；`abstract_role` 只表示授权主体，不等同于“可被管理的角色资源”。
- 菜单树展示可以用 `treeMode=true` 返回权限中心资源树，但最终排序、隐藏字段、路由元信息仍由 admin-service 控制。

### 9.2 example-service 查询报表范围权限

example-service 需要把报表建模为主资源，把城市、部门、门店、数据集等建模为范围资源。直接范围权限和依赖当前报表主权限的子权限会在运行时取并集。

| 步骤 | 动作 | 说明 |
|------|------|------|
| 1 | 同步报表资源 | 例如 `resourceType=REPORT`、`resourceCode=report:sales` |
| 2 | 同步范围资源 | 例如 `resourceType=DATA`、`resourceCode=data:dept:A` |
| 3 | 配置直接范围权限 | 例如 A 部门主管角色拥有 `data:dept:A + DATA_READ` |
| 4 | 配置报表主权限 | 推荐示例为 `report:sales + DATA_READ`、`report:sales + DATA_EDIT` |
| 5 | 配置子权限 | 在销售报表主权限下额外挂 `data:dept:B + DATA_READ` |
| 6 | 运行时查询 | 调用 `POST /api/perm/auth/query-scopes`，可同时传 `DATA_READ` 和 `DATA_EDIT` |
| 7 | 业务过滤 | example-service 把返回的 `resourceCode` 或 `scopeAll=true` 转换为本服务报表查询条件 |

运行时规则：

- permission-center 先判断用户是否拥有主资源操作，例如 `report:sales + DATA_READ` 或 `report:sales + DATA_EDIT`。
- 主权限不通过时，`allowed=false` 且 `items=[]`。
- 直接范围权限 `DIRECT` 与当前主权限下的子权限 `DEPENDENT` 按并集返回。
- 多个主操作和多个范围操作可以一次查询；范围操作必须被一个已通过的主操作激活。
- 主资源上的 `DATA_READ/DATA_EDIT` 是推荐范例，用于 example-service 表达报表承载数据的读写；其他业务系统可以定义自己的主操作和范围操作映射。
- 子权限也要参与条件计算和冲突处理，未满足条件的子权限不进入结果。
- `items=[]` 默认表示无显式范围权限，不表示全量范围。
- 全量范围必须通过 `scopeAll=true` 显式表达，例如 `DATA_EDIT + DEPT + scopeAll=true` 表示可编辑全部部门范围。
- 权限中心不生成 SQL；example-service 自行把 `data:city:shanghai`、`data:dept:finance` 等资源键映射为查询条件。

### 9.3 运行时查询边界

- `auth/check` 和 `auth/batch-check` 解决“能不能做”。
- `auth/query-resources` 解决“能操作哪些资源对象”。
- `auth/query-scopes` 解决“允许访问主资源后，能操作哪些范围资源”。
- `permission-view/*` 解决“为什么有/没有权限”，用于管理端解释、排查和审计，不作为业务服务高频运行时依赖。

## 10. 场景八：接口变更后的全量同步和权限影响

目标：服务发布后接口新增、删除或路径变化，权限中心跟随更新。

| 步骤 | 接口 | 关键入参 | 结果 |
|------|------|----------|------|
| 1 | `POST /api/perm/service-config/sync` | 新的 FULL 接口列表 | 权限中心计算 diff |
| 2 | 自动处理 | 新接口创建 API 资源和映射 | 可被授权 |
| 3 | 自动处理 | 删除接口软删映射和自动创建资源 | Gateway 不再匹配旧接口 |
| 4 | 自动处理 | 影响已有角色权限时递增版本 | 缓存失效 |
| 5 | `POST /api/perm/service-config/apis` | `serviceCode` | 验证最新接口资源树 |

关键逻辑：

- 同步入口不做增量模式，减少接入方心智负担。
- 自动删除只应处理由接口同步创建的 API 资源，避免误删人工维护资源。
- 路径变更应视为旧接口删除和新接口新增。

## 11. 场景九：资源依赖自动补全

目标：授权某个资源时，自动补齐它依赖的接口或数据资源权限。

| 步骤 | 接口 | 关键入参 | 结果 |
|------|------|----------|------|
| 1 | `POST /api/perm/resource-dependency/create` | 源资源、依赖资源、触发操作位、required 操作位、`autoGrant=true` | 建立依赖规则 |
| 2 | `POST /api/perm/role-resource-permission/save` | 给角色授权源资源 | 自动补齐依赖资源权限 |
| 3 | 自动处理 | 写入 `grantSource=AUTO_DEP + grantDepId` | 标记补全来源 |
| 4 | `POST /api/perm/role-resource-permission/list` | 查询角色权限 | 能看到自动补齐结果 |
| 5 | `POST /api/perm/resource-dependency/batch-sync` | 修改依赖规则 | 清理旧补全并重新评估 |

关键逻辑：

- 自动补全不应产生重复授权。
- 依赖规则变更时按 `grantDepId` 精准清理自动补全记录。
- 自动补全同样需要记录变更日志和递增权限版本。

## 12. 场景十：权限视图和审计排查

目标：让管理端、测试和运维能解释“用户为什么有/没有某权限”。

| 查询目标 | 接口 | 说明 |
|----------|------|------|
| 用户有效角色 | `POST /api/perm/permission-view/effective-roles` | 展示直接、分组、组织、职位等来源 |
| 用户有效权限 | `POST /api/perm/permission-view/effective-permissions` | 展示资源、操作、数据范围 |
| 用户资源树 | `POST /api/perm/permission-view/resource-tree` | 菜单/按钮展示常用 |
| 资源授权用户 | `POST /api/perm/permission-view/resource-users` | 反查谁拥有某资源权限 |
| 角色权限 | `POST /api/perm/permission-view/role-permissions` | 角色维度排查 |
| 近期变更 | `POST /api/perm/permission-view/recent-changes` | 结合变更日志解释权限变化 |
| 操作日志 | `POST /api/perm/operation-log/list` | 所有写操作轻量审计 |
| 权限变更日志 | `POST /api/perm/permission-change-log/list` | 权限 diff 审计 |

关键逻辑：

- 权限视图应使用实时事实数据或最新缓存，不应返回已软删除记录。
- 视图接口用于解释和展示，不应承担写入职责。
- 审计日志需要包含 `requestId`、操作者、目标对象、前后差异或摘要。

## 13. 场景十一：回收、删除和级联

目标：确保删除和回收不会留下可生效的悬挂权限。

| 场景 | 接口 | 级联或失效 |
|------|------|------------|
| 回收用户角色 | `POST /api/perm/user-role/revoke` | 失效用户有效角色缓存 |
| 回收角色权限 | `POST /api/perm/role-resource-permission/revoke` | 级联软删子权限，递增角色版本 |
| 删除子权限 | `POST /api/perm/role-resource-permission/remove-child` | 只允许删除 `depend_on IS NOT NULL` 记录 |
| 删除资源 | `POST /api/perm/resource-entity/remove` | 软删资源、接口映射、角色权限、依赖关系 |
| 删除角色 | `POST /api/perm/abstract-role/remove` | 软删用户角色关系和角色权限，递增版本 |
| 删除用户 | `POST /api/perm/abstract-user/remove` | 软删用户角色关系和个人角色权限 |
| 停用用户/角色/资源/服务 | 对应 update/save 接口 | 运行时鉴权直接拒绝或不参与计算 |

关键逻辑：

- 删除统一是软删除。
- 删除父权限必须级联删除 `depend_on` 指向它的子权限。
- 回收和删除都必须写审计日志。
- 任何影响鉴权结果的变更都必须触发缓存失效。

## 14. 是否满足目标的检查点

| 目标 | 检查方式 |
|------|----------|
| 通用权限服务 | 外部系统只使用稳定业务键即可完成主体同步、授权和鉴权 |
| 接口规范统一 | 全部接口走 `/api/perm/*`，无 RESTful Path 参数，无 body `tenantId` |
| SaaS 多租户 | 所有查询和写入都强制带 `X-Tenant-Id`，接口映射也按租户过滤 |
| Gateway 可接入 | `check-interface` 使用 serviceCode、method、原始 path 判定 |
| SDK 可接入 | `auth/check`、`auth/batch-check`、`auth/query-resources`、`auth/query-scopes` 都不要求内部数据库 ID |
| 管理端可解释 | 权限视图、操作日志、变更日志能解释授权来源和变更历史 |
| 数据权限可表达 | `depend_on` 子权限和直接范围权限共同表达主资源上下文内的有效范围，运行时通过 `auth/query-scopes` 查询 |
| 接口同步简单 | 首期只有 FULL 同步，接入服务不需要维护增量事件 |
| 缓存一致性 | 权限变更、依赖变更、角色关系变更都能触发版本递增和缓存失效 |

## 15. 仍需实现时重点校验

- `abstract_role.external_id` 当前 schema 没有唯一索引；若对外使用 `roleType + roleExternalId` 定位角色，实现时需要补唯一约束或在服务层强校验。
- `operationCode` 在解析时必须结合 `resourceType`，避免不同资源类型下同名操作产生歧义。
- `resourceCode` 必须结合 `resourceType + codeType + domainCode` 解析，避免跨域或多编码歧义。
- `check-interface` 查询 `resource_api_mapping` 必须带 `tenant_id`。
- `auth/query-resources` 和 `auth/query-scopes` 必须复用 `auth/check` 的鉴权计算链路，避免查询结果和布尔鉴权结果不一致。
- `role_resource_permission.scope_all` 必须显式参与查询结果，不能用空结果或特殊资源编码隐式表示全量范围。
- 所有 Request DTO 必须移除 `tenantId`。
- 所有列表响应必须包装成 `data.items`，不能直接返回数组。
