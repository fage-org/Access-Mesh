---
doc_type: design
title: 默认组织树与用户生命周期设计
status: adopted
domain: org-user
last_reviewed: 2026-08-15
---

# 默认组织树与用户生命周期设计

> 状态：**v1.0 定稿**（2026-06-10）。本文固化“默认组织树作为用户目录/身份池”的设计，用于约束多组织树场景下用户生命周期、组织成员关系、权限资源建模和同步逻辑。
>
> 关联文档：`architecture.md`、`services/admin-service.md`、`org-user-permission-contract.md`、`../archive/2026-06-21/api-gap-analysis.md`（已归档，16 个 🔧 接口已实现）、`permission-center/overview.md`、`schema/admin-service.sql`、`schema/permission-center.sql`。

---

## 1. 背景

AccessMesh 支持多棵组织树，以适配企业中不同维度的组织结构，例如行政组织、岗位体系、项目团队、区域条线等。

早期考虑过抽象一个独立 `USER_POOL` 资源类型，用于管理可被加入各组织树的用户集合。但当用户规模达到数千到数万人时，独立用户池会带来额外管理界面、授权模型和候选集查询复杂度。

因此本设计采用更贴近现有 admin-service 模型的方案：

> **默认组织树就是租户内用户目录/身份池。**

默认组织树负责用户生命周期；其他组织树只管理“已有用户与本组织节点的关系”。

---

## 2. 核心决策

| 决策 | 说明 |
|------|------|
| 默认组织树承担用户目录语义 | `sys_org_tree_config.is_default=true` 的组织树不是普通展示默认值，而是租户内身份目录树。 |
| 用户生命周期只归默认组织树 | 创建用户、删除用户、禁用/启用、重置密码、账号资料维护属于 `ADMIN_USER` 权限，不下放给普通业务组织树管理员。 |
| 非默认组织树只维护成员关系 | 非默认组织树可以添加/移除已有用户与本组织节点的关系，不能创建、禁用、删除真实用户。 |
| 不新增 `USER_POOL` 资源类型 | 用户池概念由默认组织树承载，避免额外资源类型和额外授权面。 |
| 候选用户不是全租户用户 | 给非默认组织增加成员时，候选集来自默认组织树中操作者可见/可管理范围内的用户，而不是所有用户。 |
| 组织成员关系必须投影为权限角色关系 | `sys_user_org` 是权限事实来源之一，变化后必须由 `access.application` 同事务稳定投影为本地 `user_role`。 |

---

## 3. 权限边界

### 3.1 用户生命周期权限

用户生命周期操作使用 `ADMIN_USER`：

| 操作 | 权限 | 说明 |
|------|------|------|
| 新建用户 | `ADMIN_USER:CREATE` | 只能创建到默认组织树。 |
| 修改用户资料 | `ADMIN_USER:UPDATE` | 作用于用户身份本身；自我修改可保留业务豁免。 |
| 删除用户 | `ADMIN_USER:DELETE` | 高危生命周期操作，只能由身份目录管理员执行。 |
| 启用/禁用用户 | `ADMIN_USER:ENABLE/DISABLE` | 高危生命周期操作，不属于普通组织成员管理。 |
| 重置密码 | `ADMIN_USER:RESET_PASSWORD` | 高危账号操作，不属于普通组织成员管理。 |

### 3.2 组织成员关系权限

用户与组织、岗位、团队等节点的关系管理使用 `ADMIN_ORG:UPDATE`，作用点是目标组织/岗位实例：

| 操作 | 权限 | 说明 |
|------|------|------|
| 给非默认组织增加成员 | `ADMIN_ORG:UPDATE` | 添加已有用户与目标组织的关系。 |
| 从非默认组织移除成员 | `ADMIN_ORG:UPDATE` | 只删除关系，不删除用户身份。 |
| 给岗位挂载/移除用户 | `ADMIN_ORG:UPDATE` | 岗位是特殊组织，仍走组织成员关系。 |

默认组织树上的成员变更具有身份目录含义，不能与非默认组织树采用同一套普通关系操作语义。移动用户默认归属、解除默认树关系、切换主组织等操作必须按用户生命周期/身份目录规则单独校验。

### 3.3 功能角色权限

用户详情中分配 BASIC_ROLE、GROUP_ROLE、PERSONAL 等功能角色，仍使用 `ROLE:MANAGE`。这类操作不归 `ADMIN_ORG` 或 `ADMIN_USER`。

