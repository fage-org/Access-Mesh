# 用户角色代理修复计划（admin-service P1/P2 修复）

> 状态：已完成（待最终验收）
> 关联设计：
> - [docs/design/services/admin-service-api-contract.md](../design/services/admin-service-api-contract.md) §4.1.1 / §4.1.2 / §4.1.5 / §4.4
> - [docs/design/permission-center/api-contract.md](../design/permission-center/api-contract.md) §6.2.2.4（user-role sync）、§6.2.4
> - [docs/design/permission-center/overview.md](../design/permission-center/overview.md) "对外接口仅用业务键"设计哲学
> - [docs/design/org-user-permission-contract.md](../design/org-user-permission-contract.md) v1.2 权限矩阵
> - [docs/plans/org-user-page-impl-plan.md](org-user-page-impl-plan.md)（前置 P1 实现，本计划是其后续修复）
>
> 创建日期：2026-06-14
> 触发：上一轮 P1 16 接口实现后，用户提交 6 项 P1/P2 findings；6 视角 ultracode 审查暴露 12 条已确认 high+ 担忧 + 17 条计划未覆盖的同类问题。
>
> 用户已确认决策（2026-06-14，后续修订 2026-06-15）：
> ① P1-3 方向演进 —— 原决策"admin 代理层自己反查 roleId"，后改为**前端直接传业务键 `(roleTypeCode, roleExternalId)`**（M5），无需反查，也不改 perm-center 契约
> ② 门禁码 —— 原决策"按契约用 GRANT/REVOKE"，后回退为**ROLE:MANAGE**（M3 修订），admin 层与 perm-center 内部统一，无双层门禁
> ③ 本 PR 范围 —— 采纳全部 11 项（含可见性裁剪、fail-safe）

---

## 1. 目标

修复用户角色代理链路（admin-service `/user-role/{list,assign,revoke}`、`/user/member-candidates`、`/user/page`、`/user/delete` 等）共 6 项已确认 P1/P2 缺陷，并把同根因暴露的 12 条扩散问题一次性收敛，保证：

1. **接口可调通**：assign/revoke 不再被 `@NotBlank` 校验拒绝。
2. **门禁与契约对齐**：admin 层用 `ROLE:MANAGE`（M3 回退后与 perm-center 统一），双层都通过。
3. **业务键边界干净**：admin 代理层不把 `parseLong(externalId)` 当 roleId，不污染 perm-center 业务键导向契约。
4. **同步 envelope 一致**：`relationKey` 在 createUser / deleteUser / UserOrgService / SyncTaskBuilder 4 处用同一 helper 拼装，POSITION 用户的 BIND/UNBIND businessKey 不再对不上。
5. **可见性裁剪到位**：默认树后代查询不再给到非操作者可见的用户/组织（消除 §4.1.1 / §4.1.2 / §4.1.5 越权风险）。
6. **同步链 fail-safe**：permission-center 端 user 删除时本地兜底清 `user_role`，即使 admin 端 envelope 漏发或乱序也不残留。

## 2. 非目标

- **不**在 admin 端新建独立的 GRANT/REVOKE 操作码（M3 修订后统一用 ROLE:MANAGE，AdminOperationCode 不再定义 GRANT/REVOKE 常量）。
- **不**改 permission-center 的 `RoleResp` / `UserRolesResp.RoleSummary` 增加内部 ID 字段（违反业务键导向设计哲学）。
- **不**对 `BatchAuthCheckReq` 上限做服务端硬限制（仅 admin 端分批 + cap）。
- **不**做 `getDescendantIdsIncludingSelf` cap 的全局推广（只裁剪默认树场景，其它路径单独 issue）。
- **不**重构 perm-common 与 permission-center 内部 DTO 的双份维护问题（中期债务，单独 issue）。

## 3. 背景：6 项已确认 P1/P2 + 12 条审查发现

### 3.1 用户提交的 6 项原始 findings

| ID | 严重度 | 问题 | 根因 |
|----|--------|------|------|
| P1-A | P1 阻断 | assign/revoke 传 `domainCode=null`，但 perm-common `UserAssignRoleReq.AssignItem.domainCode` / `UserRoleBatchRevokeReq.RevokeItem.domainCode` 都是 `@NotBlank`；permission-center controller 有 `@Valid`，请求被 400 拒绝 | DTO 校验语义与代理层调用契约不一致；`RoleResp` 不返回 `domainCode`，代理层无法填值 |
| P1-B | P1 阻断 | admin 代理门禁码与 perm-center 内部不一致：原契约写 `ADMIN_ROLE:GRANT@roleId`/`ADMIN_ROLE:REVOKE@roleId`，但 perm-center 内部用 `ROLE:MANAGE`，双层门禁不统一 | 门禁码语义错位；最终决策统一为 `ROLE:MANAGE@roleExternalId` |
| P1-C | P1 阻断 | `RoleProxyServiceImpl.listUserRoles` 把 `roleExternalId` 强行 `parseLong` 当 roleId 返回；功能角色（externalId 为业务键字符串）必崩 | `UserRolesResp.RoleSummary` 只暴露业务键，不返回 `abstract_role.id`；代理层用 `parseRoleId` 兜底 null |
| P1-D | P1 越权 | `/user/member-candidates` 取默认树根全量后代，没按操作者 `ADMIN_ORG:VIEW` 可见范围裁剪 | 实现绕过"操作者可见范围"语义，仅做了入口边界 + 目标 org 排除 |
| P1-E | P1 残留 | `/user/delete` UNBIND envelope 写死 `relationKey = "ORG:" + orgId`；岗位 POSITION 用户被删时 perm-center 残留 user_role | 与 `UserOrgServiceImpl` 的 `roleTypeCode + ":" + orgId` 不一致；硬编码 prefix |
| P2-F | P2 显示 | `listUserRoles` 丢弃 perm-center 返回的 `validFrom/validTo`，固定填 null | 复制粘贴时填了 null 占位但没回填 |

