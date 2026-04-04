# 通用权限中心 - 详细设计文档

本文档面向 AI 或开发人员实现权限中心后端与管理端，与 `permission_center_schema.sql` 及架构文档配套使用。

---

## 1. 概述与架构

### 1.1 设计原则

- **无数据库外键**：所有关联为逻辑 ID，由应用保证一致性。
- **租户隔离**：所有表带 `tenant_id`，查询必须带租户条件。
- **软删除**：统一使用 `delete_flag`（`0` = 未删除，删除时填本行 id），唯一约束均带 `WHERE delete_flag = 0`。`deleted_at` 仅为审计展示字段，不参与索引条件。
- **审计字段**：每表含 `created_by`、`updated_by`、`deleted_by`、`created_at`、`updated_at`、`deleted_at`。
- **原表为事实层**：`abstract_user`、`abstract_role`、`user_role`、`resource_entity`、`operation_permission`、`role_resource_permission` 等原表是权限事实来源。
- **kernel 为消费层**：`gateway`、`identity-service` 与其他运行时组件只消费 `permission-center` 对外暴露的查询/判定/版本接口。
- **接口映射显式建模**：接口资源与 HTTP 路由关系通过 `resource_api_mapping` 维护。
- **运行时版本独立维护**：权限变更后的快照刷新依据 `permission_version`。
- **操作绑定资源类型**：`operation_permission.resource_type` 直接表达操作适用的资源类型，取代按业务域分组。
- **位运算操作继承**：`binary_bit + inherit_mask`（BIGINT，63 位）表达操作间继承关系，`effective = binary_bit | inherit_mask`。
- **条件预设+自定义审核**：`permission_condition` 支持系统预设条件（始终生效）和自定义条件（需审核通过后生效）。
- **冲突查询时失效**：`permission_conflict_rule` 在查询/快照组装时检测，冲突权限失效并异步通知修正，不在写入时阻止。
- **资源依赖声明式维护**：`resource_dependency` 由资源注册方（业务系统）自动维护，权限中台负责存储与查询。
- **资源继承查询方控制**：资源树的继承展开（子资源/父资源）由查询接口参数控制，不在表结构中定义。

### 1.2 核心概念

| 概念 | 说明 |
|------|------|
| 类型定义 (type_definition) | 类型枚举 KV：user_type/role_type/resource_type，type_value 为枚举整型。 |
| 业务域 (biz_domain) | 对权限对象分类，控制数据量与管理边界。 |
| 抽象用户 (abstract_user) | 对应具体业务的人/服务/第三方，通过 user_type 区分，无 biz_domain，通过角色关联到域。 |
| 抽象角色 (abstract_role) | 对应角色/组织/团队/职位等，属于某 biz_domain 或全局；与权限直接关联。 |
| 操作权限 (operation_permission) | 如 VIEW、EDIT、ACCESS；绑定 resource_type；用 binary_bit + inherit_mask 表达继承。 |
| 权限资源实体 (resource_entity) | 权限作用对象（菜单、报表、数据集等），支持树形。 |
| 接口资源映射 (resource_api_mapping) | 接口类资源到 `service_code + http_method + path_pattern` 的显式映射。 |
| 权限条件 (permission_condition) | 权限生效条件：预设（handler 编码）或自定义（需审核）。 |
| 用户-角色 (user_role) | 用户与角色多对多，可带 valid_from/valid_to。 |
| 角色-资源-操作 (role_resource_permission) | 角色对某资源在某操作上的授权，可带 can_manage 和 condition_id。 |
| 域范围配置 (domain_scope_config) | 域下允许的角色类型/资源类型/操作。 |
| 域关系配置 (domain_relation_config) | 域内角色类型与资源类型的可关联关系（ROLE_RESOURCE）。 |
| 域引用绑定 (domain_scope_binding) | 将全局角色/资源/操作绑定到特定业务域。 |
| 资源依赖 (resource_dependency) | 声明式资源间依赖关系，由资源注册方自动维护。 |
| 权限冲突规则 (permission_conflict_rule) | 同资源互斥操作对，查询时检测失效。 |
| 权限版本 (permission_version) | 运行时版本号，供 identity-service 写入令牌、gateway 刷新快照。 |

### 1.3 逻辑关系简图

