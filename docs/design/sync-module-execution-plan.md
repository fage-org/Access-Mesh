# 同步模块执行计划

本文档定义 admin-service 与 permission-center 之间同步模块的实施任务、接口标准和验证标准。它是同步模块落地的执行入口；接口细节以 `permission-center/api-contract.md`，表结构以 `schema/*.sql` 为准。

## 1. 目标与边界

### 1.1 目标

- 将用户、组织、岗位、成员关系、菜单等 admin-service 本地事实稳定同步到 permission-center。
- 使用本地消息表 `sys_sync_task` 实现主业务事务内落任务、事务外重放。
- 使用 permission-center 专用 sync/full-sync 接口，禁止旧全局万能 replay 入口 `/api/sync/{operation}`。
- 通过 `sync_metadata` 记录外部同步 ownership、最后 syncVersion 和 full-sync 差异校准范围。
- 全程使用业务键，禁止 admin-service 存储 permission-center 内部主键。

### 1.2 非目标

- 不把 `role_resource_permission` 纳入 admin-service 同步任务；授权关系走 permission-center 正式管理 API。
- 不使用 RocketMQ 承载当前同步链路；RocketMQ 仅预留给未来异步事件。
- 不在主业务事务内直接调用 Feign 或远程 API。
- 不通过手工编辑 payload 做补偿；人工补偿只允许 `retry-now/reset` 与 `rebuild-from-fact`。

## 2. 统一标准

### 2.1 任务模型标准

`sys_sync_task` 是本地消息任务表，不是失败后才写入的重试表。

| 字段 | 标准 |
| ---- | ---- |
| `messageKey` | 单次业务事件唯一；合并 PENDING 任务时覆盖为最新事件 key |
| `syncAction` | 仅允许 `PERM_ABSTRACT_USER_SYNC`、`PERM_ABSTRACT_ROLE_SYNC`、`PERM_USER_ROLE_SYNC`、`PERM_RESOURCE_ENTITY_SYNC` |
| `businessKey` | 按 `api-contract.md` §6.2.2.4 生成的原文 |
| `businessKeyHash` | `businessKey` 的 SHA-256 lowercase hex，用于唯一约束和查询 |
| `batchKey` | 全量校准批次键；实时单次同步为空 |
| `batchKeyHash` | `batchKey` 的 SHA-256 lowercase hex |
| `payloadVersion` | Handler 支持的 DTO 版本；高版本必须拒绝执行 |
| `displayAttrs` | 仅供 UI 和审计展示，严禁参与执行路由 |
| `phase` | 全量校准阶段，用于同批次阶段推进 |
| `status` | `PENDING/PROCESSING/SUCCESS/FAILED` |

### 2.2 接口标准

- 所有接口使用 `POST + JSON Body`。
- 所有 sync/full-sync 接口必须校验可信服务身份，并确认认证身份与单条 sync 顶层 `sourceService` 或 full-sync 的 `scope.sourceService` 一致。
- 所有单条同步事实必须携带 `syncVersion.occurredAt + syncVersion.sequenceNo`；full-sync 在每个 `items[]` 元素中携带。
- 旧版本请求返回 `code=200`，`data.stale=true`，`data.retryClass=STALE_VERSION`，调度器直接置 `SUCCESS`。
- `retryClass` 固定为 `RETRYABLE/DEPENDENCY_MISSING/NON_RETRYABLE/SECURITY_DENIED/STALE_VERSION`。
- full-sync 必须携带强制 scope，只清理命中 `sync_metadata` 的事实，不扫描删除人工维护或其他 ownership 通道的数据。

### 2.3 Key 标准

- `businessKey/scopeKey` 均使用 `key=value&key=value` 的有序参数串。
- 参数值必须 URL percent-encoding。
- key 原文不包含 `tenantId/sourceService/entityKind`。
- 数据库同时保存 key 原文和 SHA-256 hash；唯一索引使用 hash。

### 2.4 阶段推进标准

