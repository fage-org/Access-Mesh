---
doc_type: design
title: 6.1 类型定义页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-06-30
---

# 6.1 类型定义页 前端设计

> 任务：T-FE-003（Phase 1，mock 驱动）
> 后端契约：`docs/design/permission-center/api-contract.md` §5.1
> 参照范式：2.1 组织与用户页（`docs/archive/2026-06-21/org-user-page-impl-plan.md` §1/§4 表格列表范式）、2.2 角色管理页（`role-manage.md` SSOT/降级/核对清单结构）

## 1. 页面定位

类型定义页管理 `type_definition` 表的**多组枚举字典**：按 `type_key` 分组，每条是 `(typeCode 对外稳定编码, typeValue 内部值, name, isSystem, sortOrder, extra)` 的 code↔value 映射。

- **4 个 typeKey 分组**：`user_type`（用户/主体类型）、`role_type`（角色类型）、`resource_type`（资源类型）、`group_type`（分组类型）。对齐 schema `permission-center.sql:15-50` 注释。
- **系统预置项（isSystem=true）**：租户初始化自动写入，**不可删改**（schema:47 注释）——前端隐藏编辑/删除按钮（业务约束，非权限），后端亦跳过删除。
- **租户自定义项（isSystem=false）**：可 CRUD。
- **无 status 字段**：`type_definition` 表无启停概念（与 `abstract_role` 不同），本页无启停列/启停按钮。
- **typeValue 不让用户填**：服务端在 `tenant+typeKey` 内自动分配（设计意图，对齐 T-PERM-019 D1）。Phase 1 mock 自行 `max+1` 分配；表单不展示 typeValue 字段，列表只读展示以便理解 code↔value。

## 2. 布局结构

PureTableBar 表格列表范式（遵循 `frontend-layout-patterns`），非左右分栏树范式：

```
┌─ type-def-page（main-content）─────────────────────────────┐
│ PureTableBar                                                │
│  ├─ #title：typeKey 下拉 + keyword 输入 + 搜索/重置        │
│  └─ #buttons：新增（v-if=canAdd）                           │
│ pure-table（adaptive 分页表格）                             │
│  列：typeKey(标签) / typeCode / name / typeValue(只读)      │
│      / isSystem(标签) / sortOrder / 操作                    │
│ 分页                                                        │
└─────────────────────────────────────────────────────────────┘
```

- 表格滚动交给 `pure-table` 的 `adaptive` prop，不手动设 overflow。
- 覆写 layout `.main-content` margin：`div.type-def-page.main-content { margin: var(--space-3) }`（特异性 0,2,1 > layout scoped 0,2,0，无需 `!important`）。
- `.table-wrap { flex: 1; min-height: 0; overflow: hidden }` + `:deep(.pure-table)/:deep(.el-table)` 高度填充（复用 MemberTab 范式）。

## 3. 字段定义

### 3.1 类型定义响应（TypeDefResp，对齐后端 TypeDefinitionResp）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 内部主键 |
| tenantId | number? | 租户 ID |
| typeKey | string | 类型分组键（user_type/role_type/resource_type/group_type） |
| typeCode | string | 对外稳定编码（租户+typeKey 内唯一，`uk_type_definition_code`） |
| typeValue | number | 内部存储/计算值（**服务端自动分配，前端只读**） |
| name | string | 显示名称 |
| description | string \| null | 描述（可空） |
| isSystem | boolean | 系统预置不可删改（true）/ 租户自定义（false） |
| sortOrder | number | 排序 |
| extra | string \| null | 扩展属性 JSON（可空，如 `{"max_depth":5}`） |
| createdAt | string? | 创建时间 |

### 3.2 类型定义表单（TypeDefFormData）

| 字段 | 校验 | 说明 |
|---|---|---|
| typeKey | 必填 | 新建下拉选 4 分组；编辑只读（稳定分组键，不可改） |
| typeCode | 可空，字母数字下划线中划线，最长 64 | 新建可填（留空=服务端按规则自动生成）；编辑只读（对外稳定编码，改它破坏既有引用，与 typeKey 同口径）；update 请求不含 typeCode 字段 |
| name | 必填，2-64 字符 | 显示名称；系统预置项编辑态只读（不可改名），仅 description/sortOrder/extra 可改 |
| description | 可空 | 描述 |
| isSystem | — | 仅编辑态只读展示；**新建不暴露开关**——前端创建固定 isSystem=false（租户自定义），系统预置走初始化种子（schema:47 语义，见 §8 备注） |
| sortOrder | 必填，0-9999 | 排序号 |
| extra | 可空 | 扩展属性 JSON |

