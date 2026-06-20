# Permission Center 外部 API 契约草案

> 本文档定义 permission-center 对外稳定接口契约。目标是让权限中心既能服务 AccessMesh 内部 Gateway/SDK，又能作为通用权限管理服务暴露给外部业务系统。

> **全局注记（2026-06-20 审计 S-001）**：本文档中出现的 `permissionVersion` 字段均为**不透明令牌**（ETag 语义），由服务端对当前主体权限集合计算内容摘要生成，**不依赖 `permission_version` 表**（该表已决策删除，见 design-review §A'-3 + v3.5 §9.2）。

> **scopeAll → scopeMode 迁移注记（2026-06-20 审计 S-005=A）**：本文档中约 30+ 处 `scopeAll` (boolean) 字段计划全量迁移到 `scopeMode` 三值枚举（INSTANCE/ALL/NONE），覆盖运行时鉴权口 + 管理端授权配置 + 排查页（design-review §B-1 决策 + 审计 S-005=A）。**当前文档暂未逐处改造**，与工作单 B（落地暂缓，见 design-review §11）绑定，待工作单 B 派生 plan 时随代码一并落地。落地前 `scopeAll` 字段维持现状语义。

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
| 管理配置 API            | `/api/perm/*`                | 管理端、admin-service、接入系统后台 | 资源、角色、授权、条件、域配置、日志 |
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

| 对象      | 标准入参字段                                                    | 说明                                                                     |
| --------- | --------------------------------------------------------------- | ------------------------------------------------------------------------ |
| 租户      | `X-Tenant-Id`                                                   | 只放 Header，不放 Body                                                   |
| 用户/主体 | `subjectTypeCode` + `subjectExternalId`                         | `subjectTypeCode` 对应 `type_definition(type_key='user_type').type_code` |
| 角色      | `domainCode` + `roleTypeCode` + `roleExternalId`                | 对外接口使用外部角色标识；可被外部调用分配/授权的角色必须有 `externalId` |
| 业务域    | `domainCode`                                                    | 可空；为空表示全局域                                                     |
| 资源      | `domainCode` + `resourceTypeCode` + `resourceCode` + `codeType` | `codeType` 默认 `default`                                                |
| 操作      | `operationCode`                                                 | 在 `resourceTypeCode` 范围内解析；全局操作允许不绑定资源类型             |
| 条件      | `conditionCode`                                                 | 可空                                                                     |
| 明细记录  | `id` 或 `ids`                                                   | 仅用于更新/删除权限关系、日志详情等权限中心已返回的记录                  |

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
| `POST /api/perm/abstract-role/extra-roles/list`   | 查询分组角色额外基本角色 |
| `POST /api/perm/abstract-role/extra-roles/add`    | 分组角色添加基本角色     |
| `POST /api/perm/abstract-role/extra-roles/remove` | 分组角色移除基本角色     |

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

### 5.4 服务与接口映射

| 接口                                         | 说明                              |
| -------------------------------------------- | --------------------------------- |
| `POST /api/perm/service-config/list`         | 查询接入服务                      |
| `POST /api/perm/service-config/detail`       | 查询服务详情                      |
| `POST /api/perm/service-config/save`         | 幂等保存服务                      |
| `POST /api/perm/service-config/remove`       | 删除服务，支持批量                |
| `POST /api/perm/service-config/sync`         | 全量同步服务接口，权限中心做 diff |
| `POST /api/perm/service-config/apis`         | 查询服务接口资源树                |
| `POST /api/perm/resource-api-mapping/list`   | 查询接口映射                      |
| `POST /api/perm/resource-api-mapping/create` | 创建接口映射                      |
| `POST /api/perm/resource-api-mapping/update` | 更新接口映射                      |
| `POST /api/perm/resource-api-mapping/remove` | 删除接口映射，支持批量            |

### 5.5 授权关系

