---
doc_type: design
title: 7.1 操作日志页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-07-01
---

# 7.1 操作日志页 前端设计

> 任务：T-FE-005（Phase 1，mock 驱动，第 1 批末页）
> 后端契约：`docs/design/permission-center/api-contract.md` §5.8（操作日志仅 1 行表格条目，路径写错且无独立字段契约章节——🔧 登记 T-PERM-025）
> 参照范式：6.1 类型定义页 / 6.2 系统配置页（`type-definition.md` / `system-config.md`，PureTableBar 表格列表范式 + SSOT/降级/核对清单结构）

## 1. 页面定位

操作日志页查询 `operation_log` 表的**轻量全量操作日志**（记录所有写操作，与 `permission_change_log` 区分：后者只记权限变更详情）。

- **只读查询页**：无 CRUD 写操作，仅 list 查询 + 详情查看。无新增/编辑/删除按钮。
- **服务端分页**：后端 `OperationLogListReq` 支持 `pageNum/pageSize`（`@NotNull`），返回 `PaginatedResp`。**非** system-config 的全量本地过滤——前端不做本地切片。
- **筛选仅 module/action**：后端 Req 只暴露这两个筛选维度。任务标题「筛选」按此两项实现，其余维度（操作者/时间范围/目标）登记 T-PERM-025 🔧。
- **无 detail 接口**：后端只有 list，`OperationLogResp` 已含全部字段。详情由前端抽屉展示（无需单独 detail 接口）。
- **复用 SYSTEM_CONFIG:VIEW 门禁**：后端 `LogQueryAppServiceImpl.listOperationLogs` 以 `SYSTEM_CONFIG:VIEW` 校验（**无独立 OPERATION_LOG 资源类型/权限码**）。本页 perms SSOT 独立，但 VIEW 值复用 `SYSTEM_CONFIG:VIEW`。

## 2. 布局结构

PureTableBar 表格列表范式（遵循 `frontend-layout-patterns`），与 6.1/6.2 同源：

```
┌─ operation-log-page（main-content）─────────────────────────┐
│ PureTableBar                                                 │
│  ├─ #title：module 下拉 + action 下拉 + 搜索/重置            │
│  └─ #buttons：（空——只读页无新增按钮）                       │
│ pure-table（adaptive 分页表格）                               │
│  列：时间 / 模块(tag) / 操作(tag) / 操作人 / 目标类型         │
│      / 摘要(tooltip 截断) / 操作(查看)                        │
│ 分页                                                         │
│ LogDetailDrawer（右侧抽屉，展示全字段）                       │
└──────────────────────────────────────────────────────────────┘
```

- 表格滚动交给 `pure-table` 的 `adaptive` prop，不手动设 overflow。
- 覆写 layout `.main-content` margin：`div.operation-log-page.main-content { margin: var(--space-3) }`（特异性 0,2,1 > layout scoped 0,2,0，无需 `!important`）。
- `.table-wrap { flex: 1; min-height: 0; overflow: hidden }` + `:deep(.pure-table)/:deep(.el-table)` 高度填充（复用 type-def 范式）。
- 摘要列：长文本用 `el-tooltip` 完整查看 + 单元格内 `text-overflow: ellipsis` 截断，避免撑爆表格。
- 详情抽屉：`el-drawer`（右侧 rtl，size 480px）+ `el-descriptions` 网格展示全字段，summary 跨列多行。

## 3. 字段定义

### 3.1 操作日志响应（OperationLogResp，对齐后端 OperationLogResp）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 日志 ID |
| tenantId | number? | 租户 ID（从 X-Tenant-Id Header 取） |
| module | string | 所属模块（type_definition / abstract_role / abstract_user / system_config 等） |
| action | string | 操作类型（CREATE / UPDATE / DELETE / SYNC / ASSIGN / BATCH_GRANT 等） |
| targetType | string \| null | 操作目标类型（可空） |
| targetId | number \| null | 操作目标 ID（可空） |
| summary | string \| null | 操作摘要（可空，长文本） |
| operatorId | number \| null | 操作人 ID（可空） |
| operatorName | string \| null | 操作人名称（可空） |
| ipAddress | string \| null | 操作人 IP（可空） |
| requestId | string \| null | 请求/追踪 ID（可空，关联追踪） |
| createdAt | string? | 创建时间（后端 LocalDateTime） |

### 3.2 筛选表单（OperationLogSearchForm）