> **无 typeValue 字段**（已确认决策）：服务端自动分配（D1），表单不收集，列表只读展示。

## 4. 交互流程

### 4.1 列表加载与过滤

- **加载**：进入页面 `getTypeDefList({})` → 后端返回 `ItemsResp`（全量，无分页/无过滤，见 §8 🔧 第 2 条）→ hook `loadTable` 本地做 typeKey/keyword 过滤 + sortOrder 排序 + 切片分页。
- **typeKey 下拉过滤**：顶部下拉选 typeKey（或「全部」）→ `onSearch` 重置页码 + `loadTable`，hook 本地过滤。
- **keyword 搜索**：hook 本地按 name / typeCode 模糊匹配。
- **分页**：`onPageChange` / `onPageSizeChange`，`pagination.total` = 本地过滤后长度，`tableData` = 切片后的当前页。
- **字典表量小**：每次翻页重拉全量可接受；Phase 2 后端补 typeKey/keyword/pageNum/pageSize 参数 + 返回 PaginatedResp 后（T-PERM-023）可切回服务端分页。

### 4.2 新增

- 顶部「新增」按钮（门禁 `TYPE_DEFINITION:CREATE`）→ 表单弹窗。
- 表单：typeKey 下拉（默认取搜索下拉当前值）、typeCode 可填或留空、name、description、sortOrder、extra。**无 typeValue**（服务端自动分配）、**无 isSystem 开关**（前端创建固定 isSystem=false=租户自定义，系统预置走初始化种子，见 §8 备注）。
- 提交 → `createTypeDef`（不含 typeValue/isSystem）→ mock 校验 typeKey+typeCode 唯一（排除已软删行，违反返回 409）、自动分配 typeValue（含已软删行的 max+1，软删不复用 D1）+ typeCode（若留空按 `${typeKey}_${name}` 规则生成）→ 成功 `loadTable`。

### 4.3 编辑

- 操作列「编辑」按钮（`v-if="canEdit && !isSystemPreset(row)"`，门禁 `TYPE_DEFINITION:MANAGE`）→ 表单弹窗。
- 编辑态：typeKey/typeCode 只读（稳定），name（系统预置项只读，自定义项可改）、description/sortOrder/extra 可改。
- 提交 → `updateTypeDef`（仅可改字段，不含 typeKey/typeCode/typeValue）→ mock 对系统预置项拒绝改名（返回 403）→ 成功 `loadTable`。

### 4.4 删除

- 操作列「删除」按钮（`v-if="canDelete && !isSystemPreset(row)"`，门禁 `TYPE_DEFINITION:MANAGE`）→ `ElMessageBox.confirm` 二次确认 → `removeTypeDefs([id])` → mock 软删（置 deleted=true，对齐 schema delete_flag），isSystem=true 跳过（返回提示）→ 成功 `loadTable`。
- 系统预置项两按钮均隐藏（业务约束，非权限）。
- **软删不复用 typeValue**：已删行保留在数据中，nextTypeValue 仍计入其 typeValue，新建不会复用已删除的最高值（D1）。

## 5. API 依赖（链接后端契约章节）

| 操作 | 接口 | 请求 | 响应 | 核对 |
|---|---|---|---|---|
| 列表 | `POST /api/perm/type-definition/list` | `{domainCode?}` | `ItemsResp<TypeDefResp>`（全量，无分页/无 typeKey 过滤） | 🔧 见 §8 |
| 详情 | `POST /api/perm/type-definition/detail` | `{id}` (IdReq) | `TypeDefResp` | ✅ |
| 创建 | `POST /api/perm/type-definition/create` | `{typeKey,typeCode?,name,description?,sortOrder?,extra?}` | `TypeDefResp` | 🔧 见 §8 |
| 更新 | `POST /api/perm/type-definition/update` | `{typeId,name?,description?,sortOrder?,extra?}` | `TypeDefResp` | ✅ |
| 删除 | `POST /api/perm/type-definition/remove` | `{ids:[]}` | `Void` | ✅ |

## 6. 组件结构

