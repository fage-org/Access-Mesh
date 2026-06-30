---
doc_type: design
title: 2.2 角色管理页 前端设计
status: draft
domain: frontend
last_reviewed: 2026-06-30
---

# 2.2 角色管理页 前端设计

> 任务：T-FE-002（Phase 1，mock 驱动）
> 后端契约：`docs/design/permission-center/api-contract.md` §5.2 / §6.10.3
> 参照范式：2.1 组织与用户页（`docs/archive/2026-06-21/org-user-page-impl-plan.md` §1/§4）

## 1. 页面定位

角色管理页**仅管理可手工创建的功能角色**：`BASIC_ROLE`（基础角色）与 `GROUP_ROLE`（分组角色）。

5 种抽象角色类型中，其余 3 类不在本页展示：

| 类型 | 来源 | 权限分配归属 |
|---|---|---|
| `ORG` / `POSITION` | 组织同步自动生成（admin-service → permission-center，`default-org-tree-user-lifecycle.md:167`） | 4.1 权限授予页（T-FE-014） |
| `PERSONAL` | 用户同步连带创建（`abstract_user` 创建时自动生成 `PERSONAL_{external_id}`，schema permission-center.sql:103） | 4.1 权限授予页 + 2.1 用户详情页弹窗 |

**设计依据**：ORG/POSITION/PERSONAL 被抽象成角色，只是为了让它们能"像角色一样被分配权限"——它们本身不是"被管理的角色"。角色管理页的职责是**管理角色**（创建/编辑/删除功能角色），不是**分配和管理权限**。权限分配是 4.1 权限授予页的职责（`default-org-tree-user-lifecycle.md:72`：功能角色分配走 `ROLE:MANAGE`，不归 `ADMIN_ORG`/`ADMIN_USER`）。

- **本页可 CRUD**：BASIC_ROLE / GROUP_ROLE（`MANAGEABLE_ROLE_TYPES`）。
- **配权（ROLE:MANAGE）跳转 4.1 权限授予页**（T-FE-014，待实现）；本页不内嵌配权矩阵。
- **树结构（C2）**：后端 `getRoleTree` 返回**扁平森林**——根 = `parentId=null` 的真实角色，`TreeBuilder` 按 parentId 组装，**无任何"类型虚拟根"节点**。前端 hook `filterVisibleTree` 裁剪为仅 BASIC_ROLE / GROUP_ROLE 展示（跳过 mock ROOT 容器、按类型过滤）。

## 2. 布局结构

左右分栏（CSS Grid + Flex 分层，遵循 `frontend-layout-patterns`）：

```
┌─ Grid（role-page）──────────────────────────────────────┐
│ [角色树面板 minmax(220px,280px)] [详情区 1fr]            │
│  ├─ header（标题+新增下拉）     ├─ 角色信息卡片          │
│  ├─ 搜索框                      │   ├─ 名称/类型/状态标签 │
│  └─ el-scrollbar 树(flex:1)     │   └─ 编辑/启停/配权/删除 │
│     └─ el-tree（draggable）     ├─ meta（排序/ID/只读提示）│
│                                 └─ 额外基本角色区*        │
└─────────────────────────────────────────────────────────┘
* 额外基本角色区仅当选中分组角色（GROUP_ROLE）时显示
```

- 页面层 `display: grid; grid-template-columns: minmax(220px, 280px) 1fr`（响应式宽度，禁止固定 px）。
- 高度 `calc(100vh - var(--header-offset))`，`overflow: hidden` 截断溢出。
- 树面板与详情区均 `display: flex; flex-direction: column`，内容区 `flex: 1; min-height: 0`。
- 覆写 layout `.main-content` margin：`div.role-page.main-content { margin: var(--space-3) }`（特异性 0,2,1 > layout scoped 0,2,0，无需 `!important`）。

## 3. 字段定义

### 3.1 角色树节点（RoleTreeNode）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 角色 ID（permission-center 内部主键） |
| parentId | number \| null | 父角色 ID |
| roleTypeCode | string | 角色类型编码（5 种之一） |
| name | string | 角色名称 |
| externalId | string \| null | 外部标识（可空，真实角色也可能为空，**不**作为"虚拟根"判定） |
| status | 0 \| 1 | 禁用 / 启用 |
| sortOrder | number | 排序 |
| children | RoleTreeNode[] | 子节点 |
| extra | string \| null | 扩展属性（树节点可选，编辑表单从 detail 获取） |