---

## 4. 数据模型语义

### 4.1 admin-service

| 表 | 语义 |
|----|------|
| `sys_user` | 用户身份事实源。用户创建、启停、删除均以该表为准。 |
| `sys_org` | 多组织树节点事实源。一个组织节点同时可能是可管理资源和角色容器。 |
| `sys_org_tree_config` | 组织树配置。`is_default=true` 的树承担身份目录语义。 |
| `sys_user_org` | 用户与组织节点关系。默认树关系表达用户目录归属；非默认树关系表达业务组织成员关系。 |

`sys_user_org` 当前未保存 `tree_config_id`，树归属由 `org_id` 落在哪棵 `sys_org_tree_config.root_org_id` 子树下推导。实现时必须保证组织树根不重叠，否则成员关系的树归属会产生歧义。若后续需要数据库级约束或大规模查询优化，可在独立迁移中增加 `tree_config_id`。

### 4.2 permission-center

同一个 admin 侧对象可能在 permission-center 中落成不同类型的事实：

| admin-service 对象 | permission-center 事实 | 用途 |
|--------------------|------------------------|------|
| `sys_user` | `abstract_user(subjectTypeCode=ADMIN_USER, subjectExternalId=sys_user.id)` | 作为权限主体参与鉴权。 |
| `sys_user` | `resource_entity(resourceTypeCode=ADMIN_USER, resourceCode=sys_user.id)` | 作为被管理资源支持实例级用户管理权限。 |
| `sys_org` | `resource_entity(resourceTypeCode=ADMIN_ORG, resourceCode=sys_org.id)` | 作为可管理组织资源支持组织实例权限。 |
| `sys_org` | `abstract_role(roleTypeCode=ORG/POSITION, roleExternalId=sys_org.id)` | 作为组织/岗位角色容器参与授权主链。 |
| `sys_user_org` | `user_role(abstract_user -> abstract_role)` | 把组织成员关系落成权限计算事实。 |

关键区分：

- `abstract_user` 是”谁在访问系统”。
- `resource_entity(ADMIN_USER)` 是”哪个用户对象被管理”。
- `resource_entity(ADMIN_ORG)` 是”哪个组织对象被管理”。
- `abstract_role(ORG/POSITION)` 是”组织/岗位成员关系带来的角色”。

这些概念不能混用。

**admin-service 不存储 permission-center 的任何内部主键 ID。** 所有跨服务操作统一使用业务键定位：`abstract_user` 用 `subjectTypeCode + subjectExternalId`，`resource_entity` 用 `resourceTypeCode + resourceCode`，`abstract_role` 用 `roleTypeCode + roleExternalId`。permission-center 内部通过 `TypeResolutionService` 解析业务键到内部 ID，已有 L1 Cache → L2 Redis → DB 三级缓存。

---

## 5. 投影契约

### 5.1 用户投影

用户投影必须覆盖两个事实，由 `access.application` 在写入 `sys_user` 的同一事务内维护（本地投影，业务键定位，不回填内部 ID）：

1. `sys_user -> abstract_user(subjectTypeCode=ADMIN_USER, subjectExternalId=sys_user.id)`
   - `enabled` 跟随 `sys_user.status`

2. `sys_user -> resource_entity(resourceTypeCode=ADMIN_USER, resourceCode=sys_user.id)`
   - `codeType = default`
   - `name = sys_user.name`

后续所有操作（user_role 写入、权限校验、缓存失效）均通过业务键引用，由本地权限引擎解析。

### 5.2 组织投影

组织投影必须同时覆盖两条线，由 `access.application` 在写入 `sys_org` 的同一事务内维护（本地投影，业务键定位，不回填内部 ID）：

1. 组织作为可管理资源：`resource_entity(resourceTypeCode=ADMIN_ORG, resourceCode=sys_org.id)`
   - `codeType = default`
   - 父节点通过 `resourceTypeCode=ADMIN_ORG + resourceCode=父sys_org.id` 定位，permission-center 内部解析 parentId
   - `extra` 建议包含 `orgType`、`treeConfigId`、`rootOrgId`、`isDefaultTree`、`level`、`leaderId`

2. 组织作为角色容器：
   - 普通组织 → `abstract_role(roleTypeCode=ORG, roleExternalId=sys_org.id)`
   - 岗位 → `abstract_role(roleTypeCode=POSITION, roleExternalId=sys_org.id)`
   - 父节点通过 `roleTypeCode + roleExternalId` 定位

