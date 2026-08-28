---
doc_type: task
id: T-PERM-021
title: 工作单 F：文档准确性与代码简化（指标自动化、DTO 单源、ownership、日志链路、full-sync runbook）
status: proposed
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
last_updated: 2026-08-28
---

# T-PERM-021 工作单 F：文档准确性与代码简化

> 状态：proposed
> 执行门禁：进入 `in-progress` 前必须确认 F1.c/F1.d 的设计取舍。

## 背景

F 来自归档设计评审 §11 的暂缓项，目标是补齐文档准确性、减少重复 DTO/字段来源，并把运维动作写清楚。当前核对结论是：F 中有两项若按字面执行，会与现行 schema/API 设计冲突；日志链路还存在 `requestId` / `traceId` 命名漂移，需要先收敛语义。

> **重基线（T-ACCESS-012，2026-08-22）**：落点为 access-service 单模块；schema 权威为 `access-service.sql`（`resource_entity` 仍保留 `owner_service_code/maintain_source/sync_key` 列，`operation_log` 为 `sys_audit_log`+原表合并超集，`permission_change_log` 独立保留）。原引用 `cross-service/admin-permission-sync.md`（已归档 superseded）与 `services/admin-service.md`（已归档 superseded）改为 `access-service-architecture.md`（§4 外部 sync 与本地投影边界）；F1.e 收窄为外部 sync/full-sync 运维手册。执行前确认项按新基线核对。

## 子项核对

| 子项 | 内容 | 当前核对 | 标记 |
|---|---|---|---|
| F1.a | 指标生成与文档自动化 | 与现行设计不冲突，可作为文档/工程质量项 | `NO_HARD_CONFLICT` |
| F1.b | DTO 单源 | 方向不冲突，但需保留外部 API DTO、内部 Command、DomainService 入参的边界 | `NO_HARD_CONFLICT` |
| F1.c | ownership 单源 | 若理解为删除 `resource_entity.owner_service_code` / `maintain_source` / `sync_key`，与当前 service-config/sync/resource-dependency 设计冲突（列在 access-service.sql 中保留）；新 sync/full-sync 以 `sync_metadata` 为单源 | `CONFLICT_REQUIRES_DECISION` |
| F1.d | operation/change log 链路 | `permission_change_log.request_id` 当前可空，合并后 `operation_log.request_id` 也可空；API 契约写 `X-Request-Id` 可选且未传由 Gateway 生成，但 schema 注释又把 `request_id` 写成“请求/追踪ID(trace_id)”。需先统一 requestId/traceId 语义与生成链路，再考虑 NOT NULL | `CONFLICT_REQUIRES_DECISION` + `DESIGN_DRIFT` |
| F1.e | full-sync runbook | 收窄为**外部业务服务** sync/full-sync 运维手册（`/api/perm/**/sync`、`/full-sync` 与 `sync_metadata` ownership 校准）；内部 admin→permission full-sync 编排已随 T-ACCESS-005 删除。默认产物 `docs/ops/runbook-full-sync.md`，落地时按该路径新建 | `NO_HARD_CONFLICT` |
| F1.g | `normalize()` 三副本抽取 | `TypeDefinitionAppServiceImpl`/`SystemConfigAppServiceImpl`/`LogQueryAppServiceImpl` 三处同名同实现同注释的空白规整私有方法（2026-08-28 双轨评审登记），抽 `permission.util` 单一静态方法 | `NO_HARD_CONFLICT` |
| F1.f | `PermQuery.operationPermissionIds` 无写入方扩展点 | 引擎读该字段（`PermQueryEngine` 位掩码解析分支）但全仓无任何 setter 调用方，分支不可达；HEAD 已存在（非当次变更引入，2026-08-28 双轨评审登记）。随本工作单定夺：删除字段与引擎分支，或补写入方激活 | `NO_HARD_CONFLICT` |

## 执行前确认

1. F1.c 是否只限制“新增 sync/full-sync 以 `sync_metadata` 为单源”，暂不删除 resource_entity 现有字段。
2. F1.d 是否先统一 `requestId` / `traceId` 关联语义，并设计统一 requestId 生成/兜底链路，再考虑 schema 约束收紧。
3. F1.b 是否允许保留内部 Command/领域对象，不强行用外部 DTO 贯穿分层。
4. F1.e 默认新建 `docs/ops/runbook-full-sync.md`；若改放其他位置需额外确认。

## 验收标准

- 冲突项在设计文档中有明确取舍，不允许用任务文件覆盖权威设计。
- 代码简化不得引入 Controller -> Mapper、AppService 横向调用或 N+1 查询。
- requestId 约束收紧必须先完成 requestId/traceId 语义收敛，并有生成链路、兼容策略和测试覆盖。
- 运维 runbook 至少覆盖触发条件、执行步骤、回滚/重试、验收检查。
