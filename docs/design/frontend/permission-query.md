---
doc_type: design
title: 权限排查 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-08-22   # T-ACCESS-012 重基线：取消聚合层，直连 /api/perm/* 契约端点
---

# 权限排查 前端设计

> 对应任务：T-FE-013
> 后端契约：`docs/design/permission-center/api-contract.md` §6.6-6.8
> 后端任务：T-PERM-033（permission 域门禁统一 + DTO 扩展；聚合层已取消——T-ACCESS-012 决策，页面直连 `/api/perm/*` 契约端点）

## 1. 页面定位

管理员排查主体（用户/角色）权限事实的**只读查询页**。三个核心场景：

| 场景 | 接口 | 契约 | 说明 |
|---|---|---|---|
| 当前有效权限 | `effective-permissions` | §6.8 L1353 | 分页查用户/角色有效权限（管理端排查视图） |
| 范围权限四态 | `query-scopes` | §6.7 L1254 | 查用户在主资源上下文内的范围权限四态 |
| 单权限解释 | `explain` | §6.8 L1458 | 解释单权限为什么有/没有 + 近期影响事件 |

**接口定位纠正**：原验收点名 `query-resources`（§6.6 运行时 SDK 接口，不分页、仅用户），与"管理端排查"定位冲突。本页 Tab1 改用 `effective-permissions`（管理端分页排查视图，支持 USER/ROLE + 来源角色）。`query-resources` 降为 API 核对登记，不做 UI。

## 2. 布局结构

整页无权状态 + 三 Tab：

```
┌─ 整页无权状态（canQuery=false）──────────────────────┐
│  el-result warning "无权限"                           │
└──────────────────────────────────────────────────────┘

┌─ el-tabs（canQuery=true）────────────────────────────┐
│  Tab1 当前有效权限 │ Tab2 范围权限四态 │ Tab3 单权限解释 │
│  ┌─查询表单（inline）─────────────────────────────┐  │
│  │  SubjectInputBar + 场景专属字段                │  │
│  └─结果面板──────────────────────────────────────┘  │
│  Tab1: PureTableBar + pure-table（分页表格）          │
│  Tab2: ScopeMatrixPanel（资源类型×操作矩阵，四态 tag）│
│  Tab3: ExplainPanel（allowed/reason/recentChanges）  │
└──────────────────────────────────────────────────────┘
```

覆写 layout `.main-content` margin：`div.permission-query-page.main-content { margin: var(--space-3); }`（特异性 0,2,1 覆盖 scoped 0,2,0，对齐 T-FE-012 范式）。

## 3. 主体模型（核实 permission 域后端）

| 接口 | targetType | USER 字段 | ROLE 字段 |
|---|---|---|---|
| effective-permissions | USER/ROLE | subjectTypeCode + subjectExternalId | roleTypeCode + roleExternalId + domainCode |
| query-scopes | 仅 USER | subjectTypeCode + subjectExternalId | - |
| explain | USER/ROLE | subjectTypeCode + subjectExternalId | roleTypeCode + roleExternalId + domainCode |

核实依据：
- `PermissionQueryAppServiceImpl:126` query-resources/query-scopes 用 `resolveUserId(subjectTypeCode, subjectExternalId)`，只解析用户
- `PermissionViewAppServiceImpl:698` explain USER 分支用 `resolveUserId`
- `PermissionViewAppServiceImpl:674` explain ROLE 分支用 `resolveRoleId`

**角色类型码**（对齐 `RoleType.java`，不存在 ORG_ROLE/POSITION_ROLE）：
- `ORG` / `POSITION`：domainCode 必填（组织域绑定）
- `BASIC_ROLE` / `GROUP_ROLE` / `PERSONAL`：domainCode 可空（允许全局域）

**用户类型码**：`ADMIN_USER`（AccessMesh 管理端用户主要真实类型）/ `USER`（通用）

## 4. 字段定义

### Tab1 effective-permissions
- 查询：targetType + 主体 + domainCode + resourceTypeCodes[] + operationCodes[] + resourceKeyword + includeSourceRoles + sourceRoleLimit + pageNum/pageSize
- 响应：items[]（resourceTypeCode/resourceCode/resourceName/codeType/operationCodes/scopeMode/sourceRoles/sourceRoleCount/sourceRolesTruncated）+ total/hasNext
- scopeMode 仅 INSTANCE/ALL（§6.8 effective-permissions 两态）

### Tab2 query-scopes
- 查询：subjectTypeCode + subjectExternalId + domainCode + parentResourceTypeCode + parentResourceCode + parentCodeType + parentOperationCodes[] + scopeResourceTypeCodes[] + scopeOperationCodes[] + scopeCodeType
- 响应：reason + matchedParentOperations + parentPermissionIds + scopeGroups[]（resourceTypeCode/operationCode/scopeMode/items/matchedRoleIds/...）
- scopeMode 四态 DENIED/INSTANCE/ALL/EMPTY（§6.7 L1322）

### Tab3 explain
- 查询：targetType + 主体 + domainCode + resourceTypeCode + resourceCode + codeType + operationCode + scopeMode(INSTANCE/ALL) + includeSourceRoles + includeRecentChanges + recentDays
- 响应：allowed + reason + permission + sourceRoles + matchedPermissionIds + recentChanges[]