| 接口                                                   | 说明                                           |
| ------------------------------------------------------ | ---------------------------------------------- |
| `POST /api/perm/user-role/list`                        | 查询用户角色关系                               |
| `POST /api/perm/user-role/sync`                        | 幂等同步组织/岗位用户角色关系                   |
| `POST /api/perm/user-role/full-sync`                   | 按 scope 全量校准组织/岗位用户角色关系           |
| `POST /api/perm/user-role/assign`                      | 批量分配角色或分组                             |
| `POST /api/perm/user-role/revoke`                      | 批量回收角色关系                               |
| `POST /api/perm/user-role/batch-assign`                | 按角色视角批量分配多个用户                     |
| `POST /api/perm/role-resource-permission/list`         | 查询角色权限配置                               |
| `POST /api/perm/role-resource-permission/save`         | 三段式批量保存授权，`add/update/remove` 同事务 |
| `POST /api/perm/role-resource-permission/revoke`       | 批量回收授权                                   |
| `POST /api/perm/role-resource-permission/children`     | 查询主权限的子权限                             |
| `POST /api/perm/role-resource-permission/add-child`    | 添加子权限/数据权限                            |
| `POST /api/perm/role-resource-permission/remove-child` | 删除子权限                                     |

### 5.6 高级能力

| 接口                                            | 说明                   |
| ----------------------------------------------- | ---------------------- |
| `POST /api/perm/permission-condition/list`      | 查询权限条件           |
| `POST /api/perm/permission-condition/detail`    | 查询条件详情           |
| `POST /api/perm/permission-condition/create`    | 创建条件               |
| `POST /api/perm/permission-condition/update`    | 更新条件               |
| `POST /api/perm/permission-condition/remove`    | 删除条件，支持批量     |
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

### 5.7 运行时鉴权与权限查询

| 接口                                      | 说明                                                   |
| ----------------------------------------- | ------------------------------------------------------ |
| `POST /api/perm/auth/check`               | 单次资源权限判定                                       |
| `POST /api/perm/auth/batch-check`         | 批量资源权限判定                                       |
| `POST /api/perm/auth/query-resources`     | 查询主体在指定资源类型和操作下可访问或可管理的资源集合 |
| `POST /api/perm/auth/query-scopes`        | 查询主体在某个主资源上下文内可用的范围资源权限集合     |
| `POST /api/perm/auth/check-interface`     | Gateway 接口级判定                                     |
| `POST /api/perm/auth/interface-snapshot`  | Gateway 接口权限快照，可选优化接口                     |

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
| `POST /api/perm/operation-log/list`                    | 操作日志                                               |
| `POST /api/perm/permission-change-log/list`            | 权限变更日志                                           |
| `POST /api/perm/system-config/list`                    | 查询系统配置                                           |
| `POST /api/perm/system-config/detail`                  | 查询系统配置详情                                       |
| `POST /api/perm/system-config/save`                    | 保存系统配置                                           |

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

### 6.2.1 Gateway 接口权限快照

`POST /api/perm/auth/interface-snapshot`

用于 Gateway 按服务拉取当前主体可访问的 API 快照。`permissionVersion` 是一个不透明字符串令牌，由权限中心根据当前有效角色集合和各角色权限摘要生成。

> **令牌来源（2026-06-20 审计 S-001）**：`permissionVersion` 令牌**不依赖 `permission_version` 表**（该表已决策删除，见 design-review §A'-3 + v3.5 §9.2）。令牌由服务端对当前主体有效权限集合计算内容摘要生成（如 `sha256(subjectTypeCode + subjectExternalId + serviceCode + sorted(allowedApis))`），作为不透明 ETag 使用。建议服务端将令牌与权限快照缓存到同一 Redis key（同失效），避免每次重算。

请求：

```json
{
  "subjectTypeCode": "USER",
  "subjectExternalId": "u-10001",
  "serviceCode": "admin-service",
  "permissionVersion": "perm:v2:6f4f10cfa4a99f4d8d7a7f0f97b2d6d4c796f8d5d5b154d5c55f9e42d16dc3c4"
}
```

响应：

```json
{
  "notModified": false,
  "permissionVersion": "perm:v2:6f4f10cfa4a99f4d8d7a7f0f97b2d6d4c796f8d5d5b154d5c55f9e42d16dc3c4",
  "allowedApis": [
    {
      "serviceCode": "admin-service",
      "httpMethod": "POST",
      "pathPattern": "/api/user/list",
      "hasCondition": false,
      "conditionId": null,
      "scopeAll": false
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
    }
  ]
}
```