全量校准使用同一个 `batchKey` 串联多个阶段：

| 顺序 | phase | syncAction |
| ---- | ----- | ---------- |
| 1 | `USER_SUBJECT` | `PERM_ABSTRACT_USER_SYNC` |
| 2 | `USER_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` |
| 3 | `ORG_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` |
| 4 | `ORG_ROLE` | `PERM_ABSTRACT_ROLE_SYNC` |
| 5 | `USER_ROLE` | `PERM_USER_ROLE_SYNC` |
| 6 | `MENU_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` |
| 7 | `OTHER_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` |

调度器只有在上一阶段同一 `batchKeyHash` 的任务全部 `SUCCESS` 后，才能 claim 下一阶段任务。

## 3. 任务拆分

### S0. 契约基线确认

**范围**
- 冻结 `api-contract.md` §6.2.2、`schema/admin-service.sql`、`schema/permission-center.sql`。
- 明确 legacy `SyncRetry` 代码不可继续作为新同步模块实现基础。

**交付物**
- 设计文档索引指向本执行计划。
- 同步模块代码任务以本文档为拆分依据。

**验证标准**
- `rg "sys_sync_retry|/api/sync/" docs/design --glob "!sync-module-execution-plan.md"` 无输出；本执行计划中仅允许在 legacy 迁移检查项中出现旧命名。
- `git diff --check` 通过。

### S1. admin-service 同步任务表与代码迁移

**范围**
- 将 `SysSyncRetry*`、`SyncRetry*` 迁移为 `SysSyncTask*`、`SyncTask*`。
- 实体、Mapper、DTO、Controller、Service 全部对齐 `sys_sync_task`。
- 移除 `entityType/externalId/operationType` 作为主字段的执行语义，改为 `displayAttrs`。
- 删除旧全局万能 replay 入口 `/api/sync/{operation}` 的拼接逻辑。

**接口标准**
- AppService 只调用 `SyncTaskDomainService.createOrMergePendingTask()` 之类的领域入口。
- 写业务事实和写同步任务在同一事务内完成。
- 调度器事务外 claim 和执行任务。

**验证标准**
- `rg "SysSyncRetry|sys_sync_retry|SyncRetry|/api/sync/" admin-service/src` 无残留，除非在迁移说明或历史注释中明确标记为 legacy 删除项。
- `mvn -pl admin-service -am test` 通过。
- 单元测试覆盖 PENDING 合并：同一 `tenantId + syncAction + businessKeyHash` 只保留一条 PENDING，且 messageKey/payload/syncVersion 为最新事件。
- 单元测试覆盖 `payloadVersion` 高于支持版本时置 `FAILED` 且 `retryClass=NON_RETRYABLE`。

### S2. permission-center sync_metadata 与同步领域服务

**范围**
- 新增 `sync_metadata` 实体、Mapper、DomainService。
- 为四类 entityKind 实现 version 原子比较、targetStatus 更新和 ownership 查询。
- 所有 sync/full-sync 写路径必须使用 `sync_metadata` 防乱序。

**接口标准**
- `sync_metadata` 写入和目标事实写入必须在同一事务内。
- 旧版本 no-op 不更新目标事实，也不更新 metadata。
- full-sync 只根据 `tenantId + entityKind + sourceService + scopeKeyHash` 清理。

**验证标准**
- 并发旧/新版本请求测试：旧版本在新版本后到达时返回 stale 且不覆盖目标事实。
- full-sync scope 测试：只清理命中 metadata scope 的事实，不清理人工维护和 `service-config/sync` 创建的数据。
- `targetStatus` 测试：USER_ROLE 只出现 `ACTIVE/UNBOUND`，其他 entityKind 只出现 `ACTIVE/DISABLED/DELETED`。

### S3. permission-center 专用 sync/full-sync API

**范围**
- 实现 `abstract-user/sync`、`abstract-user/full-sync`。
- 实现 `abstract-role/sync`、`abstract-role/full-sync`。
- 实现 `user-role/sync`、`user-role/full-sync`。
- 实现 `resource-entity/sync`、`resource-entity/full-sync`。

