# API 核对清单

> Phase 1：逐接口核对前端 mock 与后端真实规格，标记 ✅/🔧/❌。
> Phase 2：后端按此清单改造 🔧❌ 项。
>
> **响应信封**：mock 经 `vite-plugin-fake-server`（`mock/user-manage.ts`）统一返回后端
> `PermResult<T> = { code, message, data }`（`code=200` 成功），与 admin-service 一致；
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
| `POST /user/page` | admin-service | 🔧 | 后端 `UserPageReq` 需新增 `orgId` 筛选字段 |
| `POST /user/create` | admin-service | ✅ | 后端返回 `Long`；mock 额外返回 `initialPassword`（后端暂不返回）|
| `POST /user/update` | admin-service | ✅ | |
| `POST /user/delete` | admin-service | ✅ | 软删除，`IdsReq { ids: List<Long> }` |
| `POST /user/enable` | admin-service | ⏳ | 暂未接入 |

### create 组织分配

**决策**：mock 阶段 `createUser` 一步完成组织分配（入参含 `orgId`）。
Phase 2 后端改造：`UserCreateReq` 新增 `orgId` + `primaryOrg` 字段，创建时同时建立组织关联，无需前端调 `/user-org/assign`。

### 密码通知

**决策**：mock 阶段创建成功后弹窗展示初始密码。后端当前不返回密码，Phase 2 需后端改造。

---

## 3. 用户-组织关联

| 接口 | 服务 | 状态 | 备注 |
|------|------|------|------|
| `POST /user-org/list` | admin-service | ✅ | 后端 `getUserOrgBriefs` 正确返回 `orgName`+`orgType` |
| `POST /user-org/assign` | admin-service | ✅ | 支持 `primaryOrgId`，一步设置主组织 |
| `POST /user-org/remove` | admin-service | ✅ | |
| `POST /user-org/set-primary` | admin-service | ✅ | |

---

## 4. 用户-角色关联

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
2. `UserRoleAppService` 完成 ID 翻译：`userId` → `subjectTypeCode:USER` + `subjectExternalId:username`
3. `UserRoleAppService` 完成 `roleId` → `roleExternalId`、`roleTypeCode`、`domainCode` 翻译
4. 响应中 `roleTypeLabel` 由代理层映射（或前端按 `roleTypeCode` 查字典）
5. 响应中 `relationOrgName` 由代理层补充

---

## 5. 待核对接口

| 接口 | 服务 | 状态 |
|------|------|------|
| 角色管理 CRUD | permission-center | ⏳ |
| 权限授予查询 | permission-center | ⏳ |
| 权限变更日志 | permission-center | ⏳ |
| 业务域管理 | admin-service | ⏳ |
| 类型定义管理 | admin-service | ⏳ |

---

## 已核对接口汇总

| 状态 | 数量 | 说明 |
|------|------|------|
| ✅ 已对齐 | 8 | org/tree + user/page/create/update/delete + user-org/list/assign/remove/set-primary |
| 🔧 需后端改造 | 4 | user/page(orgId)、user/create(orgId+password)、user-role/list/assign/revoke(代理) |
| ❌ 重大差异 | 0 | |
| ⏳ 待核对 | 5+ | 角色管理、权限授予、变更日志、业务域、类型定义 |
