---
doc_type: task
id: T-PERM-036
title: 动态数据权限端到端验证（scopeMode → 业务 SQL）
status: proposed
plan: null
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md#contract-section-18-6
  - docs/design/engine/core-flows.md
  - docs/design/engine/implementation.md
  - docs/design/services/example-service.md
depends_on:
  - T-FE-013
  - T-PERM-033
acceptance:
  - 用户明确解除暂缓后才进入实施；描述校准不视为启动授权
  - 复用现行 query-scopes 与统一执行器，验证权限事实到真实业务 SQL 查询结果的端到端链路
  - 验证 INSTANCE 精确范围过滤、ALL 无范围过滤但保留租户约束、DENIED 拒绝且不发业务 SQL、EMPTY 返回空且不发业务 SQL
  - 覆盖已确认的本人、团队和部门场景，具体业务数据模型在重启时确认
  - 将实际缺口和验收结果回写对应设计，不重复建设已有查询和示例能力
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-18
---

## 背景

当前范围查询已交付，SDK 提供 `PermissionFeignClient.queryScopes`；服务端经 `PermissionQueryAppServiceImpl`、`QueryExecutionEngine.execute` 与 `ScopeCoverageProjector` 生成四态范围结果。接口与四态消费语义以[契约 §18.6](../design/access-service-api-contract.md#contract-section-18-6)为准。

example-service 已提供 Gateway 准入和业务最终检查，报表列表使用 `query-resources` 过滤并保持分页 total 同口径。该服务仍无数据源，现有示例不能证明 `query-scopes → scopeMode → 真实业务 SQL` 的数据过滤链路。[示例现状](../design/services/example-service.md)是重启时的起点，不再沿用“example-service 尚未实现”的描述。

## 范围

剩余范围为业务消费者将范围权限事实映射到真实 SQL 的端到端验证，核对主体、主资源上下文、直接与依赖范围、业务键映射、租户隔离及四态消费。现有查询引擎和接口能力继续复用。

## 当前口径

2026-10-18 用户确认继续暂缓，仅按现状校准任务描述。`status=proposed`、设计回写 pending 保持；必须由用户明确解除暂缓后才能实施。本次不引入业务数据库、SQL 自动改写模块或新的演示功能。

历史暂缓依据为 [design-review §11 Q7/B](../archive/2026-06-17/design-review.md)及 [improvement-plan §3.1](../archive/2026-08-27/improvement-plan.md)，仅用于追溯。未来实施使用上述现行设计，不恢复已退役的执行器或 DTO。

## 验收对照

- [ ] 解除暂缓并确认业务数据模型。
- [ ] 实际 SQL 端到端验证与四态消费。
- [ ] 缺口处置、设计回写和必要回归。

## 非目标 / 遗留

不重做已交付的权限判定、范围查询或报表列表接入；不因本卡重新启动权限排障页面、通用 SQL 自动改写、菜单/按钮/条件演示等独立事项。
