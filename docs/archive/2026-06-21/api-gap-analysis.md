---
doc_type: plan
title: API 核对清单（组织与用户页）
status: archived
domain: api
design_refs:
  - docs/design/services/admin-service-api-contract.md
tasks: []
acceptance: "「组织与用户」范围 22 个接口已收口（6 原对齐 + 16 已实现）；5 个 ⏳（perm-center 侧）迁出至后续页面设计阶段单独跟踪"
last_updated: 2026-06-21
archived_to: docs/archive/2026-06-21/api-gap-analysis.md
note: |
  本清单是 gap 清单型计划。每条 🔧/⏳ gap 转 T-API 任务待后续触达时渐进迁移
  （与 org-user-page P1、user-role-proxy-fix 存在交叉，需去重后再登记）。
  归档（2026-06-21）：「组织与用户」范围 22 个接口已收口 —— 6 个原已对齐（✅），
  16 个 🔧 经代码核实已由 admin-service 实现（/user/member-candidates、/user-role/*
  代理、/org/{create,update,delete}、/user/create 返回 UserCreateResp 等），
  与 org-user-page-impl-plan P1=100% 一致；gap 跟踪目的达成，契约权威以
  admin-service-api-contract.md v1.0 为准。
  5 个 ⏳（perm-center 侧：角色管理/权限授予查询/变更日志/业务域/类型定义）不属于
  「组织与用户」页范围，迁出至后续各自页面设计阶段单独跟踪，**不纳入本次收口**。
  正文表格中的 🔧/⏳/❌ 标记为归档时点快照，保留作历史核对记录，不代表当前待办。
---

# API 核对清单

> ⚠️ **本清单已于 2026-06-21 归档**（移至 `docs/archive/2026-06-21/api-gap-analysis.md`）。
> 16 个 🔧 接口经代码核实已由 admin-service 实现，与 [org-user-page-impl-plan](org-user-page-impl-plan.md)（同批次归档）P1=100% 一致。
> 当前权威契约以 [../../design/services/admin-service-api-contract.md](../../design/services/admin-service-api-contract.md) v1.0 为准。
> 下方内容保留作历史核对记录，**不再作为实现依据**；表中标 🔧 的接口实际已落地。
> **归档收口口径**：「组织与用户」范围 22 个接口（6 ✅ + 16 🔧 已实现）已收口；5 个 ⏳（perm-center 侧）迁出至后续页面设计阶段，不在本次收口内。正文表格状态为归档时点快照。

> Phase 1：逐接口核对前端 mock 与后端真实规格，标记 ✅/🔧/❌。
> Phase 2：后端按此清单改造 🔧❌ 项.
>
> 16 个 🔧 接口的契约已在 ../../design/services/admin-service-api-contract.md 定稿 v1.0 (2026-06-14)；归档时点（2026-06-21）已由 admin-service 实现收口，下方表格 🔧 标记保留为历史快照。
>
> **范围限定（2026-06-20 审计 S-025）**：本清单**仅覆盖「组织与用户」页面**涉及的 22~27 个接口（汇总见文末，✅=6 + 🔧=16 + ❌=0 + ⏳=5 = 27）。**不代表 admin-service 全量接口** —— admin-service 实际约 90 个（@RequestBody）/ 101 个（@PostMapping），详见 [../../archive/2026-06-17/design-review.md](../../archive/2026-06-17/design-review.md) §2 D2 信任锚点核实。全量接口数字待工作单 F-1.a（脚本化 `_metrics.md`）落地后根治。
>
> **响应信封**：mock 经 `vite-plugin-fake-server`（`mock/user-manage.ts`）统一返回后端
> `PermResult<T> = { code, message, data, requestId, traceId }`（`code=200` 成功），
> 与 `project-rules.md` §1.1 及 `common/model/PermResult.java` 一致；
> `requestId`（Gateway 生成）和 `traceId`（Micrometer Tracing 链路 ID）由
> `PermResultResponseAdvice` 自动填充，前端 mock 可置空但字段不可省略。
> `api/user-manage.ts` 经 `@/utils/http` 调用并按 `code` 解包后向组件暴露裸数据。

## 核对状态

| 标记 | 含义 |
|------|------|
| ✅ | 前端 mock 与后端对齐 |
| 🔧 | 后端需新增/修改 |
| ❌ | 重大差异，需双向适配 |
| ⏳ | 待核对 |

---

## 1. 组织树

| 接口 | 服务 | 状态 | 备注 |
|------|------|------|------|
| `POST /org/tree` | admin-service | ✅ | 入参 `operationCode` 已对齐；前端增加 `orgName`/`orgType`/`status` 可选过滤 |

---

## 2. 用户管理

| 接口 | 服务 | 状态 | 备注 |
|------|------|------|------|
| `POST /user/page` | admin-service | 🔧 | 需明确为默认组织树用户目录查询；组织成员列表与添加成员候选集不再复用同一语义；契约 v1.0 见 admin-service-api-contract.md §4.1 |
| `POST /user/member-candidates` | admin-service | 🔧 | 新增候选用户查询：从默认组织树中按操作者可见/可管理范围筛选，并排除目标组织已有成员；契约 v1.0 见 admin-service-api-contract.md §4.1 |
| `POST /user/create` | admin-service | 🔧 | 返回 `UserCreateResp(id, initialPassword)`；支持 `orgId`+`primaryOrg` 一步组织分配，且 **orgId 必须属于默认组织树**；需同步 `abstract_user` + `ADMIN_USER resource_entity`；契约 v1.0 见 admin-service-api-contract.md §4.1 |
| `POST /user/update` | admin-service | ✅ | |
| `POST /user/delete` | admin-service | 🔧 | 软删除，`IdsReq { ids: List<Long> }`；生命周期高危操作，只能通过默认组织树身份目录边界管理；契约 v1.0 见 admin-service-api-contract.md §4.1 |
| `POST /user/enable` | admin-service | 🔧 | `UserUpdateStatusReq(ids, status)` 启停一体；status=0 禁用(DISABLE)，status=1 启用(ENABLE)；非默认组织树成员管理员不得获得该能力；契约 v1.0 见 admin-service-api-contract.md §4.1 |

### create 组织分配

**决策**：~~mock 阶段 `createUser` 一步完成组织分配（入参含 `orgId`）。Phase 2 后端改造：`UserCreateReq` 新增 `orgId` + `primaryOrg` 字段，创建时同时建立组织关联，无需前端调 `/user-org/assign`。~~ 已完成基础字段。`orgId` 必须属于默认组织树，否则返回参数错误。按 `default-org-tree-user-lifecycle.md`，创建用户还需保证同步 `abstract_user` 与 `resource_entity(ADMIN_USER)` 两类事实，均使用业务键定位，不回填内部 ID。

### 密码通知

**决策**：~~mock 阶段创建成功后弹窗展示初始密码。后端当前不返回密码，Phase 2 需后端改造。~~ 已完成：`/user/create` 返回 `UserCreateResp(id, initialPassword)`，`/user/reset-password` 返回 `ResetPasswordResp(newPassword)`（newPassword 可选，不传则自动生成）。

---

## 3. 用户-组织关联

| 接口 | 服务 | 状态 | 备注 |
|------|------|------|------|
| `POST /user-org/list` | admin-service | ✅ | 后端 `getUserOrgBriefs` 正确返回 `orgName`+`orgType` |
| `POST /user-org/assign` | admin-service | 🔧 | 门禁为目标组织实例 `ADMIN_ORG:UPDATE` 已对齐；写入语义需改为关系级追加或显式树内替换，禁止删除用户所有组织树关系；需同步 `user_role`；契约 v1.0 见 admin-service-api-contract.md §4.3 |
| `POST /user-org/remove` | admin-service | 🔧 | 门禁为目标组织实例 `ADMIN_ORG:UPDATE`；非默认树只删除关系并回收对应 `user_role`，默认树移除按身份目录高危操作处理；契约 v1.0 见 admin-service-api-contract.md §4.3 |
| `POST /user-org/set-primary` | admin-service | 🔧 | 首期只允许默认组织树主归属；不能全局清除其他组织树主标记；如未来需要每树一个主节点，需显式树维度；契约 v1.0 见 admin-service-api-contract.md §4.3 |

### 多组织树成员关系决策

`/user-org/*` 的门禁归属仍是 `ADMIN_ORG:UPDATE`，但这只解决“谁能管理目标组织成员”的问题，不等于可以修改用户身份生命周期。

按 `default-org-tree-user-lifecycle.md`：

1. 默认组织树是用户目录/身份池，负责用户生命周期。
2. 非默认组织树只能添加/移除已有用户关系。
3. 添加成员候选集必须来自默认组织树中操作者可见/可管理范围，不能默认暴露全租户用户。
4. `user-org` 关系变化后必须同步 permission-center 的 `user_role`，否则组织/岗位角色不会进入权限计算，使用业务键定位。

---

## 4. 用户-角色关联

> ⚠️ **设计变更**（来自 `docs/design/org-user-permission-contract.md` v1.2）：**岗位已从角色模型迁为组织模型**——岗位 = 特殊组织（`ADMIN_ORG`，按 `orgType` / POSITION 树区分），用户↔岗位走 `/user-org/*` 组织成员关系，不再走 `/user-role/*`。`/user-role/*` 仅服务于**功能角色**（BASIC_ROLE/GROUP_ROLE/PERSONAL），排除 ORG 和 POSITION。

### 核心决策：A 方案 — admin 代理

permission-center 的 `/api/perm/user-role/*` 使用业务键（`subjectTypeCode` + `subjectExternalId` + `roleExternalId`），
而 admin-service 管理面使用数字 ID（`userId` + `roleId`）。

**选择 A 方案**：admin-service 新增 `/user-role/*` 代理端点，内部完成 ID ↔ 业务键翻译。
前端不感知业务键，统一使用数字 ID。

| 接口 | 权限中心原接口 | 前端 mock（即 admin 代理规格） | 状态 |
|------|---------------|-------------------------------|------|
| 查询角色 | `POST /api/perm/user-role/list` | `POST /user-role/list` `{ userId }` → `UserRoleItem[]` | 🔧 需 admin 新增代理；契约 v1.0 见 admin-service-api-contract.md §4.4 |
| 分配角色 | `POST /api/perm/user-role/assign` | `POST /user-role/assign` → `{ userId, roleId, validFrom?, validTo? }` | 🔧 需 admin 新增代理；契约 v1.0 见 admin-service-api-contract.md §4.4 |
| 回收角色 | `POST /api/perm/user-role/revoke` | `POST /user-role/revoke` → `{ userId, roleId }` | 🔧 需 admin 新增代理；契约 v1.0 见 admin-service-api-contract.md §4.4 |

**Phase 2 后端改造清单**（admin-service）：
1. 新增 `UserRoleController`，暴露 `/user-role/list`、`/user-role/assign`、`/user-role/revoke`
2. `UserRoleAppService` 完成 ID 翻译：`userId` → `subjectTypeCode:ADMIN_USER` + `subjectExternalId:String.valueOf(userId)`
3. `UserRoleAppService` 完成 `roleId` → `roleExternalId`、`roleTypeCode`、`domainCode` 翻译
4. 响应中 `roleTypeLabel` 由代理层映射（或前端按 `roleTypeCode` 查字典）
5. 响应中 `relationOrgName` 由代理层补充

---

## 5. 本页待核对接口（组织与用户）

| 接口 | 服务 | 状态 | 备注 |
|------|------|------|------|
| `POST /org/create` | admin-service | 🔧 | P0 mock 先行；组织 CRUD 含岗位（特殊组织）；契约 v1.0 见 admin-service-api-contract.md §4.2 |
| `POST /org/update` | admin-service | 🔧 | 含移动（改 parentOrgId）、状态切换；契约 v1.0 见 admin-service-api-contract.md §4.2 |
| `POST /org/delete` | admin-service | 🔧 | `IdReq`；契约 v1.0 见 admin-service-api-contract.md §4.2 |
| `POST /org/page` | admin-service | ✅ | `OrgPageReq` 新增 `orgId` 字段，支持子树筛选语义（岗位 Tab 按选中组织筛选） |
| `POST /org/users` | admin-service | ✅ | `IdReq { id: orgId }` → `OrgUserItemResp[]`；查询组织/岗位下用户列表 |
| `POST /role/list` | admin-service | ✅ | `RoleListQueryReq(roleTypeCodes?)` → `RoleListItemResp[]`；默认仅返回功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）；代理调用 permission-center `roleTypeCodes[]` 多类型过滤 |
| `POST /user/reset-password` | admin-service | 🔧 | `ResetPasswordReq(newPassword可选)` → `ResetPasswordResp(newPassword)`；不传自动生成；生命周期高危操作，只能由默认组织树身份目录边界授权；契约 v1.0 见 admin-service-api-contract.md §4.1 |
| `POST /user/enable` | admin-service | 🔧 | `UserUpdateStatusReq(ids, status)` 启停一体；同 §2，非默认组织树成员管理员不得获得该能力；契约 v1.0 见 admin-service-api-contract.md §4.1 |

## 6. 其他页面接口（不在本页核对范围）

> 以下接口归属其他页面（角色管理、权限授予、审计日志等），由对应页面设计阶段核对。

| 接口 | 服务 | 归属页面 | 状态 |
|------|------|----------|------|
| 角色管理 CRUD | permission-center | 角色管理（2.2） | ⏳ |
| 权限授予查询 | permission-center | 权限授予（4.1）| ⏳ |
| 权限变更日志 | permission-center | 权限变更日志（7.2）| ⏳ |
| 业务域管理 | admin-service | 业务域（5.1）| ⏳ |
| 类型定义管理 | admin-service | 类型定义（6.1）| ⏳ |

---

## 已核对接口汇总

> 下表为归档时点（2026-06-21）快照。**实际状态**：22 个「组织与用户」范围接口（6 ✅ + 16 🔧）已全部由 admin-service 实现收口；5 个 ⏳ 迁出至后续页面设计阶段。

| 状态 | 数量 | 说明 |
|------|------|------|
| ✅ 已对齐 | 6 | org/tree + org/page + org/users + user/update + user-org/list + role/list |
| 🔧 需后端改造（归档时点快照，**已实现收口**） | 16 | 默认树用户目录与候选用户查询、user/create/delete/enable/reset-password 生命周期边界、user-org assign/remove/set-primary 跨树语义与 user_role 同步、user-role/list/assign/revoke 代理、org/create/update/delete。**已由 admin-service 实现**，契约见 ../../design/services/admin-service-api-contract.md v1.0 |
| ❌ 重大差异 | 0 | |
| ⏳ 待核对（其他页面，**已迁出本次收口**） | 5 | 角色管理、权限授予、变更日志、业务域、类型定义 —— 属 perm-center 侧，非「组织与用户」页范围，待各自页面设计阶段单独跟踪 |