实现上不再需要在 `sys_org` 表分别存储资源 ID 和角色 ID。

### 5.3 成员关系投影

`sys_user_org` 变更后由 `access.application` 在同一事务内投影为本地 `user_role`：

- 新增关系：通过业务键定位 `abstract_user` 和 `abstract_role`，写入 `user_role`。
- 删除关系：回收对应 `user_role`（投影缺失 fail-closed 整体回滚）。
- 岗位成员 relation 指向所属组织角色（POSITION 绑定/解绑须传入所属组织上下文）。
- 分配/回收后必须失效用户有效角色缓存（事务提交后经 `@PermissionChange` 广播）。

组织/岗位成员关系由 `access.application` 在同一事务内投影为 `user_role`（`LocalProjectionDomainService.bindUserOrg/unbindUserOrg`）。外部 sync 不得再提交 `sourceType=SYS_USER_ORG`。功能角色分配（BASIC_ROLE/GROUP_ROLE/PERSONAL）走正式用户角色管理接口和 `ROLE:MANAGE` 门禁。

非默认组织树的成员关系变更不得触发用户禁用、删除或 `abstract_user` 删除。

### 5.4 同事务本地投影

`sys_user`、`sys_org`、`sys_user_org`、`sys_menu` 的写入与对应权限投影在同一 PostgreSQL 事务内完成，不再写 `sys_sync_task`，也不再 Feign 自调用。见 [`access-service-architecture.md`](access-service-architecture.md) §4。

| 管理事实 | 投影动作 |
|----------|----------|
| `sys_user` | `upsertAdminUser` / `disableAdminUser` / `deleteAdminUser` |
| `sys_org` | `upsertAdminOrg` / `deleteAdminOrg` |
| `sys_user_org` | `bindUserOrg` / `unbindUserOrg` |
| `sys_menu`（非按钮） | `upsertAdminMenu` / `deleteAdminMenu` |

本地投影 `owner_service_code=access-service`，不写 `sync_metadata`。外部业务服务仍使用 `/api/perm/**/sync` 与 `sync_metadata`。`role_resource_permission` 属于权限管理域，不由管理事实投影产生。

### 5.5 全量校准同步（仅外部业务服务）

> T-ACCESS-005（2026-08-15）整改：内部同步子系统（`sys_sync_task`、Feign、调度重试）已删除，
> access-service 对 `sys_user`/`sys_org`/`sys_user_org`/`sys_menu` 的写入由 `access.application`
> 在同一事务内直接维护本地权限投影（见 §5.4），**不再需要也不应发起 full-sync 校准**。
> 本节的 full-sync 语义仅适用于仍通过 `/api/perm/**/sync` + `sync_metadata` 同步的外部业务服务。

全量同步用于修复外部业务服务单次同步漏发、重试耗尽、权限中心数据误删或多余同步事实残留，不替代单次同步。全量同步采用 permission-center 分领域校准接口：外部业务服务按强制 scope 上报该范围内完整事实，permission-center 在同一 source/scope 下对比自身同步事实，补齐缺失并清理多余数据。全量校准可以生成或执行 `UPSERT/BIND/DISABLE/DELETE/UNBIND`。

全量校准采用单请求全量接口，不做 begin/upload/commit 批次协议；因此 scope 必须足够小且明确，禁止默认按全租户清理。建议 scope：

