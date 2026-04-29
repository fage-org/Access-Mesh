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
| 运行时鉴权/权限查询 API | `/api/perm/auth/*` | Gateway、业务服务 SDK | 高 QPS、可缓存、强稳定 |
| 服务接入 API | `/api/perm/service-config/*` | 接入服务、SDK Starter、管理端 | 服务注册、接口同步、接口资源树 |

> 不再定义 `/internal/perm/*` 主契约；本项目未上线，后续实现直接以 `/api/perm/*` 为准。

## 3. 通用协议

### 3.1 Header

| Header | 必填 | 说明 |
|--------|------|------|
| `Authorization` | 管理 API 必填 | `Bearer <token>` |
| `X-Tenant-Id` | 必填 | 当前租户 ID，由 Gateway 或可信服务注入；请求体不再保留 `tenantId` |
| `X-Request-Id` | 可选 | 未传时由 Gateway 生成 |
| `X-Service-Code` | 内部/SDK 必填 | 调用方服务编码，用于内部来源校验 |
| `X-Api-Version` | 可选 | 契约版本，默认 `2026-04-26` |

可信边界：

- 外部客户端传入的 `X-Tenant-Id/X-User-Id/X-Service-Code` 必须由 Gateway 清洗，不允许原样透传。
- Gateway 从 Token claim 解析租户和主体后重新注入标准 Header。
- 服务间调用通过 Feign 拦截器透传可信 Header；permission-center 需校验服务身份与 Header 一致性。

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
| 用户/主体 | `subjectTypeCode` + `subjectExternalId` | `subjectTypeCode` 对应 `type_definition(type_key='user_type').type_code` |
| 角色 | `domainCode` + `roleTypeCode` + `roleExternalId` | 对外接口使用外部角色标识；可被外部调用分配/授权的角色必须有 `externalId` |
| 业务域 | `domainCode` | 可空；为空表示全局域 |
| 资源 | `domainCode` + `resourceTypeCode` + `resourceCode` + `codeType` | `codeType` 默认 `default` |
| 操作 | `operationCode` | 在 `resourceTypeCode` 范围内解析；全局操作允许不绑定资源类型 |
| 条件 | `conditionCode` | 可空 |
| 明细记录 | `id` 或 `ids` | 仅用于更新/删除权限关系、日志详情等权限中心已返回的记录 |

原则：

- 运行时鉴权接口不要求调用方传内部 `id`。
- 管理端列表、创建、详情响应可以返回内部 `id`，用于后续 `update/remove`。
- 同一接口不同时接受 `id/code/externalId` 多套定位方式，避免歧义。
- 所有请求体禁止出现 `tenantId`；服务端统一从 `X-Tenant-Id` 和安全上下文读取租户。
- 对外 API 使用稳定字符串 `typeCode`；数据库实体继续保存 `type_value INT`，由服务端通过缓存解析，避免外部系统依赖内部数字枚举。
- `type_value` 在同一 `tenant_id + type_key` 内全局唯一，不随 `domainCode/biz_domain_id` 重复；`type_code` 仍可按业务域和全局分别定义。
- `domainCode` 是管理分区和命名空间，不是子租户。传入 `domainCode` 时只查该域和全局对象；不传时只查全局对象，不做跨域模糊匹配。
- Gateway 必须清洗外部伪造的 `X-Tenant-Id/X-User-Id/X-Service-Code`，再基于 Token 或可信服务身份重新注入；permission-center 不信任客户端原始 Header。

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
| `query` | 运行时权限事实查询，返回可访问资源或范围权限集合 |
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

### 5.7 运行时鉴权与权限查询

| 接口 | 说明 |
|------|------|
| `POST /api/perm/auth/check` | 单次资源权限判定 |
| `POST /api/perm/auth/batch-check` | 批量资源权限判定 |
| `POST /api/perm/auth/query-resources` | 查询主体在指定资源类型和操作下可访问或可管理的资源集合 |
| `POST /api/perm/auth/query-scopes` | 查询主体在某个主资源上下文内可用的范围资源权限集合 |
| `POST /api/perm/auth/check-interface` | Gateway 接口级判定 |
| `POST /api/perm/auth/interface-snapshot` | Gateway 接口权限快照，可选优化接口 |
| `POST /api/perm/permission-version/query` | 查询权限版本 |

