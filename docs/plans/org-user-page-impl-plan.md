# 「组织与用户」融合页 · 设计 / 实现计划

> 配套文档：`docs/design/org-user-permission-contract.md`（权限契约 v1.2，本计划的门禁来源）、`docs/design/default-org-tree-user-lifecycle.md`（默认组织树与用户生命周期）、`docs/plans/api-gap-analysis.md`（API 核对清单）。
> 已锁定设计决策：
> ① 岗位 = 折叠卡片列表（**按左树选中组织筛选，展示该组织及其子组织下的岗位**）；② 成员含子级 = 子树匹配；
> ③ 默认组织树初始选中，且默认树是用户目录/身份池；④ 主组织仅表示默认树主归属；⑤ 初始密码先弹窗（Phase 2 后端定下发）；
> ⑥ 组织 CRUD 主入口 = 树节点 hover 操作 + 拖拽移动；⑦ 用户详情弹窗只读展示所属岗位；
> ⑧ 目录/路由 name 不改（`views/system/user/`，仅改显示标题）；
> ⑨ **Tab 简化为 2 个：成员管理 + 岗位管理**（2026-06-07 确认）；
> ⑩ **左树过滤岗位（`orgType=1` 仅显示普通组织）**（2026-06-07 确认）。

---

## 0. 现状基线（已核对源码）

| 模块 | 文件 | 现状 | 完成度 |
|------|------|------|--------|
| 页面壳 | `views/system/user/index.vue` | 左 `ReOrgTreePanel` + 右（搜索栏 + `PureTableBar` + `pure-table`）；行点击弹 `UserDetailPanel`；grid 布局已按 layout-patterns | 成员半边 ✅ |
| 成员逻辑 | `views/system/user/utils/hook.ts` | `useUserManage`：`loadTable(orgId 过滤)`、搜索、分页、增删改 | ✅ |
| 组织树 | `components/ReOrgTreePanel/src/index.vue` | 选择型：加载默认树（`isDefault`）、`org-change` 事件、compact popover；**只读，无 CRUD** | 读 ✅ / 写 ❌ |
| 用户详情 | `views/system/user/components/UserDetailPanel.vue` | 组织归属（`/user-org/*` 增删/设主）✅；角色列表历史上曾含 POSITION，按契约必须改为仅功能角色；候选用户需来自默认树可见范围 | 部分 ⚠️ |
| 用户表单 | `views/system/user/form.vue` | create/edit + compact 组织选择 | ✅ |
| API | `api/user-manage.ts` | `/org/tree`、`/org-tree-config/page`、`/user/*`、`/user-org/*`、`/user-role/*` | 成员侧 ✅ |
| Mock | `mock/user-manage.ts` | 全量；`/user/page` **已实现子树匹配**（`getDescendantOrgIds`）；`/user/create` 返回 `initialPassword` | ✅ |
| 路由 | `router/modules/system.ts` | `/system/user`，标题 **"用户管理"** | 待改名 |

**结论**：成员管理（用户 CRUD + 详情面板组织归属/角色）约 70% 已就绪；**组织 CRUD（矩阵 A）、Tab 结构、岗位 Tab、权限门禁（hasPerms/降级）几乎为零**。

---

## 1. 目标组件结构

```
组织与用户页（index.vue）
├─ 左：ReOrgTreePanel（默认树；过滤 orgType≠岗位；加 editable: 增/改/删/移）
└─ 右：<el-tabs>（上下文 = 左树选中组织）
   ├─ Tab 成员管理     MemberTab.vue   成员表格（默认树创建用户；非默认树添加已有用户；启停/重置密码仅身份目录权限）
   └─ Tab 岗位管理     PositionTab.vue  岗位折叠卡片（orgType=岗位）+ CRUD + 挂载用户
   └─（行点击用户 → UserDetailPanel 弹窗：组织归属 + 所属岗位(只读) + 功能角色）
顶部固定：组织信息卡片（选中组织详情 + 编辑按钮，替代原"组织信息"Tab）
辅助：OrgForm.vue（组织 create/edit 弹窗）
```

> 组织信息固定在顶部卡片展示，不再单独设 Tab。子组织通过左侧树展开查看，也不再设 Tab。
> 树节点 hover 显「加子/改/删」按钮，支持拖拽改 parent。`OrgInfoTab/SubOrgTab` 不再单独实现。

---

## 2. 核心迁移：岗位「角色 → 特殊组织」

