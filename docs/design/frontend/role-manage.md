---
doc_type: design
title: 2.2 角色管理页 前端设计
status: draft
domain: frontend
last_reviewed: 2026-06-29
---

# 2.2 角色管理页 前端设计

> 任务：T-FE-002（Phase 1，mock 驱动）
> 后端契约：`docs/design/permission-center/api-contract.md` §5.2 / §6.10.3
> 参照范式：2.1 组织与用户页（`docs/archive/2026-06-21/org-user-page-impl-plan.md` §1/§4）

## 1. 页面定位

角色管理页承载 5 种抽象角色类型（ORG / POSITION / BASIC_ROLE / GROUP_ROLE / PERSONAL）的查看与维护。

- **本页仅消费功能角色**（BASIC_ROLE / GROUP_ROLE / PERSONAL）：可手工 CRUD。
- **ORG / POSITION 只读**：由组织同步自动生成（admin-service → permission-center），本页仅展示，不可手工增删改。
- **配权（ROLE:MANAGE）跳转 4.1 权限授予页**（T-FE-014，待实现）；本页不内嵌配权矩阵。

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
| externalId | string \| null | 外部标识（类型虚拟根为 null） |
| status | 0 \| 1 | 禁用 / 启用 |
| sortOrder | number | 排序 |
| children | RoleTreeNode[] | 子节点 |
| extra | string \| null | 扩展属性（树节点可选，编辑表单从 detail 获取） |

### 3.2 角色表单（RoleFormData）

| 字段 | 校验 | 说明 |
|---|---|---|
| roleTypeCode | 必填 | 新建可选 BASIC_ROLE/GROUP_ROLE/PERSONAL；编辑只读 |
| name | 必填，2-64 字符 | 角色名称 |
| externalId | 可空，字母数字下划线中划线，≤128 | 外部标识 |
| parentId | 可空 | 父角色（空=类型根） |
| status | 必填 | 启用/禁用 |
| sortOrder | 必填，0-9999 | 排序号 |
| extra | 可空 | 扩展属性 JSON |

## 4. 交互流程

### 4.1 树操作

- **加载**：进入页面 `getRoleTree({domainCode: null})` → 返回全局域角色树。
- **搜索**：输入框 `filter` → el-tree `filter-node-method` 按名称过滤。
- **选中**：点击节点 → 右侧展示详情卡片；分组角色同时加载额外基本角色。
- **拖拽移动**：`draggable` + `node-drop` → `moveRole({roleId, parentId})`；只读类型拒绝移动并回滚。
- **新增**：顶部「新增角色」下拉 → 按类型（BASIC_ROLE/GROUP_ROLE/PERSONAL）打开表单。
- **编辑/删除/启停**：详情卡片按钮。

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
| `ROLE:UPDATE` | UPDATE | ROLE | 编辑 / 启停 / 额外角色增删 |
| `ROLE:DELETE` | DELETE | ROLE | 删除 |
| `ROLE:MANAGE` | MANAGE | ROLE | 配权（跳 4.1） |

### 降级策略

- 无 `ROLE:VIEW` → 路由不可达（`meta.auths` 派生自 `ROLE_MANAGE_PERM_LIST`）。
- 无 `ROLE:CREATE` → 隐藏「新增角色」下拉。
- 无 `ROLE:UPDATE` → 隐藏编辑/启停/额外角色增删按钮。
- 无 `ROLE:DELETE` → 隐藏删除按钮。
- 无 `ROLE:MANAGE` → 隐藏「配权」按钮。
- ORG/POSITION 只读类型 → 无论权限如何，均不展示编辑/删除/配权按钮（业务约束，非权限）。

### mock 角色矩阵（`mock/login.ts`）

| 账号 | 角色管理权限 |
|---|---|
| admin | 全权（ROLE:VIEW/CREATE/UPDATE/DELETE/MANAGE） |
| sec（安全管理员） | 全权（与 C 功能角色分配同源） |
| hr（组织人事管理员） | 只读（ROLE:VIEW） |
| auditor（审计员） | 只读（ROLE:VIEW） |

## 8. API 核对清单（登记 T-PERM-022）

Phase 1 不改后端，🔧❌ 项登记为 Phase 2 后端任务 T-PERM-022。

### 🔧 需改造

1. **`/detail` 用内部主键而非业务键**
   - 现状：`RoleController.getRole` 用 `IdReq{id}`（内部主键）。
   - 期望：用业务键 `domainCode + roleTypeCode + roleExternalId`（已有未启用的 `RoleDetailReq`）。
   - 依据：api-contract §6.10.3 + 项目铁律「调用方不应存储 permission-center 内部主键 ID」。
   - 影响：Phase 1 mock 用树节点 id 工作正常；Phase 3 联调需后端切换。
   - 归属：T-PERM-022 🔧。

### ✅ 满足

- tree/list/create/update/move/remove/extra-roles/* 全部满足前端需求，请求/响应结构与 mock 对齐。

### 备注

- **RoleResp 缺 `roleTypeName` 友好字段**：后端已返回 `roleTypeName`，但前端统一用 `ROLE_TYPE_LABEL` 映射更稳（防类型码扩展时后端未同步）。非缺口。
- **本页范围限定**：tree/list 返回全部 5 种类型，前端按业务约束只对功能角色提供 CRUD（ORG/POSITION 只读）。非缺口，是设计意图（overview §角色模型）。

## 9. 已知限制（Phase 1）

- 配权按钮仅提示，待 T-FE-014（4.1 权限授予页）实现后接入跳转。
- 角色选择器组件未抽取，待 T-FE-014 推进时按 §6 确认。
- mock 角色树用「类型虚拟根」简化展示；真后端 tree 返回业务域内角色层级，联调时需适配虚拟根逻辑。
