# 通用权限中心 - 详细设计文档

本文档面向 AI 或开发人员实现权限中心后端与管理端，与 `permission_center_schema.sql` 配套使用。

---

## 1. 概述与架构

### 1.1 设计原则

- **无数据库外键**：所有关联为逻辑 ID，由应用保证一致性。
- **租户隔离**：所有表带 `tenant_id`，查询必须带租户条件。SaaS 多租户模式。
- **软删除**：统一使用 `delete_flag`（`0` = 未删除，删除时填本行 id），唯一约束均带 `WHERE delete_flag = 0`。`deleted_at` 仅为审计展示字段，不参与索引条件。
- **审计字段**：每表含 `created_by`、`updated_by`、`deleted_by`、`created_at`、`updated_at`、`deleted_at`。
- **原表为事实层**：`abstract_user`、`abstract_role`、`user_role`、`resource_entity`、`operation_permission`、`role_resource_permission` 等原表是权限事实来源。
- **kernel 为消费层**：`gateway`、`identity-service` 与其他运行时组件只消费 `permission-center` 对外暴露的查询/判定/版本接口。
- **接口映射显式建模**：接口资源与 HTTP 路由关系通过 `resource_api_mapping` 维护。
- **接口注册制**：Java 程序通过 SpringBoot 注解收集接口信息并上报；其他语言框架通过接口文档由接入方自行实现上报；同时支持管理端手动配置。
- **运行时版本独立维护**：权限变更后的缓存刷新依据 `permission_version`（角色级粒度）。
- **操作绑定资源类型**：`operation_permission.resource_type` 直接表达操作适用的资源类型。创建 `resource_type` 时自动预置 CRUD 四个操作。
- **位运算操作继承**：`binary_bit + inherit_mask`（BIGINT，63 位）表达操作间继承关系，`effective = binary_bit | inherit_mask`。
- **条件权限 JSONB 规则**：`permission_condition.condition_rules` 存完整条件定义（支持条件组），同一条件被多个权限引用时可复用计算结果。
- **冲突双模式**：角色互斥（写入时检查拒绝）+ 权限互斥（查询时失效 + 异步通知修正），查询时实时计算 + TTL 缓存。
- **资源依赖操作位级别**：`resource_dependency` 使用 `source_operation_bits` 作为触发条件，`required_operation_bits` 指定依赖资源所需操作位，`auto_grant=true` 时授权时自动补全。授权记录标记 `grant_source=AUTO_DEP` + `grant_dep_id`，依赖规则变更时按标记精准清理。
- **资源继承查询方控制**：资源树的继承展开（子资源/父资源）由查询接口参数控制，不在表结构中定义。
- **树形角色一体化**：`abstract_role` 通过 `parent_id` 支持树形，去除 `role_group` 和 `role_group_role` 表。角色类型区分层级能力：ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE，其中 ORG/GROUP_ROLE 支持子级，BASIC_ROLE/PERSONAL/POSITION 为平铺。
- **子权限/数据权限**：通过 `role_resource_permission.depend_on` 自引用实现父子权限关系（单层）。子权限可以是任意权限类型（通过 `domain_config` SUB_PERM 配置）。数据权限是一种 `resource_entity`（`resource_type=DATA`），权限中心只管存储和查询。
- **域配置合并**：`domain_config` 一张表统一管理 SCOPE/RELATION/BINDING/SUB_PERM，使用 `config_type + extra(JSONB)` 区分。每个域独立配置，无继承。
- **资源多编码体系**：`resource_entity` 支持 `code_type` 字段，同一资源可有多行不同编码类型（如 "default"/"en"/"cn"），查询权限时传 `code_type` 参数返回对应编码。
- **双日志体系**：`operation_log`（轻量全量记录所有写操作）+ `permission_change_log`（详细权限变更 diff），便于审计和排查权限问题。
- **鉴权三模式**：网关拦截接口权限、服务端单查、服务端批量查。网关回调权限中心进行接口级判定（含 context 条件评估），权限中心内部读 Redis 双份数据完成鉴权。未注册接口默认拒绝（白名单模式）。
- **所有接口 POST + JSON Body**：权限中心所有 API 统一使用 POST 方法 + JSON 请求体，无 URL 路径参数。

### 1.2 核心概念

| 概念                                      | 说明                                                                                                                                                                    |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 类型定义 (type_definition)                | 类型枚举 KV：预置 user_type/role_type/resource_type/group_type，type_value 为 INT。系统预置 + 租户可扩展（`is_system` 标记）。创建 resource_type 时自动预置 CRUD 操作。 |
| 业务域 (biz_domain)                       | 对权限对象分类，控制数据量与管理边界。扁平列表，无启停，引用检查拒删。                                                                                                  |
| 抽象用户 (abstract_user)                  | 对应具体业务的人/服务，通过 user_type 区分。`enabled` 字段控制启停，停用后鉴权不通过。支持外部系统 API 同步（幂等，带 version 防乱序）。创建时自动创建个人角色。                        |
| 抽象角色 (abstract_role)                  | 树形角色（通过 parent_id 支持层级），`status` 控制启停。角色类型决定行为：ORG(组织)/POSITION(职位)/PERSONAL(个人)/GROUP_ROLE(分组角色)/BASIC_ROLE(基本角色)。GROUP_ROLE 不直接配置权限，通过 extra.basicRoleIds 额外关联基本角色。BASIC_ROLE/PERSONAL/POSITION 不可有子级。 |
| 操作权限 (operation_permission)           | 绑定 resource_type；用 binary_bit + inherit_mask 表达继承。每个 resource_type 最多 63 个操作。无启停，用删除代替。                                                      |
| 权限资源实体 (resource_entity)            | 支持树形，支持 `code_type` 多编码体系。`status` 控制启停。数据权限也是一种资源实体（DATA 类型）。                                                                       |
| 接口资源映射 (resource_api_mapping)       | 接口类资源到 `service_code + http_method + path_pattern` 的显式映射。                                                                                                   |
| 服务配置 (service_config)                 | 接入服务注册配置，`status` 控制启停，停用后其接口不参与授权。全量同步策略。                                                                                             |
| 权限条件 (permission_condition)           | `condition_rules` JSONB 存完整条件定义（条件组），支持 DATE_RANGE/TIME_RANGE/IP_WHITELIST/IP_BLACKLIST。条件可复用。                                                    |
| 用户关联 (user_role)                      | 用户与角色的统一关联表。`target_id` 指向 `abstract_role.id`，`target_type` 标记角色类型。POSITION 类型时 `relation_id` 记录所属组织，决定数据权限范围。可带 valid_from/valid_to。 |
| 角色-资源-操作 (role_resource_permission) | 角色对某资源在某操作上的授权。支持子权限(depend_on)、条件(condition_id)、管理权(can_manage)。批量授权格式 {add,update,delete}。                                         |
| 域配置 (domain_config)                    | 统一管理 SCOPE/RELATION/BINDING/SUB_PERM。每个域独立，无继承，变更即时生效。                                                                                            |
| 资源依赖 (resource_dependency)            | 操作位级别触发，支持自动补全。`auto_grant` 补全时标记 `grant_source` + `grant_dep_id`，规则变更时按标记清理。由外部系统通过接口维护。 |                                                                                                                  |
| 权限冲突规则 (permission_conflict_rule)   | 角色互斥（ROLE_MUTEX，写入拒绝）+ 权限互斥（PERM_MUTEX，查询失效）。                                                                                                    |
| 权限版本 (permission_version)             | 角色级粒度，权限变更时自动递增，仅用于缓存失效，不存快照。                                                                                                              |
| 权限变更记录 (permission_change_log)      | 详细权限变更 diff（before/after/diff），方便排查权限问题。永久保留。                                                                                                    |
| 系统配置 (system_config)                  | 租户级配置，如角色名唯一性策略、未注册接口默认策略等。                                                                                                                  |
| 操作日志 (operation_log)                  | 轻量全量记录所有写操作，永久保留。                                                                                                                                      |

