# Phase 0 数据模型字典

本文档仅整理 Phase 0 已冻结的数据模型，不新增表、不新增字段、不修改现有语义。内容基于 `permission_center_schema.sql`、`DESIGN.md`。

## 1. 范围说明

- 冻结对象：`permission_center_schema.sql` 中 17 张表。
- 冻结约束：无外键、统一租户字段、统一审计字段、统一软删规则。
- 冻结首期范围：
  - 主体类型：`USER`、`SERVICE`、`DELEGATED`
  - 资源类型：`MENU`、`API`、`DATA`、`BUTTON`
  - 条件策略：精确鉴权支持条件；接口快照不下发带条件授权

## 2. 通用字段与规则

### 2.1 通用字段

除 `permission_change_log` 外，所有事实表均包含：

- `tenant_id`
- `created_by`
- `updated_by`
- `deleted_by`
- `created_at`
- `updated_at`
- `deleted_at`
- `delete_flag`

`permission_change_log` 保留：

- `tenant_id`
- `created_by`
- `created_at`

### 2.2 软删规则

- `delete_flag = 0` 表示未删除
- 删除时 `delete_flag = 本行 id`
- `deleted_at` 仅用于审计展示，不参与唯一索引条件
- 唯一索引与业务查询统一以 `WHERE delete_flag = 0` 为准

### 2.3 建模规则

- 不使用数据库外键，逻辑关联由应用保证
- 所有查询必须带租户条件
- 资源树继承不在表结构中建模，由 `inherit_mode` 控制查询行为
- 操作继承通过 `operation_permission.binary_bit | inherit_mask` 表达

## 3. 表清单

| 表名 | 职责 | 关键字段 | 关键约束/说明 |
|------|------|----------|---------------|
| `type_definition` | 类型枚举定义 | `type_key`, `type_value`, `biz_domain_id` | 按 `(type_key, type_value)` 查询；`biz_domain_id` 可空表示租户全局 |
| `biz_domain` | 业务域定义 | `code`, `name` | 域编码在租户内唯一 |
| `abstract_user` | 抽象主体 | `user_type`, `external_id`, `name` | 不含 `biz_domain_id`，通过角色关联到域 |
| `abstract_role` | 抽象角色 | `biz_domain_id`, `role_type`, `parent_id`, `path`, `name` | 支持树形；`biz_domain_id` 为空表示全局角色 |
| `operation_permission` | 操作权限定义 | `resource_type`, `code`, `binary_bit`, `inherit_mask` | `resource_type` 可空表示适用于全部资源类型 |
| `resource_entity` | 资源实体 | `biz_domain_id`, `parent_id`, `code`, `resource_type`, `path` | 支持树形；`biz_domain_id` 为空表示全局资源 |
| `resource_api_mapping` | API 资源与路由映射 | `resource_entity_id`, `service_code`, `http_method`, `path_pattern`, `match_order` | 面向 gateway 快照消费 |
| `permission_condition` | 权限生效条件 | `code`, `condition_source`, `expression`, `status` | `PRESET` 为系统预设；`CUSTOM` 需审核 |
| `user_role` | 用户与角色关联 | `abstract_user_id`, `abstract_role_id`, `valid_from`, `valid_to` | 支持有效期 |
| `role_resource_permission` | 角色对资源操作的授权 | `abstract_role_id`, `resource_entity_id`, `operation_permission_id`, `can_manage`, `condition_id` | `condition_id` 为空表示无条件授权 |
| `domain_scope_config` | 域范围配置 | `biz_domain_id`, `scope_type`, `scope_ref_id` | `scope_type` 为 `ROLE_TYPE`、`RESOURCE_TYPE`、`OPERATION` |
| `domain_relation_config` | 域关系配置 | `biz_domain_id`, `relation_type`, `left_ref_id`, `right_ref_id` | 当前仅 `ROLE_RESOURCE` |
| `domain_scope_binding` | 域引用绑定 | `biz_domain_id`, `bound_type`, `bound_entity_id` | 将全局角色/资源/操作绑定到指定域 |
| `resource_dependency` | 资源依赖关系 | `resource_entity_id`, `depends_on_resource_entity_id`, `source_operation_permission_id`, `required_operation_permission_id` | 声明式元数据；写入时校验防环 |
| `permission_conflict_rule` | 权限冲突规则 | `first_operation_permission_id`, `second_operation_permission_id`, `resource_type_value` | 查询时检测冲突；写入时要求 `first_id < second_id` |
| `permission_version` | 权限版本游标 | `version_no`, `trigger_entity_type`, `trigger_entity_id` | 按租户递增 |
| `permission_change_log` | 权限变更审计 | `entity_type`, `entity_id`, `operation`, `old_snapshot`, `new_snapshot`, `request_id` | 支持按用户、角色、时间、请求号检索 |