## 5. 交互流程

1. 页面入口立即 `hasPerms` 检查，无权整页无权状态（路由框架不消费 meta.auths 隐藏菜单）
2. hook 层权限短路：`canQuery=false` 时所有 load 直接 return，不发请求
3. 每 Tab 独立查询表单，主体/targetType 变化清空当前 Tab 旧结果
4. **reqSeq 请求序号**：每 Tab 独立，过期请求静默丢弃（旧请求后返回不覆盖新主体结果，对齐 T-FE-012）
5. Tab3 scopeMode=ALL 时禁用 resourceCode/codeType 输入（§6.8 L1514：ALL 不传 resourceCode/codeType）
6. Tab2 矩阵：资源类型×操作笛卡尔积，每格 scopeMode 四态 tag + tooltip 展开实例列表

## 6. API 依赖

| 端点 | 契约 | 路径 | 说明 |
|---|---|---|---|
| effective-permissions | §6.8 | POST /api/perm/permission-view/effective-permissions | 管理端分页排查视图 |
| query-scopes | §6.7 | POST /api/perm/auth/query-scopes | 范围权限四态 |
| explain | §6.8 | POST /api/perm/permission-view/explain | 单权限解释 |

**路径说明（T-ACCESS-012 决策：取消聚合层）**：原「admin 域聚合层 `/permission-query/*`」方案取消——归并后 `/perm/**` 与 `/admin/**` 同路由到 access-service，聚合层前提（前端不直连权限服务）不再成立；页面直连上表 `/api/perm/*` 契约端点，无新增 Gateway 路由（维持 3 路由契约）。Phase 1 mock 仍模拟 `/permission-query/*` 本地路径，联调（T-FE-019）时切换为契约路径。

## 7. 组件结构

- `index.vue`：主页面（三 Tab + 整页无权状态）
- `components/SubjectInputBar.vue`：USER/ROLE 主体输入栏（Tab1/3 复用，targetType 切换 + 类型码下拉 + externalId + domainCode）
- `components/ScopeMatrixPanel.vue`：Tab2 资源类型×操作矩阵面板（四态 tag + tooltip + 图例）
- `components/ExplainPanel.vue`：Tab3 解释面板（allowed/reason/权限键/sourceRoles/recentChanges 时间线）
- `utils/hook.ts`：三 Tab 独立 hook + 权限门控短路 + reqSeq + 主体变化清空
- `utils/types.ts`：常量（TARGET_TYPE/SUBJECT_TYPE/ROLE_TYPE/SCOPE_MODE/REASON/IMPACT_LEVEL）
- `utils/perms.ts`：权限码 SSOT

### Step 1.5 组件识别（T-FE-001 组件池）
- **资源键输入**（resourceTypeCode + resourceCode + codeType）：Tab2 主资源 + Tab3 单资源复用，候选登记待 2+ 页确认抽取
- **SubjectInputBar**：USER/ROLE 主体输入，候选登记
- **treeMode 结果树**：§6.6 L1249 + query-permission-tree，本页不做 UI，登记 T-PERM-033

## 8. 权限接线

- **临时口径**：`SYSTEM_CONFIG:VIEW`（Phase 1 mock，与操作日志/变更日志同源）
- **T-PERM-033 定稿**：`PERMISSION_QUERY:VIEW` 全链路
- 路由 `meta.auths`：`[...PERMISSION_QUERY_PERM_LIST]`（值 `SYSTEM_CONFIG:VIEW` 临时）
- 路由框架不消费 `meta.auths` 隐藏菜单，页面入口必须 `hasPerms` + 整页无权状态 + hook 短路

### 门禁现状（核实 permission 域）
- explain：`SYSTEM_CONFIG:VIEW`（`PermissionViewAppServiceImpl:665`）
- effective-permissions：目标实例 `USER:VIEW`/`ROLE:VIEW`（`:134/153`）
- query-resources/query-scopes：运行时接口，无排查门禁

前端单独 `SYSTEM_CONFIG:VIEW` 不能形成安全闭环（effective-permissions 还需目标实例 VIEW）。T-PERM-033 统一门禁方案 A/B（见任务文件）。

## 9. API 核对清单（登记 T-PERM-033）

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| 1 | 聚合层取消 | ✅ | T-ACCESS-012 决策：不新增聚合层/路由，页面直连 `/api/perm/*` 契约端点；mock 路径联调时切换 |
| 2 | 统一门禁 PERMISSION_QUERY:VIEW | 🔧 | 方案 A/B + 全链路（资源类型/种子/默认角色/白名单） |
| 3 | explain DTO 扩展 | 🔧 | 命中条件/条件评估/冲突详情 + 评估上下文 + 脱敏 |
| 4 | recentChanges 按权限键过滤 | 🔧 | 完整 6 字段过滤（当前 :735 只按用户/角色取 50 条） |
| 5 | ADMIN_USER/USER 主体语义 | 🔧 | 来源与候选查询方式 |
| 6 | query-resources API 核对 | 🔧 | 运行时 SDK 视角，不做 UI |
| 7 | treeMode TODO | 🔧 | §6.6 L1249 + PermissionQueryAppServiceImpl:162 |
| 8 | permission-view/* 契约差异 | 🔧 | effective-roles/resource-users/role-permissions/effective-permission-codes/resource-tree |