### 1.3 逻辑关系简图

```
type_definition  (type_key: user_type / role_type / resource_type / group_type, is_system)
                 (extra 可存 max_depth 等扩展配置)
                 (创建 resource_type 时自动预置 CRUD 操作到 operation_permission)

biz_domain
    ├── abstract_role (树形角色，biz_domain_id 可空=全局，status 启停)
    │     ├── ORG 角色：树形，同步自 sys_org
    │     ├── POSITION 角色：平铺，分配时 user_role.relation_id 指定组织
    │     ├── PERSONAL 角色：平铺，每用户1个
    │     ├── GROUP_ROLE：树形，不直接配置权限，extra.basicRoleIds 关联基本角色
    │     └── BASIC_ROLE：平铺，承载实际权限配置
    ├── resource_entity (biz_domain_id 可空=全局, code_type 多编码, status 启停)
    ├── domain_config (config_type: SCOPE / RELATION / BINDING / SUB_PERM)
    └── permission_conflict_rule (biz_domain_id 可空=全局, ROLE_MUTEX / PERM_MUTEX)

system_config (租户级配置: ROLE_NAME_UNIQUE_MODE 等)

operation_permission (resource_type 可空=全局, binary_bit + inherit_mask)

abstract_user (enabled 启停) --[user_role(target_type=ROLE/ORG/POSITION/PERSONAL/GROUP_ROLE)]--> abstract_role

abstract_role --[role_resource_permission]--> resource_entity + operation_permission
role_resource_permission.depend_on --> role_resource_permission (子权限，单层)
role_resource_permission 可带 condition_id --> permission_condition (JSONB 规则)

resource_entity --[resource_api_mapping]--> service_code + http_method + path_pattern
service_config --> service_code 的基础配置（status 启停）
resource_entity --[resource_dependency]--> resource_entity (操作位级别触发，自动补全)

permission_version (角色级粒度) --> 缓存失效
operation_log (轻量全量)
permission_change_log (权限变更 diff)

用户有效角色解析链：
  user_role(target_id → abstract_role.id)
  对 GROUP_ROLE 类型：递归展开子角色(parent_id) + extra.basicRoleIds
  对 POSITION 类型：通过 relation_id 决定数据权限范围
  过滤 status=1 的角色 → 合并去重 → 用户的有效角色集合（纯缓存方案）
```

---

## 2. 表清单与用途

| #   | 表名                     | 用途                       | 关键字段                                                                            |
| --- | ------------------------ | -------------------------- | ----------------------------------------------------------------------------------- |
| 1   | type_definition          | 类型枚举 KV（预置+可扩展） | tenant_id, biz_domain_id, type_key, type_value, is_system, extra                    |
| 2   | biz_domain               | 业务域（扁平，无启停）     | tenant_id, code, name                                                               |
| 3   | abstract_user            | 抽象用户                   | tenant_id, user_type, external_id, name, **enabled**                                |
| 4   | abstract_role            | 抽象角色（树形）           | tenant_id, biz_domain_id, **parent_id**, role_type, name, **status**                |
| 5   | operation_permission     | 操作权限（绑定资源类型）   | tenant_id, resource_type, code, binary_bit, inherit_mask                            |
| 6   | resource_entity          | 资源实体（树形+多编码）    | tenant_id, biz_domain_id, resource_type, code, **code_type**, **status**            |
| 7   | resource_api_mapping     | 接口资源映射               | tenant_id, resource_entity_id, service_code, http_method, path_pattern              |
| 8   | service_config           | 接入服务配置               | tenant_id, service_code, base_path, **status**                                      |
| 9   | permission_condition     | 权限生效条件（JSONB规则）  | tenant_id, code, **condition_rules**, enabled                                       |
| 10  | user_role                | 用户-角色关联              | tenant_id, abstract_user_id, target_type, target_id, **relation_id**, valid_from/to |
| 11  | role_resource_permission | 角色-资源-操作（子权限）   | tenant_id, abstract_role_id, resource_entity_id, op_id, depend_on, condition_id, **grant_source**, **grant_dep_id** |
| 12  | domain_config            | 域配置（四合一）           | tenant_id, biz_domain_id, config_type(SCOPE/RELATION/BINDING/SUB_PERM), extra       |
| 13  | resource_dependency      | 资源依赖（操作位级别）     | resource_entity_id, depends_on_id, source_operation_bits, required_bits, auto_grant |
| 14  | permission_conflict_rule | 冲突规则（角色+权限互斥）  | conflict_type(ROLE_MUTEX/PERM_MUTEX), role_ids/operation_ids                        |
| 15  | permission_version       | 权限版本（角色级粒度）     | tenant_id, **abstract_role_id**, version_no                                         |
| 16  | permission_change_log    | 权限变更记录（详细diff）   | entity_type, old/new/diff_snapshot, affected_ids, request_id                        |
| 17  | system_config            | 系统配置（租户级）         | tenant_id, config_key, config_value JSONB                                           |
| 18  | operation_log            | 操作日志（轻量全量）       | module, action, target_type/id, summary, operator                                   |

