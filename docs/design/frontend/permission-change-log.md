---
doc_type: design
title: 7.2 权限变更日志页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-09-03   # 2026-09-03 T-FE-022 联调收口（mock 退役/api 切 Gateway /perm 前缀/浏览器冒烟全过）——Gateway +1 端点；assign 无变更日志登记已知差距；2026-08-29   # 2026-08-29 T-PERM-032 收口：§3/§4/§5/§7 终态化（独立 PERMISSION_CHANGE_LOG:VIEW/筛选全集/createdBy/ROLE_BATCH_DELETE 补枚举）
---

# 7.2 权限变更日志页设计

> **T-FE-022 联调注记（2026-09-03）**：api/permission-change-log.ts 切 Gateway `/perm/api/perm/log/change/list`；mock/permission-change-log.ts 整删；浏览器实证 list + 详情抽屉（diff/old-new 快照/影响范围）。已知差距（登记于 T-FE-022 任务卡）：user-role assign 路径不写 permission_change_log（revoke 路径写 USER_ROLE_CHANGE），§6.8 事件历史 assign 半缺，聚合粒度设计待定。

> 状态：adopted（T-FE-012 实现产出回写）
> 关联任务：T-FE-012（前端）、T-PERM-032（后端 API 核对）
> 参照：7.1 操作日志页（`operation-log.md`）只读查询范式

## 1. 页面定位

权限变更日志页是权限排查链路的「原始审计详情」入口（core-flows §13.2 用户排查第 5 步 / §13.3 角色排查第 4 步），回答「某次权限变更具体改了什么」。

与 7.1 操作日志区分：

| 维度 | 7.1 操作日志 | 7.2 权限变更日志 |
|---|---|---|
| 表 | `operation_log` | `permission_change_log` |
| 定位 | 所有写操作的轻量记录 | 权限变更的 before/after/diff 详情 |
| 字段 | module/action/summary/operatorName | entityType/operation/oldSnapshot/newSnapshot/diffSnapshot/affected*Ids |
| 详情展示 | el-descriptions 纯字段 | el-descriptions + **diff 对比面板** + 影响分析 |

与 `permission-view/recent-changes` 区分：recent-changes 回答「最近有哪些事件可能影响权限」（轻量，`impactLevel` DIRECT/POSSIBLE）；变更日志回答「某次变更的原始 before/after/diff 审计」（详情，含完整快照）。

## 2. 布局

```
┌─ PureTableBar（筛选：实体类型下拉 + 实体 ID 输入）────────────────────┐
│ ┌─ pure-table（服务端分页）──────────────────────────────────────────┐ │
│ │ 时间 | 事件类型 | 变更操作 | 实体类型 | 变更来源 | 变更原因 | 影响范围 | 操作 │ │
│ │ ...                                                                  │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ 分页                                                                    │
└──────────────────────────────────────────────────────────────────────────┘

┌─ ChangeLogDetailDrawer（rtl 640px）──────────────────────────────────────┐
│ 基本信息区（el-descriptions）                                           │
│   日志ID / 实体类型 / 实体ID / 变更操作 / 变更来源 / 变更原因 /          │
│   请求ID / 创建时间                                                     │
│ 变更差异区（DiffSnapshotPanel）                                         │
│   结构化 diff_snapshot：eventType tag + items[]                         │
│     每项：changeType tag + permission/role/resource 业务键卡片          │
│           + before/after 状态对比（INSERT/UPDATE/DELETE 场景）          │
│   old/new 原始快照折叠（审计追溯）                                      │
│ 影响范围区                                                              │
│   受影响用户 ID tags / 受影响角色 ID tags                               │
└──────────────────────────────────────────────────────────────────────────┘
```

## 3. 数据契约

### 端点

| 项 | 值 | 说明 |
|---|---|---|
| 路径 | `POST /api/perm/log/change/list` | 后端 `LogQueryController` `@RequestMapping("/api/perm/log")` + `@PostMapping("/change/list")` |
| 权限 | 独立 `PERMISSION_CHANGE_LOG:VIEW` | T-PERM-032 审计分离（对齐操作日志先例），页面 list/count 门禁；排查视图 recent-changes 已随 T-PERM-033 切被查目标实例 `USER:VIEW`/`ROLE:VIEW` |
| 分页 | 服务端分页 | `PageResp<ChangeLogResp>` |
| detail | 无独立接口 | `ChangeLogResp` 已含全字段（含 diffSnapshot），前端抽屉展示 |