**接口标准**
- DTO 使用 record，命名为 `XxxSyncReq`、`XxxFullSyncReq`、`SyncResultResp`。
- 所有接口统一返回 `SyncResultResp`，放入统一响应壳 `data`。
- 父资源解析使用 `parentResourceTypeCode + parentResourceCode + parentCodeType`。
- POSITION 的 `relationKey=ORG:{orgExternalId}` 解析到组织角色 `abstract_role.id`，写入 `user_role.relation_id`。
- `PERM_USER_ROLE_SYNC` 只允许 `sourceType=SYS_USER_ORG` 且 `roleTypeCode in (ORG, POSITION)`。

**验证标准**
- Controller 层测试确认所有接口为 POST + JSON Body。
- 契约测试覆盖成功应用、旧版本 stale、依赖缺失、不可重试错误、安全拒绝。
- 批量 full-sync 测试覆盖补齐缺失、清理多余、同 scope 限定、跨 ownership 互不删除。
- N+1 检查：full-sync 实现必须批量解析类型和业务键，禁止循环内单条 DB 查询。

### S4. admin-service 任务生产器

**范围**
- 用户写路径生成 `PERM_ABSTRACT_USER_SYNC` 与 `PERM_RESOURCE_ENTITY_SYNC`。
- 组织写路径生成 `PERM_ABSTRACT_ROLE_SYNC` 与 `PERM_RESOURCE_ENTITY_SYNC`。
- 成员关系写路径生成 `PERM_USER_ROLE_SYNC`。
- 菜单、按钮等资源写路径生成 `PERM_RESOURCE_ENTITY_SYNC`。

**接口标准**
- 任务 payload 必须是目标 sync 接口的强类型 JSON 快照。
- `businessKey` 生成集中在一个组件，禁止散落拼接。
- `displayAttrs` 只写展示字段，不参与路由。
- 删除/禁用必须生成 `DELETE/DISABLE/UNBIND` 任务，不得只依赖 full-sync 兜底。

**验证标准**
- 用户创建、禁用、删除分别生成正确任务。
- 组织创建、移动、禁用、删除分别生成两类事实任务。
- 成员绑定/解绑生成 `BIND/UNBIND`，且 relationKey 正确编码。
- 菜单写路径相关代码不引用 permission-center 内部主键字段；`rg -n "perm_resource_id|perm_role_id|perm_user_id" admin-service/src` 无业务读写引用。

### S5. admin-service 调度器与重试执行

**范围**
- 实现原子 claim、stale lock 恢复、退避重试和 retryClass 分类处理。
- 实现 `syncAction -> Handler -> Feign/API` 路由。
- 实现 `STALE_VERSION` 成功 no-op 处理。

**接口标准**
- Handler 不读取 `displayAttrs` 做执行判断。
- 默认 stale lock timeout：`PERM_ABSTRACT_USER_SYNC`、`PERM_ABSTRACT_ROLE_SYNC`、`PERM_RESOURCE_ENTITY_SYNC`、`PERM_USER_ROLE_SYNC` 为 60s，full-sync 阶段任务为 300s；通过 `application.yml` 的 `sync-task.stale-lock-timeout.{syncAction}` 覆盖。
- `DEPENDENCY_MISSING` 使用短退避。
- `RETRYABLE` 使用指数退避。
- `NON_RETRYABLE/SECURITY_DENIED` 直接进入 `FAILED`。
- `STALE_VERSION` 进入 `SUCCESS`。

**验证标准**
- 多 worker 并发 claim 同一任务时只有一个成功。
- worker 崩溃后 stale lock 可被重新 claim。
- 不同 retryClass 对应状态和 nextRetryAt 正确。
- 所有 Handler 使用明确 Feign API，不存在 `/api/sync/{operation}`。

### S6. 全量校准编排