规则：

- 服务端必须先计算当前 `permissionVersion`，再决定是否返回 `notModified=true`。
- 快照缓存键必须至少包含 `serviceCode + permissionVersion`，不能只按 `serviceCode` 共享。
- `permissionVersion` 是不透明令牌，调用方只能回传比较，不能解析其内部结构。
- 当 `req.permissionVersion == resp.permissionVersion` 时，服务端可返回 `notModified=true` 且 `allowedApis=[]`。
- 当权限令牌变化时，服务端必须重新构建或读取新键下的快照，旧快照不能复用。
- `scopeAll=true` 的条目表示角色对该服务全部 API 拥有权限，`httpMethod` 和 `pathPattern` 为 null。调用方自行根据 `hasCondition`/`conditionId` 决定是否放行——服务端不展开 scopeAll 为逐条 API。实例级条目（`scopeAll=false`）仍按 `httpMethod + pathPattern` 精确匹配。

### 6.2.2 资源实体专用同步

`POST /api/perm/resource-entity/sync`

用于外部事实源把业务对象幂等同步为 permission-center 的 `resource_entity`。该接口只处理资源实体，不是跨实体万能 replay 入口；不接受 `entityType + operationType + payload` 形式。

> 服务间认证：现有文档仅定义内部 Feign 携带 `X-Service-Code`，尚未固化 Sa-Token 服务间认证、签名、nonce 和防重放契约。同步写接口实现前必须补充服务间认证设计；在该设计完成前，permission-center 不得只信任请求体 `sourceService`，至少必须校验可信 Header 中的服务身份与 `sourceService` 一致。

请求：