### 请求 `ChangeLogListReq`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| entityType | string | 否 | 实体类型过滤（user_role/role_resource_permission/abstract_user/abstract_role 等） |
| entityId | number | 否 | 实体 ID 过滤 |
| eventType | string | 否 | diff_snapshot.eventType 过滤（单选，T-PERM-032 补） |
| changeSource | string | 否 | 变更来源过滤（MANUAL/SERVICE_SYNC，T-PERM-032 补） |
| affectedUserId | number | 否 | 受影响用户 ID（GIN 包含匹配，T-PERM-032 补） |
| affectedRoleId | number | 否 | 受影响角色 ID（GIN 包含匹配，T-PERM-032 补） |
| since/until | string | 否 | 创建时间闭区间（ISO 无偏移墙钟，数字对齐展示；T-PERM-032 补） |
| pageNum | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |

### 响应 `ChangeLogResp`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 日志 ID |
| tenantId | number | 租户 ID |
| entityType | string | 变更实体类型（schema entity_type 列注释） |
| entityId | number? | 变更实体 ID |
| operation | string | 实体层操作：INSERT/UPDATE/DELETE/BATCH_DELETE/BATCH_REMOVE（schema operation 列注释，区别于 diff changeType） |
| oldSnapshot | string? | 变更前快照 JSON 字符串 |
| newSnapshot | string? | 变更后快照 JSON 字符串 |
| diffSnapshot | string? | 结构化变更摘要 JSON 字符串（§6.8 规范） |
| affectedAbstractUserIds | number[]? | 受影响用户 ID 数组 |
| affectedAbstractRoleIds | number[]? | 受影响角色 ID 数组 |
| changeReason | string? | 变更原因 |
| changeSource | string? | 变更来源：MANUAL/SERVICE_SYNC（后端复用 `PermConstants.MaintainSource`；schema 注释已随 T-PERM-032 修正） |
| createdBy | number? | 操作人 ID（表 created_by，T-PERM-032 暴露；名称解析归前端展示层） |
| requestId | string? | 请求/追踪 ID |
| createdAt | string | 创建时间 |

### diff_snapshot 规范（api-contract §6.8）

顶层：`eventType`（7 枚举；T-PERM-043 移除 `GROUP_ROLE_CHANGE`，T-PERM-032 增补 `ROLE_BATCH_DELETE`）+ `items[]`

| eventType | 含义 | items 典型字段 |
|---|---|---|
| USER_ROLE_CHANGE | 用户角色变更 | role |
| ROLE_PERMISSION_CHANGE | 角色权限变更 | permission + role |
| ROLE_STATUS_CHANGE | 角色状态变更 | role + before/after |
| RESOURCE_STATUS_CHANGE | 资源状态变更 | resource + before/after |
| CONDITION_CHANGE | 条件变更 | before/after |
| RESOURCE_DEPENDENCY_CHANGE | 资源依赖变更 | resource + message |
| ROLE_BATCH_DELETE | 批量删除角色的聚合事件（T-PERM-032 增补） | role（items[] 逐角色；entityId=0 + operation=BATCH_DELETE） |

`items[].changeType`：ADD / REMOVE / UPDATE

`items[].permission`：{domainCode, resourceTypeCode, resourceCode, codeType, operationCode, scopeMode}
`items[].role`：{roleTypeCode, roleExternalId, roleName}
`items[].resource`：{domainCode, resourceTypeCode, resourceCode, codeType}

前端 `parseDiffSnapshot(raw)` 安全解析 JSON 字符串，失败返回 null。

## 4. 权限接线

### SSOT

`views/system/permission-change-log/utils/perms.ts`：

```typescript
export const PERMISSION_CHANGE_LOG_PERMS = {
  LOG_VIEW: "PERMISSION_CHANGE_LOG:VIEW" // T-PERM-032 审计分离：独立权限码
} as const;
```

### 门控

- 路由 `meta.auths = [...PERMISSION_CHANGE_LOG_PERM_LIST]`（单元素 `PERMISSION_CHANGE_LOG:VIEW`）
- 表格「查看」按钮 `canView = computed(() => hasPerms(PERMISSION_CHANGE_LOG_PERMS.LOG_VIEW))`
- 无写操作（只读查询页），无 CREATE/UPDATE/DELETE/MANAGE

### 角色矩阵（mock/login.ts）

T-PERM-032 起独立 `PERMISSION_CHANGE_LOG:VIEW`，mock/login.ts 四账号按旧复用口径全员补入（对齐操作日志先例——mock 模拟 UX 不模拟最小权限；审计员 auditor 必须能查，符合审计场景）。