### 3.2 角色表单（RoleFormData）

| 字段 | 校验 | 说明 |
|---|---|---|
| roleTypeCode | 必填 | 新建可选 BASIC_ROLE/GROUP_ROLE；编辑只读 |
| name | 必填，2-64 字符 | 角色名称 |
| externalId | 可空，字母数字下划线中划线，≤128 | 外部标识 |
| parentId | 可空 | 父角色（空=顶层森林根） |
| status | 必填 | 启用/禁用 |
| sortOrder | 必填，0-9999 | 排序号 |
| extra | 可空 | 扩展属性 JSON |

## 4. 交互流程

### 4.1 树操作

- **加载**：进入页面 `getRoleTree({domainCode: null})` → 后端返回扁平森林（parentId=null 真实角色为根，无类型虚拟根）→ hook `filterVisibleTree` 裁剪为仅 BASIC_ROLE / GROUP_ROLE 展示（跳过 mock ROOT 容器 + 按类型过滤同类型子树）。
- **搜索**：输入框 `filter` → el-tree `filter-node-method` 按名称过滤。
- **选中**：点击节点 → 右侧展示详情卡片（选中即渲染，根节点也是真实角色）；分组角色同时加载额外基本角色。
- **拖拽移动**：`draggable` + `:allow-drop` + `node-drop`。
  - `allowDrop` 拦截：只读类型不可拖动；**跨类型禁止**（目标节点 roleTypeCode 须与拖拽节点一致——inner 是父须同类型，before/after 是兄弟须同类型）；inner 到只读类型目标禁止。
  - `handleNodeDrop` 兜底：跨类型 `message` 提示 + `await loadTree()` 回滚（不调 moveRole）；只读类型同理回滚。
  - 合法则 `moveRole({roleId, parentId})`。
- **新增**：顶部「新增角色」下拉 → 按类型（BASIC_ROLE / GROUP_ROLE）打开表单，默认顶层（parentId=null）。
- **编辑/删除/启停**：详情卡片按钮（ROLE:MANAGE 统一门禁，见 §7）。

### 4.1.1 父角色选择器（RoleForm 内 popover 树）

C2 后无"类型虚拟根"概念，父角色在**同类型真实角色**中选取，或设为顶层（parentId=null）。

- **新建默认父级**：`parentId=null`（顶层森林根），`parentDisplay` 显示"（顶层）"。
- **可选父级**：popover 树展示同类型全部真实角色（`filterParentTree` 按 roleTypeCode 过滤，编辑态排除自身防环），点选切换 `parentId`；提供「设为顶层」按钮重置 null。
- **类型切换**：新建时切换角色类型，父角色自动重置为顶层（跨类型父子不合法）。
- **编辑态只读（P3）**：父角色渲染为纯只读 input，无 popover、不可点选。**编辑不修改 parentId**，层级调整只走拖拽/move 接口（`updateRole` 不含 parentId 字段）。

### 4.2 分组角色额外基本角色

仅 GROUP_ROLE 节点选中时显示：

- `listExtraRoles` 加载已关联基本角色列表。
- 「添加」弹出候选（未关联的 BASIC_ROLE）→ `addExtraRole`。
- 「移除」→ `removeExtraRole`。
- 业务键定位：`groupRoleTypeCode + groupRoleExternalId` + `basicRoleTypeCode + basicRoleExternalId`。

### 4.3 配权

- 选中非只读角色 → 「配权」按钮（ROLE:MANAGE）→ 跳转 4.1 权限授予页（T-FE-014 待实现，当前仅提示）。

## 5. API 依赖（链接后端契约章节）