---

## 3. 类型与枚举

### 3.1 type_definition 预置类型

- **user_type**：USER(1) 人员、SERVICE(2) 服务
- **role_type**：ORG(1) 组织、POSITION(2) 职位、PERSONAL(3) 个人、GROUP_ROLE(5) 分组角色、BASIC_ROLE(6) 基本角色。GROUP_ROLE 不可直接配置权限，通过 extra.basicRoleIds 额外关联基本角色。BASIC_ROLE/PERSONAL/POSITION 不可有子级。
- **resource_type**：MENU(1) 菜单、BUTTON(2) 按钮、API(3) 接口、DATA(4) 数据。DATA 类型用于数据权限范围（如城市、部门等维度）。创建新 resource_type 时自动预置 CRUD 四个 operation_permission。
- **group_type**：不预置，租户自定义。

### 3.2 CRUD 操作预置

创建 resource_type 时自动生成四个操作：

| 操作   | code   | binary_bit | inherit_mask | effective | 说明              |
| ------ | ------ | ---------- | ------------ | --------- | ----------------- |
| CREATE | CREATE | 1          | 0            | 1         | 创建              |
| READ   | READ   | 2          | 0            | 2         | 读取              |
| UPDATE | UPDATE | 4          | 2            | 6         | 修改（继承 READ） |
| DELETE | DELETE | 8          | 2            | 10        | 删除（继承 READ） |

不预置 ACCESS 操作，API 资源复用 CRUD。用户可自行添加更多操作（最多 63 个/resource_type）。

### 3.3 其他枚举

- **user_role.target_type**：`ROLE` | `ORG` | `POSITION` | `PERSONAL` | `GROUP_ROLE`，与 abstract_role.role_type 对应。
- **domain_config.config_type**：`SCOPE` | `RELATION` | `BINDING` | `SUB_PERM`。
- **permission_conflict_rule.conflict_type**：`ROLE_MUTEX` | `PERM_MUTEX`。
- **permission_change_log.entity_type**：`user_role` | `role_resource_permission` | `abstract_user` | `abstract_role` 等。
- **permission_change_log.change_source**：`ADMIN` | `SYNC` | `API` | `SYSTEM`。
- **permission_condition.condition_rules.items[].type**：`DATE_RANGE` | `TIME_RANGE` | `IP_WHITELIST` | `IP_BLACKLIST`。
- **system_config.config_key**：`ROLE_NAME_UNIQUE_MODE`（角色名唯一性）| `UNREGISTERED_API_POLICY`（未注册接口策略）等。

---

## 4. 角色模型

### 4.1 核心规则

- **抽象角色（abstract_role）**：通过 `parent_id` 支持树形层级，`status` 字段控制启停（0=停用/1=启用），停用角色不参与鉴权。角色类型（role_type）决定角色的行为和层级能力。
- **角色类型行为差异**：

  | 角色类型     | 层级 | 可配权限 | 子级 | 说明 |
  | ------------ | ---- | -------- | ---- | ---- |
  | ORG(1)       | 是   | 是       | 可   | 组织树，同步自 `sys_org` |
  | POSITION(2)  | 否   | 是       | 否   | 职位角色，分配给用户时 `user_role.relation_id` 记录所属组织，决定数据权限范围。组织归属约束在 `admin-service` 处理 |
  | PERSONAL(3)  | 否   | 是       | 否   | 个人角色，每用户1个，用户创建时自动生成 |
  | GROUP_ROLE(5)| 是   | **否**   | 可   | 分组角色，不直接配置权限，通过 `extra.basicRoleIds` 额外关联基本角色。应用层+CHECK约束禁止配置权限 |
  | BASIC_ROLE(6)| 否   | 是       | 否   | 常规角色，承载实际权限配置 |

- **个人角色**：用户创建时自动创建个人角色 `PERSONAL_{external_id}`，role_type=PERSONAL(3)。每用户最多 1 个个人角色，不在管理界面展示。写入 `user_role(target_type=PERSONAL, target_id=personalRoleId)`。
- **角色名唯一性**：可配置（租户级 `system_config`）。方案一：不限制（允许同名）；方案二：(tenant_id, biz_domain_id, name) 唯一。由 `system_config.config_key='ROLE_NAME_UNIQUE_MODE'` 控制。
- **无权限继承**：树形仅用于层级管理和批量关联，不继承权限。用户关联到 GROUP_ROLE 后，自动获得该分组角色及其递归子角色下所有 BASIC_ROLE 的权限。
- **分组角色可移动**：GROUP_ROLE 可以修改 `parent_id` 移动到其他父分组下。
- **GROUP_ROLE 的 extra 结构**：`extra.basicRoleIds` 存储额外关联的基本角色 ID 数组。增加标记字段 `extra.hasExtraRoles`（布尔），仅在为 true 时展开查询 basicRoleIds。
- **反向查找**：通过 PostgreSQL JSONB GIN 索引实现 `extra.basicRoleIds @> [roleId]` 反向查询某个基本角色被哪些分组角色引用。

### 4.2 用户关联

- **用户→角色**：`user_role(target_id=abstract_role.id, target_type=角色类型)`。
- **POSITION 组织绑定**：`user_role(relation_id=orgId)`，决定该用户此职位的数据权限范围。同一职位绑定不同组织时数据权限不同。
- 唯一约束：`(tenant_id, abstract_user_id, target_type, target_id, COALESCE(relation_id, 0))`。

### 4.3 用户有效角色解析

