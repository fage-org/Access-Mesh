---
doc_type: design
title: 2.2 角色管理页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-08-28   # 2026-08-28 T-PERM-022 收口：§4.1/§5/§8 终态化（detail 业务键/move 类型一致+环路 20050/tree 全量+enabledOnly）；此前：2026-07-26
---

# 2.2 角色管理页 前端设计

> 任务：T-FE-002（Phase 1，mock 驱动）
> 后端契约：`docs/design/permission-center/api-contract.md` §5.2 / §6.10.3
> 参照范式：2.1 组织与用户页（`docs/archive/2026-06-21/org-user-page-impl-plan.md` §1/§4）

## 1. 页面定位

角色管理页**仅管理可手工创建的功能角色**：`BASIC_ROLE`（基础角色，首期唯一功能角色）。
`GROUP_ROLE`（分组角色）自 T-PERM-043 起后端写入口删除（extra-roles/* 退役、create/update 拒绝 20022），本页选项隐藏、存量节点不展示（代码保留，见 §4.2）。

5 种抽象角色类型中，其余 3 类不在本页展示：

| 类型 | 来源 | 权限分配归属 |
|---|---|---|
| `ORG` / `POSITION` | 组织投影自动生成（`access.application` 同事务维护，`default-org-tree-user-lifecycle.md:167`） | 权限授予页（v3 已重建，T-FE-036；组织入口二期） |
| `PERSONAL` | 用户同步连带创建（`abstract_user` 创建时自动生成 `PERSONAL_{external_id}`，schema access-service.sql `abstract_role` 注释） | 权限授予页（v3 已重建；个人入口首期移除，待个人角色同步链路恢复）+ 2.1 用户详情页弹窗 |

**设计依据**：ORG/POSITION/PERSONAL 被抽象成角色，只是为了让它们能"像角色一样被分配权限"——它们本身不是"被管理的角色"。角色管理页的职责是**管理角色**（创建/编辑/删除功能角色），不是**分配和管理权限**。权限分配是权限授予页的职责（v3 已重建，T-FE-036）（`default-org-tree-user-lifecycle.md:72`：功能角色分配走 `ROLE:MANAGE`，不归 `ORG`/`USER` 资源类型）。

- **本页可 CRUD**：BASIC_ROLE（`MANAGEABLE_ROLE_TYPES`，T-PERM-043 后仅此一项）。
- **配权入口已恢复（T-FE-036，2026-08-02）**：角色信息卡片提供「权限授予」按钮（`ROLE:VIEW` 门控），跳转 `/perm/grant?subjectType=ROLE`；BASIC_ROLE 携带 `roleExternalId` 预选。本页不内嵌配权矩阵。
- **树结构（C2）**：后端 `getRoleTree` 返回**扁平森林**——根 = `parentId=null` 的真实角色，`TreeBuilder` 按 parentId 组装，**无任何"类型虚拟根"节点**。前端 hook `filterVisibleTree` 裁剪为仅 BASIC_ROLE 展示（跳过 mock ROOT 容器、按类型过滤；GROUP_ROLE 节点整棵裁掉）。

## 2. 布局结构

左右分栏（CSS Grid + Flex 分层，遵循 `frontend-layout-patterns`）：

```
┌─ Grid（role-page）──────────────────────────────────────┐
│ [角色树面板 minmax(220px,280px)] [详情区 1fr]            │
│  ├─ header（标题+新增下拉）     ├─ 角色信息卡片          │
│  ├─ 搜索框                      │   ├─ 名称/类型/状态标签 │
│  └─ el-scrollbar 树(flex:1)     │   └─ 编辑/启停/删除 │
│     └─ el-tree（draggable）     ├─ meta（排序/ID/只读提示）│
│                                 └─ 额外基本角色区*        │
└─────────────────────────────────────────────────────────┘
* 额外基本角色区仅当选中分组角色（GROUP_ROLE）时显示；T-PERM-043 后 GROUP_ROLE 节点
  不展示，该区不可达（代码保留，见 §4.2）
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
| roleTypeCode | 必填 | 新建仅可选 BASIC_ROLE（T-PERM-043 后 GROUP_ROLE 隐藏）；编辑只读 |
| name | 必填，2-64 字符 | 角色名称 |
| externalId | 新建必填（可管理类型）；编辑只读 | 外部标识；BASIC_ROLE 新建强制必填（业务键依赖：schema 唯一索引 `uk_abstract_role_external`；历史上额外角色功能亦依赖此业务键）。**编辑态只读**——externalId 是业务键/定位锚点，改它会破坏既有引用，与 parentId 只读同口径；update 请求不含 externalId 字段 |
| parentId | 可空 | 父角色（空=顶层森林根） |
| status | 必填 | 启用/禁用 |
| sortOrder | 必填，0-9999 | 排序号 |
| extra | 可空 | 扩展属性 JSON |

## 4. 交互流程

### 4.1 树操作

- **加载**：进入页面 `getRoleTree({domainCode: null})` → 后端返回扁平森林（parentId=null 真实角色为根，无类型虚拟根；T-PERM-022 起含禁用角色，status 为展示字段）→ hook `filterVisibleTree` 裁剪为仅 BASIC_ROLE 展示（跳过 mock ROOT 容器 + 按类型过滤同类型子树；GROUP_ROLE 节点整棵裁掉）。
- **搜索**：输入框 `filter` → el-tree `filter-node-method` 按名称过滤。
- **选中**：点击节点 → 右侧展示详情卡片（选中即渲染，根节点也是真实角色）。
- **拖拽移动**：`draggable` + `:allow-drop` + `node-drop`。
  - `allowDrop` 拦截：只读类型不可拖动；**跨类型禁止**（目标节点 roleTypeCode 须与拖拽节点一致——inner 是父须同类型，before/after 是兄弟须同类型）；inner 到只读类型目标禁止。
  - `handleNodeDrop` 兜底：跨类型 `message` 提示 + `await loadTree()` 回滚（不调 moveRole）；只读类型同理回滚。
  - 合法则 `moveRole({roleId, parentId})`，**成功后 `await loadTree()` 同步 parentId**（el-tree 仅移动 DOM 不更新 data.parentId，不重拉会导致后续编辑父角色展示/连续拖拽按旧 parentId 判断，评审 P2-拖拽）。
- **新增**：顶部「新增角色」下拉 → 按类型（BASIC_ROLE）打开表单，默认顶层（parentId=null）。
- **编辑/删除/启停**：详情卡片按钮（ROLE:MANAGE 统一门禁，见 §7）。

### 4.1.1 父角色选择器（RoleForm 内 popover 树）

C2 后无"类型虚拟根"概念，父角色在**同类型真实角色**中选取，或设为顶层（parentId=null）。

- **新建默认父级**：`parentId=null`（顶层森林根），`parentDisplay` 显示"（顶层）"。
- **可选父级**：popover 树展示同类型全部真实角色（`filterParentTree` 按 roleTypeCode 过滤，编辑态排除自身防环），点选切换 `parentId`；提供「设为顶层」按钮重置 null。
- **类型切换**：新建时切换角色类型，父角色自动重置为顶层（跨类型父子不合法）。
- **编辑态只读（P3）**：父角色渲染为纯只读 input，无 popover、不可点选。**编辑不修改 parentId**，层级调整只走拖拽/move 接口（`updateRole` 不含 parentId 字段）。

### 4.2 分组角色额外基本角色（T-PERM-043 已退役，代码保留）

后端 `extra-roles/list|add|remove` 三接口已删除（写入口 `add` 自实现起写 `user_role.abstract_user_id=null` 违反 NOT NULL 从未成功，`list` 恒空；管理侧 user_role 关系与运行时 `abstract_role.extra.basicRoleIds` 双事实源遗留登记见仓库 README「技术债遗留登记」段）。未来按 `role_inclusion(group_role_id, included_role_id)` 单事实源另行立项后恢复。

前端处置：**代码保留不删**——`index.vue` 额外基本角色面板（`v-if` GROUP_ROLE 选中）、`hook.ts` 的 `loadExtraRoles/addExtraRole/removeExtraRole`、`api/role-manage.ts` 的 extra-roles 封装与 `ROLE:ASSIGN/REVOKE` perm 串均保留；`MANAGEABLE_ROLE_TYPES` 收窄为 `[BASIC_ROLE]` 后 GROUP_ROLE 节点不进树、选项不进下拉，面板不可达（死代码，待恢复时随常量放开）。

### 4.3 配权

- 配权入口已恢复（T-FE-036，2026-08-02）：「权限授予」按钮（`ROLE:VIEW` 门控，`Key` 图标，类型主按钮）跳转 `/perm/grant?subjectType=ROLE`，BASIC_ROLE 携带 `roleExternalId` 预选。

## 5. API 依赖（链接后端契约章节）

| 操作 | 接口 | 请求 | 响应 | 核对 |
|---|---|---|---|---|
| 角色树 | `POST /api/perm/abstract-role/tree` | `{domainCode?,enabledOnly?}` | `ItemsResp<{root:RoleTreeNode}>` | ✅ |
| 角色列表 | `POST /api/perm/abstract-role/list` | `{domainCode?,roleTypeCode?,roleTypeCodes?,keyword?,pageNum,pageSize,sort?}` | `PaginatedResp<RoleResp>` | ✅ |
| 创建 | `POST /api/perm/abstract-role/create` | `{parentId?,roleTypeCode,externalId?,name,sortOrder?,extra?}` | `RoleResp` | ✅ |
| 更新 | `POST /api/perm/abstract-role/update` | `{roleId,name?,status?,sortOrder?,extra?}` | `RoleResp` | ✅ |
| 移动 | `POST /api/perm/abstract-role/move` | `{roleId,parentId?}` | `Void` | ✅ |
| 删除 | `POST /api/perm/abstract-role/remove` | `{ids:[]}` | `Void` | ✅ |
| 详情 | `POST /api/perm/abstract-role/detail` | `{roleTypeCode,roleExternalId}` | `RoleResp` | ✅ |

> T-PERM-043：`extra-roles/list|add|remove` 三行移除（后端接口删除，前端封装保留为不可达代码，见 §4.2）。`create`/`update` 后端显式拒绝 GROUP_ROLE（20022），与本页仅 BASIC_ROLE 的口径一致。

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
| 角色选择器 | 父角色选择（RoleForm 内 popover 树） | 权限授予页（选角色，待重做）+ 2.1 功能角色分配 | ⏳ 待确认（权限授予页重做时） |
| 树面板（左树+搜索+CRUD hover） | 本页角色树 | 3.1 资源树 / 5.1 业务域树 | ⏳ 待确认（模式相似但数据结构异） |

> 角色选择器在权限授予页重做时若模式一致则派生 `ReRolePicker` 子任务（T-FE-001 维护）。当前不提前抽取。

## 7. 权限接线（hasPerms → 按钮 → 降级）

`ROLE_MANAGE_PERMS`（`views/system/role/utils/perms.ts`，SSOT）：

| perm 串 | 操作码 | 锚点 | 控制按钮 |
|---|---|---|---|
| `ROLE:VIEW` | VIEW | ROLE | 路由可达 + 树可见 |
| `ROLE:CREATE` | CREATE | ROLE | 新增角色下拉 |
| `ROLE:MANAGE` | MANAGE | ROLE | 编辑 / 启停 / 删除 / 移动 |
| `ROLE:ASSIGN` | ASSIGN | ROLE | 分组角色添加额外基本角色（T-PERM-043 后不可达，代码保留） |
| `ROLE:REVOKE` | REVOKE | ROLE | 分组角色移除额外基本角色（T-PERM-043 后不可达，代码保留） |

> **B1 口径**：后端 `RoleManageAppServiceImpl` 的 updateRole/moveRole/deleteRoles 均以 `ROLE:MANAGE` 做门禁，无独立 UPDATE/DELETE/MOVE 操作码；detail/list/tree 读接口以类型级 `ROLE:VIEW` 做门禁（T-PERM-022 收口）。前端 EDIT/DELETE/GRANT 统一映射到 `ROLE:MANAGE`（评审 P2 修正）。`ROLE_MANAGE_PERM_LIST` 用 `Set` 去重，确保路由 `meta.auths` 无冗余。
>
> **额外角色独立门禁（评审 P1-额外角色；T-PERM-043 后接口已删）**：原后端 addExtraRole/removeExtraRole 分别校验 `ASSIGN`/`REVOKE` 的入口已删除；前端 `canAssign`/`canRevoke` 门控与 perm 串保留（面板不可达），待 role_inclusion 立项恢复接线。

### 降级策略

- 无 `ROLE:VIEW` → 路由不可达（`meta.auths` 派生自 `ROLE_MANAGE_PERM_LIST`）。
- 无 `ROLE:CREATE` → 隐藏「新增角色」下拉。
- 无 `ROLE:MANAGE` → 隐藏编辑/启停/删除按钮。
- 无 `ROLE:ASSIGN` / `ROLE:REVOKE` → 隐藏额外角色「添加/移除」按钮（T-PERM-043 后面板不可达，门控保留）。
- ORG/POSITION/PERSONAL 只读类型 → 无论权限如何，均不展示编辑/删除按钮（业务约束，非权限）；GROUP_ROLE 节点整体不展示（写入口已删除）。

### mock 角色矩阵（`mock/login.ts`）

| 账号 | 角色管理权限 |
|---|---|
| admin | 全权（ROLE:VIEW/CREATE/MANAGE/ASSIGN/REVOKE） |
| sec（安全管理员） | 全权（与 C 功能角色分配同源；含 ROLE_ADD/EDIT(MANAGE)/ASSIGN/REVOKE） |
| hr（组织人事管理员） | 只读（ROLE:VIEW） |
| auditor（审计员） | 只读（ROLE:VIEW） |

## 8. API 核对清单（登记 T-PERM-022）

Phase 1 不改后端，🔧❌ 项登记为 Phase 2 后端任务 T-PERM-022。**已收口 2026-08-28**：三项全部落地（含评审新发现的 move 环路防护与 tree enabledOnly 消费方口径，均经设计定案），处置记录见各条。

### 🔧 需改造（已全部收口 2026-08-28）

1. **`/detail` 用内部主键而非业务键**
   - 现状：`PermRoleController.getRole` 用 `IdReq{id}`（内部主键）。
   - 期望：用业务键二元组 `roleTypeCode + externalId` 定位（tenantId 走上下文，**不含 domainCode**）。
   - 依据：schema 唯一索引 `uk_abstract_role_external (tenant_id, role_type, external_id)`（access-service.sql `abstract_role` 节）已保证租户内 `(role_type, external_id)` 唯一；`abstract_role` 表无 domain 字段（编码规范 §18，bizDomainId 已删）；`external_id` 列注释明示「按 tenant_id + role_type + external_id 定位」。
   - 前端可行性：✅ 角色树 `RoleTreeNode` 已返回 `roleTypeCode` + `externalId`（RoleTreeResp.java），前端可直接取用，前提满足。
   - 旧 DTO 处置：**废弃 `RoleDetailReq.java`**（带 domainCode，bizDomainId 旧时代遗留，零引用，与 schema/编码规范 §18 矛盾）；T-PERM-022 新建正确的二元组请求体。
   - 影响：Phase 1 mock 用树节点 id 工作正常；Phase 3 联调（T-FE-016）需后端切换。
   - 归属：T-PERM-022 🔧。
   - **处置（2026-08-28 收口）**：`RoleDetailReq` 重写为二元组 `{roleTypeCode, roleExternalId}`（不带 domainCode），Controller/Service/Mapper 走 `selectByTypeAndExternalId`；未命中 `data=null`（未知 roleTypeCode 与 list 空分页同口径）；前端 `getRoleDetail` 封装与 mock 同步切业务键。契约见 api-contract §5.2 角色管理契约要点。

2. **`/move` 缺父子类型兼容校验**
   - 现状：`RoleManageAppServiceImpl.moveRole` 仅校验父存在 + 调用方 `ROLE:MANAGE`，**不校验**父子角色类型是否一致（同类型内嵌套合法，跨类型嵌套如 BASIC_ROLE 挂到 GROUP_ROLE 下应拒绝）。
   - 期望：move 时校验 `target.parentId` 对应父角色的 `roleTypeCode === node.roleTypeCode`，不一致则 `BizException` 拒绝。
   - 前端兜底：本页已用 `allowDrop` + `handleNodeDrop` 回滚拦截跨类型拖拽（P1-拖拽修复）；后端兜底校验为 Phase 2 缺口。
   - 归属：T-PERM-022 🔧。
   - **处置（2026-08-28 收口）**：`moveRole` 补同类型校验（跨类型拒绝 20022，`Objects.equals` 比较父子 role_type）。**评审新发现一并修复（设计定案）**：move 原无环路防护——移到自身/子孙下 parent 链成环（环节点从树构建静默消失、祖先/子孙递归 CTE 不收敛可挂查询，admin 域组织 10108/菜单 10207 有同场景先例而角色域漏配）；复用 `resolveDescendantRoleIdsBatch` 判定 + 新错误码 **20050** `ROLE_PARENT_INVALID`。存量 GROUP_ROLE 组树为冻结读模型，跨类型装配自本任务起无 API 通道（UserRoleWriteProjectionPgIT 组树装配改 JDBC 直改 + 投影镜像，引擎语义用例不受影响）。**评审收口（设计定案）**：sync/full-sync 的 parent 补同款环路判定（防外部错误数据旁路 move 防护；单条 sync 判环先于版本推进——拒绝不消耗同步版本；full-sync 为写入前逐项判定：当前生效图=库内关系+本事务已应用项的边，STALE 保持旧边、仅拒真正闭合环的项、前缀安全项放行，同批重复 businessKey 拒绝 DUPLICATE_BUSINESS_KEY，父类型缺省=scope 类型落地契约 §6.2.2.4；全程一次查询无循环内数据库调用）；detail/list 补类型级 ROLE:VIEW 门禁（与 tree 同款）；remove 级联扩展到 BASIC_ROLE 子孙且**级联根有权即整棵子树可删、不对子孙做独立权限过滤**（项目规则「父级有权限子级即有权限」，2026-08-28 设计定案——内部门禁引擎级统一登记 **T-PERM-045**）。并发交叉移动的成环窗口与递归 CTE 遇环不收敛登记 **T-PERM-044**（三棵树统一加固，含 admin 域组织/菜单同构缺口）。

3. **`/tree` 只返回启用角色，禁用后从树消失无法再启用**
   - 现状：`getRoleTree` 走 `selectEnabledRoleTree`（已退役，见处置行），SQL 含 `AND status = 1`，禁用角色不在树中。
   - 期望：树接口返回 `delete_flag=0` 全部有效角色，`status` 只作展示字段（禁用角色仍可见、可重新启用）——新增 `selectValidRoleTree`（只过滤 `delete_flag=0`），`getRoleTree` 改用它。
   - 前端可行性：✅ 本页已有启停按钮（`handleToggleStatus`），mock 树含禁用节点（id=103 访客 status=0），前端按 status 渲染禁用标签、支持从树中重新启用。后端切换后前端无需改动。
   - 影响：Phase 1 mock 含禁用节点体验正常；Phase 3 联调（T-FE-016）真后端下禁用角色会消失，需后端先切换。
   - 归属：T-PERM-022 🔧。
   - **处置（2026-08-28 收口）**：`selectEnabledRoleTree` 退役，新 `selectValidRoleTree`（仅过滤 `delete_flag=0`），禁用角色入树、status 为展示字段。**消费方口径（设计定案：前端入参后端过滤）**：请求体新增 `enabledOnly`（默认 false 返回全部有效角色）——本页不传（需见禁用可再启用）；授权页主体树传 true（SQL 过滤，T-FE-036 既有的禁用标记渲染保留为防御展示）。契约见 api-contract §6.10.3。

### ✅ 满足

- list/create/update/move/remove 全部满足前端需求，请求/响应结构与 mock 对齐。
  - ~~**tree 不在此列**：`/tree` 只返回启用角色（`AND status=1`），禁用后从树消失无法再启用，见 §8 第 3 条 🔧（T-PERM-022）。~~（已随 T-PERM-022 收口：树返回全部有效角色，见 §8 第 3 条处置）

### 备注

- **RoleResp 缺 `roleTypeName` 友好字段**：后端已返回 `roleTypeName`，但前端统一用 `ROLE_TYPE_LABEL` 映射更稳（防类型码扩展时后端未同步）。非缺口。
- **本页范围限定**：角色管理页仅管理 BASIC_ROLE（T-PERM-043 后唯一可手工创建的功能角色）。ORG/POSITION/PERSONAL 由外部同步生成，不在本页展示——它们被抽象成角色仅为"像角色一样被分配权限"，权限分配归权限授予页（v3 已重建，T-FE-036；组织入口二期、个人入口首期移除）与 2.1 用户详情弹窗（评审确认 B 选项 3）。GROUP_ROLE 写入口已删除、选项隐藏（存量节点不展示，delete/move 后端仍可用作清理）。非缺口，是设计意图。
- **PERSONAL 权限分配入口**（原 T-FE-014 设计要点，v3 首期移除个人入口，permission-grant.md §1.2）：① 权限授予页选角色时能选到 PERSONAL（待个人 `abstract_role` 同步链路落地后恢复）；② 2.1 用户详情页弹额外窗口配置该用户 PERSONAL 角色的权限（弹窗形式避免页面杂乱）。

## 9. 已知限制（Phase 1）

- ~~配权入口已随旧权限授予页移除~~（T-FE-036 已恢复，2026-08-02：「权限授予」按钮跳转 `/perm/grant?subjectType=ROLE`，见 §4.3）。
- 角色选择器组件未抽取，待权限授予页重做时按 §6 确认（T-FE-036 落地为页面级数据适配器，语义差异大不抽取整块 UI，同 §6 既有结论）。
- mock 角色树对齐后端扁平森林结构（C2 已移除"类型虚拟根"展示构造）；联调时直接对接后端 `getRoleTree`，无虚拟根适配成本。