| 字段 | 校验 | 说明 |
|---|---|---|
| module | 可空（null=全部） | 模块下拉筛选，对齐后端 Req.module? |
| action | 可空（null=全部） | 操作类型下拉筛选，对齐后端 Req.action? |

> **仅两筛选维度**：后端 `OperationLogListReq` 只支持 module/action。其余维度（operatorId/createdAt 时间范围/targetType）schema 有字段但 Req 未暴露，登记 T-PERM-025 🔧。前端本地硬编码 `MODULE_OPTIONS`/`ACTION_OPTIONS` 下拉选项（后端无枚举接口），值与 mock 数据对齐。

## 4. 交互流程

### 4.1 列表加载与筛选

- **加载**：进入页面 `getOperationLogList({ module, action, pageNum, pageSize })` → 后端返回 `PaginatedResp`（服务端分页 + module/action 过滤）→ `tableData = res.items` / `pagination.total = res.total`。
- **module/action 筛选**：下拉 `@change` 触发 `onSearch`（重置 page=1 后 loadTable）。后端按 module/action 精确过滤。
- **分页**：`onPageChange` / `onPageSizeChange`，服务端分页（非本地切片）。`pagination.total` = 后端返回 total。

### 4.2 详情查看

- 操作列「查看」按钮（`v-if="canView"`，门禁 `SYSTEM_CONFIG:VIEW`）→ 打开 `LogDetailDrawer`。
- 抽屉用 `el-descriptions` 展示该条日志全部字段（module/action el-tag / targetType / targetId / summary 多行 / operatorId / operatorName / ipAddress / requestId / createdAt）。
- **无 API 调用**：纯展示 list 已返回的字段（后端无 detail 接口）。

### 4.3 无写操作

- **只读页**：无新增/编辑/删除按钮，无表单弹窗。operation_log 由后端各 AppService 的 `@OperationLog` AOP 自动写入，前端不可手动增删改。

## 5. API 依赖（链接后端契约章节）

| 操作 | 接口 | 请求 | 响应 | 核对 |
|---|---|---|---|---|
| 列表 | `POST /api/perm/log/operation/list` | `{module?,action?,pageNum,pageSize}` (OperationLogListReq) | `PaginatedResp<OperationLogResp>`（服务端分页） | 🔧 见 §8 |

> **路径说明**：后端 `LogQueryController`（`@RequestMapping("/api/perm/log")`）实际路径 = `/api/perm/log/operation/list`，**非** api-contract §5.8 表格写的 `/api/perm/operation-log/list`（契约与实现不符，登记 T-PERM-025 🔧）。前端按后端实现对接，联调时直接对真后端无需改路径。
>
> **无 detail 接口**：后端只有 list，详情由前端抽屉展示 list 已返回字段。

## 6. 组件结构

```
views/system/operation-log/
├── index.vue                  # 主页面（PureTableBar 表格 + 权限门控 + 抽屉编排）
├── components/
│   └── LogDetailDrawer.vue    # 日志详情抽屉（el-drawer + el-descriptions 全字段展示）
└── utils/
    ├── hook.ts                # useOperationLog（服务端分页表格加载，无写操作）
    ├── perms.ts               # OPERATION_LOG_PERMS（SSOT，VIEW 复用 SYSTEM_CONFIG:VIEW）
    └── types.ts               # OperationLogSearchForm + MODULE/ACTION_OPTIONS + 工厂
```

### Step 1.5 组件识别（登记 T-FE-001 组件池）

| 候选 | 本页使用场景 | 跨页复用 | 确认状态 |
|---|---|---|---|
| 分页表格 hook（tableData/pagination/loadTable） | 本页表格（服务端分页变体） | 6.1 类型定义 / 6.2 系统配置 / 7.2 变更日志（同范式表格列表） | ⏳ 待确认（T-FE-012 推进时，模式一致则派生 ReTableHook，含本地过滤/服务端分页两种变体） |
| 详情抽屉（el-drawer + el-descriptions 全字段） | 本页日志详情 | 7.2 变更日志（diff 快照详情）/ 4.2 权限解释（拒绝原因详情） | ⏳ 待确认（T-FE-012/013 推进时） |

> 当前不提前抽取，待 2+ 页确认模式一致后由 T-FE-001 派生子任务。

## 7. 权限接线（hasPerms → 按钮 → 降级）

`OPERATION_LOG_PERMS`（`views/system/operation-log/utils/perms.ts`，SSOT）：