| 操作 | 接口 | 请求 | 响应 | 核对 |
|---|---|---|---|---|
| 角色树 | `POST /api/perm/abstract-role/tree` | `{domainCode?}` | `ItemsResp<{root:RoleTreeNode}>` | ✅ |
| 角色列表 | `POST /api/perm/abstract-role/list` | `{domainCode?,roleTypeCode?,roleTypeCodes?,keyword?,pageNum,pageSize,sort?}` | `PaginatedResp<RoleResp>` | ✅ |
| 创建 | `POST /api/perm/abstract-role/create` | `{parentId?,roleTypeCode,externalId?,name,sortOrder?,extra?}` | `RoleResp` | ✅ |
| 更新 | `POST /api/perm/abstract-role/update` | `{roleId,name?,status?,sortOrder?,extra?}` | `RoleResp` | ✅ |
| 移动 | `POST /api/perm/abstract-role/move` | `{roleId,parentId?}` | `Void` | ✅ |
| 删除 | `POST /api/perm/abstract-role/remove` | `{ids:[]}` | `Void` | ✅ |
| 详情 | `POST /api/perm/abstract-role/detail` | `{id}` (IdReq) | `RoleResp` | 🔧 见 §7 |
| 额外角色列表 | `POST /api/perm/abstract-role/extra-roles/list` | `{domainCode?,groupRoleTypeCode,groupRoleExternalId}` | `ItemsResp<RoleSummaryResp>` | ✅ |
| 额外角色增 | `POST /api/perm/abstract-role/extra-roles/add` | 业务键 | `Void` | ✅ |
| 额外角色删 | `POST /api/perm/abstract-role/extra-roles/remove` | 业务键 | `Void` | ✅ |

## 6. 组件结构（含可复用组件识别）

```
views/system/role/
├── index.vue                  # 主页面（左右分栏 + 权限门控）
├── components/
│   └── RoleForm.vue           # 角色表单弹窗（新建/编辑）
└── utils/
    ├── hook.ts                # useRoleManage（树加载/CRUD/移动/额外角色）
    ├── perms.ts               # ROLE_MANAGE_PERMS（SSOT）
    └── types.ts               # RoleFormData + 辅助判定
```

### Step 1.5 组件识别（登记 T-FE-001 组件池）

| 候选 | 本页使用场景 | 跨页复用 | 确认状态 |
|---|---|---|---|
| 角色选择器 | 父角色选择（RoleForm 内 popover 树） | 4.1 权限授予（选角色）+ 2.1 功能角色分配 | ⏳ 待确认（T-FE-014 推进时） |
| 树面板（左树+搜索+CRUD hover） | 本页角色树 | 3.1 资源树 / 5.1 业务域树 | ⏳ 待确认（模式相似但数据结构异） |

> 角色选择器在 T-FE-014 推进时若模式一致则派生 `ReRolePicker` 子任务（T-FE-001 维护）。当前不提前抽取。

## 7. 权限接线（hasPerms → 按钮 → 降级）

`ROLE_MANAGE_PERMS`（`views/system/role/utils/perms.ts`，SSOT）：

| perm 串 | 操作码 | 锚点 | 控制按钮 |
|---|---|---|---|
| `ROLE:VIEW` | VIEW | ROLE | 路由可达 + 树可见 |
| `ROLE:CREATE` | CREATE | ROLE | 新增角色下拉 |
| `ROLE:MANAGE` | MANAGE | ROLE | 编辑 / 启停 / 删除 / 移动 / 配权（跳 4.1） |

> **B1 口径**：后端 `RoleManageAppServiceImpl` 的 updateRole(:150)/moveRole(:176)/deleteRoles(:196) 均以 `ROLE:MANAGE` 做门禁，无独立 UPDATE/DELETE/MOVE 操作码。前端 EDIT/DELETE/GRANT 统一映射到 `ROLE:MANAGE`（评审 P2 修正）。`ROLE_MANAGE_PERM_LIST` 用 `Set` 去重，确保路由 `meta.auths` 无冗余。

### 降级策略

- 无 `ROLE:VIEW` → 路由不可达（`meta.auths` 派生自 `ROLE_MANAGE_PERM_LIST`）。
- 无 `ROLE:CREATE` → 隐藏「新增角色」下拉。
- 无 `ROLE:MANAGE` → 隐藏编辑/启停/删除/配权/额外角色增删按钮。
- ORG/POSITION/PERSONAL 只读类型 → 无论权限如何，均不展示编辑/删除/配权按钮（业务约束，非权限）。

