---
doc_type: design
title: 权限排查 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-08-29   # T-PERM-033 收口：门禁设计定案（无独立排查码，目标实例 USER:VIEW/ROLE:VIEW）+ explain 契约扩展 + API 核对清单收口
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

**用户类型码**：`LOCAL_USER`（AccessMesh 管理端用户/本地访问主体，原 ADMIN_USER 更名）/ `USER`（外部人员）

**主体语义核对（T-PERM-033 收口）**：`resolveUserId` 统一经 `type_definition.user_type` 解析类型值（USER=1/SERVICE=2/LOCAL_USER=3）后按 `abstract_user(tenant_id, user_type, external_id)` 定位主体。`LOCAL_USER` 主体的 `external_id` 由本地投影写为 **`sys_user.id` 字符串**（`LocalProjectionDomainServiceImpl`，主体 ID 同源 T-ORG-001）——排查页选 `LOCAL_USER` 时 `subjectExternalId` 填管理端用户 ID 字符串；`USER` 为外部同步主体，`subjectExternalId` 为外部系统业务键。

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
- **treeMode 结果树**：已从契约移除（2026-08-27，树由调用方自建，见 v3.5.1-evolution）；query-permission-tree 为独立接口不受影响，本页不做 UI

## 8. 权限接线（T-PERM-033 设计定案，2026-08-29）

- **设计定案**：**不引入独立排查权限码**（原预案 `PERMISSION_QUERY:VIEW` 否决——权限码结构为「资源:操作」，`PERMISSION_QUERY` 是操作描述而非资源；排查能力随目标数据可见性走）
- **API 门禁**：`explain` / `effective-permissions` / `recent-changes` = **被查目标实例 `USER:VIEW` / `ROLE:VIEW`**（查谁就要对谁有 VIEW；ROLE 未解析时类型级兜底、USER 未解析返回空/NOT_FOUND），`explain`/`recent-changes` 从临时口径 `SYSTEM_CONFIG:VIEW` 切换
- **query-scopes**：维持契约 §6.7 运行时语义，**不加排查门禁**（设计定案登记；页面级 UI 门控制入口）
- **页面级 UI 门** = `USER:VIEW` 或 `ROLE:VIEW` 任一命中（`canQuery`，hook 层短路）；API 层仍按目标实例逐一校验
- 路由 `meta.auths`：`[...PERMISSION_QUERY_PERM_LIST]`（值 `USER:VIEW`、`ROLE:VIEW`；仅声明，不被路由框架消费）
- 路由框架不消费 `meta.auths` 隐藏菜单，页面入口必须 `hasPerms` + 整页无权状态 + hook 短路

### 门禁矩阵（能力边界）

| 操作者权限 | 结果 |
|---|---|
| 对目标用户有 `USER:VIEW`（或目标角色 `ROLE:VIEW`） | 可查该目标的权限事实 |
| 仅有其他目标的 VIEW | 查该目标被拒（SecurityException） |
| 无任何 USER:VIEW/ROLE:VIEW | 页面整页无权状态（API 全拒） |

### explain 契约扩展（后端已实现，前端展示随 T-FE-019 联调接线）

- 请求增可选 `context.clientIp`（管理员模拟输入；缺省回退当前请求，响应 `evaluationContextSource` 标注 `ADMIN_INPUT`/`CURRENT_REQUEST`）
- 响应增 `evaluatedClientIp` / `conditionEvaluations`（逐项评估 + IP 掩码脱敏 + `OK/DISABLED/NOT_FOUND/INVALID` fail-close）/ `conflictDrops`（互斥丢弃条目 + 命中规则）
- `recentChanges` 按权限键 6 字段过滤（USER 目标保留 `USER_ROLE_CHANGE`，`impactLevel` 对齐 `DIRECT`/`POSSIBLE`）
- 日期/时间类条件按服务进程时钟评估，不可模拟

## 9. API 核对清单（T-PERM-033 收口，2026-08-29）

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| 1 | 聚合层取消 | ✅ | T-ACCESS-012 决策：不新增聚合层/路由，页面直连 `/api/perm/*` 契约端点；mock 路径联调时切换 |
| 2 | 统一门禁 | ✅ | 设计定案：无独立排查码——explain/recent-changes 门禁切被查目标实例 `USER:VIEW`/`ROLE:VIEW`（effective-permissions 原样保留同款检查）；页面 UI 门 = USER:VIEW 或 ROLE:VIEW |
| 3 | explain DTO 扩展 | ✅ | `context.clientIp` 输入 + `evaluationContextSource`（ADMIN_INPUT/CURRENT_REQUEST 回退）+ 条件评估明细（IP 掩码脱敏、日期/时间原样）+ 互斥丢弃明细；前端展示随 T-FE-019 |
| 4 | recentChanges 按权限键过滤 | ✅ | 6 字段匹配（null 请求字段通配）+ USER 目标保留 `USER_ROLE_CHANGE`；候选池 200 / 返回上限 50；`impactLevel` 对齐 DIRECT/POSSIBLE |
| 5 | LOCAL_USER/USER 主体语义 | ✅ | 核对结论见 §3 核对补记；`resolveUserId` = type_definition user_type 解析 + `abstract_user(tenant, type, externalId)` |
| 6 | query-resources API 核对 | ✅ | §6.6 实现（`PermissionQueryAppServiceImpl.queryResources`）响应字段名与契约逐项一致（resourceTypeCode/resourceCode/codeType/resourceName/canGrant/scopeMode/operations/matchedRoleIds/matchedPermissionIds/grantSources），treeMode 已移除、includeChildren/includeInherited 已实现；无差异登记 |
| 7 | ~~treeMode TODO~~ | 已收口 | 2026-08-27 设计定案：从契约移除（无真实消费方），树由调用方自建，登记 v3.5.1-evolution |
| 8 | permission-view/* 契约差异 | ✅ | 核对结论：effective-roles/resource-tree 门禁=目标用户 `USER:VIEW`（实现一致）；role-permissions/effective-permission-codes 门禁与实现一致；**唯一差异**：`resource-users` 契约 §5.8 端点表写「查询拥有资源权限的**用户**」，实现（`getResourcePermissions`）返回的是该资源上的**角色**授予分布（RoleGrantInfo）——登记差异待该端点有消费方时二选一收口（改实现聚合用户维度 or 契约表述对齐角色维度），当前无消费方 |