| perm 串 | 操作码 | 锚点 | 控制按钮 |
|---|---|---|---|
| `SYSTEM_CONFIG:VIEW` | VIEW | SYSTEM_CONFIG（复用） | 「查看」按钮（详情抽屉）+ 列表数据可见（后端 listOperationLogs 校验 VIEW） |

> **复用说明**：后端 `LogQueryAppServiceImpl.listOperationLogs`（及 listChangeLogs/recentChanges）以 `SYSTEM_CONFIG:VIEW` 门禁，**无独立 OPERATION_LOG 资源类型/权限码**。本页 SSOT 独立文件，但 VIEW 值复用 `SYSTEM_CONFIG:VIEW`——与后端一致。本页为只读查询页，只有 VIEW 一项，无 SAVE/MANAGE。`OPERATION_LOG_PERM_LIST` 用 `Set` 去重确保路由 `meta.auths` 无冗余。

### 降级策略

> **路由可达性与按钮门禁现状（项目共性，非本页独有）**：
> - **路由可达性**：pure-admin-thin 的 `filterNoPermissionTree`（`router/utils.ts:85`）路由过滤**只基于 `meta.roles`，不使用 `meta.auths`**。本页及 user/role/type-def/system-config 等所有页 meta 均只有 `auths` 无 `roles`，故菜单对所有登录用户可见，**页面级拦截靠后端 403 兜底**。
> - **按钮门禁**：本页按钮 `canView = hasPerms(OPERATION_LOG_PERMS.LOG_VIEW)`（`index.vue`），`hasPerms`（`utils/auth.ts:131`）读取**登录态 `permissions`**（`/auth/user-menu` 下发的 perm 串数组），**不是 `meta.auths`**。
> - **`meta.auths` 的真实用途**：仅作为路由元信息清单（派生自 `OPERATION_LOG_PERM_LIST`），供 `hasAuth`（`router/utils.ts:366`，从当前路由 meta.auths 读）使用；本页按钮未用 `hasAuth`。即 `meta.auths` 是路由级元信息/`hasAuth` 清单，不参与本页按钮显隐。
> 这与 role-manage.md / type-definition.md / system-config.md 同口径（既有文档同样把按钮门禁写成 auths 控制，属共性表述偏差）。

- 无 `SYSTEM_CONFIG:VIEW` → **菜单仍可见、路由可达**（路由过滤基于 roles，本项目未设 roles）；进入页面后 `loadTable` 调 `/api/perm/log/operation/list` 由后端 VIEW 校验拒绝（403），前端 `message` 报错。本页 VIEW 级按钮（「查看」）隐藏（`v-if="canView"`，`hasPerms` 读登录态 permissions 判定），操作列显示「—」。
- 有 `SYSTEM_CONFIG:VIEW` → 「查看」按钮可见，可打开详情抽屉。

> 🔧 `SYSTEM_CONFIG:VIEW` 复用作为日志查询门禁的审计语义问题登记 T-PERM-025：当前复用致「有系统配置 VIEW 权限即可查全部操作日志」，审计场景可能需独立 `OPERATION_LOG:VIEW`。确认型，非必改——若后端独立，前端仅需改 `OPERATION_LOG_PERMS.LOG_VIEW` 常量值 + 矩阵补串。
>
> 🔧 路由级 auths 拦截缺失属项目共性问题（type-def/role/user/system-config 同），若需「无 VIEW 真正路由不可达」需改 `filterNoPermissionTree` 按 `meta.auths` 过滤——影响所有页，超出 T-FE-005 范围，登记待统一立项处理。

### mock 角色矩阵（`mock/login.ts`）

| 账号 | 操作日志权限 | 说明 |
|---|---|---|
| admin | 可查看（VIEW） | 复用 SYSTEM_CONFIG:VIEW（admin 全权） |
| sec（安全管理员） | 可查看（VIEW） | 复用 SYSTEM_CONFIG:VIEW（sec 有 CONFIG_SAVE 含 VIEW） |
| hr（组织人事管理员） | 可查看（VIEW） | 复用 SYSTEM_CONFIG:VIEW（hr 有 SYSTEM_CONFIG_VIEW_PERMS） |
| auditor（审计员） | 可查看（VIEW） | 复用 SYSTEM_CONFIG:VIEW（auditor 有 SYSTEM_CONFIG_VIEW_PERMS）——审计员必须能查日志 |

