# 「组织与用户」融合页 · 权限契约

> 状态：**v1.1 定稿**（2026-06-06 完成第 8 节核对并按设计意图修正成员/岗位归属，源码佐证见该节）。本文是「组织管理 + 用户管理」融合页（菜单名：**组织与用户**）的权限设计基线。
> 操作码以 admin-service `AdminOperationCode`（`CREATE/UPDATE/DELETE/VIEW/ENABLE/DISABLE/RESET_PASSWORD/GRANT/REVOKE`）为准；前端 perm 码用 `system:模块:动作` 约定。
>
> 关联文档：`frontend/docs/design/frontend/api-gap-analysis.md`（接口契约）、`docs/design/improvement-plan.md`（页面地图，需同步合并 2.1 用户管理 + 3.1 组织架构 → 组织与用户）。

---

## 1. 背景与范围

将原计划的两页融合为一页：

- 左：**可管理组织树**（节点增删改 + 移动）
- 右：Tab 分区 —— **组织信息 / 成员（含子级）/ 岗位 / 子组织**
- 行点击：用户详情面板（组织归属 + 角色分配）

融合动机：两页骨架（组织树 + 用户/成员表）重合度约 70%，且组织/用户/成员/岗位同属一个业务域（组织人事/主体）。

### 核心抽象（核对后确立）

- **岗位 = 特殊组织**：admin-service 按 `SysOrg.orgType` 区分组织，岗位以独立 **POSITION 树**承载（`OrgTreeConfigServiceImpl.POSITION_TREE_TYPE`、`singleAssoc=false`），不进主组织树，单列岗位 Tab；经 `/org/*` 管理、由 `OrgSyncHandler` 同步至 permission-center。故岗位的增删改与"分配用户"全部归**组织管理**（`ADMIN_ORG`），不是独立角色面。
- **成员 = 组织成员关系**：用户与组织（含岗位）的归属是 `user-org` 关系，归"**组织成员管理**"，门禁锚定**组织实例**。
- **功能角色 = 真正的角色**：用户详情面板里分配的 BASIC_ROLE 等功能角色，才走 `ROLE` 资源类型与 `user-role` 关系。

### 调用链路

```
融合页（前端 hasPerms 门控）
   │  POST /org/* /user/* /user-org/* /user-role/*
   ▼
admin-service（甲层后端门禁）
   │  AdminPermissionValidator.check{Type|Instance|BatchInstance}Level(
   │      AdminResourceType, [resourceCode], AdminOperationCode)
   │  └─ Feign → permission-center /checkAuth（subjectTypeCode=ADMIN_USER）
   ▼
permission-center（乙层：被管理的权限模型）
   按 资源类型 × 操作码 判定；组织/岗位同步为内部角色（RoleType.ORG/POSITION）
```

> 实证：`admin-service/.../security/AdminPermissionValidatorImpl` 经 `PermissionFeignClient.checkAuth/batchCheckAuth` 调权限中心；各 `*ServiceImpl` 在写操作前调用 `permissionValidator.check*Level(...)`。

### 两层「权限」定义（全文沿用）

| 层 | 含义 | 本页落点 |
|----|------|----------|
| **甲层** | 操作本页所需的权限 | 前端按钮门控（`hasPerms`）+ **admin-service** `AdminPermissionValidator`（`AdminResourceType × AdminOperationCode`，经 Feign 落到权限中心 `checkAuth`） |
| **乙层** | AccessMesh 被管理的权限模型本身 | permission-center 注册的资源类型（`ADMIN_ORG/ADMIN_USER/ADMIN_ROLE` 等）、操作码、业务域、角色定义；组织/岗位同步为内部角色 |

---

## 2. 核心结论：融合不会让乙层变乱

后端按 **资源类型 × 操作码** 授权（admin-service Controller 无字符串权限码，全部走 Service 层 `permissionValidator.check*Level` → 权限中心），**不按页面授权**。因此：

> 融合的是 **UI 聚合**，不是授权边界。每个动作仍各自按其资源类型（`ADMIN_ORG` / `ADMIN_USER` / `ADMIN_ROLE`）独立校验，乙层模型不因融合而改变。

**乙层会乱，当且仅当踩到以下两个反模式之一：**

