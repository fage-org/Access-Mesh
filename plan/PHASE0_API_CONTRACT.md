# Phase 0 API 契约说明

本文档仅冻结 Phase 0 已有接口边界与数据结构，内容来自 `DESIGN.md`、`PERMISSION_SERVICE_DESIGN.md`、`MIXED_KERNEL_ARCHITECTURE.md`，不新增接口能力。

## 1. 契约范围

- 服务边界：
  - `gateway` 只消费快照、判定与版本接口
  - `identity-service` 登录时只查询版本，不直接拉取完整权限快照
  - `permission-center` 提供权限查询、判定、版本、治理与审计接口
- 首期接口协议：
  - 统一采用 `POST + JSON` 为主
  - 文档中已存在的 `GET/PUT/DELETE` 管理接口按现有设计冻结
- 术语约定：
  - API 请求字段中的 `userId`，在现有 Phase 0 语义上按 `abstract_user_id` 解释
  - 换言之：接口层命名使用 `userId`，事实层与落库语义统一对应 `abstract_user_id`
  - `permissionVersion` 作为运行时对外字段名，底层事实模型对应 `permission_version.version_no`

## 2. 对外接口清单

| 能力 | 接口 | 说明 |
|------|------|------|
| 鉴权 | `POST /api/perm/check` | 精确鉴权 |
| 用户角色 | `GET/POST/DELETE /api/perm/users/{userId}/roles` | 用户角色列表、分配、回收 |
| 角色权限 | `GET/POST/DELETE /api/perm/roles/{roleId}/permissions` | 角色授权列表、添加、回收 |
| 域配置 | `GET/PUT /api/perm/domains/{domainId}/scope` | 域范围配置 |
| 域关系 | `GET/PUT /api/perm/domains/{domainId}/relation` | 域关系配置 |
| 域引用 | `GET/PUT /api/perm/domains/{domainId}/binding` | 域引用绑定 |
| 权限条件 | `GET/POST/PUT /api/perm/conditions` | 条件 CRUD 与审核 |
| 资源依赖 | `GET/POST/DELETE /api/perm/resource-dependencies` | 资源依赖维护 |
| 冲突规则 | `GET/POST/DELETE /api/perm/conflict-rules` | 冲突规则维护 |
| 冲突检测 | `POST /api/perm/conflict-detection` | 冲突扫描 |
| 变更记录 | `POST /api/perm/change-logs` | 审计查询 |
| 接口快照 | `POST /api/perm/policy/interface-snapshot` | 面向 gateway 的接口权限快照 |
| 接口判定 | `POST /api/perm/decision/interface` | 单次接口访问判定 |
| 版本查询 | `POST /api/perm/version/query` | 查询当前权限版本 |

## 3. 核心请求/响应结构

### 3.1 精确鉴权

请求体：

```json
{
  "tenantId": 1,
  "userId": 1001,
  "resourceEntityId": 2001,
  "operationPermissionId": 3001,
  "bizDomainId": 4001,
  "inheritMode": "NONE",
  "checkDependency": false,
  "context": {}
}
```

字段说明：

| 字段 | 必填 | 说明 |
|------|------|------|
| `tenantId` | 是 | 租户 ID |
| `userId` | 是 | 当前主体 ID，按现有设计解释为 `abstract_user_id` |
| `resourceEntityId` | 是 | 资源实体 ID |
| `operationPermissionId` | 是 | 操作权限 ID |
| `bizDomainId` | 否 | 业务域 ID |
| `inheritMode` | 否 | `NONE`、`CHILDREN`、`PARENT`、`BOTH`，默认 `NONE` |
| `checkDependency` | 否 | 是否同时校验依赖链，默认 `false` |
| `context` | 否 | 条件评估上下文 |

响应体：

```json
{
  "granted": true,
  "denyReason": null,
  "grantedBy": [],
  "conflicts": [],
  "dependencyGaps": []
}
```

响应字段冻结：

| 字段 | 说明 |
|------|------|
| `granted` | 是否通过 |
| `denyReason` | 未通过时的拒绝原因 |
| `grantedBy` | 命中的授权来源 |
| `conflicts` | 检测到的冲突详情 |
| `dependencyGaps` | 依赖缺口 |

### 3.2 接口快照

请求体：

```json
{
  "tenantId": 1,
  "userId": 1001,
  "bizDomainId": 4001
}
```

响应体：

```json
{
  "entries": [
    {
      "resourceCode": "user:list",
      "operationCode": "ACCESS",
      "serviceCode": "system-service",
      "httpMethod": "GET",
      "pathPattern": "/api/system/user/list",
      "extra": {}
    }
  ],
  "permissionVersion": 12
}
```

响应字段：

| 字段 | 说明 |
|------|------|
| `entries` | 接口权限快照条目列表 |
| `entries[].resourceCode` | 资源编码 |
| `entries[].operationCode` | 操作编码 |
| `entries[].serviceCode` | 服务编码 |
| `entries[].httpMethod` | HTTP 方法 |
| `entries[].pathPattern` | 路径模式 |
| `entries[].extra` | 扩展字段 |
| `permissionVersion` | 当前快照对应的权限版本 |

快照边界冻结：

- 仅输出接口快照消费所需字段
- 仅输出 `API` 类型资源
- 仅输出无条件授权
- 冲突项不进入快照

### 3.3 版本查询

请求体：

```json
{
  "tenantId": 1
}
```

响应语义：

- 至少返回当前租户权限版本号
- 现有执行计划在后续阶段提到“输出更新时间”，但 Phase 0 不单独冻结该字段名与响应结构

### 3.4 接口判定

请求语义：

- 按 `service_code + http_method + path` 做单次接口判定
- 供 gateway 或运行时组件按需调用

## 4. 拒绝原因冻结

`/api/perm/check` 中 `denyReason` 仅允许使用以下值：

- `NO_ROLE`
- `NO_PERMISSION`
- `CONDITION_FAIL`
- `CONFLICT`
- `DEPENDENCY_FAIL`

## 5. gateway / identity-service 对接边界

### 5.1 gateway

- 不直接查库
- 只消费：
  - `POST /api/perm/policy/interface-snapshot`
  - `POST /api/perm/decision/interface`
  - `POST /api/perm/version/query`
- Phase 0 当前最小实现口径按 `(tenantId, abstract_user_id, permissionVersion)` 组织
- 后续执行计划中出现过 `(tenantId, subjectKey, permissionVersion)` 表述，但该口径不在 Phase 0 自行覆盖当前最小实现约定

### 5.2 identity-service

- 登录时只查询 `permissionVersion`
- 下列令牌字段来自现有执行计划的运行时目标口径引用，不视为 Phase 0 已交付的令牌结构冻结项：
  - `tenantId`
  - `subjectId`
  - `subjectKey`
  - `subjectType`
  - `permissionVersion`
  - `delegationContext`
- 不在登录时直接拉取完整权限快照

## 6. 契约约束

- Phase 0 只冻结接口名称、职责、请求/响应骨架与边界
- 具体 HTTP 状态码、异常包装结构、鉴权网关内部缓存实现，当前文档未额外扩展
- 后续阶段如需细化，只能在不推翻本基线的前提下补充