## 4. 首期枚举冻结

### 4.1 `type_definition.type_key`

- `user_type`
- `role_type`
- `resource_type`

### 4.2 首期主体类型

| 类型 | 说明 |
|------|------|
| `USER` | 人类用户主体 |
| `SERVICE` | 服务主体 |
| `DELEGATED` | 服务携带用户委托上下文后的复合主体 |

### 4.3 首期资源类型

| 类型 | 说明 |
|------|------|
| `MENU` | 菜单类资源 |
| `API` | 接口类资源 |
| `DATA` | 数据类资源 |
| `BUTTON` | 按钮类资源 |

### 4.4 条件与状态枚举

| 字段 | 已冻结取值 |
|------|------------|
| `permission_condition.condition_source` | `PRESET`、`CUSTOM` |
| `permission_condition.status` | `APPROVED`、`PENDING`、`REJECTED` |
| `domain_scope_config.scope_type` | `ROLE_TYPE`、`RESOURCE_TYPE`、`OPERATION` |
| `domain_relation_config.relation_type` | `ROLE_RESOURCE` |
| `domain_scope_binding.bound_type` | `ROLE`、`RESOURCE`、`OPERATION` |

### 4.5 首批操作编码

| 操作编码 | 说明 |
|----------|------|
| `VIEW` | 查看类操作 |
| `EDIT` | 编辑类操作 |
| `ACCESS` | 接口类资源默认操作编码 |
| `DATA_READ` | 数据读取类操作 |
| `DATA_EXPORT` | 数据导出类操作 |

补充说明：

- Phase 0 决策：接口类资源默认操作编码统一为 `ACCESS`。
- 首批操作位值约定：
  - `VIEW`：`binary_bit=1`，`inherit_mask=0`
  - `EDIT`：`binary_bit=4`，`inherit_mask=1`
  - `ACCESS`：`binary_bit=8`，`inherit_mask=0`
  - `DATA_READ`：`binary_bit=16`，`inherit_mask=0`
  - `DATA_EXPORT`：`binary_bit=32`，`inherit_mask=0`

## 5. 运行时语义冻结

### 5.1 资源继承语义

| `inherit_mode` | 含义 |
|----------------|------|
| `NONE` | 仅检查当前资源 |
| `CHILDREN` | 向下展开子资源 |
| `PARENT` | 向上检查父资源 |
| `BOTH` | 同时检查父子资源 |

### 5.2 操作继承语义

- `effective = binary_bit | inherit_mask`
- `operation_permission.resource_type` 直接表示操作适用资源类型
- `NULL` 表示操作适用于全部资源类型

### 5.3 快照边界

- 接口快照仅面向 `API` 类型资源
- `condition_id != null` 的授权不进入首期接口快照
- 冲突授权在查询/快照组装时失效，不在写入时阻止

## 6. 数据模型验收基线

- 17 张表名称、职责、关键字段与现有 DDL 保持一致
- `DESIGN.md`、`MIXED_KERNEL_ARCHITECTURE.md`、`PERMISSION_SERVICE_DESIGN.md` 的关键术语与本字典一致
- 后续开发不允许绕开 `type_definition`、`permission_version`、`resource_api_mapping`、`role_resource_permission` 等核心术语另起别名