| 反模式 | 后果 |
|--------|------|
| 造**页面形状**的资源类型（如 `ADMIN_ORG_USER`）门控整页 | 与细粒度 `ADMIN_ORG:* / ADMIN_USER:*` 形成两套重叠词汇，授权语义说不清 |
| 把**配权/定义类操作**（给角色/岗位配菜单与资源权限、定义独立角色与字典）搬上本页 | 页面同时成为「被授权对象」与「授权定义工具」，跨进权限-定义域 |

这两件事**不做**，融合就不会让乙层乱。

---

## 3. 「融合反而更清晰」的机制

1. **以业务域为授权单元**：组织 + 岗位 + 用户 + 成员同属「组织人事/主体」业务域（对应 `DomainClassifyService`）。融合把该域显形成一块完整 UI。
2. **以资源类型为权限形状**：权限只按 `ADMIN_ORG`（含岗位）/ `ADMIN_USER` / `ADMIN_ROLE`（及底层 `ROLE`）造，不造页面资源类型。
3. **页面只是该域的投影**：于是可定义一个**与页面无关、可复用**的角色「组织人事管理员」=授予该域这组资源类型的 perms；页面将来再拆再合，该角色定义一行不动。

> 对照不融合：两页**照样**需要同样的细粒度授权，但易诱导出 `user-page` / `org-page` 两个页面形状权限，把同一业务域**碎片化**。
>
> 一句话：**乱的不是「混合」，是「按页面造权限」；融合逼你按域思考，反而堵住碎片化。**

---

## 4. 权限矩阵

> 列：UI 动作 → 乙层 `资源类型:操作`（= admin-service 实际门禁，括注端点）→ 甲层前端 perm 码 → 无权降级。
> 关系动作的资源归属已钉死，见第 5 节备注 ¹²³⁴。
> 「查看」类读接口在 Service 层**无 engine 门禁**，由菜单可见性（`ADMIN_MENU` 的 `VIEW`）+ 域过滤承担——故乙层列标注「读，无服务级门禁」。

### A. 组织树（Tab：组织信息 / 子组织）—— `ADMIN_ORG`（orgType≠岗位）

| UI 动作 | 资源:操作（乙层 / 端点） | 前端 perm 码（甲层） | 无权降级 |
|---|---|---|---|
| 查看组织树 | 读，无服务级门禁（`/org/tree`） | `system:org:view` | **页面入口最小权**（菜单可见性）；无则不可进 |
| 新增根/子组织 | `ADMIN_ORG:CREATE`（`/org/create`） | `system:org:add` | 隐藏「+新增组织」 |
| 编辑组织 | `ADMIN_ORG:UPDATE`（`/org/update`） | `system:org:edit` | 树只读，编辑按钮隐藏 |
| 删除组织 | `ADMIN_ORG:DELETE`（`/org/delete`） | `system:org:delete` | 隐藏删除 |
| 移动节点（改 parent） | `ADMIN_ORG:UPDATE` ¹（`/org/update` 改 `parentOrgId`） | `system:org:edit` | 禁用拖拽 |
| 启用/禁用组织 | `ADMIN_ORG:UPDATE` ¹（`/org/update` 改 `status`） | `system:org:edit` | 隐藏状态切换 |

### B. 成员（Tab：成员，含子级）—— 用户身份 `ADMIN_USER` + 组织成员关系 `ADMIN_ORG`

| UI 动作 | 资源:操作（乙层 / 端点） | 前端 perm 码 | 无权降级 |
|---|---|---|---|
| 查看成员列表 | 读，无服务级门禁（`/user/page?orgId`） | `system:user:view` | 成员 Tab 空/隐藏 |
| 新增用户（默认归当前组织） | `ADMIN_USER:CREATE`（`/user/create`） | `system:user:add` | 隐藏「+新增用户」 |
| 编辑用户 | `ADMIN_USER:UPDATE`（`/user/update`，改己豁免） | `system:user:edit` | 隐藏「修改」 |
| 删除用户 | `ADMIN_USER:DELETE`（`/user/delete`，批量实例级） | `system:user:delete` | 隐藏「删除」 |
| 启用/禁用 | `ADMIN_USER:ENABLE`（`/user/enable`，批量实例级） | `system:user:enable` | 隐藏状态切换 |
| 重置密码 | `ADMIN_USER:RESET_PASSWORD`（`/user/reset-password`，改己豁免） | `system:user:reset-pwd` | 隐藏「重置密码」 |
| 添加/移除成员、设主组织 | **`ADMIN_ORG:UPDATE`** ²（组织成员管理；作用在**目标组织实例**；`/user-org/assign|remove|set-primary`） | `system:org:member` | 成员增删只读 |