**矛盾**：现 `UserDetailPanel` 把岗位当角色（`roleTypeCode=POSITION` + `relationOrgName`，经 `/user-role/assign`）；契约 v1.2 定为**特殊组织**（`ADMIN_ORG`，按 `orgType` / POSITION 树）。

**迁移方案（借现有端点，零新关系概念）**：

| 维度 | 迁移前（现状） | 迁移后（契约） |
|------|---------------|---------------|
| 岗位数据源 | `/user-role/list` 里的 POSITION 项 | `/org/*`（`orgType=岗位` / POSITION 树），平铺 |
| 用户↔岗位 | `/user-role/assign\|revoke` | `/user-org/assign\|remove`（org 成员关系，`orgType` 区分） |
| 用户的岗位 | 角色列表内联 | `getUserOrgs(userId)` 按 `orgType` 拆出（`OrgBrief.orgType` 已具备） |
| 角色列表 | 含 ORG/POSITION/BASIC/GROUP | `otherRoles` 仅留**功能角色**（BASIC_ROLE/GROUP_ROLE/PERSONAL），排除 ORG **和 POSITION** |
| 门禁 | — | 岗位 CRUD `ADMIN_ORG:*`；挂载用户 `ADMIN_ORG:UPDATE`；配权红线 |

**前端改动点**：`UserDetailPanel.otherRoles` 过滤加 `&& r.roleTypeCode !== "POSITION" && r.roleTypeCode !== "ORG"`；新增"所属岗位"节（只读，`getUserOrgs` 按 `orgType` 拆出岗位型 org）；岗位逻辑移入 `PositionTab.vue`。添加岗位成员时，候选用户必须调用默认树候选用户接口，不能从全租户用户或硬编码 mock 中取。

---

## 3. 任务分解（分阶段，文件级）

### P0 — 前端骨架（纯 mock 可跑通，不依赖后端改造）

| # | 任务 | 文件 | 要点 | 状态 |
|---|------|------|------|------|
| P0-1 | 路由/标题改名 | `router/modules/system.ts` | `title:"组织与用户"`；`name` 可保留 `SystemUser` | ✅ 已完成 |
| P0-2 | 右侧改 Tab 壳 | `index.vue` | 引入 `<el-tabs>`；**仅保留 2 个 Tab**：成员管理 + 岗位管理；顶部固定组织信息卡片 | ✅ 已完成 |
| P0-3 | 成员 Tab | `components/MemberTab.vue`(新) | 迁入现表格逻辑；新增**启用/禁用**（行内 `el-switch`）+ **重置密码**操作 | ✅ 已完成 |
| P0-4 | 组织树可编辑 | `ReOrgTreePanel/src/index.vue` | 加 `editable?:boolean`、`orgType?` 过滤；`editable` 时渲染节点 hover 操作（加子/改/删）+ 顶部"新增根组织"，emit `node-add/node-edit/node-delete/node-move`；**过滤 orgType=2（岗位）** | ✅ 已完成 |
| P0-5 | 组织表单 | `components/OrgForm.vue`(新) | 字段对齐 `OrgCreateReq/OrgUpdateReq`：`orgName/code/orgType/parentOrgId/status/sort` | ✅ 已完成 |
| P0-6 | 顶部组织信息卡片 | `index.vue` 内联 | 展示选中组织详情 + 编辑按钮（复用 OrgForm） | ✅ 已完成 |
| P0-7 | ~~子组织 Tab~~ | ~~已移除~~ | ~~子组织通过左侧树展开查看，不再设 Tab~~ | ✅ 已移除 |
| P0-8 | 岗位 Tab（折叠卡片） | `components/PositionTab.vue`(新) | **折叠卡片（el-collapse）**展示岗位（`orgType=岗位`）+ CRUD（复用 OrgForm，orgType 固定）+ "挂载用户"（选用户→`/user-org/assign`）。**按左树选中组织筛选**，展示该组织及其子组织下的岗位。卡片内展示：岗位名、所属组织路径、已分配人数、展开后的用户列表（调用 `/org/users`） | ✅ 已完成 |
| P0-9 | 详情面板迁移 | `UserDetailPanel.vue` | `otherRoles` 排除 POSITION + 新增「所属岗位」节（只读，取 `getUserOrgs` 按 `orgType=岗位` 拆分）；角色候选改"功能角色"数据源（去掉 301/302 岗位项） | ✅ 已完成 |
| P0-10 | API + Mock 扩充 | `api/user-manage.ts`、`mock/user-manage.ts` | 见下「接口增量」 | ✅ 已完成 |