### 3.2 审查发现的 12 条已确认扩散问题

> 来源：6 视角 workflow 审查（架构合规性 / 安全语义 / 数据一致性 / API 兼容性 / 性能规模 / 完整性批判），48 个 agent 对抗性核实。

| ID | 严重度 | 问题 | 关联原 finding |
|----|--------|------|----------------|
| EXT-1 | high | `RoleProxyServiceImpl.fetchUserRoles` line 458 同样用 `parseRoleId` 把业务键转 Long，影响登录用户信息的 `roles` 字段 | P1-C 水下复发点 |
| EXT-2 | blocker | `UserServiceImpl.createUser` line 204 同样写死 `"ORG:" + req.orgId()`；line 203 已算出 `roleTypeCode` 但没用 | P1-E 水下复发点 |
| EXT-3 | high | `UserServiceImpl.pageUsers` line 466 在 `req.orgId` 为空时只做 `ADMIN_USER:VIEW` 类型级，没按 `ADMIN_ORG:VIEW` 可见范围裁剪 | P1-D 水下复发点（§4.1.1 越权） |
| EXT-4 | high | `validateUsersInDefaultTreeScope` line 793-820 只验"目标用户在默认树边界内"，没验"操作者对该用户的归属组织有 `ADMIN_ORG:VIEW`"。被 delete/enable/reset-password 共用 | P1-D 水下复发点（§4.1.5 越权） |
| EXT-5 | high | `createUser/deleteUser` 把 `defaultConfigs.get(0).getRootOrgId()` 直接当 `treeRootExternalId` 喂给 `userOrgBind/Unbind`；当用户在多默认树或岗位场景下，envelope 桶 scopeKey 错配 | 与 `UserOrgServiceImpl.assignUserToOrgs` 用的 `orgTreeConfigDomainService.resolveTreeRootExternalId(tenantId, orgId)` 不一致 |
| EXT-6 | blocker | ~~MANAGE→GRANT/REVOKE 切换形成双层门禁~~ → 已通过 M3 修订解决：admin 层统一使用 `ROLE:MANAGE`，与 perm-center 内部 `engine.getDeniedIds(... ROLE ... MANAGE)` 一致，无双层门禁 | P1-B 关联（已解决） |
| EXT-7 | high | `permission-center.PermissionCheckAppServiceImpl.batchCheck` line 102-127 是 `for` 循环逐条 `engine.query`，没批处理。1k 默认树后代会触发 1000 次 SQL 查询 | P1-D 性能放大风险 |
| EXT-8 | high | admin `SyncTaskDomainServiceImpl.enqueueAll` line 138-145 同样 `for` 循环逐条 `insert`。删 100 用户产生 300+ 次 INSERT | P1-E 关联性能 |
| EXT-9 | high | `UserServiceImpl.deleteUser` 三步在同一事务里没问题，但不同 envelope 之间没批次约束。publisher 可能乱序处理（先 user DELETE 再 UNBIND，导致 perm-center 残留 user_role） | P1-E 关联一致性 |
| EXT-10 | medium | perm-common 和 permission-center 内部 DTO 双份维护是结构性债务（每次 DTO 演进必双改） | P1-A 暴露的元问题 |
| EXT-11 | medium | `getDescendantIdsIncludingSelf` 在 5 处使用，全无规模限制；超大默认树会爆 | EXT-3/EXT-4 关联性能 |
| EXT-12 | medium | `permission-center` 内部 `UserAssignRoleReq.AssignItem.domainCode` 也是 `@NotBlank`；perm-common 单独放宽不够 | P1-A 双份 DTO 必须同改 |

### 3.3 设计决策（用户已确认）