```
type_definition  (type_key: user_type / role_type / resource_type)

biz_domain
    ├── abstract_role (biz_domain_id 可空=全局)
    ├── resource_entity (biz_domain_id 可空=全局)
    ├── domain_scope_config / domain_relation_config / domain_scope_binding
    └── permission_conflict_rule (biz_domain_id 可空=全局)

operation_permission (resource_type 可空=全局, binary_bit + inherit_mask)

abstract_user --[user_role]--> abstract_role
abstract_role --[role_resource_permission]--> resource_entity + operation_permission
role_resource_permission 可带 condition_id --> permission_condition
resource_entity --[resource_api_mapping]--> service_code + http_method + path_pattern
resource_entity --[resource_dependency]--> resource_entity (依赖链，写入时防环)
permission_version --> identity-service / gateway
```

---

## 2. 表清单与用途

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| type_definition | 类型枚举 KV 定义 | tenant_id, biz_domain_id, type_key, type_value, name |
| biz_domain | 业务域 | tenant_id, code, name |
| abstract_user | 抽象用户 | tenant_id, user_type, external_id, name |
| abstract_role | 抽象角色（树） | tenant_id, biz_domain_id, role_type, parent_id, path, name |
| operation_permission | 操作权限（绑定资源类型） | tenant_id, resource_type, code, binary_bit, inherit_mask |
| resource_entity | 资源实体（树） | tenant_id, biz_domain_id, parent_id, code, name, resource_type, path |
| resource_api_mapping | 接口资源映射 | tenant_id, resource_entity_id, service_code, http_method, path_pattern |
| permission_condition | 权限生效条件 | tenant_id, code, condition_source, expression, status |
| user_role | 用户-角色关联 | tenant_id, abstract_user_id, abstract_role_id, valid_from, valid_to |
| role_resource_permission | 角色-资源-操作 | tenant_id, abstract_role_id, resource_entity_id, operation_permission_id, condition_id |
| domain_scope_config | 域下允许的类型/操作 | tenant_id, biz_domain_id, scope_type, scope_ref_id |
| domain_relation_config | 域内角色-资源类型关联 | tenant_id, biz_domain_id, relation_type, left_ref_id, right_ref_id |
| domain_scope_binding | 域引用全局实体 | tenant_id, biz_domain_id, bound_type, bound_entity_id |
| resource_dependency | 资源依赖（声明式） | resource_entity_id, depends_on_resource_entity_id, required_operation_permission_id |
| permission_conflict_rule | 同资源互斥操作对 | first_operation_permission_id, second_operation_permission_id, resource_type_value |
| permission_version | 权限版本游标 | tenant_id, version_no, trigger_entity_type, trigger_entity_id |
| permission_change_log | 变更记录 | entity_type, entity_id, operation, old_snapshot, new_snapshot, affected_*_ids |

---

## 3. 类型与枚举

- **type_definition**：`type_key` 如 `user_type`、`role_type`、`resource_type`；`type_value` 为 INT，业务表存 type_value。
- 显示名称与描述从 type_definition 按 (type_key, type_value) 查询；biz_domain_id 可空表示租户全局类型。
- `user_type` 首批至少覆盖 `USER`、`SERVICE`。
- `resource_type` 首批至少覆盖 `MENU`、`BUTTON`、`API`、`DATA`。
- 接口类资源默认操作编码统一使用 `ACCESS`。
- **operation_permission.resource_type**：引用 type_definition 中 type_key='resource_type' 的 type_value，NULL 表示适用所有资源类型。
- **domain_scope_config.scope_type**：`ROLE_TYPE` | `RESOURCE_TYPE` | `OPERATION`。
- **domain_relation_config.relation_type**：当前仅 `ROLE_RESOURCE`。
- **domain_scope_binding.bound_type**：`ROLE` | `RESOURCE` | `OPERATION`。
- **permission_condition.condition_source**：`PRESET` | `CUSTOM`。
- **permission_condition.status**：`APPROVED` | `PENDING` | `REJECTED`。
- **permission_change_log.entity_type**：`user_role` | `batch_user_role` | `role_resource_permission` | `batch_role_resource_permission` | `abstract_user` | `abstract_role` | `resource_entity` 等。
- **permission_change_log.change_source**：`ADMIN` | `MQ_SYNC` | `API` | `SYSTEM`。

---

## 4. 鉴权流程与接口级消费（Java 实现要点）