**接口增量（P0 先 mock）**：
- ✅ `POST /org/create` `/org/update` `/org/delete`（`IdReq`）；移动复用 `/org/update` 改 `parentOrgId`
- ✅ 岗位：复用 `/org/tree`（按 `orgType`/POSITION 树）或加 `/org/page?orgType=`；**已补充 `/org/page` 按选中组织子树筛选（传 `orgId` 参数）**；挂载复用 `/user-org/*`
- ✅ `POST /user/enable`（`IdsReq`，启停）、`POST /user/reset-password`（`{userId,newPassword?}`）
- ✅ 功能角色候选来源：加 `POST /role/list`（仅 BASIC_ROLE/GROUP_ROLE/PERSONAL）
- ✅ **新增 `POST /org/users`（`{orgId}`）→ 返回该组织/岗位下的用户列表**（P0-8 岗位卡片内展示已分配用户）

### P1 — 后端契约（admin-service；契约 §8 遗留 + api-gap）

| # | 任务 | 依据 |
|---|------|------|
| P1-1 | `/user/page` 收敛为默认树用户目录查询；组织成员列表改走 `/org/users` 或专门成员列表 | 默认树身份目录契约 |
| P1-2 | 新增 `/user/member-candidates`：从默认树可见范围查询候选用户，排除目标组织已有成员 | api-gap §2/§3 |
| P1-3 | `/user/create` 增 `orgId`+初始密码返回，并同步 `abstract_user` + `ADMIN_USER resource_entity`（使用业务键，不回填内部 ID） | api-gap §2 |
| P1-4 | `/user/enable`、`/user/reset-password` 接前端，但只作为身份目录生命周期权限，不给普通非默认树成员管理员 | 矩阵 B |
| P1-5 | ✅ **成员门禁已修正**：`UserOrgServiceImpl` 使用 `ADMIN_ORG:UPDATE`（目标组织/岗位实例）；仍需修正跨树全量替换语义和 `user_role` 同步 | 契约 §8、备注 ② |
| P1-6 | 新增 `/user-role/{list,assign,revoke}` 代理，门禁 `ROLE:MANAGE` | 契约 §8 遗留②、备注 ③ |
| P1-7 | 岗位经 `/org/*`(orgType) + `/user-org/*`；`/role/list` 仅功能角色 | 契约 §8 遗留③ |

### P2 — 权限接线 + 降级 ✅ 已完成

| # | 任务 | 要点 | 状态 |
|---|------|------|------|
| P2-1 | 按钮门控 | 18 个操作点全部使用 `hasPerms(ORG_USER_PERMS.XXX)` 门控，perm 码来自 SSOT `perms.ts` | ✅ |
| P2-2 | 无权降级 | 树 `editable` = 三个写权限 OR；拖拽 `allow-drag/allow-drop` 受门控；岗位 Tab `v-if`；启用/禁用 `:disabled` + tooltip；组织归属读写分支 | ✅ |
| P2-3 | 菜单下发 | v1.4 双轨并行：`/auth/user-menu` 返回 `permissions[]`（perm 串列表），store 写入 Pinia + localStorage；mock 角色矩阵从 SSOT 反向导入（admin/hr/sec/auditor） | ✅ |

---

## 4. 权限接线清单（hasPerms → 按钮 → 降级）

> v1.4 起 perm 码统一为乙层格式 `资源类型:操作码`（如 `ADMIN_ORG:CREATE`），前端 `hasPerms` 与后端 `engine.hasPermission` 同源。
> 完整 SSOT 见 `frontend/src/views/system/user/utils/perms.ts`。

