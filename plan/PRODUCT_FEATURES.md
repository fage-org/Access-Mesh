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

---

## 通用约定

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
  "data": {}
}
```

### 分页请求参数

| 参数     | 类型 | 必填 | 说明                        |
| -------- | ---- | ---- | --------------------------- |
| pageNum  | int  | 否   | 页码，默认 1                |
| pageSize | int  | 否   | 每页条数，默认 20，最大 500 |

### 分页响应结构

```json
{
  "code": 200,
  "data": {
    "rows": [],
    "total": 100,
    "pageNum": 1,
    "pageSize": 20
  }
}
```

### 软删除约定

- 所有删除为软删除（`delete_flag = 本行id`，`deleted_at = now()`）。
- 所有查询默认过滤已删除记录。
- 删除接口统一使用 `POST /api/perm/{resource}/remove`，请求体为 ID 列表。

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
GET /api/perm/types
```

**请求参数**

| 参数        | 类型   | 必填 | 说明                                           |
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
POST /api/perm/types
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
PUT /api/perm/types/{id}
```

**业务规则**

- `is_system=true` 的记录不可修改。
- `typeKey` 和 `typeValue` 不可修改（变更含义用新建代替）。
- 可修改 `name`、`description`、`sortOrder`、`extra`。

#### 1.3.4 删除类型

```
POST /api/perm/types/remove
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
GET /api/perm/domains
```

**请求参数**

| 参数    | 类型   | 必填 | 说明                     |
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
POST /api/perm/domains
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
PUT /api/perm/domains/{id}
```

**业务规则**

- `code` 不可修改。
- 可修改 `name`、`description`。

#### 2.3.4 删除域

```
POST /api/perm/domains/remove
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
GET /api/perm/users
```

**请求参数**

| 参数     | 类型   | 必填 | 说明                            |
| -------- | ------ | ---- | ------------------------------- |
| userType | int    | 否   | 用户类型枚举值                  |
| keyword  | string | 否   | 按 name 或 external_id 模糊搜索 |
| pageNum  | int    | 否   | 页码                            |
| pageSize | int    | 否   | 每页条数                        |

#### 3.3.2 查询用户详情

```
GET /api/perm/users/{id}
```

**响应 data**

```json
{
  "id": 1,
  "userType": 1,
  "userTypeName": "人员",
  "externalId": "emp_001",
  "name": "张三",
  "extra": {},
  "createdAt": "2026-01-01T00:00:00Z"
}
```

#### 3.3.3 创建用户

```
POST /api/perm/users
```

**请求体**

| 字段       | 类型   | 必填 | 说明                 |
| ---------- | ------ | ---- | -------------------- |
| userType   | int    | 是   | 用户类型枚举值       |
| externalId | string | 是   | 外部业务系统唯一标识 |
| name       | string | 否   | 显示名               |
| extra      | object | 否   | 扩展属性             |

**业务规则**

- `(tenant_id, user_type, external_id)` 唯一。
- `userType` 必须在 type_definition 中存在（type_key='user_type'）。

#### 3.3.4 批量创建/同步用户