### 5.8 视图与审计

| 接口 | 说明 |
|------|------|
| `POST /api/perm/permission-view/effective-roles` | 查询用户有效角色 |
| `POST /api/perm/permission-view/effective-permissions` | 分页筛选查询用户或角色当前有效权限 |
| `POST /api/perm/permission-view/resource-tree` | 查询用户资源树 |
| `POST /api/perm/permission-view/resource-users` | 查询拥有资源权限的用户 |
| `POST /api/perm/permission-view/role-permissions` | 查询角色权限视图 |
| `POST /api/perm/permission-view/explain` | 解释单个用户或角色对某资源操作的当前权限和近期影响事件 |
| `POST /api/perm/permission-view/recent-changes` | 查询近期可能影响用户或角色权限的变更事件 |
| `POST /api/perm/operation-log/list` | 操作日志 |
| `POST /api/perm/permission-change-log/list` | 权限变更日志 |
| `POST /api/perm/system-config/list` | 查询系统配置 |
| `POST /api/perm/system-config/save` | 保存系统配置 |

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
  "conditionEvaluated": false
}
```

### 6.2 Gateway 接口级鉴权

`POST /api/perm/auth/check-interface`

```json
{
  "subjectTypeCode": "USER",
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
  "matchedResources": [
    {
      "resourceId": 200,
      "resourceTypeCode": "API",
      "resourceCode": "admin:user:list",
      "operationCode": "ACCESS",
      "allowed": true,
      "matchedRoleIds": [10],
      "matchedPermissionIds": [100]
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
- `service-config/sync` 自动创建的 API 资源必须写入 `resource_entity.ownerServiceCode=serviceCode`、`maintainSource=SERVICE_SYNC`、`syncKey`。
- FULL diff 只能软删除同一 `ownerServiceCode + maintainSource=SERVICE_SYNC` 范围内本次缺失的 API 映射和自动创建资源。
- 已不存在接口软删除映射和自动创建的 API 资源，不删除 `maintainSource=MANUAL` 或其他维护来源的资源。

### 6.4 三段式角色授权

`POST /api/perm/role-resource-permission/save`

```json
{
  "domainCode": "admin",
  "roleTypeCode": "BASIC_ROLE",
  "roleExternalId": "role_admin",
  "add": [
    {
      "resourceTypeCode": "MENU",
      "resourceCode": "sys:user",
      "codeType": "default",
      "operationCode": "VIEW",
      "scopeAll": false,
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
- 角色使用 `domainCode + roleTypeCode + roleExternalId` 定位；`domainCode` 为空时只定位全局角色。
- 授权项使用 `domainCode + resourceTypeCode + resourceCode + codeType + operationCode` 定位资源与操作。
- 当授权项 `scopeAll=true` 时，使用 `resourceTypeCode + operationCode` 表达该资源类型的全量范围权限，不传 `resourceCode/codeType`。
- 操作必须与资源类型兼容。
- `canManage=true` 表示授权者可把同一条权限授权给他人，但不得扩大资源、操作或范围；可授权对象列表由业务服务控制。
- 授权者必须已经拥有目标权限且该权限 `canManage=true`，才能把同一权限授权给他人。
- 对范围权限，授权者只能授权自己已有的范围；拥有 `scopeAll=true` 才能授权全量范围。
- permission-center 只校验授权者是否具备同一权限的委托能力，不负责生成候选被授权人列表。
- 写入 `operation_log` 和 `permission_change_log`，递增 `permission_version`。
- 资源依赖自动补全产生的授权必须标记 `grantSource=AUTO_DEP`。

### 6.5 子权限/范围权限

子权限通过 `role_resource_permission.depend_on` 表达。`depend_on` 指向一条主权限记录的 `id`，表示当前授权依赖该主权限存在。典型场景是：角色拥有"销售报表 DATA_READ"主权限，同时该主权限下挂"上海数据 DATA_READ"和"杭州数据 DATA_READ"作为范围权限。

第一步，创建或保存主权限。

`POST /api/perm/role-resource-permission/save`

```json
{
  "domainCode": "example",
  "roleTypeCode": "BASIC_ROLE",
  "roleExternalId": "role_report_viewer",
  "add": [
    {
      "resourceTypeCode": "REPORT",
      "resourceCode": "report:sales",
      "codeType": "default",
      "operationCode": "DATA_READ",
      "scopeAll": false,
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
      "resourceTypeCode": "REPORT",
      "resourceCode": "report:sales",
      "operationCode": "DATA_READ",
      "scopeAll": false,
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
      "resourceTypeCode": "DATA",
      "resourceCode": "data:city:shanghai",
      "codeType": "default",
      "operationCode": "DATA_READ",
      "scopeAll": false,
      "conditionCode": null
    },
    {
      "resourceTypeCode": "DATA",
      "resourceCode": "data:city:hangzhou",
      "codeType": "default",
      "operationCode": "DATA_READ",
      "scopeAll": false,
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
      "resourceTypeCode": "DATA",
      "resourceCode": "data:city:shanghai",
      "operationCode": "DATA_READ",
      "scopeAll": false,
      "dependOn": 200
    },
    {
      "id": 202,
      "resourceTypeCode": "DATA",
      "resourceCode": "data:city:hangzhou",
      "operationCode": "DATA_READ",
      "scopeAll": false,
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
      "resourceTypeCode": "DATA",
      "resourceCode": "data:city:shanghai",
      "resourceName": "上海数据",
      "operationCode": "DATA_READ",
      "scopeAll": false,
      "dependOn": 200
    }
  ]
}
```

运行时不要通过 `auth/check` 承载范围集合。`auth/check` 只做主权限布尔判定；业务需要范围权限集合时，调用 `POST /api/perm/auth/query-scopes`，由该接口合并直接范围权限和依赖当前主权限的子权限。

全量范围授权项示例，可出现在 `role-resource-permission/save.add` 或 `role-resource-permission/add-child.children` 中：

```json
{
  "resourceTypeCode": "DATA",
  "operationCode": "DATA_EDIT",
  "scopeAll": true,
  "canManage": false,
  "conditionCode": null
}
```

规则：

- `parentPermissionId` 必须存在于当前租户，且 `depend_on IS NULL`。
- 子权限继承父权限的 `abstract_role_id`，调用方不需要再次传角色。
- 子权限的 `depend_on = parentPermissionId`，只支持一层，不允许子权限继续挂子权限。
- 子权限资源类型必须符合 `domain_config(config_type='SUB_PERM')` 中对当前业务域的配置。
- `scopeAll=true` 表示该授权覆盖 `resourceTypeCode` 下全部资源；此时请求不传 `resourceCode/codeType`，运行时响应通过 `scopeAll=true` 明确表达全量范围。
- 删除主权限时，系统必须级联软删 `depend_on` 指向该主权限的所有子权限。
- 删除子权限只能通过 `remove-child` 或主权限级联删除完成。
- 子权限写入、删除都必须记录 `permission_change_log`，并递增父角色的 `permission_version`。

### 6.6 通用资源权限查询

`POST /api/perm/auth/query-resources`

用于业务服务查询某个主体在指定资源类型和操作下的有效权限集合。典型场景包括 admin-service 查询用户能管理哪些组织、哪些角色、哪些菜单。前提是这些业务对象已经作为 `resource_entity` 同步或创建到权限中心。

请求：

```json
{
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001",
  "domainCode": "admin",
  "resourceTypeCodes": ["ORG"],
  "operationCodes": ["MANAGE"],
  "codeType": "default",
  "includeInherited": true,
  "includeChildren": false,
  "treeMode": false,
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
      "resourceCode": "org:100",
      "resourceName": "研发中心",
      "codeType": "default",
      "operations": ["MANAGE"],
      "canManage": true,
      "matchedRoleIds": [10, 11],
      "matchedPermissionIds": [301, 315],
      "grantSources": ["MANUAL"]
    }
  ],
  "permissionVersion": "u-10001:42",
  "cacheTtlSeconds": 60
}
```

admin-service 查询示例：

| 查询目标 | 建模方式 | 查询参数 |
|----------|----------|----------|
| 可管理组织 | 组织同步为资源，例如 `resourceTypeCode=ORG`、`resourceCode=org:{orgId}` | `resourceTypeCodes=["ORG"]`、`operationCodes=["MANAGE"]` |
| 可管理角色 | 角色同步为资源，例如 `resourceTypeCode=ROLE`、`resourceCode=role:{roleExternalId}` | `resourceTypeCodes=["ROLE"]`、`operationCodes=["MANAGE"]` 或 `["ASSIGN"]` |
| 可见菜单 | 菜单同步为资源，例如 `resourceTypeCode=MENU`、`resourceCode=menu:{menuCode}` | `resourceTypeCodes=["MENU"]`、`operationCodes=["VIEW"]`、`treeMode=true` |

规则：

- 查询接口只返回权限事实和资源业务键，不查询 admin-service 的组织、角色、菜单业务表。
- 调用方拿到 `resourceCode` 后，由业务服务映射成本服务内的组织树、角色列表或菜单树。
- 多个角色命中同一资源时，按 `resourceTypeCode + resourceCode + codeType` 去重，并合并 `operations`、`matchedRoleIds`、`matchedPermissionIds`。
- 条件、冲突规则、停用状态、角色继承、资源继承必须与 `auth/check` 使用同一套计算逻辑。
- `treeMode=true` 只基于权限中心保存的资源父子关系组装树；业务排序、展示字段仍由业务服务决定。
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

响应：

```json
{
  "allowed": true,
  "reason": null,
  "matchedParentOperations": ["DATA_READ", "DATA_EDIT"],
  "parentPermissionIds": [200, 260],
  "items": [
    {
      "resourceTypeCode": "DATA",
      "resourceCode": "data:dept:A",
      "resourceName": "A部门数据",
      "codeType": "default",
      "scopeAll": false,
      "operations": ["DATA_READ", "DATA_EDIT"],
      "sources": ["DIRECT"],
      "matchedRoleIds": [10],
      "matchedPermissionIds": [301, 302],
      "dependOnPermissionIds": []
    },
    {
      "resourceTypeCode": "DATA",
      "resourceCode": "data:dept:B",
      "resourceName": "B部门数据",
      "codeType": "default",
      "scopeAll": false,
      "operations": ["DATA_READ"],
      "sources": ["DEPENDENT"],
      "matchedRoleIds": [12],
      "matchedPermissionIds": [401],
      "dependOnPermissionIds": [200]
    },
    {
      "resourceTypeCode": "DATA",
      "resourceCode": null,
      "resourceName": null,
      "codeType": null,
      "scopeAll": true,
      "operations": ["DATA_EDIT"],
      "sources": ["DIRECT"],
      "matchedRoleIds": [15],
      "matchedPermissionIds": [501],
      "dependOnPermissionIds": []
    }
  ],
  "mergeMode": "UNION",
  "permissionVersion": "u-10001:42",
  "cacheTtlSeconds": 60
}
```

规则：

- 权限中心先按 `parentResourceTypeCode + parentResourceCode + parentCodeType + parentOperationCodes[]` 执行主权限判定。
- 主权限全部不通过时，返回 `allowed=false`、`items=[]`，不继续返回范围权限。
- `DIRECT` 范围权限来自当前主体有效角色下 `depend_on IS NULL` 的范围资源授权。
- `DEPENDENT` 范围权限来自 `depend_on IN parentPermissionIds` 的子权限授权，只在当前主资源上下文内生效。
- 有效范围权限计算公式为 `effectiveScopes = DIRECT ∪ DEPENDENT`。
- 多操作查询按范围资源聚合，按 `scopeAll + resourceTypeCode + resourceCode + codeType` 去重，并合并 `operations/sources/matchedPermissionIds`。
- 范围操作必须被至少一个已通过的主操作激活。推荐在 example-service 中使用同名业务数据动作，例如 `report:sales + DATA_READ -> dept + DATA_READ`、`report:sales + DATA_EDIT -> dept + DATA_EDIT`；这只是推荐范例，不作为所有接入系统的强制标准。
- 如果主操作和范围操作不是同名关系，应通过域配置声明映射规则；未配置映射时，默认只做同名操作匹配。
- `scopeAll=true` 表示该 `resourceTypeCode` 下全量范围权限，例如 `DATA_EDIT + DEPT + scopeAll=true` 表示可编辑全部部门范围；实现不应展开返回全部部门明细。
- `items=[]` 不表示全量范围，只表示没有显式范围权限；全量必须通过 `scopeAll=true` 明确表达。
- 权限中心只返回范围权限事实，不生成 SQL、不解释业务字段；业务服务自行把 `resourceCode` 或 `scopeAll=true` 映射为查询条件。

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
      "scopeAll": false,
      "sourceRoles": [
        {
          "roleTypeCode": "BASIC_ROLE",
          "roleExternalId": "role_report_viewer",
          "roleName": "报表查看员",
          "via": ["GROUP_ROLE:finance_admin"]
        }
      ],
      "sourceRoleCount": 1,
      "sourceRolesTruncated": false,
      "matchedPermissionIds": [200]
    }
  ],
  "total": 1,
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
  "includeSourceRoles": true,
  "includeRecentChanges": true,
  "recentDays": 30
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
    "scopeAll": false
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
  ]
}
```

规则：

- `explain` 只解释一个资源和一个操作，不返回权限列表。
- `allowed/reason` 应复用 `auth/check` 的主体、角色、资源、操作、条件、冲突计算逻辑。
- 用户视角需要返回命中的来源角色；未命中时返回拒绝原因和相关近期影响事件。
- `includeRecentChanges=true` 时，只返回与目标权限键相关的近期事件；默认窗口为 30 天，服务端可限制最大窗口。
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
  "eventTypes": ["USER_ROLE_CHANGE", "ROLE_PERMISSION_CHANGE", "ROLE_STATUS_CHANGE", "RESOURCE_STATUS_CHANGE", "CONDITION_CHANGE"],
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
        "scopeAll": false
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
  "total": 1,
  "pageNum": 1,
  "pageSize": 20,
  "hasNext": false
}
```

规则：

- `recent-changes` 返回的是“可能影响目标权限的变更事件”，不是目标有效权限的精确历史 diff。
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
        "scopeAll": false
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
- `eventType` 固定枚举：`USER_ROLE_CHANGE`、`ROLE_PERMISSION_CHANGE`、`ROLE_STATUS_CHANGE`、`RESOURCE_STATUS_CHANGE`、`CONDITION_CHANGE`、`GROUP_ROLE_CHANGE`、`RESOURCE_DEPENDENCY_CHANGE`。
- `items[].changeType` 固定枚举：`ADD`、`REMOVE`、`UPDATE`。
- `recent-changes` 响应中的 `impactLevel` 固定枚举：`DIRECT` 表示直接命中查询对象，`POSSIBLE` 表示通过角色、资源、条件、分组等间接关系可能影响查询对象。
- 权限项使用稳定业务键：`domainCode + resourceTypeCode + resourceCode + codeType + operationCode + scopeAll`。
- 用户或角色来源使用稳定业务键，不要求在 `diff_snapshot` 中暴露内部 ID；内部 ID 可保留在 `old_snapshot/new_snapshot/entity_id` 中用于审计追溯。
- `old_snapshot/new_snapshot` 继续保存原始变更前后快照；`diff_snapshot` 只保存排查展示需要的摘要。

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
      "autoGrant": true,
      "description": "授权销售报表读取时自动补齐查询接口"
    }
  ]
}
```