1. 查询用户直接关联的所有角色：`user_role WHERE abstract_user_id=?`。
2. 对每条记录，根据 `target_type` 展开：
   - BASIC_ROLE/PERSONAL/POSITION：直接使用 `target_id` 作为有效角色
   - GROUP_ROLE：递归展开子角色（通过 `abstract_role.parent_id`） + 解析 `extra.basicRoleIds` 获取额外关联的基本角色
   - ORG：直接使用 `target_id`（组织树层级在 admin-service 处理）
3. 合并去重，**过滤 abstract_role.status=1**（停用角色排除），得到用户的有效角色集合。
4. 若传入 `biz_domain_id`，则只保留该域角色和全局角色。
5. **纯缓存方案**：缓存中维护用户的有效角色集合，关联变更时失效重算。

### 4.4 分组角色成员视图

- 查看某个 GROUP_ROLE 的角色列表时，展示**直接子角色 + extra.basicRoleIds 关联的基本角色**，标注来源（直接子角色/额外关联）。
- 查询接口支持参数控制是否展开子角色。

### 4.5 域归属

- 角色有 `biz_domain_id`（可空=全局）。
- **全局角色可关联任何域的资源权限**，域级角色仅关联同域资源。
- 若鉴权时传入 `biz_domain_id`，则只保留该域下的角色和全局角色。

### 4.6 删除规则

- **删除角色**：
  - GROUP_ROLE：级联软删 user_role + 递归删除子角色（含子角色的 user_role）+ 失效缓存。不删除子角色关联的权限。
  - BASIC_ROLE/PERSONAL/POSITION：级联软删 user_role + role_resource_permission（含子权限）+ 失效缓存。
  - ORG：级联软删 user_role + 递归删除子角色 + 失效缓存。不删除子角色的权限。
- **删除用户**：级联软删 user_role + 个人角色的 role_resource_permission + 失效缓存。

---

## 5. 子权限/数据权限模型

### 5.1 设计思路

- **数据权限是一种 resource_entity**：例如"XX市数据"是一个 `resource_type=DATA` 的资源实体。
- **子权限通过 depend_on 关联**：`role_resource_permission.depend_on` 指向父级 `role_resource_permission.id`，表示本条授权依赖于某个父权限绑定。
- **单层依赖**：只支持一层 depend_on（父→子），不支持多层嵌套。
- **子权限可以是任意权限类型**：通过 `domain_config` 的 SUB_PERM 配置控制哪些资源类型可以作为哪些父类型的子权限。
- **权限中心只管存储和查询**：不关心业务方如何解释数据权限，只负责返回权限数据。
- **查询父权限时自动带出子权限**。
- **数据权限支持编码转换**：与模块 12 的 code_type 统一机制。

### 5.2 SUB_PERM 配置

在 `domain_config` 中，`config_type='SUB_PERM'`，`extra` 示例：

```json
{
  "allowed": [
    { "parent_type": "MENU", "child_types": ["BUTTON", "DATA"] },
    { "parent_type": "API", "child_types": ["DATA"] }
  ]
}
```

### 5.3 示例

```
-- 全局数据权限
role_resource_permission #100: (角色R, 资源="XX市数据"(DATA), 操作=READ, depend_on=NULL)

-- 报表A的READ权限
role_resource_permission #200: (角色R, 资源="报表A"(MENU), 操作=READ, depend_on=NULL)

-- 报表A额外的YY市数据权限（子权限，依赖于 #200）
role_resource_permission #201: (角色R, 资源="YY市数据"(DATA), 操作=READ, depend_on=200)
```

查询时：报表A的数据范围 = 全局基线(XX市) ∪ 资源级扩展(YY市) = XX市+YY市。

### 5.4 级联软删

删除父级 `role_resource_permission` 时，自动级联软删所有 `depend_on` 指向它的子权限记录。

---

## 6. 鉴权流程与接口级消费

### 6.1 三种鉴权模式

1. **网关拦截**：网关查本地缓存判断接口权限。未注册接口默认拒绝（白名单模式）。
2. **服务端单查**：业务服务调用权限中心 API 单次判定。
3. **服务端批量查**：一次查询用户对多个资源的权限。

### 6.2 入参与出口

- **入参**：tenant_id, abstract_user_id, resource_entity_id, operation_permission_id；可选 biz_domain_id、code_type、context（Map）、inherit_mode（NONE/CHILDREN/PARENT/BOTH）。
- **出口**：允许/拒绝 + 拒绝原因（无角色/无授权/条件不满足/冲突失效/用户停用/角色停用/资源停用/服务停用/未注册接口）。

### 6.3 步骤

1. **校验用户状态**：`abstract_user.enabled = false` → 直接拒绝。

2. **解析用户的有效角色集合**
   从缓存中获取。缓存未命中时实时解析：
   - 直接角色：`user_role` 中 valid_from/valid_to 包含当前时间的记录，且 target_type 不为 GROUP_ROLE
   - 分组角色：`user_role` 中 target_type=GROUP_ROLE 的记录 → 递归展开子角色（parent_id） + 解析 extra.basicRoleIds → abstract_role
   - 组织角色：`user_role` 中 target_type=ORG 的记录 → 直接使用 target_id
   - 职位角色：`user_role` 中 target_type=POSITION 的记录 → 直接使用 target_id，relation_id 决定数据权限范围
   - 过滤 `abstract_role.status = 1`。
   - 若传入 `biz_domain_id`，则只保留该域角色和全局角色。

3. **角色互斥检测**（ROLE_MUTEX）
   检查用户有效角色集合中是否存在互斥对。若存在，两个互斥角色均从有效集合中排除。

4. **解析角色对 (resource_entity_id, operation_permission_id) 的授权**
   查 `role_resource_permission`，匹配有效角色集合 + 资源 + 操作。

5. **资源树继承展开**（由 inherit_mode 参数控制）
   - NONE（默认）：只匹配精确资源。
   - CHILDREN：向下展开子资源。
   - PARENT：向上检查父资源授权。
   - BOTH：双向检查。

6. **条件校验**
   若 `condition_id` 不为空，查 `permission_condition`（须 `enabled=1`），执行 `condition_rules` 判定，不通过则该条授权无效。同一 condition_id 被多次引用时复用计算结果。

7. **权限互斥检测**（PERM_MUTEX）
   检查用户对同一资源是否同时拥有互斥操作对。冲突权限失效并触发异步通知。查询时实时计算 + TTL 缓存。

