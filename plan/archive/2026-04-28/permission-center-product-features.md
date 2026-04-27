# Archived / 非权威来源

本文档是 2026-04-28 文档重整前的旧版产品功能长文档，可能包含旧接口和旧字段。实现时请以 `plan/permission-center/api-contract.md` 和 `plan/schema/permission-center.sql` 为准。

---

# 通用权限中心 - 产品功能文档

本文档定义权限中心所有功能模块、接口、入参/出参及业务规则，面向前端开发、后端开发和测试人员。

---

## 目录

1. [类型定义管理](#1-类型定义管理)
2. [业务域管理](#2-业务域管理)
3. [抽象用户管理](#3-抽象用户管理)
4. [角色分组管理](#4-角色分组管理)
5. [抽象角色管理](#5-抽象角色管理)
6. [分组-角色关联管理](#6-分组-角色关联管理)
7. [操作权限管理](#7-操作权限管理)
8. [资源实体管理](#8-资源实体管理)
9. [接口资源映射与服务注册](#9-接口资源映射与服务注册)
10. [权限条件管理](#10-权限条件管理)
11. [用户关联管理（用户-角色/分组）](#11-用户关联管理)
12. [角色权限配置（角色-资源-操作）](#12-角色权限配置)
13. [子权限/数据权限管理](#13-子权限数据权限管理)
14. [域配置管理](#14-域配置管理)
15. [资源依赖管理](#15-资源依赖管理)
16. [权限冲突规则管理](#16-权限冲突规则管理)
17. [鉴权服务](#17-鉴权服务)
18. [权限版本与快照](#18-权限版本与快照)
19. [变更记录查询](#19-变更记录查询)
20. [用户权限视图](#20-用户权限视图)
21. [系统配置管理](#21-系统配置管理)

---

## 通用约定

### 项目规范

- **所有接口统一使用 POST 方法。**
- **所有参数通过 JSON 请求体传递，不通过 URL 路径或 Query 参数传递。**
- Content-Type 统一为 `application/json`。

> **例外**：文件上传/下载接口不遵循 POST+JSON Body 约定。文件上传使用 `multipart/form-data`，文件下载使用 GET 方法。

### 接口路径命名规则

标准 CRUD 操作使用统一后缀：

| 后缀      | 含义           |
| --------- | -------------- |
| `/list`   | 分页/列表查询  |
| `/detail` | 详情查询       |
| `/create` | 创建           |
| `/update` | 更新           |
| `/remove` | 删除（软删除） |

特殊操作使用动作语义后缀（如 `/move`、`/sync`、`/check`）。

### 请求头

| 头部          | 必填 | 说明                                     |
| ------------- | ---- | ---------------------------------------- |
| X-Tenant-Id   | 是   | 租户ID，所有接口必传                     |
| Authorization | 是   | Bearer Token                             |
| X-Request-Id  | 否   | 请求追踪ID（trace_id），用于变更记录关联 |

### 通用响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "requestId": "uuid-xxx",
  "traceId": "64-hex-trace-id"
}
```

| 字段        | 类型     | 说明                                                          |
| ----------- | -------- | ------------------------------------------------------------- |
| `code`      | `int`    | 200 = 成功；非零为错误码                                       |
| `message`   | `String` | 面向前端展示的提示文本，不得包含堆栈信息                      |
| `data`      | `Object` | 业务数据；失败时为 `null`                                     |
| `requestId` | `String` | 请求追踪 ID，由 Gateway 生成                                   |
| `traceId`   | `String` | 链路追踪 ID，由 Micrometer Tracing 生成，全链路透传           |

### 分页请求参数

所有分页接口的请求体中包含以下字段：

| 参数   | 类型 | 必填 | 说明                      |
| ------ | ---- | ---- | ------------------------- |
| page   | int  | 否   | 当前页码，从 1 开始       |
| size   | int  | 否   | 每页条数，默认 20，最大不超过 100 |

### 分页响应结构

```json
{
  "code": 200,
  "data": {
    "items": [],
    "pagination": {
      "total": 100,
      "page": 1,
      "size": 20,
      "totalPages": 5
    }
  }
}
```

### 软删除约定

- 所有删除为软删除（`delete_flag = 本行id`，`deleted_at = now()`）。
- 所有查询默认过滤已删除记录。
- 删除接口请求体统一为 `{ "ids": [1, 2, 3] }`。

---

## 1. 类型定义管理

### 1.1 功能描述

管理 `user_type`、`role_type`、`resource_type` 等类型枚举。系统预置类型（`is_system=true`）不可删改，租户可扩展自定义类型。

### 1.2 适用场景

- 系统初始化时预置标准类型（如 USER/SERVICE、MENU/BUTTON/API/DATA）。
- 租户管理员扩展自定义类型（如 resource_type 新增 REPORT、DASHBOARD）。
- 管理端下拉选择框的数据源。

### 1.3 接口列表

#### 1.3.1 查询类型列表

```
POST /api/perm/type-definition/list
```

**请求体**

| 字段        | 类型   | 必填 | 说明                                           |
| ----------- | ------ | ---- | ---------------------------------------------- |
| typeKey     | string | 否   | 类型键，如 user_type、role_type、resource_type |
| bizDomainId | long   | 否   | 业务域ID，NULL 查全局                          |

**响应 data**

```json
[
  {
    "id": 1,
    "typeKey": "resource_type",
    "typeValue": 1,
    "name": "菜单",
    "description": "菜单类型资源",
    "isSystem": true,
    "sortOrder": 1,
    "extra": { "max_depth": 10 },
    "bizDomainId": null
  }
]
```

#### 1.3.2 创建类型

```
POST /api/perm/type-definition/create
```

**请求体**

| 字段        | 类型   | 必填 | 说明                  |
| ----------- | ------ | ---- | --------------------- |
| typeKey     | string | 是   | 类型键                |
| typeValue   | int    | 是   | 枚举值                |
| name        | string | 是   | 显示名称              |
| description | string | 否   | 描述                  |
| bizDomainId | long   | 否   | 业务域ID，NULL 为全局 |
| sortOrder   | int    | 否   | 排序，默认 0          |
| extra       | object | 否   | 扩展配置 JSON         |

**业务规则**

- `(tenant_id, biz_domain_id, type_key, type_value)` 唯一。
- `is_system` 由系统初始化设置，接口创建的默认 `is_system=false`。
- `typeValue` 在同一 `(tenant_id, biz_domain_id, typeKey)` 下不允许重复。

**响应 data**：创建后的完整对象。

#### 1.3.3 更新类型

```
POST /api/perm/type-definition/update
```

**请求体**

| 字段        | 类型   | 必填 | 说明          |
| ----------- | ------ | ---- | ------------- |
| id          | long   | 是   | 类型ID        |
| name        | string | 否   | 显示名称      |
| description | string | 否   | 描述          |
| sortOrder   | int    | 否   | 排序          |
| extra       | object | 否   | 扩展配置 JSON |

**业务规则**

- `is_system=true` 的记录不可修改。
- `typeKey` 和 `typeValue` 不可修改（变更含义用新建代替）。
- 可修改 `name`、`description`、`sortOrder`、`extra`。

#### 1.3.4 删除类型

```
POST /api/perm/type-definition/remove
```

**请求体**

```json
{ "ids": [1, 2, 3] }
```

**业务规则**

- `is_system=true` 的记录不可删除。
- 删除前检查是否有业务数据引用该类型（如 abstract_user.user_type、abstract_role.role_type、resource_entity.resource_type、operation_permission.resource_type），有引用则拒绝删除。

---

## 2. 业务域管理

### 2.1 功能描述

管理业务域，对权限对象进行逻辑分类和管理边界划分。

### 2.2 适用场景

- 企业有多个子系统（OA/CRM/ERP），每个子系统作为一个业务域。
- 不同业务域下的角色、资源、操作相互隔离。
- 全局角色/资源可通过域绑定在多个域中使用。

### 2.3 接口列表

#### 2.3.1 查询域列表

```
POST /api/perm/biz-domain/list
```

**请求体**

| 字段    | 类型   | 必填 | 说明                     |
| ------- | ------ | ---- | ------------------------ |
| keyword | string | 否   | 按 code 或 name 模糊搜索 |

**响应 data**

```json
[
  {
    "id": 1,
    "code": "oa",
    "name": "OA办公系统",
    "description": "...",
    "createdAt": "2026-01-01T00:00:00Z"
  }
]
```

#### 2.3.2 创建域

```
POST /api/perm/biz-domain/create
```

**请求体**

| 字段        | 类型   | 必填 | 说明               |
| ----------- | ------ | ---- | ------------------ |
| code        | string | 是   | 域编码，租户内唯一 |
| name        | string | 是   | 域名称             |
| description | string | 否   | 描述               |

**业务规则**

- `code` 在租户内唯一。
- `code` 格式：字母、数字、下划线、连字符，长度 2-64。

#### 2.3.3 更新域

```
POST /api/perm/biz-domain/update
```

**请求体**

| 字段        | 类型   | 必填 | 说明   |
| ----------- | ------ | ---- | ------ |
| id          | long   | 是   | 域ID   |
| name        | string | 否   | 域名称 |
| description | string | 否   | 描述   |

**业务规则**

- `code` 不可修改。
- 可修改 `name`、`description`。

#### 2.3.4 删除域

```
POST /api/perm/biz-domain/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

**业务规则**

- 删除前检查域下是否有角色、资源、分组、域配置等数据，有则拒绝删除。

---

## 3. 抽象用户管理

### 3.1 功能描述

管理抽象用户。用户是权限系统的主体，通过 `user_type` 区分不同类型（人员、服务、第三方等），通过 `external_id` 与外部业务系统关联。

### 3.2 适用场景

- 业务系统用户同步到权限中心。
- 服务账号注册用于 API 级鉴权。
- 第三方系统接入的用户管理。

### 3.3 接口列表

#### 3.3.1 查询用户列表（分页）

```
POST /api/perm/abstract-user/list
```

**请求体**

| 字段     | 类型   | 必填 | 说明                            |
| -------- | ------ | ---- | ------------------------------- |
| userType | int    | 否   | 用户类型枚举值                  |
| keyword  | string | 否   | 按 name 或 external_id 模糊搜索 |
| pageNum  | int    | 否   | 页码                            |
| pageSize | int    | 否   | 每页条数                        |

#### 3.3.2 查询用户详情

```
POST /api/perm/abstract-user/detail
```

**请求体**

| 字段 | 类型 | 必填 | 说明   |
| ---- | ---- | ---- | ------ |
| id   | long | 是   | 用户ID |

**响应 data**

```json
{
  "id": 1,
  "userType": 1,
  "userTypeName": "人员",
  "externalId": "emp_001",
  "name": "张三",
  "enabled": true,
  "extra": {},
  "createdAt": "2026-01-01T00:00:00Z"
}
```

#### 3.3.3 创建用户

```
POST /api/perm/abstract-user/create
```

**请求体**

| 字段       | 类型    | 必填 | 说明                                    |
| ---------- | ------- | ---- | --------------------------------------- |
| userType   | int     | 是   | 用户类型枚举值                          |
| externalId | string  | 是   | 外部业务系统唯一标识                    |
| name       | string  | 否   | 显示名                                  |
| enabled    | boolean | 否   | 是否启用，默认 true。false 时鉴权不通过 |
| extra      | object  | 否   | 扩展属性                                |

**业务规则**

- `(tenant_id, user_type, external_id)` 唯一。
- `userType` 必须在 type_definition 中存在（type_key='user_type'）。
- 创建时自动创建个人角色 `PERSONAL_{externalId}`（role_type=PERSONAL），写入 user_role(target_type=PERSONAL, target_id=personalRoleId)。

#### 3.3.4 批量创建/同步用户

```
POST /api/perm/abstract-user/sync
```

**请求体**

```json
{
  "users": [
    { "userType": 1, "externalId": "emp_001", "name": "张三" },
    { "userType": 1, "externalId": "emp_002", "name": "李四" }
  ]
}
```

**业务规则**

- 按 `(user_type, external_id)` 匹配：已存在则更新 name/extra，不存在则创建。
- 返回创建数和更新数。

**响应 data**

```json
{ "created": 1, "updated": 1 }
```

#### 3.3.5 更新用户

```
POST /api/perm/abstract-user/update
```

**请求体**

| 字段    | 类型    | 必填 | 说明                         |
| ------- | ------- | ---- | ---------------------------- |
| id      | long    | 是   | 用户ID                       |
| name    | string  | 否   | 显示名                       |
| enabled | boolean | 否   | 是否启用。false 时鉴权不通过 |
| extra   | object  | 否   | 扩展属性                     |

**业务规则**

- `userType` 和 `externalId` 不可修改。
- 可修改 `name`、`enabled`、`extra`。
- `enabled` 设为 false 后，该用户所有鉴权请求直接拒绝。

#### 3.3.6 删除用户

```
POST /api/perm/abstract-user/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

**业务规则**

- 级联软删 `user_role` 中该用户的所有关联。
- 级联软删个人角色的 `role_resource_permission`。
- 写入 operation_log + permission_change_log。
- 失效该用户的缓存。

---

## 4. ~~角色分组管理~~（已移除，功能合并至抽象角色树形结构）

> 角色分组（role_group）和分组-角色关联（role_group_role）表已移除。
> 树形层级现通过 `abstract_role.parent_id` 实现，通过 `role_type=GROUP_ROLE` 区分分组角色。
> 分组角色不直接配置权限，通过 `extra.basicRoleIds` 额外关联基本角色。
> 详见 [DESIGN.md §4 角色模型](../DESIGN.md) 和下方第 5 节"抽象角色管理"。

---

## 5. 抽象角色管理

### 5.1 功能描述

管理抽象角色（树形）。角色是权限配置的最小单元，通过 `parent_id` 支持层级结构，通过 `role_type=GROUP_ROLE` 区分分组角色。

### 5.2 适用场景

- 定义系统角色（如管理员、普通用户、审核员）。
- 定义业务角色（如报表管理员、数据分析师）。

### 5.3 接口列表

#### 5.3.1 查询角色列表（分页）

```
POST /api/perm/abstract-role/list
```

**请求体**

| 字段        | 类型   | 必填 | 说明                           |
| ----------- | ------ | ---- | ------------------------------ |
| bizDomainId | long   | 否   | 业务域ID                       |
| roleType    | int    | 否   | 角色类型枚举值                 |
| keyword     | string | 否   | 按 name 模糊搜索               |
| parentId    | long   | 否   | 父角色ID（查平铺子角色） |
| pageNum     | int    | 否   | 页码                           |
| pageSize    | int    | 否   | 每页条数                       |

#### 5.3.2 查询角色详情

```
POST /api/perm/abstract-role/detail
```

**请求体**

| 字段 | 类型 | 必填 | 说明   |
| ---- | ---- | ---- | ------ |
| id   | long | 是   | 角色ID |

**响应 data**

```json
{
  "id": 10,
  "roleType": 1,
  "roleTypeName": "岗位",
  "name": "前端开发",
  "bizDomainId": null,
  "externalId": "role_frontend",
  "status": 1,
  "sortOrder": 1,
  "extra": {},
  "parentId": null,
  "parentName": null,
  "children": [
    { "roleId": 12, "roleName": "前端实习生" }
  ],
  "createdAt": "2026-01-01T00:00:00Z"
}
```

#### 5.3.3 创建角色

```
POST /api/perm/abstract-role/create
```

**请求体**

| 字段        | 类型   | 必填 | 说明                        |
| ----------- | ------ | ---- | --------------------------- |
| roleType    | int    | 是   | 角色类型枚举值              |
| name        | string | 是   | 角色名称                    |
| bizDomainId | long   | 否   | 业务域ID，NULL 为全局       |
| externalId  | string | 否   | 外部标识                    |
| parentId    | long   | 否   | 父角色ID（GROUP_ROLE 类型下可设置，建立树形层级） |
| status      | int    | 否   | 状态：0=停用 1=启用，默认 1                      |
| sortOrder   | int    | 否   | 排序                                             |
| extra       | object | 否   | 扩展属性。GROUP_ROLE 类型时 `extra.basicRoleIds` 关联基本角色 |

**业务规则**

- `roleType` 必须在 type_definition 中存在（type_key='role_type'）。
- GROUP_ROLE 类型时 `parentId` 可设，BASIC_ROLE/PERSONAL/POSITION 不可有子级。
- GROUP_ROLE 不可直接配置权限，通过 `extra.basicRoleIds` 额外关联基本角色。
- 写入 permission_change_log。

#### 5.3.4 更新角色

```
POST /api/perm/abstract-role/update
```

**请求体**

| 字段        | 类型   | 必填 | 说明                |
| ----------- | ------ | ---- | ------------------- |
| id          | long   | 是   | 角色ID              |
| name        | string | 否   | 角色名称            |
| bizDomainId | long   | 否   | 业务域ID            |
| externalId  | string | 否   | 外部标识            |
| status      | int    | 否   | 状态：0=停用 1=启用 |
| sortOrder   | int    | 否   | 排序                |
| extra       | object | 否   | 扩展属性            |

**业务规则**

- `roleType` 不可修改。
- 可修改 `name`、`externalId`、`bizDomainId`、`status`、`sortOrder`、`extra`。
- `status` 设为 0 后，该角色不参与鉴权。
- 角色名唯一性根据 `system_config.ROLE_NAME_UNIQUE_MODE` 配置检查。

#### 5.3.5 删除角色

```
POST /api/perm/abstract-role/remove
```

**请求体**

```json
{ "ids": [10, 11] }
```

**业务规则**

- 级联软删：
  - 所有 `user_role` 中直接关联该角色（target_id=该角色id）的记录。
  - 所有 `role_resource_permission` 中该角色的授权记录（含子权限级联）。
  - 若为 GROUP_ROLE：递归删除子角色（含子角色的 user_role），不删除子角色的权限。
- 写入 permission_change_log。
- 失效相关用户的缓存。

---

## 6. ~~分组-角色关联管理~~（已移除，功能合并至抽象角色树形结构）

> 分组-角色关联（role_group_role）表已移除。
> 分组角色通过 `abstract_role.parent_id` 树形关联基本角色，额外关联通过 `extra.basicRoleIds` 实现。
> 对应的管理接口为 `POST /api/perm/abstract-role/extra-roles/*` 系列，见第 5 节"抽象角色管理"。

---

## 7. 操作权限管理

### 7.1 功能描述

管理操作权限（如 VIEW、EDIT、DELETE、ACCESS）。操作绑定资源类型，通过 `binary_bit + inherit_mask` 表达操作间的继承关系。

### 7.2 适用场景

- 定义各资源类型下可用的操作。
- 配置操作继承（如 EDIT 隐含 VIEW）。

### 7.3 接口列表

#### 7.3.1 查询操作列表

```
POST /api/perm/operation-permission/list
```

**请求体**

| 字段          | 类型    | 必填 | 说明                                              |
| ------------- | ------- | ---- | ------------------------------------------------- |
| resourceType  | int     | 否   | 资源类型枚举值，查该类型适用的操作                |
| includeGlobal | boolean | 否   | 是否包含全局操作（resource_type=NULL），默认 true |

**响应 data**

```json
[
  {
    "id": 1,
    "resourceType": null,
    "code": "VIEW",
    "name": "查看",
    "binaryBit": 1,
    "inheritMask": 0,
    "effective": 1
  },
  {
    "id": 2,
    "resourceType": null,
    "code": "EDIT",
    "name": "编辑",
    "binaryBit": 4,
    "inheritMask": 2,
    "effective": 6
  }
]
```

#### 7.3.2 创建操作

```
POST /api/perm/operation-permission/create
```

**请求体**

| 字段         | 类型   | 必填 | 说明                        |
| ------------ | ------ | ---- | --------------------------- |
| resourceType | int    | 否   | 适用的资源类型，NULL 为全局 |
| code         | string | 是   | 操作编码                    |
| name         | string | 是   | 显示名                      |
| binaryBit    | long   | 是   | 独占位值（1, 2, 4, 8, ...） |
| inheritMask  | long   | 否   | 继承位掩码，默认 0          |

**业务规则**

- `(tenant_id, resource_type, code)` 唯一（resource_type 可空分开约束）。
- `binaryBit` 必须为 2 的幂，且在同一 `(tenant_id, resource_type)` 下不重复。
- `inheritMask` 中引用的位必须为已存在操作的 `binaryBit`。

#### 7.3.3 更新操作

```
POST /api/perm/operation-permission/update
```

**请求体**

| 字段        | 类型   | 必填 | 说明       |
| ----------- | ------ | ---- | ---------- |
| id          | long   | 是   | 操作ID     |
| name        | string | 否   | 显示名     |
| inheritMask | long   | 否   | 继承位掩码 |

**业务规则**

- `code` 和 `binaryBit` 不可修改。
- 可修改 `name`、`inheritMask`。
- 修改 `inheritMask` 后需触发 permission_version 递增。

#### 7.3.4 删除操作

```
POST /api/perm/operation-permission/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

**业务规则**

- 删除前检查是否有 role_resource_permission 引用，有则拒绝。
- 删除前检查是否有其他操作的 inheritMask 引用了该操作的 binaryBit，有则拒绝。

---

## 8. 资源实体管理

### 8.1 功能描述

管理权限资源实体（树形）。资源是权限作用的对象，如菜单、按钮、API、数据范围等。

### 8.2 适用场景

- 菜单/按钮资源的权限控制。
- API 资源的接口级鉴权。
- 数据范围资源（DATA 类型）用于数据权限。

### 8.3 接口列表

#### 8.3.1 查询资源树

```
POST /api/perm/resource-entity/tree
```

**请求体**

| 字段         | 类型 | 必填 | 说明           |
| ------------ | ---- | ---- | -------------- |
| bizDomainId  | long | 否   | 业务域ID       |
| resourceType | int  | 否   | 资源类型枚举值 |

#### 8.3.2 查询资源列表（分页，平铺）

```
POST /api/perm/resource-entity/list
```

**请求体**

| 字段         | 类型   | 必填 | 说明                     |
| ------------ | ------ | ---- | ------------------------ |
| bizDomainId  | long   | 否   | 业务域ID                 |
| resourceType | int    | 否   | 资源类型枚举值           |
| parentId     | long   | 否   | 父资源ID                 |
| keyword      | string | 否   | 按 code 或 name 模糊搜索 |
| pageNum      | int    | 否   | 页码                     |
| pageSize     | int    | 否   | 每页条数                 |

#### 8.3.3 查询资源详情

```
POST /api/perm/resource-entity/detail
```

**请求体**

| 字段 | 类型 | 必填 | 说明   |
| ---- | ---- | ---- | ------ |
| id   | long | 是   | 资源ID |

**响应 data**

```json
{
  "id": 1,
  "code": "sys:user:list",
  "name": "用户列表",
  "resourceType": 2,
  "resourceTypeName": "按钮",
  "bizDomainId": 1,
  "parentId": null,
  "path": "/1",
  "sortOrder": 1,
  "extra": {},
  "createdAt": "2026-01-01T00:00:00Z"
}
```

#### 8.3.4 创建资源

```
POST /api/perm/resource-entity/create
```

**请求体**

| 字段         | 类型   | 必填 | 说明                          |
| ------------ | ------ | ---- | ----------------------------- |
| code         | string | 是   | 资源编码                      |
| name         | string | 是   | 名称                          |
| resourceType | int    | 是   | 资源类型枚举值                |
| codeType     | string | 否   | 编码类型，默认 "default"      |
| bizDomainId  | long   | 否   | 业务域ID                      |
| parentId     | long   | 否   | 父资源ID                      |
| status       | int    | 否   | 状态：0=停用 1=启用，默认 1   |
| sortOrder    | int    | 否   | 排序                          |
| extra        | object | 否   | 扩展属性（如菜单图标/路由等） |

**业务规则**

- `(tenant_id, resource_type, biz_domain_id, code, code_type)` 唯一（biz_domain_id 可 NULL）。
- `resourceType` 必填，引用 type_definition 中的 resource_type。
- `parentId` 若非空须存在且未删。
- 树深度校验：若 `resource_type` 在 type_definition.extra 中配置了 `max_depth`，则校验当前深度不超过限制。
- 自动计算 `path`。

#### 8.3.5 批量创建资源

```
POST /api/perm/resource-entity/batch-create
```

**请求体**

```json
{
  "resources": [
    {
      "code": "rpt:view",
      "name": "查看报表",
      "resourceType": 1,
      "parentId": null
    },
    {
      "code": "rpt:edit",
      "name": "编辑报表",
      "resourceType": 2,
      "parentId": null
    }
  ]
}
```

#### 8.3.6 更新资源

```
POST /api/perm/resource-entity/update
```

**请求体**

| 字段         | 类型   | 必填 | 说明                |
| ------------ | ------ | ---- | ------------------- |
| id           | long   | 是   | 资源ID              |
| name         | string | 否   | 名称                |
| resourceType | int    | 否   | 资源类型枚举值      |
| status       | int    | 否   | 状态：0=停用 1=启用 |
| sortOrder    | int    | 否   | 排序                |
| extra        | object | 否   | 扩展属性            |

**业务规则**

- `code` 和 `codeType` 不可修改。
- 可修改 `name`、`resourceType`、`status`、`sortOrder`、`extra`。
- `parentId` 修改通过专用"移动资源"接口。

#### 8.3.7 移动资源

```
POST /api/perm/resource-entity/move
```

**请求体**

| 字段           | 类型 | 必填 | 说明                        |
| -------------- | ---- | ---- | --------------------------- |
| id             | long | 是   | 资源ID                      |
| targetParentId | long | 否   | 目标父资源ID，NULL 移到根级 |

**业务规则**

- 防环校验。
- 移动后更新自身及所有子资源的 `path`。

#### 8.3.8 删除资源

```
POST /api/perm/resource-entity/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

**业务规则**

- 级联软删子资源。
- 级联软删关联的 `resource_api_mapping`。
- 级联软删关联的 `role_resource_permission`（含子权限）。
- 级联软删关联的 `resource_dependency`。

---

## 9. 接口资源映射与服务注册

### 9.1 功能描述

管理接入服务配置和接口资源映射。支持服务端自动上报和管理端手动配置。

### 9.2 适用场景

- Java 服务启动时自动上报接口到权限中心。
- 管理端手动配置非 Java 服务的接口资源。
- Gateway 接口级鉴权的数据源。

### 9.3 接口列表

#### 9.3.1 查询服务列表

```
POST /api/perm/service-config/list
```

**请求体**

| 字段    | 类型   | 必填 | 说明                            |
| ------- | ------ | ---- | ------------------------------- |
| keyword | string | 否   | 按 serviceCode 或 name 模糊搜索 |

#### 9.3.2 创建/更新服务

```
POST /api/perm/service-config/save
```

**请求体**

| 字段        | 类型   | 必填 | 说明                        |
| ----------- | ------ | ---- | --------------------------- |
| serviceCode | string | 是   | 服务编码，租户内唯一        |
| name        | string | 是   | 服务名称                    |
| basePath    | string | 否   | 基础路径前缀                |
| status      | int    | 否   | 状态：0=停用 1=启用，默认 1 |
| description | string | 否   | 描述                        |
| extra       | object | 否   | 扩展配置                    |

**业务规则**

- `serviceCode` 已存在则更新，不存在则创建（幂等）。

#### 9.3.3 服务接口全量同步

```
POST /api/perm/service-config/sync
```

**请求体**

| 字段        | 类型   | 必填 | 说明         |
| ----------- | ------ | ---- | ------------ |
| serviceCode | string | 是   | 服务编码     |
| bizDomainId | long   | 否   | 业务域ID     |
| groups      | array  | 是   | 接口分组列表 |

```json
{
  "serviceCode": "user-service",
  "bizDomainId": null,
  "groups": [
    {
      "code": "user-controller",
      "name": "用户管理",
      "apis": [
        {
          "method": "GET",
          "path": "/api/users",
          "name": "查询用户列表",
          "description": "分页查询用户"
        },
        {
          "method": "POST",
          "path": "/api/users",
          "name": "创建用户",
          "description": "创建新用户"
        }
      ]
    }
  ]
}
```

**业务规则**

- 按 `serviceCode` 全量同步：
  1. 获取该服务当前已有的所有 resource_entity（API 类型）和 resource_api_mapping。
  2. 与上报数据做 diff。
  3. 新接口：自动创建 resource_entity（分组→父资源节点，接口→子资源节点）+ resource_api_mapping。
  4. 已删接口：软删对应的 resource_api_mapping 和 resource_entity。
  5. 已变更接口：更新 name、description 等。
- resource_entity 的 code 生成规则：`{serviceCode}:{groupCode}:{method}:{path_simplified}`。
- 接口类资源的 `resource_type` 为 API 类型枚举值。
- 自动关联默认操作 ACCESS。
- 写入 permission_change_log。

**响应 data**

```json
{
  "created": 3,
  "updated": 1,
  "deleted": 2,
  "unchanged": 5
}
```

#### 9.3.4 查询服务的接口资源

```
POST /api/perm/service-config/apis
```

**请求体**

| 字段        | 类型   | 必填 | 说明     |
| ----------- | ------ | ---- | -------- |
| serviceCode | string | 是   | 服务编码 |

**响应 data**：该服务下的接口资源树（分组→接口）。

#### 9.3.5 手动创建接口映射

```
POST /api/perm/resource-api-mapping/create
```

**请求体**

| 字段             | 类型    | 必填 | 说明                 |
| ---------------- | ------- | ---- | -------------------- |
| resourceEntityId | long    | 是   | 关联的资源实体ID     |
| serviceCode      | string  | 是   | 服务编码             |
| httpMethod       | string  | 是   | HTTP 方法            |
| pathPattern      | string  | 是   | 路径模式（完整路径） |
| matchOrder       | int     | 否   | 匹配优先级，默认 0   |
| enabled          | boolean | 否   | 是否启用，默认 true  |

#### 9.3.6 更新接口映射

```
POST /api/perm/resource-api-mapping/update
```

**请求体**

| 字段        | 类型    | 必填 | 说明       |
| ----------- | ------- | ---- | ---------- |
| id          | long    | 是   | 映射记录ID |
| httpMethod  | string  | 否   | HTTP 方法  |
| pathPattern | string  | 否   | 路径模式   |
| matchOrder  | int     | 否   | 匹配优先级 |
| enabled     | boolean | 否   | 是否启用   |

#### 9.3.7 删除接口映射

```
POST /api/perm/resource-api-mapping/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

---

## 10. 权限条件管理

### 10.1 功能描述

管理权限生效条件（可复用实体）。条件可绑定到 `role_resource_permission`，运行时动态判定权限是否生效。条件规则采用结构化 JSONB 存储，支持组合逻辑。

### 10.2 适用场景

- 仅在指定日期范围内生效的权限（DATE_RANGE）。
- 仅在指定时间段内生效的权限（TIME_RANGE）。
- 仅内网 IP 可访问（IP_WHITELIST / IP_BLACKLIST）。
- 多条件组合（AND/OR 逻辑）。

### 10.3 接口列表

#### 10.3.1 查询条件列表

```
POST /api/perm/permission-condition/list
```

**请求体**

| 字段    | 类型   | 必填 | 说明                 |
| ------- | ------ | ---- | -------------------- |
| keyword | string | 否   | 按 code 或 name 搜索 |

#### 10.3.2 创建条件

```
POST /api/perm/permission-condition/create
```

**请求体**

| 字段           | 类型   | 必填 | 说明                           |
| -------------- | ------ | ---- | ------------------------------ |
| code           | string | 是   | 条件编码，租户内唯一           |
| name           | string | 是   | 名称                           |
| conditionRules | object | 是   | 结构化规则（见下方 JSON 示例） |
| description    | string | 否   | 说明                           |

**conditionRules 结构**

```json
{
  "logic": "AND",
  "items": [
    {
      "type": "DATE_RANGE",
      "params": { "startDate": "2026-01-01", "endDate": "2026-12-31" }
    },
    {
      "type": "TIME_RANGE",
      "params": { "startTime": "09:00", "endTime": "18:00" }
    },
    {
      "type": "IP_WHITELIST",
      "params": { "cidrs": ["10.0.0.0/8", "192.168.0.0/16"] }
    }
  ]
}
```

**预置条件类型**

| type         | 说明      | params 参数                     |
| ------------ | --------- | ------------------------------- |
| DATE_RANGE   | 日期范围  | startDate, endDate (yyyy-MM-dd) |
| TIME_RANGE   | 时间段    | startTime, endTime (HH:mm)      |
| IP_WHITELIST | IP 白名单 | cidrs (CIDR 数组)               |
| IP_BLACKLIST | IP 黑名单 | cidrs (CIDR 数组)               |

**业务规则**

- `code` 在租户内唯一。
- `conditionRules.logic` 支持 AND / OR。
- 条件是可复用实体，可被多个 role_resource_permission 引用。

#### 10.3.3 更新条件

```
POST /api/perm/permission-condition/update
```

**请求体**

| 字段           | 类型   | 必填 | 说明       |
| -------------- | ------ | ---- | ---------- |
| id             | long   | 是   | 条件ID     |
| name           | string | 否   | 名称       |
| conditionRules | object | 否   | 结构化规则 |
| description    | string | 否   | 说明       |

**业务规则**

- `code` 不可修改。
- 修改后立即影响所有引用该条件的权限配置。

#### 10.3.4 删除条件

```
POST /api/perm/permission-condition/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

**业务规则**

- 删除前检查是否有 role_resource_permission 引用（`condition_id` 指向该条件），有则拒绝。

---

## 11. 用户关联管理

### 11.1 功能描述

管理用户与角色/分组的关联关系。通过 `target_type` 区分关联目标。

### 11.2 适用场景

- 给用户分配角色或加入分组。
- 批量用户权限配置。
- 临时权限（通过 valid_from/valid_to）。

### 11.3 接口列表

#### 11.3.1 查询用户关联列表

```
POST /api/perm/user-role/list
```

**请求体**

| 字段       | 类型   | 必填 | 说明         |
| ---------- | ------ | ---- | ------------ |
| userId     | long   | 是   | 用户ID       |
| targetType | string | 否   | 角色类型：ROLE / ORG / POSITION / PERSONAL / GROUP_ROLE |

**响应 data**

```json
[
  {
    "id": 1,
    "targetType": "ROLE",
    "targetId": 10,
    "targetName": "前端开发",
    "validFrom": null,
    "validTo": null,
    "createdAt": "2026-01-01T00:00:00Z"
  },
  {
    "id": 2,
    "targetType": "GROUP_ROLE",
    "targetId": 1,
    "targetName": "技术部",
    "validFrom": "2026-01-01T00:00:00Z",
    "validTo": "2026-12-31T23:59:59Z",
    "createdAt": "2026-01-01T00:00:00Z"
  }
]
```

#### 11.3.2 批量分配关联

```
POST /api/perm/user-role/assign
```

**请求体**

```json
{
  "userId": 1,
  "assignments": [
    {
      "targetType": "ROLE",
      "targetId": 10,
      "validFrom": null,
      "validTo": null
    },
    {
      "targetType": "GROUP_ROLE",
      "targetId": 1,
      "validFrom": "2026-01-01T00:00:00Z",
      "validTo": "2026-12-31T23:59:59Z"
    }
  ]
}
```

**业务规则**

- `(tenant_id, abstract_user_id, target_type, target_id)` 唯一。已存在则更新 valid_from/valid_to。
- `target_type=ROLE` / `ORG` / `POSITION` / `PERSONAL` 时，校验角色存在且未删。
- `target_type=GROUP_ROLE` 时，校验分组角色存在且未删，且 `role_type=GROUP_ROLE`。
- `validFrom/validTo` 均为可选，NULL 表示不限制。
- 写入 permission_change_log。
- 失效该用户的有效角色缓存。

#### 11.3.3 批量回收关联

```
POST /api/perm/user-role/revoke
```

**请求体**

```json
{
  "userId": 1,
  "ids": [1, 2]
}
```

**业务规则**

- 软删对应的 user_role 记录。
- 写入 permission_change_log。
- 失效该用户的缓存。

#### 11.3.4 批量用户分配（按角色/分组视角）

```
POST /api/perm/user-role/batch-assign
```

**请求体**

```json
{
  "targetType": "ROLE",
  "targetId": 10,
  "userIds": [1, 2, 3],
  "validFrom": null,
  "validTo": null
}
```

**业务规则**

- 批量给多个用户分配同一角色或加入同一分组。
- 业务规则同 11.3.2。
- 写入 permission_change_log（entity_type=batch_user_role）。

---

## 12. 角色权限配置

### 12.1 功能描述

配置角色对资源的操作权限。核心授权表 `role_resource_permission` 的管理。

### 12.2 适用场景

- 为角色配置菜单/按钮/API 权限。
- 配置 can_manage 标记允许转授权。
- 绑定权限条件。

### 12.3 接口列表

#### 12.3.1 查询角色的权限列表

```
POST /api/perm/role-resource-permission/list
```

**请求体**

| 字段            | 类型    | 必填 | 说明                                              |
| --------------- | ------- | ---- | ------------------------------------------------- |
| roleId          | long    | 是   | 角色ID                                            |
| resourceType    | int     | 否   | 按资源类型过滤                                    |
| bizDomainId     | long    | 否   | 按业务域过滤                                      |
| includeChildren | boolean | 否   | 是否包含子权限（depend_on 不为 NULL），默认 false |

**响应 data**

```json
[
  {
    "id": 100,
    "resourceEntityId": 1,
    "resourceCode": "sys:user",
    "resourceName": "用户管理",
    "resourceType": 1,
    "operationPermissionId": 1,
    "operationCode": "VIEW",
    "operationName": "查看",
    "canManage": false,
    "conditionId": null,
    "conditionName": null,
    "dependOn": null,
    "childPermissions": []
  }
]
```

#### 12.3.2 批量配置角色权限

```
POST /api/perm/role-resource-permission/grant
```

**请求体**

```json
{
  "roleId": 10,
  "add": [
    {
      "resourceEntityId": 1,
      "operationPermissionId": 1,
      "canManage": false,
      "conditionId": null
    }
  ],
  "update": [
    {
      "id": 100,
      "canManage": true,
      "conditionId": 5
    }
  ],
  "delete": [101, 102]
}
```

**业务规则**

- 三段式操作：`add`（新增）、`update`（修改现有）、`delete`（删除）在同一事务中执行。

- 校验角色、资源、操作存在且未删。
- 校验操作与资源类型匹配：`operation_permission.resource_type` 为 NULL 或等于 `resource_entity.resource_type`。
- 若 `conditionId` 非空，校验条件存在且 `enabled=true`。
- 若启用域配置校验，通过 `domain_config` 校验域内关联合法性。
- `resource_type` 冗余字段自动从 `resource_entity.resource_type` 填充。
- `(tenant_id, abstract_role_id, resource_entity_id, operation_permission_id, COALESCE(depend_on, 0))` 唯一。已存在则更新 can_manage/condition_id。
- 写入 permission_change_log。
- 触发 permission_version 递增。

#### 12.3.3 批量回收角色权限

```
POST /api/perm/role-resource-permission/revoke
```

**请求体**

```json
{
  "roleId": 10,
  "ids": [100, 101]
}
```

**业务规则**

- 软删指定的 role_resource_permission 记录。
- 若被删记录有子权限（`depend_on` 指向它），级联软删子权限。
- 写入 permission_change_log。
- 触发 permission_version 递增。

---

## 13. 子权限/数据权限管理

### 13.1 功能描述

管理子权限（`role_resource_permission.depend_on`）。子权限用于资源级的数据权限控制。

### 13.2 适用场景

- 用户对报表A有VIEW权限，需额外配置该报表可见的数据范围（如特定城市）。
- 同一报表的不同操作（VIEW/EDIT）有不同的数据范围。

### 13.3 接口列表

#### 13.3.1 查询主权限的子权限

```
POST /api/perm/role-resource-permission/children
```

**请求体**

| 字段         | 类型 | 必填 | 说明     |
| ------------ | ---- | ---- | -------- |
| permissionId | long | 是   | 主权限ID |

**响应 data**

```json
[
  {
    "id": 201,
    "resourceEntityId": 50,
    "resourceCode": "data:city:yy",
    "resourceName": "YY市数据",
    "resourceType": 4,
    "operationPermissionId": 5,
    "operationCode": "DATA_READ",
    "operationName": "数据读取",
    "dependOn": 200
  }
]
```

#### 13.3.2 为主权限添加子权限

```
POST /api/perm/role-resource-permission/add
```

**请求体**

```json
{
  "permissionId": 200,
  "children": [
    {
      "resourceEntityId": 50,
      "operationPermissionId": 5
    }
  ]
}
```

**业务规则**

- `permissionId` 必须存在且 `depend_on IS NULL`（只能给主权限添加子权限）。
- 子权限的 `abstract_role_id` 继承自父权限。
- 子权限的 `depend_on = permissionId`。
- `resource_type` 冗余字段自动填充。
- 写入 permission_change_log。

#### 13.3.3 删除子权限

```
POST /api/perm/role-resource-permission/remove-child
```

**请求体**

```json
{ "ids": [201, 202] }
```

**业务规则**

- 只能删除 `depend_on IS NOT NULL` 的记录。
- 写入 permission_change_log。

---

## 14. 域配置管理

### 14.1 功能描述

管理域下的范围(SCOPE)、关系(RELATION)、绑定(BINDING)、子权限定义(SUB_PERM)配置。四种配置统一存储在 `domain_config` 表。

### 14.2 适用场景

- 配置域下允许使用的角色类型、资源类型、操作。
- 配置域内角色类型与资源类型的可关联关系。
- 将全局角色/资源/操作绑定到特定域。
- 定义域下哪些资源类型可作为子权限的数据范围。

### 14.3 接口列表

#### 14.3.1 查询域配置

```
POST /api/perm/domain-config/list
```

**请求体**

| 字段       | 类型   | 必填 | 说明                                  |
| ---------- | ------ | ---- | ------------------------------------- |
| domainId   | long   | 是   | 域ID                                  |
| configType | string | 否   | SCOPE / RELATION / BINDING / SUB_PERM |

**响应 data**

```json
[
  {
    "id": 1,
    "configType": "SCOPE",
    "extra": { "scope_type": "ROLE_TYPE", "scope_ref_id": 1 }
  },
  {
    "id": 2,
    "configType": "RELATION",
    "extra": {
      "relation_type": "ROLE_RESOURCE",
      "left_ref_id": 1,
      "right_ref_id": 2
    }
  },
  {
    "id": 3,
    "configType": "BINDING",
    "extra": { "bound_type": "ROLE", "bound_entity_id": 100 }
  }
]
```

#### 14.3.2 添加域配置

```
POST /api/perm/domain-config/create
```

**请求体**

| 字段       | 类型   | 必填 | 说明                                  |
| ---------- | ------ | ---- | ------------------------------------- |
| domainId   | long   | 是   | 域ID                                  |
| configType | string | 是   | SCOPE / RELATION / BINDING / SUB_PERM |
| extra      | object | 是   | 配置内容 JSON                         |

**extra 格式说明**

- **SCOPE**: `{"scope_type": "ROLE_TYPE|RESOURCE_TYPE|OPERATION", "scope_ref_id": <type_value 或 operation_id>}`
- **RELATION**: `{"relation_type": "ROLE_RESOURCE", "left_ref_id": <role_type_value>, "right_ref_id": <resource_type_value>}`
- **BINDING**: `{"bound_type": "ROLE|RESOURCE|OPERATION", "bound_entity_id": <实体ID>}`
- **SUB_PERM**: `{"sub_resource_type": <resource_type_value>, "description": "子权限数据范围说明"}`

**业务规则**

- SCOPE: `scope_ref_id` 须在 type_definition 或 operation_permission 中存在。
- RELATION: `left_ref_id` 和 `right_ref_id` 须在 type_definition 中存在。
- BINDING: `bound_entity_id` 须指向全局实体（`biz_domain_id IS NULL`）。

#### 14.3.3 删除域配置

```
POST /api/perm/domain-config/remove
```

**请求体**

```json
{
  "domainId": 1,
  "ids": [1, 2]
}
```

---

## 15. 资源依赖管理

### 15.1 功能描述

管理资源间的声明式依赖关系。由业务系统注册资源时自动维护，权限中心提供存储、查询和依赖完整性检查。依赖关系使用位运算表达操作级粒度。

支持依赖规则变更时的自动补全与清理：授权时自动补全（`auto_grant=true`），补全记录标记 `grant_source='AUTO_DEP'` + `grant_dep_id`，规则变更时按标记精准清理并重新评估。

### 15.2 适用场景

- **按钮→接口映射**：按钮 CREATE 权限 → 自动补全对应 POST 接口的 ACCESS 权限
- **菜单→数据源**：查看菜单需要对应数据集的 DATA_READ 权限
- **接口级联**：修改接口自动补全审计日志写入权限
- 权限配置时提示"缺少依赖权限"

### 15.3 接口列表

#### 15.3.1 查询资源依赖列表

```
POST /api/perm/resource-dependency/list
```

**请求体**

| 字段                      | 类型 | 必填 | 说明       |
| ------------------------- | ---- | ---- | ---------- |
| resourceEntityId          | long | 否   | 主体资源ID |
| dependsOnResourceEntityId | long | 否   | 依赖资源ID |

#### 15.3.2 添加资源依赖

```
POST /api/perm/resource-dependency/create
```

**请求体**

| 字段                      | 类型    | 必填 | 说明                                              |
| ------------------------- | ------- | ---- | ------------------------------------------------- |
| resourceEntityId          | long    | 是   | 主体资源ID                                        |
| dependsOnResourceEntityId | long    | 是   | 依赖资源ID                                        |
| sourceOperationBits       | bigint  | 否   | 触发条件：对主体资源的操作位掩码，NULL 为任意操作 |
| requiredOperationBits     | bigint  | 是   | 对依赖资源所需的操作位掩码                        |
| autoGrant                 | boolean | 否   | 是否自动补齐依赖权限，默认 false                  |

**业务规则**

- 主体资源和依赖资源须存在且未删。
- 写入时防环校验（检查新依赖是否形成循环）。
- `(tenant_id, resource_entity_id, depends_on_resource_entity_id)` 唯一。
- `autoGrant=true` 时，授权主体资源对应操作时自动补齐依赖资源的 `requiredOperationBits` 权限。

#### 15.3.3 删除资源依赖

```
POST /api/perm/resource-dependency/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

**业务规则**

- 删除依赖规则后，自动清理所有 `grant_source='AUTO_DEP'` 且 `grant_dep_id` 匹配的补全记录。
- 清理后递增受影响角色的 permission_version，触发缓存失效。

#### 15.3.4 批量同步依赖

```
POST /api/perm/resource-dependency/batch-sync
```

**请求体**

```json
{
  "resourceEntityId": 1,
  "dependencies": [
    {
      "dependsOnResourceEntityId": 50,
      "sourceOperationBits": 4,
      "requiredOperationBits": 2,
      "autoGrant": true
    }
  ]
}
```

**业务规则**

- 按 `resourceEntityId` 全量替换：传入列表即为该资源的完整依赖集。
- 不在列表中的已有依赖自动删除，触发清理补全记录 + 重新评估。
- 新增/修改的依赖规则，重新评估受影响角色的自动补全状态。

#### 15.3.5 查询依赖图

```
POST /api/perm/resource-dependency/graph
```

**请求体**

| 字段             | 类型   | 必填 | 说明                                                      |
| ---------------- | ------ | ---- | --------------------------------------------------------- |
| resourceEntityId | long   | 是   | 起点资源ID                                                |
| graphMode        | string | 否   | AROUND(围绕)/UPSTREAM(上游)/DOWNSTREAM(下游)，默认 AROUND |

**响应 data**：边集合（source → target + operationBits）。

#### 15.3.6 检查依赖完整性

```
POST /api/perm/resource-dependency/check
```

**请求体**

| 字段                  | 类型 | 必填 | 说明       |
| --------------------- | ---- | ---- | ---------- |
| abstractUserId        | long | 是   | 用户ID     |
| resourceEntityId      | long | 否   | 指定资源ID |
| operationPermissionId | long | 否   | 指定操作ID |

**响应 data**

```json
{
  "complete": false,
  "gaps": [
    {
      "resourceEntityId": 1,
      "resourceName": "报表A",
      "dependsOnResourceId": 50,
      "dependsOnResourceName": "数据集X",
      "requiredOperationCode": "DATA_READ",
      "missing": true
    }
  ]
}
```

---

## 16. 权限冲突规则管理

### 16.1 功能描述

管理两种类型的冲突规则：

- **ROLE_MUTEX（角色互斥）**：同一用户不能同时拥有两个互斥角色，写入时检查并拒绝。
- **PERM_MUTEX（权限互斥）**：同资源下操作的互斥规则，查询时实时计算冲突并使权限失效，异步通知管理员。

### 16.2 适用场景

- 同一用户不能同时拥有"管理员"和"审计员"角色（ROLE_MUTEX）。
- 同一用户对同一资源不能同时拥有"审批"和"提交"操作（PERM_MUTEX）。
- ROLE_MUTEX 在分配角色时实时拦截。
- PERM_MUTEX 在鉴权查询时实时计算 + TTL 缓存 + version 失效。

### 16.3 接口列表

#### 16.3.1 查询冲突规则列表

```
POST /api/perm/conflict-rule/list
```

**请求体**

| 字段        | 类型 | 必填 | 说明         |
| ----------- | ---- | ---- | ------------ |
| bizDomainId | long | 否   | 按业务域过滤 |

#### 16.3.2 添加冲突规则

```
POST /api/perm/conflict-rule/create
```

**请求体**

| 字段                        | 类型   | 必填 | 说明                                     |
| --------------------------- | ------ | ---- | ---------------------------------------- |
| conflictType                | string | 是   | ROLE_MUTEX / PERM_MUTEX                  |
| firstOperationPermissionId  | long   | 条件 | PERM_MUTEX 时必填，互斥操作一            |
| secondOperationPermissionId | long   | 条件 | PERM_MUTEX 时必填，互斥操作二            |
| firstRoleId                 | long   | 条件 | ROLE_MUTEX 时必填，互斥角色一            |
| secondRoleId                | long   | 条件 | ROLE_MUTEX 时必填，互斥角色二            |
| bizDomainId                 | long   | 否   | 业务域ID，NULL 为全局                    |
| resourceTypeValue           | int    | 否   | PERM_MUTEX 时可指定资源类型，NULL 为所有 |

**业务规则**

- PERM_MUTEX 时存库自动排序：`first_id < second_id`。
- ROLE_MUTEX 时存库自动排序：`first_role_id < second_role_id`。
- PERM_MUTEX: `(tenant_id, biz_domain_id, first_operation_id, second_operation_id)` 唯一。
- ROLE_MUTEX: `(tenant_id, first_role_id, second_role_id)` 唯一。
- ROLE_MUTEX 写入时检查：若已有用户同时拥有两角色，拒绝创建并返回冲突用户列表。

#### 16.3.3 删除冲突规则

```
POST /api/perm/conflict-rule/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

#### 16.3.4 冲突检测

```
POST /api/perm/conflict-rule/detect
```

**请求体**

| 字段             | 类型 | 必填 | 说明                     |
| ---------------- | ---- | ---- | ------------------------ |
| abstractUserId   | long | 否   | 指定用户（空则扫描所有） |
| resourceEntityId | long | 否   | 指定资源                 |
| bizDomainId      | long | 否   | 业务域                   |

**响应 data**

```json
{
  "violations": [
    {
      "userId": 1,
      "userName": "张三",
      "resourceId": 10,
      "resourceName": "审批单",
      "firstOperation": { "id": 1, "code": "APPROVE" },
      "secondOperation": { "id": 2, "code": "SUBMIT" },
      "ruleBizDomainId": null
    }
  ]
}
```

---

## 17. 鉴权服务

### 17.1 功能描述

核心鉴权接口，支持三种鉴权模式：

1. **Gateway 拦截**：网关层统一拦截，通过接口快照 + 本地缓存高性能判定。
2. **服务单次检查**：业务服务内部调用，单资源单操作判定。
3. **服务批量检查**：业务服务内部调用，批量判定多个资源/操作组合。

缓存策略：L1 本地缓存（30s-1min TTL） + L2 Redis 缓存（1-5min TTL），version 变更时失效。

**未注册接口默认拒绝（白名单模式）**：未在 `resource_api_mapping` 中注册的接口默认 deny。

### 17.2 接口列表

#### 17.2.1 权限检查

```
POST /api/perm/auth/check
```

**请求体**

| 字段                  | 类型   | 必填 | 说明                                 |
| --------------------- | ------ | ---- | ------------------------------------ |
| abstractUserId        | long   | 是   | 用户ID                               |
| resourceEntityId      | long   | 是   | 资源ID                               |
| operationPermissionId | long   | 是   | 操作ID                               |
| bizDomainId           | long   | 否   | 业务域ID                             |
| inheritMode           | string | 否   | NONE/CHILDREN/PARENT/BOTH，默认 NONE |
| context               | object | 否   | 条件表达式上下文（Map）              |

**响应 data**

```json
{
  "allowed": true,
  "reason": null,
  "matchedRoleId": 10,
  "matchedPermissionId": 100,
  "conditionEvaluated": false
}
```

当 `allowed=false` 时，`reason` 可能的值：

| reason             | 说明                   |
| ------------------ | ---------------------- |
| USER_DISABLED      | 用户已停用             |
| ROLE_DISABLED      | 角色已停用             |
| NO_ROLE            | 用户无有效角色         |
| NO_PERMISSION      | 角色无该资源操作授权   |
| CONDITION_NOT_MET  | 条件不满足             |
| CONFLICT_DETECTED  | 冲突规则导致权限失效   |
| API_NOT_REGISTERED | 接口未注册（白名单外） |

#### 17.2.2 批量权限检查

```
POST /api/perm/auth/check-batch
```

**请求体**

```json
{
  "abstractUserId": 1,
  "bizDomainId": null,
  "checks": [
    { "resourceEntityId": 1, "operationPermissionId": 1 },
    { "resourceEntityId": 2, "operationPermissionId": 1 }
  ]
}
```

**响应 data**

```json
{
  "results": [
    { "resourceEntityId": 1, "operationPermissionId": 1, "allowed": true },
    {
      "resourceEntityId": 2,
      "operationPermissionId": 1,
      "allowed": false,
      "reason": "NO_PERMISSION"
    }
  ]
}
```

---

## 18. 权限版本与缓存

### 18.1 功能描述

权限版本管理和 Redis 双份缓存机制，供 Gateway 运行时鉴权使用。版本号在权限变更时自增，仅用于缓存失效，不保存历史快照。

### 18.2 Redis 缓存设计

权限中心在 Redis 中维护两份缓存，Gateway 通过回调权限中心接口完成鉴权，权限中心内部读取这两份数据进行计算。

#### 第一份：用户→角色

```
Key:   perm:user:roles:{tenantId}:{userId}
Value: Set<roleId>  →  ["1", "2", "5"]
TTL:   5 分钟（可配置）
```

**更新时机**：
- 分配角色时：向该 Key 添加 roleId
- 取消角色时：从该 Key 移除 roleId
- 删除用户/角色时：清理相关 Key
- 变更时同时递增受影响角色的 permission_version

#### 第二份：角色→权限

```
Key:   perm:role:perms:{tenantId}:{roleId}
Value: JSON 对象，包含该角色的完整权限信息：
       {
         "apiPerms": [                          // 接口权限
           {"serviceCode": "admin-service", "httpMethod": "GET", "pathPattern": "/api/admin/users/**", "operationCode": "READ", "hasCondition": false},
           {"serviceCode": "admin-service", "httpMethod": "POST", "pathPattern": "/api/admin/users/sync", "operationCode": "WRITE", "hasCondition": true, "conditionId": 10},
           ...
         ],
         "resourcePerms": [                     // 资源权限（复用给菜单查询、权限视图等场景）
           {"resourceEntityId": 1, "operationPermissionId": 1, "canManage": false},
           ...
         ],
         "version": 42                          // 当前版本号，用于缓存失效判断
       }
TTL:   5 分钟（可配置）
```

**更新时机**：
- 角色配置权限（role_resource_permission 变更）时：重建该 roleId 的权限信息
- 接口资源映射变更时：重建相关角色的 apiPerms
- 变更时同时递增该角色的 permission_version

### 18.3 缓存更新流程

```
角色分配/取消：
  更新 perm:user:roles:{tenantId}:{userId}
  递增关联角色的 permission_version
  （不更新角色权限缓存，因为角色权限本身没变）

角色权限变更：
  重建 perm:role:perms:{tenantId}:{roleId}
  递增该角色的 permission_version
  （不更新用户角色缓存，因为用户→角色关系没变）

角色删除：
  删除 perm:role:perms:{tenantId}:{roleId}
  遍历并更新所有拥有该角色的用户的 perm:user:roles

用户删除：
  删除 perm:user:roles:{tenantId}:{userId}
```

### 18.4 接口列表

#### 18.4.1 查询当前版本

```
POST /api/perm/permission-version/query
```

**请求体**

| 字段           | 类型 | 必填 | 说明                            |
| -------------- | ---- | ---- | ------------------------------- |
| abstractRoleId | long | 否   | 指定角色ID，NULL 返回租户级版本 |

**响应 data**

```json
{
  "tenantId": 1,
  "abstractRoleId": 10,
  "versionNo": 42,
  "triggerEntityType": "role_resource_permission",
  "triggeredAt": "2026-04-18T10:00:00Z"
}
```

#### 18.4.2 接口级判定（Gateway 回调入口）

```
POST /api/perm/auth/check-interface
```

**请求体**

| 字段        | 类型   | 必填 | 说明                                       |
| ----------- | ------ | ---- | ------------------------------------------ |
| tenantId    | long   | 是   | 租户ID                                     |
| userId      | long   | 是   | 用户ID                                     |
| serviceCode | string | 是   | 服务编码                                   |
| httpMethod  | string | 是   | HTTP 方法                                  |
| path        | string | 是   | 请求路径                                   |
| context     | object | 否   | 条件评估上下文（如 IP、时间等运行时参数）  |

**响应 data**

```json
{
  "allowed": true,
  "matchedRoleId": 10,
  "matchedOperationCode": "READ"
}
```

当 `allowed=false` 时，`reason` 可能的值：

| reason             | 说明                 |
| ------------------ | -------------------- |
| USER_DISABLED      | 用户已停用           |
| ROLE_DISABLED      | 角色已停用           |
| NO_ROLE            | 用户无有效角色       |
| NO_PERMISSION      | 角色无该接口访问权限 |
| API_NOT_REGISTERED | 接口未注册           |

**鉴权逻辑**：
1. 读 Redis `perm:user:roles:{tenantId}:{userId}` → 获取用户有效 roleId 集合
2. 对每个 roleId，读 Redis `perm:role:perms:{tenantId}:{roleId}` → 获取角色权限
3. 合并所有角色的 apiPerms
4. 在 apiPerms 中按 `serviceCode + httpMethod + pathPattern` 匹配（Ant 风格）
5. 若匹配条目 `hasCondition=true`，使用请求中的 `context`（IP、时间等）评估条件
6. 匹配成功且条件满足 → allowed=true；未匹配或条件不满足 → allowed=false

---

## 19. 变更记录查询

### 19.1 功能描述

双日志体系：

- **operation_log（操作日志）**：轻量级，记录所有管理操作（谁在什么时候做了什么）。
- **permission_change_log（权限变更日志）**：详细记录权限相关变更的 before/after/diff，支持审计和回溯。

两种日志永久保留，查询支持按时间/操作者/目标类型/动作类型多维度过滤。

### 19.2 接口列表

#### 19.2.1 查询操作日志

```
POST /api/perm/operation-log/list
```

**请求体**

| 字段       | 类型     | 必填 | 说明                                                       |
| ---------- | -------- | ---- | ---------------------------------------------------------- |
| actionType | string   | 否   | 动作类型（CREATE/UPDATE/DELETE/GRANT/REVOKE/SYNC 等）      |
| targetType | string   | 否   | 目标类型（USER/ROLE/GROUP/RESOURCE/PERMISSION/SERVICE 等） |
| operatorId | long     | 否   | 操作者ID                                                   |
| startTime  | datetime | 否   | 开始时间                                                   |
| endTime    | datetime | 否   | 结束时间                                                   |
| pageNum    | int      | 否   | 页码                                                       |
| pageSize   | int      | 否   | 每页条数                                                   |

**响应 data**

```json
{
  "rows": [
    {
      "id": 1,
      "actionType": "CREATE",
      "targetType": "ROLE",
      "targetId": 10,
      "targetName": "前端开发",
      "summary": "创建角色: 前端开发",
      "operatorId": 99,
      "requestId": "req-001",
      "createdAt": "2026-04-18T10:00:00Z"
    }
  ],
  "total": 1
}
```

#### 19.2.2 查询权限变更记录

```
POST /api/perm/permission-change-log/list
```

**请求体**

| 字段           | 类型     | 必填 | 说明                                                |
| -------------- | -------- | ---- | --------------------------------------------------- |
| actionType     | string   | 否   | 动作类型（GRANT/REVOKE/CASCADE_DELETE 等）          |
| targetType     | string   | 否   | 目标类型（ROLE_PERMISSION/USER_ROLE/GROUP_ROLE 等） |
| abstractUserId | long     | 否   | 按影响用户过滤                                      |
| abstractRoleId | long     | 否   | 按影响角色过滤                                      |
| bizDomainId    | long     | 否   | 按业务域过滤                                        |
| requestId      | string   | 否   | 请求追踪ID                                          |
| startTime      | datetime | 否   | 开始时间                                            |
| endTime        | datetime | 否   | 结束时间                                            |
| pageNum        | int      | 否   | 页码                                                |
| pageSize       | int      | 否   | 每页条数                                            |

**响应 data**

```json
{
  "rows": [
    {
      "id": 1,
      "actionType": "GRANT",
      "targetType": "ROLE_PERMISSION",
      "targetId": 100,
      "beforeSnapshot": null,
      "afterSnapshot": { "roleId": 10, "resourceId": 1, "operationBits": 3 },
      "diff": { "added": { "operationBits": 3 } },
      "affectedUserIds": [1, 2, 3],
      "affectedRoleIds": [10],
      "changeSource": "ADMIN",
      "requestId": "req-001",
      "createdBy": 99,
      "createdAt": "2026-04-18T10:00:00Z"
    }
  ],
  "total": 1
}
```

---

## 20. 用户权限视图

### 20.1 功能描述

为用户提供"我的权限"视图，支持完整视图（角色+资源+操作+来源）。核心特性：

- **来源追溯**：展示权限继承链（直接/分组/个人角色）。
- **实时计算**：基于当前数据实时聚合，不使用快照。
- **多维度查询**：按用户、按资源、按角色三个维度。
- **近期变更**：结合 permission_change_log 展示用户权限的近期变化。

### 20.2 接口列表

#### 20.2.1 查询用户有效角色

```
POST /api/perm/permission-view/effective-roles
```

**请求体**

| 字段          | 类型    | 必填 | 说明                         |
| ------------- | ------- | ---- | ---------------------------- |
| userId        | long    | 是   | 用户ID                       |
| bizDomainId   | long    | 否   | 按业务域过滤                 |
| includeSource | boolean | 否   | 是否包含来源信息，默认 false |

**响应 data（includeSource=true）**

```json
[
  {
    "roleId": 10,
    "roleName": "前端开发",
    "roleType": 1,
    "source": "DIRECT",
    "sourceDetail": null
  },
  {
    "roleId": 11,
    "roleName": "技术经理",
    "roleType": 1,
    "source": "GROUP",
    "sourceDetail": {
      "groupId": 1,
      "groupName": "技术部",
      "path": [{ "groupId": 1, "groupName": "技术部" }]
    }
  }
]
```

- `source`：`DIRECT`=用户直接关联角色，`GROUP`=通过分组获得。
- `sourceDetail`：分组来源时包含分组链路信息。

#### 20.2.2 查询用户有效权限（资源-操作列表）

```
POST /api/perm/permission-view/effective-permissions
```

**请求体**

| 字段             | 类型    | 必填 | 说明                                   |
| ---------------- | ------- | ---- | -------------------------------------- |
| userId           | long    | 是   | 用户ID                                 |
| bizDomainId      | long    | 否   | 按业务域过滤                           |
| resourceType     | int     | 否   | 按资源类型过滤                         |
| includeDataScope | boolean | 否   | 是否包含数据权限（子权限），默认 false |

**响应 data**

```json
[
  {
    "resourceEntityId": 1,
    "resourceCode": "sys:user",
    "resourceName": "用户管理",
    "resourceType": 1,
    "operations": [
      { "operationId": 1, "operationCode": "VIEW", "canManage": false },
      { "operationId": 2, "operationCode": "EDIT", "canManage": true }
    ],
    "dataScope": [
      {
        "operationId": 2,
        "operationCode": "EDIT",
        "scopeResources": [
          {
            "resourceId": 50,
            "resourceCode": "data:city:xx",
            "resourceName": "XX市数据"
          }
        ]
      }
    ]
  }
]
```

#### 20.2.3 查询用户资源树（带权限标记）

```
POST /api/perm/permission-view/resource-tree
```

**请求体**

| 字段         | 类型 | 必填 | 说明     |
| ------------ | ---- | ---- | -------- |
| userId       | long | 是   | 用户ID   |
| bizDomainId  | long | 否   | 业务域ID |
| resourceType | int  | 否   | 资源类型 |

**响应 data**：资源树结构，每个节点附带用户拥有的操作列表。

#### 20.2.4 按资源维度查询权限用户

```
POST /api/perm/permission-view/resource-users
```

**请求体**

| 字段                  | 类型 | 必填 | 说明     |
| --------------------- | ---- | ---- | -------- |
| resourceEntityId      | long | 是   | 资源ID   |
| operationPermissionId | long | 否   | 操作ID   |
| bizDomainId           | long | 否   | 业务域ID |

**响应 data**：拥有该资源权限的用户列表（含来源角色信息）。

#### 20.2.5 按角色维度查询权限

```
POST /api/perm/permission-view/role-permissions
```

**请求体**

| 字段         | 类型 | 必填 | 说明     |
| ------------ | ---- | ---- | -------- |
| roleId       | long | 是   | 角色ID   |
| resourceType | int  | 否   | 资源类型 |
| bizDomainId  | long | 否   | 业务域ID |

**响应 data**：该角色拥有的所有资源+操作列表。

#### 20.2.6 查询用户近期权限变更

```
POST /api/perm/permission-view/recent-changes
```

**请求体**

| 字段     | 类型 | 必填 | 说明             |
| -------- | ---- | ---- | ---------------- |
| userId   | long | 是   | 用户ID           |
| days     | int  | 否   | 最近天数，默认 7 |
| pageNum  | int  | 否   | 页码             |
| pageSize | int  | 否   | 每页条数         |

**响应 data**：该用户权限相关的近期变更记录列表（来源 permission_change_log）。

---

## 21. 系统配置管理

### 21.1 功能描述

管理租户级系统配置。配置项以 key-value 形式存储在 `system_config` 表，控制权限中心的全局行为。

### 21.2 预置配置项

| configKey             | 说明                 | 默认值        | 可选值                 |
| --------------------- | -------------------- | ------------- | ---------------------- |
| ROLE_NAME_UNIQUE_MODE | 角色名唯一性检查模式 | NONE          | NONE / TENANT / DOMAIN |
| DEFAULT_DENY_REASON   | 默认拒绝原因         | NO_PERMISSION | 自定义字符串           |
| CACHE_L1_TTL_SECONDS  | L1 本地缓存 TTL      | 30            | 10-300                 |
| CACHE_L2_TTL_SECONDS  | L2 Redis 缓存 TTL    | 180           | 60-600                 |

### 21.3 接口列表

#### 21.3.1 查询系统配置

```
POST /api/perm/system-config/list
```

**请求体**

```json
{}
```

**响应 data**

```json
[
  {
    "id": 1,
    "configKey": "ROLE_NAME_UNIQUE_MODE",
    "configValue": "NONE",
    "description": "角色名唯一性检查模式"
  }
]
```

#### 21.3.2 更新系统配置

```
POST /api/perm/system-config/update
```

**请求体**

| 字段        | 类型   | 必填 | 说明   |
| ----------- | ------ | ---- | ------ |
| configKey   | string | 是   | 配置键 |
| configValue | string | 是   | 配置值 |

**业务规则**

- configKey 须在预置配置项列表中。
- 修改后立即生效（触发缓存刷新）。
- 写入 operation_log。

---

## 附录 A：接口总览

| #   | 模块      | 接口路径                                        | 说明                |
| --- | --------- | ----------------------------------------------- | ------------------- |
| 1   | 类型定义  | POST /api/perm/type-definition/list                       | 查询类型列表        |
| 2   | 类型定义  | POST /api/perm/type-definition/create                     | 创建类型            |
| 3   | 类型定义  | POST /api/perm/type-definition/update                     | 更新类型            |
| 4   | 类型定义  | POST /api/perm/type-definition/remove                     | 删除类型            |
| 5   | 业务域    | POST /api/perm/biz-domain/list                            | 查询域列表          |
| 6   | 业务域    | POST /api/perm/biz-domain/create                          | 创建域              |
| 7   | 业务域    | POST /api/perm/biz-domain/update                          | 更新域              |
| 8   | 业务域    | POST /api/perm/biz-domain/remove                          | 删除域              |
| 9   | 抽象用户  | POST /api/perm/abstract-user/list                         | 查询用户列表        |
| 10  | 抽象用户  | POST /api/perm/abstract-user/detail                       | 查询用户详情        |
| 11  | 抽象用户  | POST /api/perm/abstract-user/create                       | 创建用户            |
| 12  | 抽象用户  | POST /api/perm/abstract-user/update                       | 更新用户            |
| 13  | 抽象用户  | POST /api/perm/abstract-user/remove                       | 删除用户            |
| 14  | 抽象用户  | POST /api/perm/abstract-user/sync                           | 外部系统同步        |
| 15  | 抽象角色  | POST /api/perm/abstract-role/list                         | 查询角色列表        |
| 16  | 抽象角色  | POST /api/perm/abstract-role/detail                       | 查询角色详情        |
| 17  | 抽象角色  | POST /api/perm/abstract-role/create                       | 创建角色            |
| 18  | 抽象角色  | POST /api/perm/abstract-role/update                       | 更新角色            |
| 19  | 抽象角色  | POST /api/perm/abstract-role/remove                       | 删除角色            |
| 20  | 抽象角色  | POST /api/perm/abstract-role/move                           | 移动角色            |
| 21  | 分组角色  | POST /api/perm/abstract-role/extra-roles/add              | 分组角色添加基本角色|
| 22  | 分组角色  | POST /api/perm/abstract-role/extra-roles/remove           | 分组角色移除基本角色|
| 23  | 分组角色  | POST /api/perm/abstract-role/extra-roles/list             | 查询分组角色基本角色|
| 24  | 操作权限  | POST /api/perm/operation-permission/list                  | 查询操作列表        |
| 25  | 操作权限  | POST /api/perm/operation-permission/create                | 创建操作            |
| 26  | 操作权限  | POST /api/perm/operation-permission/update                | 更新操作            |
| 27  | 操作权限  | POST /api/perm/operation-permission/remove                | 删除操作            |
| 28  | 资源实体  | POST /api/perm/resource-entity/tree                     | 查询资源树          |
| 29  | 资源实体  | POST /api/perm/resource-entity/list                     | 查询资源列表        |
| 30  | 资源实体  | POST /api/perm/resource-entity/create                   | 创建资源            |
| 31  | 资源实体  | POST /api/perm/resource-entity/update                   | 更新资源            |
| 32  | 资源实体  | POST /api/perm/resource-entity/remove                   | 删除资源            |
| 33  | 服务      | POST /api/perm/service-config/list                        | 查询服务列表        |
| 34  | 服务      | POST /api/perm/service-config/create                      | 注册服务            |
| 35  | 服务      | POST /api/perm/service-config/update                      | 更新服务            |
| 36  | 服务      | POST /api/perm/service-config/remove                      | 删除服务            |
| 37  | 服务      | POST /api/perm/service-config/sync                        | 全量同步接口资源    |
| 38  | 接口映射  | POST /api/perm/resource-api-mapping/list                | 查询接口映射        |
| 39  | 接口映射  | POST /api/perm/resource-api-mapping/create              | 创建映射            |
| 40  | 接口映射  | POST /api/perm/resource-api-mapping/update              | 更新映射            |
| 41  | 接口映射  | POST /api/perm/resource-api-mapping/remove              | 删除映射            |
| 42  | 条件      | POST /api/perm/permission-condition/list                  | 查询条件列表        |
| 43  | 条件      | POST /api/perm/permission-condition/create                | 创建条件            |
| 44  | 条件      | POST /api/perm/permission-condition/update                | 更新条件            |
| 45  | 条件      | POST /api/perm/permission-condition/remove                | 删除条件            |
| 46  | 用户分配  | POST /api/perm/user-role/list                           | 查询用户关联        |
| 47  | 用户分配  | POST /api/perm/user-role/assign                         | 分配角色            |
| 48  | 用户分配  | POST /api/perm/user-role/unassign                       | 回收角色            |
| 49  | 角色权限  | POST /api/perm/role-resource-permission/list            | 查询角色权限        |
| 50  | 角色权限  | POST /api/perm/role-resource-permission/batch-save      | 批量授权            |
| 51  | 角色权限  | POST /api/perm/role-resource-permission/children        | 查询子权限          |
| 52  | 域配置    | POST /api/perm/domain-config/list                       | 查询域配置          |
| 53  | 域配置    | POST /api/perm/domain-config/save                       | 保存域配置          |
| 54  | 域配置    | POST /api/perm/domain-config/remove                     | 删除域配置          |
| 55  | 依赖      | POST /api/perm/resource-dependency/list                 | 查询依赖列表        |
| 56  | 依赖      | POST /api/perm/resource-dependency/create               | 添加依赖            |
| 57  | 依赖      | POST /api/perm/resource-dependency/remove               | 删除依赖            |
| 58  | 依赖      | POST /api/perm/resource-dependency/batch-sync           | 批量同步依赖        |
| 59  | 冲突      | POST /api/perm/conflict-rule/list                       | 查询冲突规则        |
| 60  | 冲突      | POST /api/perm/conflict-rule/create                     | 创建冲突规则        |
| 61  | 冲突      | POST /api/perm/conflict-rule/remove                     | 删除冲突规则        |
| 62  | 冲突      | POST /api/perm/conflict-rule/detect                     | 冲突检测            |
| 63  | 版本      | POST /api/perm/permission-version/query                 | 查询权限版本        |
| 64  | 视图      | POST /api/perm/permission-view/user-permissions         | 用户权限完整视图    |
| 65  | 系统配置  | POST /api/perm/system-config/list                       | 查询系统配置        |
| 66  | 系统配置  | POST /api/perm/system-config/save                       | 保存系统配置        |
| 67  | 日志      | POST /api/perm/operation-log/list                       | 操作日志查询        |
| 68  | 日志      | POST /api/perm/permission-change-log/list               | 权限变更记录查询    |
| 69  | 鉴权      | POST /api/perm/auth/check                               | 单次鉴权            |
| 70  | 鉴权      | POST /api/perm/auth/batch-check                         | 批量鉴权            |
| 71  | 鉴权      | POST /api/perm/auth/check-interface                     | 接口级判定          |

---

## 附录 B：枚举值速查

### target_type（user_role）

| 值          | 说明         |
| ----------- | ------------ |
| ROLE        | 基本角色     |
| ORG         | 组织角色     |
| POSITION    | 职位角色     |
| PERSONAL    | 个人角色     |
| GROUP_ROLE  | 分组角色     |

### config_type（domain_config）

| 值       | 说明                                   |
| -------- | -------------------------------------- |
| SCOPE    | 域范围（允许的角色类型/资源类型/操作） |
| RELATION | 域关系（角色类型-资源类型可关联关系）  |
| BINDING  | 域绑定（全局实体绑定到域）             |
| SUB_PERM | 子权限定义（域下数据权限范围）         |

### conflict_type（permission_conflict_rule）

| 值         | 说明                              |
| ---------- | --------------------------------- |
| ROLE_MUTEX | 角色互斥（写入时拒绝）            |
| PERM_MUTEX | 权限互斥（查询时失效 + 异步通知） |

### condition_rule_type（permission_condition.condition_rules）

| 值           | 说明      | params 参数                     |
| ------------ | --------- | ------------------------------- |
| DATE_RANGE   | 日期范围  | startDate, endDate (yyyy-MM-dd) |
| TIME_RANGE   | 时间段    | startTime, endTime (HH:mm)      |
| IP_WHITELIST | IP 白名单 | cidrs (CIDR 数组)               |
| IP_BLACKLIST | IP 黑名单 | cidrs (CIDR 数组)               |

### change_source（permission_change_log）

| 值      | 说明                   |
| ------- | ---------------------- |
| ADMIN   | 管理端操作             |
| SYNC  | 外部系统 API 同步        |
| API     | 外部 API 调用          |
| SYSTEM  | 系统内部（如级联删除） |

### inherit_mode（鉴权接口）

| 值       | 说明               |
| -------- | ------------------ |
| NONE     | 仅精确匹配         |
| CHILDREN | 向下展开子资源     |
| PARENT   | 向上查找父资源授权 |
| BOTH     | 双向查找           |

### permission check reason

| 值                 | 说明                   |
| ------------------ | ---------------------- |
| USER_DISABLED      | 用户已停用             |
| ROLE_DISABLED      | 角色已停用             |
| NO_ROLE            | 用户无有效角色         |
| NO_PERMISSION      | 角色无该资源操作授权   |
| CONDITION_NOT_MET  | 条件不满足             |
| CONFLICT_DETECTED  | 冲突规则导致权限失效   |
| API_NOT_REGISTERED | 接口未注册（白名单外） |

### action_type（operation_log / permission_change_log）

| 值             | 说明     |
| -------------- | -------- |
| CREATE         | 创建     |
| UPDATE         | 更新     |
| DELETE         | 删除     |
| GRANT          | 授权     |
| REVOKE         | 回收     |
| SYNC           | 同步     |
| CASCADE_DELETE | 级联删除 |

### target_type（operation_log）

| 值         | 说明     |
| ---------- | -------- |
| USER       | 用户     |
| ROLE       | 角色     |
| GROUP      | 分组     |
| RESOURCE   | 资源     |
| PERMISSION | 权限配置 |
| SERVICE    | 服务     |
| CONDITION  | 条件     |
| CONFIG     | 配置     |