### 4.1 入参与出口

- **入参**：tenant_id, abstract_user_id, resource_entity_id, operation_permission_id；可选 biz_domain_id；可选 context（Map，供 condition 表达式使用）；可选 inherit_mode（NONE / CHILDREN / PARENT / BOTH）。
- **出口**：boolean 或结果对象（是否通过 + 原因：无角色 / 无授权 / 条件不满足 / 冲突失效）。

### 4.2 步骤

1. **解析用户在该（些）域下的角色**
   查 user_role（abstract_user_id = ?，valid_from/valid_to 包含当前时间）+ abstract_role；若传入 biz_domain_id，则只保留 role.biz_domain_id = ? 或全局角色（biz_domain_id IS NULL）。

2. **解析角色对 (resource_entity_id, operation_permission_id) 的授权**
   查 role_resource_permission，abstract_role_id IN (上一步角色)，resource_entity_id = ?，operation_permission_id = ?。

3. **资源树继承展开**（由 inherit_mode 参数控制）
   - NONE（默认）：只匹配精确资源。
   - CHILDREN：向下展开子资源，检查当前资源授权是否覆盖子资源。
   - PARENT：向上检查父资源授权是否可覆盖当前资源。
   - BOTH：双向检查。

4. **条件校验**
   若 role_resource_permission.condition_id 不为空，查 permission_condition（须 status=APPROVED），执行条件判定（PRESET 走 handler，CUSTOM 走表达式引擎），不通过则该条授权无效。

5. **冲突检测**
   查 permission_conflict_rule，检查该用户对同一资源是否同时拥有互斥操作对。若冲突，相关权限失效并触发异步通知。

6. **汇总**
   若存在至少一条授权通过（直接或继承）且未被冲突失效，则鉴权通过。

### 4.3 性能建议

- **缓存**：key = (tenant_id, abstract_user_id, biz_domain_id)，value = Set of (resource_entity_id, operation_permission_id)。user_role 或 role_resource_permission 变更时按 user/role 失效；TTL 1~5 分钟。
- **resource_dependency**：表数据量通常不大，可启动时或按需加载到内存/本地缓存。
- **冲突检测**：冲突规则数量有限，可缓存在内存中。
- **列表接口**：仅返回"用户拥有的 (resource, op)"时默认不做继承展开和冲突检测；精确检查时按需启用。
- **接口快照缓存**：`gateway` 本地缓存按 `(tenant_id, abstract_user_id, permission_version)` 组织。

### 4.4 gateway 接口级权限包装流程

1. `gateway` 从令牌中拿到 `tenant_id`、`abstract_user_id`、`permissionVersion`。
2. 若本地无快照或版本变更，从 `permission-center` 拉取接口权限快照。
3. `permission-center` 从 `user_role`、`role_resource_permission`、`resource_entity`、`operation_permission` 组装可访问接口资源集合。
4. 组装时执行冲突检测，冲突权限从快照中排除并触发异步通知。
5. 对接口类资源，结合 `resource_api_mapping` 输出 `service_code + http_method + path_pattern + operation_code` 快照。
6. 首期接口快照仅下发无条件授权，`condition_id != null` 的授权不进入快照。
7. `gateway` 完成路由匹配并决定放行/拒绝。

当前最小实现约定：

- `principalContext.subjectId` 当前按 `abstract_user_id` 解释。
- `permission-center` 通过真实查询链组装接口快照：`user_role -> abstract_role -> role_resource_permission -> resource_entity -> operation_permission -> resource_api_mapping`。
- `InterfacePermissionRule.capabilityCode` 当前按 `resource_code + ":" + operation_code` 生成。
- `condition_id != null` 的授权项首期不下发到接口快照，仅参与精确鉴权（阶段性边界）。

---

## 5. 授权与配置流程

### 5.1 用户分配角色（user_role）

- **校验**：abstract_user_id、abstract_role_id 存在且未删；若启用域校验，则角色须在域范围内（域内角色或全局角色或该域 binding 的全局角色）。
- **写入**：INSERT user_role；写 permission_change_log。

### 5.2 角色配置权限（role_resource_permission）