8. **汇总**
   存在至少一条授权通过且未被冲突失效 → 鉴权通过。

### 6.4 缓存策略（Redis 双份 + Gateway L1）

- **Redis 第一份（用户→角色）**：`perm:user:roles:{tenantId}:{userId}` → Set<roleId>。角色分配/取消时更新。
- **Redis 第二份（角色→权限）**：`perm:role:perms:{tenantId}:{roleId}` → 角色的完整权限信息（包含接口权限 + 资源权限 + 条件标记）。条件权限也存入缓存，但标记 `hasCondition=true`，权限中心鉴权时识别到该标记后查 `permission_condition` 表并使用请求 context 评估条件。角色权限变更时重建。
- **条件评估复用**：相同 `condition_id` 在同一次请求内复用计算结果，避免重复评估。
- **Gateway L1 本地缓存**：权限判定结果缓存，TTL 30s，用于降低权限中心的 QPS。
- **失效策略**：权限变更 → 更新对应 Redis Key → Gateway L1 TTL 过期后自动获取最新结果。无需版本号轮询。
- **类型定义/域配置**：Redis 缓存，TTL 1~5 分钟。

### 6.5 gateway 接口级权限流程

1. `gateway` 从令牌中拿到 `tenant_id`、`abstract_user_id`。
2. 提取路由信息中的 `serviceCode` + `httpMethod` + `path`。
3. 查 L1 缓存 → 命中 → 直接判定放行/拒绝。
4. L1 未命中 → 回调 `permission-center` POST `/api/perm/auth/check-interface`。
5. 权限中心内部读取 Redis 两份数据（用户角色 + 角色权限）→ 匹配判定 → 返回 allowed/denied。
6. Gateway 将结果写入 L1 缓存 → 放行或返回 403。

---

## 7. 授权与配置流程

### 7.1 用户管理（abstract_user）

- **创建**：写入 abstract_user + 自动创建个人角色（`PERSONAL_{external_id}`，role_type=PERSONAL） + 写 user_role(target_type=PERSONAL, target_id=personalRoleId)。
- **同步**：支持外部系统 API 同步（幂等，按 `(source_system, entity_type, external_id, version)` 去重，version 防乱序）。
- **停用**：设 `enabled=false`，鉴权时直接拒绝。
- **删除**：级联软删 user_role + 个人角色的 role_resource_permission + 失效缓存。

### 7.2 用户关联角色（user_role）

- **校验**：用户存在且 enabled；角色存在且 status=1。角色互斥检测（ROLE_MUTEX）。POSITION 类型时校验 relation_id 合法性（由 admin-service 处理组织归属约束）。
- **写入**：INSERT user_role；写 operation_log + permission_change_log。
- **缓存失效**：失效该用户的有效角色缓存 + 递增关联角色的 permission_version。

### 7.3 分组角色管理（GROUP_ROLE）

- **添加子角色**：修改子角色 abstract_role.parent_id = groupId；校验 GROUP_ROLE 可配权限（禁止配置）。
- **添加额外基本角色**：修改 GROUP_ROLE 的 extra.basicRoleIds 数组，添加 roleId。
- **移除额外基本角色**：修改 GROUP_ROLE 的 extra.basicRoleIds 数组，移除 roleId。
- **缓存失效**：失效所有关联到该 GROUP_ROLE（含父分组链）的用户的有效角色缓存。

### 7.4 角色配置权限（role_resource_permission）

- **批量授权格式**：`{add: [], update: [], delete: []}`，三个数组，方便日志记录。
- **冲突处理**：相同角色+资源重复授权时后写覆盖操作位。
- **树存储**：只存用户勾选的节点，查询接口支持展开父级/展开子级两种模式。
- **编码转换**：查询权限时传 `code_type` 参数返回对应编码。
- **校验**：角色/资源/操作存在且未删；操作与资源类型匹配。
- **depend_on**：可选，引用同表某条记录（单层依赖）。填写时校验目标记录存在且 `depend_on IS NULL`（不能依赖子权限）。
- **condition_id**：可选，引用 `permission_condition`（须 `enabled=1`）。
- **资源依赖自动补全**：授权时查询 resource_dependency，auto_grant=true 的依赖自动补全。补全记录 `grant_source='AUTO_DEP'` + `grant_dep_id=dependency.id`。
- **依赖规则变更清理**：规则删除/修改时，按 `grant_source='AUTO_DEP' AND grant_dep_id=dependency.id` 精准清理失效记录，重新评估补全状态。
- **写入**：批量写入 role_resource_permission；写 operation_log + permission_change_log（含 diff）；递增角色的 permission_version。

### 7.5 域配置（domain_config）

- **config_type=SCOPE**：域下允许的 ROLE_TYPE/RESOURCE_TYPE/OPERATION。
- **config_type=RELATION**：域内角色类型与资源类型的可关联关系。
- **config_type=BINDING**：将全局角色/资源/操作绑定到特定业务域。
- **config_type=SUB_PERM**：子权限配置，定义哪些资源类型可以作为哪些父类型的子权限。
- **变更即时生效**：写入后立即失效相关缓存。

### 7.6 资源依赖（resource_dependency）

- **维护方式**：由外部系统通过接口（create/remove/list/batch-sync）维护。
- **操作位级别**：`source_operation_bits` 为触发条件（源资源授权含这些 bit 时才触发），`required_operation_bits` 为依赖资源需要的操作位。
- **自动补全**：`auto_grant=true` 时，授权源资源时自动为该角色补全依赖资源的权限。补全记录 `grant_source='AUTO_DEP'` + `grant_dep_id=dependency.id`，用于后续清理。
- **典型场景**：按钮→接口映射（按钮 CREATE 权限 → 自动补全对应 POST 接口的 ACCESS 权限）、菜单→数据源依赖等。
- **规则变更清理**：
  - 规则删除/`auto_grant` 改为 false → 按 `grant_source='AUTO_DEP' AND grant_dep_id=dependency.id` 清理所有已补全记录 → 递增受影响角色的 permission_version → 触发权限变更日志。
  - `source_operation_bits` / `required_operation_bits` 修改 → 重新评估：遍历所有拥有 `depends_on_resource_entity_id` 源资源的角色，不满足新规则的清理，新满足的补全 → 递增受影响角色的 permission_version。