```json
{
  "operation": "UPSERT",
  "resourceTypeCode": "ADMIN_ORG",
  "resourceCode": "2001",
  "codeType": "default",
  "name": "研发部",
  "parentResourceTypeCode": "ADMIN_ORG",
  "parentResourceCode": "1000",
  "path": null,
  "status": 1,
  "sortOrder": 10,
  "extra": {
    "orgType": "ORG",
    "source": "admin-service"
  },
  "sourceService": "admin-service",
  "sourceEntityType": "sys_org",
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
- 父资源使用 `parentResourceTypeCode + parentResourceCode` 业务键定位，permission-center 内部解析为 `parentId`；父资源不存在时返回 `retryClass=DEPENDENCY_MISSING`，调用方可按短退避重发。
- 调用方必须通过可信 Header 提供服务身份；permission-center 必须校验认证服务身份、`sourceService`、`resourceTypeCode` 白名单，禁止任意服务同步任意资源类型。
- admin-service 的 `PERM_RESOURCE_ENTITY_SYNC` 同步任务统一调用本接口；用户、角色、用户角色关系不走本接口。
- admin-service 的全量校准同步走 `resource-entity/full-sync`，不是逐条调用本接口。

#### 6.2.2.1 资源实体分领域全量校准

`POST /api/perm/resource-entity/full-sync`

请求体必须携带强制 scope，permission-center 只在该 scope 对应的同步来源范围内做差异校准，禁止默认按租户全量清理。

```json
{
  "scope": {
    "sourceService": "admin-service",
    "resourceTypeCode": "ADMIN_ORG"
  },
  "items": [
    {
      "resourceCode": "2001",
      "codeType": "default",
      "name": "研发部",
      "parentResourceTypeCode": "ADMIN_ORG",
      "parentResourceCode": "1000",
      "parentCodeType": "default",
      "status": 1,
      "sortOrder": 10,
      "extra": {
        "orgType": "ORG"
      },
      "sourceEntityType": "sys_org",
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
- permission-center 以 `sync_metadata(entityKind=RESOURCE_ENTITY, sourceService, scopeKey)` 作为 full-sync ownership 范围；请求中存在则 upsert 并更新 metadata，请求中缺失的 metadata 对应事实按删除语义软删除。`resource_entity.owner_service_code/maintain_source/sync_key` 不作为本接口的清理依据。
- `resource-entity/full-sync` 与既有 `service-config/sync` 是两条独立 ownership 通道。本接口只清理命中 `sync_metadata` scope 的同步事实，绝不按 `resourceTypeCode` 扫描删除资源，也不删除 `service-config/sync`、`MANUAL` 或其他维护来源创建的事实。
- 全量接口仍必须执行 source 白名单校验和旧版本 no-op 规则。
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

admin-service 的非资源实体同步使用专用 sync/full-sync 接口，不通过 `resource-entity/sync`，也不复用角色授权管理接口表达同步语义。

| 接口 | syncAction | scopeKey / businessKey |
|------|------------|----------------|
| `POST /api/perm/abstract-user/sync` | `PERM_ABSTRACT_USER_SYNC` | businessKey：`subjectTypeCode={subjectTypeCode}&subjectExternalId={subjectExternalId}` |
| `POST /api/perm/abstract-user/full-sync` | 全量主体校准 | scopeKey：`subjectTypeCode={subjectTypeCode}` |
| `POST /api/perm/abstract-role/sync` | `PERM_ABSTRACT_ROLE_SYNC` | businessKey：`roleTypeCode={roleTypeCode}&roleExternalId={roleExternalId}` |
| `POST /api/perm/abstract-role/full-sync` | 全量角色校准 | scopeKey：`roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |
| `POST /api/perm/user-role/sync` | `PERM_USER_ROLE_SYNC` | businessKey：`subjectTypeCode={subjectTypeCode}&subjectExternalId={subjectExternalId}&roleTypeCode={roleTypeCode}&roleExternalId={roleExternalId}&relationKey={relationKey}` |
| `POST /api/perm/user-role/full-sync` | 全量组织/岗位成员校准 | scopeKey：`sourceType=SYS_USER_ORG&roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |

约束：

- `PERM_USER_ROLE_SYNC` 仅允许 `sourceType=SYS_USER_ORG` 且 `roleTypeCode in (ORG, POSITION)`；BASIC_ROLE/GROUP_ROLE/PERSONAL 等功能角色分配走正式用户角色管理接口和 `ROLE:MANAGE` 门禁。
- `PERM_USER_ROLE_SYNC` 的 `relationKey` 使用所属组织角色业务键，固定格式为 `ORG:{orgExternalId}`。写入 `businessKey` 时必须按 §6.2.2.4 编码为 `relationKey=ORG%3A{orgExternalId}`。permission-center 通过 `roleTypeCode=ORG + roleExternalId=orgExternalId` 解析为所属组织 `abstract_role.id`，写入 `user_role.relation_id`。`relation_id` 因此表示关联组织角色 ID，不表示 `ADMIN_ORG resource_entity.id`，也不对外暴露为 API 入参。
- 单次 sync 接口的 `operation` 使用混合严格语义：禁用为 `DISABLE`，删除为 `DELETE`，成员移除为 `UNBIND`。
- full-sync 接口均为单请求全量校准接口，必须携带强制 scope，只在 scope 内补齐缺失并清理多余同步事实。
- 所有 sync/full-sync 接口的调度分类以 §6.2.2.2 为准；错误响应返回 `RETRYABLE`、`DEPENDENCY_MISSING`、`NON_RETRYABLE`、`SECURITY_DENIED`，旧版本 no-op 使用成功响应并返回 `STALE_VERSION`。

主体同步请求：

```json
{
  "operation": "UPSERT",
  "subjectTypeCode": "ADMIN_USER",
  "subjectExternalId": "10001",
  "name": "张三",
  "enabled": true,
  "extra": {
    "username": "zhangsan"
  },
  "sourceService": "admin-service",
  "sourceEntityType": "sys_user",
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
    "sourceService": "admin-service",
    "subjectTypeCode": "ADMIN_USER"
  },
  "items": [
    {
      "subjectExternalId": "10001",
      "name": "张三",
      "enabled": true,
      "extra": {
        "username": "zhangsan"
      },
      "sourceEntityType": "sys_user",
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
  "roleTypeCode": "ORG",
  "roleExternalId": "2001",
  "name": "研发部",
  "parentRoleTypeCode": "ORG",
  "parentRoleExternalId": "1000",
  "treeRootExternalId": "1",
  "status": 1,
  "sortOrder": 10,
  "extra": {
    "orgType": "ORG"
  },
  "sourceService": "admin-service",
  "sourceEntityType": "sys_org",
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
    "sourceService": "admin-service",
    "roleTypeCode": "ORG",
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
        "orgType": "ORG"
      },
      "sourceEntityType": "sys_org",
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
  "sourceType": "SYS_USER_ORG",
  "subjectTypeCode": "ADMIN_USER",
  "subjectExternalId": "10001",
  "roleTypeCode": "POSITION",
  "roleExternalId": "3001",
  "relationKey": "ORG:2001",
  "validFrom": null,
  "validTo": null,
  "sourceService": "admin-service",
  "sourceEntityType": "sys_user_org",
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
    "sourceService": "admin-service",
    "sourceType": "SYS_USER_ORG",
    "roleTypeCode": "POSITION",
    "treeRootExternalId": "1"
  },
  "items": [
    {
      "subjectTypeCode": "ADMIN_USER",
      "subjectExternalId": "10001",
      "roleTypeCode": "POSITION",
      "roleExternalId": "3001",
      "relationKey": "ORG:2001",
      "validFrom": null,
      "validTo": null,
      "sourceEntityType": "sys_user_org",
      "sourceEntityId": "10001:3001",
      "syncVersion": {
        "occurredAt": "2026-06-12T10:00:00.123",
        "sequenceNo": 1024
      }
    }
  ]
}
```

> `items[].roleTypeCode` 为必填字段，且必须与 `scope.roleTypeCode` 严格相等；不一致时该 item 返回 `NON_RETRYABLE`（`reason=ROLE_TYPE_CODE_MISMATCH_WITH_SCOPE`），不进入 `markStatus` 路径。admin-service 必须按 binding 对应组织的 `orgType`（ORG/POSITION）拆分为多个 envelope，每个 envelope 内 item.roleTypeCode 与 scope.roleTypeCode 对齐。

非资源实体 full-sync 规则：

- `abstract-user/full-sync` 对比 `sync_metadata(entityKind=ABSTRACT_USER, sourceService, scopeKey)`。
- `abstract-role/full-sync` 对比 `sync_metadata(entityKind=ABSTRACT_ROLE, sourceService, scopeKey)`；item 的父角色默认与 scope 中的 `roleTypeCode` 同类型，如需跨类型必须显式传 `parentRoleTypeCode`。
- `user-role/full-sync` 对比 `sync_metadata(entityKind=USER_ROLE, sourceService, scopeKey)`；请求缺失的旧关系按 `UNBOUND` 处理，不删除正式功能角色分配。
- 所有 full-sync 接口只清理命中 `sync_metadata` 的同步事实，不扫描删除人工维护或正式管理 API 创建的事实。

#### 6.2.2.5 服务间内部调用认证（sync/full-sync 专用）

admin-service 调度器通过 Feign 调用 permission-center 的 8 个 sync/full-sync 接口属于无 HTTP 上下文的服务间内部调用，不走前端会话与 Gateway 鉴权。请求必须同时携带以下三个 Header：

| Header | 注入方 | 说明 |
|--------|--------|------|
| `X-Tenant-Id` | 调用方业务侧拦截器（如 admin-service `FeignTenantInterceptor`） | 调度端 `processOne` 按任务 `tenantId` 设置 `TenantContextHolder` 后由拦截器从 ThreadLocal 读取并注入 |
| `X-Service-Code` | `perm-sdk` 的 `FeignInternalSyncInterceptor` | 默认值取自 `perm.service-code`（admin-service 固定 `admin-service`）；调用方已显式声明的值不被覆盖 |
| `X-Internal-Secret` | `perm-sdk` 的 `FeignInternalSyncInterceptor` | 取自 `perm.internal-secret`，与 permission-center 端配置共享同一密钥 |

permission-center 端拦截器执行顺序与决策语义（自 S5fix2 起）：

| order | 拦截器 | 路径 | 决策语义 |
|-------|--------|------|----------|
| 1 | `InternalApiSecretInterceptor` | `/api/perm/**` | 校验 `X-Internal-Secret`，通过则在 request 写 `INTERNAL_AUTHENTICATED=true` attribute；失败直接 403 终止链。 |
| 2 | `HeaderSignatureInterceptor` | `/api/**`, `/internal/**`, `/actuator/**` | 4 路径决策树（见下）。 |
| 3 | `PermTenantInterceptor` | `/api/**`, `/internal/**`, `/actuator/**` | 提取并设置租户上下文。 |

`HeaderSignatureInterceptor` 4 路径决策树：

1. **路径 1（服务间内部调用）**：`request.getAttribute(INTERNAL_AUTHENTICATED) == true` → 直接放行。该 attribute 仅由 order=1 拦截器在密钥校验通过后写入，请求方无法伪造。
2. **路径 2（完全匿名）**：`X-User-Id` 与 `X-Tenant-Id` 均缺失 → 放行（actuator 健康检查、未登录探活）。
3. **路径 3（异常请求）**：仅有 `X-Tenant-Id` 而无 `X-User-Id`，且未通过路径 1（无 INTERNAL_AUTHENTICATED）→ 记录 `BLOCKED_REQUEST` 安全事件后 403。这是 P0 修复前调度 Feign 同步请求被误拒的场景，现在仅在 InternalApiSecret 未通过时才会触发。
4. **路径 4（用户态调用）**：`X-User-Id` 存在 → 必须存在合法 `X-User-Signature` + `X-Signature-Timestamp`，按 HMAC-SHA256(`userId|tenantId|timestamp`) 校验；时间戳超过 `perm.signature.valid-seconds` 或签名错误均 403。

**安全决策原则**：基于已验证的 attribute（仅前置拦截器可写）而非未验证的请求头。任何拦截器对外暴露的“信任决策”都不允许直接读未经验证的 `X-*` 请求头；这是 P0 防御 — 否则攻击方只需带 `X-Tenant-Id` 就能绕过 HMAC 校验，或者反之让合法 Feign 调用被误判为篡改。

密钥管理：

- 通过 `PERM_INTERNAL_SECRET` 环境变量注入；K8s 部署使用 Secret，本地开发使用 `.env` 或 Vault；密钥不进 git。
- 密钥轮换通过双密钥窗口期实现：在过渡期允许新旧两个密钥同时通过校验，所有调用方滚动更新后再下线旧密钥。

调用方契约：

- **MUST NOT** 在 `SyncTaskFeignClient` 等内部调用 Feign 接口的方法签名声明 `@RequestHeader("X-Service-Code")` —— 由拦截器统一注入。
- 调度器 `@Scheduled` 触发时无 HTTP 上下文，**MUST** 在 `processOne` 入口按任务 `tenantId` 设置 `TenantContextHolder`，并在 finally 中恢复或清理，避免线程池租户串味。
- 仅当配置了 `perm.internal-secret` 时 `FeignInternalSyncInterceptor` 才生效；前端服务依赖 perm-sdk 但未配置该密钥时不会启动失败、也不会注入相关 Header。

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
| `user-role/full-sync` | `sourceType=SYS_USER_ORG&roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |

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
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
      "canGrant": false,
      "conditionCode": null
    }
  ],
  "update": [
    {
      "id": 100,
      "canGrant": true,
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
- `canGrant=true` 表示授权者可把同一条权限授权给他人，但不得扩大资源、操作或范围；可授权对象列表由业务服务控制。
- 授权者必须已经拥有目标权限且该权限 `canGrant=true`，才能把同一权限授权给他人。
- 对范围权限，授权者只能授权自己已有的范围；拥有 `scopeAll=true` 才能授权全量范围。
- permission-center 只校验授权者是否具备同一权限的委托能力，不负责生成候选被授权人列表。
- 写入 `operation_log` 和 `permission_change_log`，并通过 Redis pub/sub 广播 `PermInvalidateEvent` 失效缓存（afterCommit）。~~递增 `permission_version`~~（已废弃，审计 S-001）。
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
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
      "canGrant": false,
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
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
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
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
      "conditionCode": null
    },
    {
      "resourceTypeCode": "DATA",
      "resourceCode": "data:city:hangzhou",
      "codeType": "default",
      "operationCode": "DATA_READ",
      "scopeAll": false,
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
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
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
      "dependOn": 200
    },
    {
      "id": 202,
      "resourceTypeCode": "DATA",
      "resourceCode": "data:city:hangzhou",
      "operationCode": "DATA_READ",
      "scopeAll": false,
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
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
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
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
  "canGrant": false,
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
- 子权限写入、删除都必须记录 `permission_change_log`，并通过 Redis pub/sub 广播 `PermInvalidateEvent` 失效父角色缓存（afterCommit）。~~递增父角色的 `permission_version`~~（已废弃，审计 S-001）。

### 6.6 通用资源权限查询

`POST /api/perm/auth/query-resources`

用于业务服务查询某个主体在指定资源类型和操作下的有效权限集合。典型场景包括 admin-service 查询用户能管理哪些组织、哪些角色、哪些菜单。前提是这些业务对象已经作为 `resource_entity` 同步或创建到权限中心。

请求：

```json
{
  "subjectTypeCode": "ADMIN_USER",
  "subjectExternalId": "10001",
  "domainCode": "admin",
  "resourceTypeCodes": ["ADMIN_ORG"],
  "operationCodes": ["UPDATE"],
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
      "resourceTypeCode": "ADMIN_ORG",
      "resourceCode": "100",
      "resourceName": "研发中心",
      "codeType": "default",
      "operations": ["UPDATE"],
      "canGrant": true,
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

| 查询目标   | 建模方式                                                                           | 查询参数                                                                  |
| ---------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| 可管理组织 | 组织同步为管理资源，例如 `resourceTypeCode=ADMIN_ORG`、`resourceCode={sys_org.id}` | `resourceTypeCodes=["ADMIN_ORG"]`、`operationCodes=["UPDATE"]` 或其他管理操作 |
| 可管理用户 | 用户同步为管理资源，例如 `resourceTypeCode=ADMIN_USER`、`resourceCode={sys_user.id}` | `resourceTypeCodes=["ADMIN_USER"]`、`operationCodes=["UPDATE","DELETE","ENABLE","DISABLE","RESET_PASSWORD"]` |
| 可管理角色 | 角色同步为资源，例如 `resourceTypeCode=ROLE`、`resourceCode=role:{roleExternalId}` | `resourceTypeCodes=["ROLE"]`、`operationCodes=["MANAGE"]` 或 `["ASSIGN"]` |
| 可见菜单   | 菜单同步为资源，例如 `resourceTypeCode=MENU`、`resourceCode=menu:{menuCode}`       | `resourceTypeCodes=["MENU"]`、`operationCodes=["VIEW"]`、`treeMode=true`  |

规则：

- 查询接口只返回权限事实和资源业务键，不查询 admin-service 的组织、角色、菜单业务表。
- 调用方拿到 `resourceCode` 后，由业务服务映射成本服务内的组织树、角色列表或菜单树。
- AccessMesh 管理端中，`ADMIN_USER`/`ADMIN_ORG` 的 `resourceCode` 固定使用 admin-service 本地主键字符串，避免与组织编码、用户名等可变业务字段混用。
- AccessMesh 管理端中，ADMIN_USER/ADMIN_ORG 的 resourceCode 固定使用 admin-service 本地主键字符串。所有接口均支持业务键参数，permission-center 内部通过 TypeResolutionService 解析为内部 ID。调用方不应存储 permission-center 的内部主键 ID。
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
    },
    {
    },
      "resourceTypeCode": "DATA",
      "resourceCode": null,
      "resourceName": null,
      "codeType": null,
      "scopeAll": true,
      "operations": ["DATA_READ", "DATA_EDIT"],
      "sources": ["DIRECT"],
      "matchedRoleIds": [10],
      "matchedPermissionIds": [301, 302],
      "dependOnPermissionIds": []
    {
      "resourceTypeCode": "DATA",
      "resourceCode": "data:dept:B",
      "resourceName": "B部门数据",
      "codeType": "default",
      "scopeAll": false,
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
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
    },
    {
      "resourceTypeCode": "REPORT",
      "resourceCode": null,
      "resourceName": null,
      "codeType": null,
      "operationCodes": ["DATA_READ"],
      "scopeAll": true,
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
      ],
      "sourceRoleCount": 1,
      "sourceRolesTruncated": false,
      "matchedPermissionIds": [200]
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
- `scopeAll=true` 的条目表示该 `resourceTypeCode` 下全量范围权限，此时 `resourceCode`、`resourceName`、`codeType` 均为 null。不展开 scopeAll 为逐条资源实例。实例级条目（`scopeAll=false`）按 `resourceCode + codeType` 精确表示。

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
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
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
        "scopeAll": false
    },
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
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
    {
      "serviceCode": "admin-service",
      "httpMethod": null,
      "pathPattern": null,
      "hasCondition": true,
      "conditionId": 5,
      "scopeAll": true
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
- `relationId` 与表 `user_role.relation_id` 一致（如 POSITION 等类型需要时填写，否则 `null`）。

#### 6.10.3 `abstract-role/tree` 与 `extra-roles/*`

**`POST /api/perm/abstract-role/tree`**：

```json
{
  "domainCode": "admin"
}
```

- `domainCode` 可省略或显式 `null`：仅返回**全局域**角色树（`biz_domain_id` 为空）。
- `domainCode` 有值：返回该业务域下角色**以及**全局域角色的合并树（与实现中「域 OR 全局」过滤一致）。

**`POST /api/perm/abstract-role/extra-roles/list|add|remove`** — 使用业务键定位分组角色与基本角色，示例（`add`）：

```json
{
  "groupDomainCode": "admin",
  "groupRoleTypeCode": "GROUP_ROLE",
  "groupRoleExternalId": "finance_admin",
  "basicDomainCode": "admin",
  "basicRoleTypeCode": "BASIC_ROLE",
  "basicRoleExternalId": "role_report_viewer"
}
```

分组角色和基本角色可能属于不同业务域，因此使用独立的 `groupDomainCode` 和 `basicDomainCode` 分别定位。

`list` 仅需定位分组角色的一组字段（`domainCode` + `groupRoleTypeCode` + `groupRoleExternalId`），响应为 `{ "items": [...] }`，每项为角色摘要（至少包含 `id`、`roleTypeCode`、`externalId`、`name`）。

#### 6.10.4 `resource-api-mapping/create` 与 `update` 响应

- `create`、`update` 成功后响应 `data` 为**单条**映射对象（与列表项结构一致），至少包含映射主键 `id` 及 `serviceCode`、`httpMethod`、`pathPattern` 等关键字段，便于调用方无需再发 `list` 即可确认结果。

#### 6.10.5 `permission-view/explain` 在 `targetType=ROLE` 时的语义

- `targetType=USER`：复用运行时鉴权等价逻辑（与 `auth/check` 一致的主体、角色解析、条件、冲突等）。
- `targetType=ROLE`：**仅**判定该角色在 `role_resource_permission` 上是否**直接**拥有指定 `resourceTypeCode + resourceCode + codeType + operationCode`（含 `scopeAll`、条件启用、记录停用等角色侧字段）；**不**走用户维度的 `auth/check` 链路，不模拟用户继承的多角色并集。

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
12. **委托授权**：`canGrant=true` 表示可把同一条权限授权给他人，但不得扩大资源、操作或范围；被授权对象候选范围由业务服务控制。
13. **资源依赖方向**：`resource_dependency.resource_entity_id` 是源资源/被授权资源，`depends_on_resource_entity_id` 是被源资源依赖、需要自动补全的目标资源。
14. **同步所有权**：服务接口同步和资源依赖同步必须通过 `ownerServiceCode + maintainSource + syncKey` 限定 FULL diff 删除范围。
15. **变更摘要枚举**：`diff_snapshot.eventType`、`items[].changeType` 和 `recent-changes.impactLevel` 使用固定枚举，不使用开放字符串。