规则：

- `source*` 表示源资源，即被授权后会触发依赖补全的资源，对应 `resource_dependency.resource_entity_id`。
- `target*` 表示被源资源依赖、需要自动补全的目标资源，对应 `resource_dependency.depends_on_resource_entity_id`。
- 授权源资源时，自动补全查询条件必须是 `resource_dependency.resource_entity_id = sourceResourceId`，不能反向使用 `depends_on_resource_entity_id` 查询。
- `sourceOperationCodes` 转为 `source_operation_bits`；为空表示任意源操作触发。
- `requiredOperationCodes` 转为 `required_operation_bits`，表示目标资源需要自动补全的操作。
- FULL diff 只清理同一 `ownerServiceCode=serviceCode + maintainSource` 范围内本次缺失的依赖规则，不清理其他服务或其他维护来源的规则。
- 同一语义依赖仍受 `tenant_id + resource_entity_id + depends_on_resource_entity_id + source_operation_bits` 唯一约束保护，避免不同来源重复创建同一条依赖。

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
- 当外部系统只知道用户 `subjectTypeCode + subjectExternalId`、资源 `resourceTypeCode + resourceCode`、操作 `operationCode` 时，系统应能完成鉴权判定。
- 当管理端查询任何列表接口时，响应 `data` 应始终是对象，且列表数据位于 `data.items`。
- 当调用批量删除接口时，系统应接受 `{ "ids": [...] }` 并执行软删除，不暴露 RESTful Path 参数。
- 当同一路径映射存在于多个租户时，接口级鉴权应只在 `X-Tenant-Id` 对应租户内匹配。
- 当 SDK 调用权限中心时，Feign 返回类型应与服务端 Controller 响应 DTO 完全一致。
- 当调用任意接口时，请求体不得包含 `tenantId`；租户统一从 `X-Tenant-Id` 读取。
- 当 admin-service 查询用户能管理哪些组织、角色、菜单时，应通过 `POST /api/perm/auth/query-resources` 返回资源业务键集合。
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
3. **授权入口**：三段式角色授权保留，接口命名为 `POST /api/perm/role-resource-permission/save`。
4. **接口同步**：首期仅支持 FULL 全量同步。
5. **兼容策略**：项目未上线，不考虑旧接口兼容，直接按新契约实现。
6. **运行时查询**：SDK 除布尔鉴权外，需要提供通用资源查询和范围权限查询；查询结果返回权限事实，不返回业务服务私有数据。
7. **范围权限**：`query-scopes = DIRECT 直接范围权限 ∪ DEPENDENT 子权限范围权限`，并支持 `parentOperationCodes[]` 与 `scopeOperationCodes[]` 多操作查询。
8. **全量范围**：`role_resource_permission` 增加 `scope_all` 字段，`scopeAll=true` 显式表示某资源类型下的全量范围权限；空 `items=[]` 不表示全量。
9. **类型模型**：对外 API 使用 `subjectTypeCode/resourceTypeCode/roleTypeCode`，内部存储继续使用 `type_value INT`，通过 `type_definition` 缓存解析；`type_value` 在同一 `tenant_id + type_key` 内全局唯一。
10. **业务域模型**：`domainCode` 是管理分区和命名空间；传入时查该域 + 全局，不传时只查全局，不跨域模糊匹配。
11. **接口映射**：同一路径允许映射多个接口资源，Gateway 接口鉴权采用 OR 语义，任一映射资源权限通过即允许。
12. **委托授权**：`canManage=true` 表示可把同一条权限授权给他人，但不得扩大资源、操作或范围；被授权对象候选范围由业务服务控制。
13. **资源依赖方向**：`resource_dependency.resource_entity_id` 是源资源/被授权资源，`depends_on_resource_entity_id` 是被源资源依赖、需要自动补全的目标资源。
14. **同步所有权**：服务接口同步和资源依赖同步必须通过 `ownerServiceCode + maintainSource + syncKey` 限定 FULL diff 删除范围。
15. **变更摘要枚举**：`diff_snapshot.eventType`、`items[].changeType` 和 `recent-changes.impactLevel` 使用固定枚举，不使用开放字符串。