- **写入校验**：检查防环、租户归属。

### 7.7 冲突规则（permission_conflict_rule）

- **ROLE_MUTEX**（角色互斥）：first_abstract_role_id < second_abstract_role_id 存库。分配角色时检查，违反直接拒绝。
- **PERM_MUTEX**（权限互斥）：first_operation_permission_id < second_operation_permission_id 存库。查询时检测失效 + 异步通知。
- **性能**：权限互斥查询时实时计算 + TTL 缓存，版本变更时失效。

### 7.8 权限条件（permission_condition）

- `condition_rules` JSONB 存完整条件定义：
  ```json
  {
    "logic": "AND",
    "items": [
      {
        "type": "DATE_RANGE",
        "params": { "start": "2025-01-01", "end": "2025-12-31" }
      },
      { "type": "TIME_RANGE", "params": { "start": "09:00", "end": "18:00" } },
      { "type": "IP_WHITELIST", "params": { "cidrs": ["192.168.1.0/24"] } }
    ]
  }
  ```
- 预置类型：DATE_RANGE、TIME_RANGE、IP_WHITELIST、IP_BLACKLIST。
- 条件独立实体，多个 role_resource_permission 可引用同一 condition_id，复用计算结果。
- `enabled` 开关控制启停（0=停用/1=启用）。

---

## 8. 接口注册机制

### 8.1 服务注册（service_config）

- 每个接入服务在 `service_config` 中注册基础信息：`service_code`、`name`、`base_path` 等。
- `status` 控制启停（0=停用/1=启用），停用后该服务的接口不参与授权。
- 管理端可手动配置，Java 服务也可启动时自动注册。
- 接口级落库：service_config 存服务信息，resource_api_mapping 每行存一个接口。

### 8.2 接口上报

- **全量同步**：每次上报覆盖该 service_code 的所有接口。permission-center 做 diff。
- **手动管理**：支持管理界面手动增删改接口映射。
- **自动创建**：上报时自动创建对应的 `resource_entity`（API 类型）+ `resource_api_mapping`。分组信息自动建立资源树。
- **路径约定**：`resource_api_mapping.path_pattern` 存储的是 **Gateway 接收到的原始请求路径**（如 `/admin/api/users/list`），不是后端服务的实际路径（如 `/api/users/list`，即 StripPrefix 剥离后的路径）。
  - 服务上报接口时，自动拼接 `service_config.base_path` + 实际监听路径 = 完整路径（Gateway 暴露的路径）。
  - Gateway 鉴权时直接用客户端原始请求路径匹配 `path_pattern`。

### 8.3 Java SDK 要点

- 通过 SpringBoot 注解（如 `@RequestMapping`）收集接口信息。
- 启动时调用 permission-center 批量注册接口。
- 支持通过 `@Tag` 或自定义注解标记分组信息。

### 8.4 OpenFeign 接口契约（供 admin-service / example-service 调用）

外部服务通过 OpenFeign 调用权限中心，接口定义如下：

| 方法签名 | 路径 | 说明 |
|----------|------|------|
| `PermResult<Void> syncUser(UserSyncDTO)` | `POST /api/perm/users/create` | 同步用户创建 |
| `PermResult<Void> syncUser(UserSyncDTO)` | `POST /api/perm/users/update` | 同步用户更新 |
| `PermResult<Void> removeUser(IdListDTO)` | `POST /api/perm/users/remove` | 同步用户删除 |
| `PermResult<Void> syncMenu(MenuSyncDTO)` | `POST /api/perm/menus/sync` | 同步菜单/按钮 |
| `PermResult<Void> syncRole(RoleSyncDTO)` | `POST /api/perm/roles/sync` | 同步角色 |
| `AuthCheckResp checkAuth(AuthCheckReq)` | `POST /api/auth/check` | 单次鉴权（Gateway 回调） |

#### 公共 DTO

```java
// 用户同步 DTO
public record UserSyncDTO(
    String sourceSystem,    // 来源系统标识（如 "admin-service"）
    String entityType,      // "user"
    String externalId,      // sys_user.id
    int version,            // 版本号，防乱序
    String operation,       // "create" / "update" / "delete"
    String name,
    String phone,
    String email,
    Boolean enabled
) {}

// ID 列表 DTO
public record IdListDTO(List<Long> ids) {}

// 菜单同步 DTO
public record MenuSyncDTO(
    String sourceSystem,
    String entityType,      // "menu"
    String externalId,      // sys_menu.id
    int version,
    String name,
    String menuType,        // "MENU" / "BUTTON"
    String parentExternalId, // 父菜单 ID
    String path,            // 接口路径
    String method           // HTTP 方法
) {}

// 角色同步 DTO
public record RoleSyncDTO(
    String sourceSystem,
    String entityType,      // "role"
    String externalId,
    int version,
    String code,
    String name,
    String roleType,        // "ORG" / "POSITION" / "PERSONAL" / "GROUP_ROLE" / "BASIC_ROLE"
    String parentExternalId
) {}
```

---

## 9. 变更记录（双日志体系）

### 9.1 操作日志（operation_log）

- **全量记录**所有写操作，简单记录。
- 字段：module（所属模块）、action（操作类型）、target_type/target_id、summary、operator 等。
- 永久保留。
- 查询维度：时间、操作人、模块、操作类型。

### 9.2 权限变更记录（permission_change_log）

- **仅记录权限相关变更**，详细记录 before/after/diff（JSONB），方便排查用户因配置问题导致权限失效。
- 字段：entity_type、entity_id、operation、old_snapshot、new_snapshot、diff_snapshot、affected_user_ids、affected_role_ids、change_source、request_id。
- 永久保留。
- 查询维度：时间、操作人、目标类型、动作类型、受影响用户、受影响角色、request_id。
- 同一次操作产生的多条记录通过 `request_id`（trace_id）关联。

---

## 10. 用户权限视图

### 10.1 视图内容

- **完整视图**：用户所有有效权限（角色 + 资源 + 操作位 + 来源追溯）。
- **来源追溯**：显示权限继承链路（来自哪个角色 / 来自哪个分组）。
- **实时计算**：每次查询时汇总，不缓存视图结果。

### 10.2 查询维度

