---
doc_type: task
id: T-PERM-036
title: 动态数据权限端到端验证（scopeMode → 业务 SQL）
status: done
plan: docs/archive/2026-10-20/example-permission-demo-plan.md
domain: cross-service
design_refs:
  - docs/design/example-permission-demo.md#field-permissions
  - docs/design/example-permission-demo.md
  - docs/design/access-service-api-contract.md#contract-section-18-6
  - docs/design/engine/core-flows.md
  - docs/design/engine/implementation.md
  - docs/design/services/example-service.md
depends_on:
  - T-ACCESS-138
acceptance:
  - "使用非管理员演示身份，先完成配置/授权再导入；验证同一主体的行撤权、字段撤权、正负写入和跨报表隔离。"
  - "验证报表上下文字段隔离、行列四态组合、隐藏列无旁路、导入/读写字段权限以及字段新增删除的授权边界。"
  - 整计划执行授权已覆盖本卡；契约定稿和 T-ACCESS-138 完成后才进入实施
  - 复用现行 query-scopes 与统一执行器，验证权限事实到真实业务 SQL 查询结果的端到端链路
  - 验证 INSTANCE 精确范围过滤、ALL 无范围过滤但保留租户约束、DENIED 拒绝且不发业务 SQL、EMPTY 返回空且不发业务 SQL
  - 覆盖配置驱动的本人、团队、部门及范围并集，扩展已有地区字段仅修改配置和授权，无需修改 Java 或重启
  - 将实际缺口和验收结果回写对应设计，不重复建设已有查询和示例能力
design_writeback:
  required: true
  status: done
last_updated: 2026-10-20
---

## 背景

当前范围查询已交付，SDK 提供 `PermissionFeignClient.queryScopes`；服务端经 `PermissionQueryAppServiceImpl`、`QueryExecutionEngine.execute` 与 `ScopeCoverageProjector` 生成四态范围结果。接口与四态消费语义以[契约 §18.6](../../../design/access-service-api-contract.md#contract-section-18-6)为准。

example-service 已提供 Gateway 准入和业务最终检查，报表列表使用 `query-resources` 过滤并保持分页 total 同口径。独立业务库和行列执行器已由前置任务推进，现役内存探针本身不能证明 `query-scopes → scopeMode → 真实业务 SQL` 的数据过滤链路。[示例现状](../../../design/services/example-service.md)是重启时的起点，不再沿用“example-service 尚未实现”的描述。

## 范围

剩余范围为业务消费者将范围权限事实映射到真实 SQL 的端到端验证，核对主体、主资源上下文、直接与依赖范围、业务键映射、租户隔离及四态消费。现有查询引擎和接口能力继续复用。

本卡纳入 [权限演示计划](../example-permission-demo-plan.md)，在 T-ACCESS-140 交付动态策略和真实 SQL 后承担跨服务验证，不重复实现过滤器。输入/过滤实现分别由 T-ACCESS-138/140 承接；需求见 [权限演示设计](../../../design/example-permission-demo.md)。验证配置发布、无代码动态扩展、非法配置拒绝与读写范围一致性。

验证字段作为报表数据权限的真实 SQL 投影和写列约束，覆盖同名字段跨报表、行列 ALL 不互相放宽和空字段不取数。

本卡依赖导入完成后运行在线主线；按设计联合场景固定授权前提，任务阶段由 141/143 承接，142 整合。

## 当前口径

2026-10-19 用户已要求逐步执行整个权限演示计划，本卡用户授权层面的暂缓解除。定向真实链路与完整回归均已通过，设计回写完成；不把启动授权等同于技术准入或行为验收。

历史暂缓依据为 [design-review §11 Q7/B](../../2026-06-17/design-review.md)及 [improvement-plan §3.1](../../2026-08-27/improvement-plan.md)，仅用于追溯。未来实施使用上述现行设计，不恢复已退役的执行器或 DTO。

## 验收对照

- [x] 使用非管理员演示身份，先完成配置/授权再导入；验证同一主体的行撤权、字段撤权、正负写入和跨报表隔离。
- [x] 验证报表上下文字段隔离、行列四态组合、隐藏列无旁路、导入/读写字段权限以及字段新增删除的授权边界。
- [x] 解除暂缓并确认业务数据模型。
- [x] 实际 SQL 四态、配置发布、无代码扩展及读写范围端到端验证。
- [x] 缺口处置、设计回写和必要回归。

## 非目标 / 遗留

不重做已交付的权限判定、范围查询或报表列表接入；不因本卡重新启动权限排障页面、通用 SQL 自动改写、菜单/按钮/条件演示等独立事项。

## 完成记录

真实普通用户、四态、字段生命周期和双租户证据见[验收记录](evidence/example-permission-demo/T-PERM-036.md)；2026-10-20 完整回归 3,115 pass / 0 fail / 0 error / 0 skip，20:37 min，含 E2E/heavy。