| 领域 | 接口 | scope 示例 |
|------|------|------------|
| 主体 | `POST /api/perm/abstract-user/full-sync` | `subjectTypeCode={subjectTypeCode}` |
| 角色 | `POST /api/perm/abstract-role/full-sync` | `roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |
| 用户角色关系 | `POST /api/perm/user-role/full-sync` | `sourceType=SYS_USER_ORG&roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId}` |
| 资源实体 | `POST /api/perm/resource-entity/full-sync` | `resourceTypeCode={resourceTypeCode}` |

外部业务服务发起全量校准时必须按依赖顺序编排（**类型必须为外部业务服务自有类型**）：

1. `abstract_user/full-sync`：同步有效用户主体（`subjectTypeCode` 为外部服务自有用户类型）。
2. `resource-entity/full-sync(resourceTypeCode=<外部资源类型>)`：同步外部资源实体。
3. `abstract-role/full-sync(roleTypeCode=<外部角色类型>&treeRootExternalId={treeRootExternalId})`：同步外部角色容器。
4. `user-role/full-sync(sourceType=<外部 sourceType>&roleTypeCode={roleTypeCode}&treeRootExternalId={treeRootExternalId})`：同步外部成员关系。
5. 其他资源按资源类型调用 `resource-entity/full-sync`。

> **九轮评审 P2（2026-08-15）**：禁止使用 `ADMIN_USER`/`ADMIN_ORG`/`ORG`/`POSITION`/`SYS_USER_ORG` 等 access-service 本地投影保留键——外部 sync/full-sync 携带保留键会被 `LocalProjectionGuard` 以 20042（`LOCAL_PROJECTION_IMMUTABLE`）拒绝；access-service 对 `sys_user`/`sys_org`/`sys_user_org`/`sys_menu` 的投影由 `access.application` 同事务维护（§5.4），不参与任何 full-sync。

外部业务服务的单次删除/禁用仍必须生成对应 `DISABLE/DELETE/UNBIND` envelope；全量校准是最终一致性兜底，不是跳过单次同步的理由。

`businessKey` 与 `scopeKey` 编码格式统一以 `permission-center/api-contract.md` §6.2.2.4 为准；本文档只描述领域顺序，不维护另一套拼接规则。

---

## 6. API 语义

| 场景 | 契约 |
|------|------|
| 用户目录列表 | 查询默认组织树下用户，并按操作者在默认树中的可见/可管理范围过滤。 |
| 组织成员列表 | 查询目标组织已有成员，权限锚定目标组织。 |
| 添加成员候选集 | 从默认组织树中查询候选用户，并排除目标组织已有成员。不得默认暴露全租户用户。 |
| 创建用户 | 只能创建到默认组织树；`orgId` 必须属于默认组织树。 |
| 非默认树添加成员 | 只能建立用户与目标组织关系，不得删除该用户其他树关系。 |
| 非默认树移除成员 | 只能删除目标关系，不得删除用户身份。 |
| 设置主组织 | 首期只允许在默认组织树内操作；如果未来需要每棵树一个主节点，必须显式增加树维度。 |

`/user-org/assign` 不得再采用“先删除用户所有组织关系再插入新关系”的全局替换语义。允许的实现方式只有两类：

1. 纯追加/幂等添加目标关系。
2. 在明确传入树上下文后，仅替换该树内关系。

---

## 7. 默认组织树治理

默认组织树必须满足以下约束：

- 每个租户最多一棵默认组织树；业务上应保证存在且只有一棵身份目录树。
- 默认组织树根节点不能与其他组织树根节点形成祖先/后代重叠。
- 已存在用户时，切换默认组织树属于高危迁移动作，不应作为普通配置开关。
- 删除默认组织树或默认树节点时，必须先处理其下用户身份归属，不允许造成有效用户无默认树归属。
- `single_assoc=true` 在默认组织树中表示用户在身份目录内只有一个主归属；岗位树等非默认树可允许多归属。

### 7.1 组织树归属解析约定

user-org / user_role 同步链路上的 `treeRootExternalId` 必须由统一 resolver 解析得出：

- 任意 `orgId` 必须能沿 `SysOrg` 父链反查到唯一一个 `SysOrgTreeConfig.rootOrgId`，命中条件为：
  - 该 org 自身 `id ∈ rootOrgId 集合`；或
  - 该 org 的某个祖先 `id ∈ rootOrgId 集合`。
- 不能反查命中的 org 视为**游离 org**，禁止参与 user-org / user_role 同步，必须以业务异常 `ORG_TREE_ROOT_NOT_RESOLVED` 中断。
- 增量（`UserOrgWriteAppServiceImpl.assignUserToOrgs` / `removeUserFromOrg`）与外部业务服务的全量同步必须使用同一 resolver 入口：
  - 增量：`OrgTreeConfigDomainService.resolveTreeRootExternalId(tenantId, orgId)` —— 单条解析；
  - 全量：`OrgTreeConfigDomainService.resolveTreeRootExternalIds(tenantId, orgIds)` —— **批量解析**：一次加载租户全部 `SysOrgTreeConfig` 与全部相关 `SysOrg` 路径，内存内交集匹配，避免循环单条调用导致的 N+1。
- **批量 resolver 语义**：任一 `orgId` 不命中（org 不存在、游离、或租户无任何 tree config）即抛 `BizException(ORG_TREE_ROOT_NOT_RESOLVED)`，message 含全部缺失项以便排障；**禁止部分返回**，**禁止 fallback `"1"`**。空入参 → 返回空 Map，不调任何 mapper。
- 全量校准要求租户至少存在一棵默认组织树（`SysOrgTreeConfig.is_default=true` 且 `rootOrgId` 非空）；缺失即抛 `FULL_SYNC_NO_DEFAULT_TREE`，禁止 fallback。
- 全量同步的 `ORG_ROLE` / `USER_ROLE` 阶段按 **`(treeRootExternalId, roleTypeCode)` 二维分桶**发 envelope：`treeRoot` 由批量 resolver 解析每个 binding/org 的实际归属得出，**不再**依赖 `trees.get(0).getRootOrgId()` 隐式单树假设。
- 解析阶段不限 `is_default`：user-org 关系适用于任何已配置树（默认 + 非默认），均通过业务键 `treeRootExternalId = String.valueOf(rootOrgId)` 与 permission-center scopeKey 对账。
- **禁止任何形式的 `treeRootExternalId` 默认值**（如硬编码 `"1"`），无论增量、全量、或 fallback 路径。

---

## 8. 实现影响清单

| 优先级 | 影响项 |
|--------|--------|
| P0 | 修正 `user-org/assign` 的跨树全量删除语义，避免破坏默认组织树归属。 |
| P0 | 补齐 permission-center 鉴权与写入接口的业务键重载，使 admin-service 无需存储内部 ID。 |
| P0 | 补齐 permission-center 创建接口的父节点业务键解析能力：`resource_entity` 和 `abstract_role` 创建时接受父节点业务键（`resourceTypeCode+resourceCode` / `roleTypeCode+roleExternalId`），内部解析为 parentId。 |
| P0 | 补齐 `sys_user` 同步时同时创建 `resource_entity(ADMIN_USER)`，使用业务键定位。 |
| P0 | 补齐 `sys_org -> resource_entity(ADMIN_ORG)` 与 `sys_org -> abstract_role(ORG/POSITION)` 双同步，均使用业务键定位。 |
| P0 | 删除 `sys_user.perm_user_id`、`sys_org.perm_role_id` 字段及所有引用，改为业务键调用。 |
| P0 | 清理 `RoleProxyServiceImpl` 中 `ORG_ROLE` 旧口径：(1) `ROLE_TYPE_LABELS` 移除 `ORG_ROLE` 条目，新增 `ORG`→组织角色、`POSITION`→岗位角色；(2) `createRoleForOrg` 的 `roleTypeCode` 由硬编码 `ORG_ROLE` 改为按 `SysOrg.orgType` 动态选择 `ORG`(orgType=1) / `POSITION`(orgType=2)；(3) `grantMenuToRole` / `revokeMenuFromRole` 同理，由调用方传入而非硬编码；(4) 评估 `createRoleForOrg` 是否应废弃，改由 `access.application` 在组织写入事务内统一维护 `abstract_role(ORG/POSITION)`。 |
| P0 | 补齐 `sys_user_org -> user_role` 同步和缓存失效。 |
| P1 | 拆分用户目录、组织成员列表、添加成员候选集的查询语义。 |
| P1 | 默认组织树切换、删除、根节点配置增加保护规则。 |
| P1 | 内部同步任务模型已由 T-ACCESS-005 退役；管理事实与权限投影同事务维护。外部业务服务仍走 `/api/perm/**/sync|full-sync`。 |
| P2 | 前端文案和按钮从”新增用户”区分为”创建用户”和”添加已有用户”。 |

---

## 9. 禁止事项

- 禁止新增页面形状资源类型，例如 `ADMIN_ORG_USER`。
- 禁止把非默认组织树管理员授予用户禁用、删除、重置密码等生命周期能力。
- 禁止在 admin-service 存储 permission-center 的内部主键 ID（`abstract_user.id`、`resource_entity.id`、`abstract_role.id` 等）。所有跨服务引用使用业务键。
- 禁止在 `user-org` 非默认树操作中删除用户所有组织关系。
- 禁止让添加成员候选集默认包含租户内所有用户。
- 禁止在 user-org / user_role 同步链路上 fallback `treeRootExternalId="1"` 或其他静默默认值；缺失或解析失败必须以业务异常中断。