- **按用户查**：传 abstract_user_id，返回该用户所有有效权限。
- **按资源查**：传 resource_entity_id，返回哪些角色/用户拥有该资源权限。
- **按角色查**：传 abstract_role_id，返回该角色的所有权限配置。

### 10.3 结合变更日志

- 用户权限视图可关联 permission_change_log，展示最近权限变更历史。

---

## 11. 管理端页面建议

- **用户管理**：按租户/域筛用户；多选用户 → 分配分组/角色；「我的权限」「权限变更」按用户查；enabled 开关控制用户启停。
- **分组管理**：按域筛分组，树形展示；管理分组下的角色；查看分组成员。
- **角色与权限**：角色列表，按域筛选；status 控制启停；选角色 → 资源树 + 操作多选 → 配置 can_manage、condition_id、子权限；批量授权 {add, update, delete}。
- **域配置**：四 Tab——可用范围(SCOPE)、可关联关系(RELATION)、域引用(BINDING)、子权限配置(SUB_PERM)。
- **权限条件**：条件列表；condition_rules JSONB 可视化编辑；enabled 开关。
- **冲突规则**：角色互斥 + 权限互斥两种类型；冲突检测按钮。
- **接口管理**：服务列表（status 启停）+ 接口资源树 + 接口映射。
- **资源依赖**：展示资源依赖关系，支持外部系统维护。
- **系统配置**：租户级配置管理（角色名唯一性等）。
- **变更记录**：操作日志（轻量全量）+ 权限变更记录（详细 diff）；用户权限视图关联变更历史。

---

## 12. 接口清单（全部 POST + JSON Body）

| 模块     | 接口                                                | 说明                                                  |
| -------- | --------------------------------------------------- | ----------------------------------------------------- |
| 鉴权     | POST /api/perm/auth/check                           | 单次鉴权；返回允许/拒绝+原因                          |
| 鉴权     | POST /api/perm/auth/batch-check                     | 批量鉴权                                              |
| 鉴权     | POST /api/perm/auth/check-interface                 | 接口级判定（Gateway 回调入口）                        |
| 类型定义 | POST /api/perm/type-definition/list                 | 类型列表                                              |
| 类型定义 | POST /api/perm/type-definition/create               | 创建类型（resource_type 自动预置 CRUD 操作）          |
| 类型定义 | POST /api/perm/type-definition/update               | 更新类型                                              |
| 类型定义 | POST /api/perm/type-definition/remove               | 删除类型（检查引用拒绝）                              |
| 业务域   | POST /api/perm/biz-domain/list                      | 域列表                                                |
| 业务域   | POST /api/perm/biz-domain/create                    | 创建域                                                |
| 业务域   | POST /api/perm/biz-domain/update                    | 更新域                                                |
| 业务域   | POST /api/perm/biz-domain/remove                    | 删除域（检查引用拒绝）                                |
| 用户     | POST /api/perm/abstract-user/list                   | 用户列表                                              |
| 用户     | POST /api/perm/abstract-user/detail                 | 用户详情                                              |
| 用户     | POST /api/perm/abstract-user/create                 | 创建用户（自动创建个人角色）                          |
| 用户     | POST /api/perm/abstract-user/update                 | 更新用户（含 enabled 启停）                           |
| 用户     | POST /api/perm/abstract-user/remove                 | 删除用户（级联删）                                    |
| 用户     | POST /api/perm/abstract-user/sync                   | 外部系统同步（幂等，version 防乱序）                    |
| 角色     | POST /api/perm/abstract-role/list                   | 角色列表                                              |
| 角色     | POST /api/perm/abstract-role/detail                 | 角色详情                                              |
| 角色     | POST /api/perm/abstract-role/create                 | 创建角色                                              |
| 角色     | POST /api/perm/abstract-role/update                 | 更新角色（含 status 启停）                            |
| 角色     | POST /api/perm/abstract-role/remove                 | 删除角色（级联）                                      |
| 角色     | POST /api/perm/abstract-role/move                   | 移动角色（修改 parent_id）                            |
| 分组角色 | POST /api/perm/abstract-role/extra-roles/add        | 分组角色添加额外基本角色                              |
| 分组角色 | POST /api/perm/abstract-role/extra-roles/remove     | 分组角色移除额外基本角色                              |
| 分组角色 | POST /api/perm/abstract-role/extra-roles/list       | 查询分组角色额外关联的基本角色                        |
| 操作     | POST /api/perm/operation-permission/list            | 操作列表                                              |
| 操作     | POST /api/perm/operation-permission/create          | 创建操作                                              |
| 操作     | POST /api/perm/operation-permission/update          | 更新操作                                              |
| 操作     | POST /api/perm/operation-permission/remove          | 删除操作（检查引用拒绝）                              |
| 资源     | POST /api/perm/resource-entity/tree                 | 资源树                                                |
| 资源     | POST /api/perm/resource-entity/list                 | 资源列表                                              |
| 资源     | POST /api/perm/resource-entity/create               | 创建资源                                              |
| 资源     | POST /api/perm/resource-entity/update               | 更新资源（含 status 启停）                            |
| 资源     | POST /api/perm/resource-entity/remove               | 删除资源（检查引用拒绝）                              |
| 服务     | POST /api/perm/service-config/list                  | 服务列表                                              |
| 服务     | POST /api/perm/service-config/create                | 注册服务                                              |
| 服务     | POST /api/perm/service-config/update                | 更新服务（含 status 启停）                            |
| 服务     | POST /api/perm/service-config/remove                | 删除服务                                              |
| 服务     | POST /api/perm/service-config/sync                  | 全量同步接口资源                                      |
| 接口映射 | POST /api/perm/resource-api-mapping/list            | 接口映射列表                                          |
| 接口映射 | POST /api/perm/resource-api-mapping/create          | 创建映射                                              |
| 接口映射 | POST /api/perm/resource-api-mapping/update          | 更新映射                                              |
| 接口映射 | POST /api/perm/resource-api-mapping/remove          | 删除映射                                              |
| 条件     | POST /api/perm/permission-condition/list            | 条件列表                                              |
| 条件     | POST /api/perm/permission-condition/create          | 创建条件                                              |
| 条件     | POST /api/perm/permission-condition/update          | 更新条件（含 enabled 启停）                           |
| 条件     | POST /api/perm/permission-condition/remove          | 删除条件                                              |
| 用户分配 | POST /api/perm/user-role/list                       | 用户关联列表                                          |
| 用户分配 | POST /api/perm/user-role/assign                     | 分配角色/分组（含角色互斥检测）                       |
| 用户分配 | POST /api/perm/user-role/unassign                   | 回收角色/分组                                         |
| 角色权限 | POST /api/perm/role-resource-permission/list        | 角色权限列表（支持展开父级/子级，code_type 编码转换） |
| 角色权限 | POST /api/perm/role-resource-permission/batch-save  | 批量授权 {add, update, delete}（含依赖自动补全）      |
| 角色权限 | POST /api/perm/role-resource-permission/children    | 查询子权限列表                                        |
| 域配置   | POST /api/perm/domain-config/list                   | 域配置列表                                            |
| 域配置   | POST /api/perm/domain-config/save                   | 保存域配置                                            |
| 域配置   | POST /api/perm/domain-config/remove                 | 删除域配置                                            |
| 依赖     | POST /api/perm/resource-dependency/list             | 资源依赖列表                                          |
| 依赖     | POST /api/perm/resource-dependency/create           | 添加依赖                                              |
| 依赖     | POST /api/perm/resource-dependency/remove           | 删除依赖                                              |
| 依赖     | POST /api/perm/resource-dependency/batch-sync       | 批量同步依赖（外部系统）                              |
| 冲突     | POST /api/perm/conflict-rule/list                   | 冲突规则列表                                          |
| 冲突     | POST /api/perm/conflict-rule/create                 | 创建冲突规则                                          |
| 冲突     | POST /api/perm/conflict-rule/remove                 | 删除冲突规则                                          |
| 冲突     | POST /api/perm/conflict-rule/detect                 | 冲突检测                                              |
| 版本     | POST /api/perm/permission-version/query             | 查询权限版本                                          |
| 视图     | POST /api/perm/permission-view/user-permissions     | 用户权限完整视图（含来源追溯）                        |
| 视图     | POST /api/perm/permission-view/resource-permissions | 资源权限视图                                          |
| 视图     | POST /api/perm/permission-view/role-permissions     | 角色权限视图                                          |
| 视图     | POST /api/perm/permission-view/recent-changes       | 用户最近权限变更                                      |
| 系统配置 | POST /api/perm/system-config/list                   | 系统配置列表                                          |
| 系统配置 | POST /api/perm/system-config/save                   | 保存系统配置                                          |
| 日志     | POST /api/perm/operation-log/list                   | 操作日志查询                                          |
| 日志     | POST /api/perm/permission-change-log/list           | 权限变更记录查询                                      |

