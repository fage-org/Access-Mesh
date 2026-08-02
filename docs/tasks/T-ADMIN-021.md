---
doc_type: task
id: T-ADMIN-021
title: org-tree 扩展 includePositions（组织+岗位一体树，授权页主体树数据源）- **二期，首期不推进（第十四轮收窄）**
status: proposed  # 二期，首期不推进（第十四轮：首期只角色入口）
plan: docs/plans/frontend-phase2-plan.md
domain: admin-service
design_refs:
  - docs/design/frontend/permission-grant.md#§9
  - docs/design/services/admin-service.md
  - docs/design/services/admin-service-api-contract.md#§4.2.1
depends_on: []
acceptance:
  - "org-tree 接口新增 includePositions 参数（默认 false 兼容现有调用）：true 时返回组织树 + 岗位节点（岗位作为所属组织的子节点，同一树结构，不分页）；**响应定稿 `PermResult<OrgItemsResp>{data:{items:[...]}}`（P1-3 第八轮：不再返回裸数组，含既有调用方适配）**"
  - "岗位节点复用现有 `orgType=2` 字段区分（组织 orgType=1，与 PositionTab 一致；不引入新字段）"
  - "数据源门禁对齐现有 org-tree（ADMIN_ORG:VIEW / ADMIN_ORG:VIEW_POSITION）；**否定性验收：仅 ADMIN_ORG:VIEW（无 VIEW_POSITION）的调用者响应中不包含任何岗位节点（orgType=2）**；**故障注入验收：permission-center 不可达/非 200/空响应时 org-tree 返回 SystemException 错误响应（EXTERNAL_SERVICE_ERROR 业务码，统一响应非 200），不得返回裁剪后的树（岗位裁剪不得静默降级，P2-1）**"
  - "design_writeback：docs/design/frontend/permission-grant.md §9 组织主体适配器描述确认；docs/design/services/admin-service-api-contract.md §4.2.1 includePositions 契约（P1-6）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-01
---

# T-ADMIN-021 org-tree 扩展 includePositions

> 状态：proposed（**二期，首期不推进**--第十四轮首期只角色入口，组织入口延后）
> 复杂度：🟡 中
> 关联前端：T-FE-036（组织入口左栏主体树依赖本接口，见 permission-grant.md §9）

## 背景

权限授予页（T-FE-036）组织入口需要"组织 + 岗位"一体左栏主体树。现状：

- `org-tree`（orgType=1）只返回普通组织树（`ReOrgTreePanel` 固定 orgType=1）；
- 岗位由 `getOrgPage({orgType:2})` 分页加载（`PositionTab` 模式），非树结构且分页上限 100。

2026-08-01 第六轮评审 P2-3 定案：**新增后端一体树接口（不分页）**，前端一次拉取，避免岗位分页遍历与静默漏节点。

## 实现要点

- 扩展 admin-service `org-tree` 接口：请求体新增 `includePositions`（boolean，默认 false 兼容现有调用）。**请求/响应/兼容行为/权限裁剪以 `docs/design/services/admin-service-api-contract.md §4.2.1` 为准（P1-6）**：`includePositions=true` 时**忽略 `orgType`**（返回组织+岗位一体树，与 orgType=1/2 单类型语义互斥）；默认 false 时行为与现状完全一致（orgType 必填，`ORG_TYPE_REQUIRED` 保留，P1-2）。
- **响应包装改造（P1-3）**：本任务顺带把 `/org/tree` 响应从裸数组改为 `PermResult<OrgItemsResp>{data:{items:[...]}}`（契约 §4.2.1 定稿形状），**同步适配既有调用方**（user 页 org-tree 消费处）；合规债务清单相应销账。
- `includePositions=true` 时：组织树照常返回，岗位（orgType=2）作为其所属组织的**子节点**挂入同一树（岗位自身不再有下级）。
- 节点区分：组织节点带 `orgType=1`（现有字段），岗位节点带 `orgType=2`（前端以此区分主体入口类型）。
- 数据量：岗位总数为组织数量级（数百~数千），单次全量返回可接受；若未来超阈值再评估懒加载。
- 门禁：对齐现有 org-tree 数据源门禁（`ADMIN_ORG:VIEW`；岗位部分需 `ADMIN_ORG:VIEW_POSITION`，按现有 PositionTab 门禁语义）。**后端按调用者岗位权限裁剪岗位节点**（仅 `ADMIN_ORG:VIEW` 的调用者不返回岗位节点；前端隐藏不作安全边界）。
- **裁剪判定入口（P2-1，第八轮定稿）**：`AdminPermissionValidator` 新增**非抛出判定** `boolean hasTypeLevel(String resourceTypeCode, String operationCode)`——**仅成功响应且 `allowed=false` 返回 false**；**协议/传输故障**（Feign 异常/非 200/空响应）抛 `SystemException`（`AdminErrorCode.EXTERNAL_SERVICE_ERROR`，RoleProxyServiceImpl 同款用法）——**不得复用 `SecurityException`**（全局映射 403，与故障注入验收矛盾）；**注意：`GlobalExceptionHandler.handleSystemException` 无 `@ResponseStatus`，现状映射为 HTTP 200 + 业务码（EXTERNAL_SERVICE_ERROR）**——本任务**不改全局映射**（会影响 RoleProxyServiceImpl 等既有抛点语义），故障可识别性由统一响应业务码保证（调用方判 code!=200 即错误）；`checkAndThrow` 保持不动（既有调用方语义不变）；岗位裁剪用 `hasTypeLevel(ADMIN_ORG, VIEW_POSITION)`，false 才裁剪。

## 验收标准

见 acceptance（includePositions 参数、岗位子节点、orgType 区分、门禁、design_writeback）。

## 完成记录

（待实现后填写）