**范围**
- 实现 `rebuild-from-fact`，按 batchKey 创建阶段任务。
- 实现阶段推进：USER_SUBJECT -> USER_RESOURCE -> ORG_RESOURCE -> ORG_ROLE -> USER_ROLE -> MENU_RESOURCE -> OTHER_RESOURCE。
- 实现按 scope 组织 full-sync 请求。

**接口标准**
- 每次全量校准生成唯一 `batchKey=sourceService={sourceService}&runId={uuid}`。
- 一个阶段失败时，后续阶段不得执行。
- full-sync scope 必须足够小，禁止默认全租户清理。

**验证标准**
- 模拟上一阶段失败，下一阶段不被 claim。
- 同一 `tenantId + sourceService` 下最多只能存在一个未结束的 `batchKey`；并发触发应返回业务错误，不创建第二个批次。
- 重新触发同一领域 full-sync 可补齐 permission-center 缺失事实。
- permission-center 多余同步事实只在命中 metadata scope 时被清理。

### S7. 管理端补偿与观测

**范围**
- 提供同步任务列表、详情、retry-now/reset、rebuild-from-fact。
- 输出结构化日志和 traceId。
- 调度器 metrics 暴露 `PENDING` 数量、单任务处理耗时 p50/p99、`retryClass` 占比、`FAILED` 数量和最老未处理任务等待时长。
- 保留 `lastError`，首期不建 attempt 明细表。

**接口标准**
- 管理接口仍使用 POST + JSON Body。
- 不提供手工编辑 payload。
- 补偿操作必须有管理权限门禁和操作日志。

**验证标准**
- 无权限用户不能查看或操作同步任务。
- retry-now 只影响指定任务，不绕过 payloadVersion 检查。
- rebuild-from-fact 生成新的 batchKey 和新 payload。
- metrics 能按租户、`syncAction` 和 `phase` 维度观测队列积压、处理耗时与失败分类。

### S8. 清理与回归

**范围**
- 删除旧 `SyncRetry` 命名、旧 DTO、旧 Mapper、旧占位 URL。
- 更新 README 和文档引用。
- 补齐迁移说明。

**验证标准**
- `rg "SysSyncRetry|sys_sync_retry|SyncRetry|/api/sync/" admin-service/src docs/design --glob "!sync-module-execution-plan.md"` 无输出；本执行计划中仅允许在 legacy 迁移检查项中出现旧命名。
- `mvn test` 通过。
- `mvn clean compile` 通过。
- 集成测试覆盖用户、组织、成员、菜单四条主链路。

## 4. 验收矩阵

| 能力 | 必测场景 |
| ---- | -------- |
| 幂等 | 同一事件重复投递只应用一次 |
| 合并 | 同一业务键多次 PENDING 只保留最新任务 |
| 乱序 | 新版本先成功，旧版本后到达返回 stale |
| 依赖 | 父资源/父角色缺失返回 `DEPENDENCY_MISSING` 并短退避 |
| 安全 | 请求 sourceService 与认证主体不一致时拒绝 |
| full-sync | 只清理本 source/scope/metadata 命中的事实 |
| ownership | `service-config/sync` 与 `resource-entity/full-sync` 互不删除对方事实 |
| 崩溃恢复 | PROCESSING 超时后可重新 claim |
| 权限 | 同步任务管理接口具备门禁和操作日志 |
| N+1 | 批量解析、批量查询，禁止循环单条 DB 查询 |

## 5. 实施顺序建议

1. 先做 S1/S2，建立表、实体、metadata 和 key 工具，保证基础设施可用。
2. 再做 S3，完成 permission-center 的专用接口和契约测试。
3. 再做 S4/S5，让 admin-service 能产生任务并稳定重放。
4. 再做 S6/S7，补齐全量校准和人工补偿。
5. 最后做 S8 清理旧实现和全链路回归。

任何阶段发现接口字段需要变更时，先更新 `api-contract.md` 和 schema，再改代码；不得在代码中形成与文档不同的私有契约。