## 5. API 核对清单（-> T-PERM-032）

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| 1 | 端点路径 | ✅ | 核实已随 T-ACCESS-007 评审修正（§5.8 表现即实际路径），本任务在 §5.8 契约要点补记 |
| 2 | 字段契约章节 | ✅ | T-PERM-032 补 §5.8 permission-change-log 契约要点（端点/筛选全集/createdBy/门禁） |
| 3 | 筛选维度 | ✅ | T-PERM-032 全集落地（设计定案）：eventType/changeSource/受影响 user·role/时间范围全部暴露，维度对齐 schema 索引；页面与 recent-changes 统一条件组（原两套查询合并） |
| 4 | 操作人字段 | ✅ | `ChangeLogResp` 暴露 `createdBy`（表 created_by；名称解析归前端展示层），抽屉展示 |
| 5 | changeSource 枚举 | ✅ | schema 注释修正为 MANUAL/SERVICE_SYNC（复用 PermConstants.MaintainSource）；operation 注释同步补 BATCH_DELETE/BATCH_REMOVE |
| 6 | 独立权限码 | ✅ | 设计定案（2026-08-29）：独立 `PERMISSION_CHANGE_LOG:VIEW`，五步清单全链路（枚举/DDL 种子=31/bootstrap 固定图/下发白名单/前端常量+mock 矩阵）；排查视图 recent-changes 已随 T-PERM-033 切被查目标实例 USER:VIEW/ROLE:VIEW |
| 7 | list 端点 | ✅ | `/api/perm/log/change/list` 分页查询可用，返回 PageResp |
| 8 | diff_snapshot 规范 | ✅ | §6.8 完整规范，7 种 eventType（T-PERM-043 后 6 种 + T-PERM-032 增 ROLE_BATCH_DELETE）+ 3 种 changeType 固定枚举 |
| 9 | detail 端点 | ✅ | 无需独立 detail（Resp 含全字段 + diffSnapshot），设计合理 |
| 10 | eventType 枚举一致性 | ✅ | 设计定案：契约 §6.8 增补第 7 枚举 `ROLE_BATCH_DELETE`（批量删除聚合事件，entityId=0 + operation=BATCH_DELETE——原 6 枚举无一语义覆盖）；前端 `DiffEventType`/`EVENT_TYPE_META` 同步 |

## 6. 组件识别（Step 1.5 -> T-FE-001 组件池）

| 候选组件 | 场景 | 确认状态 |
|---|---|---|
| DiffSnapshotPanel | 权限变更日志 diff 展示 + 操作日志详情（未来扩展） | ⏳ 待确认（本页内联实现，T-FE-005 当前 OperationLogResp 无 diffSnapshot 字段，共用性待后续扩展确认） |

遵循「2+ 页确认后才抽取」原则，本页先内联于 `components/DiffSnapshotPanel.vue`，不现在抽取。待 T-FE-005 操作日志详情扩展 diff 展示（或后端 OperationLogResp 补 diffSnapshot）后再抽取为 `ReDiffViewer`。

## 7. Mock 数据说明

`mock/permission-change-log.ts`（零 src 依赖，本地声明类型，对齐 operation-log.ts 范式）：

- 变更日志（T-PERM-043 删 GROUP_ROLE_CHANGE 条目后）覆盖全部 7 种 eventType（含 T-PERM-032 收编的 ROLE_BATCH_DELETE；未知值仍验证 fallback 渲染）
- 覆盖 entityType：role_resource_permission / user_role / resource_entity / abstract_role / permission_condition / resource_dependency
- 覆盖 operation：INSERT / UPDATE / DELETE / BATCH_DELETE / BATCH_REMOVE（后两者为 entityId=0 批量聚合日志，对齐后端 RoleManageAppServiceImpl 批量删除 / UserManageAppServiceImpl 批量撤销的聚合日志写入）
- 覆盖 entityId=0 批量聚合场景：抽象角色批量删除 + 用户角色批量撤销
- 覆盖 changeSource：MANUAL / SERVICE_SYNC
- diff_snapshot 含 permission/role/resource/before-after 组合，验证 diff 面板结构化渲染
- createdAt 固定字符串（脚本禁用 Date.now），按 DESC 排序验证分页
- 服务端分页 + 全维度过滤（T-PERM-032 筛选全集，对齐后端 Req；时间闭区间字符串可比）+ 全条目补 createdBy
