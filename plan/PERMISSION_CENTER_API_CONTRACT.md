# Permission Center 外部 API 契约草案

> 本文档定义 permission-center 对外稳定接口契约。目标是让权限中心既能服务 AccessMesh 内部 Gateway/SDK，又能作为通用权限管理服务暴露给外部业务系统。

## 1. 设计目标

- **统一命名空间**：所有稳定对外接口统一使用 `/api/perm/{resource}/{action}`。
- **保持通用性**：运行时和外部接入接口使用稳定业务键，避免外部系统必须感知权限中心内部主键。
- **保持强约束**：所有接口 `POST + application/json`，禁止 URL Path 参数和 Query 参数。
- **保留扩展空间**：响应 `data` 必须是对象，列表也用 `{ "items": [...] }` 包装。
- **安全多租户**：租户、操作者、调用来源优先来自 Header/Token/SecurityContext，不信任请求体里的同名字段。
- **SDK 友好**：DTO 进入独立 `permission-center-api` 或 `perm-common` 契约模块，不复用服务端内部 `Req/Resp`。

## 2. 接口分层

| 分层 | 路径前缀 | 调用方 | 特点 |
|------|----------|--------|------|
| 管理配置 API | `/api/perm/*` | 管理端、admin-service、接入系统后台 | 资源、角色、授权、条件、域配置、日志 |
| 运行时鉴权 API | `/api/perm/auth/*` | Gateway、业务服务 SDK | 高 QPS、可缓存、强稳定 |
| 服务接入 API | `/api/perm/service-config/*` | 接入服务、SDK Starter、管理端 | 服务注册、接口同步、接口资源树 |

> 不再定义 `/internal/perm/*` 主契约；本项目未上线，后续实现直接以 `/api/perm/*` 为准。

## 3. 通用协议

### 3.1 Header

| Header | 必填 | 说明 |
|--------|------|------|
| `Authorization` | 管理 API 必填 | `Bearer <token>` |
| `X-Tenant-Id` | 必填 | 当前租户 ID，由 Gateway 注入或调用方传入；请求体不再保留 `tenantId` |
| `X-Request-Id` | 可选 | 未传时由 Gateway 生成 |
| `X-Service-Code` | 内部/SDK 必填 | 调用方服务编码，用于内部来源校验 |
| `X-Api-Version` | 可选 | 契约版本，默认 `2026-04-26` |

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

| 对象 | 标准入参字段 | 说明 |
|------|--------------|------|
| 租户 | `X-Tenant-Id` | 只放 Header，不放 Body |
| 用户/主体 | `subjectType` + `subjectExternalId` | `subjectType` 对应 `type_definition(type_key='user_type').type_value` |
| 角色 | `roleType` + `roleExternalId` | 对外接口使用外部角色标识；可被外部调用分配/授权的角色必须有 `externalId` |
| 业务域 | `domainCode` | 可空；为空表示全局或不按域过滤 |
| 资源 | `resourceType` + `resourceCode` + `codeType` | `codeType` 默认 `default` |
| 操作 | `operationCode` | 在 `resourceType` 范围内解析；全局操作允许 `resourceType=null` |
| 条件 | `conditionCode` | 可空 |
| 明细记录 | `id` 或 `ids` | 仅用于更新/删除权限关系、日志详情等权限中心已返回的记录 |

原则：

- 运行时鉴权接口不要求调用方传内部 `id`。
- 管理端列表、创建、详情响应可以返回内部 `id`，用于后续 `update/remove`。
- 同一接口不同时接受 `id/code/externalId` 多套定位方式，避免歧义。
- 所有请求体禁止出现 `tenantId`；服务端统一从 `X-Tenant-Id` 和安全上下文读取租户。

## 4. 动词规范

| Action | 语义 |
|--------|------|
| `list` | 分页或非分页列表，响应必须包装 `{items,...}` |
| `tree` | 树结构查询 |
| `detail` | 单条详情 |
| `create` | 创建 |
| `update` | 局部更新 |
| `save` | 幂等创建或更新 |
| `remove` | 批量软删除，请求体统一 `{ "ids": [...] }` |
| `assign` | 分配用户角色关系 |
| `revoke` | 回收用户角色或权限关系 |
| `grant` | 权限授权，偏业务语义 |
| `sync` | 外部系统全量同步 |
| `check` | 判定 |
| `detect` | 检测但不落库 |