| 区域 / 控件 | 前端 perm 码（v1.4） | 后端门禁 | 无权表现 |
|---|---|---|---|
| 页面/树可见 | `ADMIN_ORG:VIEW` | 菜单可见性 | 不可进 |
| 树·新增根/子组织 | `ADMIN_ORG:CREATE` | `ADMIN_ORG:CREATE` | 隐藏 |
| 树·编辑/移动节点 | `ADMIN_ORG:UPDATE` | `ADMIN_ORG:UPDATE` | 隐藏/禁拖拽 |
| 树·删除组织 | `ADMIN_ORG:DELETE` | `ADMIN_ORG:DELETE` | 隐藏 |
| 成员表可见 | `ADMIN_USER:VIEW` | 读 | Tab 空 |
| 成员·创建用户 | `ADMIN_USER:CREATE` | `ADMIN_USER:CREATE`（仅默认组织树） | 隐藏 |
| 成员·添加/移除/设主组织 | `ADMIN_ORG:MANAGE_MEMBER` | `ADMIN_ORG:MANAGE_MEMBER`（目标组织） | 只读 |
| 成员·修改 | `ADMIN_USER:UPDATE` | `ADMIN_USER:UPDATE` | 隐藏 |
| 成员·删除 | `ADMIN_USER:DELETE` | `ADMIN_USER:DELETE` | 隐藏 |
| 成员·启用/禁用 | `ADMIN_USER:ENABLE` | `ADMIN_USER:ENABLE` | 隐藏切换 |
| 成员·重置密码 | `ADMIN_USER:RESET_PASSWORD` | `ADMIN_USER:RESET_PASSWORD` | 隐藏 |
| 详情·分配/回收功能角色 | `ROLE:MANAGE` | `ROLE:MANAGE`（目标角色） | 只读 |
| 岗位 Tab 可见 | `ADMIN_ORG:VIEW_POSITION` | 读 | Tab 隐藏 |
| 岗位·新增 | `ADMIN_ORG:CREATE_POSITION` | `ADMIN_ORG:CREATE_POSITION` | 隐藏 |
| 岗位·编辑 | `ADMIN_ORG:UPDATE_POSITION` | `ADMIN_ORG:UPDATE_POSITION` | 隐藏 |
| 岗位·删除 | `ADMIN_ORG:DELETE_POSITION` | `ADMIN_ORG:DELETE_POSITION` | 隐藏 |
| 岗位·挂载/卸载用户 | `ADMIN_ORG:ASSIGN_POSITION_USER` | `ADMIN_ORG:ASSIGN_POSITION_USER` | 只读 |
| ❌ 配置岗位/角色权限 | —（不在本页） | `ADMIN_ROLE:GRANT/REVOKE` | 红线 |

---

## 5. 设计决策（已确认，写死）

### 5.1 岗位 Tab 作用域 → **按选中组织筛选的折叠卡片**

岗位 = 特殊组织节点（`orgType=2`），挂在组织树下，与普通组织共享同一棵树。页面上不混入左侧组织树（左树仅显示 `orgType=1`），而是集中在岗位 Tab 以**折叠卡片（el-collapse）**展示——**按左树当前选中组织筛选，展示该组织及其子组织下的所有 `orgType=2` 节点**。每张卡片展开后显示已分配用户 + "添加成员"按钮。岗位所属组织通过 `parentOrgId` → `orgName` 展示以便定位。

> 例如：选中"研发中心"时，岗位 Tab 显示"研发中心"及其子组织（后端组、前端组等）下的所有岗位（研发总监、系统架构师、开发工程师等）。

**岗位卡片展示结构**：

```
┌─ 岗位折叠卡片（el-collapse-item）─────────────────────────┐
│ 标题行：🔽 研发总监                              1人已分配 │
│       📍 研发中心 > 后端组                              │
├─ 展开内容 ──────────────────────────────────────────────┤
│  ┌─ 用户列表 ─────────────────────────────────────────┐ │
│  │ 👤 张三                              [移除]        │ │
│  │ 👤 王五                              [移除]        │ │
│  └─────────────────────────────────────────────────────┘ │
│  [+ 添加成员]  [编辑岗位]  [删除岗位]                     │
└─────────────────────────────────────────────────────────┘
```

**字段说明**：
- **岗位名**：`orgName`（如"研发总监"）
- **所属组织路径**：`parentOrgId` 链向上追溯，展示 `"研发中心 > 后端组"`（便于定位岗位挂在哪个组织下）
- **已分配人数**：该岗位下通过 `user-org` 关联的用户数（调用 `/org/users` 统计）
- **用户列表**：展开后展示已分配用户，支持"移除"操作（调用 `/user-org/remove`）
- **添加成员**：弹窗选择默认树候选用户，调用 `/user-org/assign` 挂载到岗位；候选集不得使用全租户用户列表

**岗位的数据权限模型**（以"数据安全员"为例）：

> 数据安全员岗位由部门 A 设立（`parentOrgId = 部门 A`），对部门 A 及以下组织有效。
> 部门 A 下小组甲的用户张三被分配该岗位。
> 张三只能审批/查看小组甲的范围——数据权限由后端按张三所属组织（小组甲）+ 岗位生效范围（部门 A 及以下）动态判定，前端不感知。