### mock 角色矩阵（`mock/login.ts`）

| 账号 | 角色管理权限 |
|---|---|
| admin | 全权（ROLE:VIEW/CREATE/MANAGE） |
| sec（安全管理员） | 全权（与 C 功能角色分配同源；B1 后 `ROLE_ADD + ROLE_GRANT(MANAGE)` 去重） |
| hr（组织人事管理员） | 只读（ROLE:VIEW） |
| auditor（审计员） | 只读（ROLE:VIEW） |

## 8. API 核对清单（登记 T-PERM-022）

Phase 1 不改后端，🔧❌ 项登记为 Phase 2 后端任务 T-PERM-022。

### 🔧 需改造

1. **`/detail` 用内部主键而非业务键**
   - 现状：`RoleController.getRole` 用 `IdReq{id}`（内部主键）。
   - 期望：用业务键二元组 `roleTypeCode + externalId` 定位（tenantId 走上下文，**不含 domainCode**）。
   - 依据：schema 唯一索引 `uk_abstract_role_external (tenant_id, role_type, external_id)`（permission-center.sql:147）已保证租户内 `(role_type, external_id)` 唯一；`abstract_role` 表无 domain 字段（编码规范 §18，bizDomainId 已删）；`external_id` 列注释明示「按 tenant_id + role_type + external_id 定位」。
   - 前端可行性：✅ 角色树 `RoleTreeNode` 已返回 `roleTypeCode` + `externalId`（RoleTreeResp.java），前端可直接取用，前提满足。
   - 旧 DTO 处置：**废弃 `RoleDetailReq.java`**（带 domainCode，bizDomainId 旧时代遗留，零引用，与 schema/编码规范 §18 矛盾）；T-PERM-022 新建正确的二元组请求体。
   - 影响：Phase 1 mock 用树节点 id 工作正常；Phase 3 联调（T-FE-016）需后端切换。
   - 归属：T-PERM-022 🔧。

2. **`/move` 缺父子类型兼容校验**
   - 现状：`RoleManageAppServiceImpl.moveRole`（:176）仅校验父存在 + 调用方 `ROLE:MANAGE`，**不校验**父子角色类型是否一致（同类型内嵌套合法，跨类型嵌套如 BASIC_ROLE 挂到 GROUP_ROLE 下应拒绝）。
   - 期望：move 时校验 `target.parentId` 对应父角色的 `roleTypeCode === node.roleTypeCode`，不一致则 `BizException` 拒绝。
   - 前端兜底：本页已用 `allowDrop` + `handleNodeDrop` 回滚拦截跨类型拖拽（P1-拖拽修复）；后端兜底校验为 Phase 2 缺口。
   - 归属：T-PERM-022 🔧。

### ✅ 满足

- tree/list/create/update/move/remove/extra-roles/* 全部满足前端需求，请求/响应结构与 mock 对齐。

### 备注

- **RoleResp 缺 `roleTypeName` 友好字段**：后端已返回 `roleTypeName`，但前端统一用 `ROLE_TYPE_LABEL` 映射更稳（防类型码扩展时后端未同步）。非缺口。
- **本页范围限定**：角色管理页仅管理 BASIC_ROLE / GROUP_ROLE（评审反馈驱动修正）。ORG/POSITION/PERSONAL 由外部同步生成，不在本页展示——它们被抽象成角色仅为"像角色一样被分配权限"，权限分配归 T-FE-014 权限授予页与 2.1 用户详情弹窗（评审确认 B 选项 3）。非缺口，是设计意图。
- **PERSONAL 权限分配入口**（登记 T-FE-014 设计要点）：① 4.1 权限授予页选角色时能选到 PERSONAL；② 2.1 用户详情页弹额外窗口配置该用户 PERSONAL 角色的权限（弹窗形式避免页面杂乱）。

## 9. 已知限制（Phase 1）

- 配权按钮仅提示，待 T-FE-014（4.1 权限授予页）实现后接入跳转。
- 角色选择器组件未抽取，待 T-FE-014 推进时按 §6 确认。
- mock 角色树对齐后端扁平森林结构（C2 已移除"类型虚拟根"展示构造）；联调时直接对接后端 `getRoleTree`，无虚拟根适配成本。