## 5. API 清单

### 5.1 类型与域

| 接口 | 说明 |
|------|------|
| `POST /api/perm/type-definition/list` | 查询类型定义 |
| `POST /api/perm/type-definition/detail` | 查询类型详情 |
| `POST /api/perm/type-definition/create` | 创建类型 |
| `POST /api/perm/type-definition/update` | 更新类型 |
| `POST /api/perm/type-definition/remove` | 删除类型，支持批量 |
| `POST /api/perm/biz-domain/list` | 查询业务域 |
| `POST /api/perm/biz-domain/detail` | 查询业务域详情 |
| `POST /api/perm/biz-domain/create` | 创建业务域 |
| `POST /api/perm/biz-domain/update` | 更新业务域 |
| `POST /api/perm/biz-domain/remove` | 删除业务域，支持批量 |

### 5.2 主体与角色

| 接口 | 说明 |
|------|------|
| `POST /api/perm/abstract-user/list` | 查询主体列表 |
| `POST /api/perm/abstract-user/detail` | 查询主体详情 |
| `POST /api/perm/abstract-user/create` | 创建主体，适合管理端 |
| `POST /api/perm/abstract-user/sync` | 幂等同步外部主体 |
| `POST /api/perm/abstract-user/update` | 更新主体 |
| `POST /api/perm/abstract-user/remove` | 删除主体，支持批量 |
| `POST /api/perm/abstract-role/list` | 查询角色列表 |
| `POST /api/perm/abstract-role/tree` | 查询角色树 |
| `POST /api/perm/abstract-role/detail` | 查询角色详情 |
| `POST /api/perm/abstract-role/create` | 创建角色 |
| `POST /api/perm/abstract-role/update` | 更新角色 |
| `POST /api/perm/abstract-role/move` | 移动角色树节点 |
| `POST /api/perm/abstract-role/remove` | 删除角色，支持批量 |
| `POST /api/perm/abstract-role/extra-roles/list` | 查询分组角色额外基本角色 |
| `POST /api/perm/abstract-role/extra-roles/add` | 分组角色添加基本角色 |
| `POST /api/perm/abstract-role/extra-roles/remove` | 分组角色移除基本角色 |

### 5.3 资源与操作

| 接口 | 说明 |
|------|------|
| `POST /api/perm/operation-permission/list` | 查询操作权限 |
| `POST /api/perm/operation-permission/detail` | 查询操作详情 |
| `POST /api/perm/operation-permission/create` | 创建操作 |
| `POST /api/perm/operation-permission/update` | 更新操作 |
| `POST /api/perm/operation-permission/remove` | 删除操作，支持批量 |
| `POST /api/perm/resource-entity/tree` | 查询资源树 |
| `POST /api/perm/resource-entity/list` | 查询资源列表 |
| `POST /api/perm/resource-entity/detail` | 查询资源详情 |
| `POST /api/perm/resource-entity/create` | 创建资源 |
| `POST /api/perm/resource-entity/batch-create` | 批量创建资源 |
| `POST /api/perm/resource-entity/update` | 更新资源 |
| `POST /api/perm/resource-entity/move` | 移动资源树节点 |
| `POST /api/perm/resource-entity/remove` | 删除资源，支持批量 |

### 5.4 服务与接口映射

| 接口 | 说明 |
|------|------|
| `POST /api/perm/service-config/list` | 查询接入服务 |
| `POST /api/perm/service-config/detail` | 查询服务详情 |
| `POST /api/perm/service-config/save` | 幂等保存服务 |
| `POST /api/perm/service-config/remove` | 删除服务，支持批量 |
| `POST /api/perm/service-config/sync` | 全量同步服务接口，权限中心做 diff |
| `POST /api/perm/service-config/apis` | 查询服务接口资源树 |
| `POST /api/perm/resource-api-mapping/list` | 查询接口映射 |
| `POST /api/perm/resource-api-mapping/create` | 创建接口映射 |
| `POST /api/perm/resource-api-mapping/update` | 更新接口映射 |
| `POST /api/perm/resource-api-mapping/remove` | 删除接口映射，支持批量 |

### 5.5 授权关系

