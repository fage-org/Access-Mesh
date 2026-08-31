---
doc_type: design
title: 7.1 操作日志页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-08-31   # 2026-08-31 T-PERM-037 收口：路由级 auths 登记收口（menus 接线归 Phase 3 T-FE-015）；2026-08-28 T-PERM-025 收口：§5/§7/§8/§9 终态化（OPERATION_LOG:VIEW 审计分离、action 动态字典、五维筛选）
---

# 7.1 操作日志页 前端设计

> 任务：T-FE-005（Phase 1，mock 驱动，第 1 批末页）
> 后端契约：`docs/design/permission-center/api-contract.md` §5.8（T-PERM-025 收口：两行条目 + operation-log 契约要点，路径为实现路径）
> 参照范式：6.1 类型定义页 / 6.2 系统配置页（`type-definition.md` / `system-config.md`，PureTableBar 表格列表范式 + SSOT/降级/核对清单结构）

## 1. 页面定位

操作日志页查询 `operation_log` 表的**轻量全量操作日志**（记录所有写操作，与 `permission_change_log` 区分：后者只记权限变更详情）。

- **只读查询页**：无 CRUD 写操作，仅 list 查询 + 详情查看。无新增/编辑/删除按钮。
- **服务端分页**：后端 `OperationLogListReq` 支持 `pageNum/pageSize`（`@NotNull`），返回 `PaginatedResp`。**非** system-config 的全量本地过滤——前端不做本地切片。
- **筛选五维（T-PERM-025 收口）**：module/action/操作者 ID/时间范围/目标类型，全部服务端精确匹配（原仅 module/action 两维，2026-08-28 扩展）。
- **无 detail 接口**：后端有 list 与 action-options 两端点（均无 detail），`OperationLogResp` 已含全部字段。详情由前端抽屉展示（无需单独 detail 接口）。
- **独立 OPERATION_LOG:VIEW 门禁（T-PERM-025 审计分离，2026-08-28）**：后端操作日志查询（list/action-options）以独立 `OPERATION_LOG:VIEW` 校验（资源类型 OPERATION_LOG=30），不再复用 `SYSTEM_CONFIG:VIEW`。

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

- **加载**：进入页面 `getOperationLogList({ module, action, operatorId, since, until, targetType, pageNum, pageSize })` → 后端返回 `PaginatedResp`（服务端五维过滤分页）→ `tableData = res.items` / `pagination.total = res.total`；同时 `loadActionOptions` 拉取动态 action 字典（失败降级为空下拉，不阻塞列表）。
- **筛选**：module/action 下拉与 targetType/operatorId 输入 `@change`/回车触发 `onSearch`（重置 page=1 后 loadTable）；时间范围 datetimerange 序列化为 since/until（UTC 墙钟，与 createdAt 展示同参照系）。后端全部精确匹配。
- **分页**：`onPageChange` / `onPageSizeChange`，服务端分页（非本地切片）。`pagination.total` = 后端返回 total。

### 4.2 详情查看

- 操作列「查看」按钮（`v-if="canView"`，门禁 `OPERATION_LOG:VIEW`）→ 打开 `LogDetailDrawer`。
- 抽屉用 `el-descriptions` 展示该条日志全部字段（module/action el-tag / targetType / targetId / summary 多行 / operatorId / operatorName / ipAddress / requestId / createdAt）。
- **无 API 调用**：纯展示 list 已返回的字段（后端无 detail 接口）。

### 4.3 无写操作

- **只读页**：无新增/编辑/删除按钮，无表单弹窗。operation_log 由后端各 AppService 的 `@OperationLog` AOP 自动写入，前端不可手动增删改。

## 5. API 依赖（链接后端契约章节）

| 操作 | 接口 | 请求 | 响应 | 核对 |
|---|---|---|---|---|
| 列表 | `POST /api/perm/log/operation/list` | `{module?,action?,operatorId?,since?,until?,targetType?,pageNum,pageSize}` | `PaginatedResp<OperationLogResp>`（服务端分页，排序 createdAt DESC） | ✅（T-PERM-025 收口） |
| 字典 | `POST /api/perm/log/operation/action-options` | `{module?}` | `ItemsResp<String>`（action 去重集合，字典序） | ✅（T-PERM-025 新增） |

