---
doc_type: task
id: T-PERM-021
title: 工作单 F：文档准确性与代码简化（指标自动化、DTO 单源、ownership、日志链路、full-sync runbook）
status: in-progress
plan: docs/plans/design-review-def-followup-plan.md
domain: permission-center
design_refs:
  - docs/archive/2026-06-17/design-review.md#§11
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/implementation.md
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md
  - docs/design/project-rules.md
depends_on: []
blocks: []
acceptance:
  - "执行前确认：F1.c 不得在当前设计未迁移前物理删除 resource_entity.owner_service_code / maintain_source / sync_key"
  - "执行前确认：F1.d 先统一 requestId / traceId 关联语义和 requestId 生成链路，再考虑把 permission_change_log.request_id 改为 NOT NULL"
  - "DTO 单源改造不得破坏外部 API DTO 与内部 Command/领域对象边界"
  - "full-sync runbook 收窄为外部业务服务 sync/full-sync 运维手册（/api/perm/**/sync、/full-sync；内部 full-sync 编排已随 T-ACCESS-005 删除）；默认按归档评审新建 docs/ops/runbook-full-sync.md，如调整位置需执行前确认"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-12
---

# T-PERM-021 工作单 F：文档准确性与代码简化

> 状态：in-progress（2026-09-12 执行门禁三项拍板后启动，定案见下节；registry 同日行）
> ~~执行门禁：进入 `in-progress` 前必须确认 F1.c/F1.d 的设计取舍。~~ 已确认（2026-09-12）。

## 执行门禁定案（2026-09-12 用户逐项拍板，registry 同日行）

| 子项 | 定案 | 范围影响 |
|---|---|---|
| F1.c | **两列保留**（`owner_service_code`/`maintain_source`），范围收窄为文档收敛——「判定靠 type_definition.extra 类型声明、记录靠两列、外部同步 ownership 以 sync_metadata 为单源」口径写死，零 schema 改动 | 事实更正：卡面原记三列中 `resource_entity.sync_key` 已随 T-PERM-052 物理删除；撞码归属判定/通道清理边界/投影防线三消费点在用（UserRoleSyncAppServiceImpl/ResourceSyncHandlerImpl/LocalProjectionGuard），删除需按服务拆类型重构另议 |
| F1.d | **语义收敛 + 两列收紧 NOT NULL**：全链路单 ID（traceId 仅为 MDC 键/响应字段别名，无第二追踪体系）；RequestContext 无头直连兜底生成 UUID；bootstrap/异步双删日志等无请求上下文写入方合成 ID；`operation_log.request_id`/`permission_change_log.request_id` DDL 改 NOT NULL（存量 dev 库重建） | 评审原文的「管理 UI 审计详情一键展开」不在本卡验收面，不随本批 |
| F1.f | **删除**：`PermQuery.operationPermissionIds` 字段+getter+`PermQueryEngine:1343/1355` 两处分支（全仓零写入方、分支永不可达），不保留待激活 | — |
| F1.b | 按卡面默认：保留外部 API DTO / 内部 Command / DomainService 入参分层边界，不强行单层贯穿 | — |
| F1.e | 按卡面默认：新建 `docs/ops/runbook-full-sync.md` | — |

## 背景

F 来自归档设计评审 §11 的暂缓项，目标是补齐文档准确性、减少重复 DTO/字段来源，并把运维动作写清楚。当前核对结论是：F 中有两项若按字面执行，会与现行 schema/API 设计冲突；日志链路还存在 `requestId` / `traceId` 命名漂移，需要先收敛语义。

> **重基线（T-ACCESS-012，2026-08-22）**：落点为 access-service 单模块；schema 权威为 `access-service.sql`（`resource_entity` 仍保留 `owner_service_code/maintain_source/sync_key` 列，`operation_log` 为 `sys_audit_log`+原表合并超集，`permission_change_log` 独立保留）。原引用 `cross-service/admin-permission-sync.md`（已归档 superseded）与 `services/admin-service.md`（已归档 superseded）改为 `access-service-architecture.md`（§4 外部 sync 与本地投影边界）；F1.e 收窄为外部 sync/full-sync 运维手册。执行前确认项按新基线核对。

