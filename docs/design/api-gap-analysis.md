# API 核对清单

> Phase 1：逐接口核对前端 mock 与后端真实规格，标记 ✅/🔧/❌。
> Phase 2：后端按此清单改造 🔧❌ 项。
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
| `POST /user/page` | admin-service | ✅ | `UserPageReq` 支持 `orgId` 子树筛选（选中组织及其子孙组织下的成员） |
| `POST /user/create` | admin-service | ✅ | 返回 `UserCreateResp(id, initialPassword)`；支持 `orgId`+`primaryOrg` 一步组织分配（**orgId 必须属于默认组织树**） |
| `POST /user/update` | admin-service | ✅ | |
| `POST /user/delete` | admin-service | ✅ | 软删除，`IdsReq { ids: List<Long> }` |
| `POST /user/enable` | admin-service | ✅ | `UserUpdateStatusReq(ids, status)` 启停一体；status=0 禁用(DISABLE)，status=1 启用(ENABLE) |

### create 组织分配

**决策**：~~mock 阶段 `createUser` 一步完成组织分配（入参含 `orgId`）。Phase 2 后端改造：`UserCreateReq` 新增 `orgId` + `primaryOrg` 字段，创建时同时建立组织关联，无需前端调 `/user-org/assign`。~~ 已完成。`orgId` 必须属于默认组织树，否则返回参数错误。

### 密码通知

**决策**：~~mock 阶段创建成功后弹窗展示初始密码。后端当前不返回密码，Phase 2 需后端改造。~~ 已完成：`/user/create` 返回 `UserCreateResp(id, initialPassword)`，`/user/reset-password` 返回 `ResetPasswordResp(newPassword)`（newPassword 可选，不传则自动生成）。

---

## 3. 用户-组织关联

| 接口 | 服务 | 状态 | 备注 |
|------|------|------|------|
| `POST /user-org/list` | admin-service | ✅ | 后端 `getUserOrgBriefs` 正确返回 `orgName`+`orgType` |
| `POST /user-org/assign` | admin-service | ✅ | 支持 `primaryOrgId`，一步设置主组织；门禁为目标组织实例 `ADMIN_ORG:UPDATE` |
| `POST /user-org/remove` | admin-service | ✅ | 门禁为目标组织实例 `ADMIN_ORG:UPDATE` |
| `POST /user-org/set-primary` | admin-service | ✅ | 门禁为目标组织实例 `ADMIN_ORG:UPDATE` |

---

## 4. 用户-角色关联

> ⚠️ **2026-06-07 设计变更**（来自 `docs/design/org-user-permission-contract.md` v1.1）：**岗位已从角色模型迁为组织模型**——岗位 = 特殊组织（`ADMIN_ORG`，按 `orgType` / POSITION 树区分），用户↔岗位走 `/user-org/*` 组织成员关系，不再走 `/user-role/*`。`/user-role/*` 仅服务于**功能角色**（BASIC_ROLE/GROUP_ROLE/PERSONAL），排除 ORG 和 POSITION。

### 核心决策：A 方案 — admin 代理

permission-center 的 `/api/perm/user-role/*` 使用业务键（`subjectTypeCode` + `subjectExternalId` + `roleExternalId`），
而 admin-service 管理面使用数字 ID（`userId` + `roleId`）。

**选择 A 方案**：admin-service 新增 `/user-role/*` 代理端点，内部完成 ID ↔ 业务键翻译。
前端不感知业务键，统一使用数字 ID。

| 接口 | 权限中心原接口 | 前端 mock（即 admin 代理规格） | 状态 |
|------|---------------|-------------------------------|------|
| 查询角色 | `POST /api/perm/user-role/list` | `POST /user-role/list` `{ userId }` → `UserRoleItem[]` | 🔧 需 admin 新增代理 |
| 分配角色 | `POST /api/perm/user-role/assign` | `POST /user-role/assign` → `{ userId, roleId, validFrom?, validTo? }` | 🔧 需 admin 新增代理 |
| 回收角色 | `POST /api/perm/user-role/revoke` | `POST /user-role/revoke` → `{ userId, roleId }` | 🔧 需 admin 新增代理 |

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
| `POST /org/create` | admin-service | 🔧 | P0 mock 先行；组织 CRUD 含岗位（特殊组织） |
| `POST /org/update` | admin-service | 🔧 | 含移动（改 parentOrgId）、状态切换 |
| `POST /org/delete` | admin-service | 🔧 | `IdReq` |
| `POST /org/page` | admin-service | ✅ | `OrgPageReq` 新增 `orgId` 字段，支持子树筛选语义（岗位 Tab 按选中组织筛选） |
| `POST /org/users` | admin-service | ✅ | `IdReq { id: orgId }` → `OrgUserItemResp[]`；查询组织/岗位下用户列表 |
| `POST /role/list` | admin-service | ✅ | `RoleListQueryReq(roleTypeCodes?)` → `RoleListItemResp[]`；默认仅返回功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）；代理调用 permission-center `roleTypeCodes[]` 多类型过滤 |
| `POST /user/reset-password` | admin-service | ✅ | `ResetPasswordReq(newPassword可选)` → `ResetPasswordResp(newPassword)`；不传自动生成 |
| `POST /user/enable` | admin-service | ✅ | `UserUpdateStatusReq(ids, status)` 启停一体 |

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

| 状态 | 数量 | 说明 |
|------|------|------|
| ✅ 已对齐 | 14 | org/tree + org/page + org/users + user/page/create/update/delete/enable/reset-password + user-org/list/assign/remove/set-primary + role/list |
| 🔧 需后端改造 | 6 | user-role/list/assign/revoke(代理，仅功能角色)、org/create/update/delete |
| ❌ 重大差异 | 0 | |
| ⏳ 待核对（其他页面） | 5 | 角色管理、权限授予、变更日志、业务域、类型定义 |