| 决策点 | 选项 | 决策 |
|--------|------|------|
| `domainCode` 处理 | A) perm-common 放宽 `@NotBlank`；B) perm-center `RoleResp` 加字段；C) admin 多一次反查 | **A**：放宽 `@NotBlank`，配合服务端跨字段业务校验（ORG/POSITION 必带 domainCode） |
| 门禁码 | A) 用契约 GRANT/REVOKE；B) 保留 MANAGE 改契约 | **B（修订）**：最终统一为 `ROLE:MANAGE`，admin 层与 perm-center 内部一致，消除双层门禁 |
| 可见性裁剪 | A) `engine.getDeniedIds` 批量过滤；B) perm-center 暴露专用接口；C) 不裁剪 | **A**：`batchCheckAuth` 批量过滤默认树后代，配合操作者级缓存 |
| roleId 补全 | A) admin 反查（保契约纯净）；B) perm-center `RoleSummary` 加字段；C) 前端直接传业务键 | **C（修订）**：前端传 `(roleTypeCode, roleExternalId)`，admin 不需要 roleId，**保护 perm-center 业务键导向契约** |
| 本 PR 范围 | A) 全部一次到位；B) 最小集；C) 拆分迭代 | **A**：13 项主改动 + 3 项配套全部一次到位 |

---

## 4. 修复方案（13 项主改动 M1-M13 + 3 项配套 S1-S3）

> 用户决策"采纳全部 11 项"指 6 视角审查后归并的逻辑批次（DTO+操作码 / 业务键边界 / relationKey 收敛 / 可见性裁剪 / fail-safe），落到具体改动 = 13 项主改 + 3 项配套。命名 M = Main change，S = Supporting change。

### 第 1 批：DTO + 操作码（基础设施，4 项）

#### M1：DTO 放宽 `@NotBlank`（双端同改）

| 文件 | 改动 |
|------|------|
| `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/req/UserAssignRoleReq.java` | `AssignItem.domainCode` 去掉 `@NotBlank`；javadoc 标注"功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）允许 null 表示全局域；ORG/POSITION 必填" |
| `perm-sdk/perm-common/.../UserRoleBatchRevokeReq.java` | `RevokeItem.domainCode` 同上 |
| `permission-center/src/main/java/cn/ac/fage/accessmesh/permission/dto/req/UserAssignRoleReq.java` | 内部 DTO 同步放宽 |
| `permission-center/.../dto/req/UserRoleBatchRevokeReq.java` | 同步放宽 |

**校验**：服务端实现 (`UserManageAppServiceImpl` line 257/288/328 等) 已全部用 `item.domainCode() != null ? item.domainCode() : ""` 兜底，**无需改 service 实现**。

**关联**：覆盖 P1-A、EXT-12。

#### M2：服务端跨字段业务校验（Feature flag 控制）

| 文件 | 改动 |
|------|------|
| `permission-center/.../UserManageAppServiceImpl.java` `assignRole` 入口 | 新增 Feature flag `@Value("${permission.assign.strict-domain-check:true}") boolean strictDomainCheck`；当 `strictDomainCheck=true` 时校验：`for item in req.items()`：若 `roleTypeCode IN (ORG, POSITION) && (domainCode == null \|\| domainCode.isBlank())` → 抛 `BizException(ErrorCode.INVALID_PARAM, "ORG/POSITION 角色必须指定 domainCode")` |
| `UserManageAppServiceImpl.assignRolesBatch` | 同样校验 |
| `UserManageAppServiceImpl.revokeRolesBatch` | 同样校验 |

**上线策略**：先设 `strict-domain-check=false` 部署观察一轮，确认存量无脏数据后改为 `true`。

**关联**：覆盖审查担忧 "DTO 放宽缺补偿性校验"。

#### M3：门禁码切回 ROLE:MANAGE（与 perm-center 统一）

| 文件 | 改动 |
|------|------|
| `admin-service/.../RoleProxyServiceImpl.java` `assignRole` | `AdminOperationCode.GRANT` → `ROLE:MANAGE`（资源类型 `AdminResourceType.ROLE`，操作码 `MANAGE`） |
| `RoleProxyServiceImpl.revokeRole` | `AdminOperationCode.REVOKE` → `ROLE:MANAGE`（同上） |

> **修订说明（2026-06-15）**：原方案切到 GRANT/REVOKE，但评审后确认 admin 层应与 perm-center 内部
> 保持一致，统一使用 `ROLE:MANAGE`。GRANT/REVOKE 作为 perm-center 内部操作码存在，
> admin 层不再单独定义。`AdminOperationCode.MANAGE` 常量已恢复。

**关联**：覆盖 P1-B。

#### M4：`AdminOperationCode.MANAGE` 常量处理

> **修订说明（2026-06-15）**：原方案删除 `MANAGE` 常量（Phase 1 时 M3 切到 GRANT/REVOKE，
> `MANAGE` 无引用）。但 M3 修订后回退到 `ROLE:MANAGE`，`AdminOperationCode` 不再定义
> `GRANT`/`REVOKE` 常量。权限校验使用 `AdminResourceType.ROLE` + 操作码 `"MANAGE"` 字符串，
> `AdminOperationCode` 中不保留 `MANAGE` 常量（门禁调用直接传字符串或使用 `RoleProxyServiceImpl`
> 内部常量），避免 `AdminOperationCode` 膨胀。

**关联**：覆盖 P1-B 收尾。

---