| 接口 | 说明 |
|------|------|
| `POST /api/perm/user-role/list` | 查询用户角色关系 |
| `POST /api/perm/user-role/assign` | 批量分配角色或分组 |
| `POST /api/perm/user-role/revoke` | 批量回收角色关系 |
| `POST /api/perm/user-role/batch-assign` | 按角色视角批量分配多个用户 |
| `POST /api/perm/role-resource-permission/list` | 查询角色权限配置 |
| `POST /api/perm/role-resource-permission/save` | 三段式批量保存授权，`add/update/remove` 同事务 |
| `POST /api/perm/role-resource-permission/revoke` | 批量回收授权 |
| `POST /api/perm/role-resource-permission/children` | 查询主权限的子权限 |
| `POST /api/perm/role-resource-permission/add-child` | 添加子权限/数据权限 |
| `POST /api/perm/role-resource-permission/remove-child` | 删除子权限 |

### 5.6 高级能力

| 接口 | 说明 |
|------|------|
| `POST /api/perm/permission-condition/list` | 查询权限条件 |
| `POST /api/perm/permission-condition/detail` | 查询条件详情 |
| `POST /api/perm/permission-condition/create` | 创建条件 |
| `POST /api/perm/permission-condition/update` | 更新条件 |
| `POST /api/perm/permission-condition/remove` | 删除条件，支持批量 |
| `POST /api/perm/domain-config/list` | 查询域配置 |
| `POST /api/perm/domain-config/save` | 幂等保存域配置 |
| `POST /api/perm/domain-config/remove` | 删除域配置 |
| `POST /api/perm/resource-dependency/list` | 查询资源依赖 |
| `POST /api/perm/resource-dependency/create` | 创建资源依赖 |
| `POST /api/perm/resource-dependency/update` | 更新资源依赖 |
| `POST /api/perm/resource-dependency/remove` | 删除资源依赖，支持批量 |
| `POST /api/perm/resource-dependency/batch-sync` | 按资源全量同步依赖 |
| `POST /api/perm/resource-dependency/graph` | 查询依赖图 |
| `POST /api/perm/resource-dependency/check` | 检查依赖是否成环 |
| `POST /api/perm/conflict-rule/list` | 查询冲突规则 |
| `POST /api/perm/conflict-rule/create` | 创建冲突规则 |
| `POST /api/perm/conflict-rule/update` | 更新冲突规则 |
| `POST /api/perm/conflict-rule/remove` | 删除冲突规则，支持批量 |
| `POST /api/perm/conflict-rule/detect` | 冲突检测 |

### 5.7 运行时鉴权

| 接口 | 说明 |
|------|------|
| `POST /api/perm/auth/check` | 单次资源权限判定 |
| `POST /api/perm/auth/batch-check` | 批量资源权限判定 |
| `POST /api/perm/auth/check-interface` | Gateway 接口级判定 |
| `POST /api/perm/auth/interface-snapshot` | Gateway 接口权限快照，可选优化接口 |
| `POST /api/perm/permission-version/query` | 查询权限版本 |

### 5.8 视图与审计

| 接口 | 说明 |
|------|------|
| `POST /api/perm/permission-view/effective-roles` | 查询用户有效角色 |
| `POST /api/perm/permission-view/effective-permissions` | 查询用户有效权限 |
| `POST /api/perm/permission-view/resource-tree` | 查询用户资源树 |
| `POST /api/perm/permission-view/resource-users` | 查询拥有资源权限的用户 |
| `POST /api/perm/permission-view/role-permissions` | 查询角色权限视图 |
| `POST /api/perm/permission-view/recent-changes` | 查询近期权限变更 |
| `POST /api/perm/operation-log/list` | 操作日志 |
| `POST /api/perm/permission-change-log/list` | 权限变更日志 |
| `POST /api/perm/system-config/list` | 查询系统配置 |
| `POST /api/perm/system-config/save` | 保存系统配置 |

## 6. 核心请求契约

### 6.1 单次鉴权

`POST /api/perm/auth/check`

```json
{
  "subjectType": 1,
  "subjectExternalId": "u-10001",
  "resourceType": 1,
  "resourceCode": "sys:user",
  "operationCode": "VIEW",
  "domainCode": "admin",
  "codeType": "default",
  "inheritMode": "NONE",
  "includeDataScope": false,
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
  "matchedRoleIds": [10],
  "matchedPermissionIds": [100],
  "conditionEvaluated": false,
  "dataScopes": []
}
```

### 6.2 Gateway 接口级鉴权

`POST /api/perm/auth/check-interface`