- **校验**：角色、资源、操作存在且未删；若启用域配置校验，则通过 domain_scope_config / domain_relation_config 校验该域下该角色类型可关联该资源类型。操作与资源类型的匹配通过 operation_permission.resource_type 校验。
- **condition_id**：可选，引用 permission_condition（须 status=APPROVED）。
- **写入**：INSERT role_resource_permission；写 permission_change_log。

### 5.3 域配置

- **domain_scope_config**：维护域下允许的 ROLE_TYPE / RESOURCE_TYPE / OPERATION。
- **domain_relation_config**：维护 ROLE_RESOURCE（角色类型-资源类型可关联关系）。资源类型与操作的绑定通过 operation_permission.resource_type 表达。
- **domain_scope_binding**：维护域绑定的全局角色/资源/操作。

### 5.4 资源依赖（resource_dependency）

- **写入**：由业务系统在注册核心资源时自动维护。例如注册"报表"资源时，同时声明它依赖"数据集"资源。
- **写入校验**：INSERT 时检查新依赖是否形成环（防环校验在写入时完成，不在读取时限深度）。
- **查询**：权限中台提供依赖查询接口，但不将依赖展开内建到标准鉴权流程中。调用方按需调用"检查依赖链完整性"接口。

### 5.5 权限冲突规则（permission_conflict_rule）

- **写入**：first_operation_permission_id < second_operation_permission_id 存库；resource_type_value 可选。
- **运行策略**：查询时检测——组装权限快照或执行鉴权时，检查用户对同一资源是否同时拥有互斥操作对。若冲突则**双方权限失效**（从快照中排除），不修改表数据，异步通知管理员修正授权配置。
- **检测接口**：可单独提供"冲突检测"接口，按用户或资源扫描违规并返回列表。

### 5.6 权限条件（permission_condition）

- **预设条件**（condition_source=PRESET）：系统内置，expression 存 handler 编码（如 `WORKDAY_ONLY`、`INTERNAL_IP`），应用层有对应 handler 实现。status 始终为 APPROVED。
- **自定义条件**（condition_source=CUSTOM）：用户创建 → status=PENDING → 管理员审核 → APPROVED 或 REJECTED。只有 APPROVED 的条件可被 role_resource_permission 引用。
- **鉴权时**：查 permission_condition，PRESET 走 handler 执行，CUSTOM 走表达式引擎（传入 context Map）。

---

## 6. 变更记录（permission_change_log）

### 6.1 写入约定

- **单条 user_role**：entity_type=user_role，entity_id=user_role.id，affected_abstract_user_ids=[该用户]，affected_abstract_role_ids=[该角色]。
- **批量用户-角色**：entity_type=batch_user_role，affected_abstract_user_ids=本批全部用户，affected_abstract_role_ids=本批全部角色，new_snapshot 含 assignments 列表。
- **批量角色-资源-操作**：entity_type=batch_role_resource_permission，new_snapshot/old_snapshot 含 resource_entity_ids、operation_permission_ids。

### 6.2 查询

- 按用户：`WHERE tenant_id = ? AND ? = ANY(affected_abstract_user_ids) ORDER BY created_at DESC`。
- 按角色：`WHERE tenant_id = ? AND ? = ANY(affected_abstract_role_ids) ORDER BY created_at DESC`。
- 按时间、biz_domain_id、entity_type、request_id 组合过滤。

### 6.3 大批量

- 单次影响数极大（如 >5000）时，可仅写 request_id 与 new_snapshot 中的 id 列表。或约定单批上限（如 2000）并拆批写多条 log。

---

## 7. 管理端页面建议

- **用户管理**：按租户/域筛用户；多选用户 → 「分配角色」选域、多选角色、valid_from/valid_to → 提交；「我的权限」「权限变更」按用户查。
- **角色与权限**：按域筛角色，树形展示；选角色 → 资源树 + 操作多选（受 domain_relation_config 限制，操作受 operation_permission.resource_type 匹配）→ 每 (资源, 操作) 填 can_manage、condition_id。
- **域配置**：三 Tab——可用范围(domain_scope_config)、可关联关系(domain_relation_config)、域引用(domain_scope_binding)。
- **权限条件**：条件列表（区分 PRESET/CUSTOM）；自定义条件的创建与审核流程。
- **权限冲突**：维护 permission_conflict_rule；「检测冲突」按钮调用冲突检测接口，展示违规用户/资源列表。
- **资源依赖**：展示资源依赖图（由业务系统自动维护，管理端只读或辅助编辑）。
- **变更记录**：按用户/角色/时间/request_id 查，可展开 old_snapshot/new_snapshot。