### 第 2 批：业务键边界（admin 接口改为业务键导向，3 项）

> **方向修订（2026-06-15 用户确认）**：原方案 M5 是"admin 反查 roleId"，但核心问题是
> admin 为什么需要 roleId？前端只是拿 roleId 当不透明句柄回传。如果前端直接传业务键
> (roleTypeCode, roleExternalId)，admin 就可以直接调 perm-center，完全不需要反查。
> 这比新增 resolve-ids 端点更干净——**不改 perm-center 契约，也不需要 RoleResolver**。

#### M5：admin DTO 改业务键 + RoleProxyServiceImpl 逻辑简化

**核心变更**：assign/revoke 接口从收 `roleId` 改为收 `(roleTypeCode, roleExternalId)`，
list 接口从返 `roleId` 改为返 `(roleTypeCode, roleExternalId)`。

**后端 DTO 改动**：

| 文件 | 改动 |
|------|------|
| `admin-service/.../dto/req/UserRoleAssignReq.java` | `Long roleId` → `@NotBlank String roleTypeCode` + `@NotBlank String roleExternalId` |
| `admin-service/.../dto/req/UserRoleRevokeReq.java` | `Long roleId` → `@NotBlank String roleTypeCode` + `@NotBlank String roleExternalId` |
| `admin-service/.../dto/resp/UserRoleItemResp.java` | `Long roleId` → `String roleTypeCode` + `String roleExternalId` |

**后端 Service 改动**：

| 文件 | 改动 |
|------|------|
| `admin-service/.../controller/UserRoleController.java` | assign/revoke 方法签名适配新 DTO |
| `admin-service/.../service/RoleProxyService.java` | `assignRole(Long userId, Long roleId, ...)` → `assignRole(Long userId, String roleTypeCode, String roleExternalId, ...)`；revokeRole 同理 |
| `admin-service/.../service/impl/RoleProxyServiceImpl.java` `assignRole` | 删除"1. 实例级门禁"中 `String.valueOf(roleId)` → 改用 `roleExternalId` 作资源实例标识；删除 `resolveRoleRef(roleId)` 调用，直接用入参的 `roleTypeCode` / `roleExternalId`；校验功能角色类型改用 `roleTypeCode` 直接判断 |
| `RoleProxyServiceImpl.revokeRole` | 同上简化 |
| `RoleProxyServiceImpl.listUserRoles` line 564-583（**P1-C 修复**） | 删除 `parseRoleId(r.roleExternalId())`；改为 `new UserRoleItemResp(r.roleTypeCode(), r.roleExternalId(), r.roleName(), r.roleTypeLabel(), r.validFrom(), r.validTo())` |
| `RoleProxyServiceImpl.fetchUserRoles` line 442-460（**EXT-1 修复**） | 同样删除 `parseRoleId`，改用业务键 |

**前端改动**：

| 文件 | 改动 |
|------|------|
| `frontend/src/api/user-manage.ts` | `UserRoleItem` 类型：`roleId: number` → `roleTypeCode: string; roleExternalId: string`；`UserRoleAssignReq` / `UserRoleRevokeReq` 同理 |
| `frontend/src/views/system/user/components/UserDetailPanel.vue` | `handleRevoke(role)` 传参改为 `{ userId, roleTypeCode: role.roleTypeCode, roleExternalId: role.roleExternalId }`；`handleAssignRole()` 同理改为传 `roleTypeCode + roleExternalId` |

**关联**：覆盖 P1-C、EXT-1。**消除 RoleResolver 需求**。

#### M6：删除 `RoleProxyServiceImpl.parseRoleId` 私有方法 + `resolveRoleRef` 方法

M5 改完后，`parseRoleId` 和 `resolveRoleRef` 均不再被任何路径调用，删除避免后续误用。

#### M7：`listUserRoles` 透传 `validFrom/validTo`（合并到 M5 实现）

M5 重构 `listUserRoles` 时一并修复 `validFrom/validTo` 透传（原 line 580-581 的 `null, null` → `r.validFrom(), r.validTo()`）。

**关联**：覆盖 P2-F。
---

### 第 3 批：relationKey 收敛（统一 helper，3 项）

#### M8：抽 `UserOrgKeys` helper

**新增文件**：`admin-service/src/main/java/cn/ac/fage/accessmesh/admin/support/UserOrgKeys.java`

> **修正（2026-06-15）**：初版错误地使用 `roleTypeCode` 当前缀（POSITION → `POSITION:xxx`），
> 但契约 §6.2.2.4 明确 relationKey **固定格式 `ORG:{orgExternalId}`**，无论角色类型。
> 已修正为 `relationKey(Object orgIdOrExternalId)`，前缀固定 `ORG:`。

```java
public final class UserOrgKeys {
    private UserOrgKeys() {}

    /**
     * relationKey 固定格式 ORG:{orgIdOrExternalId}
     * 与 permission-center api-contract.md §6.2.2.4 对齐
     * 即使角色类型为 POSITION，前缀也使用 ORG
     */
    public static String relationKey(Object orgIdOrExternalId) {
        if (orgIdOrExternalId == null) {
            throw new IllegalArgumentException("orgIdOrExternalId 不能为空");
        }
        return "ORG:" + orgIdOrExternalId;
    }
}
```

