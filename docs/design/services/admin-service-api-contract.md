---
doc_type: design
title: Admin Service 对前端 API 契约（组织与用户域）
status: adopted
domain: admin-service
last_reviewed: 2026-06-20
---

# Admin Service 对前端 API 契约（组织与用户域）

> 状态: v1.0 草案 (2026-06-14). 本文是「组织与用户」融合页所需 admin-service 接口的契约基线.
>
> 关联文档:
> - `../project-rules.md` (强约束: 报文/接口/异常/错误码段)
> - `../permission-center/api-contract.md` (业务键, /api/perm/user-role/* 代理调用)
> - `../default-org-tree-user-lifecycle.md` (默认组织树身份目录边界)
> - `../org-user-permission-contract.md` v1.2 (页面门禁与岗位=特殊组织决策)
> - `./admin-service.md` (admin-service 职责与同步任务模型)
> - `../schema/admin-service.sql` (字段事实)
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
- **§2.2 路径**: admin-service 直接挂载在网关路由 `/admin/api/**` 下, 实际控制器映射为 `/user`, `/org`, `/user-org`, `/user-role`, `/role` 等资源根. 本契约文档中所有路径均为服务内部映射 (前端经网关访问).
- **请求体禁止 `tenantId`**: 服务端统一从 `X-Tenant-Id` Header 与 SecurityContext 读取. 前端经网关后无需感知.
- **§1.2 业务错误码**: admin-service 业务错误使用 `10001-19999` 段; 系统公共错误 (参数校验/系统异常) 使用 `90001-99999` 段, 由 `common` 模块统一定义.
- **§3.2 异常**: 业务拒绝 (资源不存在/状态冲突/默认树边界违规等) 抛 `BizException`; 安全拒绝 (操作者身份缺失/权限不足/越权) 抛 `SecurityException`; 技术故障 (DB/RPC/序列化) 抛 `SystemException`. **禁止**用 `SecurityException` 表达"资源不存在"或"参数非法".

---

## 2. 门禁规范

admin-service 通过 `AdminPermissionValidator` 调用 permission-center 的 `auth/check`/`auth/batch-check` 完成门禁. 接口形态:

```java
void checkTypeLevel(String resourceTypeCode, String operationCode);
void checkInstanceLevel(String resourceTypeCode, String resourceCode, String operationCode);
void checkBatchInstanceLevel(String resourceTypeCode, List<String> resourceCodes, String operationCode);
```

资源类型常量 (`AdminResourceType`):

| 常量 | 值 | 说明 |
|------|------|------|
| `AdminResourceType.USER` | `ADMIN_USER` | 被管理的用户实例 (resource_entity, code=sys_user.id) |
| `AdminResourceType.ORG` | `ADMIN_ORG` | 被管理的组织/岗位实例 (resource_entity, code=sys_org.id) |
| `AdminResourceType.ROLE` | `ADMIN_ROLE` | (本契约只读: 仅 /role/list 用) |

操作码常量 (`AdminOperationCode`): `CREATE / UPDATE / DELETE / VIEW / ENABLE / DISABLE / RESET_PASSWORD / GRANT / REVOKE`.

本契约接口的门禁映射表:

| 接口 | 资源类型 | 资源粒度 | 操作码 | 备注 |
|------|----------|----------|--------|------|
| `/user/page` | `ADMIN_USER` | 类型级 | `VIEW` | 默认树身份目录范围内列表 |
| `/user/member-candidates` | `ADMIN_ORG` | 实例级 (目标 orgId) | `UPDATE` | 仅校验"能管理目标组织的成员"; 候选用户范围由默认树可见性二次裁剪 |
| `/user/create` | `ADMIN_USER` | 类型级 | `CREATE` | 若入参带 orgId, 同时需 `ADMIN_ORG:UPDATE@orgId` |
| `/user/update` | `ADMIN_USER` | 实例级 (userId) | `UPDATE` | 自我修改业务豁免在调用前处理 |
| `/user/delete` | `ADMIN_USER` | 实例级批量 (ids) | `DELETE` | 默认树身份目录边界 |
| `/user/enable` | `ADMIN_USER` | 实例级批量 (ids) | `ENABLE` 或 `DISABLE` | 按入参 `status` 派发: 1=ENABLE, 0=DISABLE |
| `/user/reset-password` | `ADMIN_USER` | 实例级 (userId) | `RESET_PASSWORD` | 默认树身份目录边界 |
| `/user/detail` | `ADMIN_USER` | 实例级 (userId) | `VIEW` | 类型级 VIEW 门禁 + 默认树可见范围裁剪（P1-2：复用 `validateUsersInDefaultTreeScope`，与 `/user/page` 同等约束，防止知道 ID 即可读列表不可见用户；无组织关系用户拒绝）|
| `/org/tree` | `ADMIN_ORG` | 类型级 | `VIEW` 或 `CREATE` | 入参 `operationCode` 决定语义: `VIEW`=可视范围; `CREATE`=新增用户时可选挂载点 (限默认树) |
| `/org/page` | `ADMIN_ORG` | 类型级 | `VIEW` | |
| `/org/users` | `ADMIN_ORG` | 实例级 (orgId) | `VIEW` | |
| `/org/create` | `ADMIN_ORG` | 实例级 (parentOrgId, 顶级时类型级) | `CREATE` | |
| `/org/update` | `ADMIN_ORG` | 实例级 (orgId) | `UPDATE` | 改 `parentOrgId` 等价于"移动", 同时需新父级 `UPDATE` |
| `/org/delete` | `ADMIN_ORG` | 实例级 (orgId) | `DELETE` | |
| `/user-org/list` | `ADMIN_USER` | 实例级 (userId) | `VIEW` | 读用户成员关系视图 |
| `/user-org/assign` | `ADMIN_ORG` | 实例级批量 (orgIds) | `UPDATE` | 关系级追加; 默认树关系受身份目录边界二次校验 |
| `/user-org/remove` | `ADMIN_ORG` | 实例级 (orgId) | `UPDATE` | 非默认树仅删关系并回收对应 user_role; 默认树移除按身份目录高危处理 |
| `/user-org/set-primary` | `ADMIN_ORG` | 实例级 (orgId) | `UPDATE` | 首期仅允许默认组织树主归属 |
| `/user-role/list` | `ADMIN_USER` | 实例级 (userId) | `VIEW` | admin 代理直查; 不再额外要求 `ROLE:MANAGE` |
| `/user-role/assign` | `ROLE` | 实例级 (roleExternalId) | `MANAGE` | admin 代理 `permission-center /api/perm/user-role/assign`; 前端传业务键 `(roleTypeCode, roleExternalId)`. **admin 层不做 ROLE:MANAGE 预检，由 permission-center 兜底**（P1-1：admin 预检曾把 roleExternalId 当 ROLE resource_entity.code，语义错位会误拒；perm 用正确 abstract_role.id 校验）|
| `/user-role/revoke` | `ROLE` | 实例级 (roleExternalId) | `MANAGE` | 同上 |
| `/role/list` | `ADMIN_ROLE` | 类型级 | `VIEW` | 仅功能角色 |

> **默认树身份目录边界二次校验**: `/user/create`、`/user/delete`、`/user/enable`、`/user/reset-password`、`/user-org/set-primary` 在通过 `AdminPermissionValidator` 后, AppService 内部还要二次确认目标用户的默认树关系存在 (通过 `sys_user_org` 推导), 且操作者在默认树该子树下具备可见性. 不满足时抛 `BizException(ErrorCode.NOT_IN_DEFAULT_TREE_SCOPE)`. 这一层不能用 `SecurityException` 表达.

---

## 3. 与 permission-center 的同步动作

写操作必须在主事务内写 `sys_sync_task` (本地消息表). 见 `admin-service.md` §同步任务模型 与 `cross-service/admin-permission-sync.md`.

`syncAction` 收敛为 4 类:

| syncAction | 来源表 | 目标事实 | 业务键格式 (api-contract.md §6.2.2.4) |
|------------|--------|----------|---------------------------------------|
| `PERM_ABSTRACT_USER_SYNC` | `sys_user` | `abstract_user` | `subjectTypeCode=ADMIN_USER&subjectExternalId={sys_user.id}` |
| `PERM_ABSTRACT_ROLE_SYNC` | `sys_org` | `abstract_role(ORG/POSITION)` | `roleTypeCode={ORG\|POSITION}&roleExternalId={sys_org.id}` |
| `PERM_USER_ROLE_SYNC` | `sys_user_org` | `user_role` | `subjectTypeCode=ADMIN_USER&subjectExternalId={sys_user.id}&roleTypeCode={ORG\|POSITION}&roleExternalId={sys_org.id}&relationKey=ORG%3A{父组织sys_org.id}` |
| `PERM_RESOURCE_ENTITY_SYNC` | `sys_user / sys_org / sys_menu` | `resource_entity` | `resourceTypeCode={ADMIN_USER\|ADMIN_ORG}&resourceCode={sys_user.id\|sys_org.id}&codeType=default` |

> `PERM_USER_ROLE_SYNC` 的 `relationKey` 仅在 `roleTypeCode=POSITION` 时有意义 (岗位需要绑定所属组织); `roleTypeCode=ORG` 时, 实现可省略 `relationKey` 参数项.

> 写操作必须为每条事实变更产生独立的同步任务 (按 `tenantId + syncAction + businessKeyHash` 合并 `PENDING` 任务). `messageKey` 与 `payload` 由 admin-service Handler 生成; payload schema 与目标 sync 接口请求体一致.

本契约接口产生的同步动作清单:

| 接口 | 主事务 | 同步动作 |
|------|--------|----------|
| `/user/create` | INSERT `sys_user` (+ INSERT `sys_user_org` 若带 orgId) | 1. `PERM_ABSTRACT_USER_SYNC` (UPSERT) <br> 2. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_USER` UPSERT) <br> 3. 若带 orgId: `PERM_USER_ROLE_SYNC` (BIND) |
| `/user/update` | UPDATE `sys_user` | 1. `PERM_ABSTRACT_USER_SYNC` (UPSERT) <br> 2. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_USER` UPSERT, 仅当 name 等展示属性变化) |
| `/user/delete` | 软删除 `sys_user` (delete_flag=1, status=1), 级联清理 `sys_user_org` | 1. 每条被清理的 user-org → `PERM_USER_ROLE_SYNC` (UNBIND) <br> 2. `PERM_ABSTRACT_USER_SYNC` (DELETE) <br> 3. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_USER` DELETE) |
| `/user/enable` | UPDATE `sys_user.status` | 1. `PERM_ABSTRACT_USER_SYNC` (UPSERT, payload `enabled` 跟随 status; `operation=DISABLE` 时使用 DISABLE) <br> 2. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_USER` UPSERT 或 DISABLE) |
| `/user/reset-password` | UPDATE `sys_user.password` | 不产生同步任务 (密码不进入 permission-center) |
| `/org/create` | INSERT `sys_org` | 1. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_ORG` UPSERT) <br> 2. `PERM_ABSTRACT_ROLE_SYNC` (UPSERT, `roleTypeCode=ORG` 或 `POSITION` 视 `orgType`) |
| `/org/update` | UPDATE `sys_org` | 1. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_ORG` UPSERT) <br> 2. `PERM_ABSTRACT_ROLE_SYNC` (UPSERT) |
| `/org/delete` | 软删除 `sys_org`, 级联清理 `sys_user_org` | 1. 每条被清理的 user-org → `PERM_USER_ROLE_SYNC` (UNBIND) <br> 2. `PERM_ABSTRACT_ROLE_SYNC` (DELETE) <br> 3. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_ORG` DELETE) |
| `/user-org/assign` | INSERT/UPDATE `sys_user_org` (按关系级追加; 同时设置 `is_primary` 当 `primaryOrgId` 命中) | 每个新增/变化关系 → `PERM_USER_ROLE_SYNC` (BIND); 同步 envelope 必须按 `roleTypeCode∈{ORG,POSITION}` 拆分 (api-contract §6.10 约束) |
| `/user-org/remove` | DELETE `sys_user_org` (单条) | `PERM_USER_ROLE_SYNC` (UNBIND) |
| `/user-org/set-primary` | UPDATE `sys_user_org.is_primary` | 不产生 `user_role` 拓扑变化, 不进入 sys_sync_task; admin-service 内自管 `is_primary` 字段 |
| `/user-role/assign`, `/user-role/revoke` | **不写 sys_sync_task** | admin 代理直调 permission-center `/api/perm/user-role/assign\|revoke` (功能角色走正式管理 API, 见 admin-service.md §同步任务模型) |

> 关键边界: `PERM_USER_ROLE_SYNC` 仅承载 `sourceType=SYS_USER_ORG` 且 `roleTypeCode∈{ORG, POSITION}` 的关系. 功能角色 (BASIC_ROLE/GROUP_ROLE/PERSONAL) 经 /user-role/* 代理走正式接口, 不进入 sys_sync_task.

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
| `status` | `Integer` | 否 | 0=正常, 1=禁用 |
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
| `status` | `Integer` | 0=正常, 1=禁用 |
| `orgs` | `List<OrgBrief>` | 用户所属组织简表 |
| `createdAt` | `LocalDateTime` | |

`UserPageItemResp.OrgBrief`:

| 字段 | 类型 | 说明 |
|------|------|------|
| `orgId` | `Long` | sys_org.id |
| `orgName` | `String` | |
| `orgType` | `String` | 字典值 (Phase 2 决策: 后端固定为 String 字典编码; 前端 `OrgBrief.orgType` 可选) |
| `isPrimary` | `boolean` | 是否主组织 |

**门禁**: `ADMIN_USER:VIEW` 类型级 + 默认树可见范围裁剪 (操作者只能看到默认树中其有 `ADMIN_ORG:VIEW` 的子树成员).

**同步动作**: 无 (只读)

**错误码段**: 10001-10099 (用户查询)

**当前差距 (来自 api-gap-analysis，已归档 `docs/archive/2026-06-21/`)**: 需要明确语义为"默认组织树身份目录查询"; 区别于添加组织成员时的候选用户查询 (后者改用 §4.1.2 `/user/member-candidates`). 该差距已由 admin-service 实现收口。

**验收要点**:
- 操作者无 `ADMIN_USER:VIEW` 时返回空列表 + `code=200` (不抛 SecurityException).
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

**门禁**: `ADMIN_ORG:UPDATE@targetOrgId` (实例级, 校验"能管理目标组织成员").

**同步动作**: 无 (只读)

**错误码段**: 10100-10119

**当前差距**: 接口未实现. 当前前端 mock 复用 `/user/page`, 但语义与默认树身份目录查询不同, 需独立接口.

**验收要点**:
- 候选集**严格**来自默认树中操作者具备 `ADMIN_USER:VIEW` (或 `ADMIN_ORG:VIEW`) 的范围; 不暴露全租户用户.
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
| `status` | `Integer` | 否 | 默认 0 (正常) |
| `orgId` | `Long` | 否 | 创建时一步完成挂载; **必须**属于默认组织树, 否则抛 `BizException(ORG_NOT_IN_DEFAULT_TREE)` |
| `primaryOrg` | `Boolean` | 否 | 仅当 `orgId` 非空时生效, 默认 true |

**响应 DTO**: `UserCreateResp`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 新用户 ID |
| `initialPassword` | `String` | 系统生成的随机初始密码明文, **仅本次返回** |

**门禁**: `ADMIN_USER:CREATE` 类型级 (+ 若带 `orgId` 还需 `ADMIN_ORG:UPDATE@orgId`).

**同步动作**:
1. 主事务: INSERT `sys_user` (+ 可选 INSERT `sys_user_org`)
2. `PERM_ABSTRACT_USER_SYNC` (operation=`UPSERT`, businessKey=`subjectTypeCode=ADMIN_USER&subjectExternalId={id}`)
3. `PERM_RESOURCE_ENTITY_SYNC` (operation=`UPSERT`, resourceTypeCode=`ADMIN_USER`, resourceCode=`{id}`)
4. 若带 `orgId`: `PERM_USER_ROLE_SYNC` (operation=`BIND`)

**错误码段**: 10120-10149

**当前差距**: 现有实现已支持 `orgId/primaryOrg` 字段与 `initialPassword` 返回. 待补: ① 默认树校验 (`orgId` 必须在默认树); ② `sys_sync_task` 写入收敛为 4 类 syncAction; ③ 不再回填 permission-center 内部 ID.

**验收要点**:
- `username` 在租户内重复时抛 `BizException(USERNAME_DUPLICATED)`.
- `orgId` 非默认树时抛 `BizException(ORG_NOT_IN_DEFAULT_TREE)`.
- `initialPassword` 必须非空且强度满足策略 (8-32 位, 可配置).
- 同步任务必须与主事务原子写入; 任一失败整体回滚.

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
| `status` | `Integer` | 否 | 0/1 |

**响应**: `PermResult<Void>`

**门禁**: `ADMIN_USER:UPDATE@id` (实例级). 自我修改业务豁免在 AppService 调用门禁前判断 (operatorId == id 时跳过门禁).

**同步动作**:
- `PERM_ABSTRACT_USER_SYNC` (UPSERT) — 同步 `enabled` (若 status 变化)
- `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_USER` UPSERT) — 仅当 `name` 变化 (展示属性)

**错误码段**: 10150-10169

**验收要点**: 字段未变化时不应产生同步任务 (避免无效消息).

---

#### 4.1.5 `POST /user/delete` 🔧

**目的**: 批量软删除用户. 高危身份目录操作, 仅默认组织树管理员可执行.

**请求 DTO**: `IdsReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ids` | `List<Long>` | 是 | sys_user.id 列表 |

**响应**: `PermResult<Void>`

**门禁**: `ADMIN_USER:DELETE` 实例级批量 (`checkBatchInstanceLevel(USER, ids, DELETE)`) + 默认树边界二次校验 (操作者必须在每个目标用户的默认树主归属子树下具备 `ADMIN_ORG:UPDATE`).

**同步动作** (每个 id):
1. 主事务: 软删 `sys_user`, 级联软删 `sys_user_org`
2. 每条 user-org 关系 → `PERM_USER_ROLE_SYNC` (UNBIND)
3. `PERM_ABSTRACT_USER_SYNC` (DELETE)
4. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_USER` DELETE)

**错误码段**: 10170-10189

**当前差距**: 现有实现仅做软删 + 关系清理; 需补默认树边界校验, 同步任务收敛为 4 类 syncAction.

**验收要点**:
- 批量中任一用户不在操作者默认树可管范围抛 `BizException(NOT_IN_DEFAULT_TREE_SCOPE)`, 整批回滚.
- 软删后 `username` 在租户内可被新用户复用 (业务策略, 与现状一致).
- 不允许删除当前操作者本人 → `BizException(CANNOT_DELETE_SELF)`.

---

#### 4.1.6 `POST /user/enable` 🔧

**目的**: 批量启用/禁用用户 (启停一体). `status=1` 启用, `status=0` 禁用. 高危生命周期操作.

> 设计决策: 启停**不**拆为 `/user/enable` + `/user/disable` 双接口, 沿用现有 `UserUpdateStatusReq(ids, status)` 形态; 在 AppService 内部按 `status` 动态选择门禁操作码 (`ENABLE` vs `DISABLE`).

**请求 DTO**: `UserUpdateStatusReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ids` | `List<Long>` | 是 | 用户 ID 列表 |
| `status` | `Integer` | 是 | 1=启用, 0=禁用 |

**响应**: `PermResult<Void>`

**门禁**:
- `status=1`: `ADMIN_USER:ENABLE` 实例级批量
- `status=0`: `ADMIN_USER:DISABLE` 实例级批量
- 加默认树边界二次校验 (与 §4.1.5 同).

**同步动作** (每个 id):
1. 主事务: UPDATE `sys_user.status`
2. `PERM_ABSTRACT_USER_SYNC` (operation=`UPSERT` 时 payload `enabled` 跟随; 或 operation=`DISABLE` 直发 DISABLE)
3. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_USER` UPSERT 或 DISABLE)

**错误码段**: 10190-10209

**当前差距**: 现有 `UserController.updateStatus` 已支持 `UserUpdateStatusReq`; 需补: ① 按 status 派发 ENABLE/DISABLE 门禁码; ② 默认树边界二次校验; ③ 同步任务收敛.

**验收要点**:
- 不允许禁用操作者本人 → `BizException(CANNOT_DISABLE_SELF)`.
- 非默认树成员管理员调用此接口必须被门禁拦截 (因其无 `ADMIN_USER:ENABLE/DISABLE`).

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

**门禁**: `ADMIN_USER:RESET_PASSWORD@userId` 实例级 + 默认树边界二次校验.

**同步动作**: 无 (密码不进入 permission-center)

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
| `operationCode` | `String` | 否 | `VIEW` 或 `CREATE`; 不传时按 `VIEW` 处理 |
| `treeConfigId` | `Long` | 否 | 组织树配置 ID; 不传则返回默认树 |
| `orgName` | `String` | 否 | 模糊匹配 |
| `orgType` | `Integer` | 否 | |
| `status` | `Integer` | 否 | |
| `parentOrgId` | `Long` | 否 | 用于查询子树; 一般不与 `treeConfigId` 同时使用 |

**响应**: `PermResult<List<OrgResp>>` (顶层 `data` 为对象时应包装在 `{ items: [...] }`; 历史此接口直接返回 List, 已纳入合规债务清单, Phase 2 修正为 `{ items: [...] }`)

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

**门禁**: `ADMIN_ORG:{operationCode}` 类型级.

**同步动作**: 无.

**当前差距** (合规债务): ① `OrgQuery` 当前 record 缺 `operationCode` 与 `treeConfigId` 字段; ② 顶层响应应改为 `{ items: [...] }` 包装. 列入 Phase 2 修正项.

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

**门禁**: `ADMIN_ORG:VIEW`.

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

**门禁**: `ADMIN_ORG:VIEW@id`.

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
| `phone` | `String` | 否 | |
| `email` | `String` | 否 | |
| `status` | `Integer` | 否 | 默认 0 |
| `sort` | `Integer` | 否 | |

**响应**: `PermResult<Long>` (新组织 ID)

**门禁**:
- 顶级 (`parentOrgId=null`): `ADMIN_ORG:CREATE` 类型级
- 子级: `ADMIN_ORG:UPDATE@parentOrgId` 实例级 (在父级下添加子节点等价于"修改父级结构")

**同步动作**:
1. 主事务: INSERT `sys_org`
2. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_ORG` UPSERT, `extra` 含 `orgType`, `treeConfigId`, `rootOrgId`, `isDefaultTree`, `level`)
3. `PERM_ABSTRACT_ROLE_SYNC` (UPSERT, `roleTypeCode=ORG` 或 `POSITION` 视 `orgType`)

**错误码段**: 10300-10329

**当前差距**: 接口存在但未连接 permission-center 同步; 需新建 sys_sync_task 写入逻辑.

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
| `phone` | `String` | 否 | |
| `email` | `String` | 否 | |
| `status` | `Integer` | 否 | |
| `sort` | `Integer` | 否 | |

**响应**: `PermResult<Void>`

**门禁**: `ADMIN_ORG:UPDATE@id` 实例级. 若 `parentOrgId` 变化, 还需 `ADMIN_ORG:UPDATE@新parentOrgId`.

**同步动作**:
1. 主事务: UPDATE `sys_org`
2. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_ORG` UPSERT)
3. `PERM_ABSTRACT_ROLE_SYNC` (UPSERT)
4. 若 `parentOrgId` 变化: 影响子树 user_role 的 `relationKey` (POSITION 关系), 需对该组织 + 其子岗位下的所有 `sys_user_org` 重新生成 `PERM_USER_ROLE_SYNC` (UNBIND 旧 relationKey + BIND 新 relationKey). Phase 2 实现可暂仅记录 TODO, 后续按全量校准兜底.

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

**门禁**: `ADMIN_ORG:DELETE@id` 实例级.

**同步动作**:
1. 主事务: 软删 `sys_org` (delete_flag=1), 级联软删 `sys_user_org`
2. 每条被清理的 user-org → `PERM_USER_ROLE_SYNC` (UNBIND)
3. `PERM_ABSTRACT_ROLE_SYNC` (DELETE)
4. `PERM_RESOURCE_ENTITY_SYNC` (`ADMIN_ORG` DELETE)

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

**门禁**: `ADMIN_USER:VIEW@userId`.

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

**门禁**: 对 `orgIds` 中每个组织实例分别 `ADMIN_ORG:UPDATE@orgId` (`checkBatchInstanceLevel`).

**写入语义** (关键决策):
- **追加**已存在的关系幂等忽略, 不删除用户在其他组织树的关系.
- **禁止**"先 wipe 再 batch insert"模式; 必须按 `(userId, orgId)` 对增量比较.
- 如果 `primaryOrgId` 非空: 仅在 `primaryOrgId` 所属组织树内将其设为主, **不**清除用户在其他树的 `is_primary` 标记 (首期仅默认树主归属生效, 见 §4.3.4).

**同步动作**:
- 每个**新增**关系 → `PERM_USER_ROLE_SYNC` (BIND)
- 已存在关系 → 不产生同步任务

**错误码段**: 10400-10429

**当前差距**: 现有实现需明确"关系级追加而非全量替换"; 同步必须按 `roleTypeCode∈{ORG,POSITION}` 拆分多个 envelope (api-contract §6.10 约束).

**验收要点**:
- `userId` 在默认树有归属时方可追加非默认树关系; 未在默认树时抛 `BizException(USER_NOT_IN_DEFAULT_TREE)`.
- 候选 `orgIds` 中含已删除组织 → `BizException(ORG_NOT_FOUND)`.
- 任一 `orgId` 操作者无 `ADMIN_ORG:UPDATE` → `SecurityException`, 整批回滚.

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
- 非默认树关系: `ADMIN_ORG:UPDATE@orgId`
- 默认树关系: `ADMIN_USER:UPDATE@userId` (按身份目录边界, 等同"移动用户默认归属")

**同步动作**:
- DELETE `sys_user_org` (单条) → `PERM_USER_ROLE_SYNC` (UNBIND)

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

**门禁**: `ADMIN_ORG:UPDATE@orgId` + 默认树边界校验.

**写入语义**:
- 仅在默认树内将 `(userId, orgId)` 的 `is_primary=true`, 同时把该用户在默认树的其他关系置 `is_primary=false`.
- **禁止**清除其他组织树的 `is_primary` 标记 (即便是历史脏数据, 也由专门 `is_primary` 修复迁移负责, 不通过本接口).

**同步动作**: 不写 `sys_sync_task` (`is_primary` 是 admin 内自管字段, 不映射到 permission-center user_role 拓扑).

**错误码段**: 10460-10479

**当前差距**: 现有实现未限制必须为默认树; 需补.

**验收要点**:
- `orgId` 不在默认树 → `BizException(PRIMARY_MUST_BE_IN_DEFAULT_TREE)`.
- 用户与 `orgId` 不存在关联 → `BizException(USER_ORG_RELATION_NOT_FOUND)`.

---

### 4.4 用户-角色代理 (`/user-role`)

> **核心决策**: admin-service 新增 `UserRoleController` 代理 permission-center `/api/perm/user-role/*`. 接口使用业务键 `(roleTypeCode, roleExternalId)` 标识角色, 前端直接传业务键. 门禁统一使用 `ROLE:MANAGE@roleExternalId`. 仅服务功能角色 (BASIC_ROLE/GROUP_ROLE/PERSONAL); 排除 ORG/POSITION (后者走 /user-org/*).

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
| `roleExternalId` | `String` | 角色业务键（前端据此回传 assign/revoke） |
| `roleName` | `String` | 角色名 |
| `roleTypeLabel` | `String` | 显示名 (代理层映射) |
| `targetType` | `String` | (前端展示用, 同 `roleTypeCode`) |
| `relationId` | `Long` | POSITION 角色对应的所属组织 abstract_role.id（permission-center 内部主键）; 其他类型为 null. 内部参考字段, 前端不直接消费 |
| `relationExternalId` | `String` | POSITION 角色对应的所属组织业务键（= sys_org.id 字符串，permission-center 返回）; admin 据此解析组织名（P2-1：替代用 relationId 错查 sys_org）|
| `relationOrgName` | `String` | POSITION 角色对应的所属组织名 (代理层用 relationExternalId 查 sys_org 补) |
| `validFrom` | `LocalDateTime` | |
| `validTo` | `LocalDateTime` | |

**门禁**: `ADMIN_USER:VIEW@userId` (admin-service 层); permission-center 层不再额外要求 (本接口为读).

**代理动作**: admin 调 `permission-center /api/perm/user-role/list` (业务键 `subjectTypeCode=ADMIN_USER, subjectExternalId={userId}`); permission-center 响应含 `relationExternalId`（关联组织角色业务键），admin 据此查 `sys_org` 补 `relationOrgName`. 返回业务键 `(roleTypeCode, roleExternalId)` 替代 roleId.

**错误码段**: 10500-10519

**当前差距**: 接口未实现.

**验收要点**:
- `userId` 不存在 → `BizException(USER_NOT_FOUND)`.
- 前端通过 `(roleTypeCode, roleExternalId)` 业务键回传 assign/revoke，不再使用 roleId.

---

#### 4.4.2 `POST /user-role/assign` 🔧

**目的**: 给用户分配功能角色 (BASIC_ROLE/GROUP_ROLE/PERSONAL). admin 代理直调 permission-center `/api/perm/user-role/assign`.

**请求 DTO**: `UserRoleAssignReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `roleTypeCode` | `String` | 是 | 必须为 BASIC_ROLE/GROUP_ROLE/PERSONAL, 否则拒绝 |
| `roleExternalId` | `String` | 是 | 角色业务键 |
| `validFrom` | `LocalDateTime` | 否 | |
| `validTo` | `LocalDateTime` | 否 | |

**响应**: `PermResult<Void>`

**门禁**: `ROLE:MANAGE@roleExternalId` — **由 permission-center 兜底**（admin 层不做预检，P1-1 修复：admin 预检曾把 roleExternalId 当 ROLE resource_entity.code 传 auth/check，而 ROLE 权限实际挂 abstract_role.id 维度，预检语义错位会误拒；permission-center `UserManageAppServiceImpl.assignRole` 用正确 abstract_role.id 经 `getDeniedIds(ROLE, MANAGE)` 校验）。

**代理动作**:
1. 校验 `roleTypeCode∈{BASIC_ROLE, GROUP_ROLE, PERSONAL}` (若为 ORG/POSITION → `BizException(ROLE_TYPE_NOT_SUPPORTED, 应走 /user-org/*)`).
2. 翻译 `userId → subjectTypeCode=ADMIN_USER, subjectExternalId={userId}`; 直接用入参 `(roleTypeCode, roleExternalId)`.
3. 调 permission-center `/api/perm/user-role/assign` (`items[]` 单元素); 透传 `validFrom/validTo`.
4. **不**写 sys_sync_task.

**错误码段**: 10520-10549

**当前差距**: 接口未实现.

**验收要点**:
- `roleTypeCode` 为 ORG/POSITION → `BizException(ROLE_TYPE_NOT_SUPPORTED)`.
- permission-center 返回非 200 → 透传错误码与 message; admin 层不吞错.

---

#### 4.4.3 `POST /user-role/revoke` 🔧

**目的**: 回收用户的功能角色.

**请求 DTO**: `UserRoleRevokeReq`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | |
| `roleTypeCode` | `String` | 是 | 同 assign 约束 |
| `roleExternalId` | `String` | 是 | 角色业务键 |

**响应**: `PermResult<Void>`

**门禁**: `ROLE:MANAGE@roleExternalId` — **由 permission-center 兜底**（同 assign，P1-1 修复；permission-center `revokeRolesBatch` 用 abstract_role.id 经 `getDeniedIds(ROLE, MANAGE)` 校验）。

**代理动作**:
1. 校验 `roleTypeCode` (同 assign).
2. 调 permission-center `/api/perm/user-role/revoke` (`items[]` 含 `roleTypeCode + roleExternalId + subjectTypeCode + subjectExternalId`, `relationId=null`).
3. 不写 sys_sync_task.

**错误码段**: 10550-10579

**当前差距**: 接口未实现.

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
| `roleExternalId` | `String` | 角色业务键（前端据此回传 assign/revoke） |
| `roleName` | `String` | 角色名称 |
| `roleTypeLabel` | `String` | 角色类型显示名 |

**门禁**: `ADMIN_ROLE:VIEW`.

**代理动作**: 调 permission-center `/api/perm/abstract-role/tree` 或 list 接口, 按 `roleTypeCodes` 过滤.

---

## 5. 已对齐接口汇总 (✅ 6 项)

| # | 接口 | 来源 record | 备注 |
|---|------|-------------|------|
| 1 | `POST /org/tree` | `OrgQuery` | 待补 `operationCode/treeConfigId` 字段; 待包装为 `{ items }` |
| 2 | `POST /org/page` | `OrgPageReq` | 含 `orgId` 子树筛选; 已对齐 |
| 3 | `POST /org/users` | `IdReq` | 前端 mock 入参字段名为 `orgId`, 待 Phase 2 调整为 `id` |
| 4 | `POST /user/update` | `UserUpdateReq` | 已对齐 |
| 5 | `POST /user-org/list` | `IdReq` | 已对齐; 待包装 `{ items }` |
| 6 | `POST /role/list` | `RoleListQueryReq` | 已对齐 |

---

## 6. 验收标准

Phase 2 后端实现以上 22 个接口后, 必须满足:

1. **字段对齐**: 前端 `frontend/src/api/user-manage.ts` 中所有类型与本契约 record 字段名/类型一一对齐, 不允许不一致.
2. **门禁**: 所有写操作经 `AdminPermissionValidator` 调用 permission-center `auth/check`/`auth/batch-check`; `AdminPermissionValidatorImpl` 不在本地短路判断 (除自我修改豁免).
3. **同步任务**: 所有写操作 (除 /user/reset-password, /user-org/set-primary, /user-role/assign|revoke) 在主事务内写入 `sys_sync_task`, syncAction 严格收敛为 4 类, businessKey 严格遵守 api-contract §6.2.2.4.
4. **错误码段**: admin-service 业务错误使用 10001-19999 段, 系统错误使用 90001-99999 段; `XxxErrorCode` 枚举类不重复定义系统段.
5. **响应壳统一**: 所有接口返回 `PermResult<T>`, 列表不直接返回数组 (由 `PermResultResponseAdvice` 强制); 现有违反此规则的接口 (例如 `/org/tree` 直接返回 `List<OrgResp>`) 列入 Phase 2 修正项.
6. **异常映射**: 业务拒绝抛 `BizException`; 安全拒绝抛 `SecurityException`; 技术故障抛 `SystemException`. 不允许用 `SecurityException` 表达"资源不存在".
7. **默认树身份目录边界**: `/user/create` (带 orgId), `/user/delete`, `/user/enable`, `/user/reset-password`, `/user-org/set-primary` 必须在 AppService 内做默认树边界二次校验, 失败抛 `BizException`.
8. **同步幂等**: 同一 `tenantId + syncAction + businessKeyHash` 下未发送任务可合并为最新 payload; 已 PROCESSING/SUCCESS 的任务不可改, 且旧版本到达 permission-center 后 no-op.

---

## 7. 已确认决策 (设计沉淀)

| # | 决策 | 理由 |
|---|------|------|
| 1 | admin 代理 user-role/* 而非前端直连 permission-center | 避免业务键暴露给前端; 前端只感知数字 ID; admin 内部完成 ID↔业务键翻译 (api-gap-analysis §4 A 方案，已归档 `docs/archive/2026-06-21/`) |
| 2 | `/user/create` 一次性返回 `initialPassword` (明文) | 仅本次返回, 由前端弹窗展示给操作者; 后续无法再获取 |
| 3 | `/user/enable` 启停一体 (`status=0/1`), 不拆 `/user/disable` | 前端 mock 已采用此形态; AppService 内部按 status 派发 ENABLE/DISABLE 门禁码 |
| 4 | `/user-org/assign` 关系级追加, 禁止 wipe 模式 | 防止跨树意外清除 (default-org-tree §3.2); 已存在关系幂等忽略 |
| 5 | `/user-org/set-primary` 首期只允许默认树主归属 | 不能全局清除其他组织树主标记 (api-gap-analysis §3，已归档) |
| 6 | 岗位 = 特殊组织 (`orgType=2`), 走 `/org/*` + `/user-org/*` | org-user-permission-contract.md v1.2 决策; `/user-role/*` 仅服务功能角色 |
| 7 | 候选用户来自默认树可见范围, 新增 `/user/member-candidates` 接口与 `/user/page` 解耦 | api-gap-analysis §2（已归档）; 默认树 = 用户目录/身份池, 不暴露全租户用户 |
| 8 | 写操作必须在主事务内写 sys_sync_task, 收敛为 4 类 syncAction | admin-service.md §同步任务模型; 通过本地消息表 + 调度器重发保障最终一致 |
| 9 | `/user-role/assign|revoke` 不写 sys_sync_task | 功能角色走 permission-center 正式管理 API; sys_sync_task 仅承载 SYS_USER_ORG 派生关系 (admin-service.md §同步任务模型) |
| 10 | admin-service 不存储 permission-center 内部 ID | 跨服务统一用业务键; 业务键格式严格按 api-contract.md §6.2.2.4 |
| 11 | `IdReq` 入参字段名为 `id` 而非 `orgId/userId` | 复用公共 record; 前端在 Phase 2 调整 mock 字段 (例如 `/org/users` 入参 `{ id }`) |
| 12 | 列表响应统一用 `{ items: [...] }` 包装, 即便是非分页列表 | project-rules.md §1.3 强约束; 现有违反此规则的接口列入 Phase 2 修正项 (如 `/org/tree`, `/role/list`, `/user-org/list`, `/org/users`) |
| 13 | `/user/update` 自我修改业务豁免 | 在 AppService 调用门禁前判断 `operatorId == id` 跳过门禁; 不放在门禁层 |
| 14 | 错误码段 admin-service 子分配 | 用户域 10001-10299 / 组织域 10300-10499 / 关系域 10400-10499 / 角色代理 10500-10599 / 其他保留 10600-19999 |

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

## 附录 B. 错误码段建议 (admin-service 10001-10599 区间)

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