```
POST /api/perm/users/batch
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

#### 3.3.5 更新用户

```
PUT /api/perm/users/{id}
```

**业务规则**

- `userType` 和 `externalId` 不可修改。
- 可修改 `name`、`extra`。

#### 3.3.6 删除用户

```
POST /api/perm/users/remove
```

**业务规则**

- 级联软删 `user_role` 中该用户的所有关联。
- 写入 permission_change_log。
- 失效该用户的缓存。

---

## 4. 角色分组管理

### 4.1 功能描述

管理角色分组（树形）。分组用于组织角色，方便批量权限管理。用户关联分组后自动获得该分组及其递归子分组下所有角色的权限。

### 4.2 适用场景

- 按部门/团队/项目组织角色。
- 批量授权：将用户加入分组即获得该分组所有角色权限。
- 角色分类展示。

### 4.3 接口列表

#### 4.3.1 查询分组树

```
GET /api/perm/groups/tree
```

**请求参数**

| 参数         | 类型    | 必填 | 说明                               |
| ------------ | ------- | ---- | ---------------------------------- |
| bizDomainId  | long    | 否   | 业务域ID，NULL 查全局              |
| includeRoles | boolean | 否   | 是否在树节点中包含角色，默认 false |

**响应 data**

```json
[
  {
    "id": 1,
    "name": "技术部",
    "code": "tech_dept",
    "bizDomainId": null,
    "isDefault": false,
    "sortOrder": 1,
    "children": [
      {
        "id": 2,
        "name": "前端组",
        "code": "frontend_team",
        "children": [],
        "roles": [{ "id": 10, "name": "前端开发", "roleType": 1 }]
      }
    ],
    "roles": [{ "id": 11, "name": "技术经理", "roleType": 1 }]
  }
]
```

**业务规则**

- 默认分组（`is_default=true`）不在树中返回。
- 若 `includeRoles=true`，每个分组节点附带其**直接关联**的角色列表。

#### 4.3.2 查询分组详情

```
GET /api/perm/groups/{id}
```

**响应 data**：分组基础信息 + 直接子分组列表 + 直接角色列表 + 关联用户数。

#### 4.3.3 查询分组角色（含子分组展开）

```
GET /api/perm/groups/{id}/roles
```

**请求参数**

| 参数            | 类型    | 必填 | 说明                          |
| --------------- | ------- | ---- | ----------------------------- |
| includeChildren | boolean | 否   | 是否展开子分组角色，默认 true |
| pageNum         | int     | 否   | 页码                          |
| pageSize        | int     | 否   | 每页条数                      |

**响应 data**

```json
{
  "rows": [
    {
      "roleId": 10,
      "roleName": "前端开发",
      "roleType": 1,
      "source": "DIRECT",
      "sourceGroupId": null,
      "sourceGroupName": null
    },
    {
      "roleId": 12,
      "roleName": "代码审查员",
      "roleType": 1,
      "source": "CHILD_GROUP",
      "sourceGroupId": 3,
      "sourceGroupName": "代码审查组"
    }
  ],
  "total": 2
}
```

**业务规则**

- `source`：`DIRECT`=直接关联，`CHILD_GROUP`=来自子分组（附带来源分组信息）。

#### 4.3.4 查询分组关联用户

```
GET /api/perm/groups/{id}/users
```

**响应 data**：关联到该分组的用户列表（不含子分组的用户）。

#### 4.3.5 创建分组

```
POST /api/perm/groups
```

**请求体**

| 字段        | 类型   | 必填 | 说明                    |
| ----------- | ------ | ---- | ----------------------- |
| name        | string | 是   | 分组名称                |
| code        | string | 否   | 分组编码                |
| parentId    | long   | 否   | 父分组ID，NULL 为根分组 |
| bizDomainId | long   | 否   | 业务域ID，NULL 为全局   |
| sortOrder   | int    | 否   | 排序                    |
| extra       | object | 否   | 扩展属性                |

**业务规则**

- `parentId` 若非空，须为已存在的分组且未删除。
- `parentId` 不能指向默认分组。
- 自动计算 `path`（如 `/parentPath/newId`）。

#### 4.3.6 更新分组

```
PUT /api/perm/groups/{id}
```

**业务规则**

- 默认分组不可更新。
- 可修改 `name`、`code`、`sortOrder`、`extra`。
- `parentId` 修改通过专用"移动分组"接口。

#### 4.3.7 移动分组

```
PUT /api/perm/groups/{id}/move
```

**请求体**

```json
{ "targetParentId": 5 }
```

**业务规则**

- 默认分组不可移动。
- 不能移动到自身或自身的子分组下（防环）。
- 移动后更新自身及所有子分组的 `path`。
- 写入 permission_change_log。
- 失效相关用户的有效角色缓存。

#### 4.3.8 删除分组

```
POST /api/perm/groups/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

**业务规则**

- 默认分组不可删除。
- 级联软删：
  - 子分组一并软删。
  - 该分组及子分组的 `role_group_role`（分组-角色关联）软删。
  - 该分组及子分组的 `user_role(target_type=GROUP)`（用户-分组关联）软删。
- **不删除角色本身**。
- 写入 permission_change_log。
- 失效相关用户的有效角色缓存。

---

## 5. 抽象角色管理

### 5.1 功能描述

管理抽象角色（平铺）。角色是权限配置的最小单元，通过 `role_group_role` 关联到分组。

### 5.2 适用场景

- 定义系统角色（如管理员、普通用户、审核员）。
- 定义业务角色（如报表管理员、数据分析师）。

### 5.3 接口列表