---

## 13. 操作权限位运算约定

### 13.1 binary_bit + inherit_mask

- **operation_permission**：`binary_bit` 为单一比特（如 1, 2, 4, 8），BIGINT 支持最多 63 个独立操作。
- `inherit_mask` 为继承的位掩码：`effective = binary_bit | inherit_mask`。
- 预置 CRUD 约定：
  - CREATE：`binary_bit=1`，`inherit_mask=0`，effective=1
  - READ：`binary_bit=2`，`inherit_mask=0`，effective=2
  - UPDATE：`binary_bit=4`，`inherit_mask=2`，effective=6（含 READ）
  - DELETE：`binary_bit=8`，`inherit_mask=2`，effective=10（含 READ）

### 13.2 鉴权时位运算

- 对用户在某资源上的所有授权操作做 OR 合并后，与请求操作的 effective 做 AND 判断。
- 核心鉴权链路也可直接按 `operation_permission.id` 匹配，位运算作为批量汇总的优化手段。

---

## 14. 系统配置（system_config）

### 14.1 预置配置项

| config_key              | 说明           | 默认值                   |
| ----------------------- | -------------- | ------------------------ |
| ROLE_NAME_UNIQUE_MODE   | 角色名唯一性   | `{"mode":"NO_RESTRICT"}` |
| UNREGISTERED_API_POLICY | 未注册接口策略 | `{"mode":"DENY"}`        |

- **ROLE_NAME_UNIQUE_MODE**：`NO_RESTRICT`=不限制，`DOMAIN_UNIQUE`=(tenant_id, biz_domain_id, name) 唯一。
- **UNREGISTERED_API_POLICY**：`DENY`=默认拒绝（白名单），`ALLOW`=默认放行（黑名单）。

---

## 15. 文件与约定速查

- **建表 SQL**：`permission_center_schema.sql`（按文件内顺序执行即可，18 张表）。
- **类型定义**：`type_definition` 表，按 `(type_key, type_value)` 查询枚举。
- **软删除**：所有查询默认带 `WHERE delete_flag = 0`；删除时更新 delete_flag = 本行 id。
- **唯一约束**：均带 `WHERE delete_flag = 0`。
- **操作继承**：`binary_bit | inherit_mask`（BIGINT）。创建 resource_type 自动预置 CRUD。
- **资源继承**：查询接口参数 `inherit_mode` 控制。
- **资源多编码**：`resource_entity.code_type` 支持同一资源多编码体系。
- **角色模型**：树形一体化(abstract_role)，通过 parent_id 支持层级，role_type 区分 ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE。GROUP_ROLE 不可配权限。
- **用户启停**：abstract_user.enabled，停用后鉴权拒绝。
- **角色启停**：abstract_role.status（0/1），停用不参与鉴权。
- **资源启停**：resource_entity.status（0/1）。
- **服务启停**：service_config.status（0/1），停用后接口不参与授权。
- **子权限**：role_resource_permission.depend_on 自引用，单层，删除父权限级联软删子权限。
- **条件**：condition_rules JSONB 完整规则，支持条件组。
- **冲突**：ROLE_MUTEX 写入拒绝 + PERM_MUTEX 查询失效。
- **资源依赖**：操作位级别触发，auto_grant 自动补全，补全记录标记 grant_source/grant_dep_id，规则变更时精准清理。
- **双日志**：operation_log(轻量全量) + permission_change_log(权限变更 diff)。
- **版本**：角色级粒度，仅用于缓存失效。
- **缓存**：L1 本地 + L2 Redis 混合。
- **接口标准**：全部 POST + JSON Body。