#### M9：UserServiceImpl 两处替换

| 文件 | 改动 |
|------|------|
| `admin-service/.../UserServiceImpl.java` `createUser` line 204（**EXT-2 修复**） | `String relationKey = "ORG:" + req.orgId();` → `String relationKey = UserOrgKeys.relationKey(req.orgId());` |
| `UserServiceImpl.deleteUser` line 322（**P1-E 修复**） | `String relationKey = "ORG:" + uo.getOrgId();` → `String relationKey = UserOrgKeys.relationKey(uo.getOrgId());` |

#### M10：UserOrgServiceImpl + SyncTaskBuilder 收敛到 helper

| 文件 | 改动 |
|------|------|
| `admin-service/.../UserOrgServiceImpl.java` line 132 | `String relationKey = roleTypeCode + ":" + assoc.getOrgId();` → `UserOrgKeys.relationKey(assoc.getOrgId())` |
| `UserOrgServiceImpl.java` line 208 | `String relationKey = roleTypeCode + ":" + orgId;` → `UserOrgKeys.relationKey(orgId)` |
| `admin-service/.../SyncTaskBuilder.java` line 931 | `String relationKey = roleTypeCode + ":" + orgExternalId;` → `UserOrgKeys.relationKey(orgExternalId)` |

**额外修复 EXT-5**：

| 文件 | 改动 |
|------|------|
| `UserServiceImpl.createUser` line 206-213 | `Long defaultRootId = defaultConfigs.get(0).getRootOrgId();` → `String treeRootExternalId = orgTreeConfigDomainService.resolveTreeRootExternalId(tenantId, req.orgId());` |
| `UserServiceImpl.deleteUser` line 316-325 | 批量解析：`Map<Long, String> rootExternalIds = orgTreeConfigDomainService.resolveTreeRootExternalIds(tenantId, orgIds);`；for 循环内取 `rootExternalIds.get(uo.getOrgId())` |

**关联**：覆盖 P1-E、EXT-2、EXT-5。

---

### 第 4 批：可见性裁剪（消 §4.1.1 / §4.1.2 / §4.1.5 越权风险，2 项）

#### M11：抽 `OrgVisibilityService`

**新增文件**：`admin-service/src/main/java/cn/ac/fage/accessmesh/admin/service/security/OrgVisibilityService.java`

```java
public interface OrgVisibilityService {
    /**
     * 过滤出操作者通过 ADMIN_ORG:VIEW 可见的组织子集
     * @param operatorId 操作者用户 ID（默认从 StpUtil 取）
     * @param orgIds     候选组织 ID 集合
     * @return 可见的子集（保留顺序）
     */
    Set<Long> filterVisibleOrgIds(Long operatorId, Collection<Long> orgIds);

    /**
     * 取操作者在默认树范围内可见的所有组织 ID
     * @param tenantId 租户 ID
     * @param operatorId 操作者用户 ID
     * @return 默认树后代 ∩ 操作者 ADMIN_ORG:VIEW 通过的子集
     */
    Set<Long> getOperatorVisibleDefaultTreeOrgIds(Long tenantId, Long operatorId);
}
```

**实现**：
- 内部用 `permissionFeignClient.batchCheckAuth(BatchAuthCheckReq{ADMIN_ORG, orgIds, VIEW})` 批量校验。
- 缓存：`(operatorId, defaultRootId)` → 可见 orgId Set，60s TTL。
- 分批：`orgIds` 超过 500 时按 500 一批分次调用（防 EXT-7 服务端 N 次循环放大；服务端 N 次循环优化是本 PR 范围外的 issue）。

#### M12：4 处共用 OrgVisibilityService

| 调用点 | 改动 |
|--------|------|
| `UserServiceImpl.memberCandidates` line 663-672（**P1-D 修复**） | 取默认树后代后，调 `orgVisibilityService.filterVisibleOrgIds(operatorId, descendantIds)` 裁剪 |
| `UserServiceImpl.pageUsers` line 466（**EXT-3 修复**） | `req.orgId == null` 时也按操作者可见默认树裁剪：用 `orgVisibilityService.getOperatorVisibleDefaultTreeOrgIds(tenantId, operatorId)` 作为 `orgIds` 过滤；`req.orgId != null` 时验证 orgId 在可见集合中 |
| `UserServiceImpl.validateUsersInDefaultTreeScope` line 793-820（**EXT-4 修复**） | 改为 `validateUsersUnderOperatorVisibleScope(tenantId, operatorId, userIds)`：取每个 userId 的所属组织，验证至少一个组织在 `getOperatorVisibleDefaultTreeOrgIds(tenantId, operatorId)` 中；否则抛 `USER_NOT_IN_OPERATOR_VISIBLE_SCOPE`（新错误码）|
| 错误码 `AdminErrorCode` | 新增 `USER_NOT_IN_OPERATOR_VISIBLE_SCOPE(11016, "用户不在操作者可见范围内")` |