## 子项核对

| 子项 | 内容 | 当前核对 | 标记 |
|---|---|---|---|
| F1.a | 指标生成与文档自动化 | 与现行设计不冲突，可作为文档/工程质量项 | `NO_HARD_CONFLICT` |
| F1.b | DTO 单源 | 方向不冲突，但需保留外部 API DTO、内部 Command、DomainService 入参的边界 | `NO_HARD_CONFLICT` |
| F1.c | ownership 单源 | ✅ 定案（2026-09-12）：两列保留（`owner_service_code`/`maintain_source`），文档写死「判定靠类型声明、记录靠两列、外部同步以 sync_metadata 为单源」口径；`resource_entity.sync_key` 已随 T-PERM-052 删除（卡面原记三列已过期） | `DECIDED_保留两列` |
| F1.d | operation/change log 链路 | ✅ 定案（2026-09-12）：单 ID 语义收敛 + 两列收紧 NOT NULL（兜底生成 + 无上下文写入方合成 ID，dev 库重建） | `DECIDED_收敛+NOT_NULL` |
| F1.e | full-sync runbook | 收窄为**外部业务服务** sync/full-sync 运维手册（`/api/perm/**/sync`、`/full-sync` 与 `sync_metadata` ownership 校准）；内部 admin→permission full-sync 编排已随 T-ACCESS-005 删除。默认产物 `docs/ops/runbook-full-sync.md`，落地时按该路径新建 | `NO_HARD_CONFLICT` |
| F1.g | `normalize()` 副本抽取 | `TypeDefinitionAppServiceImpl`/`SystemConfigAppServiceImpl`/`LogQueryAppServiceImpl`/`BizDomainAppServiceImpl` **四处**同名同实现副本（2026-08-28 双轨评审登记三处，2026-09-12 现状核实 +1——BizDomain 为卡面更新后新增），抽 `permission.util` 单一静态方法 | `NO_HARD_CONFLICT` |
| F1.f | `PermQuery.operationPermissionIds` 无写入方扩展点 | ✅ 定案（2026-09-12）：删除字段与引擎分支（全仓零写入方、分支永不可达；2026-08-28 双轨评审登记，2026-09-12 复核仍成立） | `DECIDED_删除` |

## 实施记录（2026-09-12）