---

## 8. 接口建议（供 AI 实现参考）

| 能力 | 建议接口 | 说明 |
|------|----------|------|
| 鉴权 | POST /api/perm/check | 入参含 inherit_mode（NONE/CHILDREN/PARENT/BOTH）；返回是否通过及原因。 |
| 用户角色 | GET/POST/DELETE /api/perm/users/{userId}/roles | 列表/批量分配/回收。 |
| 角色权限 | GET/POST/DELETE /api/perm/roles/{roleId}/permissions | 列表/批量添加/回收 (resource_id, operation_id)；可带 can_manage、condition_id。 |
| 域配置 | GET/PUT /api/perm/domains/{domainId}/scope, /relation, /binding | 域范围、域关系、域引用。 |
| 权限条件 | GET/POST/PUT /api/perm/conditions | 条件 CRUD；PUT 含审核操作。 |
| 资源依赖 | GET/POST/DELETE /api/perm/resource-dependencies | 列表/新增/删除；写入时校验防环。 |
| 冲突规则 | GET/POST/DELETE /api/perm/conflict-rules | 列表/新增/删除。 |
| 冲突检测 | POST /api/perm/conflict-detection | 返回违规用户/资源/操作列表。 |
| 变更记录 | GET /api/perm/change-logs | 支持按 user_id、role_id、biz_domain_id、时间、entity_type、request_id 过滤。 |
| 接口快照 | POST /api/perm/policy/interface-snapshot | 返回 gateway 可直接消费的接口资源快照。 |
| 接口判定 | POST /api/perm/decision/interface | 按 `service_code + http_method + path` 做单次接口判定。 |
| 版本查询 | POST /api/perm/version/query | 查询当前租户权限版本。 |

---

## 9. 操作权限位运算约定

### 9.1 binary_bit + inherit_mask

- **operation_permission**：`binary_bit` 为单一比特（如 1, 2, 4, 8），BIGINT 支持最多 63 个独立操作。
- `inherit_mask` 为继承的位掩码：`effective = binary_bit | inherit_mask`。
- 例如 VIEW=1、EDIT(binary_bit=4, inherit_mask=1) 则 EDIT 的 effective=5（含 VIEW）。
- 首批操作位值约定：
  - VIEW：`binary_bit=1`，`inherit_mask=0`
  - EDIT：`binary_bit=4`，`inherit_mask=1`
  - ACCESS：`binary_bit=8`，`inherit_mask=0`
  - DATA_READ：`binary_bit=16`，`inherit_mask=0`
  - DATA_EXPORT：`binary_bit=32`，`inherit_mask=0`
- 操作绑定 `resource_type`：operation_permission.resource_type 直接表达此操作适用的资源类型，NULL 表示适用所有。

### 9.2 鉴权时位运算

- 业务侧若按位判断，可对用户在某资源上的所有授权操作做 OR 合并后，再与请求操作的 effective 做 AND 判断。
- 核心鉴权链路也可直接按 `operation_permission.id` 匹配（role_resource_permission 按 id 关联），位运算作为批量汇总的优化手段。

---

## 10. 文件与约定速查

- **建表 SQL**：`permission_center_schema.sql`（按文件内顺序执行即可，17 张表）。
- **类型定义**：`type_definition` 表（原 system_config），按 `(type_key, type_value)` 查询枚举。
- **软删除**：所有查询默认带 `WHERE delete_flag = 0`；删除时更新 delete_flag = 本行 id、deleted_at = now()、deleted_by = 操作人。
- **唯一约束**：均带 `WHERE delete_flag = 0`，注意 biz_domain_id/resource_type 可空表的分开约束。
- **操作继承**：`binary_bit | inherit_mask`（BIGINT），通过 `operation_permission.resource_type` 绑定资源类型。
- **资源继承**：查询接口参数 `inherit_mode`（NONE/CHILDREN/PARENT/BOTH）控制，不在表结构中定义。
- **条件审核**：自定义条件须 status=APPROVED 才可被引用。
- **冲突检测**：查询时失效 + 异步通知，不修改授权表数据。
- **资源依赖**：声明式元数据，写入时防环，按需查询不内建到标准鉴权流程。