这个模型与岗位=特殊组织（`orgType=2`，经 `/org/*` 管理、`/user-org/*` 挂载）**完全兼容**，无需改设计。前端只需做"**按选中组织筛选展示** + 挂载/卸载用户"两项。

**岗位筛选 API 细节**：
- 前端调用：`POST /org/page` 传 `{ orgType: 2, orgId: 选中组织ID }`
- 后端/mock 逻辑：先取 `orgId` 的子树所有组织 ID（含自身），再筛选 `orgType=2` 且 `parentOrgId` 在该集合内的岗位
- 排序：按 `parentOrgId` 组织层级 + `sort` 字段排序

### 5.6 岗位 API 设计

#### 5.6.1 岗位列表查询（按选中组织筛选）

```typescript
// POST /org/page
// 请求参数
interface OrgPageQuery {
  pageNum: number;
  pageSize: number;
  orgName?: string;      // 岗位名搜索
  orgType: 2;            // 固定=岗位
  orgId?: number;        // 选中组织ID（用于子树筛选）
  status?: number;
}

// 响应项
interface OrgPageItem {
  id: number;
  orgName: string;       // 岗位名
  code: string;
  parentOrgId: number | null;
  parentOrgName?: string; // 直接父组织名
  orgType: number;       // =2
  status: number;
  sort: number;
}
```

**筛选逻辑**（mock 实现）：
1. 获取 `orgId` 的子树所有组织 ID（含自身）——复用 `getDescendantOrgIds`
2. 筛选 `orgType === 2` 且 `parentOrgId` 在步骤1集合内的节点
3. 按 `parentOrgId` + `sort` 排序

#### 5.6.2 岗位下用户列表查询

```typescript
// POST /org/users
// 请求参数
interface OrgUsersQuery {
  orgId: number;         // 岗位ID
}

// 响应
interface OrgUserItem {
  userId: number;
  username: string;
  name: string;
  avatar?: string;
  isPrimary: boolean;    // 是否主组织
}
```

**实现逻辑**：遍历 `mockUsers`，筛选 `orgs` 中包含该 `orgId` 的用户。

#### 5.6.3 岗位 CRUD

复用 `/org/*` 接口，创建/更新时固定 `orgType = 2`：
- `POST /org/create` —— 创建岗位（`orgType` 固定为 2）
- `POST /org/update` —— 编辑岗位
- `POST /org/delete` —— 删除岗位（级联清理 `user-org` 关联）

#### 5.6.4 岗位用户挂载/卸载

复用 `/user-org/*` 接口：
- `POST /user-org/assign` —— 将用户挂载到岗位（`orgId` 为岗位ID）
- `POST /user-org/remove` —— 从岗位移除用户

---

### 5.2 用户详情弹窗 → **只读展示所属岗位**

详情面板在"组织归属"与"功能角色"之间加「所属岗位」节。岗位信息只读（来自 `getUserOrgs` 按 `orgType=岗位` 拆分），挂载/卸载操作统一归岗位 Tab。

### 5.3 组织 CRUD 主入口 → **树节点 hover 操作 + 拖拽移动**

树节点 hover 显「加子/改/删」按钮，支持拖拽改 parent。顶部组织信息卡片提供编辑入口作为补充。`ReOrgTreePanel` 的 `editable` prop 默认 `false`，compact/form 路径不受影响。

### 5.4 目录/路由名 → **暂不改**

保留 `views/system/user/` 目录，路由 `name: "SystemUser"` 不动，仅改 `meta.title` 为 `"组织与用户"`。减小改动面、不碰缓存键。后续如需统一为 `org-user`，单独排期。

### 5.5 Tab 结构 → **2 个 Tab + 顶部组织信息卡片**

经原型确认，Tab 简化为 2 个：
- **成员管理**：成员表格（搜索 + 表格 + 启用/禁用 + 重置密码）
- **岗位管理**：岗位折叠卡片（CRUD + 挂载用户）

组织信息不再设独立 Tab，改为**顶部固定卡片**展示（含编辑按钮）。子组织通过左侧树展开查看，不再设 Tab。

---

## 6. 验证与风险