| 子项 | 处置 | 落点 |
|---|---|---|
| F1.a | **定性收口**：不建 `_metrics.md`+CI 对账管道（与 2026-08「文档不携带活计数、CI 以退出状态判定」既定口径相抵）；替代处置=清扫活文档四处残留活计数措辞改指 `HttpApiPathSnapshotTest`/源码目录 | architecture（75 接口预估）、admin 契约（16/19 待实现注记）、overview（22/23/11/18 个）、implementation（11+11） |
| F1.b | **定性收口**（用户拍板另立任务）：14 对同名 Req 双轨现状盘点钉死，含 `UserAssignRoleReq.items @Size(max=1000)` 现役分叉实证（SDK 副本缺失该约束；守卫测试只覆盖必填性抓不到）；统一改造另立 [T-PERM-065](T-PERM-065.md) | 盘点结论与消费面（40+ 文件）见 065 卡 |
| F1.c | 零 schema 改动：schema 注释/api-contract §6.2.2.1 经核对已含口径（T-PERM-052 回写）；架构 §4.3 补终态钉死句（判定靠类型声明、记录靠两列、外部同步以 sync_metadata 为单源——评审勿再建议删列收敛） | access-service-architecture §4.3 |
| F1.d | requestId 全链路单 ID 收敛 + 两列 NOT NULL（详见下节） | 代码 12 文件 + schema + 四文档 + 回归锁 6 用例 |
| F1.e | `docs/ops/runbook-full-sync.md` 新建：通道总览/前置清单/执行步骤/retryClass 决策表/验收/回滚（不可逆声明+反向补数据）/故障处置；`docs/README.md` 目录树登记 ops/ | docs/ops/ |
| F1.f | `PermQuery.operationPermissionIds` 字段+getter+`PermQueryEngine` 两分支删除（全仓零残留实证，含测试） | PermQuery/PermQueryEngine |
| F1.g | 四副本（TypeDefinition/SystemConfig/LogQuery/**BizDomain**——卡面原记三处，+1 为 2026-09-12 核实新增）抽 `StringUtils.normalizeFilterParam`，16 调用点换用 | StringUtils + 四 AppServiceImpl |

### F1.d 实施明细

- **RequestContext 第六要素** `requestId`（`withRequestId` 拷贝方法；拦截器 `resolveRequestId` 单点解析=头值/兜底 UUID/截断 64，六个绑定点挂载）；MDC traceId 改与之**单源同值**（原 setMdc 独立读头+独立生成 UUID 的漂移源消除）。
- `OperatorContext.getRequestId()`（null 安全，与 getOperatorId 的 fail-closed 语义区分）；**16 处** ChangeLogContext 调用点 `null`→上下文取值（原全部传字面量 null——`permission_change_log.request_id` 实为恒空列，比卡面「可空」更糟的事实修正）。
- `AuditDomainServiceImpl` 两落库点（recordChangeLog/asyncRecordLog）空值合成 UUID：无请求上下文写入方兜底；同一次请求的 operation_log 与 permission_change_log 由上游同源值 JOIN 关联。
- `OperationLogAspect` requestId 改上下文优先（HTTP 头回退）；`RResponseAdvice` 删除 X-Trace-Id 头偏好路径（全仓无生产方，traceId≡requestId）+ requestId 取值链 头→MDC→UUID（common 不依赖 access 基础设施）；Gateway `GlobalExceptionHandler` traceId 从 otel span 改同值 requestId（Tracer 依赖删除；tracing 未接线时原值恒 null）。
- DDL：`operation_log.request_id`/`permission_change_log.request_id` 改 NOT NULL + 两列注释统一单 ID 口径 + 部分索引 `WHERE request_id IS NOT NULL` 谓词冗余去除；`AccessServiceSchemaPostgresTest` 直插语句补列。
- 文档：api-contract §3.1 X-Request-Id 行、project-rules §1.1 requestId/traceId 行、architecture 链路追踪行、admin 契约 §1.1、access-service-architecture §6.1「四要素」→六要素清单。
- 回归锁（旧实现下均失败）：拦截器 3 用例（头透传同源 MDC / 无头兜底 UUID 与 MDC 同值 / 超长截断 64）；审计 3 用例（两落点 null 合成 UUID / 上游非空原样透传不覆盖——JOIN 语义基础）。
- 测试：单测轨道（common+gateway+access-service，`-DskipTestcontainers -DskipE2E`）全绿；收口全量（含容器+E2E）见完成记录。

## 执行前确认（已全部确认，2026-09-12）

~~四问已于 2026-09-12 全部确认，定案见「执行门禁定案」节与 registry 同日行。~~

1. ~~F1.c 是否只限制“新增 sync/full-sync 以 `sync_metadata` 为单源”，暂不删除 resource_entity 现有字段。~~ → 两列保留+文档收敛（sync_key 已删的收窄事实一并更正）。
2. ~~F1.d 是否先统一 `requestId` / `traceId` 关联语义，并设计统一 requestId 生成/兜底链路，再考虑 schema 约束收紧。~~ → 收敛 + NOT NULL（含兜底与无上下文合成）。
3. ~~F1.b 是否允许保留内部 Command/领域对象，不强行用外部 DTO 贯穿分层。~~ → 保留边界。
4. ~~F1.e 默认新建 `docs/ops/runbook-full-sync.md`；若改放其他位置需额外确认。~~ → 按默认路径。

## 验收标准

- 冲突项在设计文档中有明确取舍，不允许用任务文件覆盖权威设计。
- 代码简化不得引入 Controller -> Mapper、AppService 横向调用或 N+1 查询。
- requestId 约束收紧必须先完成 requestId/traceId 语义收敛，并有生成链路、兼容策略和测试覆盖。
- 运维 runbook 至少覆盖触发条件、执行步骤、回滚/重试、验收检查。