#### 5.3.1 查询角色列表（分页）

```
GET /api/perm/roles
```

**请求参数**

| 参数        | 类型   | 必填 | 说明                           |
| ----------- | ------ | ---- | ------------------------------ |
| bizDomainId | long   | 否   | 业务域ID                       |
| roleType    | int    | 否   | 角色类型枚举值                 |
| keyword     | string | 否   | 按 name 模糊搜索               |
| groupId     | long   | 否   | 所属分组ID（精确匹配直接关联） |
| pageNum     | int    | 否   | 页码                           |
| pageSize    | int    | 否   | 每页条数                       |

#### 5.3.2 查询角色详情

```
GET /api/perm/roles/{id}
```

**响应 data**

```json
{
  "id": 10,
  "roleType": 1,
  "roleTypeName": "岗位",
  "name": "前端开发",
  "bizDomainId": null,
  "externalId": "role_frontend",
  "sortOrder": 1,
  "extra": {},
  "groups": [
    { "groupId": 1, "groupName": "技术部" },
    { "groupId": 2, "groupName": "前端组" }
  ],
  "createdAt": "2026-01-01T00:00:00Z"
}
```

#### 5.3.3 创建角色

```
POST /api/perm/roles
```

**请求体**

| 字段        | 类型   | 必填 | 说明                  |
| ----------- | ------ | ---- | --------------------- |
| roleType    | int    | 是   | 角色类型枚举值        |
| name        | string | 是   | 角色名称              |
| bizDomainId | long   | 否   | 业务域ID，NULL 为全局 |
| externalId  | string | 否   | 外部标识              |
| groupIds    | long[] | 否   | 初始关联的分组ID列表  |
| sortOrder   | int    | 否   | 排序                  |
| extra       | object | 否   | 扩展属性              |

**业务规则**

- `roleType` 必须在 type_definition 中存在（type_key='role_type'）。
- 创建后自动加入默认分组（写入 role_group_role）。
- 若 `groupIds` 非空，同时写入额外的 role_group_role。
- 写入 permission_change_log。

#### 5.3.4 更新角色

```
PUT /api/perm/roles/{id}
```

**业务规则**

- `roleType` 不可修改。
- 可修改 `name`、`externalId`、`bizDomainId`、`sortOrder`、`extra`。

#### 5.3.5 删除角色

```
POST /api/perm/roles/remove
```

**业务规则**

- 级联软删：
  - 所有 `role_group_role`（含默认分组的关联）。
  - 所有 `user_role(target_type=ROLE)` 中直接关联该角色的记录。
  - 所有 `role_resource_permission` 中该角色的授权记录（含子权限级联）。
- 写入 permission_change_log。
- 失效相关用户的缓存。

---

## 6. 分组-角色关联管理

### 6.1 功能描述

管理分组与角色的多对多关联。一个角色可属于多个分组。

### 6.2 接口列表

#### 6.2.1 为分组添加角色

```
POST /api/perm/groups/{groupId}/roles
```

**请求体**

```json
{ "roleIds": [10, 11, 12] }
```

**业务规则**

- 不能向默认分组手动添加角色（默认分组由系统自动维护）。
- 若启用域校验：全局分组可关联任何域的角色，域级分组仅关联同域角色。
- 已存在的关联不报错（幂等）。
- 写入 permission_change_log。
- 失效该分组关联的所有用户的有效角色缓存。

#### 6.2.2 从分组移除角色

```
POST /api/perm/groups/{groupId}/roles/remove
```

**请求体**

```json
{ "roleIds": [10, 11] }
```

**业务规则**

- 不能从默认分组移除角色。
- 软删对应的 role_group_role 记录。
- 写入 permission_change_log。
- 失效相关用户缓存。

#### 6.2.3 查询角色所属分组

```
GET /api/perm/roles/{roleId}/groups
```

**响应 data**

```json
[
  { "groupId": 1, "groupName": "技术部", "isDefault": false },
  { "groupId": 99, "groupName": "__default__", "isDefault": true }
]
```

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
GET /api/perm/operations
```

**请求参数**

| 参数          | 类型    | 必填 | 说明                                              |
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
    "inheritMask": 1,
    "effective": 5
  }
]
```

#### 7.3.2 创建操作

```
POST /api/perm/operations
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
PUT /api/perm/operations/{id}
```

**业务规则**

