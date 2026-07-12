# 7.2 权限变更日志页设计

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
| 权限 | `SYSTEM_CONFIG:VIEW` 复用 | `LogQueryAppServiceImpl.listChangeLogs:87`，无独立权限码 |
| 分页 | 服务端分页 | `PaginatedResp<ChangeLogResp>` |
| detail | 无独立接口 | `ChangeLogResp` 已含全字段（含 diffSnapshot），前端抽屉展示 |

### 请求 `ChangeLogListReq`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| entityType | string | 否 | 实体类型过滤（user_role/role_resource_permission/abstract_user/abstract_role 等） |
| entityId | number | 否 | 实体 ID 过滤 |
| pageNum | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |

### 响应 `ChangeLogResp`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 日志 ID |
| tenantId | number | 租户 ID |
| entityType | string | 变更实体类型（schema L626） |
| entityId | number? | 变更实体 ID |
| operation | string | 实体层操作：INSERT/UPDATE/DELETE（schema L627，区别于 diff changeType） |
| oldSnapshot | string? | 变更前快照 JSON 字符串 |
| newSnapshot | string? | 变更后快照 JSON 字符串 |
| diffSnapshot | string? | 结构化变更摘要 JSON 字符串（§6.8 规范） |
| affectedAbstractUserIds | number[]? | 受影响用户 ID 数组 |
| affectedAbstractRoleIds | number[]? | 受影响角色 ID 数组 |
| changeReason | string? | 变更原因 |
| changeSource | string? | 变更来源：MANUAL/SERVICE_SYNC（后端复用 `PermConstants.MaintainSource`） |
| requestId | string? | 请求/追踪 ID |
| createdAt | string | 创建时间 |

### diff_snapshot 规范（api-contract §6.8 L1590-1671）

顶层：`eventType`（7 枚举）+ `items[]`

| eventType | 含义 | items 典型字段 |
|---|---|---|
| USER_ROLE_CHANGE | 用户角色变更 | role |
| ROLE_PERMISSION_CHANGE | 角色权限变更 | permission + role |
| ROLE_STATUS_CHANGE | 角色状态变更 | role + before/after |
| RESOURCE_STATUS_CHANGE | 资源状态变更 | resource + before/after |
| CONDITION_CHANGE | 条件变更 | before/after |
| GROUP_ROLE_CHANGE | 分组角色变更 | role |
| RESOURCE_DEPENDENCY_CHANGE | 资源依赖变更 | resource + message |

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
  LOG_VIEW: "SYSTEM_CONFIG:VIEW" // 复用后端，无独立权限码
} as const;
```

### 门控

- 路由 `meta.auths = [...PERMISSION_CHANGE_LOG_PERM_LIST]`（单元素 `SYSTEM_CONFIG:VIEW`）
- 表格「查看」按钮 `canView = computed(() => hasPerms(PERMISSION_CHANGE_LOG_PERMS.LOG_VIEW))`
- 无写操作（只读查询页），无 CREATE/UPDATE/DELETE/MANAGE

### 角色矩阵（mock/login.ts）

复用 `SYSTEM_CONFIG:VIEW`，矩阵不新增权限串。admin/sec/hr/auditor 均已通过前页 SYSTEM_CONFIG 矩阵获得 VIEW，均可查看变更日志（审计员 auditor 必须能查，符合审计场景）。

## 5. API 核对清单（-> T-PERM-032）

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| 1 | 端点路径 | 🔧 | api-contract §5.8 写 `/api/perm/permission-change-log/list`，后端实际 `/api/perm/log/change/list`。契约路径错误，Phase 2 修正 |
| 2 | 字段契约章节 | 🔧 | api-contract 无独立 §6.x 变更日志字段契约章节（仅 §5.8 表格 1 行 + §6.8 diff_snapshot 规范）。Phase 2 补 |
| 3 | 筛选维度 | 🔧 | 后端 Req 只支持 entityType/entityId；schema 有 affected_*_ids/created_at/diff_snapshot.eventType 等可用筛选未暴露。Phase 2 补 eventType/changeSource/时间范围/affected user·role 筛选 |
| 4 | 操作人字段 | 🔧 | ChangeLogResp 缺操作人（实体有 createdBy 未暴露，无 operatorName）。Phase 2 补 |
| 5 | changeSource 枚举 | 🔧 | schema L631 注释写 ADMIN/SYNC/API/SYSTEM，后端代码实际用 MANUAL/SERVICE_SYNC（复用 PermConstants.MaintainSource）。schema 注释修正 |
| 6 | 独立权限码 | 🔧 | 复用 SYSTEM_CONFIG:VIEW 做审计查询门禁，审计语义混淆。Phase 2 评估独立 PERMISSION_CHANGE_LOG:VIEW |
| 7 | list 端点 | ✅ | `/api/perm/log/change/list` 分页查询可用，返回 PaginatedResp |
| 8 | diff_snapshot 规范 | ✅ | §6.8 L1590-1671 完整规范，7 种 eventType + 3 种 changeType 固定枚举 |
| 9 | detail 端点 | ✅ | 无需独立 detail（Resp 含全字段 + diffSnapshot），设计合理 |
| 10 | eventType 枚举一致性 | 🔧 | 后端批量删除角色 `diffSnapshot.eventType` 写 `"ROLE_BATCH_DELETE"`（`RoleManageAppServiceImpl:286`），超出 §6.8 定义的 7 枚举。前端 `EVENT_TYPE_META` fallback 显示原值不崩溃，但枚举不一致需后端收敛或契约补枚举 |

## 6. 组件识别（Step 1.5 -> T-FE-001 组件池）

| 候选组件 | 场景 | 确认状态 |
|---|---|---|
| DiffSnapshotPanel | 权限变更日志 diff 展示 + 操作日志详情（未来扩展） | ⏳ 待确认（本页内联实现，T-FE-005 当前 OperationLogResp 无 diffSnapshot 字段，共用性待后续扩展确认） |

遵循「2+ 页确认后才抽取」原则，本页先内联于 `components/DiffSnapshotPanel.vue`，不现在抽取。待 T-FE-005 操作日志详情扩展 diff 展示（或后端 OperationLogResp 补 diffSnapshot）后再抽取为 `ReDiffViewer`。

## 7. Mock 数据说明

`mock/permission-change-log.ts`（零 src 依赖，本地声明类型，对齐 operation-log.ts 范式）：

- 14 条变更日志，覆盖全部 7 种 eventType + 1 种超枚举（ROLE_BATCH_DELETE，验证 fallback）
- 覆盖 entityType：role_resource_permission / user_role / resource_entity / abstract_role / permission_condition / resource_dependency
- 覆盖 operation：INSERT / UPDATE / DELETE / BATCH_DELETE / BATCH_REMOVE（后两者为 entityId=0 批量聚合日志，对齐后端 `RoleManageAppServiceImpl:300` / `UserManageAppServiceImpl:672`）
- 覆盖 entityId=0 批量聚合场景：抽象角色批量删除 + 用户角色批量撤销
- 覆盖 changeSource：MANUAL / SERVICE_SYNC
- diff_snapshot 含 permission/role/resource/before-after 组合，验证 diff 面板结构化渲染
- createdAt 固定字符串（脚本禁用 Date.now），按 DESC 排序验证分页
- 服务端分页 + entityType/entityId 过滤（对齐后端 Req）