```
views/system/type-def/
├── index.vue                  # 主页面（PureTableBar 表格 + 权限门控）
├── components/
│   └── TypeForm.vue           # 类型表单弹窗（新建/编辑）
└── utils/
    ├── hook.ts                # useTypeDef（分页表格加载 + CRUD）
    ├── perms.ts               # TYPE_DEF_PERMS（SSOT）
    └── types.ts               # TypeDefFormData + TYPE_KEY 常量 + isSystemPreset
```

### Step 1.5 组件识别（登记 T-FE-001 组件池）

| 候选 | 本页使用场景 | 跨页复用 | 确认状态 |
|---|---|---|---|
| 分页表格 hook（tableData/pagination/loadTable/CRUD） | 本页表格 | 6.2 系统配置 / 7.1 操作日志 / 7.2 变更日志（同范式表格列表） | ⏳ 待确认（T-FE-004/005/012 推进时，模式一致则派生 ReTableHook） |
| typeKey 下拉选择器 | 本页过滤 | 5.1 业务域 CLASSIFY 类型归属 / 资源类型筛选 | ⏳ 待确认（T-FE-006 推进时） |

> 当前不提前抽取，待 2+ 页确认模式一致后由 T-FE-001 派生子任务。

## 7. 权限接线（hasPerms → 按钮 → 降级）

`TYPE_DEF_PERMS`（`views/system/type-def/utils/perms.ts`，SSOT）：

| perm 串 | 操作码 | 锚点 | 控制按钮 |
|---|---|---|---|
| `TYPE_DEFINITION:VIEW` | VIEW | TYPE_DEFINITION | 路由可达 + 列表可见 |
| `TYPE_DEFINITION:CREATE` | CREATE | TYPE_DEFINITION | 新增 |
| `TYPE_DEFINITION:MANAGE` | MANAGE | TYPE_DEFINITION | 编辑 / 删除（update/remove 统一口径） |

> **B1 口径**：后端 `TypeDefinitionAppServiceImpl` 的 updateType(:161)/deleteTypesByIds(:209) 均以 `TYPE_DEFINITION:MANAGE` 做门禁，无独立 UPDATE/DELETE 操作码。前端 EDIT/DELETE 统一映射到 `TYPE_DEFINITION:MANAGE`（与角色管理页 ROLE:MANAGE 同口径）。`TYPE_DEF_PERM_LIST` 用 `Set` 去重（EDIT/DELETE 同值），确保路由 `meta.auths` 无冗余。

### 降级策略

- 无 `TYPE_DEFINITION:VIEW` → 路由不可达（`meta.auths` 派生自 `TYPE_DEF_PERM_LIST`）。
- 无 `TYPE_DEFINITION:CREATE` → 隐藏「新增」按钮。
- 无 `TYPE_DEFINITION:MANAGE` → 隐藏编辑/删除按钮。
- `isSystem=true` 系统预置项 → 无论权限如何，均不展示编辑/删除按钮（业务约束，非权限）。

### mock 角色矩阵（`mock/login.ts`）

| 账号 | 类型定义权限 |
|---|---|
| admin | 全权（VIEW/CREATE/MANAGE） |
| sec（安全管理员） | 全权（TYPE_ADD/TYPE_EDIT/TYPE_DELETE） |
| hr（组织人事管理员） | 只读（VIEW） |
| auditor（审计员） | 只读（VIEW） |

## 8. API 核对清单（登记 T-PERM-023）

Phase 1 不改后端，🔧❌ 项登记为 Phase 2 后端任务 T-PERM-023。

### 🔧 需改造

1. **`typeValue` 自动分配未实现（T-PERM-019 D1 漂移）**
   - 现状：`TypeCreateReq.typeValue` 仍 `@NotNull`，`createType` 直接用入参，无 allocator。
   - 期望：服务端在 `tenant+typeKey` 内自动分配 `typeValue`（`max+1`，**软删不复用**——已删行的 typeValue 仍占位），`TypeCreateReq` 移除 `typeValue` 字段。
   - 依据：schema 唯一索引 `uk_type_definition_value (tenant_id,type_key,type_value) WHERE delete_flag=0`（permission-center.sql）保证未删行唯一；`type_value` 是内部计算值，不应外部入参；D1 明确软删不复用。
   - 前端可行性：✅ 本页表单已不收集 typeValue，mock `nextTypeValue` 取全部行（含已软删）max+1。后端切换后前端无需改动。
   - 归属：T-PERM-023 🔧（收敛 T-PERM-019 D1）。

