---
doc_type: design
title: admin-service 与 permission-center 同步设计
status: adopted
domain: cross-service
last_reviewed: 2026-06-20
---

# admin-service 与 permission-center 同步设计

> 状态：**v1.0 定稿**（2026-06-14）。本文是 admin-service 与 permission-center 同步模块的当前有效设计。
>
> 历史执行过程已归档到 `docs/archive/2026-06-14/sync-module-execution-plan.md`，仅用于追溯，不作为实现依据。

## 1. 目标与边界

admin-service 是用户、组织、菜单等管理事实的来源；permission-center 是权限计算事实的来源。同步模块负责把 admin-service 中需要参与权限控制的事实稳定投递到 permission-center，并保证重试、乱序、全量校准和人工补偿都有明确边界。

> **写后读一致性（2026-06-20 审计 S-008）**：同步为异步分钟级延迟（Q1 决策）。**admin 写操作完成后立即跳转 permission-center 查询页验证时，permission-view/* 直查 perm 数据可能读到旧事实**，这是预期行为而非 bug。
>
> **admin UI 回显规范**：admin 写操作（创建用户 / 调整组织 / 分配关系等）完成后，UI 应使用 **admin 本地事实**（admin 库 `sys_user` / `sys_org` / `sys_user_org` 等）即时回显，**不立即跳转查询 perm-center 派生视图**。例如：
> - admin 创建用户 → 保存成功 → 列表立即显示新用户（读 admin `sys_user`），不跳"用户权限视图"查 perm
> - admin 调整用户组织关系 → 保存成功 → 关系列表立即更新（读 admin `sys_user_org`），不查 perm `user_role`
>
> permission-center 派生视图（如"用户权限视图"）应在用户主动进入时查询，此时通常已过同步窗口。若 UI 必须呈现同步状态，可显示 sync_task 状态（但不阻塞跳转）。

同步范围：

| admin-service 事实 | permission-center 事实 | 说明 |
|--------------------|------------------------|------|
| `sys_user` | `abstract_user` | 用户作为权限主体 |
| `sys_user` | `resource_entity(ADMIN_USER)` | 用户作为被管理资源 |
| `sys_org` | `resource_entity(ADMIN_ORG)` | 组织、岗位作为被管理资源 |
| `sys_org` | `abstract_role(ORG/POSITION)` | 组织、岗位作为角色容器 |
| `sys_user_org` | `user_role` | 组织、岗位成员关系进入权限计算 |
| 菜单、按钮等 | `resource_entity` | 前端权限资源和可管理资源 |

非目标：

- `role_resource_permission` 不纳入 admin-service 同步任务；授权关系走 permission-center 正式管理 API。
- 当前同步链路不依赖 RocketMQ；RocketMQ 仅作为未来异步事件通道预留。
- 主业务事务内不直接调用 Feign 或远程 API。
- 不提供手工编辑 payload 的补偿入口；人工补偿只允许 `retry-now/reset` 与 `rebuild-from-fact`。

## 2. 跨服务引用规则

admin-service **不得存储 permission-center 内部主键 ID**。所有跨服务引用统一使用稳定业务键：

| 目标事实 | 业务键 |
|----------|--------|
| `abstract_user` | `subjectTypeCode + subjectExternalId` |
| `abstract_role` | `domainCode + roleTypeCode + roleExternalId` |
| `resource_entity` | `resourceTypeCode + resourceCode + codeType` |
| `user_role` | 主体业务键 + 角色业务键 + `sourceType/relationKey` |

permission-center 内部通过类型解析和业务键查询转换为内部 ID。外部服务只能感知业务键和接口响应，不感知内部表结构主键。

## 3. 本地同步任务模型

admin-service 使用 `sys_sync_task` 作为本地消息任务表。它是业务事实变更的事务内 outbox，不是失败后才写入的重试表。

写路径要求：

1. 主业务事实与同步任务在同一个 admin-service 事务内写入。
2. 事务提交后由调度器异步 claim 并执行任务。
3. 调度器按 `syncAction -> Handler -> 具体 Feign/API` 路由，禁止拼接旧全局 replay 入口。

核心字段语义：

| 字段 | 语义 |
|------|------|
| `messageKey` | 单次业务事件唯一键；合并 PENDING 任务时覆盖为最新事件 key |
| `syncAction` | 领域级同步动作，只允许固定枚举 |
| `businessKey` | 同一同步对象的稳定业务键原文 |
| `businessKeyHash` | `businessKey` 的 SHA-256 lowercase hex，用于唯一约束和查询 |
| `batchKey` | 全量校准批次键；实时单次同步为空 |
| `batchKeyHash` | `batchKey` 的 SHA-256 lowercase hex |
| `payloadVersion` | Handler 支持的 DTO 版本；高版本必须拒绝执行 |
| `payload` | 目标 sync/full-sync 接口的强类型 JSON 快照 |
| `displayAttrs` | 仅用于 UI 和审计展示，不参与执行路由 |
| `phase` | 全量校准阶段 |
| `status` | `PENDING/PROCESSING/SUCCESS/FAILED` |

同一 `tenantId + syncAction + businessKeyHash` 下的 PENDING 任务可以合并为最新 payload；已进入 `PROCESSING/SUCCESS` 的任务不得回写覆盖。旧版本请求到达 permission-center 后必须 no-op。

## 4. syncAction 枚举

| syncAction | payload.operation | 来源 | 目标 |
|------------|-------------------|------|------|
| `PERM_ABSTRACT_USER_SYNC` | `UPSERT/DISABLE/DELETE` | `sys_user` | `abstract_user` |
| `PERM_ABSTRACT_ROLE_SYNC` | `UPSERT/DISABLE/DELETE` | `sys_org` | `abstract_role(ORG/POSITION)` |
| `PERM_USER_ROLE_SYNC` | `BIND/UNBIND` | `sys_user_org` | `user_role` |
| `PERM_RESOURCE_ENTITY_SYNC` | `UPSERT/DISABLE/DELETE` | `sys_user/sys_org/sys_menu` 等 | `resource_entity` |

`PERM_USER_ROLE_SYNC` 只承载 `sys_user_org` 派生的组织/岗位关系，payload 必须满足 `sourceType=SYS_USER_ORG` 且 `roleTypeCode in (ORG, POSITION)`。BASIC_ROLE、GROUP_ROLE、PERSONAL 等功能角色分配不进入同步任务。

## 5. Key 与版本规则

`businessKey` 与 `scopeKey` 统一使用有序参数串：

```text
key=value&key=value
```

规则：

- 参数名按契约固定顺序输出。
- 参数值必须 URL percent-encoding。
- key 原文不包含 `tenantId/sourceService/entityKind`，这些字段由任务、请求 envelope 或 metadata 维度承载。
- 数据库同时保存 key 原文和 SHA-256 hash；唯一索引使用 hash。
- 单条同步必须携带 `syncVersion.occurredAt + syncVersion.sequenceNo`。
- full-sync 的每个 `items[]` 元素也必须携带 syncVersion。

## 6. permission-center 同步接口

permission-center 提供分领域专用 sync/full-sync 接口：

| 领域 | 单条同步 | 全量校准 |
|------|----------|----------|
| 用户主体 | `POST /api/perm/abstract-user/sync` | `POST /api/perm/abstract-user/full-sync` |
| 角色容器 | `POST /api/perm/abstract-role/sync` | `POST /api/perm/abstract-role/full-sync` |
| 用户角色关系 | `POST /api/perm/user-role/sync` | `POST /api/perm/user-role/full-sync` |
| 资源实体 | `POST /api/perm/resource-entity/sync` | `POST /api/perm/resource-entity/full-sync` |

所有接口必须使用 `POST + JSON Body`，统一返回 `SyncResultResp` 并放入统一响应体 `data`。full-sync 不引入独立顶层响应类型，明细放入 `data.detail`。

`retryClass` 固定为：

| retryClass | 调度语义 |
|------------|----------|
| `RETRYABLE` | 指数退避后重试 |
| `DEPENDENCY_MISSING` | 短退避后重试 |
| `NON_RETRYABLE` | 进入 `FAILED` |
| `SECURITY_DENIED` | 进入 `FAILED` |
| `STALE_VERSION` | 视为成功 no-op，任务进入 `SUCCESS` |

## 7. 服务间安全

sync/full-sync 请求必须携带可信服务身份，并校验认证身份与请求中的 `sourceService` 一致。

必需 Header：

| Header | 说明 |
|--------|------|
| `X-Tenant-Id` | 当前租户 |
| `X-Service-Code` | 调用方服务编码 |
| `X-Internal-Secret` | 内部服务密钥 |

permission-center 拦截器链路：

1. `InternalApiSecretInterceptor`：仅处理 `/api/perm/**`，校验内部密钥并写入可信 request attribute。
2. `HeaderSignatureInterceptor`：处理签名、nonce 和防重放能力。
3. `PermTenantInterceptor`：基于可信 request attribute 和租户 Header 建立租户上下文。

禁止基于未验证的请求头直接做信任决策；拦截器之间的“已认证”信号必须通过 request attribute 传递。

## 8. sync_metadata

permission-center 使用 `sync_metadata` 记录外部同步 ownership、版本和 full-sync scope。

写入规则：

- 目标事实写入与 metadata 写入必须在同一事务中完成。
- 新版本请求应用目标事实并更新 metadata。
- 旧版本请求返回 `stale=true` 与 `retryClass=STALE_VERSION`，不更新目标事实，也不更新 metadata。
- full-sync 只允许清理命中 `tenantId + entityKind + sourceService + scopeKeyHash` 的 metadata scope 内事实。
- 人工维护或其他 ownership 通道创建的数据不得被当前 source/scope 的 full-sync 清理。

`targetStatus` 约束：

| entityKind | 允许状态 |
|------------|----------|
| `USER_ROLE` | `ACTIVE/UNBOUND` |
| 其他 entityKind | `ACTIVE/DISABLED/DELETED` |

## 9. 全量校准编排

全量校准用于修复漏发、重试耗尽、权限中心事实误删或多余同步事实残留，不替代单次同步。单次删除、禁用、解绑仍必须生成对应任务。

同一次 full-sync 使用同一个 `batchKey` 串联阶段，建议格式：

```text
sourceService={sourceService}&runId={uuid}
```

阶段顺序：

| 顺序 | phase | syncAction | 说明 |
|------|-------|------------|------|
| 1 | `USER_SUBJECT` | `PERM_ABSTRACT_USER_SYNC` | 同步用户主体 |
| 2 | `USER_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` | 同步 `ADMIN_USER` 管理资源 |
| 3 | `ORG_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` | 同步 `ADMIN_ORG` 组织/岗位资源 |
| 4 | `ORG_ROLE` | `PERM_ABSTRACT_ROLE_SYNC` | 同步 `ORG/POSITION` 角色容器 |
| 5 | `USER_ROLE` | `PERM_USER_ROLE_SYNC` | 同步组织/岗位成员关系 |
| 6 | `MENU_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` | 同步菜单、按钮等资源 |
| 7 | `OTHER_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` | 同步其他资源实体 |

调度器只有在上一阶段同一 `batchKeyHash` 的任务全部 `SUCCESS` 后，才能 claim 下一阶段任务。一个 `tenantId + sourceService` 下最多只能存在一个未结束 full-sync 批次。

scope 必须足够小且明确，禁止默认全租户清理。ORG_ROLE 和 USER_ROLE 阶段必须按 `(treeRootExternalId, roleTypeCode)` 二维分桶生成 envelope；`item.roleTypeCode` 必须与 `scope.roleTypeCode` 一致。不生成空 envelope。

user-org / user-role 全量校准必须通过 `OrgTreeConfigDomainService.resolveTreeRootExternalIds` 批量解析树根。任一 active org 或 binding.orgId 无法解析到树根时，立即抛业务异常并终止本次 full-sync，禁止 fallback `"1"`。

## 10. 批量处理要求

full-sync 必须使用三段式批量处理：

1. 收集：遍历 items，收集所有 externalIds、parent codes、relationKey 三元组等待解析键集合。
2. 批量解析：使用 `TypeResolutionService.batchResolve*` 与 Mapper 批量查询一次性预加载现有事实和依赖。
3. 批量写入：基于内存索引判断 insert/update/delete，统一收集差异并批量写入。

禁止在 full-sync 循环内调用单条 Mapper 查询或单条类型解析。`doSyncOne` 仅供单条 sync 接口使用，不得在 full-sync 中循环调用无预加载上下文的版本。

## 11. 调度与重试

调度器通过原子 claim 把任务从 `PENDING` 切到 `PROCESSING`，写入 `lockedAt/lockedBy` 后执行。

默认 stale lock timeout：

| 任务类型 | 默认超时 |
|----------|----------|
| 单条同步任务 | 60s |
| full-sync 阶段任务 | 300s |

配置项：

```yaml
accessmesh.sync.scheduler.stale-lock-timeout.{syncAction}
```

失败处理：

- `DEPENDENCY_MISSING` 使用短退避。
- `RETRYABLE` 使用指数退避。
- `NON_RETRYABLE/SECURITY_DENIED` 直接进入 `FAILED`。
- `STALE_VERSION` 进入 `SUCCESS`。

### 11.1 user_role 孤儿延迟补偿（fail-safe，2026-06-15 M13 落地）

用户删除走 admin 端 `PERM_USER_ROLE_SYNC`(UNBIND) envelope 异步解耦 `user_role`。正常路径下 envelope 处理后 `user_role` 即清；但 envelope 可能延迟到达、乱序或丢失（EXT-9 一致性风险），导致 `abstract_user` 已软删而 `user_role` 仍残留。

**兜底机制**：permission-center 新增 `UserRoleOrphanCleanupTask` 定时任务，作为 envelope 之外的最终一致兜底，**不与 envelope 形成双写冲突**（`softDeleteBatch` 幂等，已删跳过）。

| 项 | 说明 |
|---|---|
| 触发 | `@Scheduled(fixedDelay = "${permission.orphan-cleanup.interval:300000}")`，默认每 5 分钟 |
| 扫描条件 | `abstract_user.deleted=true` 且 `user_role.deleted=false` 且 `user_role.updated_at < now - window` |
| 窗口 | `permission.orphan-cleanup.window-minutes`（默认 5），给 envelope 处理留时间，避免误清正常延迟中的记录 |
| 监控 | 扫到孤儿时 `log.warn(tenantId, userId, roleId)`，用于排查 envelope 丢失根因 |

> 此兜底仅覆盖"用户已删但 user_role 残留"一类孤儿；`user-org` 关系变更的 BIND/UNBIND 一致性仍以 envelope 为主，TTL + 本任务为辅。与 §1「写后读一致性」Q1 决策（分钟级延迟可接受）一致。

## 12. 管理与观测

管理端可以提供：

- 同步任务列表与详情。
- `retry-now/reset`。
- `rebuild-from-fact`。
- 结构化日志与 traceId。
- metrics：`PENDING` 数量、单任务处理耗时 p50/p99、`retryClass` 占比、`FAILED` 数量、最老未处理任务等待时长。

补偿操作必须有管理权限门禁和操作日志。首期只保留 `lastError`，不新增 attempt 明细表。

## 13. 禁止事项

- 禁止恢复 `/api/sync/{operation}` 旧全局万能 replay 入口。
- 禁止业务逻辑读取 `displayAttrs` 做执行判断。
- 禁止 admin-service 存储 permission-center 内部主键。
- 禁止在主业务事务内发起 Feign 或 MQ 调用。
- 禁止把 `role_resource_permission` 纳入 admin-service 同步任务。
- 禁止 full-sync 默认全租户清理。
- 禁止 user-org / user-role 同步链路 fallback `treeRootExternalId="1"`。
- 禁止 full-sync 循环内单条 DB 查询或单条类型解析。