> **矩阵不新增权限串**：本页复用 `SYSTEM_CONFIG:VIEW`，已在前页（system-config）矩阵中分配所有账号。`mock/login.ts` 仅加注释说明，不新增 import/矩阵条目。

## 8. API 核对清单（登记 T-PERM-025）

Phase 1 不改后端，🔧❌ 项登记为 Phase 2 后端任务 T-PERM-025。

### 🔧 需改造

1. **api-contract §5.8 路径错误 + 缺 operation-log 专属字段契约**
   - 现状：§5.8 表格写 `POST /api/perm/operation-log/list`，但后端 `LogQueryController` 实际路径 = `POST /api/perm/log/operation/list`（`@RequestMapping("/api/perm/log")` + `@PostMapping("/operation/list")`）——**契约与实现不一致**。且 operation-log 仅 1 行表格条目，**无独立字段契约章节**（无字段表/请求示例），字段由后端 DTO（`OperationLogListReq`/`OperationLogResp`）落地。
   - 期望：§5.8 修正路径为 `/api/perm/log/operation/list`（或后端改路径对齐契约——留给后端定），并补 operation-log 请求/响应字段契约，与其他资源契约章节同口径。
   - 前端可行性：✅ 本页已按后端实际路径 + DTO 字段实现，契约补全/路径修正后前端无需改动（路径与字段已对齐实现）。
   - 归属：T-PERM-025 🔧。

2. **筛选维度不足**
   - 现状：`OperationLogListReq` 只支持 `module`/`action` 两个筛选维度。schema `operation_log` 有 `operator_id`/`created_at`/`target_type`/`target_id` 等可用筛选字段（且有对应索引 `idx_operation_log_operator`/`idx_operation_log_target`），但 Req 未暴露。任务标题「筛选」期望更多维度（操作者/时间范围/目标）。
   - 期望：Req 补 `operatorId`/`createdAt` 时间范围（since/until）/`targetType` 等筛选维度。
   - 前端可行性：✅ Phase 1 mock 仅 module/action 过滤（对齐后端 Req）。后端补维度后前端筛选表单扩展。
   - 归属：T-PERM-025 🔧。

3. **`SYSTEM_CONFIG:VIEW` 复用审计语义确认**
   - 现状：`LogQueryAppServiceImpl.listOperationLogs`（及 listChangeLogs/recentChanges）以 `SYSTEM_CONFIG:VIEW` 门禁，**无独立 OPERATION_LOG 资源类型/权限码**。当前复用致「有系统配置 VIEW 权限即可查全部操作日志」。
   - 期望：确认审计场景是否需独立 `OPERATION_LOG:VIEW` 权限码（审计员查日志与系统配置查看是否应分离权限）。
   - 前端可行性：✅ Phase 1 mock 复用 SYSTEM_CONFIG:VIEW（所有账号均可查，审计员必须能查）。后端独立后前端仅需改 `OPERATION_LOG_PERMS.LOG_VIEW` 常量值 + 矩阵补串。
   - 归属：T-PERM-025 🔧（确认型，非必改）。

### ✅ 满足

- list 满足前端需求，服务端分页 + module/action 过滤，请求/响应结构与 mock 对齐。`OperationLogResp` 含全部字段，详情抽屉无需 detail 接口。

### 备注

- **只读查询页**：operation_log 由后端各 AppService 的 `@OperationLog` AOP 自动写入（见 permission-center-coding-standards §7），前端不可手动增删改。本页无写操作。
- **轻量全量日志**：operation_log 记录所有写操作（简单摘要），与 permission_change_log（权限变更详情 + diff 快照）区分。变更日志页见 7.2（T-FE-012）。
- **无 detail 接口**：后端只有 list，`OperationLogResp` 已含全部字段。详情由前端抽屉展示——避免为详情单独开接口（list 数据已是全字段）。

## 9. 已知限制（Phase 1）

- 筛选仅 module/action 两维度（对齐后端 Req）；Phase 2 后端补 operatorId/createdAt 时间范围/targetType 后扩展（T-PERM-025）。
- module/action 下拉选项前端本地硬编码（后端无枚举接口）；联调时若后端补枚举接口可切动态加载。
- 详情抽屉纯展示 list 返回字段（无 detail 接口）；若后续需关联 permission_change_log 详情，7.2 变更日志页处理。
- 联调（T-FE-019）需后端先确认 §8 三项 🔧（尤其路径——契约写错路径，联调时按后端实际 `/api/perm/log/operation/list`）。