2. **`list` 返回全量 ItemsResp，无分页/无 typeKey/keyword 过滤**
   - 现状：`TypeListReq` 仅 `domainCode`（且未生效），`/list` 返回 `ItemsResp<TypeDefinitionResp>`（全量 `items`，无 total/pageNum/pageSize）。
   - 期望：`TypeListReq` 增加 `typeKey`/`keyword`/`pageNum`/`pageSize` 参数，`/list` 返回 `PaginatedResp<TypeDefinitionResp>`（服务端分页+过滤）。
   - 前端可行性：✅ Phase 1 前端已适配 `ItemsResp` 全量 + 本地过滤分页（hook `loadTable`）；字典表量小可接受。后端补参数+分页结构后，前端 hook 切回服务端分页即可。
   - 归属：T-PERM-023 🔧。

3. **`create` 不接收 `typeCode`**
   - 现状：`TypeCreateReq` 无 `typeCode` 字段，`createType` 未 `setTypeCode`（潜在 bug）。
   - 期望：`TypeCreateReq` 增加 `typeCode`（可空，留空则服务端按规则生成），`createType` 写入。
   - 依据：`typeCode` 是对外稳定编码，应由调用方提供以便外部系统引用，schema `uk_type_definition_code` 保证唯一。
   - 前端可行性：✅ 本页表单 typeCode 可填或留空，mock 按规则生成。后端补字段或服务端生成。
   - 归属：T-PERM-023 🔧。

4. **`resource_type` 创建联动预置 CRUD `operation_permission` 未实现**
   - 现状：schema:38 注释承诺创建 resource_type 时联动预置 operation_permission（如 MENU/BUTTON/API/DATA 的默认操作集），代码无实现。
   - 期望：创建 resource_type 类型定义时，按预置模板联动创建对应 operation_permission 行。
   - 归属：T-PERM-023 🔧/❌（需结合 T-PERM-028 资源+操作定义后端确认范围）。

5. **`create` 的 `isSystem` 字段为 DESIGN_DRIFT（前端不应可创建系统预置项）**
   - 现状：`TypeCreateReq` 含 `isSystem` 字段，允许调用方创建 `isSystem=true` 的类型。
   - 期望：`TypeCreateReq` 移除 `isSystem` 字段（或服务端强制 false）。系统预置项只走租户初始化种子流程，不可由 API 创建——schema:47 语义 `is_system=true=预置不可删改`，若可由前端创建会立即产生不可编辑/不可删除的"锁死"行。
   - 前端可行性：✅ 本页新建表单已不暴露 isSystem 开关、`TypeDefCreateReq` 已不含该字段、hook create 不透传。后端移除字段后前端无需改动。
   - 归属：T-PERM-023 🔧（收敛 T-PERM-019 D 系列）。

### ✅ 满足

- detail/update/remove 满足前端需求，请求/响应结构与 mock 对齐。
  - **list/create 不在此列**：list 返回全量 ItemsResp 无分页/过滤；create 缺 typeCode 入参 / typeValue 自动分配 / isSystem 应移除，见 §8 🔧 第 2-5 条（T-PERM-023）。

### 备注

- **isSystem 业务约束非权限**：系统预置项（`isSystem=true`）不可删改是 schema 层业务约束，前端隐藏编辑/删除按钮，后端 `update` 拒改名 / `remove` 跳过删除。无需独立操作码。
- **isSystem 不可由前端创建**：系统预置项只走租户初始化种子，新建表单不暴露开关、`TypeDefCreateReq` 不含该字段（见 §8 第 5 条 🔧）。后端 `TypeCreateReq.isSystem` 为 DESIGN_DRIFT，Phase 2 收敛。
- **稳定编码设计**：typeKey/typeCode/typeValue 在 update 中均不可改（`TypeDefUpdateReq` 不含这些字段）——对外稳定编码改动会破坏既有引用，与角色管理页 externalId 只读同口径。

## 9. 已知限制（Phase 1）

- list 为前端本地过滤+分页（后端返回全量 ItemsResp）；Phase 2 后端补 typeKey/keyword/pageNum/pageSize + 返回 PaginatedResp 后切换服务端分页。
- typeValue 自动分配为 mock 层 `max+1`（含已软删行，软删不复用）；Phase 2 后端实现 D1 allocator 后联调。
- mock 删除为软删（置 deleted=true，对齐 schema delete_flag），已删行不出现在列表/detail，但 typeValue 仍占位。
- 联调（T-FE-022）需后端先补 §8 五项 🔧。