### C. 功能角色分配（行点击→详情面板）—— 真正的角色 `ROLE`

| UI 动作 | 资源:操作（乙层 / 端点） | 前端 perm 码 | 无权降级 |
|---|---|---|---|
| 查看用户角色 | 读，无服务级门禁（`/user-role/list`） | `system:user:view` | 角色区不显示 |
| 分配/回收功能角色 | **`ROLE:MANAGE`** ³（目标角色实例；`/user-role/assign|revoke`，**Phase 2 待建代理**） | `system:user:role:assign` | 角色区只读 |

### D. 岗位（Tab：岗位）—— 岗位 = 特殊组织 `ADMIN_ORG`（按 orgType / POSITION 树区分）⚠️ 配权贴近红线

| UI 动作 | 资源:操作（乙层 / 端点） | 前端 perm 码 | 无权降级 |
|---|---|---|---|
| 查看岗位 | 读，无服务级门禁（`ADMIN_ORG`，按 orgType=岗位 / POSITION 树过滤；`/org/tree`） | `system:org:position:view` | 岗位 Tab 隐藏 |
| 新增 / 编辑 / 删除岗位 | `ADMIN_ORG:CREATE` / `UPDATE` / `DELETE` ⁴（特殊组织，经 `/org/*`，同步 permission-center） | `system:org:position:add` / `:edit` / `:delete` | 隐藏增删改 |
| 分配 / 移除用户到岗位 | **`ADMIN_ORG:UPDATE`** ²（组织成员管理；作用在**岗位组织实例**；`/user-org/*`） | `system:org:position:assign` | 岗位区只读 |
| ~~配置岗位权限（授予菜单/资源权限）~~ | `ADMIN_ROLE:GRANT/REVOKE`（`/role/grant-menu`、`/role/revoke-menu`） | — | **❌ 红线：不在本页**（详见 §6.3） |

---

## 5. 关系动作归属契约（钉死，避免二义）

| 备注 | 规则（核对后定稿） |
|------|------|
| ¹ | **改类操作在本页甲层用粒度操作码**：admin-service 对组织（含岗位）的编辑、移动、改状态统一用 `UPDATE`（无独立 ORG ENABLE，状态改由 `/org/update` 承载），用户启用用 `ENABLE`。`OperationCodeConstants` 虽含 `UPDATE`，但 permission-center 内部角色管理把改/删折叠为 `MANAGE`——该折叠是乙层底层细节，不在本页甲层暴露 |
| ² | **成员增删 / 主组织 / 岗位用户 = 组织成员管理 = `ADMIN_ORG:UPDATE`（实例级，作用在目标组织/岗位实例上）**。语义是"管理选中组织/岗位的成员"，门禁锚定 **ORG 实例**，不归 `USER`。⚠️ **当前后端实现不符**：`UserOrgServiceImpl` 现以 `ADMIN_USER:UPDATE`（被操作用户）门禁，**Phase 2 必须改为 `ADMIN_ORG:UPDATE`（目标组织）** —— 见 §8 遗留实现项 |
| ³ | **功能角色分配（C 区，BASIC_ROLE 等）= `ROLE:MANAGE`（目标角色实例）**——须有权管理该角色，才能授予他人（AccessMesh 敏感面，宁严勿松）。permission-center `UserManageAppServiceImpl.assignRole/revokeRolesBatch` 已用 `getDeniedIds(..., ROLE, 目标角色, MANAGE)` 强制；admin-service `/user-role/*` 代理 **Phase 2 待建**（api-gap §4），建成后须沿用此门禁，且**不得**复用 `ADMIN_ROLE:GRANT/REVOKE`（那是配权语义，属红线） |
| ⁴ | **岗位作为特殊组织**：岗位实例的 CRUD 属组织管理（`ADMIN_ORG:*`，经 `/org/*`，本页允许），与"配置岗位权限"（红线）严格分离。岗位单列 Tab、用 POSITION 树承载（不进主组织树）；由 `OrgSyncHandler` 同步至 permission-center（内部对应 `RoleType.POSITION`）|