- `code` 和 `binaryBit` 不可修改。
- 可修改 `name`、`inheritMask`。
- 修改 `inheritMask` 后需触发 permission_version 递增。

#### 7.3.4 删除操作

```
POST /api/perm/operations/remove
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
GET /api/perm/resources/tree
```

**请求参数**

| 参数         | 类型 | 必填 | 说明           |
| ------------ | ---- | ---- | -------------- |
| bizDomainId  | long | 否   | 业务域ID       |
| resourceType | int  | 否   | 资源类型枚举值 |

#### 8.3.2 查询资源列表（分页，平铺）

```
GET /api/perm/resources
```

**请求参数**

| 参数         | 类型   | 必填 | 说明                     |
| ------------ | ------ | ---- | ------------------------ |
| bizDomainId  | long   | 否   | 业务域ID                 |
| resourceType | int    | 否   | 资源类型枚举值           |
| parentId     | long   | 否   | 父资源ID                 |
| keyword      | string | 否   | 按 code 或 name 模糊搜索 |
| pageNum      | int    | 否   | 页码                     |
| pageSize     | int    | 否   | 每页条数                 |

#### 8.3.3 查询资源详情

```
GET /api/perm/resources/{id}
```

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
POST /api/perm/resources
```

**请求体**

| 字段         | 类型   | 必填 | 说明           |
| ------------ | ------ | ---- | -------------- |
| code         | string | 是   | 资源编码       |
| name         | string | 是   | 名称           |
| resourceType | int    | 否   | 资源类型枚举值 |
| bizDomainId  | long   | 否   | 业务域ID       |
| parentId     | long   | 否   | 父资源ID       |
| sortOrder    | int    | 否   | 排序           |
| extra        | object | 否   | 扩展属性       |

**业务规则**

- `code` 按域唯一：`(tenant_id, biz_domain_id, code)`。
- `parentId` 若非空须存在且未删。
- 树深度校验：若 `resource_type` 在 type_definition.extra 中配置了 `max_depth`，则校验当前深度不超过限制。
- 自动计算 `path`。

#### 8.3.5 批量创建资源

```
POST /api/perm/resources/batch
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
PUT /api/perm/resources/{id}
```

**业务规则**

- `code` 不可修改。
- 可修改 `name`、`resourceType`、`sortOrder`、`extra`。
- `parentId` 修改通过专用"移动资源"接口。

#### 8.3.7 移动资源

```
PUT /api/perm/resources/{id}/move
```

**请求体**

```json
{ "targetParentId": 5 }
```

**业务规则**

- 防环校验。
- 移动后更新自身及所有子资源的 `path`。

#### 8.3.8 删除资源

```
POST /api/perm/resources/remove
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
GET /api/perm/services
```

#### 9.3.2 创建/更新服务

```
POST /api/perm/services
```

**请求体**

| 字段        | 类型   | 必填 | 说明                 |
| ----------- | ------ | ---- | -------------------- |
| serviceCode | string | 是   | 服务编码，租户内唯一 |
| name        | string | 是   | 服务名称             |
| basePath    | string | 否   | 基础路径前缀         |
| description | string | 否   | 描述                 |
| extra       | object | 否   | 扩展配置             |

**业务规则**

- `serviceCode` 已存在则更新，不存在则创建（幂等）。

#### 9.3.3 服务接口全量同步

```
POST /api/perm/services/{serviceCode}/sync
```

**请求体**

```json
{
  "bizDomainId": null,
  "groups": [
    {
      "code": "user-controller",
      "name": "用户管理",
      "apis": [
        {
          "method": "GET",
          "path": "/api/v1/users",
          "name": "查询用户列表",
          "description": "分页查询用户"
        },
        {
          "method": "POST",
          "path": "/api/v1/users",
          "name": "创建用户",
          "description": "创建新用户"
        },
        {
          "method": "GET",
          "path": "/api/v1/users/{id}",
          "name": "查询用户详情",
          "description": "按ID查询用户"
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
GET /api/perm/services/{serviceCode}/apis
```

**响应 data**：该服务下的接口资源树（分组→接口）。

#### 9.3.5 手动管理接口映射

```
POST /api/perm/api-mappings
PUT /api/perm/api-mappings/{id}
POST /api/perm/api-mappings/remove
```

**请求体（创建）**

| 字段             | 类型    | 必填 | 说明                 |
| ---------------- | ------- | ---- | -------------------- |
| resourceEntityId | long    | 是   | 关联的资源实体ID     |
| serviceCode      | string  | 是   | 服务编码             |
| httpMethod       | string  | 是   | HTTP 方法            |
| pathPattern      | string  | 是   | 路径模式（完整路径） |
| matchOrder       | int     | 否   | 匹配优先级，默认 0   |
| enabled          | boolean | 否   | 是否启用，默认 true  |

---

## 10. 权限条件管理

### 10.1 功能描述

管理权限生效条件。条件可绑定到 `role_resource_permission`，运行时动态判定权限是否生效。

### 10.2 适用场景

- 仅工作日生效的权限（PRESET: `WORKDAY_ONLY`）。
- 仅内网IP可访问（PRESET: `INTERNAL_IP`）。
- 自定义表达式条件（CUSTOM: SpEL/OGNL 表达式）。

### 10.3 接口列表

#### 10.3.1 查询条件列表

```
GET /api/perm/conditions
```

**请求参数**

| 参数            | 类型    | 必填 | 说明                 |
| --------------- | ------- | ---- | -------------------- |
| conditionSource | string  | 否   | PRESET / CUSTOM      |
| enabled         | boolean | 否   | 启用状态             |
| keyword         | string  | 否   | 按 code 或 name 搜索 |

#### 10.3.2 创建条件

```
POST /api/perm/conditions
```

**请求体**

| 字段            | 类型    | 必填 | 说明                                                  |
| --------------- | ------- | ---- | ----------------------------------------------------- |
| code            | string  | 是   | 条件编码，租户内唯一                                  |
| name            | string  | 是   | 名称                                                  |
| conditionSource | string  | 是   | PRESET / CUSTOM                                       |
| expression      | string  | 是   | 表达式（PRESET 为 handler 编码，CUSTOM 为表达式文本） |
| enabled         | boolean | 否   | 是否启用，默认 true                                   |
| description     | string  | 否   | 说明                                                  |

**业务规则**

- `code` 在租户内唯一。
- 创建即可用（无审核流），通过 `enabled` 控制启停。

#### 10.3.3 更新条件

```
PUT /api/perm/conditions/{id}
```

**业务规则**

- `code` 不可修改。
- 修改 `enabled` 为 false 时，不影响已引用该条件的 role_resource_permission 记录，但鉴权时该条件视为不满足。

#### 10.3.4 删除条件

```
POST /api/perm/conditions/remove
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
GET /api/perm/users/{userId}/assignments
```

**请求参数**

| 参数       | 类型   | 必填 | 说明         |
| ---------- | ------ | ---- | ------------ |
| targetType | string | 否   | ROLE / GROUP |

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
    "targetType": "GROUP",
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
POST /api/perm/users/{userId}/assignments
```

**请求体**

```json
{
  "assignments": [
    {
      "targetType": "ROLE",
      "targetId": 10,
      "validFrom": null,
      "validTo": null
    },
    {
      "targetType": "GROUP",
      "targetId": 1,
      "validFrom": "2026-01-01T00:00:00Z",
      "validTo": "2026-12-31T23:59:59Z"
    }
  ]
}
```

**业务规则**

- `(tenant_id, abstract_user_id, target_type, target_id)` 唯一。已存在则更新 valid_from/valid_to。
- `target_type=GROUP` 时，不能关联默认分组。
- `target_type=ROLE` 时，校验角色存在且未删。
- `target_type=GROUP` 时，校验分组存在且未删，且不是默认分组。
- `validFrom/validTo` 均为可选，NULL 表示不限制。
- 写入 permission_change_log。
- 失效该用户的有效角色缓存。

#### 11.3.3 批量回收关联

```
POST /api/perm/users/{userId}/assignments/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

**业务规则**

- 软删对应的 user_role 记录。
- 写入 permission_change_log。
- 失效该用户的缓存。

#### 11.3.4 批量用户分配（按角色/分组视角）

```
POST /api/perm/assignments/batch
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
GET /api/perm/roles/{roleId}/permissions
```

**请求参数**

| 参数            | 类型    | 必填 | 说明                                              |
| --------------- | ------- | ---- | ------------------------------------------------- |
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
POST /api/perm/roles/{roleId}/permissions
```

**请求体**

```json
{
  "permissions": [
    {
      "resourceEntityId": 1,
      "operationPermissionId": 1,
      "canManage": false,
      "conditionId": null
    },
    {
      "resourceEntityId": 1,
      "operationPermissionId": 2,
      "canManage": true,
      "conditionId": 5
    }
  ]
}
```

**业务规则**

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
POST /api/perm/roles/{roleId}/permissions/remove
```

**请求体**

```json
{ "ids": [100, 101] }
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
GET /api/perm/permissions/{permId}/children
```

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
POST /api/perm/permissions/{permId}/children
```

**请求体**

```json
{
  "children": [
    {
      "resourceEntityId": 50,
      "operationPermissionId": 5
    }
  ]
}
```

**业务规则**

- `permId` 必须存在且 `depend_on IS NULL`（只能给主权限添加子权限）。
- 子权限的 `abstract_role_id` 继承自父权限。
- 子权限的 `depend_on = permId`。
- `resource_type` 冗余字段自动填充。
- 写入 permission_change_log。

#### 13.3.3 删除子权限

```
POST /api/perm/permissions/children/remove
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

管理域下的范围(SCOPE)、关系(RELATION)、绑定(BINDING)配置。三种配置统一存储在 `domain_config` 表。

### 14.2 适用场景

- 配置域下允许使用的角色类型、资源类型、操作。
- 配置域内角色类型与资源类型的可关联关系。
- 将全局角色/资源/操作绑定到特定域。

### 14.3 接口列表

#### 14.3.1 查询域配置

```
GET /api/perm/domains/{domainId}/config
```

**请求参数**

| 参数       | 类型   | 必填 | 说明                       |
| ---------- | ------ | ---- | -------------------------- |
| configType | string | 否   | SCOPE / RELATION / BINDING |

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
POST /api/perm/domains/{domainId}/config
```

**请求体**

| 字段       | 类型   | 必填 | 说明                       |
| ---------- | ------ | ---- | -------------------------- |
| configType | string | 是   | SCOPE / RELATION / BINDING |
| extra      | object | 是   | 配置内容 JSON              |

**extra 格式说明**

- **SCOPE**: `{"scope_type": "ROLE_TYPE|RESOURCE_TYPE|OPERATION", "scope_ref_id": <type_value 或 operation_id>}`
- **RELATION**: `{"relation_type": "ROLE_RESOURCE", "left_ref_id": <role_type_value>, "right_ref_id": <resource_type_value>}`
- **BINDING**: `{"bound_type": "ROLE|RESOURCE|OPERATION", "bound_entity_id": <实体ID>}`

**业务规则**

- SCOPE: `scope_ref_id` 须在 type_definition 或 operation_permission 中存在。
- RELATION: `left_ref_id` 和 `right_ref_id` 须在 type_definition 中存在。
- BINDING: `bound_entity_id` 须指向全局实体（`biz_domain_id IS NULL`）。

#### 14.3.3 删除域配置

```
POST /api/perm/domains/{domainId}/config/remove
```

**请求体**

```json
{ "ids": [1, 2] }
```

---

## 15. 资源依赖管理

### 15.1 功能描述

管理资源间的声明式依赖关系。由业务系统注册资源时自动维护，权限中心提供存储、查询和依赖完整性检查。

### 15.2 适用场景

- 报表资源依赖数据集资源：查看报表需要数据集的 DATA_READ 权限。
- 权限配置时提示"缺少依赖权限"。

### 15.3 接口列表

#### 15.3.1 查询资源依赖列表

```
GET /api/perm/resource-dependencies
```

**请求参数**

| 参数                      | 类型 | 必填 | 说明       |
| ------------------------- | ---- | ---- | ---------- |
| resourceEntityId          | long | 否   | 主体资源ID |
| dependsOnResourceEntityId | long | 否   | 依赖资源ID |

#### 15.3.2 添加资源依赖

```
POST /api/perm/resource-dependencies
```

**请求体**

| 字段                          | 类型 | 必填 | 说明                                          |
| ----------------------------- | ---- | ---- | --------------------------------------------- |
| resourceEntityId              | long | 是   | 主体资源ID                                    |
| dependsOnResourceEntityId     | long | 是   | 依赖资源ID                                    |
| sourceOperationPermissionId   | long | 否   | 仅当对主体资源做该操作时应用，NULL 为任意操作 |
| requiredOperationPermissionId | long | 是   | 对依赖资源所需的操作ID                        |

**业务规则**

- 主体资源和依赖资源须存在且未删。
- 写入时防环校验（检查新依赖是否形成循环）。
- `(tenant_id, resource_entity_id, depends_on_resource_entity_id, COALESCE(source_operation_permission_id, 0))` 唯一。

#### 15.3.3 删除资源依赖

```
POST /api/perm/resource-dependencies/remove
```

#### 15.3.4 查询依赖图

```
POST /api/perm/resource-dependencies/graph
```

**请求体**

| 字段             | 类型   | 必填 | 说明                                                      |
| ---------------- | ------ | ---- | --------------------------------------------------------- |
| resourceEntityId | long   | 是   | 起点资源ID                                                |
| graphMode        | string | 否   | AROUND(围绕)/UPSTREAM(上游)/DOWNSTREAM(下游)，默认 AROUND |

**响应 data**：边集合（source → target + operation）。

#### 15.3.5 检查依赖完整性

```
POST /api/perm/resource-dependencies/check
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

管理同资源下操作的互斥规则。查询/快照组装时检测冲突，冲突权限失效。

### 16.2 适用场景

- 同一用户对同一资源不能同时拥有"审批"和"提交"操作。
- 运行时冲突检测与异步通知管理员修正。

### 16.3 接口列表

#### 16.3.1 查询冲突规则列表

```
GET /api/perm/conflict-rules
```

#### 16.3.2 添加冲突规则

```
POST /api/perm/conflict-rules
```

**请求体**

| 字段                        | 类型 | 必填 | 说明                            |
| --------------------------- | ---- | ---- | ------------------------------- |
| firstOperationPermissionId  | long | 是   | 互斥操作一                      |
| secondOperationPermissionId | long | 是   | 互斥操作二                      |
| bizDomainId                 | long | 否   | 业务域ID，NULL 为全局           |
| resourceTypeValue           | int  | 否   | 仅指定资源类型生效，NULL 为所有 |

**业务规则**

- 存库时自动排序：`first_id < second_id`。
- `(tenant_id, biz_domain_id, first_id, second_id)` 唯一。

#### 16.3.3 删除冲突规则

```
POST /api/perm/conflict-rules/remove
```

#### 16.3.4 冲突检测

```
POST /api/perm/conflict-detection
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

核心鉴权接口，判断用户对资源的操作是否被授权。

### 17.2 接口列表

#### 17.2.1 权限检查

```
POST /api/perm/check
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

| reason            | 说明                 |
| ----------------- | -------------------- |
| NO_ROLE           | 用户无有效角色       |
| NO_PERMISSION     | 角色无该资源操作授权 |
| CONDITION_NOT_MET | 条件不满足           |
| CONFLICT_DETECTED | 冲突规则导致权限失效 |

#### 17.2.2 批量权限检查

```
POST /api/perm/check/batch
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

#### 17.2.3 接口级判定

```
POST /api/perm/decision/interface
```

**请求体**

| 字段           | 类型   | 必填 | 说明      |
| -------------- | ------ | ---- | --------- |
| abstractUserId | long   | 是   | 用户ID    |
| serviceCode    | string | 是   | 服务编码  |
| httpMethod     | string | 是   | HTTP 方法 |
| path           | string | 是   | 请求路径  |

**响应 data**

```json
{
  "allowed": true,
  "matchedResourceId": 5,
  "matchedOperationCode": "ACCESS"
}
```

**业务规则**

- 通过 `resource_api_mapping` 匹配路由。
- 按 `match_order` 排序，取第一个匹配的。
- 路径支持 Ant 风格匹配（`/api/users/**`、`/api/users/{id}`）。

---

## 18. 权限版本与快照

### 18.1 功能描述

权限版本管理和接口快照服务，供 gateway 运行时鉴权使用。

### 18.2 接口列表

#### 18.2.1 查询当前版本

```
POST /api/perm/version/query
```

**响应 data**

```json
{
  "tenantId": 1,
  "versionNo": 42,
  "triggerEntityType": "role_resource_permission",
  "triggeredAt": "2026-04-18T10:00:00Z"
}
```

#### 18.2.2 接口权限快照

```
POST /api/perm/policy/interface-snapshot
```

**请求体**

| 字段           | 类型 | 必填 | 说明                                   |
| -------------- | ---- | ---- | -------------------------------------- |
| abstractUserId | long | 是   | 用户ID                                 |
| versionNo      | long | 否   | 客户端当前持有的版本号（用于增量判断） |

**响应 data**

```json
{
  "versionNo": 42,
  "changed": true,
  "rules": [
    {
      "serviceCode": "user-service",
      "httpMethod": "GET",
      "pathPattern": "/api/v1/users",
      "capabilityCode": "sys:user:list:ACCESS",
      "resourceEntityId": 5,
      "operationCode": "ACCESS"
    }
  ]
}
```

**业务规则**

- 若传入 `versionNo` 等于当前版本，返回 `changed=false`，`rules` 为空。
- 快照仅包含 `condition_id IS NULL` 的授权（无条件授权）。
- 冲突权限从快照中排除。
- gateway 本地缓存按 `(tenant_id, abstract_user_id, versionNo)` 组织。

---

## 19. 变更记录查询

### 19.1 功能描述

查询权限变更记录，支持多维度过滤。

### 19.2 接口列表

#### 19.2.1 查询变更记录

```
POST /api/perm/change-logs
```

**请求体**

| 字段           | 类型     | 必填 | 说明           |
| -------------- | -------- | ---- | -------------- |
| abstractUserId | long     | 否   | 按影响用户过滤 |
| abstractRoleId | long     | 否   | 按影响角色过滤 |
| bizDomainId    | long     | 否   | 按业务域过滤   |
| entityType     | string   | 否   | 变更实体类型   |
| requestId      | string   | 否   | 请求追踪ID     |
| startTime      | datetime | 否   | 开始时间       |
| endTime        | datetime | 否   | 结束时间       |
| pageNum        | int      | 否   | 页码           |
| pageSize       | int      | 否   | 每页条数       |

**响应 data**

```json
{
  "rows": [
    {
      "id": 1,
      "entityType": "role_resource_permission",
      "entityId": 100,
      "operation": "INSERT",
      "oldSnapshot": null,
      "newSnapshot": { "roleId": 10, "resourceId": 1, "operationId": 1 },
      "affectedUserIds": [1, 2, 3],
      "affectedRoleIds": [10],
      "changeReason": "初始配置",
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

为用户提供"我的权限"视图，支持简洁视图和来源明细视图。

### 20.2 接口列表

#### 20.2.1 查询用户有效角色

```
GET /api/perm/users/{userId}/effective-roles
```

**请求参数**

| 参数          | 类型    | 必填 | 说明                         |
| ------------- | ------- | ---- | ---------------------------- |
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
GET /api/perm/users/{userId}/effective-permissions
```

**请求参数**

| 参数             | 类型    | 必填 | 说明                                   |
| ---------------- | ------- | ---- | -------------------------------------- |
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
GET /api/perm/users/{userId}/resource-tree
```

**请求参数**

| 参数         | 类型 | 必填 | 说明     |
| ------------ | ---- | ---- | -------- |
| bizDomainId  | long | 否   | 业务域ID |
| resourceType | int  | 否   | 资源类型 |

**响应 data**：资源树结构，每个节点附带用户拥有的操作列表。

---

## 附录：枚举值速查

### target_type（user_role）

| 值    | 说明     |
| ----- | -------- |
| ROLE  | 关联角色 |
| GROUP | 关联分组 |

### config_type（domain_config）

| 值       | 说明                                   |
| -------- | -------------------------------------- |
| SCOPE    | 域范围（允许的角色类型/资源类型/操作） |
| RELATION | 域关系（角色类型-资源类型可关联关系）  |
| BINDING  | 域绑定（全局实体绑定到域）             |

### condition_source（permission_condition）

| 值     | 说明                     |
| ------ | ------------------------ |
| PRESET | 系统预设（handler 编码） |
| CUSTOM | 自定义（表达式文本）     |

### change_source（permission_change_log）

| 值      | 说明                   |
| ------- | ---------------------- |
| ADMIN   | 管理端操作             |
| MQ_SYNC | 消息队列同步           |
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

| 值                | 说明                 |
| ----------------- | -------------------- |
| NO_ROLE           | 用户无有效角色       |
| NO_PERMISSION     | 角色无该资源操作授权 |
| CONDITION_NOT_MET | 条件不满足           |
| CONFLICT_DETECTED | 冲突规则导致权限失效 |