**关联**：覆盖 P1-D、EXT-3、EXT-4。

---

### 第 5 批：fail-safe（防一致性故障，1 项）

#### M13：permission-center user 删除延迟补偿——兜底清 user_role

> **策略修订（2026-06-15 用户确认）**：原方案是在 OP_DELETE 分支直接清 user_role，
> 但 admin 端已有 UNBIND envelope 机制，直接清会形成双写。改为延迟补偿：
> 仅在确认 UNBIND envelope 未到达时才清理。

**实现方案**：

1. **新增 `UserRoleOrphanCleanupTask` 定时任务**（permission-center）

| 文件 | 改动 |
|------|------|
| `permission-center/.../scheduler/UserRoleOrphanCleanupTask.java`（新增） | `@Scheduled(fixedDelay = "${permission.orphan-cleanup.interval:300000}")` 扫描 `abstract_user` 已软删但 `user_role` 仍存活的孤儿记录：`SELECT ur.* FROM user_role ur JOIN abstract_user au ON ur.abstract_user_id = au.id WHERE au.deleted = true AND ur.deleted = false AND ur.updated_at < :cutoff`（cutoff = now - 5min，给 envelope 处理留窗口） |
| `permission-center/.../mapper/UserRoleMapper.java` | 新增 `List<UserRole> selectOrphansByCutoff(Long tenantId, LocalDateTime cutoff)` |
| `permission-center/.../mapper/UserRoleMapper.xml` | 对应 SQL |
| `application.yml` | `permission.orphan-cleanup.interval: 300000`（5 分钟，可调） |

2. **幂等保证**：`softDeleteBatch` 已内置幂等（已删的跳过），与 UNBIND envelope 双清不冲突。

3. **监控**：扫到孤儿时 `log.warn` 记录 `(tenantId, userId, roleId)`，便于排查 envelope 丢失根因。

**关联**：覆盖 EXT-9（envelope 顺序/丢失风险兜底）。

---

### 配套：前端 + 种子 + 契约测试

#### S1：前端 perm 串同步

| 文件 | 改动 |
|------|------|
| `frontend/src/views/system/user/utils/perms.ts` | `USER_ROLE_ASSIGN` 和 `USER_ROLE_REVOKE` 统一使用 `ROLE:MANAGE`（与后端 admin 层门禁一致） |
| 前端 mock 角色矩阵 | `frontend/mock/auth.ts` 或角色定义文件中 admin/hr 的 perm 列表确保含 `ROLE:MANAGE` |

#### S2：种子数据核实 + 补丁

**核实步骤**：
1. `grep -E "ROLE.*MANAGE" docs/design/schema/permission-center*.sql` 核实 perm-center 内部 ROLE 资源类型的 MANAGE 操作码种子是否存在
2. 若 perm-center 内部 ROLE:MANAGE 缺种子 → 补 `INSERT INTO operation_permission`（若 perm-center 服务实际启动时已自动创建则跳过；以 `mvn test` 集成测试为准）
3. 角色矩阵核实：admin 角色（admin/hr/sec/auditor）需持有 `ROLE:MANAGE`（admin 层与 perm-center 内部统一，无双层门禁）

**关联**：覆盖 EXT-6（已通过 M3 修订统一为 ROLE:MANAGE，消除双层门禁）。

#### S3：契约一致性测试

**新增文件**：`admin-service/src/test/java/cn/ac/fage/accessmesh/admin/contract/PermCommonReqContractTest.java`

```java
@Test
void perm_common_dto_should_match_permission_center_internal_dto() {
    // 反射比对 RecordComponent[] 结构
    // perm-common UserAssignRoleReq vs permission.dto.req.UserAssignRoleReq
    // perm-common UserRoleBatchRevokeReq vs permission.dto.req.UserRoleBatchRevokeReq
    // 字段名、类型、注解（@NotBlank / @NotNull / @Size 等）必须一一对应
}
```

**目的**：防 EXT-10 类问题（双份 DTO 漏改一份）静默复发。

---

## 5. 实施顺序

| 阶段 | 改动批次 | 编译/测试边界 |
|------|---------|---------------|
| 1 | M1（perm-common DTO 放宽）+ M3 + M4（admin 门禁码切换 + 删常量）+ S1（前端 perms.ts 同步切换）| ✅ 已完成（2026-06-14） |
| 2 | M2（permission-center 跨字段校验，Feature flag） | `mvn compile -pl permission-center` |
| 3 | M8（UserOrgKeys helper） | `mvn compile -pl admin-service` |
| 4 | M9 + M10（UserServiceImpl + UserOrgServiceImpl + SyncTaskBuilder 收敛 + EXT-5 批量解析） | ✅ 已完成（2026-06-15） |
| 5 | M5 + M6 + M7（DTO 改业务键 + 删 parseRoleId/resolveRoleRef + 透传 validFrom/To）| ✅ 已完成（2026-06-15） |
| 6 | M11 + M12（OrgVisibilityService + 4 处共用） | ✅ 已完成（2026-06-15） |
| 7 | M13（permission-center 延迟补偿定时任务） | ✅ 已完成（2026-06-15） |
| 8 | 配套 S2（种子核实）+ S3（契约测试） | ✅ 已完成（2026-06-15） |