- **验证**：每阶段跑 `pnpm build && pnpm typecheck && pnpm lint`（项目无 test 脚本）；P0 用 mock 手测四 Tab + 树 CRUD + 启停/重置/挂载岗位。
- **风险**：
  - 岗位迁移触及"用户的 orgs 现在会混入岗位（按 orgType 拆分）"——需 `getUserOrgs` 返回稳定 `orgType`，P0 mock 要补岗位型组织数据。
  - `ReOrgTreePanel` 是复用组件（form.vue 也用），加 `editable` 须保证默认关闭、compact 路径零回归。
  - 前端 perm 串依赖 `sys_menu` 下发；P2 前按钮可先用 `hasPerms` 占位，菜单未配则默认隐藏，需与后端约定初始放开策略避免"全隐藏"。

---

## 7. 建议执行顺序

`P0-1 → P0-2 → P0-10(mock 先行) → P0-4(树过滤+可编辑) → P0-6(顶部卡片) → P0-3(成员Tab) → P0-8(岗位Tab折叠卡片) → P0-9(详情面板) → P0-5(OrgForm) → 自测` ⇒ P0 前端骨架 ✅。
`P2`(权限接线 + 降级) ⇒ ✅。
随后 `P1`（后端契约改造）。

---

## 8. 当前进度（2026-06-14 更新）

### P0 完成度：100% ✅

| 任务 | 状态 | 备注 |
|------|------|------|
| P0-1 路由/标题改名 | ✅ | `title:"组织与用户"` |
| P0-2 右侧改 Tab 壳 | ✅ | 2 个 Tab + 顶部组织信息卡片 |
| P0-3 成员 Tab | ✅ | 启用/禁用 + 重置密码 |
| P0-4 组织树可编辑 | ✅ | editable + orgType 过滤 |
| P0-5 组织表单 | ✅ | OrgForm.vue 字段对齐 |
| P0-6 顶部组织信息卡片 | ✅ | 展示选中组织详情 |
| P0-8 岗位 Tab | ✅ | 折叠卡片 + CRUD + 挂载用户 |
| P0-9 详情面板迁移 | ✅ | `otherRoles` 排除 POSITION + 新增「所属岗位」节 |
| P0-10 API + Mock 扩充 | ✅ | `/org/users` + `/org/page` 子树筛选 |

### P2 完成度：100% ✅

| 任务 | 状态 | 备注 |
|------|------|------|
| P2-1 按钮门控 | ✅ | 18 个操作点 `hasPerms(ORG_USER_PERMS.XXX)` 门控 |
| P2-2 无权降级 | ✅ | 树 editable / 拖拽 / Tab 隐藏 / disabled / 读写分支 |
| P2-3 菜单下发 | ✅ | `/auth/user-menu` → store → `hasPerms()`；mock 4 角色矩阵 |

### P1 完成度：100% ✅（后端契约改造，16 个 🔧 接口）

| 任务 | 状态 | 备注 |
|------|------|------|
| P1-1 `/user/page` 收敛为默认树用户目录查询 | ✅ | orgId 子树语义 + 默认树校验 |
| P1-2 新增 `/user/member-candidates` | ✅ | 默认树候选范围 + 排除已有成员 |
| P1-3 `/user/create` 增 orgId + 初始密码返回 | ✅ | Outbox 双信封同步 |
| P1-4 `/user/enable`、`/user/reset-password` 身份目录边界 | ✅ | 默认树边界 + 权限门禁 |
| P1-5 `/user-org/*` 跨树语义 + user_role 同步 | ✅ | 关系级追加 + 高危保护 |
| P1-6 新增 `/user-role/{list,assign,revoke}` 代理 | ✅ | ID↔业务键翻译 + ROLE:MANAGE 门禁 |
| P1-7 岗位经 `/org/*` + `/role/list` 仅功能角色 | ✅ | orgType 区分权限 |

### 下一步

**P1 后端契约改造**（16 个 🔧 接口，契约已定稿 v1.0）：
1. `/user/page` 落实 `orgId` 子树语义
2. `/user/member-candidates` 新增候选用户查询
3. `/user/create` 增 orgId + 初始密码返回 + 同步 abstract_user + ADMIN_USER resource_entity
4. `/user/delete` 软删除 + 身份目录边界
5. `/user/enable`、`/user/reset-password` 身份目录生命周期权限
6. `/user-org/assign` 关系级追加 + user_role 同步
7. `/user-org/remove` 跨树语义 + user_role 回收
8. `/user-org/set-primary` 默认树主归属约束
9. `/user-role/{list,assign,revoke}` 代理（ID ↔ 业务键翻译）
10. `/org/create`、`/org/update`、`/org/delete` 组织 CRUD