```json
{
  "subjectType": 1,
  "subjectExternalId": "u-10001",
  "serviceCode": "admin-service",
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
  "matchedResourceId": 200,
  "matchedOperationCode": "ACCESS",
  "matchedRoleIds": [10],
  "cacheTtlSeconds": 30
}
```

规则：

- 查询 `resource_api_mapping` 必须带 `tenant_id + service_code + http_method + enabled + delete_flag=0`。
- `path` 使用 Gateway 收到的原始路径，不使用 StripPrefix 后的服务内部路径。
- 未注册接口默认拒绝，返回 `API_NOT_REGISTERED`。

### 6.3 服务接口全量同步

`POST /api/perm/service-config/sync`

```json
{
  "serviceCode": "admin-service",
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
          "operationCode": "ACCESS",
          "resourceCode": "admin:user:list",
          "description": "用户列表查询"
        }
      ]
    }
  ]
}
```

规则：

- 首期只支持 `syncMode=FULL`。FULL 模式下，以本次上报内容作为该 `serviceCode` 的完整事实来源。
- 权限中心自动拼接 `basePath + path` 得到 Gateway 原始路径。
- 新接口自动创建 API 类型 `resource_entity` 和 `resource_api_mapping`。
- 已不存在接口软删除映射和自动创建的 API 资源，不删除人工维护的非 API 资源。

### 6.4 三段式角色授权

`POST /api/perm/role-resource-permission/save`

```json
{
  "roleType": 1,
  "roleExternalId": "role_admin",
  "add": [
    {
      "resourceType": 1,
      "resourceCode": "sys:user",
      "codeType": "default",
      "operationCode": "VIEW",
      "canManage": false,
      "conditionCode": null
    }
  ],
  "update": [
    {
      "id": 100,
      "canManage": true,
      "conditionCode": "office-hours"
    }
  ],
  "remove": [101, 102]
}
```

规则：

- `add/update/remove` 在同一事务中完成。
- 角色使用 `roleType + roleExternalId` 定位。
- 授权项使用 `resourceType + resourceCode + codeType + operationCode` 定位资源与操作。
- 操作必须与资源类型兼容。
- 写入 `operation_log` 和 `permission_change_log`，递增 `permission_version`。
- 资源依赖自动补全产生的授权必须标记 `grantSource=AUTO_DEP`。

### 6.5 子权限/数据权限

子权限通过 `role_resource_permission.depend_on` 表达。`depend_on` 指向一条主权限记录的 `id`，表示当前授权依赖该主权限存在。典型场景是：角色拥有"销售报表 VIEW"主权限，同时该主权限下挂"上海数据 DATA_READ"和"杭州数据 DATA_READ"作为数据范围。

第一步，创建或保存主权限。

`POST /api/perm/role-resource-permission/save`

```json
{
  "roleType": 1,
  "roleExternalId": "role_report_viewer",
  "add": [
    {
      "resourceType": 1,
      "resourceCode": "report:sales",
      "codeType": "default",
      "operationCode": "VIEW",
      "canManage": false,
      "conditionCode": null
    }
  ],
  "update": [],
  "remove": []
}
```

响应中的主权限 `id` 用于后续子权限挂载。

```json
{
  "items": [
    {
      "id": 200,
      "resourceType": 1,
      "resourceCode": "report:sales",
      "operationCode": "VIEW",
      "dependOn": null
    }
  ]
}
```

第二步，为主权限添加子权限。

`POST /api/perm/role-resource-permission/add-child`

```json
{
  "parentPermissionId": 200,
  "children": [
    {
      "resourceType": 4,
      "resourceCode": "data:city:shanghai",
      "codeType": "default",
      "operationCode": "DATA_READ",
      "conditionCode": null
    },
    {
      "resourceType": 4,
      "resourceCode": "data:city:hangzhou",
      "codeType": "default",
      "operationCode": "DATA_READ",
      "conditionCode": null
    }
  ]
}
```

响应示例：

```json
{
  "items": [
    {
      "id": 201,
      "resourceType": 4,
      "resourceCode": "data:city:shanghai",
      "operationCode": "DATA_READ",
      "dependOn": 200
    },
    {
      "id": 202,
      "resourceType": 4,
      "resourceCode": "data:city:hangzhou",
      "operationCode": "DATA_READ",
      "dependOn": 200
    }
  ]
}
```

第三步，查询主权限下的子权限。

`POST /api/perm/role-resource-permission/children`