---

## 6. 三条军规（保证乙层不乱）

1. **只造资源类型形状的权限，绝不造页面形状的资源类型。** 菜单可见性 = 由 `ADMIN_ORG/ADMIN_USER` 的可见读 **派生**，不单独设 `ADMIN_ORG_USER:*`。
2. **关系动作钉死归属**（成员/主组织/岗位用户 = 组织成员管理 `ADMIN_ORG:UPDATE`；功能角色分配 = `ROLE:MANAGE`），写进第 4/5 节当契约，永不二义。
3. **守红线**：本页做「组织结构（含岗位作为特殊组织）+ 用户身份 + 成员/角色**关系**」，**不出现"配权 / 独立角色定义"类操作**。
   - **允许**：岗位实例 CRUD（`ADMIN_ORG:*`，岗位 Tab）—— 它是组织管理，不是角色定义。
   - **禁止**在本页暴露：`/role/grant-menu`、`/role/revoke-menu`（`ADMIN_ROLE:GRANT/REVOKE`，给组织/岗位/角色**配菜单与资源权限**）；`/role/create`（`ADMIN_ROLE:CREATE`，定义独立功能角色）；以及 orgType 字典 / 资源 / 操作 / 条件等定义。
   - 这些属「配权与定义」面，归权限中心管理页；本页仅消费"已存在的组织/岗位/角色 → 分配给用户"。

---

## 7. 业务域委派示例（清晰性的回报）

因第 4 节门禁全为资源类型形状、同属一个业务域，可一键委派整页能力：

```
角色：组织人事管理员（与页面无关、可复用）
  授予（组织人事业务域内）：
    ADMIN_ORG:   CREATE, UPDATE, DELETE        # 组织 + 岗位（特殊组织）的增删改、移动、改状态；
                                               # 成员/主组织/岗位用户归属含于 ADMIN_ORG:UPDATE；查看由菜单可见性派生
    ADMIN_USER:  CREATE, UPDATE, DELETE, ENABLE, RESET_PASSWORD   # 用户身份本身
    ROLE:        MANAGE（仅对目标功能角色）      # C 区"分配功能角色给用户"，不含角色定义
  不授予：
    ADMIN_ROLE:  CREATE / GRANT / REVOKE       # 独立角色定义、给组织/岗位/角色配权 —— 红线之外
    RESOURCE / OPERATION / CONDITION 定义       # 红线之外
```

只读委派（如审计岗）：仅授予各资源类型的可见读 / `VIEW` → 页面自动进入全只读降级态。

---

## 8. 核对结论（定稿依据）

> 草案 4 项「待核对」已逐项坐实，源码佐证如下；矩阵据此回填，「待核对」标记清除。
> 其中成员/岗位归属按设计意图（岗位=特殊组织、成员归组织管理）定稿，与当前后端实现的偏差列入「遗留实现项」。

1. **操作码** ✅
   `permission-center/.../constant/OperationCodeConstants.java` 含 `CREATE/VIEW/MANAGE/UPDATE/DELETE/ASSIGN/REVOKE/SYNC/MANAGE_API_MAPPING/SYNC_INTERFACE`——**`UPDATE` 存在**（注释为"某些场景下是 MANAGE 别名"）。但 `RoleManageAppServiceImpl` 实测：角色 `create→CREATE`，`update/delete/move→MANAGE`。本页甲层门禁实际取自 admin-service `AdminOperationCode.java`：`CREATE/UPDATE/DELETE/VIEW/ENABLE/DISABLE/RESET_PASSWORD/GRANT/REVOKE/PUBLISH/TRIGGER/TOGGLE`——**粒度齐全、无 MANAGE**。→ 矩阵改类操作用 `UPDATE/DELETE/ENABLE`，备注 ¹ 据此定稿。

2. **资源类型** ✅
   `permission-center/.../enums/ResourceTypeCode.java` = `USER/ROLE/RESOURCE/SERVICE/DOMAIN/API/TYPE_DEFINITION/SYSTEM_CONFIG/OPERATION/CONDITION/CONFLICT_RULE/DEPENDENCY`——**不含 ORG，不含 POSITION**。`enums/RoleType.java` 表明 `ORG(1)`、`POSITION(2)` 是角色类型（同步落地形态）。本页甲层资源类型取自 admin-service `AdminResourceType.java`：`ADMIN_USER/ADMIN_ORG/ADMIN_ROLE/ADMIN_MENU/...`——**`ORG` 以 `ADMIN_ORG` 坐实**。→ 矩阵乙层列用 `ADMIN_ORG/ADMIN_USER/ADMIN_ROLE`。