每阶段完成后运行 `mvn compile`，第 8 阶段全跑 `mvn test`。

**配套 S2 / S3 移到第 8 阶段；S1 已前置到第 1 阶段（必须与 M3/M4 同步切换）。**

---

## 6. 验收标准

### 6.1 功能可用

- [ ] `/user-role/assign` 传 `domainCode=null`（功能角色场景）请求成功，permission-center 服务端按全局域处理
- [ ] `/user-role/assign` 传 `roleTypeCode=ORG && domainCode=null` 在 `strict-domain-check=true` 时被服务端 `BizException` 拒绝（M2）
- [ ] `/user-role/assign` 传 `roleTypeCode=ORG && domainCode=null` 在 `strict-domain-check=false` 时不报错（M2 feature flag）
- [ ] `/user-role/list` 返回 `roleTypeCode + roleExternalId`（不再有 roleId），前端据此回传 assign/revoke
- [ ] `/user-role/list` 返回的 `validFrom/validTo` 与 perm-center 一致，不再固定 null
- [ ] `/user/member-candidates` 仅返回操作者通过 `ADMIN_ORG:VIEW` 可见的默认树用户
- [ ] `/user/page` 在 `req.orgId == null` 时按操作者可见范围裁剪
- [ ] `/user/delete` 对 ORG 用户 / POSITION 用户都正确生成 UNBIND envelope 且 `relationKey` 与 BIND 时一致
- [ ] `/user/create` 指定 POSITION orgId 时 BIND envelope `relationKey` 是 `ORG:{orgId}`（与契约 §6.2.2.4 一致，前缀固定 ORG）

### 6.2 门禁正确

- [x] 操作者持有 `ROLE:MANAGE` → assign/revoke 通过（admin 层与 perm-center 内部统一，无双层门禁）
- [x] 操作者缺 `ROLE:MANAGE` → 被拒绝（与 org-user-permission-contract.md §5 备注³ 对齐）
- [x] 前端 `hasPerms("ROLE:MANAGE")` 与后端入口门禁一致

### 6.3 数据一致性

- [ ] 删 1 个 POSITION 用户后，`select * from user_role where ...` 在 perm-center 中无残留
- [ ] 即使 admin UNBIND envelope 延迟到达或丢失，perm-center 延迟补偿任务在 5 分钟内清掉孤儿 user_role

### 6.4 编译/测试

- [x] `mvn compile` 三模块全通过
- [x] `mvn test -pl admin-service` 通过 `PermCommonReqContractTest` + `OrgVisibilityServiceImplTest` + `SyncTaskBuilderFullSyncTest`
- [x] `pnpm build` 前端通过
- [x] 上一轮 P1 16 接口的回归测试不退化（全量 `mvn test` 通过，153 tests 0 failures）

### 6.5 文档

- [x] 本计划文档 `progress` 章节记录每项完成状态
- [ ] `docs/plans/org-user-page-impl-plan.md` 第 8 节更新（P1 完成 → 含本轮修复）
- [x] S2 核实结果记录在 §8 当前进度（perm-center ROLE:MANAGE 种子已补 + 角色矩阵统一为 ROLE:MANAGE，无双层门禁）
- [x] perm-center ROLE:MANAGE 种子已补（seed-perm-operations.sql），无需额外更新 schema SQL

---

## 7. 风险 / 回滚

| 风险 | 缓解 |
|------|------|
| M5 DTO 改业务键后，前端传参兼容性 | 前后端同提交；旧版前端传 roleId 会 400（字段缺失），无静默错误 |
| M11 OrgVisibilityService batchCheckAuth 在大默认树（>10k）放大延迟 | 60s 缓存吸收高频；分 500 一批降低单次时延；超大租户后续单独优化（EXT-7 服务端 endpoint） |
| M2 跨字段校验过严导致存量数据迁移问题 | Feature flag 控制，上线时先 `strict-domain-check=false`，预跑 `select * from user_role where role_type_code IN ('ORG','POSITION') and (domain_code IS NULL or domain_code='')` 核实存量无脏数据后再开 |
| M13 延迟补偿窗口期（5 min）内数据不一致 | 窗口期短，且仅影响已删用户的 user_role 残留（无安全风险，仅审计不一致）；可通过缩短 interval 调整 |
| 部分用户已配旧权限点但缺 `ROLE:MANAGE` | S1+S2 同步前端 perm 串和种子；rollout 前先核实角色矩阵确保 admin/hr/sec/auditor 持有 `ROLE:MANAGE`；可观测：日志记录 `permissionValidator.checkInstanceLevel` 拒绝事件 |

