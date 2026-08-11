---
doc_type: task
id: T-ACCESS-012
title: 删除残留引用、回写设计并重基线任务看板
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/architecture.md
  - docs/design/project-rules.md
  - docs/design/services/admin-service.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/permission-center/overview.md
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
  - docs/design/schema/admin-service.sql
  - docs/design/schema/permission-center.sql
  - docs/design/README.md
  - docs/README.md
depends_on:
  - T-ACCESS-011
blocks: []
acceptance:
  - "按最终实现回写整体架构、服务设计、权限设计、核心流程、实现设计、项目规范和文档索引"
  - "access-service.sql 成为当前 schema 权威入口；旧 admin/permission schema 和服务设计按生命周期标记 superseded 或归档"
  - "T-ACCESS-002 产出 access-service.sql 后、本任务进入 in-progress 前，将该文件补入本卡 design_refs；当前不创建指向未产出文件的悬空引用"
  - "全仓扫描旧服务名、旧数据库名、旧模块路径和内部同步术语；非归档残留均删除或有明确历史说明"
  - "逐项核对 proposed 的 T-PERM/T-ADMIN 后端任务：有效任务改为 access-service 路径与新 schema，失效/重叠任务取消并完成依赖重连"
  - "更新相关 phase plan、tasks/README、plans/README、design/README、docs/README，任务状态和设计回写映射一致"
  - "依赖图无循环和 dangling，设计变更待核对项全部关闭或登记明确后续责任"
  - "计划满足归档条件；稳定结论只存在于 docs/design，计划和任务卡不形成第二套契约"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-11
---

# T-ACCESS-012 删除残留引用、回写设计并重基线任务看板

## 背景

归并会使大量旧服务路径、schema 引用和未开始任务失效。本任务负责生命周期闭环，不以简单全文替换掩盖语义变化。

## 范围

- 回写所有受影响权威设计和索引。
- 扫描并处理旧运行引用。
- 对现有未完成任务逐卡重基线、取消或重连。
- 完成计划归档前自检。

## 完成记录

（待实施后填写。）
