---
doc_type: design
title: Admin Service 对前端 API 契约（组织与用户域）
status: adopted
domain: admin-service
last_reviewed: 2026-08-23
---

# Admin Service 对前端 API 契约（组织与用户域）

> 状态：`adopted`。本文整体仍是「组织与用户」融合页 HTTP 路径、DTO、错误码和业务行为的兼容基线。**归并后定位（T-ACCESS-012，2026-08-22）**：本文描述的 `/admin/**`、`/auth/**` 契约由 access-service 管理域（admin 域）承载，文件名与路径保留历史叫法；文件名中的 "Admin Service" 指该管理域而非独立服务（原 admin-service 已归并为 access-service，服务设计见 `../access-service-architecture.md`）。
>
> **目标架构（T-ACCESS-005，2026-08-15）**：§3、§4 各写接口的投影动作以及 §6/§7 的内部一致性契约已改为同事务本地权限投影。HTTP 路径、DTO 与错误码继续有效。access 内部不再写 `sys_sync_task`、不再 Feign 自调用。
>
> **术语（T-ACCESS-012）**：本文中「admin / admin-service 层」指 access-service admin 域入口，「permission-center」指同服务 permission 域（本地 `PermQueryEngine`/AppService 调用，无跨服务 HTTP）；历史决策表中的旧服务名表述按此映射阅读，不改变契约本身。
>
> 关联文档:
> - `../project-rules.md` (强约束: 报文/接口/异常/错误码段)
> - `../permission-center/api-contract.md` (业务键, /api/perm/user-role/* 代理调用)
> - `../default-org-tree-user-lifecycle.md` (默认组织树身份目录边界)
> - `../org-user-permission-contract.md` v1.2 (页面门禁与岗位=特殊组织决策)
> - `../../archive/2026-08-22/admin-service.md` (原 admin-service 服务设计，已 superseded；同步任务模型已随 T-ACCESS-005 退役)
> - `../schema/access-service.sql` (字段事实，唯一权威 DDL)
>
> 当前 16 个接口处于"契约已定稿, 待 Phase 2 后端实现"状态; 另 6 个接口与前端 mock 已对齐, 见 §5 已对齐汇总.
>
> 本契约不重复 `project-rules.md` 全文; 仅复述与本契约直接相关的强约束条款 (§1).

---

## 1. 通用约束

引用 `project-rules.md`:

- **§1.1 统一响应壳**: 所有接口返回 `PermResult<T> { code, message, data, requestId, traceId }`. `code=200` 为成功, 失败时 `data=null`. `requestId/traceId` 由网关与 Micrometer Tracing 注入, 业务侧不写入.
- **§1.3 分页**: 入参 `{ pageNum, pageSize, sort? }`, `pageNum>=1`, `1<=pageSize<=100`, `sort` 形如 `"createdAt,desc"`. 出参分页对象统一为 `PaginatedResult<T> { items: T[], total, pageNum, pageSize, hasNext }`. 非分页列表也必须用 `{ items: [...] }` 包装, 禁止顶层数组.
- **§2.1 HTTP 方法**: 所有接口 `POST + application/json + @RequestBody DTO`. 禁止 `@GetMapping/@PutMapping/@DeleteMapping/@PatchMapping`, 禁止 `@RequestParam` (除文件上传/下载), 禁止路径参数. 业务 ID 必须放 JSON Body.
- **§2.2 路径**: access-service admin 域挂载在网关路由 `/admin/api/**` 下, 实际控制器映射为 `/user`, `/org`, `/user-org`, `/user-role`, `/role` 等资源根. 本契约文档中所有路径均为服务内部映射 (前端经网关访问).
- **请求体禁止 `tenantId`**: 服务端统一从 `X-Tenant-Id` Header 与 SecurityContext 读取. 前端经网关后无需感知.
- **§1.2 业务错误码**: access-service 管理域（admin 域）业务错误使用 `10001-19999` 段; 系统公共错误 (参数校验/系统异常) 使用 `90001-99999` 段, 由 `common` 模块统一定义.
- **§3.2 异常**: 业务拒绝 (资源不存在/状态冲突/默认树边界违规等) 抛 `BizException`; 安全拒绝 (操作者身份缺失/权限不足/越权) 抛 `SecurityException`; 技术故障 (DB/RPC/序列化) 抛 `SystemException`. **禁止**用 `SecurityException` 表达"资源不存在"或"参数非法".

---

## 2. 门禁规范

admin 门禁通过 `AdminPermissionValidator` 本地调用 `PermQueryEngine` 完成，不再 Feign 自调用。接口形态:

```java
void checkTypeLevel(String resourceTypeCode, String operationCode);
void checkInstanceLevel(String resourceTypeCode, String resourceCode, String operationCode);
void checkBatchInstanceLevel(String resourceTypeCode, List<String> resourceCodes, String operationCode);
```

> **终态口径（T-ACCESS-016 定稿，2026-08-23）**：门面三方法形态不变，是 `SecurityException` 的唯一出口（引擎纯查询，见 permission-center implementation §3.1）——`checkInstanceLevel` 内部走 `engine.hasPermissionByCode`、`checkBatchInstanceLevel` 内部走 `engine.getDeniedResourceCodes`（实施 T-PERM-042）。业务对象门禁统一**业务编码语义**：`resourceCode` 为业务 ID 字符串（`/user/**` 的 userId、`/org/**` 的 orgId；统一主体 ID 后 `resource_entity(USER).code = sys_user.id = abstract_user.id`，数值与语义一致），不得使用 `resource_entity.id`。下表及各章节资源类型串已按收敛映射切换为 `USER/ORG/ROLE`（access-service-architecture §13 资源类型注册表；常量类已合一为 `ResourceTypeCode`，原 `AdminResourceType` 随 T-ACCESS-018 删除）。

资源类型常量（`ResourceTypeCode`，T-ACCESS-018 合一后单一常量源，原 AdminResourceType 已删除）:

| 常量 | 值 | 说明 |
|------|------|------|
| `ResourceTypeCode.USER` | `USER` | 被管理的用户实例 (resource_entity, code=sys_user.id；原 ADMIN_USER 并入，T-ACCESS-018) |
| `ResourceTypeCode.ORG` | `ORG` | 被管理的组织/岗位实例 (resource_entity, code=sys_org.id；原 ADMIN_ORG 并入) |
| `ResourceTypeCode.ROLE` | `ROLE` | (本契约只读: 仅 /role/list 用；原 ADMIN_ROLE 并入) |

操作码常量 (`AdminOperationCode`): `CREATE / UPDATE / DELETE / VIEW / ENABLE / DISABLE / RESET_PASSWORD / GRANT / REVOKE`.

本契约接口的门禁映射表:

| 接口 | 资源类型 | 资源粒度 | 操作码 | 备注 |
|------|----------|----------|--------|------|
| `/user/page` | `USER` | 类型级 | `VIEW` | 默认树身份目录范围内列表 |
| `/user/member-candidates` | `ORG` | 实例级 (目标 orgId) | `UPDATE` | 仅校验"能管理目标组织的成员"; 候选用户范围由默认树可见性二次裁剪 |
| `/user/create` | `USER` | 类型级 | `CREATE` | 若入参带 orgId, 同时需 `ORG:UPDATE@orgId` |
| `/user/update` | `USER` | 实例级 (userId) | `UPDATE` | 自我修改业务豁免在调用前处理 |
| `/user/delete` | `USER` | 实例级批量 (ids) | `DELETE` | 默认树身份目录边界 |
| `/user/enable` | `USER` | 实例级批量 (ids) | `ENABLE` | 启停共用一码（toggle），按入参 `status` 设置实体字段 |
| `/user/reset-password` | `USER` | 实例级 (userId) | `RESET_PASSWORD` | 默认树身份目录边界 |
| `/user/detail` | `USER` | 实例级 (userId) | `VIEW` | 类型级 VIEW 门禁 + 默认树可见范围裁剪（P1-2：复用 `validateUsersInDefaultTreeScope`，与 `/user/page` 同等约束，防止知道 ID 即可读列表不可见用户；无组织关系用户拒绝）|
| `/org/tree` | `ORG` | 类型级 | `VIEW` 或 `CREATE` | 入参 `operationCode` 决定语义: `VIEW`=可视范围; `CREATE`=新增用户时可选挂载点 (限默认树) |
| `/org/page` | `ORG` | 类型级 | `VIEW` | |
| `/org/users` | `ORG` | 实例级 (orgId) | `VIEW` | |
| `/org/create` | `ORG` | 实例级 (parentOrgId, 顶级时类型级) | `CREATE` | |
| `/org/update` | `ORG` | 实例级 (orgId) | `UPDATE` | 改 `parentOrgId` 等价于"移动", 同时需新父级 `UPDATE` |
| `/org/delete` | `ORG` | 实例级 (orgId) | `DELETE` | |
| `/user-org/list` | `USER` | 实例级 (userId) | `VIEW` | 读用户成员关系视图 |
| `/user-org/assign` | `ORG` | 实例级批量 (orgIds) | `UPDATE` | 关系级追加; 默认树关系受身份目录边界二次校验 |
| `/user-org/remove` | `ORG` | 实例级 (orgId) | `UPDATE` | 非默认树仅删关系并回收对应 user_role; 默认树移除按身份目录高危处理 |
| `/user-org/set-primary` | `ORG` | 实例级 (orgId) | `UPDATE` | 首期仅允许默认组织树主归属 |
| `/user-role/list` | `USER` | 实例级 (userId) | `VIEW` | admin 代理直查; 不再额外要求 `ROLE:MANAGE` |
| `/user-role/assign` | — | — | — | ⛔ 已退役（T-ACCESS-006）：保留映射恒抛 `10111`（`ROLE_API_RETIRED`）；角色分配走 `/api/perm/user-role/assign`（`ROLE:MANAGE` 门禁由 permission 域 enforce）|
| `/user-role/revoke` | — | — | — | ⛔ 已退役（T-ACCESS-006）：同上，走 `/api/perm/user-role/revoke` |
| `/role/list` | `ROLE` | 类型级 | `VIEW` | 仅功能角色 |

> **默认树身份目录边界二次校验**: `/user/create`、`/user/delete`、`/user/enable`、`/user/reset-password`、`/user-org/set-primary` 在通过 `AdminPermissionValidator` 后, AppService 内部还要二次确认目标用户的默认树关系存在 (通过 `sys_user_org` 推导), 且操作者在默认树该子树下具备可见性. 不满足时抛 `BizException(ErrorCode.NOT_IN_DEFAULT_TREE_SCOPE)`. 这一层不能用 `SecurityException` 表达.

---

## 3. 与权限域的本地投影

用户、组织、菜单及成员关系写入由 `access.application` 编排，在同一 PostgreSQL 事务内维护管理事实、本地权限投影和 `permission_change_log`。不再写 `sys_sync_task`，不再 Feign 自调用。见 [`../access-service-architecture.md`](../access-service-architecture.md) §4。

投影定位（稳定外部键，独立主键；`owner_service_code=access-service`；不写 `sync_metadata`）：

| 管理事实 | 投影 | 外部键 |
|----------|------|--------|
| `sys_user` | `abstract_user(LOCAL_USER)` + `resource_entity(USER)` | `external_id` / `code` = `sys_user.id.toString()` |
| `sys_org` | `abstract_role(ORG\|POSITION)` + `resource_entity(ORG)` | `external_id` / `code` = `sys_org.id.toString()` |
| `sys_menu`（DIR/MENU/EXTERNAL/IFRAME/HIDDEN 五值全量投影，T-ACCESS-015） | `resource_entity(MENU)` | `code` = `sys_menu.id.toString()` |
| `sys_user_org` | `user_role` | 主体 `LOCAL_USER` + 角色 `ORG/POSITION` |

> **终态口径（T-ACCESS-016 定稿，2026-08-23）**：① 主体 ID 统一后 `sys_user.id = abstract_user.id`（唯一 ID 源，access-service-architecture §12），`external_id`/`code` 的数值与语义不变（同一 Long 的字符串化）；② 类型码随 §13 注册表收敛：`abstract_user(ADMIN_USER)`→`abstract_user(LOCAL_USER)`（user_type 更名）、`resource_entity(ADMIN_USER/ADMIN_ORG/ADMIN_MENU)`→`resource_entity(USER/ORG/MENU)`；③ 保留业务键终态（§4.3 终态注记）：subject 侧 `ADMIN_USER`→`LOCAL_USER`（无兼容别名）；resource 侧取消类型级保留，本地投影行改按所有权保护（外部 sync UPSERT/DISABLE/DELETE 任一 mutation 分支前置 `owner=access-service` 即 20045、新建撞 code 由唯一索引兜底；USER/MENU 保持公共类型可被外部同步自身资源）。类型串替换已随 T-ACCESS-018 落地（本契约全量切换）；USER/ROLE 投影全写路径补齐已随 T-ACCESS-019 落地（2026-08-23，permission 域 abstract-user/abstract-role 管理入口同事务投影，外部 sync 入口遗留登记）。

保护：权限管理入口与外部 `/api/perm/**/sync|full-sync` 拒绝改写本地投影——所有权检查（`owner=access-service` 即 20045，外部 sync UPSERT/DISABLE/DELETE 任一 mutation 分支前置，T-ACCESS-018 落地）与保留业务键 `LOCAL_USER` / `ORG|POSITION` / `SYS_USER_ORG`（subject 侧原 `ADMIN_USER` 已更名；resource 侧取消类型级保留，管理入口类型保留清单为 `{USER, ORG, MENU, ROLE}`，ROLE 随 T-ACCESS-019 增补），以及内部 `sourceService`。拒绝类型为 `BizException(20045)`。

本契约接口的投影动作：

| 接口 | 主事务 | 投影 |
|------|--------|------|
| `/user/create` | INSERT `sys_user` (+ 可选 `sys_user_org`) | `upsertAdminUser`；若带 orgId：`bindUserOrg` |
| `/user/update` | UPDATE `sys_user` | `upsertAdminUser` |
| `/user/delete` | 软删 `sys_user`，级联清理 `sys_user_org` | 每条关系 `unbindUserOrg`；`deleteAdminUser` |
| `/user/enable` | UPDATE `sys_user.status` | 启用 `upsertAdminUser`；停用 `disableAdminUser` |
| `/user/reset-password` | UPDATE `sys_user.password` | 无（密码不进入权限投影） |
| `/org/create` | INSERT `sys_org` | `upsertAdminOrg` |
| `/org/update` | UPDATE `sys_org` | `upsertAdminOrg` |
| `/org/delete` | 软删 `sys_org`，级联清理 `sys_user_org` | 每条关系 `unbindUserOrg`；`deleteAdminOrg` |
| `/user-org/assign` | INSERT/UPDATE `sys_user_org` | 每个新增关系 `bindUserOrg` |
| `/user-org/remove` | DELETE `sys_user_org` | `unbindUserOrg` |
| `/user-org/set-primary` | UPDATE `sys_user_org.is_primary` | 无（`is_primary` 不映射 `user_role` 拓扑） |
| `/user-role/assign`, `/user-role/revoke` | 恒拒绝 `10111`（已退役，无投影动作） | 角色分配/回收由 `/api/perm/user-role/*` 直接提供 |

> 功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）继续走 `/user-role/*` 正式管理 API。组织/岗位角色只能由组织与成员关系写入投影产生，`createRoleForOrg` 与针对保留角色类型的菜单授权一律拒绝。

---

## 4. 接口契约

### 4.1 用户管理 (`/user`)

#### 4.1.1 `POST /user/page` 🔧

**目的**: 默认组织树身份目录视角下的用户分页查询. 用于「组织与用户」页左侧选中组织或岗位后的用户列表.

**请求 DTO**: `UserPageReq` (`record`)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pageNum` | `Integer` | 否 | 默认 1, 最小 1 |
| `pageSize` | `Integer` | 否 | 默认 20, 范围 1-100 |
| `sort` | `String` | 否 | 形如 `"createdAt,desc"` |
| `username` | `String` | 否 | 模糊匹配 (默认树范围内) |
| `name` | `String` | 否 | 模糊匹配 |
| `phone` | `String` | 否 | 模糊匹配 |
| `email` | `String` | 否 | 模糊匹配 |
| `status` | `Integer` | 否 | 1=启用, 0=停用（与 DDL `sys_user.status` 一致） |
| `orgId` | `Long` | 否 | 选中组织/岗位 ID; 不传时返回操作者在默认树内可见的全部用户; 传时仅返回直接挂在该组织 (含子树, 视实现决策) 的成员 |

**响应 DTO**: `PaginatedResult<UserPageItemResp>`, `items[]` 字段:

`UserPageItemResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | sys_user.id |
| `username` | `String` | 登录账号 |
| `name` | `String` | 显示名 |
| `phone` | `String` | |
| `email` | `String` | |
| `status` | `Integer` | 1=启用, 0=停用（与 DDL `sys_user.status` 一致） |
| `orgs` | `List<OrgBrief>` | 用户所属组织简表 |
| `createdAt` | `LocalDateTime` | |

`UserPageItemResp.OrgBrief`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `orgId` | `Long` | sys_org.id |
| `orgName` | `String` | |
| `orgType` | `String` | 字典值 (Phase 2 决策: 后端固定为 String 字典编码; 前端 `OrgBrief.orgType` 可选) |
| `isPrimary` | `boolean` | 是否主组织 |

**门禁**: `USER:VIEW` 类型级 + 默认树可见范围裁剪 (操作者只能看到默认树中其有 `ORG:VIEW` 的子树成员).

**同步动作**: 无 (只读)

**错误码段**: 10001-10099 (用户查询)

**当前差距 (来自 api-gap-analysis，已归档 `docs/archive/2026-06-21/`)**: 需要明确语义为"默认组织树身份目录查询"; 区别于添加组织成员时的候选用户查询 (后者改用 §4.1.2 `/user/member-candidates`). 该差距已由 admin-service 实现收口。

**验收要点**:
- 操作者无 `USER:VIEW` 时返回空列表 + `code=200` (不抛 SecurityException).
- 操作者在默认树中无任一可见组织时返回空列表.
- `orgId` 落在非默认树时返回 `BizException(ErrorCode.ORG_NOT_IN_DEFAULT_TREE)`; 该接口语义只服务身份目录视图.

---

#### 4.1.2 `POST /user/member-candidates` 🔧 (新增)

**目的**: 给非默认组织/岗位添加成员时, 查询候选用户. 候选范围 = 默认组织树中操作者可见 ∩ 排除目标组织已有成员.

**请求 DTO**: `MemberCandidatesReq` (`record`, 新增)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `targetOrgId` | `Long` | 是 | 目标组织/岗位 ID (用户即将被加入的组织, 通常属于非默认树) |
| `pageNum` | `Integer` | 否 | 默认 1 |
| `pageSize` | `Integer` | 否 | 默认 20, 范围 1-100 |
| `keyword` | `String` | 否 | 关键字 (按 username/name/phone/email 模糊匹配) |

**响应 DTO**: `PaginatedResult<MemberCandidateItemResp>`

`MemberCandidateItemResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | sys_user.id |
| `username` | `String` | |
| `name` | `String` | |
| `avatar` | `String` | 可选 |
| `primaryOrgName` | `String` | 默认树主归属组织名, 便于识别 |
| `alreadyAssigned` | `Boolean` | 固定 false (服务端已过滤; 字段保留用于一致性) |

**门禁**: `ORG:UPDATE@targetOrgId` (实例级, 校验"能管理目标组织成员").

**同步动作**: 无 (只读)

**错误码段**: 10100-10119

**当前差距**: 接口未实现. 当前前端 mock 复用 `/user/page`, 但语义与默认树身份目录查询不同, 需独立接口.

**验收要点**:
- 候选集**严格**来自默认树中操作者具备 `USER:VIEW` (或 `ORG:VIEW`) 的范围; 不暴露全租户用户.
- 必须排除目标组织已通过 `sys_user_org` 直接关联的用户.
- `targetOrgId` 不存在或已删除时抛 `BizException`.

---

#### 4.1.3 `POST /user/create` 🔧

**目的**: 在默认组织树身份目录中新建用户; 可选一步完成组织挂载. 系统生成随机初始密码, 仅本次响应返回.

**请求 DTO**: `UserCreateReq` (现有)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | `String` | 是 | 登录账号, 租户内唯一 |
| `name` | `String` | 是 | 显示名 |
| `phone` | `String` | 否 | |
| `email` | `String` | 否 | |
| `status` | `Integer` | 否 | 默认 1 (启用)；1=启用, 0=停用（T-ACCESS-021 修正：原文「0=正常,1=禁用」与 DDL/启停接口语义矛盾）；仅接纳 0/1，其它值抛 `BizException(INVALID_PARAM)`（T-ADMIN-022 评审修复：create 为 status 写入口，与 update/enable 同口径） |
| `orgId` | `Long` | 否 | 创建时一步完成挂载; **必须**属于默认组织树, 否则抛 `BizException(ORG_NOT_IN_DEFAULT_TREE)` |
| `primaryOrg` | `Boolean` | 否 | 仅当 `orgId` 非空时生效, 默认 true |

**响应 DTO**: `UserCreateResp`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 新用户 ID |
| `initialPassword` | `String` | 系统生成的随机初始密码明文, **仅本次返回** |

**门禁**: `USER:CREATE` 类型级 (+ 若带 `orgId` 还需 `ORG:UPDATE@orgId`).

**投影动作**:
1. 主事务: INSERT `sys_user` (+ 可选 INSERT `sys_user_org`)
2. `LocalProjectionDomainService.upsertAdminUser`
3. 若带 `orgId`: `bindUserOrg`
4. 同事务写 `permission_change_log`；缓存失效仅在提交后发生

**错误码段**: 10120-10149

**当前差距**: 默认树校验与初始密码返回已由实现覆盖；内部 ID 不再回填前端。

**验收要点**:
- `username` 在租户内重复时抛 `BizException(USERNAME_DUPLICATED)`.
- `orgId` 非默认树时抛 `BizException(ORG_NOT_IN_DEFAULT_TREE)`.
- `initialPassword` 必须非空且强度满足策略 (8-32 位, 可配置).
- 管理事实、投影与 `permission_change_log` 必须同事务；任一失败整体回滚.

---

#### 4.1.4 `POST /user/update` ✅

**目的**: 更新用户基本资料. 状态字段可写但建议改用 `/user/enable` 启停一体.

**请求 DTO**: `UserUpdateReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `Long` | 是 | sys_user.id |
| `name` | `String` | 否 | |
| `phone` | `String` | 否 | |
| `email` | `String` | 否 | |
| `status` | `Integer` | 否 | 1=启用, 0=停用；仅接纳 0/1，其它值抛 `BizException(INVALID_PARAM)`（T-ADMIN-022 语义收口，与 DDL `sys_user.status` 单一口径） |

**响应**: `PermResult<Void>`

**门禁**: `USER:UPDATE@id` (实例级). 自我修改业务豁免在 AppService 调用门禁前判断 (operatorId == id 时跳过门禁).

**投影动作**: 同事务 `upsertAdminUser`（名称/状态变化一并投影）。

**错误码段**: 10150-10169

**验收要点**: 投影与管理事实同事务提交；回滚不发布缓存失效。

---

#### 4.1.5 `POST /user/delete` 🔧

**目的**: 批量软删除用户. 高危身份目录操作, 仅默认组织树管理员可执行.

**请求 DTO**: `IdsReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ids` | `List<Long>` | 是 | sys_user.id 列表 |

**响应**: `PermResult<Void>`

**门禁**: `USER:DELETE` 实例级批量 (`checkBatchInstanceLevel(USER, ids, DELETE)`) + 默认树边界二次校验 (操作者必须在每个目标用户的默认树主归属子树下具备 `ORG:UPDATE`).

**投影动作** (每个 id):
1. 主事务: 软删 `sys_user`, 级联软删 `sys_user_org`
2. 每条 user-org 关系 → `unbindUserOrg`
3. `deleteAdminUser`

**错误码段**: 10170-10189

**当前差距**: 默认树边界校验由 `UserWriteAppService` 执行。

**验收要点**:
- 批量中任一用户不在操作者默认树可管范围抛 `BizException(NOT_IN_DEFAULT_TREE_SCOPE)`, 整批回滚.
- 软删后 `username` 在租户内可被新用户复用 (业务策略, 与现状一致).
- 不允许删除当前操作者本人 → `BizException(CANNOT_DELETE_SELF)`.

---

#### 4.1.6 `POST /user/enable` 🔧

**目的**: 批量启用/停用用户 (启停一体, 管理员手工启停). `status=1` 启用, `status=0` 停用. 高危生命周期操作.

> 设计决策: 启停**不**拆为 `/user/enable` + `/user/disable` 双接口, 沿用现有 `UserUpdateStatusReq(ids, status)` 形态; 在 AppService 内部按 `status` 动态选择门禁操作码 (`ENABLE` vs `DISABLE`).
>
> **status 语义单一口径（T-ADMIN-022）**: `sys_user.status` 仅 0(停用)/1(启用)；登录失败临时锁定不落库（Redis 失败计数键剩余 TTL 即锁定时长，键过期自动恢复），历史 `status=2` 已删除。

**请求 DTO**: `UserUpdateStatusReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ids` | `List<Long>` | 是 | 用户 ID 列表 |
| `status` | `Integer` | 是 | 1=启用, 0=停用 |

**响应**: `PermResult<Void>`

**门禁**:
- `status=1`: `USER:ENABLE` 实例级批量
- `status=0`: `USER:ENABLE（toggle）` 实例级批量
- 加默认树边界二次校验 (与 §4.1.5 同).

**投影动作** (每个 id):
1. 主事务: UPDATE `sys_user.status`
2. 启用 → `upsertAdminUser`；停用 → `disableAdminUser`

**错误码段**: 10190-10209

**当前差距**: 门禁码按 status 派发与默认树二次校验由 `UserWriteAppService` 执行。

**验收要点**:
- 不允许停用操作者本人 → `BizException(CANNOT_DISABLE_SELF)`.
- 非默认树成员管理员调用此接口必须被门禁拦截 (因其无 `USER:ENABLE`（启停共用一码，v1.4 DISABLE 已并入）).

---

#### 4.1.7 `POST /user/reset-password` 🔧

**目的**: 管理员重置用户密码. 不传 `newPassword` 时由系统生成随机密码, 明文仅本次返回. 高危生命周期操作.

**请求 DTO**: `ResetPasswordReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `newPassword` | `String` | 否 | 长度 8-32; 不传则系统生成 |

**响应 DTO**: `ResetPasswordResp`

| 字段 | 类型 | 说明 |
|------|------|------|
| `newPassword` | `String` | 生效的密码明文, **仅本次返回** |

**门禁**: `USER:RESET_PASSWORD@userId` 实例级 + 默认树边界二次校验.

**同步动作**: 无 (密码不进入 permission-center). 重置成功后 `sys_user.force_reset_pwd` 置 `true`（DDL 语义「首次登录/管理员重置后须改密」——T-ADMIN-022 二轮评审修复，登录页据此提示联系管理员；系统无自助改密通道）.

**错误码段**: 10210-10229

**当前差距**: 现有实现已具备 `newPassword` 可选 + `ResetPasswordResp` 返回; 待补默认树边界校验.

**验收要点**:
- 操作者本人重置自己密码走另外的"修改密码"接口, 不复用本接口.
- 自定义 `newPassword` 不满足策略时抛 `BizException(PASSWORD_TOO_WEAK)`.

---

### 4.2 组织管理 (`/org`)

#### 4.2.1 `POST /org/tree` ✅

**目的**: 返回组织层级树. 入参 `operationCode` 控制语义: `VIEW`=可视范围, `CREATE`=作为新增用户挂载点 (限默认树).

**请求 DTO**: `OrgQuery` (现有 record + 待新增 `operationCode`/`treeConfigId` 字段)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `operationCode` | `String` | 否 | `VIEW` 或 `CREATE`; 不传时按 `VIEW` 处理。**`includePositions=true` 时仅支持 `VIEW`（P2-1 ：CREATE + 混合树 → 参数校验失败——岗位裁剪固定检查 VIEW_POSITION，CREATE 混合树会形成 CREATE+VIEW_POSITION 混合门禁；CREATE 场景保持 `orgType=1` 单类型树，不需要岗位节点）** |
| `treeConfigId` | `Long` | 否 | 组织树配置 ID; 不传则返回默认树 |
| `orgName` | `String` | 否 | 模糊匹配 |
| `orgType` | `Integer` | 条件必填 | 1=组织, 2=岗位；**`includePositions != true` 时必填**（缺失 → `ORG_TYPE_REQUIRED`，保留现有业务码；后端 `treeOrgs` 按 orgType 分发 `VIEW/VIEW_POSITION` 门禁，放开空值会在单一门禁下返回全部类型，P1-2）；**`includePositions=true` 时忽略本字段（一体树语义，岗位裁剪由 hasTypeLevel 独立门控）** |
| `includePositions` | `Boolean` | 否 | **T-ADMIN-021 新增（2026-08-01 评审 P1-6）**；默认 `false` 行为与现状完全一致；`true` 时返回组织+岗位一体树：岗位（orgType=2）作为所属组织（orgType=1）的**子节点**挂入同一树（岗位自身无下级），**忽略 `orgType` 单类型过滤** |
| `status` | `Integer` | 否 | |
| `parentOrgId` | `Long` | 否 | 用于查询子树; 一般不与 `treeConfigId` 同时使用 |

**响应（P1-3 定稿）**: `PermResult<OrgItemsResp>`，`data.items[]`（`OrgItemsResp{ items: List<OrgResp> }`）；**唯一形状，不再返回裸数组**（历史直返 List 已废弃；包装改造由 **T-ADMIN-021** 落地，见合规债务清单）

`OrgResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | |
| `orgType` | `Integer` | 1=组织, 2=岗位 (字典值) |
| `orgName` | `String` | |
| `parentOrgId` | `Long` | null=顶级 |
| `code` | `String` | |
| `phone` | `String` | |
| `email` | `String` | |
| `status` | `Integer` | 0/1 |
| `sort` | `Integer` | |
| `createdAt` | `LocalDateTime` | |
| `updatedAt` | `LocalDateTime` | |
| `children` | `List<OrgResp>` | 树形 |

**门禁**: `ORG:{operationCode}` 类型级.

**岗位节点裁剪（T-ADMIN-021）**: `includePositions=true` 时，岗位节点（orgType=2）按调用者岗位权限**后端裁剪**——调用者仅具备 `ORG:VIEW`（无 `ORG:VIEW_POSITION`）时响应不包含任何岗位节点；裁剪判定用非抛出入口 `hasTypeLevel(ORG, VIEW_POSITION)`（**仅明确拒绝返回 false；permission-center 技术故障抛异常向上，不得静默降级为裁剪后的树**，P2-1）。前端隐藏不作为安全边界。

**同步动作**: 无.

**当前差距** (合规债务): ① `OrgQuery` 当前 record 缺 `operationCode` 与 `treeConfigId` 字段; ② 顶层响应应改为 `{ items: [...] }` 包装. **②已由 T-ADMIN-021 消化（2026-08-01 P1-3：响应定稿 `PermResult<OrgItemsResp>{data:{items}}`，含调用方适配）**; ① 仍列入 Phase 2 修正项.

---

#### 4.2.2 `POST /org/page` ✅

**目的**: 平铺分页查询组织/岗位列表. 岗位 Tab 通过 `orgType=2` + `orgId=选中组织` 实现子树筛选.

**请求 DTO**: `OrgPageReq` (现有)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pageNum` | `Integer` | 否 | 默认 1 |
| `pageSize` | `Integer` | 否 | 默认 20 |
| `sort` | `String` | 否 | |
| `orgName` | `String` | 否 | 模糊匹配 |
| `orgType` | `Integer` | 否 | 1=组织, 2=岗位 |
| `status` | `Integer` | 否 | |
| `parentOrgId` | `Long` | 否 | 直接父级 |
| `orgId` | `Long` | 否 | 子树根; 传入时返回该组织及其全部子孙 (岗位 Tab 用) |

**响应**: `PaginatedResult<OrgResp>` (children 字段为空数组, 平铺语义)

**门禁**: `ORG:VIEW`.

---

#### 4.2.3 `POST /org/users` ✅

**目的**: 查询组织/岗位下通过 `sys_user_org` 关联的用户列表. 岗位卡片展开用.

**请求 DTO**: `IdReq` (公共 record)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `Long` | 是 | 组织/岗位 ID |

> 前端 mock 实际传 `{ orgId }`, 与后端 `IdReq.id` 不一致. **决策**: 以 `IdReq.id` 为准, 前端在 Phase 2 调整 mock 字段名为 `id`. 列入 Phase 2 前端调整项.

**响应**: `PermResult<List<OrgUserItemResp>>` (Phase 2 改为 `{ items: [...] }` 包装)

`OrgUserItemResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | `Long` | |
| `username` | `String` | |
| `name` | `String` | |
| `avatar` | `String` | 可选 |
| `isPrimary` | `Boolean` | 是否主组织 |

**门禁**: `ORG:VIEW@id`.

---

#### 4.2.4 `POST /org/create` 🔧

**目的**: 创建组织或岗位 (`orgType=1` 组织, `orgType=2` 岗位).

**请求 DTO**: `OrgCreateReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `orgType` | `Integer` | 是 | 1=组织, 2=岗位 |
| `orgName` | `String` | 是 | |
| `parentOrgId` | `Long` | 否 | null=顶级; 顶级仅允许在默认树根 (业务策略) |
| `code` | `String` | 否 | 租户内唯一 (有值时) |
| `status` | `Integer` | 否 | 默认 0 |
| `sort` | `Integer` | 否 | |

> 评审 P2（2026-08-15）：组织 `phone`/`email` 字段已从契约/请求 DTO/响应模型删除——`sys_org` 实体与表不含联系方式字段（声明必须生效）。

**响应**: `PermResult<Long>` (新组织 ID)

**门禁**:
- 顶级 (`parentOrgId=null`): `ORG:CREATE` 类型级
- 子级: `ORG:UPDATE@parentOrgId` 实例级 (在父级下添加子节点等价于"修改父级结构")

**投影动作**:
1. 主事务: INSERT `sys_org`
2. `upsertAdminOrg`（`roleTypeCode=ORG` 或 `POSITION` 视 `orgType`）

**错误码段**: 10300-10329

**当前差距**: 组织写入已由 `OrgWriteAppService` 同事务投影。

**验收要点**:
- `code` 重复抛 `BizException(ORG_CODE_DUPLICATED)`.
- `parentOrgId` 不存在或已删除抛 `BizException(PARENT_ORG_NOT_FOUND)`.
- 跨树 `parentOrgId` 抛 `BizException(CROSS_TREE_PARENT_FORBIDDEN)`.

---

#### 4.2.5 `POST /org/update` 🔧

**目的**: 更新组织属性. 改 `parentOrgId` 等价于"移动子树".

**请求 DTO**: `OrgUpdateReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `Long` | 是 | |
| `orgName` | `String` | 否 | |
| `parentOrgId` | `Long` | 否 | 改动等价于移动 |
| `code` | `String` | 否 | |
| `status` | `Integer` | 否 | |
| `sort` | `Integer` | 否 | |

**响应**: `PermResult<Void>`

**门禁**: `ORG:UPDATE@id` 实例级. 若 `parentOrgId` 变化, 还需 `ORG:UPDATE@新parentOrgId`.

**投影动作**:
1. 主事务: UPDATE `sys_org`
2. `upsertAdminOrg`
3. 若 `parentOrgId` 变化：由组织投影更新父子关系；成员 `user_role` 的 relationKey 随本地投影维护，不再拆外部 envelope。

**错误码段**: 10330-10359

**验收要点**:
- 不允许把组织移到自己的子树下 (`BizException(CIRCULAR_PARENT)`).
- 不允许跨树移动 (`BizException(CROSS_TREE_MOVE_FORBIDDEN)`).

---

#### 4.2.6 `POST /org/delete` 🔧

**目的**: 软删除组织 (单条). 子组织非空时拒绝.

**请求 DTO**: `IdReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `Long` | 是 | sys_org.id |

**响应**: `PermResult<Void>`

**门禁**: `ORG:DELETE@id` 实例级.

**投影动作**:
1. 主事务: 软删 `sys_org` (delete_flag=1), 级联软删 `sys_user_org`
2. 每条被清理的 user-org → `unbindUserOrg`
3. `deleteAdminOrg`

**错误码段**: 10360-10389

**验收要点**:
- 存在未删除子组织 → `BizException(ORG_HAS_CHILDREN)`.
- 默认树根节点不允许删除 → `BizException(CANNOT_DELETE_DEFAULT_ROOT)`.

---

### 4.3 用户-组织关系 (`/user-org`)

#### 4.3.1 `POST /user-org/list` ✅

**目的**: 查询用户所属组织列表 (含主组织标记).

**请求 DTO**: `IdReq` (`{ id: userId }`)

**响应**: `PermResult<List<UserPageItemResp.OrgBrief>>` (Phase 2 包装为 `{ items: [...] }`)

| 字段 | 类型 | 说明 |
|------|------|------|
| `orgId` | `Long` | |
| `orgName` | `String` | |
| `orgType` | `String` | 字典值 |
| `isPrimary` | `boolean` | |

**门禁**: `USER:VIEW@userId`.

---

#### 4.3.2 `POST /user-org/assign` 🔧

**目的**: 给用户**追加**组织关系 (关系级精确变更, 禁止跨树 wipe). 可选同时设置主组织.

**请求 DTO**: `UserOrgAssignReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `orgIds` | `List<Long>` | 是 | 待追加的组织 ID 列表 (允许已存在的关系幂等忽略) |
| `primaryOrgId` | `Long` | 否 | 主组织; 必须落在 `orgIds` 内或用户已有关系内; 默认树边界校验 |

**响应**: `PermResult<Void>`

**门禁**: 对 `orgIds` 中每个组织实例分别 `ORG:UPDATE@orgId` (`checkBatchInstanceLevel`).

**写入语义** (关键决策):
- **追加**已存在的关系幂等忽略, 不删除用户在其他组织树的关系.
- **禁止**"先 wipe 再 batch insert"模式; 必须按 `(userId, orgId)` 对增量比较.
- 如果 `primaryOrgId` 非空: 仅在 `primaryOrgId` 所属组织树内将其设为主, **不**清除用户在其他树的 `is_primary` 标记 (首期仅默认树主归属生效, 见 §4.3.4).

**投影动作**:
- 每个**新增**关系 → `bindUserOrg`
- 已存在关系 → 不重复投影

**错误码段**: 10400-10429

**当前差距**: 关系级追加由 `UserOrgWriteAppService` 执行；不再拆外部 sync envelope。

**验收要点**:
- `userId` 在默认树有归属时方可追加非默认树关系; 未在默认树时抛 `BizException(USER_NOT_IN_DEFAULT_TREE)`.
- 候选 `orgIds` 中含已删除组织 → `BizException(ORG_NOT_FOUND)`.
- 任一 `orgId` 操作者无 `ORG:UPDATE` → `SecurityException`, 整批回滚.

---

#### 4.3.3 `POST /user-org/remove` 🔧

**目的**: 移除单条 user-org 关系. 默认树关系按身份目录高危处理.

**请求 DTO**: `UserOrgRemoveReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `orgId` | `Long` | 是 | |

**响应**: `PermResult<Void>`

**门禁**:
- 非默认树关系: `ORG:UPDATE@orgId`
- 默认树关系: `USER:UPDATE@userId` (按身份目录边界, 等同"移动用户默认归属")

**投影动作**:
- DELETE `sys_user_org` (单条) → `unbindUserOrg`

**错误码段**: 10430-10459

**当前差距**: 现有实现未区分默认树/非默认树门禁; 需新增分支判断.

**验收要点**:
- 移除后用户默认树关系归 0 时抛 `BizException(USER_LOSE_DEFAULT_TREE_HOME)` — 默认树主归属不可被普通组织成员管理员意外清除.
- 被移除关系若是该树内 `is_primary`, 必须同时把同树另一关系提升为主 (业务策略: 选 sort 最小的; 若该树仅此一条则按上一条规则拒绝).

---

#### 4.3.4 `POST /user-org/set-primary` 🔧

**目的**: 设置用户主组织. 首期**仅允许**默认组织树主归属.

**请求 DTO**: `UserOrgSetPrimaryReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `orgId` | `Long` | 是 | 目标主组织 ID, **必须**位于默认组织树 |

**响应**: `PermResult<Void>`

**门禁**: `ORG:UPDATE@orgId` + 默认树边界校验.

**写入语义**:
- 仅在默认树内将 `(userId, orgId)` 的 `is_primary=true`, 同时把该用户在默认树的其他关系置 `is_primary=false`.
- **禁止**清除其他组织树的 `is_primary` 标记 (即便是历史脏数据, 也由专门 `is_primary` 修复迁移负责, 不通过本接口).

**投影动作**: 无（`is_primary` 是 admin 内自管字段，不映射 `user_role` 拓扑）。

**错误码段**: 10460-10479

**当前差距**: 现有实现未限制必须为默认树; 需补.

**验收要点**:
- `orgId` 不在默认树 → `BizException(PRIMARY_MUST_BE_IN_DEFAULT_TREE)`.
- 用户与 `orgId` 不存在关联 → `BizException(USER_ORG_RELATION_NOT_FOUND)`.

---

### 4.4 用户-角色 (`/user-role`)

> **核心决策（T-ACCESS-006 修订）**: 合并后角色管理由 permission 域直接提供（`/api/perm/user-role/*`），admin 侧不再维护角色代理。`/user-role/list` 保留为读接口（经 `access.application.query` 的 `UserRoleQueryService` 聚合，POSITION 补所属组织名）；`/user-role/assign`、`/user-role/revoke` 退役——保留映射但恒抛 `10111`（`ROLE_API_RETIRED`），前端请改用 `/api/perm/user-role/assign|revoke`（门禁 `ROLE:MANAGE` 由 permission 域 enforce）。
> 接口使用业务键 `(roleTypeCode, roleExternalId)` 标识角色。仅服务功能角色 (BASIC_ROLE/GROUP_ROLE/PERSONAL); 排除 ORG/POSITION (后者走 /user-org/*)。

#### 4.4.1 `POST /user-role/list` 🔧

**目的**: 查询用户已分配的角色列表 (含组织/岗位/功能角色全集, 由 admin 代理拼接).

**请求 DTO**: `UserRoleListReq` (新增)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | sys_user.id |

**响应**: `PermResult<{ items: UserRoleItemResp[] }>`

`UserRoleItemResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `roleTypeCode` | `String` | `ORG` / `POSITION` / `PERSONAL` / `GROUP_ROLE` / `BASIC_ROLE` |
| `roleExternalId` | `String` | 角色业务键（`/api/perm/user-role/*` 消费） |
| `roleName` | `String` | 角色名 |
| `roleTypeLabel` | `String` | 显示名 (代理层映射) |
| `targetType` | `String` | (前端展示用, 同 `roleTypeCode`) |
| `relationId` | `Long` | POSITION 角色对应的所属组织 abstract_role.id（permission-center 内部主键）; 其他类型为 null. 内部参考字段, 前端不直接消费 |
| `relationExternalId` | `String` | POSITION 角色对应的所属组织业务键（= sys_org.id 字符串，permission-center 返回）; admin 据此解析组织名（P2-1：替代用 relationId 错查 sys_org）|
| `relationOrgName` | `String` | POSITION 角色对应的所属组织名 (代理层用 relationExternalId 查 sys_org 补) |
| `validFrom` | `LocalDateTime` | |
| `validTo` | `LocalDateTime` | |

**门禁**: `USER:VIEW@userId` (admin-service 层); permission-center 层不再额外要求 (本接口为读).

**代理动作（T-ACCESS-006 修订）**: `UserRoleQueryService`（`access.application.query`）经专用 QueryMapper 读取 `user_role ⨝ abstract_role`（有效期窗口过滤），再批量查 `sys_org` 补 `relationOrgName`。返回业务键 `(roleTypeCode, roleExternalId)` 替代 roleId。

**错误码段**: 10500-10519

**当前差距**: 接口已由本地代理实现。

**验收要点**:
- `userId` 不存在 → `BizException(USER_NOT_FOUND)`.
- 前端通过 `(roleTypeCode, roleExternalId)` 业务键回传 assign/revoke，不再使用 roleId.

---

#### 4.4.2 `POST /user-role/assign` ⛔ 退役

> **T-ACCESS-006 退役**: 保留映射但恒抛 `10111`（`ROLE_API_RETIRED`）。角色分配由 permission 域 `/api/perm/user-role/assign` 直接提供（`ROLE:MANAGE` 门禁由 permission 域 `UserManageAppServiceImpl.assignRole` 经 `getDeniedIds(ROLE, MANAGE)` 强制）。

**目的**: 给用户分配功能角色 (BASIC_ROLE/GROUP_ROLE/PERSONAL).（退役前语义：admin 代理本地调用 `UserManageAppService.assignRole`）

**请求 DTO**: `UserRoleAssignReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `roleTypeCode` | `String` | 是 | 必须为 BASIC_ROLE/GROUP_ROLE/PERSONAL, 否则拒绝 |
| `roleExternalId` | `String` | 是 | 角色业务键 |
| `validFrom` | `LocalDateTime` | 否 | |
| `validTo` | `LocalDateTime` | 否 | |

**响应**: `PermResult<Void>`

**门禁**: `ROLE:MANAGE@roleExternalId` — **由 permission 域引擎兜底**（admin 入口不做预检，P1-1 修复：admin 预检曾把 roleExternalId 当 ROLE resource_entity.code 传 auth/check，而 ROLE 权限实际挂 abstract_role.id 维度，预检语义错位会误拒；permission 域 `UserManageAppServiceImpl.assignRole` 用正确 abstract_role.id 经 `getDeniedIds(ROLE, MANAGE)` 校验）。

**代理动作**:
1. 校验 `roleTypeCode∈{BASIC_ROLE, GROUP_ROLE, PERSONAL}` (若为 ORG/POSITION → `BizException`，应走 /user-org/*).
2. 翻译 `userId → subjectTypeCode=LOCAL_USER, subjectExternalId={userId}`; 直接用入参 `(roleTypeCode, roleExternalId)`.
3. 本地调用 `UserManageAppService.assignRole`.

**错误码段**: 10520-10549

**当前差距**: 接口已由本地代理实现；保留角色类型拒绝。

**验收要点**:
- `roleTypeCode` 为 ORG/POSITION → `BizException(ROLE_TYPE_NOT_SUPPORTED)`.
- permission 域引擎校验拒绝 → 透传错误码与 message; admin 入口不吞错（本地调用，无跨服务 HTTP）.

---

#### 4.4.3 `POST /user-role/revoke` ⛔ 退役

> **T-ACCESS-006 退役**: 保留映射但恒抛 `10111`（`ROLE_API_RETIRED`）。角色回收由 permission 域 `/api/perm/user-role/revoke` 直接提供（`ROLE:MANAGE` 门禁由 permission 域 enforce）。

**目的**: 回收用户的功能角色.（退役前语义：admin 代理本地调用 `UserManageAppService.revokeRolesBatch`）

**请求 DTO**: `UserRoleRevokeReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `roleTypeCode` | `String` | 是 | 同 assign 约束 |
| `roleExternalId` | `String` | 是 | 角色业务键 |

**响应**: `PermResult<Void>`

**门禁**: `ROLE:MANAGE@roleExternalId` — **由 permission 域引擎兜底**（同 assign，P1-1 修复；permission 域 `revokeRolesBatch` 用 abstract_role.id 经 `getDeniedIds(ROLE, MANAGE)` 校验）。

**代理动作**:
1. 校验 `roleTypeCode` (同 assign).
2. 本地调用 `UserManageAppService.revokeRolesBatch`.

**错误码段**: 10550-10579

**当前差距**: 接口已由本地代理实现；保留角色类型拒绝。

---

### 4.5 角色查询 (`/role`)

#### 4.5.1 `POST /role/list` ✅

**目的**: 查询功能角色候选列表. 默认仅返回 `BASIC_ROLE / GROUP_ROLE / PERSONAL`, 排除 `ORG / POSITION`.

**请求 DTO**: `RoleController.RoleListQueryReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `roleTypeCodes` | `List<String>` | 否 | 不传时默认 `[BASIC_ROLE, GROUP_ROLE, PERSONAL]` |

**响应**: `PermResult<{ items: RoleListItemResp[] }>`

`RoleListItemResp`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `roleTypeCode` | `String` | 角色类型编码（BASIC_ROLE / GROUP_ROLE / PERSONAL） |
| `roleExternalId` | `String` | 角色业务键（`/api/perm/user-role/*` 消费） |
| `roleName` | `String` | 角色名称 |
| `roleTypeLabel` | `String` | 角色类型显示名 |

**门禁**: `ROLE:VIEW`.

**数据来源**: 本地经 `application.query`（`UserRoleQueryService` 直读 `user_role ⨝ abstract_role` 跨域只读）按 `roleTypeCodes` 过滤, 无跨服务调用.

---

### 4.6 菜单管理 (`/menu`) 🔧 (T-ACCESS-015 新增, v3.5 菜单零权限化终态)

> 菜单表仅承载 UI 路由元数据与关联资源 link，不承载权限语义（`sys_menu` 权威 DDL 见 `../schema/access-service.sql`；设计语义见 `../permission-center-v3.5-design.md` §2.1/§4.1）。按钮级权限由 OperationPermission（L1）承担，不再挂菜单。前端登录菜单聚合走 `/auth/user-menu`（v3.5 §5 单 RPC 契约），与本节管理接口分离。前端菜单管理页尚未开发（views/system 无 menu 页面），本节契约为先行定稿，无现存消费方破坏面。

#### 4.6.1 `POST /menu/create` 🔧

**请求 DTO**: `MenuCreateReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `menuType` | `String` | 是 | 枚举 `DIR/MENU/EXTERNAL/IFRAME/HIDDEN`（v3.5 五值，BUTTON 已移除） |
| `displayName` | `String` | 是 | 最长 128 |
| `parentId` | `Long` | 否 | null/0 表示顶级 |
| `path` | `String` | 否 | 最长 256；EXTERNAL/IFRAME 为外链 URL；租户内唯一（10205） |
| `icon` | `String` | 否 | 最长 64 |
| `sortOrder` | `Integer` | 否 | 默认 0，升序 |
| `status` | `Integer` | 否 | `1=ENABLED`（默认）/`0=DISABLED`，对齐 DDL |
| `resourceType` | `String` | 否 | 与 `resourceCode` 成对（同填或同空，校验失败 90001） |
| `resourceCode` | `String` | 否 | 关联资源实例；租户内同一资源仅可挂一个菜单（10206） |
| `sourceService` | `String` | 否 | 业务服务标识，缺省 `access-service`（管理端创建） |

**响应**: `PermResult<Long>`（新菜单 ID）。

**错误**: `10201` 菜单不存在（含正数 `parentId` 指向的父菜单不存在）/ `10203` 深度超限 / `10205` 路径已存在 / `10206` 资源关联已被占用 / `10207` 父菜单为自身或后代 / `90001` 成对校验失败。

**门禁**: `MENU:CREATE`（类型级）。

#### 4.6.2 `POST /menu/update` 🔧

**请求 DTO**: `MenuUpdateReq`：同 `MenuCreateReq` 全部字段均可选（null 跳过保留原值）+ 必填 `id`；`sourceService` 不可更新（创建期追溯标识）。

**响应**: `PermResult<Void>`。

**错误**: `10201` 菜单不存在（含目标 `parentId` 不存在）/ `10203` 深度超限（换父按整棵子树）/ `10205` / `10206`（排除自身的冲突预查 + 唯一索引兜底）/ `10207` 父菜单为自身或后代（防环）。

**门禁**: `MENU:UPDATE`（实例级，按 sys_menu.id）。

#### 4.6.3 `POST /menu/delete` 🔧

**请求 DTO**: `IdReq`（`id`）。

**响应**: `PermResult<Void>`。软删（`delete_flag=id`）+ 同事务清理 MENU 投影；软删后部分唯一索引释放（path/资源可复用）。

**错误**: `10201` 不存在 / `10204` 存在子菜单。

**门禁**: `MENU:DELETE`（实例级）。

#### 4.6.4 `POST /menu/detail` 🔧

**请求 DTO**: `IdReq`。**响应**: `PermResult<MenuResp>`。**错误**: `10201`。

#### 4.6.5 `POST /menu/tree` 🔧

**请求**: 无参。**响应**: `PermResult<List<MenuResp>>`（全量菜单树，管理界面用；用户可见性过滤走 `/auth/user-menu`）。

`MenuResp` 字段：`id / menuType(String) / displayName / parentId / path / icon / sortOrder / status(1=ENABLED) / resourceType / resourceCode / sourceService / createdAt / updatedAt / children`。

**写链路语义**（create/update/delete 同一事务）：

- 可选字符串字段（`path/icon/resourceType/resourceCode/sourceService`）收到空白字符串时**服务端规范化为 null**（空串写库会命中部分唯一索引并被读链路误判为业务菜单；update 时空白等同未提供，跳过保留原值）——用户决策 2026-08-22
- `status` 仅允许 `0/1`（DTO `@Min(0) @Max(1)` 校验，违规 90001）
- MENU 投影对 DIR/MENU/EXTERNAL/IFRAME/HIDDEN **全量维护**（无 BUTTON 短路；实例级门禁依赖投影行授权到具体菜单实例）
- 菜单可见性由 v3.5 §4.1 派生公式在 `/auth/user-menu` 读链路决定（业务菜单 = `resource_type` 非空走资源访问事实，纯展示 `resource_type` 为空全员可见），不消费投影
- 菜单层级最多 5 级（根=第 1 层）：`calculateDepth` 返回父节点自身深度，新节点深度 = 父深度 + 1；**换父按整棵子树校验**（新根深度 + 子树高度 - 1 ≤ 5，即最深节点不超上限；顶级目标父深度按 0 计，防止把不存在的父层多算一层）
- 父菜单校验：正数 `parentId` 必须为同租户有效菜单（否则 `10201`，无外键兜底防孤儿节点）；换父时目标父不能是被移动菜单自身或其后代（否则 `10207`，防 parent 链成环——环会导致祖先链遍历与递归 CTE 不收敛）
- `MENU_PERM_CODE_EXISTS(10202)` 已退役（perm_code 列移除），由 `MENU_PATH_EXISTS(10205)` / `MENU_RESOURCE_EXISTS(10206)` 承接

---

## 5. 已对齐接口汇总 (✅ 6 项)

| # | 接口 | 来源 record | 备注 |
|---|------|-------------|------|
| 1 | `POST /org/tree` | `OrgQuery` | 待补 `operationCode/treeConfigId` 字段; **响应包装 `{ items }` 已由 T-ADMIN-021 消化（P1-3）** |
| 2 | `POST /org/page` | `OrgPageReq` | 含 `orgId` 子树筛选; 已对齐 |
| 3 | `POST /org/users` | `IdReq` | 前端 mock 入参字段名为 `orgId`, 待 Phase 2 调整为 `id` |
| 4 | `POST /user/update` | `UserUpdateReq` | 已对齐 |
| 5 | `POST /user-org/list` | `IdReq` | 已对齐; 待包装 `{ items }` |
| 6 | `POST /role/list` | `RoleListQueryReq` | 已对齐 |

---

## 6. 验收标准

Phase 2 后端实现以上 22 个接口后, 必须满足:

1. **字段对齐**: 前端 `frontend/src/api/user-manage.ts` 中所有类型与本契约 record 字段名/类型一一对齐, 不允许不一致.
2. **门禁**: 所有写操作经 `AdminPermissionValidator` 本地调用 `PermQueryEngine`; 实现不短路判断 (除自我修改豁免).
3. **本地投影**: 所有写操作 (除 /user/reset-password, /user-org/set-primary) 在主事务内维护对应权限投影与 `permission_change_log`。`/user-role/assign|revoke` 只处理功能角色。
4. **错误码段**: admin-service 业务错误使用 10001-19999 段, 系统错误使用 90001-99999 段; `XxxErrorCode` 枚举类不重复定义系统段.
5. **响应壳统一**: 所有接口返回 `PermResult<T>`, 列表不直接返回数组 (由 `PermResultResponseAdvice` 强制); 现有违反此规则的接口 (例如 `/org/tree` 直接返回 `List<OrgResp>`) 列入 Phase 2 修正项.
6. **异常映射**: 业务拒绝抛 `BizException`; 安全拒绝抛 `SecurityException`; 技术故障抛 `SystemException`. 不允许用 `SecurityException` 表达"资源不存在".
7. **默认树身份目录边界**: `/user/create` (带 orgId), `/user/delete`, `/user/enable`, `/user/reset-password`, `/user-org/set-primary` 必须在 AppService 内做默认树边界二次校验, 失败抛 `BizException`.
8. **投影所有权**: 权限管理入口与外部 sync/full-sync 不得改写 `owner=access-service` 或保留业务键；失败抛 `BizException(20045)`。外部增量/全量同步仍使用 `sync_metadata` 做版本乱序保护。

---

## 7. 已确认决策 (设计沉淀)

| # | 决策 | 理由 |
|---|------|------|
| 1 | `/user-role/list` 读接口保留 admin 域聚合（跨域只读）；`/user-role/assign`/`revoke` 已退役（恒 `10111`），角色管理由 permission 域 `/api/perm/abstract-role`、`/api/perm/user-role/*` 直接提供 | 原代理方案避免业务键暴露（api-gap-analysis §4 A 方案，已归档）；T-ACCESS-006 起单服务内不再需要写代理，读聚合保留供前端组合查询 |
| 2 | `/user/create` 一次性返回 `initialPassword` (明文) | 仅本次返回, 由前端弹窗展示给操作者; 后续无法再获取 |
| 3 | `/user/enable` 启停一体 (`status=0/1`), 不拆 `/user/disable` | 前端 mock 已采用此形态; AppService 内部按 status 派发 ENABLE/DISABLE 门禁码 |
| 4 | `/user-org/assign` 关系级追加, 禁止 wipe 模式 | 防止跨树意外清除 (default-org-tree §3.2); 已存在关系幂等忽略 |
| 5 | `/user-org/set-primary` 首期只允许默认树主归属 | 不能全局清除其他组织树主标记 (api-gap-analysis §3，已归档) |
| 6 | 岗位 = 特殊组织 (`orgType=2`), 走 `/org/*` + `/user-org/*` | org-user-permission-contract.md v1.2 决策; `/user-role/*` 仅服务功能角色 |
| 7 | 候选用户来自默认树可见范围, 新增 `/user/member-candidates` 接口与 `/user/page` 解耦 | api-gap-analysis §2（已归档）; 默认树 = 用户目录/身份池, 不暴露全租户用户 |
| 8 | 写操作必须在同一事务内维护管理事实、本地权限投影和 permission_change_log | access-service-architecture §4；任一步失败整体回滚；缓存失效仅提交后发生 |
| 9 | 功能角色分配/回收由 `/api/perm/user-role/assign|revoke` 提供，仅处理功能角色 | ORG/POSITION 由组织与成员关系投影产生；外部 sync 的 SYS_USER_ORG 来源一律拒绝（原 admin 代理端点已退役恒 `10111`） |
| 10 | admin 域不存储 permission 域内部 ID | 跨域统一用业务键; 业务键格式严格按 api-contract.md §6.2.2.4 |
| 11 | `IdReq` 入参字段名为 `id` 而非 `orgId/userId` | 复用公共 record; 前端在 Phase 2 调整 mock 字段 (例如 `/org/users` 入参 `{ id }`) |
| 12 | 列表响应统一用 `{ items: [...] }` 包装, 即便是非分页列表 | project-rules.md §1.3 强约束; 现有违反此规则的接口列入 Phase 2 修正项 (如 `/role/list`, `/user-org/list`, `/org/users`; **`/org/tree` 已由 T-ADMIN-021 消化, P1-3**) |
| 13 | `/user/update` 自我修改业务豁免 | 在 AppService 调用门禁前判断 `operatorId == id` 跳过门禁; 不放在门禁层 |
| 14 | 错误码段 admin-service 子分配 | 用户域 10001-10299 / 组织域 10300-10499 / 关系域 10400-10499 / 角色代理 10500-10599 / 其他保留 10600-19999 |

---

## 8. OAuth2 认证与客户端管理契约 (T-ACCESS-013 补记, 2026-08-22)

> OAuth2 端点此前仅存在于归档设计（`docs/archive/2026-04-28/admin-service-design.full.md` §1.5-1.6），本节按当前实现补记为活跃契约。授权链路语义（JWT 载荷、audience、开放路径门禁）以 `access-service-architecture.md` §6 为权威。

### 8.1 授权端点 (`/auth/oauth2/*`)

所有端点 POST + JSON Body；统一响应壳 `PermResult<T>`。

#### 8.1.1 `POST /auth/oauth2/authorize`（需平台会话）

平台用户为客户端发起授权，生成一次性授权码（Redis `oauth2:code:<uuid>`，TTL 300s，Lua GET+DEL 原子消费）。

请求（`AuthorizeReq`）：`clientId`* / `responseType`*（固定 `code`）/ `redirectUri`* / `state` / `scope`（空格分隔，⊆ 客户端注册 scopes，否则 `OAUTH2_SCOPE_INVALID`；**客户端注册 scopes 为空时拒绝非空 scope 请求**——空注册不解释为无限制；空 scope 请求放行，签发的无 scope 令牌因业务路径 requiredScopes 强制非空而仅可访问 userinfo 豁免端点）/ `codeChallenge` / `codeChallengeMethod`（S256|plain）。

响应（`AuthorizeResp`）：`code` / `state`。

#### 8.1.2 `POST /auth/oauth2/token`（匿名）

授权码兑换访问令牌。仅支持 `grant_type=authorization_code`。

请求（`TokenReq`）：`grantType`* / `clientId`* / `clientSecret`* / `code`* / `redirectUri`* / `codeVerifier` / `refreshToken`。

响应（`TokenResp`）：`accessToken`（JWT）/ `tokenType`（`Bearer`）/ `expiresIn`（=客户端 `accessTokenTtl`）/ `refreshToken` / `scope`。

**JWT 载荷**（`SaJwtUtil` HS256，loginType=`oauth2`，密钥 `sa-token.jwt-secret-key`）：`loginId`（userId）/ `client_id` / `tenant_id`（字符串，无租户 `"0"`）/ `scope`（空格分隔委托范围）/ `jti` / `aud`（**T-ACCESS-013**：客户端注册 `audiences` 非空时写入 List，未配置不写）/ `eff` / `device=oauth2`。

#### 8.1.3 `POST /auth/oauth2/refresh`（匿名）

刷新令牌轮换（Lua 原子取删旧 refresh token，一次性使用；签发新 access + refresh token，scope/clientId 透传）。

请求（`RefreshTokenReq`）：`clientId`* / `refreshToken`*。响应同 `TokenResp`。

#### 8.1.4 `POST /auth/oauth2/revoke`（匿名）

撤销访问令牌：先验签（非法令牌不写 Redis，防黑名单键 DoS），`jti` 写入 `oauth2:blacklist:<jti>`，TTL=令牌剩余有效期。黑名单对全部开放路径生效。

请求（`RevokeTokenReq`）：`accessToken`*。响应：`PermResult<Void>`。

#### 8.1.5 `POST /auth/oauth2/userinfo`（需 OAuth2 JWT，默认开放路径）

资源服务器端点（默认开放路径，audience 豁免）。请求体空；`Authorization: Bearer <OAuth2 JWT>`。

响应（`OAuth2UserInfoResp`）：`sub`（userId 字符串）/ `username` / `name` / `phone` / `email`。

### 8.2 资源服务器开放路径门禁（T-ACCESS-013）

OAuth2 委托令牌访问业务 API 由显式配置的路径白名单 + 三重门禁控制（**默认拒绝**）：

- 配置：`access.oauth2.resource-paths`（application.yml；默认仅 `/auth/oauth2/userinfo`；Ant 通配允许；显式配置为全量替换；启动防护禁止覆盖 `/auth/**` 会话端点与 `/api/perm/**` 内部凭证空间（静态前缀保守判定，`/api/**/sync` 等绕过形态均拦截），且**业务开放路径必须声明 requiredScopes 与 audience**——缺失启动失败，防配置遗漏静默放行）。
- 每条规则：`path` + `requiredScopes`（令牌 scope 子集校验，独立映射模型——不接入 PermQueryEngine）+ `audience`（业务路径强制；userinfo 豁免）+ `clientIds`（可选客户端限定）。
- 恒定校验（无需配置）：验签 + 必填 claim（loginId/jti/client_id）+ 撤销黑名单 + 客户端启用动态校验（禁用立即失效 → 401）。门禁不满足 → 403。
- 委托调用绑定 `USER + delegatedClientId` 上下文（审计可区分第三方委托）。
- Gateway 侧配套 `gateway.oauth2.passthrough-paths`（外部路径口径，默认空；**仅对 Bearer 三段式 JWT 启用透传**，平台 uuid 会话仍走 Gateway 正常鉴权）透传 Authorization；双侧路径口径差异与部署约束见架构文档 §6。

### 8.3 OAuth2 客户端管理 (`/oauth2/client/*`)

门禁：`ResourceTypeCode.ADMIN_OAUTH2_CLIENT`（类型级 CREATE / 实例级 UPDATE/DELETE）；操作日志 `@OperationLog`（sys_oauth2_client）。

| 端点 | 请求 | 响应 | 备注 |
|------|------|------|------|
| `POST /oauth2/client/create` | `Oauth2ClientCreateReq` | `PermResult<Long>`（新客户端 id） | clientId 唯一（重复 `CLIENT_ID_EXISTS`）；secret BCrypt 存储 |
| `POST /oauth2/client/update` | `Oauth2ClientUpdateReq` | `PermResult<Void>` | 仅更新非 null 字段；secret 更新重新 BCrypt |
| `POST /oauth2/client/delete` | `IdsReq` | `PermResult<Void>` | 批量软删除 |
| `POST /oauth2/client/detail` | `IdReq` | `PermResult<Oauth2ClientResp>` | 不返回 clientSecret |
| `POST /oauth2/client/page` | `Oauth2ClientPageReq` | `PermResult<PaginatedResult<Oauth2ClientResp>>` | 按名称/状态过滤 |

`Oauth2ClientCreateReq`：`clientId`* / `clientSecret`* / `clientName`* / `grantTypes`（逗号分隔）/ `redirectUris`（逗号分隔）/ `scopes`（逗号分隔）/ **`audiences`**（逗号分隔资源服务器标识，T-ACCESS-013；配置后签发写入 aud claim）/ `accessTokenTtl`（60-86400）/ `refreshTokenTtl`（60-604800）/ `status`。

`Oauth2ClientResp`：上表字段 + `id` / `tenantId` / `createdAt` / `updatedAt`（不含 clientSecret）。

种子数据（access-service.sql）：admin-web / example-web / internal-service，`scopes='all'`、`audiences='access-service'`。

---

## 附录 A. 接口与前端 API 一一对照表

| 后端接口 | 前端 `user-manage.ts` 函数 | 状态 |
|----------|-----------------------------|------|
| `POST /user/page` | `getUserPage` | 🔧 |
| `POST /user/member-candidates` | (待新增) | 🔧 |
| `POST /user/create` | `createUser` | 🔧 |
| `POST /user/update` | `updateUser` | ✅ |
| `POST /user/delete` | `deleteUser` | 🔧 |
| `POST /user/enable` | `enableUsers` | 🔧 |
| `POST /user/reset-password` | `resetUserPassword` | 🔧 |
| `POST /org/tree` | `getOrgTree` | ✅ |
| `POST /org/page` | `getOrgPage` | ✅ |
| `POST /org/users` | `getOrgUsers` | ✅ |
| `POST /org/create` | `createOrg` | 🔧 |
| `POST /org/update` | `updateOrg` | 🔧 |
| `POST /org/delete` | `deleteOrg` | 🔧 |
| `POST /user-org/list` | `getUserOrgs` | ✅ |
| `POST /user-org/assign` | `assignUserOrgs` | 🔧 |
| `POST /user-org/remove` | `removeUserOrg` | 🔧 |
| `POST /user-org/set-primary` | `setPrimaryOrg` | 🔧 |
| `POST /user-role/list` | `getUserRoles` | 🔧 |
| `POST /user-role/assign` | `assignRole` | 🔧 |
| `POST /user-role/revoke` | `revokeRole` | 🔧 |
| `POST /role/list` | `getRoleList` | ✅ |

合计: 22 项接口 (16 🔧 + 6 ✅), 与 api-gap-analysis.md "已核对接口汇总" 一致 (该清单 2026-06-21 归档至 `docs/archive/2026-06-21/`，16 个 🔧 接口已由 admin-service 实现).

---

## 附录 B. 错误码段建议 (access-service 管理域 10001-10599 区间)

| 子段 | 含义 |
|------|------|
| 10001-10099 | 用户查询 (page/detail/member-candidates 业务错误) |
| 10100-10119 | 候选用户查询 |
| 10120-10169 | 用户创建/更新 |
| 10170-10209 | 用户删除/启停 |
| 10210-10229 | 用户密码重置 |
| 10300-10399 | 组织 CRUD |
| 10400-10499 | 用户-组织关系 |
| 10500-10599 | 用户-角色代理 |
| 10600-19999 | 保留给 admin-service 后续模块 (字典/通知/文件/任务/审计等) |

具体码值由各模块的 `XxxErrorCode` 枚举类落地; 90001-99999 段 (参数校验/系统异常) 由 `common` 模块统一定义.