**回滚策略**：以 git revert 维度，按"第 5 批 → 第 4 批 → 第 3 批 → 第 2 批 → 第 1 批"逆序回滚。各批之间无环依赖（M5 不依赖 M11，M11 不依赖 M2 等）。M2 服务端校验最容易引发回归，可通过 feature flag `permission.assign.strict-domain-check` 控制开关（实施时按需）。

---

## 8. 当前进度

| ID | 改动 | 状态 |
|----|------|------|
| M1 | perm-common DTO 放宽 `@NotBlank` | ✅ 完成（2026-06-14 Phase 1） |
| M2 | permission-center 跨字段业务校验（Feature flag） | ✅ 完成（2026-06-15 Phase 2） |
| M3 | RoleProxyServiceImpl 门禁码切换到 ROLE:MANAGE | ✅ 完成（2026-06-15，回退 ADMIN_ROLE:GRANT/REVOKE → ROLE:MANAGE，与契约对齐） |
| M4 | 删除 `AdminOperationCode.MANAGE` | ✅ 完成（2026-06-14 Phase 1） |
| M5 | DTO 改业务键 + RoleProxyServiceImpl 逻辑简化 | ✅ 完成（2026-06-15 Phase 5，取消 RoleResolver，改为接口收业务键） |
| M6 | 删除 `parseRoleId`（保留 `resolveRoleRef` 用于菜单授权路径） | ✅ 完成（2026-06-15 Phase 5） |
| M7 | listUserRoles 透传 `validFrom/validTo`（合并到 M5） | ✅ 完成（2026-06-15 Phase 5） |
| M8 | 新增 `UserOrgKeys` helper | ✅ 完成（2026-06-15 Phase 3，修正 relationKey 固定 ORG 前缀） |
| M9 | UserServiceImpl createUser/deleteUser 用 helper | ✅ 完成（2026-06-15 Phase 4） |
| M10 | UserOrgServiceImpl + SyncTaskBuilder + EXT-5 批量解析 | ✅ 完成（2026-06-15 Phase 4，relationKey 全部收敛到 UserOrgKeys + resolveTreeRootExternalIds 批量解析） |
| M11 | 新增 `OrgVisibilityService` | ✅ 完成（2026-06-15 Phase 6） |
| M12 | memberCandidates / pageUsers / validateUsersInDefaultTreeScope 共用 | ✅ 完成（2026-06-15 Phase 6，3 处裁剪 + 新增 USER_NOT_IN_OPERATOR_VISIBLE_SCOPE 错误码） |
| M13 | permission-center 延迟补偿定时任务 | ✅ 完成（2026-06-15 Phase 7，UserRoleOrphanCleanupTask + 5min 窗口期 + @EnableScheduling） |
| S1 | 前端 perms.ts + mock 矩阵 | ✅ 完成（2026-06-14 Phase 1，拆分 ASSIGN/REVOKE 双码） |
| S2 | 种子数据核实 | ✅ 完成（2026-06-15 Phase 8，新增 seed-perm-operations.sql 补 ROLE:MANAGE） |
| S3 | PermCommonReqContractTest | ✅ 完成（2026-06-15 Phase 8，10 个断言守卫 domainCode 放宽契约） |

---

## 9. 不在本 PR 处理（已记录、单独 issue）

| 编号 | 问题 | 决策 |
|------|------|------|
| DEFERRED-1 | EXT-7：`PermissionCheckAppServiceImpl.batchCheck` for 循环 N 次 `engine.query` 性能放大 | 单独 PR：perm-center 新增 `POST /api/perm/auth/filter-allowed`（一次完成，复用 `engine.getDeniedIds`） |
| DEFERRED-2 | EXT-8：admin `SyncTaskDomainServiceImpl.enqueueAll` for 循环 N 次 INSERT | 单独 PR：实现 `enqueueAllBatch` 批量入库 + businessKeyHash dedup |
| DEFERRED-3 | EXT-10：perm-common 与 permission-center 内部 DTO 双份维护 | 中期：让 perm-center controller 直接消费 perm-common DTO，删除内部副本 |
| DEFERRED-4 | EXT-11：`getDescendantIdsIncludingSelf` 全局加 cap（5 处使用） | 单独 PR：在 `OrgDomainServiceImpl` 加 `admin.org.descendant-cap` 配置（默认 50_000） |
| DEFERRED-5 | RoleProxyServiceImpl#createOrgRole `String.valueOf(orgId)` 当 externalId 写入 | 当前正确（ORG/POSITION 业务键约定 externalId == sys_org.id 字符串），仅在契约文档形式化（org-user-permission-contract.md 加"角色业务键约定"小节） |

---

## 10. 归档条件

满足以下条件之一时归档到 `docs/archive/YYYY-MM-DD/`：
- 16 项改动（M1-M13 + S1-S3）全部完成且 `mvn test` 通过
- 验收标准 §6.1 / §6.2 / §6.3 全部勾选
- 上一轮的 `org-user-page-impl-plan.md` 第 8 节已同步刷新
- 任何遗留问题已转移至 §9 DEFERRED 列表并关联 issue 编号