```json
{
  "permissionId": 200
}
```

响应示例：

```json
{
  "items": [
    {
      "id": 201,
      "resourceType": 4,
      "resourceCode": "data:city:shanghai",
      "resourceName": "上海数据",
      "operationCode": "DATA_READ",
      "dependOn": 200
    }
  ]
}
```

鉴权主权限时，若 `includeDataScope=true` 或接口定义要求返回数据范围，子权限会作为 `dataScopes` 返回。

```json
{
  "allowed": true,
  "reason": null,
  "matchedRoleIds": [10],
  "matchedPermissionIds": [200],
  "conditionEvaluated": false,
  "dataScopes": [
    {
      "resourceType": 4,
      "resourceCode": "data:city:shanghai",
      "resourceName": "上海数据",
      "operationCode": "DATA_READ"
    }
  ]
}
```

规则：

- `parentPermissionId` 必须存在于当前租户，且 `depend_on IS NULL`。
- 子权限继承父权限的 `abstract_role_id`，调用方不需要再次传角色。
- 子权限的 `depend_on = parentPermissionId`，只支持一层，不允许子权限继续挂子权限。
- 子权限资源类型必须符合 `domain_config(config_type='SUB_PERM')` 中对当前业务域的配置。
- 删除主权限时，系统必须级联软删 `depend_on` 指向该主权限的所有子权限。
- 删除子权限只能通过 `remove-child` 或主权限级联删除完成。
- 子权限写入、删除都必须记录 `permission_change_log`，并递增父角色的 `permission_version`。

## 7. 错误原因建议

| reason | 说明 |
|--------|------|
| `USER_NOT_FOUND` | 主体不存在 |
| `USER_DISABLED` | 主体停用 |
| `ROLE_DISABLED` | 命中角色停用 |
| `RESOURCE_DISABLED` | 资源停用 |
| `SERVICE_DISABLED` | 服务停用 |
| `NO_ROLE` | 无有效角色 |
| `NO_PERMISSION` | 无授权 |
| `CONDITION_NOT_MET` | 条件不满足 |
| `CONFLICT_DETECTED` | 权限互斥导致失效 |
| `API_NOT_REGISTERED` | 接口未注册 |
| `OBJECT_KEY_NOT_FOUND` | 标准业务键无法定位对象 |
| `OBJECT_KEY_DUPLICATED` | 标准业务键命中多个对象，需修正数据唯一性 |

## 8. 验收标准

- 当 Gateway 使用默认配置回调权限中心时，系统应调用 `POST /api/perm/auth/check-interface` 并得到稳定响应。
- 当外部系统只知道用户 `subjectType + subjectExternalId`、资源 `resourceType + resourceCode`、操作 `operationCode` 时，系统应能完成鉴权判定。
- 当管理端查询任何列表接口时，响应 `data` 应始终是对象，且列表数据位于 `data.items`。
- 当调用批量删除接口时，系统应接受 `{ "ids": [...] }` 并执行软删除，不暴露 RESTful Path 参数。
- 当同一路径映射存在于多个租户时，接口级鉴权应只在 `X-Tenant-Id` 对应租户内匹配。
- 当 SDK 调用权限中心时，Feign 返回类型应与服务端 Controller 响应 DTO 完全一致。
- 当调用任意接口时，请求体不得包含 `tenantId`；租户统一从 `X-Tenant-Id` 读取。

## 9. 实施建议

1. 直接以 `/api/perm/*` 重新实现 permission-center Controller，不保留旧路径兼容。
2. Gateway 默认路径改为 `/api/perm/auth/check-interface`。
3. SDK Feign 改为依赖稳定契约 DTO，返回类型与服务端保持一致。
4. 所有 Request DTO 移除 `tenantId` 字段，服务端从 Header/SecurityContext 获取租户和操作者。
5. 首期 `service-config/sync` 仅实现 FULL 全量同步。

## 10. 已确认决策

1. **租户来源**：只使用 `X-Tenant-Id` 和安全上下文，请求体不保留 `tenantId`。
2. **对象定位**：取消通用 `Ref` 对象，使用固定扁平字段和标准业务键。
3. **授权入口**：三段式角色授权保留，接口命名为 `POST /api/perm/role-resource-permission/save`。
4. **接口同步**：首期仅支持 FULL 全量同步。
5. **兼容策略**：项目未上线，不考虑旧接口兼容，直接按新契约实现。