> **路径说明**：api-contract §5.8 路径即实现路径 `/api/perm/log/operation/list`（历史误写 `/api/perm/operation-log/list` 已随 T-ACCESS-007 评审修复（提交 371d9d009）修正，T-PERM-025 核实无残留）。
>
> **无 detail 接口**：后端只有 list 与 action-options，详情由前端抽屉展示 list 已返回字段。

## 6. 组件结构

```
views/system/operation-log/
├── index.vue                  # 主页面（PureTableBar 表格 + 权限门控 + 抽屉编排）
├── components/
│   └── LogDetailDrawer.vue    # 日志详情抽屉（el-drawer + el-descriptions 全字段展示）
└── utils/
    ├── hook.ts                # useOperationLog（服务端分页 + action 动态字典加载，无写操作）
    ├── perms.ts               # OPERATION_LOG_PERMS（SSOT，独立 OPERATION_LOG:VIEW）
    └── types.ts               # OperationLogSearchForm（五维）+ MODULE_OPTIONS + 工厂
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
| `OPERATION_LOG:VIEW` | VIEW | OPERATION_LOG（独立，T-PERM-025） | 「查看」按钮（详情抽屉）+ 列表数据可见（后端 listOperationLogs/action-options 校验 VIEW） |

> **门禁说明**：后端操作日志查询以独立 `OPERATION_LOG:VIEW` 门禁（T-PERM-025 审计分离；变更日志已随 T-PERM-032 切独立 `PERMISSION_CHANGE_LOG:VIEW`；权限排查视图已随 T-PERM-033 切被查目标实例 `USER:VIEW`/`ROLE:VIEW`，无独立排查码）。本页为只读查询页，只有 VIEW 一项，无 SAVE/MANAGE。`OPERATION_LOG_PERM_LIST` 用 `Set` 去重确保路由 `meta.auths` 无冗余。

### 降级策略

> **路由可达性与按钮门禁现状（项目共性，非本页独有）**：
> - **路由可达性**：pure-admin-thin 的 `filterNoPermissionTree`（`router/utils.ts:85`）路由过滤**只基于 `meta.roles`，不使用 `meta.auths`**。本页及 user/role/type-def/system-config 等所有页 meta 均只有 `auths` 无 `roles`，故菜单对所有登录用户可见，**页面级拦截靠后端 403 兜底**。
> - **按钮门禁**：本页按钮 `canView = hasPerms(OPERATION_LOG_PERMS.LOG_VIEW)`（`index.vue`），`hasPerms`（`utils/auth.ts:131`）读取**登录态 `permissions`**（`/auth/user-menu` 下发的 perm 串数组），**不是 `meta.auths`**。
> - **`meta.auths` 的真实用途**：仅作为路由元信息清单（派生自 `OPERATION_LOG_PERM_LIST`），供 `hasAuth`（`router/utils.ts:366`，从当前路由 meta.auths 读）使用；本页按钮未用 `hasAuth`。即 `meta.auths` 是路由级元信息/`hasAuth` 清单，不参与本页按钮显隐。
> 这与 role-manage.md / type-definition.md / system-config.md 同口径（既有文档同样把按钮门禁写成 auths 控制，属共性表述偏差）。

- 无 `OPERATION_LOG:VIEW` → **菜单仍可见、路由可达**（路由过滤基于 roles，本项目未设 roles）；进入页面后 `loadTable` 调 `/api/perm/log/operation/list` 由后端 VIEW 校验拒绝（403），前端 `message` 报错。本页 VIEW 级按钮（「查看」）隐藏（`v-if="canView"`，`hasPerms` 读登录态 permissions 判定），操作列显示「—」。
- 有 `OPERATION_LOG:VIEW` → 「查看」按钮可见，可打开详情抽屉。

> ~~🔧 `SYSTEM_CONFIG:VIEW` 复用审计语义问题~~ 已随 T-PERM-025 审计分离收口（2026-08-28 设计定案）：独立 `OPERATION_LOG:VIEW`，前端常量已切换。
>
> ~~🔧 路由级 auths 拦截缺失属项目共性问题（type-def/role/user/system-config 同）~~ **已收口（2026-08-31 设计定案，T-PERM-037）**：菜单可见性 v3.5 §4.1 ∃op 派生方案后端已实现（`/auth/user-menu` 双轨下发按权限过滤后的 menus 树），前端接线归入 Phase 3 联调 T-FE-015（登录链路切真实接口时菜单栏从本地静态路由切后端派生 menus 树）；不改 `filterNoPermissionTree` 按 `meta.auths` 过滤（与后端派生方案重复，且 auths 为前端静态声明可绕过）。联调前维持「菜单可见、路由可达、后端 VIEW 403 兜底」。

### 角色矩阵（历史 mock 口径，真实链路 T-FE-041 后权限来自后端授权）

真实链路下本页可见性由 `OPERATION_LOG:VIEW` 授权决定（bootstrap 固定图已授予管理角色；其他角色经授权页分配）。Phase 1 mock 矩阵原按「复用 SYSTEM_CONFIG:VIEW」为全部账号开放查看，审计分离后该口径作废。

## 8. API 核对清单（T-PERM-025，2026-08-28 收口）

Phase 1 登记的 🔧 项处置终态：

1. ✅ **契约路径与字段契约**：路径误写已于 T-ACCESS-007 评审修复（提交 371d9d009）修正（§5.8 现为实现路径 `/api/perm/log/operation/list`）；operation-log 契约要点（字段/多维筛选/action-options/OPERATION_LOG:VIEW 门禁）已补入 api-contract §5.8。
2. ✅ **筛选维度扩展**：`OperationLogListReq` 补 `operatorId`/`since`/`until`（created_at 闭区间）/`targetType`，均精确匹配（等值索引友好，schema 对应 idx_operation_log_operator/idx_operation_log_target）；前端筛选表单同步扩展（操作者 ID/时间范围/目标类型）。
3. ✅ **审计分离（设计定案 2026-08-28）**：新增独立 `OPERATION_LOG:VIEW` 权限码——type_definition 种子 OPERATION_LOG=30（CRUD 预置组自动覆盖 VIEW）、`ResourceTypeCode.OPERATION_LOG` 枚举、`LogQueryAppServiceImpl` 操作日志查询（list/count/action-options）门禁切换；bootstrap 固定图管理角色补授（§14.4 最小集，新权限码须固定图持否则无授予起点死锁）；前端 `OPERATION_LOG_PERMS.LOG_VIEW` 切换。**边界**：变更日志（log/change/list）已随 T-PERM-032 切独立 `PERMISSION_CHANGE_LOG:VIEW`；权限排查视图（permission-view/explain/recent-changes）已随 T-PERM-033 切被查目标实例 `USER:VIEW`/`ROLE:VIEW`（设计定案：不引入独立排查码）。
4. ✅ **action 字典（任务卡主项）**：新增 `POST /api/perm/log/operation/action-options`（module 可选过滤）返回 operation_log 实际存在的 action 去重集合（非维护端枚举，避免与 @OperationLog 注解清单双轨漂移）；前端 ACTION_OPTIONS 硬编码 12 项子集移除，hook 动态拉取全量字典（label=value=code，filterable 下拉）；LogDetailDrawer action 展示改原始编码。
5. ✅ **action 筛选语义（任务卡决策点，设计定案 2026-08-28）**：动态字典下拉 + 精确匹配——字典含全部实际存在事件码（约 102 个）检索已闭环，保持等值匹配索引语义；不做自由输入/模糊匹配（后端查询语义未改）。

### ✅ 满足

- list 服务端分页 + module/action 过滤原已满足；T-PERM-025 扩展后五维过滤 + 动态字典。
- 无 detail 接口：`OperationLogResp` 含全部字段，详情由前端抽屉展示。

### 备注

- **只读查询页**：operation_log 由后端各 AppService 的 `@OperationLog` AOP 自动写入，前端不可手动增删改。
- **轻量全量日志**：operation_log 记录所有写操作（简单摘要），与 permission_change_log（权限变更详情 + diff 快照）区分。变更日志页见 7.2（T-FE-012）。

## 9. 已知限制

- ~~筛选仅 module/action 两维度~~ 已随 T-PERM-025 扩展五维（2026-08-28）。
- ~~module/action 下拉选项前端本地硬编码~~ action 已切动态字典（module 三值封闭集合保持本地）。
- 详情抽屉纯展示 list 返回字段（无 detail 接口）；若后续需关联 permission_change_log 详情，7.2 变更日志页处理。
- 联调（T-FE-022）：§8 五项已收口，无阻塞项。