3. **岗位边界** ✅（按"岗位=特殊组织"定稿）
   admin-service 已按 `SysOrg.orgType` 区分组织，岗位以独立 **POSITION 树**承载（`OrgTreeConfigServiceImpl.POSITION_TREE_TYPE`、`singleAssoc=false`），经 `/org/*` 管理、`OrgSyncHandlerImpl` 同步至 permission-center。故：
   - **岗位实例 CRUD + 分配用户（本页允许）**：组织管理面 `ADMIN_ORG:*`、组织成员管理 `ADMIN_ORG:UPDATE`。
   - **配置岗位/角色权限（红线）**：`RoleProxyServiceImpl.grantMenuToRole/revokeMenuFromRole→ADMIN_ROLE:GRANT/REVOKE`；`createRoleForOrg→ADMIN_ROLE:CREATE`（`/role/*`）。
   → 红线收窄为"配权与独立角色定义"，岗位的组织管理本身在本页内；矩阵 D 区据此定稿。

4. **前端 perm 码约定** ✅
   全仓 `hasPerms` 仅见样例页 `views/permission/button/perms.vue`（`permission:btn:add/edit/delete`）与指令 `directives/perms`。格式 = **冒号分隔 `模块:实体:动作`**。本页组件（`views/system/user/*`）**尚未接线** hasPerms——故本契约定名（`system:org:*` / `system:user:*` / `system:org:position:*` / `system:user:role:assign`）。注意：前端 perm 串由菜单/按钮配置（`sys_menu` 经 `/user/user-menus`、`/role/my-info` 下发）提供，与后端 `AdminResourceType` 是两套命名空间，需在菜单配置侧补齐对应按钮权限。

### 遗留实现项（不阻塞契约定稿，落 Phase 2）

- **【门禁修正】成员/主组织/岗位用户**：`UserOrgServiceImpl` 当前以 `ADMIN_USER:UPDATE`（被操作用户）门禁，须改为 **`ADMIN_ORG:UPDATE`（目标组织/岗位实例）**，对齐"组织成员管理"语义（备注 ²）。
- **【新增代理】功能角色分配**：admin-service 新增 `/user-role/{list,assign,revoke}` 代理，门禁沿用 `ROLE:MANAGE`（备注 ³）。
- **【岗位接线】**：岗位 Tab 经 `/org/*`（按 orgType / POSITION 树过滤）管理；用户↔岗位经 `/user-org/*`。前端 mock 若把岗位归 `/user-role/*`（roleTypeCode=POSITION），须按本契约校正为组织成员关系。
- **【其他】** `/user/page` 增 `orgId` 过滤、`/user/create` 增 `orgId+初始密码`（api-gap §2）；菜单/按钮配置补齐本页 perm 码（§8.4）。

---

## 9. 变更记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-06-06 | v0.1 (DRAFT) | 初稿：核心原则 + 权限矩阵 + 三条军规 + 待核对清单 |
| 2026-06-06 | v1.0 (定稿) | 完成第 8 节 4 项核对（源码佐证）：链路修正为 融合页→admin-service→permission-center；乙层资源类型改用 `ADMIN_ORG/ADMIN_USER/ADMIN_ROLE`，改类操作用粒度 `UPDATE/DELETE/ENABLE`；前端 perm 码确认 `system:模块:动作`。清除全部「待核对」标记 |
| 2026-06-06 | **v1.1 (定稿)** | 按设计意图修正归属：① **成员/主组织/岗位用户**统一归"组织成员管理" = `ADMIN_ORG:UPDATE`（目标组织实例），当前后端 `ADMIN_USER:UPDATE` 列为待修正；② **岗位 = 特殊组织**（`ADMIN_ORG`，按 orgType / POSITION 树），其 CRUD + 分配用户走 `/org/*`、`/user-org/*` 组织管理面并同步 permission-center，红线收窄为"配权（`ADMIN_ROLE:GRANT/REVOKE`）+ 独立角色定义"；C 区重命名为"功能角色分配"以与岗位区分 |
